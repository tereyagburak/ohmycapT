package com.example.captprint.domain.model

enum class DitherMethod {
    THRESHOLD,
    FLOYD_STEINBERG,
    ORDERED
}

enum class PaperSize(val displayName: String) {
    A4("A4 (210 x 297 mm)"),
    LETTER("US Letter (216 x 279 mm)"),
    A5("A5 (148 x 210 mm)"),
    LEGAL("Legal (216 x 356 mm)")
}

enum class PageScaling(val displayName: String) {
    FIT_PAGE("Sayfaya Sığdır"),
    ACTUAL_SIZE("Gerçek Boyut (%100)"),
    FILL_PAGE("Sayfayı Doldur")
}

data class PrintSettings(
    val copies: Int = 1,
    val pageRange: String = "ALL", // "ALL" veya "1-3, 5"
    val paperSize: PaperSize = PaperSize.A4,
    val scaling: PageScaling = PageScaling.FIT_PAGE,
    val tonerDensity: Int = 7, // 1..7 (7 = koyu varsayılan)
    val contrast: Float = 1.0f,
    val ditherMethod: DitherMethod = DitherMethod.FLOYD_STEINBERG,
    val invertColors: Boolean = false,
    val manualFeedStepByStep: Boolean = false, // Kağıt sıkışmasını önleyen tek tek onaylı mod
    val manualDuplex: Boolean = false
)
