package com.example.captprint

import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

enum class VirtualPrinterState {
    READY,
    COVER_OPEN,
    PAPER_OUT,
    PAPER_JAM,
    TONER_LOW,
    WARMING_UP,
    HARDWARE_ERROR,
    DISCONNECTED
}

/**
 * Canon LBP2900 / CAPT protokolünü bayt düzeyinde simüle eden sanal yazıcı motoru.
 * Taşıma (USB Transport) katmanında çalışır; üst katmanlar (C sürücüsü, JNI, rasterizer, repository)
 * yazıcının sanal olduğunu bilmeden gerçek protokol komutlarını çalıştırır.
 */
class VirtualCaptPrinter {

    companion object {
        private const val TAG = "VirtualCapt"

        // CAPT Komut Kodları
        const val CMD_CHKXSTATUS = 0xA0A8
        const val CMD_IDENT = 0xA1A1
        const val CMD_JOB_BEGIN = 0xA2A0
        const val CMD_START_0 = 0xA3A2
        const val CMD_PRINT_DATA = 0xC0A0
        const val CMD_PRINT_DATA_END = 0xC0A4
        const val CMD_SET_PARMS = 0xD0A9
        const val CMD_CHKSTATUS = 0xE0A0
        const val CMD_START_2 = 0xE0A2
        const val CMD_START_1 = 0xE0A3
        const val CMD_START_3 = 0xE0A4
        const val CMD_UPLOAD_2 = 0xE0A5
        const val CMD_FIRE = 0xE0A7
        const val CMD_JOB_END = 0xE0A9
        const val CMD_JOB_SETUP = 0xE1A1
        const val CMD_GPIO = 0xE1A2
    }

    @Volatile
    var state: VirtualPrinterState = VirtualPrinterState.READY

    @Volatile
    var fastSimulation: Boolean = false

    @Volatile
    var simulateErrorOnPage: Int = -1

    private val rxBuffer = ByteArrayOutputStream()
    private val responseQueue = LinkedBlockingQueue<Byte>()
    private val lock = Any()

    // Yazıcı dahili sayaçları
    @Volatile private var jobId: Int = 1
    @Volatile var currentPage: Int = 1
        private set
    @Volatile private var pageDecoding: Int = 1
    @Volatile private var pagePrinting: Int = 1
    @Volatile private var pageOut: Int = 0
    @Volatile private var pageCompleted: Int = 0
    @Volatile private var pageReceived: Int = 0
    @Volatile private var totalBytesReceived: Long = 0L

    fun open() {
        synchronized(lock) {
            rxBuffer.reset()
            responseQueue.clear()
            currentPage = 1
            pageDecoding = 1
            pagePrinting = 1
            pageOut = 0
            pageCompleted = 0
            pageReceived = 0
            totalBytesReceived = 0L
            Log.d(TAG, "Sanal Canon LBP2900 açıldı (Durum: $state)")
        }
    }

    fun close() {
        synchronized(lock) {
            rxBuffer.reset()
            responseQueue.clear()
            Log.d(TAG, "Sanal Canon LBP2900 kapatıldı")
        }
    }

    fun resetCounters() {
        synchronized(lock) {
            currentPage = 1
            pageDecoding = 1
            pagePrinting = 1
            pageOut = 0
            pageCompleted = 0
            pageReceived = 0
            totalBytesReceived = 0L
        }
    }

    /**
     * Host'tan (C sürücüsünden) gelen baytları işler.
     */
    fun write(buf: ByteArray, len: Int): Int {
        if (state == VirtualPrinterState.DISCONNECTED) {
            Log.w(TAG, "Sanal yazıcı bağlantısı kesik (DISCONNECTED)")
            return -1
        }

        if (simulateErrorOnPage > 0 && currentPage == simulateErrorOnPage) {
            Log.w(TAG, "Simüle edilmiş sayfa $currentPage hatası tetiklendi (write)")
            return -1
        }

        synchronized(lock) {
            rxBuffer.write(buf, 0, len)
            val currentBytes = rxBuffer.toByteArray()
            var offset = 0

            while (offset + 4 <= currentBytes.size) {
                val cmd = (currentBytes[offset].toInt() and 0xFF) or ((currentBytes[offset + 1].toInt() and 0xFF) shl 8)
                val pktLen = (currentBytes[offset + 2].toInt() and 0xFF) or ((currentBytes[offset + 3].toInt() and 0xFF) shl 8)

                if (pktLen < 4 || pktLen > 65536) {
                    // Geçersiz paket başlığı, tamponu temizle
                    rxBuffer.reset()
                    break
                }

                if (offset + pktLen <= currentBytes.size) {
                    // Tam bir paket mevcut
                    val payload = currentBytes.copyOfRange(offset + 4, offset + pktLen)
                    processCommand(cmd, payload)
                    offset += pktLen
                } else {
                    // Paketin devamı henüz gelmedi
                    break
                }
            }

            if (offset > 0) {
                val remaining = currentBytes.copyOfRange(offset, currentBytes.size)
                rxBuffer.reset()
                rxBuffer.write(remaining)
            }
        }
        return len
    }

