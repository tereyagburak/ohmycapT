package com.example.captprint.data.repository

import android.net.Uri
import com.example.captprint.audio.PrintSoundManager
import com.example.captprint.domain.model.PrinterCapabilities
import com.example.captprint.domain.model.PrinterStatus
import com.example.captprint.domain.model.PrintSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakePrinterRepository(
    private val soundManager: PrintSoundManager? = null
) : PrinterRepository {

    private val _status = MutableStateFlow<PrinterStatus>(PrinterStatus.Ready("Canon LBP2900 (Simülatör)", "USB 1-1.2"))
    override val printerStatus: StateFlow<PrinterStatus> = _status.asStateFlow()

    override val capabilities: PrinterCapabilities = PrinterCapabilities()

    private var nextPageApproval: CompletableDeferred<Unit>? = null
    private var pauseDeferred: CompletableDeferred<Unit>? = null
    @Volatile private var isPauseRequested = false
    @Volatile private var isCancelled = false

    @Volatile var simulateErrorMidPrint: Boolean = false
    @Volatile var fastSimulation: Boolean = false

    fun setFakeStatus(newStatus: PrinterStatus) {
        val oldStatus = _status.value
        if (oldStatus == newStatus) return
        _status.value = newStatus
        when (newStatus) {
            is PrinterStatus.Error -> soundManager?.playPrintError()
            is PrinterStatus.PaperOut,
            is PrinterStatus.PaperJam,
            is PrinterStatus.CoverOpen,
            is PrinterStatus.TonerLow -> soundManager?.playWarning()
            is PrinterStatus.Ready -> {
                if (oldStatus is PrinterStatus.PaperOut ||
                    oldStatus is PrinterStatus.PaperJam ||
                    oldStatus is PrinterStatus.CoverOpen ||
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
        // Simülatör durumunu korur
    }

    override fun connect() {
        _status.value = PrinterStatus.Connecting("Canon LBP2900 (Simülatör)")
        _status.value = PrinterStatus.Ready("Canon LBP2900 (Simülatör)", "USB 1-1.2")
    }

    override fun disconnect() {
        _status.value = PrinterStatus.Disconnected
    }

    override fun queryStatus(): String {
        return when (val st = _status.value) {
            is PrinterStatus.Ready ->
                "C-DURUM (0xA0A8 - SİMÜLATÖR):\n" +
                "  STATUS0=0x0000, STATUS1=0x0000, STATUS2=0x0000\n" +
                "  Kağıt Yok: HAYIR | Tampon Dolu: HAYIR | Meşgul: HAYIR\n" +
                "  Yazıyor: HAYIR | Hata: YOK\n" +
                "  Sayaçlar (Çözülen/Basılan/Çıkan): 12 / 12 / 12"
            is PrinterStatus.PaperOut ->
                "C-DURUM (0xA0A8 - SİMÜLATÖR):\n  STATUS0=0x0004 (KAĞIT YOK)\n  Kağıt Yok: EVET | Hata: EVET"
            is PrinterStatus.PaperJam ->
                "C-DURUM (0xA0A8 - SİMÜLATÖR):\n  STATUS1=0x0008 (KAĞIT SIKIŞTI)\n  Sıkışma: EVET | Hata: EVET"
            is PrinterStatus.CoverOpen ->
                "C-DURUM (0xA0A8 - SİMÜLATÖR):\n  STATUS1=0x0002 (KAPAK AÇIK)\n  Kapak Açık: EVET | Hata: EVET"
            is PrinterStatus.Error ->
                "C-DURUM (0xA0A8 - SİMÜLATÖR):\n  Hata Kodu: ${st.code} | ${st.technicalDetail}"
            else -> "Simülatör durumu: $st"
        }
    }

    override suspend fun printTestPattern(patternType: Int, invert: Boolean): Result<Unit> {
        isCancelled = false
        val stepDelay = if (fastSimulation) 250L else 1000L
        _status.value = PrinterStatus.Printing(1, 1, 0.2f, "Test sayfası basılıyor...")
        delay(stepDelay)
        if (simulateErrorMidPrint) {
            _status.value = PrinterStatus.Error(-2, "Test sayfası basılamadı (Simülasyon).", "LIBUSB_ERROR_TIMEOUT (Simüle edilmiş hata)")
            soundManager?.playPrintError()
            return Result.failure(Exception("Test sayfası simülasyon hatası"))
        }
        _status.value = PrinterStatus.Printing(1, 1, 0.8f, "Kağıt sürülüyor...")
        delay(stepDelay)
        _status.value = PrinterStatus.Ready("Canon LBP2900 (Simülatör)", "USB 1-1.2")
        soundManager?.playPrintDone()
        return Result.success(Unit)
    }

    override suspend fun printDocument(
        uri: Uri,
        settings: PrintSettings,
        onProgress: (page: Int, total: Int, percent: Float, message: String) -> Unit
    ): Result<Unit> {
        isCancelled = false
        isPauseRequested = false
        val totalPages = 3 // Simülasyon için 3 sayfa
        val ditherDelay = if (fastSimulation) 220L else 600L
        val transferDelay = if (fastSimulation) 280L else 800L

        for (p in 1..totalPages) {
            if (isCancelled) return Result.failure(Exception("Kullanıcı tarafından iptal edildi."))

            _status.value = PrinterStatus.Printing(p, totalPages, (p - 1).toFloat() / totalPages, "Sayfa $p / $totalPages dither ediliyor...")
            onProgress(p, totalPages, (p - 1).toFloat() / totalPages, "Sayfa $p / $totalPages dither ediliyor...")
            delay(ditherDelay)

            if (isCancelled) return Result.failure(Exception("Kullanıcı tarafından iptal edildi."))

            _status.value = PrinterStatus.Printing(p, totalPages, (p - 0.5f) / totalPages, "Sayfa $p / $totalPages yazıcıya aktarılıyor...")
            onProgress(p, totalPages, (p - 0.5f) / totalPages, "Sayfa $p / $totalPages yazıcıya aktarılıyor...")
            delay(transferDelay)

            if (simulateErrorMidPrint && p == 2) {
                val detail = "USB OTG Bulk Transfer Zaman Aşımı (-7) • Simüle edilmiş donanım hatası"
                _status.value = PrinterStatus.Error(-7, "Yazdırma tamamlanamadı.", detail)
                soundManager?.playPrintError()
                return Result.failure(Exception("Yazdırma tamamlanamadı: $detail"))
            }

            // Tek tek onaylı mod aktifse ve son sayfa değilse kullanıcı onayı bekle
            if (settings.manualFeedStepByStep && p < totalPages) {
                _status.value = PrinterStatus.WaitingForNextPage(p, p + 1, totalPages)
                soundManager?.playStepPageReady()
                onProgress(p, totalPages, p.toFloat() / totalPages, "Sayfa $p çıktı. Tepsiye kağıt koyup onaylayın.")
                val approval = CompletableDeferred<Unit>()
                nextPageApproval = approval
                approval.await()
                nextPageApproval = null
            }

            // Duraklatma istendiyse bekle
            if (isPauseRequested && p < totalPages) {
                _status.value = PrinterStatus.Paused(p, p + 1, totalPages)
                val resumeWait = CompletableDeferred<Unit>()
                pauseDeferred = resumeWait
                resumeWait.await()
                pauseDeferred = null
                isPauseRequested = false
            }
        }

        _status.value = PrinterStatus.Ready("Canon LBP2900 (Simülatör)", "USB 1-1.2")
        soundManager?.playPrintDone()
        return Result.success(Unit)
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
        isCancelled = true
        isPauseRequested = false
        pauseDeferred?.cancel()
        nextPageApproval?.cancel()
        _status.value = PrinterStatus.Ready("Canon LBP2900 (Simülatör)", "USB 1-1.2")
    }
}
