package com.example.captprint.data.repository

import android.content.Context
import android.hardware.usb.UsbDevice
import android.net.Uri
import com.example.captprint.CaptNative
import com.example.captprint.CaptUsbTransport
import com.example.captprint.PdfPageRasterizer
import com.example.captprint.domain.model.PrinterCapabilities
import com.example.captprint.domain.model.PrinterStatus
import com.example.captprint.domain.model.PrintSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class RealPrinterRepository(
    private val context: Context,
    private val transport: CaptUsbTransport = CaptUsbTransport(context),
    private val captNative: CaptNative = CaptNative(),
    private val rasterizer: PdfPageRasterizer = PdfPageRasterizer(context, captNative)
) : PrinterRepository {

    private val _status = MutableStateFlow<PrinterStatus>(PrinterStatus.Disconnected)
    override val printerStatus: StateFlow<PrinterStatus> = _status.asStateFlow()

    override val capabilities: PrinterCapabilities = PrinterCapabilities()

    private var activeDevice: UsbDevice? = null
    private var nextPageApproval: CompletableDeferred<Unit>? = null
    @Volatile private var isPauseRequested: Boolean = false
    private var pauseDeferred: CompletableDeferred<Unit>? = null

    init {
        refreshStatus()
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
        } else {
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
        val ok = transport.open(device)
        if (ok) {
            captNative.nativeOpen(transport)
            _status.value = PrinterStatus.Ready(model, device.deviceName)
        } else {
            _status.value = PrinterStatus.Error(
                code = -1,
                userMessage = "Yazıcı bağlantısı açılamadı.",
                technicalDetail = transport.lastError
            )
        }
    }

    override fun disconnect() {
        captNative.nativeClose()
        transport.close()
        _status.value = PrinterStatus.Disconnected
    }

    override fun queryStatus(): String {
        return if (transport.isOpen) {
            captNative.nativeQueryStatus()
        } else {
            "Yazıcı bağlı değil."
        }
    }

    override suspend fun printTestPattern(patternType: Int, invert: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val dev = activeDevice ?: transport.findPrinter()
        if (dev == null) return@withContext Result.failure(Exception("Yazıcı bulunamadı."))
        if (!transport.isOpen && !transport.open(dev)) {
            return@withContext Result.failure(Exception("Bağlantı açılamadı: ${transport.lastError}"))
        }

        captNative.nativeOpen(transport)
        val res = captNative.nativePrintTestPattern(patternType, invert)
        if (res == 0) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Test sayfası hatası: $res"))
        }
    }

    override suspend fun printDocument(
        uri: Uri,
        settings: PrintSettings,
        onProgress: (page: Int, total: Int, percent: Float, message: String) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val dev = activeDevice ?: transport.findPrinter()
        if (dev == null) return@withContext Result.failure(Exception("Yazıcı bağlı değil."))
        if (!transport.isOpen && !transport.open(dev)) {
            return@withContext Result.failure(Exception("USB portu açılamadı: ${transport.lastError}"))
        }

        captNative.nativeOpen(transport)

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
            Result.success(Unit)
        } else {
            val detail = transport.lastError.ifEmpty { "Yazıcı donanım hatası veya bağlantı kesildi." }
            _status.value = PrinterStatus.Error(-1, "Yazdırma tamamlanamadı.", detail)
            Result.failure(Exception("Yazdırma tamamlanamadı: $detail"))
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
