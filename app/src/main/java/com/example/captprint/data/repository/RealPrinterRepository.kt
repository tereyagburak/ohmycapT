package com.example.captprint.data.repository

import android.content.Context
import android.hardware.usb.UsbDevice
import android.net.Uri
import com.example.captprint.CaptNative
import com.example.captprint.CaptUsbTransport
import com.example.captprint.PdfPageRasterizer
import com.example.captprint.audio.PrintSoundManager
import com.example.captprint.domain.model.PrintSettings
import com.example.captprint.domain.model.PrinterCapabilities
import com.example.captprint.domain.model.PrinterStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class RealPrinterRepository(
    private val context: Context,
    private val transport: CaptUsbTransport = CaptUsbTransport(context),
    private val captNative: CaptNative = CaptNative(),
    private val rasterizer: PdfPageRasterizer = PdfPageRasterizer(context, captNative),
    private val soundManager: PrintSoundManager? = null,
    private val isRepositoryActive: () -> Boolean = { true }
) : PrinterRepository {

    private val _status = MutableStateFlow<PrinterStatus>(PrinterStatus.Disconnected)
    override val printerStatus: StateFlow<PrinterStatus> = _status.asStateFlow()

    override val capabilities: PrinterCapabilities = PrinterCapabilities()

    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isJobActive = AtomicBoolean(false)
    private val usbLock = Any()

    private var activeDevice: UsbDevice? = null
    private var nextPageApproval: CompletableDeferred<Unit>? = null
    @Volatile private var isPauseRequested: Boolean = false
    @Volatile private var isCancelledByUser: Boolean = false
    private var pauseDeferred: CompletableDeferred<Unit>? = null
    private var consecutivePollFailures = 0

    init {
        refreshStatus()
        startStatusMonitor()
    }

    private fun startStatusMonitor() {
        monitorScope.launch {
            while (isActive) {
                delay(1500)
                if (isJobActive.get()) continue
                try {
                    pollHardwareStatus()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun pollHardwareStatus() {
        val dev = transport.findPrinter()
        if (dev == null) {
            activeDevice = null
            if (transport.isOpen) {
                synchronized(usbLock) {
                    captNative.nativeClose()
                    transport.close()
                }
            }
            consecutivePollFailures = 0
            updateLiveStatus(PrinterStatus.Disconnected)
            return
        }

        activeDevice = dev
        val model = getModelName(dev)

        if (!transport.hasPermission(dev)) {
            consecutivePollFailures = 0
            updateLiveStatus(PrinterStatus.PermissionNeeded(dev.deviceName))
            return
        }

        val flags: IntArray? = synchronized(usbLock) {
            if (isJobActive.get()) return@synchronized null
            if (!transport.isOpen) {
                val opened = transport.open(dev)
                if (!opened) return@synchronized intArrayOf(-1)
                captNative.nativeOpen(transport)
            }
            captNative.nativePollStatus()
        }

        if (isJobActive.get()) return

        if (flags == null) {
            consecutivePollFailures++
            if (consecutivePollFailures >= 2) {
                if (transport.findPrinter() == null) {
                    synchronized(usbLock) {
                        captNative.nativeClose()
                        transport.close()
                    }
                    updateLiveStatus(PrinterStatus.Disconnected)
                } else {
                    synchronized(usbLock) {
                        captNative.nativeClose()
                        transport.close()
                    }
                    updateLiveStatus(
                        PrinterStatus.Error(
                            code = -2,
                            userMessage = "Yazıcıyla iletişim kurulamadı.",
                            technicalDetail = transport.lastError.ifEmpty { "0xA0A8 durum sorgusu zaman aşımı" }
                        )
                    )
                }
            }
            return
        }

        if (flags.isNotEmpty() && flags[0] == -1) {
            updateLiveStatus(
                PrinterStatus.Error(
                    code = -1,
                    userMessage = "Yazıcı bağlantısı açılamadı.",
                    technicalDetail = transport.lastError
                )
            )
            return
        }

        consecutivePollFailures = 0
        if (flags.size >= 8) {
            val mapped = mapStatusFlagsToPrinterStatus(flags, model, dev.deviceName)
            updateLiveStatus(mapped)
        }
    }

    private fun mapStatusFlagsToPrinterStatus(
        flags: IntArray,
        model: String,
        deviceName: String
    ): PrinterStatus {
        val s0 = flags[1]
        val s1 = flags[2]
        val s2 = flags[3]
        val s3 = flags[4]
        val s4 = flags[5]

        val noPaper = ((s0 and 0x0002) != 0) || ((s1 and 0x4000) != 0)
        val warmingUp = ((s1 and 0x0001) != 0) || ((s3 and 0x1000) != 0)
        val nErrorZero = (s2 and 0x0080) == 0

        // Canon LBP2900 0xA0A8: Kapak açıldığında status[3] bit 14 (0x4000) ve status[4]=0x005D olur
        // (ayrıca mekanik sensör nedeniyle NOPAPER1 biti de geçici olarak 1'e çekilir).
        val coverOpenBit = ((s2 and 0x0004) != 0) || ((s3 and 0x4000) != 0)
        val paperJamBit = (s2 and 0x0010) != 0
        val tonerBit = (s2 and 0x0002) != 0

        return when {
            coverOpenBit -> PrinterStatus.CoverOpen
            paperJamBit -> PrinterStatus.PaperJam
            tonerBit -> PrinterStatus.TonerLow
            warmingUp -> PrinterStatus.WarmingUp(model)
            nErrorZero -> PrinterStatus.Error(
                code = s2,
                userMessage = "Kapak açık, toner yok veya kağıt sıkışmış olabilir.",
                technicalDetail = "0xA0A8: S0=0x%04X S1=0x%04X S2=0x%04X S3=0x%04X S4=0x%04X".format(s0, s1, s2, s3, s4)
            )
            noPaper -> PrinterStatus.PaperOut
            else -> PrinterStatus.Ready(model, deviceName)
        }
    }

    private fun updateLiveStatus(newStatus: PrinterStatus) {
        val oldStatus = _status.value
        if (oldStatus == newStatus) return
        _status.value = newStatus

        if (!isRepositoryActive()) return

        when (newStatus) {
            is PrinterStatus.CoverOpen,
            is PrinterStatus.PaperOut,
            is PrinterStatus.PaperJam,
            is PrinterStatus.TonerLow -> soundManager?.playWarning()
            is PrinterStatus.Error -> soundManager?.playPrintError()
            is PrinterStatus.Ready -> {
                if (oldStatus is PrinterStatus.CoverOpen ||
                    oldStatus is PrinterStatus.PaperOut ||
                    oldStatus is PrinterStatus.PaperJam ||
                    oldStatus is PrinterStatus.TonerLow ||
                    oldStatus is PrinterStatus.Error ||
                    oldStatus is PrinterStatus.WarmingUp
                ) {
                    soundManager?.playStepPageReady()
                }
            }
            else -> Unit
        }
    }

    override fun refreshStatus() {
        val dev = transport.findPrinter()
        if (dev == null) {
            activeDevice = null
            _status.value = PrinterStatus.Disconnected
            return
        }

        activeDevice = dev
        val model = getModelName(dev)
        if (!transport.hasPermission(dev)) {
            _status.value = PrinterStatus.PermissionNeeded(dev.deviceName)
        } else if (_status.value is PrinterStatus.Disconnected || _status.value is PrinterStatus.PermissionNeeded) {
            _status.value = PrinterStatus.Ready(model, dev.deviceName)
        }
    }

    override fun connect() {
        val dev = transport.findPrinter()
        if (dev == null) {
            _status.value = PrinterStatus.Disconnected
            return
        }

        activeDevice = dev
        val model = getModelName(dev)

        if (!transport.hasPermission(dev)) {
            _status.value = PrinterStatus.PermissionNeeded(dev.deviceName)
            transport.requestPermission(dev) { granted ->
                if (granted) {
                    openAndInit(dev, model)
                } else {
                    _status.value = PrinterStatus.PermissionNeeded(dev.deviceName)
                }
            }
        } else {
            openAndInit(dev, model)
        }
    }

    private fun openAndInit(device: UsbDevice, model: String) {
        _status.value = PrinterStatus.Connecting(device.deviceName)
        val ok = synchronized(usbLock) {
            val opened = transport.open(device)
            if (opened) {
                captNative.nativeOpen(transport)
            }
            opened
        }
        if (ok) {
            _status.value = PrinterStatus.Ready(model, device.deviceName)
        } else {
            _status.value = PrinterStatus.Error(
                code = -1,
                userMessage = "Yazıcı bağlantısı açılamadı.",
                technicalDetail = transport.lastError
            )
            if (isRepositoryActive()) {
                soundManager?.playPrintError()
            }
        }
    }

    override fun disconnect() {
        synchronized(usbLock) {
            captNative.nativeClose()
            transport.close()
        }
        _status.value = PrinterStatus.Disconnected
    }

    override fun queryStatus(): String {
        return synchronized(usbLock) {
            if (transport.isOpen) {
                captNative.nativeQueryStatus()
            } else {
                "Yazıcı bağlı değil."
            }
        }
    }

    override suspend fun printTestPattern(patternType: Int, invert: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val dev = activeDevice ?: transport.findPrinter()
        if (dev == null) {
            soundManager?.playPrintError()
            return@withContext Result.failure(Exception("Yazıcı bulunamadı."))
        }
        isJobActive.set(true)
        try {
            synchronized(usbLock) {
                if (!transport.isOpen && !transport.open(dev)) {
                    soundManager?.playPrintError()
                    return@withContext Result.failure(Exception("Bağlantı açılamadı: ${transport.lastError}"))
                }
                captNative.nativeOpen(transport)
            }

            val res = captNative.nativePrintTestPattern(patternType, invert)
            if (res == 0) {
                soundManager?.playPrintDone()
                Result.success(Unit)
            } else {
                soundManager?.playPrintError()
                Result.failure(Exception("Test sayfası hatası: $res"))
            }
        } finally {
            isJobActive.set(false)
        }
    }

    override suspend fun printDocument(
        uri: Uri,
        settings: PrintSettings,
        onProgress: (page: Int, total: Int, percent: Float, message: String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        isCancelledByUser = false
        val dev = activeDevice ?: transport.findPrinter()
        if (dev == null) {
            soundManager?.playPrintError()
            return@withContext Result.failure(Exception("Yazıcı bağlı değil."))
        }

        isJobActive.set(true)
        try {
            synchronized(usbLock) {
                if (!transport.isOpen && !transport.open(dev)) {
                    soundManager?.playPrintError()
                    return@withContext Result.failure(Exception("USB portu açılamadı: ${transport.lastError}"))
                }
                captNative.nativeOpen(transport)
            }

            val success = rasterizer.printUriAdvanced(
                uri = uri,
                invert = settings.invertColors,
                tonerDensity = settings.tonerDensity,
                copies = settings.copies,
                onPageFinished = { pageIndex, totalPages ->
                    // 1. Manuel besleme tek tek onay modu
                    if (settings.manualFeedStepByStep && pageIndex < totalPages - 1) {
                        val printed = pageIndex + 1
                        val next = pageIndex + 2
                        _status.value = PrinterStatus.WaitingForNextPage(printed, next, totalPages)
                        soundManager?.playStepPageReady()
                        val approval = CompletableDeferred<Unit>()
                        nextPageApproval = approval
                        approval.await()
                        nextPageApproval = null
                        _status.value = PrinterStatus.Printing(
                            currentPage = next,
                            totalPages = totalPages,
                            progressPercent = (pageIndex + 1).toFloat() / totalPages.toFloat(),
                            statusMessage = "Sayfa $next / $totalPages dither ediliyor..."
                        )
                    }

                    // 2. Kullanıcı tarafından duraklatma (Pause) isteği varsa burada beklet
                    if (isPauseRequested && pageIndex < totalPages - 1) {
                        val completed = pageIndex + 1
                        val next = pageIndex + 2
                        _status.value = PrinterStatus.Paused(completedPages = completed, nextPageIndex = next, totalPages = totalPages)
                        val resumeWait = CompletableDeferred<Unit>()
                        pauseDeferred = resumeWait
                        resumeWait.await()
                        pauseDeferred = null
                        isPauseRequested = false
                        _status.value = PrinterStatus.Printing(
                            currentPage = next,
                            totalPages = totalPages,
                            progressPercent = (pageIndex + 1).toFloat() / totalPages.toFloat(),
                            statusMessage = "Devam ediliyor: Sayfa $next / $totalPages hazırlanıyor..."
                        )
                    }
                },
                onProgress = { page, total, percent, message ->
                    _status.value = PrinterStatus.Printing(page, total, percent, message)
                    onProgress(page, total, percent, message)
                }
            )

            if (success) {
                _status.value = PrinterStatus.Ready(getModelName(dev), dev.deviceName)
                soundManager?.playPrintDone()
                Result.success(Unit)
            } else {
                val detail = transport.lastError.ifEmpty { "Yazıcı donanım hatası veya bağlantı kesildi." }
                _status.value = PrinterStatus.Error(-1, "Yazdırma tamamlanamadı.", detail)
                if (!isCancelledByUser) {
                    soundManager?.playPrintError()
                }
                Result.failure(Exception("Yazdırma tamamlanamadı: $detail"))
            }
        } finally {
            isJobActive.set(false)
        }
    }

    override fun pausePrint() {
        isPauseRequested = true
    }

    override fun resumePrint() {
        pauseDeferred?.complete(Unit)
    }

    override fun approveNextPage() {
        nextPageApproval?.complete(Unit)
    }

    override fun cancelPrint() {
        isCancelledByUser = true
        isPauseRequested = false
        pauseDeferred?.cancel()
        captNative.nativeCancel()
        nextPageApproval?.cancel()
    }

    private fun getModelName(device: UsbDevice): String {
        return when (device.productId) {
            CaptUsbTransport.LBP2900_PID -> "Canon LBP2900"
            CaptUsbTransport.LBP3000_PID -> "Canon LBP3000"
            CaptUsbTransport.LBP3100_PID -> "Canon LBP3100"
            CaptUsbTransport.LBP6000_PID -> "Canon LBP6000"
            else -> "Canon CAPT Yazıcı (${device.deviceName})"
        }
    }
}
