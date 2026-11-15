package com.example.captprint.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.PrintDisabled
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.captprint.R

enum class TopLevelDestination(
    val route: String,
    val titleRes: Int,
    val icon: ImageVector
) {
    PRINT("print", R.string.nav_print, Icons.Default.Print),
    PRINTER("printer", R.string.nav_printer, Icons.Default.PrintDisabled),
    HISTORY("history", R.string.nav_history, Icons.Default.History),
    SETTINGS("settings", R.string.nav_settings, Icons.Default.Settings)
}
