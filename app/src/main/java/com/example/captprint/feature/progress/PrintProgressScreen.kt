package com.example.captprint.feature.progress

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.captprint.R
import com.example.captprint.data.repository.PrinterRepository
import com.example.captprint.domain.model.PrinterStatus
import com.example.captprint.ui.theme.StatusSuccess

@Composable
fun PrintProgressScreen(
    repository: PrinterRepository,
    documentTitle: String,
    totalPages: Int,
    isCompleted: Boolean,
    isManualFeed: Boolean = false,
    onDoneClick: () -> Unit,
    onReprintClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status by repository.printerStatus.collectAsState()
    var showDetailedError by remember { mutableStateOf(false) }

    val currentProgress = when (val st = status) {
        is PrinterStatus.Printing -> st.progressPercent
        is PrinterStatus.Paused -> st.completedPages.toFloat() / st.totalPages.toFloat()
        is PrinterStatus.WaitingForNextPage -> st.printedPage.toFloat() / st.totalPages.toFloat()
        else -> if (isCompleted) 1f else 0.15f
    }

    val animatedProgress by animateFloatAsState(
        targetValue = currentProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "animatedProgress"
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = Color.White
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            val errorStatus = status as? PrinterStatus.Error

            if (errorStatus != null) {
                // HATA VE OLASI SORUNLAR EKRANI
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(80.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Yazdırma İptal Edildi",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "10 saniye boyunca yazıcıya veri iletilemedi veya yazıcı yanıt vermedi.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFCFD8DC),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // OLASI SORUNLAR LİSTESİ
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF261D1D))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB74D),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Olası Sorunlar:",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFB74D)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val issues = listOf(
                                "Toner bitik",
                                "Toner başlığı yok",
                                "Toner başlığı algılanmıyor",
                                "Kağıt yok / algılanmıyor",
                                "Kablo sıkıntısı (OTG kablosu veya USB temassızlığı)"
                            )

                            issues.forEach { issue ->
                                Row(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF8A80),
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(
                                        text = issue,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // HATAYI DETAYLI GÖSTERME SEÇENEĞİ
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E262C))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color(0xFF81D4E0),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Hatayı Detaylı Göster",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                                IconButton(onClick = { showDetailedError = !showDetailedError }) {
                                    Icon(
                                        imageVector = if (showDetailedError) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Detay",
                                        tint = Color.White
                                    )
                                }
                            }

                            if (showDetailedError) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Mesaj: ${errorStatus.userMessage}\nDetay: ${errorStatus.technicalDetail}\nHata Kodu: ${errorStatus.code}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFFFCCBC),
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                        .fillMaxWidth()
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { repository.cancelPrint(); onDoneClick() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Kapat ve Geri Dön", fontWeight = FontWeight.Bold)
                    }
                }
            } else if (isCompleted) {
                // BAŞARI / TAMAMLANMA EKRANI
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = StatusSuccess,
                        modifier = Modifier.size(96.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = stringResource(R.string.print_completed_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Dosya Adı Rozeti
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E282A))
                    ) {
                        Text(
                            text = documentTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "$totalPages Sayfa Başarıyla Yazdırıldı",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFFCFD8DC),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(36.dp))

                    Button(
                        onClick = onDoneClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(stringResource(R.string.btn_done), fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onReprintClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(stringResource(R.string.btn_reprint), color = Color.White)
                    }
                }
            } else {
                // YAZDIRMA İLERLEME EKRANI: SOLDA DİKEY PROGRESS BAR + SAĞDA ANİMASYONLU LAZER YAZICI
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Üst Kısım: BEYAZ DOSYA İSMİ ROZETİ
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2628)),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = null,
                                tint = Color(0xFF81D4E0),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = documentTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Canon LBP2900 • 600 DPI Hi-SCoA Sıkıştırmalı Baskı",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFB0BEC5)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Orta Alan: Solda Dikey Progress Bar + Sağda Animasyonlu Yazıcı & Lazer Kağıt
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. SOLDA DİKEY PROGRESS BAR
                        VerticalProgressBar(
                            progress = animatedProgress,
                            modifier = Modifier
                                .width(36.dp)
                                .fillMaxHeight(0.85f)
                        )

                        // 2. SAĞDA: GELİŞMİŞ ANİMASYONLU LAZER YAZICI VE BASILAN KAĞIT
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            AnimatedLaserPrintingView(
                                progress = animatedProgress,
                                status = status,
                                modifier = Modifier.size(240.dp, 210.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Detaylı Yazdırma Açıklaması
                            when (val st = status) {
                                is PrinterStatus.Printing -> {
                                    Text(
                                        text = "Sayfa ${st.currentPage} / ${st.totalPages} Yazdırılıyor",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = st.statusMessage,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        color = Color(0xFFECEFF1)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "USB OTG Bulk Transfer • Lazer Ünitesi Aktif",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF81D4E0)
                                    )
                                }
                                is PrinterStatus.WaitingForNextPage -> {
                                    // TEK TEK KAĞIT ONAY KARTI
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF004F58)),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = stringResource(R.string.status_waiting_next_page),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = stringResource(R.string.waiting_sheet_msg, st.printedPage),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                textAlign = TextAlign.Center,
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.height(14.dp))
                                            Button(
                                                onClick = { repository.approveNextPage() },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(48.dp)
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(stringResource(R.string.btn_approve_next_page), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                                is PrinterStatus.Paused -> {
                                    // DURAKLATILDI KARTI
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF4A3400)),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "Yazdırma Duraklatıldı",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFFD54F)
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "${st.completedPages} sayfa basıldı. Sıradaki sayfa (${st.nextPageIndex}) için hazır olduğunuzda devam edin.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                textAlign = TextAlign.Center,
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.height(14.dp))
                                            Button(
                                                onClick = { repository.resumePrint() },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(48.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300))
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Yazdırmaya Devam Et", fontWeight = FontWeight.Bold, color = Color.Black)
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    Text(
                                        text = "Yazıcıya Veri İletiliyor...",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Alt Kısım: Duraklat / Devam Et ve İptal Butonları
                    Box(modifier = Modifier.fillMaxWidth()) {
                        when (status) {
                            is PrinterStatus.Printing -> {
                                if (isManualFeed) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                repository.pausePrint()
                                                onDoneClick()
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F))
                                        ) {
                                            Icon(Icons.Default.Pause, contentDescription = null, tint = Color.White)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(stringResource(R.string.btn_pause), fontWeight = FontWeight.Bold, color = Color.White)
                                        }

                                        OutlinedButton(
                                            onClick = { repository.cancelPrint(); onDoneClick() },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                        ) {
                                            Text(stringResource(R.string.btn_cancel), color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { repository.cancelPrint(); onDoneClick() },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                    ) {
                                        Text(stringResource(R.string.btn_cancel), color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            is PrinterStatus.WaitingForNextPage -> {
                                if (isManualFeed) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                repository.pausePrint()
                                                onDoneClick()
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F))
                                        ) {
                                            Icon(Icons.Default.Pause, contentDescription = null, tint = Color.White)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(stringResource(R.string.btn_pause), fontWeight = FontWeight.Bold, color = Color.White)
                                        }

                                        OutlinedButton(
                                            onClick = { repository.cancelPrint(); onDoneClick() },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                        ) {
                                            Text(stringResource(R.string.btn_cancel), color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { repository.cancelPrint(); onDoneClick() },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                    ) {
                                        Text(stringResource(R.string.btn_cancel), color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            is PrinterStatus.Paused -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = { repository.resumePrint() },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300))
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.btn_resume_print), fontWeight = FontWeight.Bold, color = Color.Black)
                                    }

                                    OutlinedButton(
                                        onClick = { repository.cancelPrint(); onDoneClick() },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    ) {
                                        Text(stringResource(R.string.btn_cancel), color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            else -> {
                                OutlinedButton(
                                    onClick = { repository.cancelPrint(); onDoneClick() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Text(stringResource(R.string.btn_cancel), color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * SOL TARAFTA DİKEY PROGRESS BAR BİLEŞENİ
 */
@Composable
fun VerticalProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(trackColor),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(progress.coerceIn(0.04f, 1f))
                    .clip(RoundedCornerShape(11.dp))
                    .background(primaryColor)
            )
        }
    }
}

/**
 * ANİMASYONLU GELİŞMİŞ LAZER YAZICI VE BASILAN BELGE ÇİZİMİ
 */
@Composable
fun AnimatedLaserPrintingView(
    progress: Float,
    status: PrinterStatus,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "LaserAnimation")

    val laserScanY by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserScanY"
    )

    val paperScrollOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "paperScroll"
    )

    val bodyColor = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // 1. Üst Giriş Tepsisi
        val trayW = w * 0.58f
        val trayH = h * 0.22f
        val trayLeft = (w - trayW) / 2f
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(trayLeft + 12f, 10f),
            size = Size(trayW - 24f, trayH),
            cornerRadius = CornerRadius(6f, 6f)
        )
        drawRoundRect(
            color = borderColor,
            topLeft = Offset(trayLeft + 12f, 10f),
            size = Size(trayW - 24f, trayH),
            cornerRadius = CornerRadius(6f, 6f),
            style = Stroke(3f)
        )

        // 2. Ana Lazer Yazıcı Gövdesi
        val bodyW = w * 0.88f
        val bodyH = h * 0.55f
        val bodyLeft = (w - bodyW) / 2f
        val bodyTop = h * 0.28f

        drawRoundRect(
            color = bodyColor,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(18f, 18f)
        )
        drawRoundRect(
            color = borderColor,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(18f, 18f),
            style = Stroke(4f)
        )

        // 3. Çıkış Yuvası (Eject Slot)
        val slotW = bodyW * 0.72f
        val slotH = 12f
        val slotLeft = bodyLeft + (bodyW - slotW) / 2f
        val slotTop = bodyTop + bodyH * 0.44f

        drawRoundRect(
            color = Color(0xFF263238),
            topLeft = Offset(slotLeft, slotTop),
            size = Size(slotW, slotH),
            cornerRadius = CornerRadius(4f, 4f)
        )

        // 4. Yazdırılan Sayfa & Üzerindeki Metin Çizgileri
        val paperW = slotW * 0.88f
        val paperH = 75f
        val paperLeft = slotLeft + (slotW - paperW) / 2f
        val paperTop = slotTop + slotH + 2f + (if (status is PrinterStatus.Printing) paperScrollOffset else 4f)

        // Beyaz Kağıt
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(paperLeft, paperTop),
            size = Size(paperW, paperH),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawRoundRect(
            color = Color.Gray,
            topLeft = Offset(paperLeft, paperTop),
            size = Size(paperW, paperH),
            cornerRadius = CornerRadius(4f, 4f),
            style = Stroke(2f)
        )

        // Kağıt Üzerindeki Metin Çizgileri
        val linePaintColor = Color.DarkGray.copy(alpha = 0.85f)
        for (i in 0 until 5) {
            val lineY = paperTop + 14f + (i * 12f)
            if (lineY < paperTop + paperH - 10f) {
                drawLine(
                    color = linePaintColor,
                    start = Offset(paperLeft + 12f, lineY),
                    end = Offset(paperLeft + paperW - (if (i % 2 == 0) 18f else 32f), lineY),
                    strokeWidth = 3f
                )
            }
        }

        // 5. LAZER IŞINI TARAMA EFEKTİ
        if (status is PrinterStatus.Printing) {
            val laserY = paperTop + (paperH * laserScanY)
            drawLine(
                color = Color(0xFF00E5FF),
                start = Offset(paperLeft + 4f, laserY),
                end = Offset(paperLeft + paperW - 4f, laserY),
                strokeWidth = 3.5f
            )
            drawCircle(
                color = Color(0x8000E5FF),
                radius = 6f,
                center = Offset(paperLeft + paperW * 0.5f, laserY)
            )
        }

        // 6. Durum LED Işığı
        val ledCenter = Offset(bodyLeft + bodyW * 0.86f, bodyTop + 24f)
        drawCircle(
            color = if (status is PrinterStatus.Printing) Color(0xFF00E676) else Color(0xFFFFB300),
            radius = 6f,
            center = ledCenter
        )
    }
}
