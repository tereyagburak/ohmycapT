package com.example.captprint

// YENİ: Android PrintService - Sistem Geneli Arka Plan Yazdırma Motoru

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrinterCapabilitiesInfo
import android.print.PrinterId
import android.print.PrinterInfo
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.captprint.audio.PrintSoundManager
import java.io.File
import java.io.FileOutputStream

class CaptPrintService : PrintService() {

    companion object {
        private const val TAG = "CaptPrintService"
        const val PRINTER_ID_CANON = "canon_lbp2900_usb"
        private const val CHANNEL_ID = "capt_print_channel"
        private const val NOTIFICATION_ID = 2900
    }

    private lateinit var handlerThread: HandlerThread
    private lateinit var serviceHandler: Handler
    private var transport: CaptUsbTransport? = null
    private var captNative: CaptNative? = null
    private var rasterizer: PdfPageRasterizer? = null
    private var ownSoundManager: PrintSoundManager? = null

    private val soundManager: PrintSoundManager
        get() = (application as? CaptApplication)?.soundManager
            ?: ownSoundManager
            ?: PrintSoundManager(this).also { ownSoundManager = it }

    override fun onCreate() {
        super.onCreate()
        handlerThread = HandlerThread("CaptPrintServiceWorker").apply { start() }
        serviceHandler = Handler(handlerThread.looper)
        transport = CaptUsbTransport(this)
        captNative = CaptNative()
        rasterizer = PdfPageRasterizer(this, captNative!!)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "CAPT Yazdırma Bildirimleri"
            val descriptionText = "Yazıcı durumu ve yazdırma bildirimleri"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showServiceNotification(title: String, message: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun getCanonPrinterInfo(): PrinterInfo {
        val printerId = generatePrinterId(PRINTER_ID_CANON)
        val caps = PrinterCapabilitiesInfo.Builder(printerId)
            .addMediaSize(PrintAttributes.MediaSize.ISO_A4, true)
            .addMediaSize(PrintAttributes.MediaSize.NA_LETTER, false)
            .addResolution(PrintAttributes.Resolution("600dpi", "600 DPI", 600, 600), true)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setColorModes(
                PrintAttributes.COLOR_MODE_COLOR or PrintAttributes.COLOR_MODE_MONOCHROME,
                PrintAttributes.COLOR_MODE_MONOCHROME
            )
            .setDuplexModes(PrintAttributes.DUPLEX_MODE_NONE, PrintAttributes.DUPLEX_MODE_NONE)
            .build()

        return PrinterInfo.Builder(printerId, "Canon LBP2900 / LBP3000 (CAPT USB)", PrinterInfo.STATUS_IDLE)
            .setDescription("Canon LBP USB OTG Lazer Yazıcı")
            .setCapabilities(caps)
            .build()
    }

    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession {
        return object : PrinterDiscoverySession() {

            private val usbReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        UsbManager.ACTION_USB_DEVICE_ATTACHED,
                        UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                            Log.d(TAG, "USB durum değişikliği yakalandı, yazıcı listesi güncelleniyor...")
                            val printer = getCanonPrinterInfo()
                            addPrinters(listOf(printer))
                        }
                    }
                }
            }

            override fun onStartPrinterDiscovery(priorityList: MutableList<PrinterId>) {
                Log.d(TAG, "onStartPrinterDiscovery tetiklendi.")
                val filter = IntentFilter().apply {
                    addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                    addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                }
                ContextCompat.registerReceiver(
                    this@CaptPrintService,
                    usbReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )

                val printer = getCanonPrinterInfo()
                addPrinters(listOf(printer))
                Log.d(TAG, "Canon LBP sistem yazdırma listesine eklendi (Durum: ${printer.status})")
            }

            override fun onStopPrinterDiscovery() {
                Log.d(TAG, "onStopPrinterDiscovery tetiklendi.")
                try {
                    unregisterReceiver(usbReceiver)
                } catch (ignored: Exception) {}
            }

            override fun onValidatePrinters(printerIds: MutableList<PrinterId>) {
                val printer = getCanonPrinterInfo()
                if (printerIds.any { it == printer.id }) {
                    addPrinters(listOf(printer))
                }
            }

            override fun onStartPrinterStateTracking(printerId: PrinterId) {
                val printer = getCanonPrinterInfo()
                if (printerId == printer.id) {
                    addPrinters(listOf(printer))
                }
            }

