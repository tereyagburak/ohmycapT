package com.example.captprint.data.repository

import android.net.Uri
import com.example.captprint.domain.model.PrinterCapabilities
import com.example.captprint.domain.model.PrinterStatus
import com.example.captprint.domain.model.PrintSettings
import kotlinx.coroutines.flow.StateFlow

interface PrinterRepository {
    val printerStatus: StateFlow<PrinterStatus>
    val capabilities: PrinterCapabilities

    fun refreshStatus()
    fun connect()
    fun disconnect()
    fun queryStatus(): String
    suspend fun printTestPattern(patternType: Int, invert: Boolean): Result<Unit>
    suspend fun printDocument(
        uri: Uri,
        settings: PrintSettings,
        onProgress: (page: Int, total: Int, percent: Float, message: String) -> Unit
    ): Result<Unit>
    fun pausePrint()
    fun resumePrint()
    fun approveNextPage() // Tek tek onaylı modda sonraki sayfayı tetikler
    fun cancelPrint()
}
