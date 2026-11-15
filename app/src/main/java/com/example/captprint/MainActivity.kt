package com.example.captprint

import android.content.Intent
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.captprint.ui.MainAppScaffold
import com.example.captprint.ui.theme.CaptPrintTheme

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as CaptApplication

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val activeRepo by app.activeRepository.collectAsState()
            CaptPrintTheme {
                MainAppScaffold(
                    repository = activeRepo ?: app.realRepository,
                    widthSizeClass = windowSizeClass.widthSizeClass
                )
            }
        }

        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        (application as CaptApplication).printerRepository.refreshStatus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val app = application as CaptApplication

        when (intent.action) {
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                app.printerRepository.connect()
            }
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
                }
                if (uri != null) {
                    // Paylaşım menüsünden gelen belge doğrudan uygulamaya aktarılır
                }
            }
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                if (uri != null) {
                    // PDF Aç ile gelen belge doğrudan uygulamaya aktarılır
                }
            }
        }
    }
}
