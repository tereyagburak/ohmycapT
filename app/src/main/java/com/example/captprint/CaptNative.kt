package com.example.captprint

// YENİ: C/NDK Katmanı JNI Sarmalayıcı Sınıfı (Faz 4 PDF Dithering ve Bant Yazdırma)

import android.graphics.Bitmap

class CaptNative {

    companion object {
        init {
            System.loadLibrary("captnative")
        }
    }

    external fun nativeOpen(transport: CaptUsbTransport): Int
    external fun nativeQueryStatus(): String

    // Faz 4 PDF Bant Motoru
    external fun nativeStartPage(widthBytes: Int, heightLines: Int, tonerDensity: Int, invert: Boolean): Int
    external fun nativeDitherAndPrintBand(bitmap: Bitmap, lines: Int, isLast: Boolean, invert: Boolean): Int
    external fun nativeEndPage(): Int

    // Faz 3 Test Deseni
    external fun nativePrintTestPattern(patternType: Int, invert: Boolean): Int

    external fun nativeCancel()
    external fun nativeClose()
}
