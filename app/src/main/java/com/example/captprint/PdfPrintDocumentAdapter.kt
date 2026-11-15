package com.example.captprint

// YENİ: Android Resmi Yazdırma Sistemi için PrintDocumentAdapter

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.util.Log
import java.io.FileOutputStream
import java.io.InputStream

class PdfPrintDocumentAdapter(
    private val context: Context,
    private val pdfUri: Uri,
    private val documentName: String
) : PrintDocumentAdapter() {

    companion object {
        private const val TAG = "PdfPrintAdapter"
    }

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }

        val info = PrintDocumentInfo.Builder(documentName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()

        val changed = (newAttributes != oldAttributes)
        callback?.onLayoutFinished(info, changed)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        if (destination == null) {
            callback?.onWriteFailed("Hedef dosya tanımsız")
            return
        }

        var input: InputStream? = null
        var output: FileOutputStream? = null

        try {
            input = context.contentResolver.openInputStream(pdfUri)
            if (input == null) {
                callback?.onWriteFailed("Belge kaynağı açılamadı: $pdfUri")
                return
            }

            output = FileOutputStream(destination.fileDescriptor)
            val buffer = ByteArray(16384)
            var bytesRead: Int

            while (input.read(buffer).also { bytesRead = it } >= 0) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onWriteCancelled()
                    return
                }
                output.write(buffer, 0, bytesRead)
            }

            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            Log.d(TAG, "Belge verisi Android Yazdırma Kuyruğuna başarıyla yazıldı.")
        } catch (e: Exception) {
            Log.e(TAG, "onWrite hatası: ${e.message}", e)
            callback?.onWriteFailed(e.message)
        } finally {
            try { input?.close() } catch (ignored: Exception) {}
            try { output?.close() } catch (ignored: Exception) {}
        }
    }
}
