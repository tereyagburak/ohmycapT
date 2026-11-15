package com.example.captprint

// ORİJİNAL: Canon CAPT Linux Driver (agalakhov/captdriver) USB I/O mantığı
// YENİ: Android UsbManager / UsbDeviceConnection tabanlı taşıma katmanı

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Canon CAPT yazıcıları için Android USB OTG iletişim köprüsü.
 * Bölüm 7 API sözleşmesini birebir uygular.
 */
class CaptUsbTransport(context: Context) {

    companion object {
        private const val TAG = "CaptUsb"
        private const val ACTION_USB_PERMISSION = "com.example.captprint.USB_PERMISSION"

        // Canon LBP2900 / 3000 / 3100 / 6000 tanımlayıcıları
        const val CANON_VID = 0x04A9 // 1193
        const val LBP2900_PID = 0x2676 // 9846
        const val LBP3000_PID = 0x26B0 // 9904
        const val LBP3100_PID = 0x26CE // 9934
        const val LBP6000_PID = 0x271A // 10010
        const val USB_CLASS_PRINTER = 7

        val SUPPORTED_PIDS = setOf(LBP2900_PID, LBP3000_PID, LBP3100_PID, LBP6000_PID)

        private const val DEFAULT_TIMEOUT_MS = 3000
        private const val MAX_CHUNK_SIZE = 16384
    }

    private val appContext: Context = context.applicationContext
    private val usbManager: UsbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager

    private var connection: UsbDeviceConnection? = null
    private var claimedInterface: UsbInterface? = null
    private var endpointIn: UsbEndpoint? = null
    private var endpointOut: UsbEndpoint? = null

    var lastError: String = ""
        private set

    val isOpen: Boolean
        get() = (connection != null && claimedInterface != null)

    /**
     * Bağlı tüm USB cihazlarını ve ayrıntılı arayüz/uç nokta dökümünü listeler.
     */
    fun listDevices(): List<String> {
        val list = mutableListOf<String>()
        val deviceList = usbManager.deviceList

        if (deviceList.isEmpty()) {
            list.add("Bağlı hiçbir USB cihazı bulunamadı. OTG kablosunu ve yazıcının açık olduğunu kontrol edin.")
            return list
        }

        for ((name, device) in deviceList) {
            val isCanon = (device.vendorId == CANON_VID && device.productId == LBP2900_PID)
            val sb = StringBuilder()
            sb.appendLine("Cihaz: $name")
            sb.appendLine("  VID: 0x%04X (%d)".format(device.vendorId, device.vendorId))
            sb.appendLine("  PID: 0x%04X (%d)".format(device.productId, device.productId))
            sb.appendLine("  Sınıf (Class): ${device.deviceClass}, Alt Sınıf: ${device.deviceSubclass}, Protokol: ${device.deviceProtocol}")
            sb.appendLine("  İzin var mı?: ${usbManager.hasPermission(device)}")
            if (isCanon) {
                sb.appendLine("  -> HEDEF CANON LBP2900 YAZICI EŞLEŞTİ <-")
            }

            // Arayüz ve uç nokta dökümü
            val ifaceCount = device.interfaceCount
            sb.appendLine("  Arayüz sayısı: $ifaceCount")
            for (i in 0 until ifaceCount) {
                val iface = device.getInterface(i)
                sb.appendLine("    [Arayüz #$i] ID:${iface.id}, Sınıf:${iface.interfaceClass}, AltSınıf:${iface.interfaceSubclass}, Protokol:${iface.interfaceProtocol}, UçNoktaSayısı:${iface.endpointCount}")
                for (e in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(e)
                    val dirStr = if (ep.direction == UsbConstants.USB_DIR_IN) "IN" else "OUT"
                    val typeStr = when (ep.type) {
                        UsbConstants.USB_ENDPOINT_XFER_BULK -> "BULK"
                        UsbConstants.USB_ENDPOINT_XFER_INT -> "INTERRUPT"
                        UsbConstants.USB_ENDPOINT_XFER_CONTROL -> "CONTROL"
                        else -> "OTHER(${ep.type})"
                    }
                    sb.appendLine("      (UçNokta #$e) Adres:0x%02X, Yön:$dirStr, Tip:$typeStr, maxPacketSize:${ep.maxPacketSize}".format(ep.address))
                }
            }
            list.add(sb.toString().trimEnd())
        }
        return list
    }