    /**
     * Host'un (C sürücüsünün) beklediği yanıt baytlarını döndürür.
     */
    fun read(dest: ByteArray, maxLen: Int, timeoutMs: Int): Int {
        if (state == VirtualPrinterState.DISCONNECTED) {
            return -1
        }

        if (simulateErrorOnPage > 0 && currentPage == simulateErrorOnPage && responseQueue.isEmpty()) {
            Log.w(TAG, "Simüle edilmiş sayfa $currentPage hatası tetiklendi (read)")
            return -1
        }

        val firstByte = responseQueue.poll(timeoutMs.toLong(), TimeUnit.MILLISECONDS) ?: return -1

        var count = 0
        dest[count++] = firstByte

        while (count < maxLen) {
            val b = responseQueue.poll() ?: break
            dest[count++] = b
        }

        return count
    }

    private fun processCommand(cmd: Int, payload: ByteArray) {
        when (cmd) {
            CMD_CHKXSTATUS -> {
                // 0xA0A8 Durum Sorgusu -> 56 baytlık yanıt döner
                val statusPacket = buildStatusPacket()
                enqueueResponse(statusPacket)
            }

            CMD_IDENT -> {
                // 0xA1A1 Tanımlama -> 4 bayt yankı
                enqueueResponse(byteArrayOf(0xA1.toByte(), 0xA1.toByte(), 0x04.toByte(), 0x00.toByte()))
            }

            CMD_START_0 -> {
                // 0xA3A2 Başlatma 0 -> 4 bayt yankı
                enqueueResponse(byteArrayOf(0xA2.toByte(), 0xA3.toByte(), 0x04.toByte(), 0x00.toByte()))
            }

            CMD_JOB_BEGIN -> {
                // 0xA2A0 Yazdırma İşi Başlangıcı -> 8 bayt yanıt (offset 2,3 = job_id)
                resetCounters()
                jobId = (jobId % 9999) + 1
                val resp = ByteArray(8)
                resp[0] = 0xA0.toByte()
                resp[1] = 0xA2.toByte()
                resp[2] = 0x08.toByte()
                resp[3] = 0x00.toByte()
                resp[4] = 0x00.toByte()
                resp[5] = 0x00.toByte()
                resp[6] = (jobId and 0xFF).toByte()
                resp[7] = ((jobId shr 8) and 0xFF).toByte()
                enqueueResponse(resp)
                Log.d(TAG, "Sanal yazıcı yeni yazdırma işi başlattı (Job ID: $jobId)")
            }

            CMD_GPIO -> {
                // 0xE1A2 GPIO -> 4 bayt yankı
                enqueueResponse(byteArrayOf(0xA2.toByte(), 0xE1.toByte(), 0x04.toByte(), 0x00.toByte()))
            }

            CMD_JOB_SETUP -> {
                // 0xE1A1 İş parametreleri -> 4 bayt yankı
                enqueueResponse(byteArrayOf(0xA1.toByte(), 0xE1.toByte(), 0x04.toByte(), 0x00.toByte()))
            }

            CMD_START_1 -> enqueueResponse(byteArrayOf(0xA3.toByte(), 0xE0.toByte(), 0x04.toByte(), 0x00.toByte()))
            CMD_START_2 -> enqueueResponse(byteArrayOf(0xA2.toByte(), 0xE0.toByte(), 0x04.toByte(), 0x00.toByte()))
            CMD_START_3 -> enqueueResponse(byteArrayOf(0xA4.toByte(), 0xE0.toByte(), 0x04.toByte(), 0x00.toByte()))
            CMD_UPLOAD_2 -> enqueueResponse(byteArrayOf(0xA5.toByte(), 0xE0.toByte(), 0x04.toByte(), 0x00.toByte()))

            CMD_SET_PARMS -> {
                // 0xD0A9 Çoklu sayfa/dither parametreleri (Tek yönlü capt_send_buf)
                // Yanıt beklenmez
            }

            CMD_PRINT_DATA -> {
                // 0xC0A0 Bant sıkıştırılmış raster verisi (Tek yönlü)
                totalBytesReceived += payload.size
            }

            CMD_PRINT_DATA_END -> {
                // 0xC0A4 Sayfa verisi bitti -> pageReceived güncellenir
                pageReceived = pageDecoding
                Log.d(TAG, "Sayfa $currentPage verisi tamamlandı (Toplam bayt: $totalBytesReceived)")
            }

            CMD_FIRE -> {
                // 0xE0A7 Kağıdı sür/ateşle
                enqueueResponse(byteArrayOf(0xA7.toByte(), 0xE0.toByte(), 0x04.toByte(), 0x00.toByte()))
                // Sayfa çıkışını simüle et
                pageOut = pageDecoding
                pageCompleted = pageDecoding
                Log.d(TAG, "Sayfa $currentPage başarıyla çıktı (pageOut=$pageOut)")
                pageDecoding++
                pagePrinting = pageDecoding
                currentPage++
            }

            CMD_JOB_END -> {
                // 0xE0A9 Yazdırma işi sonu
                enqueueResponse(byteArrayOf(0xA9.toByte(), 0xE0.toByte(), 0x04.toByte(), 0x00.toByte()))
                Log.d(TAG, "Yazdırma işi tamamlandı.")
            }

            else -> {
                // Diğer olası komutlar için güvenli varsayılan yankı yanıtı
                val resp = ByteArray(4)
                resp[0] = (cmd and 0xFF).toByte()
                resp[1] = ((cmd shr 8) and 0xFF).toByte()
                resp[2] = 0x04.toByte()
                resp[3] = 0x00.toByte()
                enqueueResponse(resp)
            }
        }
    }

