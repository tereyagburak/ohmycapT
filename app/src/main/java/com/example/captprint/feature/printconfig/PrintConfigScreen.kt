package com.example.captprint.feature.printconfig

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.captprint.R
import com.example.captprint.data.DocumentPreparationEngine
import com.example.captprint.data.PreparedDocument
import com.example.captprint.domain.model.DitherMethod
import com.example.captprint.domain.model.PaperSize
import com.example.captprint.domain.model.PrintSettings
import com.example.captprint.domain.model.PrinterStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintConfigScreen(
    document: PreparedDocument,
    printerStatus: PrinterStatus,
    onBackClick: () -> Unit,
    onStartPrint: (PrintSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var printSettings by remember { mutableStateOf(PrintSettings()) }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingPreview by remember { mutableStateOf(false) }

    // Yüksek Kaliteli Siyah-Beyaz ve İnvert Canlı Önizleme
    LaunchedEffect(
        currentPageIndex,
        printSettings.invertColors,
        printSettings.tonerDensity,
        printSettings.ditherMethod
    ) {
        isLoadingPreview = true
        withContext(Dispatchers.IO) {
            val bmp = DocumentPreparationEngine.renderMonochromePreview(
                pdfFile = document.pdfFile,
                pageIndex = currentPageIndex,
                invert = printSettings.invertColors,
                tonerDensity = printSettings.tonerDensity,
                ditherMethod = printSettings.ditherMethod,
                targetWidth = 1200
            )
            withContext(Dispatchers.Main) {
                previewBitmap = bmp
                isLoadingPreview = false
            }
        }
    }

    val isPrinterReady = printerStatus is PrinterStatus.Ready

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(document.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(
                            stringResource(R.string.page_counter_preview, currentPageIndex + 1, document.pageCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        },
        bottomBar = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (!isPrinterReady) {
                        Text(
                            text = "Yazıcı hazır değil. Lütfen OTG kablosunu takın veya USB izni verin.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Button(
                        onClick = { onStartPrint(printSettings) },
                        enabled = isPrinterReady,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (printSettings.manualFeedStepByStep) "Tek Tek Onaylı Yazdır" else stringResource(R.string.btn_print_now),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.tab_preview), fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.tab_settings_sub), fontWeight = FontWeight.SemiBold) }
                )
            }

            if (selectedTab == 0) {
                // Önizleme Sekmesi (Pinch to zoom + 1-Bit Gerçekçi Sayfa)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoadingPreview) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    } else if (previewBitmap != null) {
                        var scale by remember { mutableFloatStateOf(1f) }
                        var offsetX by remember { mutableFloatStateOf(0f) }
                        var offsetY by remember { mutableFloatStateOf(0f) }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(0.8f, 4f)
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Gerçekçi Kağıt Sayfası (İnvert durumunda da kenarlık ve gölge sayesinde belirgin)
                            Box(
                                modifier = Modifier
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = offsetX,
                                        translationY = offsetY
                                    )
                                    .shadow(elevation = 18.dp, shape = RoundedCornerShape(3.dp))
                                    .border(
                                        width = 1.5.dp,
                                        color = if (printSettings.invertColors) Color(0xFF80CBC4) else Color(0xFF90A4AE),
                                        shape = RoundedCornerShape(3.dp)
                                    )
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (printSettings.invertColors) Color.Black else Color.White)
                            ) {
                                Image(
                                    bitmap = previewBitmap!!.asImageBitmap(),
                                    contentDescription = "Siyah-Beyaz Önizleme",
                                    modifier = Modifier.size(280.dp, 396.dp) // A4 en-boy oranı (1:1.414)
                                )
                            }
                        }
                    }

                    // Siyah-Beyaz Bilgi Rozeti
                    Card(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ZoomIn, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Siyah-Beyaz",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Sayfa Geçiş Butonları (Floating Controls)
                    if (document.pageCount > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .align(Alignment.BottomCenter),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                                enabled = currentPageIndex > 0,
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(50))
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Önceki Sayfa")
                            }

                            Card(shape = RoundedCornerShape(16.dp)) {
                                Text(
                                    text = "${currentPageIndex + 1} / ${document.pageCount}",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }

                            IconButton(
                                onClick = { if (currentPageIndex < document.pageCount - 1) currentPageIndex++ },
                                enabled = currentPageIndex < document.pageCount - 1,
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(50))
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Sonraki Sayfa")
                            }
                        }
                    }
                }
            } else {
                // Ayarlar Sekmesi
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 1. MANUEL BESLEME / TEK TEK ONAYLI YAZDIRMA
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (printSettings.manualFeedStepByStep)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.manual_feed_step_by_step),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.manual_feed_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = printSettings.manualFeedStepByStep,
                                onCheckedChange = { printSettings = printSettings.copy(manualFeedStepByStep = it) }
                            )
                        }
                    }

                    // 2. Kopya Sayısı
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = stringResource(R.string.copies_count), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (printSettings.copies > 1) printSettings = printSettings.copy(copies = printSettings.copies - 1) }) {
                                Icon(Icons.Default.Remove, contentDescription = "Azalt")
                            }
                            Text(text = "${printSettings.copies}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                            IconButton(onClick = { if (printSettings.copies < 30) printSettings = printSettings.copy(copies = printSettings.copies + 1) }) {
                                Icon(Icons.Default.Add, contentDescription = "Arttır")
                            }
                        }
                    }

                    // 3. Kağıt Boyutu
                    Column {
                        Text(text = stringResource(R.string.paper_size), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        PaperSize.entries.forEach { size ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = printSettings.paperSize == size,
                                    onClick = { printSettings = printSettings.copy(paperSize = size) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(size.displayName, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    // 4. Yoğunluk / Koyuluk Slider'ı (Canlı Önizlemeyi Günceller)
                    Column {
                        Text(text = "${stringResource(R.string.toner_density_slider)}: ${printSettings.tonerDensity}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Slider(
                            value = printSettings.tonerDensity.toFloat(),
                            onValueChange = { printSettings = printSettings.copy(tonerDensity = it.toInt()) },
                            valueRange = 1f..7f,
                            steps = 5
                        )
                    }

                    // 5. Dönüşüm Yöntemi (Dither)
                    Column {
                        Text(text = stringResource(R.string.dither_method_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = printSettings.ditherMethod == DitherMethod.FLOYD_STEINBERG,
                                onClick = { printSettings = printSettings.copy(ditherMethod = DitherMethod.FLOYD_STEINBERG) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.dither_floyd), style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = printSettings.ditherMethod == DitherMethod.THRESHOLD,
                                onClick = { printSettings = printSettings.copy(ditherMethod = DitherMethod.THRESHOLD) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.dither_threshold), style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    // 6. Renkleri Ters Çevir (Invert - Canlı Önizlemeyi Günceller)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = stringResource(R.string.invert_colors), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Switch(
                            checked = printSettings.invertColors,
                            onCheckedChange = { printSettings = printSettings.copy(invertColors = it) }
                        )
                    }
                }
            }
        }
    }
}
