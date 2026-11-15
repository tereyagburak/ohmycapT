package com.example.captprint.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import com.example.captprint.domain.model.DitherMethod
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class PreparedDocument(
    val title: String,
    val pageCount: Int,
    val pdfFile: File,
    val pfd: ParcelFileDescriptor
)

object DocumentPreparationEngine {
    private const val TAG = "DocPrepEngine"

    // A4 PostScript Points = 595 x 842 pt (Standard A4 @ 72 dpi)
    private const val A4_WIDTH_PT = 595
    private const val A4_HEIGHT_PT = 842

    fun prepareDocument(context: Context, uris: List<Uri>): PreparedDocument? {
        if (uris.isEmpty()) return null

        if (uris.size == 1) {
            val uri = uris.first()
            val mimeType = context.contentResolver.getType(uri) ?: ""
            val name = getFileName(context, uri) ?: "Belge"
            val lowerName = name.lowercase()

            when {
                mimeType == "application/pdf" || lowerName.endsWith(".pdf") -> {
                    val cachePdf = copyUriToCache(context, uri, "original.pdf") ?: return null
                    val pfd = ParcelFileDescriptor.open(cachePdf, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    val pages = renderer.pageCount
                    renderer.close()
                    val finalPfd = ParcelFileDescriptor.open(cachePdf, ParcelFileDescriptor.MODE_READ_ONLY)
                    return PreparedDocument(name, pages, cachePdf, finalPfd)
                }
                lowerName.endsWith(".docx") || lowerName.endsWith(".doc") -> {
                    return convertDocxToPdf(context, uri, name)
                }
                lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls") -> {
                    return convertXlsxToPdf(context, uri, name)
                }
                lowerName.endsWith(".pptx") || lowerName.endsWith(".ppt") -> {
                    return convertPptxToPdf(context, uri, name)
                }
                lowerName.endsWith(".txt") || mimeType.startsWith("text/") -> {
                    return convertTxtToPdf(context, uri, name)
                }
                else -> {
                    return convertImagesToPdf(context, listOf(uri), name)
                }
            }
        } else {
            // Çoklu dosya seçimi: PDF, DOCX, Resim her şeyi tek bir A4 PDF dosyasında birleştir
            return mergeMultipleUrisToPdf(context, uris)
        }
    }