    private fun enqueueResponse(bytes: ByteArray) {
        for (b in bytes) {
            responseQueue.offer(b)
        }
    }

    /**
     * Gerçek Canon LBP2900 logcat analizine birebir uygun 56 baytlık durum paketi üretir.
     */
    private fun buildStatusPacket(): ByteArray {
        val p = ByteArray(56)

        // Başlık: CMD=0xA0A8, BOYUT=56 (0x0038)
        p[0] = 0xA8.toByte()
        p[1] = 0xA0.toByte()
        p[2] = 0x38.toByte()
        p[3] = 0x00.toByte()

        var s0 = 0x8A11
        var s1 = 0x0000
        var s2 = 0x0080 // nERROR = 1 (Normal, hata yok)
        var s3 = 0x0000
        var s4 = 0x0056
        var s5 = 0x0000
        var s6 = 0x0000

        when (state) {
            VirtualPrinterState.COVER_OPEN -> {
                s0 = 0x8A13 // Mekanik olarak NOPAPER1 de 1 olur
                s2 = 0x0080
                s3 = 0x4000 // Kapak açık biti (bit 14)
                s4 = 0x005D
            }
            VirtualPrinterState.PAPER_OUT -> {
                s0 = 0x8A13 // NOPAPER1 (bit 1)
                s1 = 0x4000 // NOPAPER2 (bit 14)
                s2 = 0x0080
            }
            VirtualPrinterState.PAPER_JAM -> {
                s2 = 0x0010 // Sıkışma biti (bit 4), nERROR (bit 7) sıfırlanır
            }
            VirtualPrinterState.TONER_LOW -> {
                s2 = 0x0002 // Toner/Kartuş biti (bit 1), nERROR sıfırlanır
            }
            VirtualPrinterState.WARMING_UP -> {
                s1 = 0x0001 // POWERUP
                s3 = 0x1000 // POWERUP1
                s4 = 0x0057
            }
            VirtualPrinterState.HARDWARE_ERROR -> {
                s2 = 0x0000 // nERROR = 0 (Kritik motor hatası)
            }
            VirtualPrinterState.READY,
            VirtualPrinterState.DISCONNECTED -> {
                // Normal hazır durum
            }
        }

        // status[0] at offset 4, 5
        p[4] = (s0 and 0xFF).toByte()
        p[5] = ((s0 shr 8) and 0xFF).toByte()

        p[8] = 0x0F.toByte()

        // status[1] at offset 12, 13
        p[12] = (s1 and 0xFF).toByte()
        p[13] = ((s1 shr 8) and 0xFF).toByte()

        // status[2] at offset 14, 15
        p[14] = (s2 and 0xFF).toByte()
        p[15] = ((s2 shr 8) and 0xFF).toByte()

        // status[3] at offset 16, 17
        p[16] = (s3 and 0xFF).toByte()
        p[17] = ((s3 shr 8) and 0xFF).toByte()

        // page_decoding at offset 18, 19
        p[18] = (pageDecoding and 0xFF).toByte()
        p[19] = ((pageDecoding shr 8) and 0xFF).toByte()

        // page_printing at offset 20, 21
        p[20] = (pagePrinting and 0xFF).toByte()
        p[21] = ((pagePrinting shr 8) and 0xFF).toByte()

        // page_out at offset 22, 23
        p[22] = (pageOut and 0xFF).toByte()
        p[23] = ((pageOut shr 8) and 0xFF).toByte()

        // page_completed at offset 24, 25
        p[24] = (pageCompleted and 0xFF).toByte()
        p[25] = ((pageCompleted shr 8) and 0xFF).toByte()

        // status[4] at offset 28, 29
        p[28] = (s4 and 0xFF).toByte()
        p[29] = ((s4 shr 8) and 0xFF).toByte()

        // status[5] at offset 34, 35
        p[34] = (s5 and 0xFF).toByte()
        p[35] = ((s5 shr 8) and 0xFF).toByte()

        p[36] = 0x04.toByte()
        p[37] = 0x01.toByte()

        // page_received at offset 38, 39
        p[38] = (pageReceived and 0xFF).toByte()
        p[39] = ((pageReceived shr 8) and 0xFF).toByte()

        // status[6] at offset 42, 43
        p[42] = (s6 and 0xFF).toByte()
        p[43] = ((s6 shr 8) and 0xFF).toByte()

        return p
    }
}
