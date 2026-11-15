package com.example.captprint.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.captprint.R
import com.example.captprint.domain.model.PrinterStatus
import com.example.captprint.ui.theme.StatusError
import com.example.captprint.ui.theme.StatusSuccess
import com.example.captprint.ui.theme.StatusWarning

@Composable
fun GlobalStatusBar(
    status: PrinterStatus,
    onStatusBarClick: () -> Unit,
    onConnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (dotColor, statusText, showConnectButton) = when (status) {
        is PrinterStatus.Ready -> Triple(StatusSuccess, "${status.modelName} • ${stringResource(R.string.status_ready)}", false)
        is PrinterStatus.Connecting -> Triple(StatusWarning, stringResource(R.string.status_connecting), false)
        is PrinterStatus.PermissionNeeded -> Triple(StatusWarning, stringResource(R.string.status_permission_needed), true)
        is PrinterStatus.Disconnected -> Triple(StatusError, stringResource(R.string.status_disconnected), true)
        is PrinterStatus.Printing -> Triple(StatusSuccess, "${stringResource(R.string.status_printing)} (${status.currentPage}/${status.totalPages})", false)
        is PrinterStatus.Paused -> Triple(StatusWarning, "Yazdırma Duraklatıldı (${status.completedPages}/${status.totalPages})", false)
        is PrinterStatus.WaitingForNextPage -> Triple(StatusWarning, "${stringResource(R.string.status_waiting_next_page)} (${status.printedPage}/${status.totalPages})", false)
        is PrinterStatus.WarmingUp -> Triple(StatusWarning, stringResource(R.string.status_warming_up), false)
        is PrinterStatus.PaperOut -> Triple(StatusError, stringResource(R.string.status_paper_out), false)
        is PrinterStatus.PaperJam -> Triple(StatusError, stringResource(R.string.status_paper_jam), false)
        is PrinterStatus.CoverOpen -> Triple(StatusError, stringResource(R.string.status_cover_open), false)
        is PrinterStatus.TonerLow -> Triple(StatusWarning, stringResource(R.string.status_toner_low), false)
        is PrinterStatus.Error -> Triple(StatusError, status.userMessage, true)
    }

    val animatedDotColor by animateColorAsState(targetValue = dotColor, label = "dotColor")

    // 6. İSTEK: Sistem durum çubuğu (Status Bar) ile birebir uyumlu renk ve kesintisiz dolgu
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onStatusBarClick() }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Android sistem durum çubuğu insets alanı ile bütünleşik üst padding
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(animatedDotColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (showConnectButton) {
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onConnectClick,
                        contentPadding = ButtonDefaults.ContentPadding,
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Text(text = stringResource(R.string.btn_connect), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 1.dp
            )
        }
    }
}
