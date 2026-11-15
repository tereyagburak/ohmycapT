package com.example.captprint.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.captprint.domain.model.PrinterStatus
import com.example.captprint.ui.theme.StatusError
import com.example.captprint.ui.theme.StatusSuccess
import com.example.captprint.ui.theme.StatusWarning

@Composable
fun AnimatedPrinterIllustration(
    status: PrinterStatus,
    modifier: Modifier = Modifier.size(200.dp, 160.dp)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "PrinterAnimation")

    // Ready Durumu: Yeşil nabız / nefes alma animasyonu
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Printing Durumu: Kağıt çıkış hareketi
    val paperOffsetY by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "paperOffset"
    )

    // Error / PaperJam Durumu: Kırmızı titreme (shake)
    val shakeOffsetX by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(80, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shakeOffset"
    )

    val bodyBaseColor = MaterialTheme.colorScheme.surfaceVariant
    val outlineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)

    val (statusColor, dynamicScale, currentShake) = when (status) {
        is PrinterStatus.Ready -> Triple(StatusSuccess, pulseScale, 0f)
        is PrinterStatus.Printing -> Triple(StatusSuccess, 1f, 0f)
        is PrinterStatus.Paused -> Triple(StatusWarning, 1f, 0f)
        is PrinterStatus.WaitingForNextPage -> Triple(StatusWarning, 1f, 0f)
        is PrinterStatus.Connecting, is PrinterStatus.PermissionNeeded, is PrinterStatus.WarmingUp -> Triple(StatusWarning, pulseScale, 0f)
        is PrinterStatus.PaperOut, is PrinterStatus.PaperJam, is PrinterStatus.CoverOpen, is PrinterStatus.Error -> Triple(StatusError, 1f, shakeOffsetX)
        is PrinterStatus.Disconnected -> Triple(Color.Gray.copy(alpha = 0.5f), 1f, 0f)
        is PrinterStatus.TonerLow -> Triple(StatusWarning, 1f, 0f)
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            val scale = dynamicScale
            val offsetX = currentShake

            // 1. Üst Kağıt Tepsisi & Giriş Kağıdı (Tray)
            val trayW = canvasW * 0.54f * scale
            val trayH = canvasH * 0.28f * scale
            val trayLeft = (canvasW - trayW) / 2f + offsetX
            val trayTop = canvasH * 0.12f

            drawRoundRect(
                color = Color.White,
                topLeft = Offset(trayLeft + 10f, trayTop - 8f),
                size = Size(trayW - 20f, trayH),
                cornerRadius = CornerRadius(6f, 6f)
            )
            drawRoundRect(
                color = outlineColor,
                topLeft = Offset(trayLeft + 10f, trayTop - 8f),
                size = Size(trayW - 20f, trayH),
                cornerRadius = CornerRadius(6f, 6f),
                style = Stroke(width = 3f)
            )

            // 2. Ana Yazıcı Gövdesi
            val bodyW = canvasW * 0.82f * scale
            val bodyH = canvasH * 0.52f * scale
            val bodyLeft = (canvasW - bodyW) / 2f + offsetX
            val bodyTop = canvasH * 0.32f

            drawRoundRect(
                color = bodyBaseColor,
                topLeft = Offset(bodyLeft, bodyTop),
                size = Size(bodyW, bodyH),
                cornerRadius = CornerRadius(16f, 16f)
            )
            drawRoundRect(
                color = outlineColor,
                topLeft = Offset(bodyLeft, bodyTop),
                size = Size(bodyW, bodyH),
                cornerRadius = CornerRadius(16f, 16f),
                style = Stroke(width = 4f)
            )

            // 3. Çıkış Yuvası (Slot)
            val slotW = bodyW * 0.68f
            val slotH = 10f
            val slotLeft = bodyLeft + (bodyW - slotW) / 2f
            val slotTop = bodyTop + bodyH * 0.42f

            drawRoundRect(
                color = Color.DarkGray.copy(alpha = 0.8f),
                topLeft = Offset(slotLeft, slotTop),
                size = Size(slotW, slotH),
                cornerRadius = CornerRadius(4f, 4f)
            )

            // 4. Yazdırılan / Çıkan Kağıt (Printing veya WaitingForNextPage animasyonu)
            if (status is PrinterStatus.Printing || status is PrinterStatus.WaitingForNextPage) {
                val outPaperW = slotW * 0.85f
                val outPaperH = 34f
                val outPaperLeft = slotLeft + (slotW - outPaperW) / 2f
                val yAnim = if (status is PrinterStatus.Printing) paperOffsetY else 15f
                val outPaperTop = slotTop + slotH + yAnim

                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(outPaperLeft, outPaperTop),
                    size = Size(outPaperW, outPaperH),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                drawRoundRect(
                    color = outlineColor,
                    topLeft = Offset(outPaperLeft, outPaperTop),
                    size = Size(outPaperW, outPaperH),
                    cornerRadius = CornerRadius(4f, 4f),
                    style = Stroke(width = 2f)
                )
            }

            // 5. Durum LED Işığı (LED Göstergesi)
            val ledRadius = 7f * scale
            val ledCenter = Offset(bodyLeft + bodyW * 0.84f, bodyTop + bodyH * 0.28f)
            drawCircle(
                color = statusColor,
                radius = ledRadius,
                center = ledCenter
            )
            drawCircle(
                color = outlineColor,
                radius = ledRadius,
                center = ledCenter,
                style = Stroke(width = 2f)
            )
        }
    }
}