    /**
     * VID:PID (Canon LBP2900/3000/3100/6000) veya Printer sınıfına göre hedef yazıcıyı bulur.
     */
    fun findPrinter(): UsbDevice? {
        val deviceList = usbManager.deviceList

        // 1. Öncelik: Tanımlı Canon CAPT VID ve PID eşleşmesi
        for (device in deviceList.values) {
            if (device.vendorId == CANON_VID && device.productId in SUPPORTED_PIDS) {
                Log.d(TAG, "Yazıcı Canon CAPT VID/PID ile bulundu: 0x%04X:0x%04X".format(device.vendorId, device.productId))
                return device
            }
        }

        // 2. Öncelik: Canon VID'e sahip herhangi bir cihaz
        for (device in deviceList.values) {
            if (device.vendorId == CANON_VID) {
                Log.d(TAG, "Yazıcı Canon VID ile bulundu: 0x%04X:0x%04X".format(device.vendorId, device.productId))
                return device
            }
        }

        // 3. Öncelik: USB Printer Class (7) arayüzüne sahip cihaz
        for (device in deviceList.values) {
            if (device.deviceClass == USB_CLASS_PRINTER) {
                Log.d(TAG, "Yazıcı DeviceClass ile bulundu: ${device.deviceName}")
                return device
            }
            for (i in 0 until device.interfaceCount) {
                if (device.getInterface(i).interfaceClass == USB_CLASS_PRINTER) {
                    Log.d(TAG, "Yazıcı InterfaceClass ile bulundu: ${device.deviceName}")
                    return device
                }
            }
        }

        lastError = "Canon LBP veya Printer sınıfında bir cihaz bulunamadı."
        return null
    }

    /**
     * Cihaz için USB izni olup olmadığını denetler.
     */
    fun hasPermission(device: UsbDevice): Boolean {
        return usbManager.hasPermission(device)
    }

