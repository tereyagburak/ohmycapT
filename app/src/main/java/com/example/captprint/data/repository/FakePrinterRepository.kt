package com.example.captprint.data.repository

import android.net.Uri
import com.example.captprint.domain.model.PrinterCapabilities
import com.example.captprint.domain.model.PrinterStatus
import com.example.captprint.domain.model.PrintSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakePrinterRepository : PrinterRepository {

    private val _status = MutableStateFlow<PrinterStatus>(PrinterStatus.Ready("Canon LBP2900 (Simülatör)", "USB 1-1.2"))
    override val printerStatus: StateFlow<PrinterStatus> = _status.asStateFlow()

    override val capabilities: PrinterCapabilities = PrinterCapabilities()

    private var nextPageApproval: CompletableDeferred<Unit>? = null
    private var isCancelled = false

    fun setFakeStatus(newStatus: PrinterStatus) {
        _status.value = newStatus
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
        return if (_status.value is PrinterStatus.Ready) {
            "C-DURUM (0xA0A8):\n" +
            "  STATUS0=0x0000, STATUS1=0x0000, STATUS2=0x0000\n" +
            "  Kağıt Yok: HAYIR | Tampon Dolu: HAYIR | Meşgul: HAYIR\n" +
            "  Yazıyor: HAYIR | Hata: YOK\n" +
            "  Sayaçlar (Çözülen/Basılan/Çıkan): 0 / 0 / 0"
        } else {
            "Yazıcı bağlı değil."
        }
    }

    override suspend fun printTestPattern(patternType: Int, invert: Boolean): Result<Unit> {
        isCancelled = false
        _status.value = PrinterStatus.Printing(1, 1, 0.2f, "Test sayfası basılıyor...")
        delay(1200)
        _status.value = PrinterStatus.Printing(1, 1, 0.8f, "Kağıt sürülüyor...")
        delay(800)
        _status.value = PrinterStatus.Ready("Canon LBP2900 (Simülatör)", "USB 1-1.2")
        return Result.success(Unit)
    }

    override suspend fun printDocument(
        uri: Uri,
        settings: PrintSettings,
        onProgress: (page: Int, total: Int, percent: Float, message: String) -> Unit
    ): Result<Unit> {
        isCancelled = false
        val totalPages = 3 // Simülasyon için 3 sayfa

        for (p in 1..totalPages) {
            if (isCancelled) return Result.failure(Exception("Kullanıcı tarafından iptal edildi."))

            _status.value = PrinterStatus.Printing(p, totalPages, (p - 1).toFloat() / totalPages, "Sayfa $p / $totalPages dither ediliyor...")
            onProgress(p, totalPages, (p - 1).toFloat() / totalPages, "Sayfa $p / $totalPages dither ediliyor...")
            delay(600)

            _status.value = PrinterStatus.Printing(p, totalPages, (p - 0.5f) / totalPages, "Sayfa $p / $totalPages yazıcıya aktarılıyor...")
            onProgress(p, totalPages, (p - 0.5f) / totalPages, "Sayfa $p / $totalPages yazıcıya aktarılıyor...")
            delay(800)

            // Tek tek onaylı mod aktifse ve son sayfa değilse kullanıcı onayı bekle
            if (settings.manualFeedStepByStep && p < totalPages) {
                _status.value = PrinterStatus.WaitingForNextPage(p, p + 1, totalPages)
                onProgress(p, totalPages, p.toFloat() / totalPages, "Sayfa $p çıktı. Tepsiye kağıt koyup onaylayın.")
                val approval = CompletableDeferred<Unit>()
                nextPageApproval = approval
                approval.await()
                nextPageApproval = null
            }
        }

        _status.value = PrinterStatus.Ready("Canon LBP2900 (Simülatör)", "USB 1-1.2")
        return Result.success(Unit)
    }

    override fun pausePrint() {
        val current = _status.value
        if (current is PrinterStatus.Printing) {
            _status.value = PrinterStatus.Paused(current.currentPage - 1, current.currentPage, current.totalPages)
        }
    }

    override fun resumePrint() {
        val current = _status.value
        if (current is PrinterStatus.Paused) {
            _status.value = PrinterStatus.Printing(current.nextPageIndex, current.totalPages, 0.5f, "Devam ediliyor...")
        }
    }

    override fun approveNextPage() {
        nextPageApproval?.complete(Unit)
    }

    override fun cancelPrint() {
        isCancelled = true
        nextPageApproval?.cancel()
        _status.value = PrinterStatus.Ready("Canon LBP2900 (Simülatör)", "USB 1-1.2")
    }
}