    private fun mergeMultipleUrisToPdf(context: Context, uris: List<Uri>): PreparedDocument? {
        val pdfDoc = PdfDocument()
        val tempPdf = File.createTempFile("merged_docs_", ".pdf", context.cacheDir)
        var globalPageIndex = 1

        try {
            for (uri in uris) {
                val name = getFileName(context, uri) ?: "Dosya"
                val mimeType = context.contentResolver.getType(uri) ?: ""
                val lowerName = name.lowercase()

                when {
                    mimeType == "application/pdf" || lowerName.endsWith(".pdf") -> {
                        val cachePdf = copyUriToCache(context, uri, "part.pdf") ?: continue
                        val pfd = ParcelFileDescriptor.open(cachePdf, ParcelFileDescriptor.MODE_READ_ONLY)
                        val renderer = PdfRenderer(pfd)
                        for (i in 0 until renderer.pageCount) {
                            val page = renderer.openPage(i)
                            val w = page.width * 2
                            val h = page.height * 2
                            val pageBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            pageBmp.eraseColor(Color.WHITE)
                            page.render(pageBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            page.close()

                            val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, globalPageIndex).create()
                            val newPage = pdfDoc.startPage(pageInfo)
                            val canvas = newPage.canvas
                            canvas.drawColor(Color.WHITE)

                            val scale = minOf(A4_WIDTH_PT.toFloat() / pageBmp.width.toFloat(), A4_HEIGHT_PT.toFloat() / pageBmp.height.toFloat())
                            val matrix = Matrix().apply {
                                postScale(scale, scale)
                                postTranslate((A4_WIDTH_PT - pageBmp.width * scale) / 2f, (A4_HEIGHT_PT - pageBmp.height * scale) / 2f)
                            }
                            canvas.drawBitmap(pageBmp, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
                            pdfDoc.finishPage(newPage)
                            pageBmp.recycle()
                            globalPageIndex++
                        }
                        renderer.close()
                        pfd.close()
                        cachePdf.delete()
                    }
                    lowerName.endsWith(".docx") || lowerName.endsWith(".doc") -> {
                        val subDoc = convertDocxToPdf(context, uri, name)
                        if (subDoc != null) {
                            globalPageIndex = appendPdfToDoc(subDoc.pdfFile, pdfDoc, globalPageIndex)
                            subDoc.pdfFile.delete()
                        }
                    }
                    lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls") -> {
                        val subDoc = convertXlsxToPdf(context, uri, name)
                        if (subDoc != null) {
                            globalPageIndex = appendPdfToDoc(subDoc.pdfFile, pdfDoc, globalPageIndex)
                            subDoc.pdfFile.delete()
                        }
                    }
                    lowerName.endsWith(".pptx") || lowerName.endsWith(".ppt") -> {
                        val subDoc = convertPptxToPdf(context, uri, name)
                        if (subDoc != null) {
                            globalPageIndex = appendPdfToDoc(subDoc.pdfFile, pdfDoc, globalPageIndex)
                            subDoc.pdfFile.delete()
                        }
                    }
                    lowerName.endsWith(".txt") || mimeType.startsWith("text/") -> {
                        val subDoc = convertTxtToPdf(context, uri, name)
                        if (subDoc != null) {
                            globalPageIndex = appendPdfToDoc(subDoc.pdfFile, pdfDoc, globalPageIndex)
                            subDoc.pdfFile.delete()
                        }
                    }
                    else -> {
                        // Görsel
                        val inputStream = context.contentResolver.openInputStream(uri) ?: continue
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream.close()
                        if (bitmap != null) {
                            val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, globalPageIndex).create()
                            val page = pdfDoc.startPage(pageInfo)
                            val canvas = page.canvas
                            canvas.drawColor(Color.WHITE)

                            val targetW = A4_WIDTH_PT - 40f
                            val targetH = A4_HEIGHT_PT - 40f
                            val scale = minOf(targetW / bitmap.width.toFloat(), targetH / bitmap.height.toFloat())
                            val matrix = Matrix().apply {
                                postScale(scale, scale)
                                postTranslate((A4_WIDTH_PT - bitmap.width * scale) / 2f, (A4_HEIGHT_PT - bitmap.height * scale) / 2f)
                            }
                            canvas.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
                            pdfDoc.finishPage(page)
                            bitmap.recycle()
                            globalPageIndex++
                        }
                    }
                }
            }

            if (globalPageIndex == 1) {
                return null
            }

            FileOutputStream(tempPdf).use { pdfDoc.writeTo(it) }
            val finalPfd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
            val title = "${uris.size} Öğe Birleşimi"
            return PreparedDocument(title, globalPageIndex - 1, tempPdf, finalPfd)
        } catch (e: Exception) {
            Log.e(TAG, "Çoklu dosya birleştirme hatası: ${e.message}", e)
            return null
        } finally {
            try { pdfDoc.close() } catch (ignored: Exception) {}
        }
    }

    private fun appendPdfToDoc(sourceFile: File, targetPdf: PdfDocument, startIdx: Int): Int {
        var idx = startIdx
        try {
            val pfd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val w = page.width * 2
                val h = page.height * 2
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, idx).create()
                val newPage = targetPdf.startPage(pageInfo)
                val canvas = newPage.canvas
                canvas.drawColor(Color.WHITE)

                val scale = minOf(A4_WIDTH_PT.toFloat() / bmp.width.toFloat(), A4_HEIGHT_PT.toFloat() / bmp.height.toFloat())
                val matrix = Matrix().apply {
                    postScale(scale, scale)
                    postTranslate((A4_WIDTH_PT - bmp.width * scale) / 2f, (A4_HEIGHT_PT - bmp.height * scale) / 2f)
                }
                canvas.drawBitmap(bmp, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
                targetPdf.finishPage(newPage)
                bmp.recycle()
                idx++
            }
            renderer.close()
            pfd.close()
        } catch (e: Exception) {
            Log.e(TAG, "appendPdfToDoc hatası: ${e.message}", e)
        }
        return idx
    }

    fun convertImagesToPdf(context: Context, uris: List<Uri>, title: String): PreparedDocument? {
        val pdfDoc = PdfDocument()
        val tempPdf = File.createTempFile("images_bundle_", ".pdf", context.cacheDir)

        try {
            for ((index, uri) in uris.withIndex()) {
                val inputStream = context.contentResolver.openInputStream(uri) ?: continue
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                if (bitmap == null) continue

                val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, index + 1).create()
                val page = pdfDoc.startPage(pageInfo)
                val canvas: Canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                val targetW = A4_WIDTH_PT - 40f
                val targetH = A4_HEIGHT_PT - 40f
                val scale = minOf(targetW / bitmap.width.toFloat(), targetH / bitmap.height.toFloat())

                val drawW = bitmap.width.toFloat() * scale
                val drawH = bitmap.height.toFloat() * scale
                val left = (A4_WIDTH_PT - drawW) / 2f
                val top = (A4_HEIGHT_PT - drawH) / 2f

                val matrix = Matrix().apply {
                    postScale(scale, scale)
                    postTranslate(left, top)
                }

                canvas.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
                pdfDoc.finishPage(page)
                bitmap.recycle()
            }

            FileOutputStream(tempPdf).use { pdfDoc.writeTo(it) }
            val pfd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            renderer.close()
            val finalPfd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
            return PreparedDocument(title, count, tempPdf, finalPfd)
        } catch (e: Exception) {
            Log.e(TAG, "Görsel PDF dönüştürme hatası: ${e.message}", e)
            return null
        } finally {
            try { pdfDoc.close() } catch (ignored: Exception) {}
        }
    }

    fun convertTxtToPdf(context: Context, uri: Uri, title: String): PreparedDocument? {
        val pdfDoc = PdfDocument()
        val tempPdf = File.createTempFile("txt_print_", ".pdf", context.cacheDir)

        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val reader = BufferedReader(InputStreamReader(inputStream))
            val lines = mutableListOf<String>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                lines.add(line ?: "")
            }
            reader.close()

            return renderLinesToPdf(context, lines, title)
        } catch (e: Exception) {
            Log.e(TAG, "Metin PDF dönüştürme hatası: ${e.message}", e)
            return null
        } finally {
            try { pdfDoc.close() } catch (ignored: Exception) {}
        }
    }

    fun convertDocxToPdf(context: Context, uri: Uri, title: String): PreparedDocument? {
        val extractedLines = mutableListOf<String>()

        try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: return null
            val zipInputStream = ZipInputStream(inputStream)
            var entry: ZipEntry? = zipInputStream.nextEntry

            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xmlContent = zipInputStream.bufferedReader().readText()
                    val pRegex = Regex("<w:p[^>]*>(.*?)</w:p>")
                    val tRegex = Regex("<w:t[^>]*>(.*?)</w:t>")

                    val pMatches = pRegex.findAll(xmlContent)
                    for (p in pMatches) {
                        val sb = StringBuilder()
                        val tMatches = tRegex.findAll(p.value)
                        for (t in tMatches) {
                            sb.append(t.groupValues[1])
                        }
                        val text = sb.toString().trim()
                        if (text.isNotEmpty()) {
                            extractedLines.add(text)
                        } else {
                            extractedLines.add("")
                        }
                    }
                    break
                }
                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }
            zipInputStream.close()

            if (extractedLines.isEmpty()) {
                extractedLines.add("(Boş Word Belgesi)")
            }

            return renderLinesToPdf(context, extractedLines, title)
        } catch (e: Exception) {
            Log.e(TAG, "DOCX PDF dönüştürme hatası: ${e.message}", e)
            return null
        }
    }

    fun convertXlsxToPdf(context: Context, uri: Uri, title: String): PreparedDocument? {
        val sharedStrings = mutableListOf<String>()
        val rowLines = mutableListOf<String>()

        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null

            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == "xl/sharedStrings.xml") {
                        val content = zis.bufferedReader().readText()
                        val tRegex = Regex("<t[^>]*>(.*?)</t>")
                        for (m in tRegex.findAll(content)) {
                            sharedStrings.add(m.groupValues[1])
                        }
                        break
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.startsWith("xl/worksheets/sheet1.xml") || entry.name.startsWith("xl/worksheets/sheet")) {
                        val content = zis.bufferedReader().readText()
                        val rowRegex = Regex("<row[^>]*>(.*?)</row>")
                        val cellRegex = Regex("<c[^>]*?(?:t=\"(s)\")?[^>]*>(?:<v>(.*?)</v>)?</c>")

                        for (r in rowRegex.findAll(content)) {
                            val cells = mutableListOf<String>()
                            for (c in cellRegex.findAll(r.value)) {
                                val isShared = c.groupValues[1] == "s"
                                val rawVal = c.groupValues[2]
                                val cellVal = if (isShared) {
                                    val idx = rawVal.toIntOrNull() ?: -1
                                    if (idx in 0 until sharedStrings.size) sharedStrings[idx] else rawVal
                                } else {
                                    rawVal
                                }
                                if (cellVal.isNotEmpty()) {
                                    cells.add(cellVal)
                                }
                            }
                            if (cells.isNotEmpty()) {
                                rowLines.add(cells.joinToString("  |  "))
                            }
                        }
                        break
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (rowLines.isEmpty()) {
                rowLines.add("(Boş Excel Tablosu)")
            }

            return renderLinesToPdf(context, rowLines, title, isMonospace = true)
        } catch (e: Exception) {
            Log.e(TAG, "XLSX PDF dönüştürme hatası: ${e.message}", e)
            return null
        }
    }

    fun convertPptxToPdf(context: Context, uri: Uri, title: String): PreparedDocument? {
        val slideTexts = mutableListOf<Pair<String, List<String>>>()

        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null

            val slideEntries = mutableMapOf<Int, String>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.startsWith("ppt/slides/slide") && entry.name.endsWith(".xml")) {
                        val num = entry.name.filter { it.isDigit() }.toIntOrNull() ?: 999
                        slideEntries[num] = zis.bufferedReader().readText()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val tRegex = Regex("<a:t[^>]*>(.*?)</a:t>")
            val sortedKeys = slideEntries.keys.sorted()
            for (k in sortedKeys) {
                val xml = slideEntries[k] ?: continue
                val matches = tRegex.findAll(xml).map { it.groupValues[1] }.toList()
                val slideTitle = if (matches.isNotEmpty()) matches.first() else "Slayt $k"
                val body = if (matches.size > 1) matches.drop(1) else emptyList()
                slideTexts.add(slideTitle to body)
            }

            if (slideTexts.isEmpty()) {
                slideTexts.add("Sunum" to listOf("(Boş PowerPoint Sunumu)"))
            }

            val pdfDoc = PdfDocument()
            val tempPdf = File.createTempFile("pptx_print_", ".pdf", context.cacheDir)

            val titlePaint = Paint().apply {
                color = Color.BLACK
                textSize = 18f
                isFakeBoldText = true
            }
            val bodyPaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 13f
            }

            for ((idx, slide) in slideTexts.withIndex()) {
                val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, idx + 1).create()
                val page = pdfDoc.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                val framePaint = Paint().apply {
                    color = Color.LTGRAY
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                }
                canvas.drawRoundRect(30f, 30f, A4_WIDTH_PT - 30f, A4_HEIGHT_PT - 30f, 12f, 12f, framePaint)

                canvas.drawText("Slayt ${idx + 1}: ${slide.first}", 50f, 75f, titlePaint)
                canvas.drawLine(50f, 90f, A4_WIDTH_PT - 50f, 90f, framePaint)

                var y = 125f
                for (line in slide.second) {
                    if (y > A4_HEIGHT_PT - 60f) break
                    canvas.drawText("• $line", 55f, y, bodyPaint)
                    y += 24f
                }

                pdfDoc.finishPage(page)
            }

            FileOutputStream(tempPdf).use { pdfDoc.writeTo(it) }
            val pfd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
            return PreparedDocument(title, slideTexts.size, tempPdf, pfd)
        } catch (e: Exception) {
            Log.e(TAG, "PPTX PDF dönüştürme hatası: ${e.message}", e)
            return null
        }
    }

    private fun renderLinesToPdf(
        context: Context,
        lines: List<String>,
        title: String,
        isMonospace: Boolean = false
    ): PreparedDocument? {
        val pdfDoc = PdfDocument()
        val tempPdf = File.createTempFile("lines_print_", ".pdf", context.cacheDir)

        try {
            val paint = Paint().apply {
                color = Color.BLACK
                textSize = if (isMonospace) 10f else 12f
                if (isMonospace) typeface = Typeface.MONOSPACE
            }

            val margin = 40f
            val lineHeight = if (isMonospace) 15f else 18f
            val maxLinesPerPage = ((A4_HEIGHT_PT - margin * 2) / lineHeight).toInt()

            var pageIndex = 1
            var lineIdx = 0

            while (lineIdx < lines.size || lineIdx == 0) {
                val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, pageIndex).create()
                val page = pdfDoc.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                var y = margin + lineHeight
                var linesOnThisPage = 0

                while (linesOnThisPage < maxLinesPerPage && lineIdx < lines.size) {
                    canvas.drawText(lines[lineIdx], margin, y, paint)
                    y += lineHeight
                    linesOnThisPage++
                    lineIdx++
                }

                pdfDoc.finishPage(page)
                pageIndex++
                if (lineIdx >= lines.size) break
            }

            FileOutputStream(tempPdf).use { pdfDoc.writeTo(it) }
            val pfd = ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY)
            val count = pageIndex - 1
            return PreparedDocument(title, count, tempPdf, pfd)
        } catch (e: Exception) {
            Log.e(TAG, "Satır PDF yazdırma hatası: ${e.message}", e)
            return null
        } finally {
            try { pdfDoc.close() } catch (ignored: Exception) {}
        }
    }

    /**
     * YÜKSEK KALİTELİ SİYAH-BEYAZ ÖNİZLEME MOTORU
     * Belgeyi yapay kumlanma veya piksel bozulması olmadan, yüksek çözünürlükte
     * temiz ve net bir siyah-beyaz / gri tonlamalı önizlemeye dönüştürür.
     */
    fun renderMonochromePreview(
        pdfFile: File,
        pageIndex: Int,
        invert: Boolean = false,
        tonerDensity: Int = 7,
        ditherMethod: DitherMethod = DitherMethod.FLOYD_STEINBERG,
        targetWidth: Int = 1200
    ): Bitmap? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                return null
            }
            val page = renderer.openPage(pageIndex)
            val scale = targetWidth.toFloat() / page.width.toFloat()
            val targetHeight = (page.height.toFloat() * scale).toInt()

            val srcBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            srcBitmap.eraseColor(Color.WHITE)
            page.render(srcBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            // Temiz ve Yüksek Kaliteli Siyah-Beyaz (Grayscale / Negative) Dönüşümü
            val bwBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bwBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            val matrix = ColorMatrix()
            matrix.setSaturation(0f) // Renkleri tamamen temiz siyah-beyaza çevir

            if (invert) {
                val invertMatrix = ColorMatrix(floatArrayOf(
                    -1f,  0f,  0f,  0f, 255f,
                     0f, -1f,  0f,  0f, 255f,
                     0f,  0f, -1f,  0f, 255f,
                     0f,  0f,  0f,  1f,   0f
                ))
                matrix.postConcat(invertMatrix)
            }

            paint.colorFilter = ColorMatrixColorFilter(matrix)
            canvas.drawBitmap(srcBitmap, 0f, 0f, paint)
            srcBitmap.recycle()

            bwBitmap
        } catch (e: Exception) {
            Log.e(TAG, "renderMonochromePreview hatası: ${e.message}", e)
            null
        } finally {
            try { renderer?.close() } catch (ignored: Exception) {}
            try { pfd?.close() } catch (ignored: Exception) {}
        }
    }

    private fun copyUriToCache(context: Context, uri: Uri, fileName: String): File? {
        return try {
            val tempFile = File.createTempFile("cached_", fileName, context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            null
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) name = it.getString(index)
                }
            }
        }
        return name ?: uri.lastPathSegment
    }
}
