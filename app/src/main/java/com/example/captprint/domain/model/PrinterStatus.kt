package com.example.captprint.domain.model

/**
 * Yazıcı durumu tek bir sealed class hiyerarşisi.
 * Hem donanımdan (USB / JNI) dönen gerçek durumları hem de UI durumlarını temsil eder.
 */
sealed class PrinterStatus {
    data object Disconnected : PrinterStatus()
    data class PermissionNeeded(val deviceName: String) : PrinterStatus()
    data class Connecting(val deviceName: String) : PrinterStatus()
    data class Ready(val modelName: String, val deviceName: String) : PrinterStatus()
    data class WarmingUp(val modelName: String) : PrinterStatus()
    data class Printing(val currentPage: Int, val totalPages: Int, val progressPercent: Float, val statusMessage: String) : PrinterStatus()
    data class Paused(val completedPages: Int, val nextPageIndex: Int, val totalPages: Int) : PrinterStatus()
    data class WaitingForNextPage(val printedPage: Int, val nextPage: Int, val totalPages: Int) : PrinterStatus()
    data object PaperOut : PrinterStatus()
    data object PaperJam : PrinterStatus()
    data object CoverOpen : PrinterStatus()
    data object TonerLow : PrinterStatus()
    data class Error(val code: Int, val userMessage: String, val technicalDetail: String) : PrinterStatus()
}

/**
 * LBP2900 gibi donanımların kabiliyet matrisi.
 * Uydurma donanım bilgilerini engellemek için kullanılır.
 */
data class PrinterCapabilities(
    val hasTonerLevel: Boolean = false,
    val hasFirmwareInfo: Boolean = false,
    val hasHardwareTotalPages: Boolean = false,
    val hasHeadCleaning: Boolean = false,
    val supportedMediaSizes: List<String> = listOf("A4", "Letter", "A5", "Legal"),
    val supportsInvert: Boolean = true,
    val supportsTonerDensity: Boolean = true
)
