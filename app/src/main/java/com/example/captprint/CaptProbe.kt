package com.example.captprint

// ORİJİNAL: agalakhov/captdriver (SPECS bölüm 1.1 - 1.3 ve capt-command.c)
// YENİ: Faz 1 için Android 512-bayt hizalı Bulk okuma ve ikili boyut çözümleme

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Canon CAPT protokolü tanılama ve paket alışverişi sınıfı.
 */
class CaptProbe(
    private val transport: CaptUsbTransport,
    private val onLog: (String) -> Unit
) {

    companion object {
        private const val TAG = "CaptUsb"

        // ORİJİNAL: capt-command.h
        const val CMD_GET_STATUS = 0xA0A1      // CHKJOBSTAT (Durum sorgusu)
        const val CMD_GET_EXT_STATUS = 0xA0A8  // CHKXSTATUS (Genişletilmiş durum sorgusu)
        const val CMD_GET_ID = 0xA1A0          // CAPT_IEEE_IDENT (IEEE-1284 Kimlik dizgesi)

        private val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    }

    private fun logLine(msg: String) {
        val timestamp = timeFormatter.format(Date())
        val fullMsg = "$timestamp $msg"
        Log.d(TAG, fullMsg)
        onLog(fullMsg)
    }

    private fun bytesToHex(bytes: ByteArray, length: Int = bytes.size): String {
        val sb = StringBuilder()
        for (i in 0 until minOf(length, bytes.size)) {
            sb.append("%02X ".format(bytes[i]))
        }
        return sb.toString().trimEnd()
    }

    private fun readWord(buf: ByteArray, offset: Int): Int {
        if (offset + 1 >= buf.size) return 0
        val lo = buf[offset].toInt() and 0xFF
        val hi = buf[offset + 1].toInt() and 0xFF
        return (hi shl 8) or lo
    }

    private fun flushPendingData(): Int {
        val flushBuf = ByteArray(512)
        var totalFlushed = 0
        while (true) {
            val n = transport.read(flushBuf, flushBuf.size, timeoutMs = 150)
            if (n <= 0) break
            totalFlushed += n
        }
        if (totalFlushed > 0) {
            logLine("FLUSH: Yazıcı tamponundan bekleyen $totalFlushed bayt temizlendi.")
        }
        return totalFlushed
    }

    /**
     * 16-bit little-endian komut paketini gönderir ve cevabı okur.
     */
    fun sendCommand(cmd: Int) {
        flushPendingData()

        // 4 baytlık paket: command(2B) + size=4(2B)
        val txPacket = ByteArray(4)
        txPacket[0] = (cmd and 0xFF).toByte()
        txPacket[1] = ((cmd shr 8) and 0xFF).toByte()
        txPacket[2] = 0x04
        txPacket[3] = 0x00

        logLine("TX 4B: ${bytesToHex(txPacket)}")

        val written = transport.write(txPacket, txPacket.size)
        if (written != txPacket.size) {
            logLine("HATA: Komut gönderilemedi (${transport.lastError})")
            return
        }

        // Android Bulk IN kuralı: Tampon daima maxPacketSize (512) olmalıdır
        val rxBuf = ByteArray(512)
        val read1 = transport.read(rxBuf, rxBuf.size, timeoutMs = 3000)
        if (read1 <= 0) {
            logLine("RX HATA: Yazıcıdan cevap gelmedi (-1 / zaman aşımı: ${transport.lastError})")
            return
        }

        val chunk1 = rxBuf.copyOf(read1)
        logLine("RX ${read1}B: ${bytesToHex(chunk1)}")

        if (read1 < 4) {
            logLine("UYARI: Cevap başlığı 4 bayttan kısa (${read1} bayt)")
            return
        }

        val respCmd = readWord(chunk1, 0)
        val expectedTotalSize = readWord(chunk1, 2) // Little-endian toplam bayt boyutu (Binary)

        if (respCmd != cmd) {
            logLine("UYARI: Beklenen komut 0x%04X idi, dönen: 0x%04X".format(cmd, respCmd))
        }

        var fullPacket = chunk1

        // Eğer beklenen paket boyutu ilk gelen 6 bayttan büyükse, kalanı 512 baytlık güvenli tamponla oku
        if (expectedTotalSize > fullPacket.size) {
            val rxNext = ByteArray(512)
            val read2 = transport.read(rxNext, rxNext.size, timeoutMs = 2000)
            if (read2 > 0) {
                val chunk2 = rxNext.copyOf(read2)
                logLine("RX ${read2}B (devamı): ${bytesToHex(chunk2)}")
                fullPacket = chunk1 + chunk2
            } else {
                logLine("UYARI: Kalan baytlar okunamadı (${transport.lastError})")
            }
        }

        // Komutlara göre veriyi çözümle
        when (cmd) {
            CMD_GET_ID -> {
                // Başlık sonrası gelen ASCII metin
                val textBytes = if (fullPacket.size > 4) fullPacket.copyOfRange(4, fullPacket.size) else fullPacket
                val asciiStr = String(textBytes, Charsets.ISO_8859_1).filter { it.code in 32..126 }
                logLine("KİMLİK (ASCII): $asciiStr")
            }
            CMD_GET_STATUS -> {
                if (fullPacket.size >= 6) {
                    val statusWord = readWord(fullPacket, 4)
                    logLine("DURUM (0xA0A1) -> 0x%04X (Toplam paket: %d bayt)".format(statusWord, fullPacket.size))
                }
            }
            CMD_GET_EXT_STATUS -> {
                parseExtendedStatus(fullPacket)
            }
        }
    }

    /**
     * 0xA0A8 Genişletilmiş Durum cevabını (56 bayt) çözümler.
     */
    // ORİJİNAL: agalakhov/captdriver src/capt-status.c (decode_status)
    private fun parseExtendedStatus(pkt: ByteArray) {
        if (pkt.size < 6) {
            logLine("DURUM AYRIŞTIRMA: Paket boyutu yetersiz (${pkt.size} B)")
            return
        }

        // Başlık 4 bayt: pkt[0..1]=cmd, pkt[2..3]=size, pkt[4..5]=STATUS0
        val status0 = readWord(pkt, 4)
        val sb0 = StringBuilder("  STATUS0 (0x%04X): ".format(status0))
        if ((status0 and 0x0001) != 0) sb0.append("[İşlem Sürüyor (b0)] ")
        if ((status0 and 0x0002) != 0) sb0.append("[KAĞIT YOK (b1)] ")
        if ((status0 and 0x0004) != 0) sb0.append("[TAMPON DOLU (b2)] ")
        if ((status0 and 0x0030) != 0) sb0.append("[Açılış/Hazır Değil (b4-5)] ")
        if ((status0 and 0x0080) != 0) sb0.append("[Meşgul (b7)] ")
        if ((status0 and 0x0100) != 0) sb0.append("[XSTATUS Değişti (b8)] ")
        logLine(sb0.toString())

        if (pkt.size >= 14) {
            val status1 = readWord(pkt, 12)
            val sb1 = StringBuilder("  STATUS1 (0x%04X): ".format(status1))
            if ((status1 and 0x0001) != 0) sb1.append("[PowerUp (b0)] ")
            if ((status1 and 0x0004) != 0) sb1.append("[Yazıyor (b2)] ")
            if ((status1 and 0x4000) != 0) sb1.append("[KAĞIT YOK (b14)] ")
            logLine(sb1.toString())
        }

        if (pkt.size >= 16) {
            val status2 = readWord(pkt, 14)
            val sb2 = StringBuilder("  STATUS2 (0x%04X): ".format(status2))
            if ((status2 and 0x0080) == 0) sb2.append("[HATA/nERROR (b7=0)] ")
            else sb2.append("[Hata Yok (b7=1)] ")
            if ((status2 and 0x0100) != 0) sb2.append("[Kağıt Düğmesi Basılı (b8)] ")
            logLine(sb2.toString())
        }

        if (pkt.size >= 26) {
            val pageDecoding = readWord(pkt, 18)
            val pagePrinting = readWord(pkt, 20)
            val pageOut = readWord(pkt, 22)
            val pageCompleted = readWord(pkt, 24)
            logLine("  SAYAÇLAR: Çözülen:$pageDecoding, Basılan:$pagePrinting, Çıkan:$pageOut, Biten:$pageCompleted")
        }
    }
}