    /**
     * USB erişim izni ister (Android 12+ ve 14+ kurallarına tam uyumlu).
     */
    fun requestPermission(device: UsbDevice, onResult: (Boolean) -> Unit) {
        if (hasPermission(device)) {
            onResult(true)
            return
        }

        val permissionIntent = Intent(ACTION_USB_PERMISSION).apply {
            // Android 14 kuralı: Mutable PendingIntent açık (explicit) bir paket içermelidir
            setPackage(appContext.packageName)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(appContext, 0, permissionIntent, flags)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_USB_PERMISSION) {
                    context?.unregisterReceiver(this)
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    Log.d(TAG, "USB İzin sonucu: $granted")
                    onResult(granted)
                }
            }
        }

        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(ACTION_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        Log.d(TAG, "USB İzin diyaloğu tetikleniyor...")
        usbManager.requestPermission(device, pendingIntent)
    }

    /**
     * Çift yönlü (IN ve OUT bulk uç noktaları olan) arayüzü seçip bağlantıyı açar.
     */
    fun open(device: UsbDevice): Boolean {
        close()

        if (!hasPermission(device)) {
            lastError = "Cihaz için USB izni verilmedi."
            Log.e(TAG, lastError)
            return false
        }

        var selectedIface: UsbInterface? = null
        var inEp: UsbEndpoint? = null
        var outEp: UsbEndpoint? = null

        // Bölüm 6 Kuralı: IN ve OUT bulk uç noktası olan alt ayarı/arayüzü bul
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            var currentIn: UsbEndpoint? = null
            var currentOut: UsbEndpoint? = null

            for (e in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(e)
                if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.direction == UsbConstants.USB_DIR_IN) {
                        currentIn = ep
                    } else if (ep.direction == UsbConstants.USB_DIR_OUT) {
                        currentOut = ep
                    }
                }
            }

            if (currentIn != null && currentOut != null) {
                selectedIface = iface
                inEp = currentIn
                outEp = currentOut
                break
            }
        }

        if (selectedIface == null || inEp == null || outEp == null) {
            lastError = "Hem Bulk IN hem Bulk OUT uç noktası içeren uygun bir arayüz bulunamadı."
            Log.e(TAG, lastError)
            return false
        }

        val conn = usbManager.openDevice(device)
        if (conn == null) {
            lastError = "UsbManager.openDevice başarısız oldu (bağlantı açılamadı)."
            Log.e(TAG, lastError)
            return false
        }

        // Arayüzü sahiplen (claim)
        if (!conn.claimInterface(selectedIface, true)) {
            conn.close()
            lastError = "claimInterface(iface: ${selectedIface.id}) başarısız oldu (başka servis/sürücü tutuyor olabilir)."
            Log.e(TAG, lastError)
            return false
        }

        // Alternatif ayarı etkinleştir
        try {
            conn.setInterface(selectedIface)
        } catch (e: Exception) {
            Log.w(TAG, "setInterface uyarısı (bazı cihazlarda desteklenmez): ${e.message}")
        }

        this.connection = conn
        this.claimedInterface = selectedIface
        this.endpointIn = inEp
        this.endpointOut = outEp

        Log.d(
            TAG,
            "Yazıcı bağlantısı açıldı: IN=0x%02X, OUT=0x%02X, maxPacketIn=%d, maxPacketOut=%d".format(
                inEp.address,
                outEp.address,
                inEp.maxPacketSize,
                outEp.maxPacketSize
            )
        )
        return true
    }

    /**
     * Yazıcıya bulk transfer ile veri yazar (Bölüm 7 sözleşmesi).
     * Parça boyutu <= 16384 bayt olacak şekilde parçalanarak gönderilir.
     */
    fun write(buf: ByteArray, len: Int): Int {
        val conn = connection ?: run {
            lastError = "Yazma hatası: Bağlantı açık değil."
            return -1
        }
        val outEp = endpointOut ?: run {
            lastError = "Yazma hatası: Bulk OUT uç noktası yok."
            return -1
        }

        if (len <= 0 || buf.isEmpty()) return 0

        var totalWritten = 0
        while (totalWritten < len) {
            val chunkSize = minOf(MAX_CHUNK_SIZE, len - totalWritten)
            val written = conn.bulkTransfer(outEp, buf, totalWritten, chunkSize, DEFAULT_TIMEOUT_MS)
            if (written < 0) {
                lastError = "bulkTransfer yazma hatası (-1). Yazıcı kilitlenmiş veya kablo çıkmış olabilir."
                Log.e(TAG, lastError)
                return -1
            }
            totalWritten += written
        }

        return totalWritten
    }

    /**
     * Yazıcıdan bulk transfer ile veri okur (Bölüm 7 sözleşmesi).
     */
    fun read(buf: ByteArray, maxLen: Int): Int {
        return read(buf, maxLen, DEFAULT_TIMEOUT_MS)
    }

    /**
     * Özel zaman aşımlı okuma (örn. bekleyen veriyi 200 ms ile boşaltmak için).
     */
    fun read(buf: ByteArray, maxLen: Int, timeoutMs: Int): Int {
        val conn = connection ?: run {
            lastError = "Okuma hatası: Bağlantı açık değil."
            return -1
        }
        val inEp = endpointIn ?: run {
            lastError = "Okuma hatası: Bulk IN uç noktası yok."
            return -1
        }

        if (maxLen <= 0 || buf.isEmpty()) return 0

        // Bölüm 6 Kuralı: Okuma tamponu maxPacketSize'dan küçük olmamalıdır
        if (maxLen < inEp.maxPacketSize) {
            Log.w(TAG, "UYARI: Okuma tamponu (${maxLen}) uç nokta maxPacketSize'dan (${inEp.maxPacketSize}) küçük!")
        }

        val bytesRead = conn.bulkTransfer(inEp, buf, 0, maxLen, timeoutMs)
        if (bytesRead < 0) {
            lastError = "bulkTransfer okuma zaman aşımı veya hatası (-1)."
        }
        return bytesRead
    }

    /**
     * Arayüzü bırakır ve USB bağlantısını kapatır.
     */
    fun close() {
        try {
            if (claimedInterface != null && connection != null) {
                connection?.releaseInterface(claimedInterface)
            }
            connection?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Kapatma sırasında hata: ${e.message}")
        } finally {
            connection = null
            claimedInterface = null
            endpointIn = null
            endpointOut = null
        }
    }
}
