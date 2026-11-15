package com.example.captprint.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.captprint.domain.model.PrinterStatus
import com.example.captprint.ui.theme.StatusError
import com.example.captprint.ui.theme.StatusWarning

private data class AlertInfo(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val accentColor: Color,
    val containerColor: Color
)

@Composable
fun LivePrinterAlertBanner(
    status: PrinterStatus,
    onBannerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val alertInfo: AlertInfo? = when (status) {
        is PrinterStatus.CoverOpen -> AlertInfo(
            title = "Yazıcı Kapağı Açık / Toner Erişimi",
            description = "Üst kapağı kapatın ve toner kartuşunun tam oturduğundan emin olun.",
            icon = Icons.Default.ReportProblem,
            accentColor = StatusError,
            containerColor = Color(0xFFFCE8E6)
        )
        is PrinterStatus.PaperOut -> AlertInfo(
            title = "Tepside Kağıt Yok!",
            description = "Kağıt tepsisine A4 kağıt yerleştirin.",
            icon = Icons.Default.Warning,
            accentColor = StatusError,
            containerColor = Color(0xFFFCE8E6)
        )
        is PrinterStatus.PaperJam -> AlertInfo(
            title = "Kağıt Sıkışması Algılandı!",
            description = "Kapağı açıp sıkışan kağıdı dikkatlice çıkarın.",
            icon = Icons.Default.DoNotDisturbOn,
            accentColor = StatusError,
            containerColor = Color(0xFFFCE8E6)
        )
        is PrinterStatus.TonerLow -> AlertInfo(
            title = "Toner Az veya Kartuş Sorunu!",
            description = "Toner kartuşunu çıkarıp hafifçe sallayın veya yerine tam oturtun.",
            icon = Icons.Default.Warning,
            accentColor = StatusWarning,
            containerColor = Color(0xFFFEF7E0)
        )
        is PrinterStatus.WarmingUp -> AlertInfo(
            title = "Yazıcı Isınıyor...",
            description = "Fuser ünitesi ısınıyor, kısa süre içinde hazır olacak.",
            icon = Icons.Default.LocalFireDepartment,
            accentColor = StatusWarning,
            containerColor = Color(0xFFFEF7E0)
        )
        is PrinterStatus.Error -> AlertInfo(
            title = "Yazıcı Uyarısı",
            description = status.userMessage,
            icon = Icons.Default.ErrorOutline,
            accentColor = StatusError,
            containerColor = Color(0xFFFCE8E6)
        )
        else -> null
    }

    LaunchedEffect(status) {
        if (status is PrinterStatus.CoverOpen ||
            status is PrinterStatus.PaperOut ||
            status is PrinterStatus.PaperJam ||
            status is PrinterStatus.TonerLow ||
            status is PrinterStatus.Error
        ) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    AnimatedVisibility(
        visible = alertInfo != null,
        enter = expandVertically(tween(250)) + fadeIn(tween(250)),
        exit = shrinkVertically(tween(200)) + fadeOut(tween(200)),
        modifier = modifier
    ) {
        alertInfo?.let { info ->
            val infiniteTransition = rememberInfiniteTransition(label = "AlertPulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 0.94f,
                targetValue = 1.12f,
                animationSpec = infiniteRepeatable(
                    animation = tween(650),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseScale"
            )
            val animatedBg by animateColorAsState(targetValue = info.containerColor, label = "alertBg")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable { onBannerClick() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = animatedBg),
                border = BorderStroke(1.5.dp, info.accentColor.copy(alpha = 0.65f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = info.icon,
                        contentDescription = null,
                        tint = info.accentColor,
                        modifier = Modifier
                            .size(28.dp)
                            .scale(pulseScale)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = info.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = info.accentColor
                        )
                        Text(
                            text = info.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF263238)
                        )
                    }
                }
            }
        }
    }
}
