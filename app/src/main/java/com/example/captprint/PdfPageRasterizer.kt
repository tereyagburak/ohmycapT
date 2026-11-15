package com.example.captprint

// YENİ: PDF, Görüntü (JPEG/PNG/WebP vb.) Bant Tabanlı Render, Dither ve Yazdırma Hattı

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class PdfPageRasterizer(
    private val context: Context,
    private val captNative: CaptNative
) {
    companion object {
        private const val TAG = "CaptNative"
        const val LBP2900_WIDTH_BYTES = 592
        const val LBP2900_PIXEL_WIDTH = 4736 // 592 * 8 (A4 genişlik @ 600 DPI)
        const val LBP2900_PAGE_LINES = 7016  // A4 yükseklik @ 600 DPI
        const val BAND_HEIGHT = 70          // Bant başına satır sayısı
    }

    /**
     * Geriye uyumluluk için tek parametreli progress alan sürüm (PrintService için)
     */
    fun printUri(uri: Uri, invert: Boolean, tonerDensity: Int = 7, copies: Int = 1, onProgress: (String) -> Unit): Boolean = runBlocking {
        printUriAdvanced(
            uri = uri,
            invert = invert,
            tonerDensity = tonerDensity,
            copies = copies,
            onPageFinished = null,
            onProgress = { _, _, _, msg -> onProgress(msg) }
        )
    }

    /**
     * Gelişmiş yazdırma motoru: Her sayfadan sonra duraklama (onPageFinished) ve detaylı ilerleme desteği
     */
    suspend fun printUriAdvanced(
        uri: Uri,
        invert: Boolean,
        tonerDensity: Int = 7,
        copies: Int = 1,
        onPageFinished: (suspend (pageIndex: Int, totalPages: Int) -> Unit)? = null,
        onProgress: (page: Int, total: Int, percent: Float, message: String) -> Unit
    ): Boolean {
        val mimeType = context.contentResolver.getType(uri) ?: ""
        Log.d(TAG, "printUri tetiklendi. MIME: $mimeType, URI: $uri")

        // Resim dosyası ise (image/jpeg, image/png vb.) geçici bir PDF'e dönüştür
        if (mimeType.startsWith("image/") || isImageUri(uri)) {
            onProgress(1, 1, 0.1f, "Resim formatı algılandı, A4 600 DPI yazdırılabilir belgeye dönüştürülüyor...")
            var tempPdf: File? = null
            var pfd: ParcelFileDescriptor? = null
            try {
                tempPdf = convertImageToPdf(uri)
                if (tempPdf == null) {
                    onProgress(1, 1, 0f, "HATA: Resim PDF formatına dönüştürülemedi.")
                    return false
                }
                pfd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
                return printFromParcelFdAdvanced(pfd, invert, tonerDensity, copies, onPageFinished, onProgress)
            } finally {
                try { pfd?.close() } catch (ignored: Exception) {}
                try { tempPdf?.delete() } catch (ignored: Exception) {}
            }
        }

        // Doğrudan PDF dosyası
        var pfd: ParcelFileDescriptor? = null
        try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IllegalStateException("Belge açılamadı: $uri")
            return printFromParcelFdAdvanced(pfd, invert, tonerDensity, copies, onPageFinished, onProgress)
        } finally {
            try { pfd?.close() } catch (ignored: Exception) {}
        }
    }

    fun isImageUri(uri: Uri): Boolean {
        val mimeType = context.contentResolver.getType(uri) ?: ""
        if (mimeType.startsWith("image/")) return true
        val path = uri.path?.lowercase() ?: ""
        return path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") ||
               path.endsWith(".webp") || path.endsWith(".bmp")
    }

    /**
     * Görseli A4 boyutuna oran koruyarak yerleştirir ve geçici PDF oluşturur.
     */
    fun convertImageToPdf(imageUri: Uri): File? {
        var inputStream: InputStream? = null
        var originalBitmap: Bitmap? = null
        val pdfDoc = PdfDocument()

        try {
            inputStream = context.contentResolver.openInputStream(imageUri) ?: return null
            originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return null

            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDoc.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            canvas.drawColor(Color.WHITE)

            val imgW = originalBitmap.width.toFloat()
            val imgH = originalBitmap.height.toFloat()
            val targetW = 595f - 40f
            val targetH = 842f - 40f

            val scale = minOf(targetW / imgW, targetH / imgH)
            val drawW = imgW * scale
            val drawH = imgH * scale

            val left = (595f - drawW) / 2f
            val top = (842f - drawH) / 2f

            val matrix = Matrix().apply {
                postScale(scale, scale)
                postTranslate(left, top)
            }

            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(originalBitmap, matrix, paint)
            pdfDoc.finishPage(page)

            val tempFile = File.createTempFile("img_print_${System.currentTimeMillis()}", ".pdf", context.cacheDir)
            FileOutputStream(tempFile).use { out ->
                pdfDoc.writeTo(out)
            }
            return tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Görsel PDF dönüştürme hatası: ${e.message}", e)
            return null
        } finally {
            try { inputStream?.close() } catch (ignored: Exception) {}
            originalBitmap?.recycle()
            try { pdfDoc.close() } catch (ignored: Exception) {}
        }
    }

    /**
     * Geriye uyumluluk için printFromParcelFd
     */
    fun printFromParcelFd(
        pfd: ParcelFileDescriptor,
        invert: Boolean,
        tonerDensity: Int = 7,
        copies: Int = 1,
        onProgress: (String) -> Unit
    ): Boolean = runBlocking {
        printFromParcelFdAdvanced(
            pfd = pfd,
            invert = invert,
            tonerDensity = tonerDensity,
            copies = copies,
            onPageFinished = null,
            onProgress = { _, _, _, msg -> onProgress(msg) }
        )
    }

    /**
     * ParcelFileDescriptor üzerinden sayfa sayfa (her sayfa sonrası duraklama destekli) yazar.
     */
    suspend fun printFromParcelFdAdvanced(
        pfd: ParcelFileDescriptor,
        invert: Boolean,
        tonerDensity: Int = 7,
        copies: Int = 1,
        onPageFinished: (suspend (pageIndex: Int, totalPages: Int) -> Unit)? = null,
        onProgress: (page: Int, total: Int, percent: Float, message: String) -> Unit
    ): Boolean {
        var renderer: PdfRenderer? = null
        try {
            renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount
            val totalCopies = maxOf(1, copies)
            onProgress(1, pageCount, 0.05f, "Belge Hazır: $pageCount sayfa, $totalCopies kopya.")

            for (copy in 1..totalCopies) {
                if (totalCopies > 1) {
                    onProgress(1, pageCount, 0.05f, "Kopya $copy / $totalCopies basılıyor...")
                }

                for (i in 0 until pageCount) {
                    val progressPercent = i.toFloat() / pageCount.toFloat()
                    onProgress(i + 1, pageCount, progressPercent, "Sayfa ${i + 1} / $pageCount dither ediliyor...")
                    val page = renderer.openPage(i)

                    val beginRes = captNative.nativeStartPage(
                        widthBytes = LBP2900_WIDTH_BYTES,
                        heightLines = LBP2900_PAGE_LINES,
                        tonerDensity = tonerDensity,
                        invert = invert
                    )
                    if (beginRes != 0) {
                        onProgress(i + 1, pageCount, progressPercent, "HATA: nativeStartPage başarısız: $beginRes")
                        page.close()
                        return false
                    }

                    val bandBitmap = Bitmap.createBitmap(LBP2900_PIXEL_WIDTH, BAND_HEIGHT, Bitmap.Config.ARGB_8888)

                    val scaleX = LBP2900_PIXEL_WIDTH.toFloat() / page.width.toFloat()
                    val scaleY = LBP2900_PAGE_LINES.toFloat() / page.height.toFloat()
                    val scale = minOf(scaleX, scaleY)

                    val scaledW = page.width.toFloat() * scale
                    val offsetX = (LBP2900_PIXEL_WIDTH - scaledW) / 2f

                    val totalLines = LBP2900_PAGE_LINES
                    var currentLine = 0

                    while (currentLine < totalLines) {
                        val linesInBand = if (currentLine + BAND_HEIGHT > totalLines) totalLines - currentLine else BAND_HEIGHT
                        val isLast = (currentLine + linesInBand >= totalLines)

                        bandBitmap.eraseColor(Color.WHITE)

                        val matrix = Matrix().apply {
                            postScale(scale, scale)
                            postTranslate(offsetX, -currentLine.toFloat())
                        }

                        page.render(bandBitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                        val bandRes = captNative.nativeDitherAndPrintBand(
                            bitmap = bandBitmap,
                            lines = linesInBand,
                            isLast = isLast,
                            invert = invert
                        )

                        if (bandRes != 0) {
                            onProgress(i + 1, pageCount, progressPercent, "HATA: Bant transfer hatası: $bandRes")
                            bandBitmap.recycle()
                            page.close()
                            return false
                        }

                        currentLine += linesInBand
                    }

                    bandBitmap.recycle()
                    page.close()

                    onProgress(i + 1, pageCount, (i + 0.8f) / pageCount.toFloat(), "Sayfa ${i + 1} yazıcıya aktarıldı, kağıt sürülüyor...")
                    val endRes = captNative.nativeEndPage()
                    if (endRes != 0) {
                        onProgress(i + 1, pageCount, progressPercent, "HATA: nativeEndPage başarısız: $endRes")
                        return false
                    }

                    onProgress(i + 1, pageCount, (i + 1).toFloat() / pageCount.toFloat(), "Sayfa ${i + 1} başarıyla basıldı!")

                    // TEK TEK KAĞIT ONAYI BEKLEME (Son sayfa değilse kullanıcı onayı beklenir)
                    if (onPageFinished != null) {
                        onPageFinished(i, pageCount)
                    }
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Yazdırma istisnası: ${e.message}", e)
            onProgress(1, 1, 0f, "Yazdırma Hatası: ${e.message}")
            return false
        } finally {
            try { renderer?.close() } catch (ignored: Exception) {}
        }
    }
}