            override fun onStopPrinterStateTracking(printerId: PrinterId) {}

            override fun onDestroy() {
                try {
                    unregisterReceiver(usbReceiver)
                } catch (ignored: Exception) {}
            }
        }
    }

    override fun onPrintJobQueued(printJob: PrintJob) {
        Log.d(TAG, "Yazdırma işi sıraya alındı: ${printJob.id}")
        if (printJob.isQueued) {
            printJob.start()
        }

        serviceHandler.post {
            processPrintJob(printJob)
        }
    }

    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        Log.d(TAG, "Yazdırma işi iptali istendi: ${printJob.id}")
        captNative?.nativeCancel()
        printJob.cancel()
    }

    private fun processPrintJob(printJob: PrintJob) {
        val trans = transport ?: return
        val printer = trans.findPrinter()

        if (printer == null) {
            Log.e(TAG, "Yazıcı donanımı bulunamadı")
            soundManager.playWarning()
            printJob.block("Canon LBP yazıcı bağlı bulunamadı. Lütfen OTG kablosunu kontrol edin.")
            showServiceNotification(
                "Yazıcı Bulunamadı",
                "Lütfen Canon yazıcınızın açık ve OTG kablosunun bağlı olduğundan emin olun."
            )
            return
        }

        if (!trans.hasPermission(printer)) {
            Log.w(TAG, "Yazıcı için USB izni henüz verilmemiş")
            soundManager.playWarning()
            printJob.block("USB İzni Bekleniyor. Lütfen ohmycapT uygulamasını açarak izin verin.")
            showServiceNotification(
                "Yazıcı İzni Gerekli",
                "Yazdırma işlemini tamamlamak için dokunup USB iznini onaylayın."
            )
            return
        }

        if (!trans.open(printer)) {
            val err = trans.lastError
            Log.e(TAG, "Yazıcı bağlantısı açılamadı: $err")
            soundManager.playPrintError()
            printJob.fail("Yazıcı bağlantısı açılamadı: $err")
            return
        }

        captNative?.nativeOpen(trans)

        var tempPdfFile: File? = null
        var localPfd: ParcelFileDescriptor? = null

        try {
            val doc = printJob.document
            val pfd = doc?.data
            if (pfd == null) {
                soundManager.playPrintError()
                printJob.fail("Yazdırılacak belge verisi alınamadı.")
                return
            }

            printJob.setStatus("Belge taranıyor ve hazırlanıyor...")
            tempPdfFile = File.createTempFile("spool_${System.currentTimeMillis()}", ".pdf", cacheDir)

            // AutoCloseInputStream ile descriptor'ı güvenli şekilde kopyala
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                FileOutputStream(tempPdfFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (!tempPdfFile.exists() || tempPdfFile.length() == 0L) {
                soundManager.playPrintError()
                printJob.fail("Yazdırılacak belge verisi boş.")
                return
            }

            localPfd = ParcelFileDescriptor.open(tempPdfFile, ParcelFileDescriptor.MODE_READ_ONLY)

            val copies = try {
                printJob.info.copies
            } catch (e: Exception) {
                1
            }

            val success = rasterizer?.printFromParcelFd(localPfd, false, 7, copies) { progress ->
                Log.d(TAG, "Kuyruk İlerleme: $progress")
                printJob.setStatus(progress)
            } ?: false

            if (success) {
                printJob.complete()
                soundManager.playPrintDone()
                Log.d(TAG, "Yazdırma işi başarıyla tamamlandı: ${printJob.id}")
                showServiceNotification("Yazdırma Tamamlandı", "Belge başarıyla yazdırıldı.")
            } else {
                soundManager.playPrintError()
                printJob.fail("Yazdırma sırasında bir hata oluştu.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "İş yürütme hatası: ${e.message}", e)
            soundManager.playPrintError()
            printJob.fail("Yazdırma hatası: ${e.message}")
        } finally {
            try { localPfd?.close() } catch (ignored: Exception) {}
            try { tempPdfFile?.delete() } catch (ignored: Exception) {}
            captNative?.nativeClose()
            trans.close()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ownSoundManager?.release()
        captNative?.nativeClose()
        transport?.close()
        handlerThread.quitSafely()
    }
}
