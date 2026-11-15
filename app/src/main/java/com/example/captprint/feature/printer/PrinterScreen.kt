package com.example.captprint.feature.printer

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.captprint.R
import com.example.captprint.data.repository.PrinterRepository
import com.example.captprint.domain.model.PrinterStatus
import com.example.captprint.ui.components.AnimatedPrinterIllustration
import com.example.captprint.ui.theme.StatusError
import com.example.captprint.ui.theme.StatusSuccess
import com.example.captprint.ui.theme.StatusWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PrinterHardwareDetails(
    val isConnected: Boolean = false,
    val isValidData: Boolean = false,
    val paperAvailable: Boolean? = null,
    val bufferNormal: Boolean? = null,
    val isBusy: Boolean? = null,
    val isPrinting: Boolean? = null,
    val hasHardwareError: Boolean? = null,
    val pageDecoding: Int? = null,
    val pagePrinting: Int? = null,
    val pageOut: Int? = null,
    val lastCheckTime: String = "",
    val errorMessage: String? = null
)

@Composable
fun PrinterScreen(
    repository: PrinterRepository,
    modifier: Modifier = Modifier
) {
    val status by repository.printerStatus.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.tab_status),
        stringResource(R.string.tab_maintenance),
        stringResource(R.string.tab_info)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Üst Alan: Animasyonlu Yazıcı İllüstrasyonu + Model Adı
        AnimatedPrinterIllustration(
            status = status,
            modifier = Modifier.size(220.dp, 160.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = when (status) {
                is PrinterStatus.Ready -> (status as PrinterStatus.Ready).modelName
                else -> stringResource(R.string.printer_model_title)
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3 Alt Sekme (Durum / Bakım / Bilgi)
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title, fontWeight = FontWeight.SemiBold) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTabIndex) {
            0 -> PrinterStatusSubTab(status = status, repository = repository)
            1 -> PrinterMaintenanceSubTab(status = status, repository = repository)
            2 -> PrinterInfoSubTab(repository = repository)
        }
    }
}

@Composable
fun PrinterStatusSubTab(
    status: PrinterStatus,
    repository: PrinterRepository
) {
    val coroutineScope = rememberCoroutineScope()
    var hardwareDetails by remember { mutableStateOf(PrinterHardwareDetails()) }
    var isQuerying by remember { mutableStateOf(false) }

    fun queryHardware() {
        if (status !is PrinterStatus.Ready) {
            hardwareDetails = PrinterHardwareDetails(
                isConnected = false,
                isValidData = false,
                errorMessage = "Yazıcı bağlı değil."
            )
            return
        }

        isQuerying = true
        coroutineScope.launch(Dispatchers.IO) {
            val raw = repository.queryStatus()
            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

            val isValid = raw.contains("C-DURUM") || raw.contains("STATUS0")
            if (!isValid) {
                withContext(Dispatchers.Main) {
                    hardwareDetails = PrinterHardwareDetails(
                        isConnected = true,
                        isValidData = false,
                        lastCheckTime = timeStr,
                        errorMessage = raw.ifBlank { "Donanım yanıtı alınamadı" }
                    )
                    isQuerying = false
                }
                return@launch
            }

            val hasPaperNo = raw.contains("Kağıt Yok: EVET") || raw.contains("PaperOut")
            val isBufFull = raw.contains("Tampon Dolu: EVET")
            val isBusy = raw.contains("Meşgul: EVET")
            val isPrn = raw.contains("Yazıyor: EVET")
            val hasErr = raw.contains("Hata: VAR")

            // Regex ile sayaçları yakala
            val counterRegex = Regex("Sayaçlar[^:]*:\\s*(\\d+)\\s*/\\s*(\\d+)\\s*/\\s*(\\d+)")
            val match = counterRegex.find(raw)
            val dec = match?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val prn = match?.groupValues?.get(2)?.toIntOrNull() ?: 0
            val out = match?.groupValues?.get(3)?.toIntOrNull() ?: 0

            withContext(Dispatchers.Main) {
                hardwareDetails = PrinterHardwareDetails(
                    isConnected = true,
                    isValidData = true,
                    paperAvailable = !hasPaperNo,
                    bufferNormal = !isBufFull,
                    isBusy = isBusy,
                    isPrinting = isPrn,
                    hasHardwareError = hasErr,
                    pageDecoding = dec,
                    pagePrinting = prn,
                    pageOut = out,
                    lastCheckTime = timeStr
                )
                isQuerying = false
            }
        }
    }

    // Yazıcı durumu hazır olduğunda otomatik sorgula, bağlı değilse anında sıfırla
    LaunchedEffect(status) {
        if (status is PrinterStatus.Ready) {
            queryHardware()
        } else {
            hardwareDetails = PrinterHardwareDetails(
                isConnected = false,
                isValidData = false
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Büyük Bağlantı Durumu Kartı
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (status) {
                    is PrinterStatus.Ready -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    is PrinterStatus.Disconnected, is PrinterStatus.Error, is PrinterStatus.PaperOut, is PrinterStatus.PaperJam ->
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (status) {
                            is PrinterStatus.Ready -> Icons.Default.CheckCircle
                            is PrinterStatus.Disconnected, is PrinterStatus.Error -> Icons.Default.Error
                            else -> Icons.Default.Usb
                        },
                        contentDescription = null,
                        tint = when (status) {
                            is PrinterStatus.Ready -> StatusSuccess
                            is PrinterStatus.Disconnected, is PrinterStatus.Error -> StatusError
                            else -> StatusWarning
                        },
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = when (status) {
                            is PrinterStatus.Ready -> stringResource(R.string.status_ready)
                            is PrinterStatus.Disconnected -> stringResource(R.string.status_disconnected)
                            is PrinterStatus.PermissionNeeded -> stringResource(R.string.status_permission_needed)
                            is PrinterStatus.Connecting -> stringResource(R.string.status_connecting)
                            is PrinterStatus.Printing -> stringResource(R.string.status_printing)
                            is PrinterStatus.Paused -> "Yazdırma Duraklatıldı"
                            is PrinterStatus.WaitingForNextPage -> stringResource(R.string.status_waiting_next_page)
                            is PrinterStatus.PaperOut -> stringResource(R.string.status_paper_out)
                            is PrinterStatus.PaperJam -> stringResource(R.string.status_paper_jam)
                            is PrinterStatus.CoverOpen -> stringResource(R.string.status_cover_open)
                            is PrinterStatus.TonerLow -> stringResource(R.string.status_toner_low)
                            is PrinterStatus.WarmingUp -> stringResource(R.string.status_warming_up)
                            is PrinterStatus.Error -> (status as PrinterStatus.Error).userMessage
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = when (status) {
                        is PrinterStatus.Disconnected -> stringResource(R.string.error_printer_not_found)
                        is PrinterStatus.PermissionNeeded -> stringResource(R.string.error_permission_denied)
                        is PrinterStatus.PaperOut -> stringResource(R.string.error_paper_out_desc)
                        is PrinterStatus.PaperJam -> stringResource(R.string.error_paper_jam_desc)
                        is PrinterStatus.Ready -> "Yazıcı USB Bulk transfer üzerinden bağlı ve yazdırmaya hazır."
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (status is PrinterStatus.Disconnected || status is PrinterStatus.PermissionNeeded) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { repository.connect() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.btn_connect))
                    }
                }
            }
        }

        // Donanım Durumu GUI Paneli
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Canlı Donanım Durumu (0xA0A8)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (hardwareDetails.isConnected && hardwareDetails.isValidData) {
                            Text(
                                text = "Son kontrol: ${hardwareDetails.lastCheckTime}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (!hardwareDetails.isConnected) {
                            Text(
                                text = stringResource(R.string.hardware_status_offline),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = hardwareDetails.errorMessage ?: stringResource(R.string.hardware_status_unknown),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    IconButton(
                        onClick = { queryHardware() },
                        enabled = !isQuerying && (status is PrinterStatus.Ready)
                    ) {
                        if (isQuerying) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Yenile")
                        }
                    }
                }

                // Yazıcı bağlı değilken açık uyarı bildirimi
                if (!hardwareDetails.isConnected) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.hardware_status_disconnected_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2x2 Durum GUI Kartları (Yazıcı bağlı değilken kesinlikle olumlu/yeşil göstermez)
                val isConnected = hardwareDetails.isConnected && hardwareDetails.isValidData

                val paperVal = when {
                    !hardwareDetails.isConnected -> stringResource(R.string.hardware_status_offline)
                    !hardwareDetails.isValidData -> stringResource(R.string.hardware_status_unknown)
                    hardwareDetails.paperAvailable == true -> "Kağıt Var"
                    hardwareDetails.paperAvailable == false -> "Kağıt Yok"
                    else -> "-"
                }
                val paperPos = if (isConnected) hardwareDetails.paperAvailable else null

                val bufferVal = when {
                    !hardwareDetails.isConnected -> stringResource(R.string.hardware_status_offline)
                    !hardwareDetails.isValidData -> stringResource(R.string.hardware_status_unknown)
                    hardwareDetails.bufferNormal == true -> "Normal"
                    hardwareDetails.bufferNormal == false -> "Dolu"
                    else -> "-"
                }
                val bufferPos = if (isConnected) hardwareDetails.bufferNormal else null

                val motorVal = when {
                    !hardwareDetails.isConnected -> stringResource(R.string.hardware_status_offline)
                    !hardwareDetails.isValidData -> stringResource(R.string.hardware_status_unknown)
                    hardwareDetails.isPrinting == true -> "Yazıyor"
                    hardwareDetails.isBusy == true -> "Meşgul"
                    else -> "Boşta / Hazır"
                }
                val motorPos = if (isConnected) (hardwareDetails.isBusy != true) else null

                val healthVal = when {
                    !hardwareDetails.isConnected -> stringResource(R.string.hardware_status_offline)
                    !hardwareDetails.isValidData -> "İletişim Yok"
                    hardwareDetails.hasHardwareError == false -> "Hatasız"
                    hardwareDetails.hasHardwareError == true -> "Arıza / Hata"
                    else -> "-"
                }
                val healthPos = if (isConnected) (hardwareDetails.hasHardwareError != true) else null

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HardwareMetricItem(
                        icon = Icons.Default.Description,
                        title = "Kağıt Durumu",
                        value = paperVal,
                        isPositive = paperPos,
                        modifier = Modifier.weight(1f)
                    )
                    HardwareMetricItem(
                        icon = Icons.Default.Memory,
                        title = "Tampon Bellek",
                        value = bufferVal,
                        isPositive = bufferPos,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HardwareMetricItem(
                        icon = Icons.Default.Speed,
                        title = "Lazer Motoru",
                        value = motorVal,
                        isPositive = motorPos,
                        modifier = Modifier.weight(1f)
                    )
                    HardwareMetricItem(
                        icon = Icons.Default.Security,
                        title = "Donanım Sağlığı",
                        value = healthVal,
                        isPositive = healthPos,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Sayaç Şeridi
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Text(
                            text = "Çözülen: ${if (isConnected) (hardwareDetails.pageDecoding ?: 0) else "-"}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Basılan: ${if (isConnected) (hardwareDetails.pagePrinting ?: 0) else "-"}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Çıkan: ${if (isConnected) (hardwareDetails.pageOut ?: 0) else "-"}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Bağlantıyı Kes Butonu
        if (status is PrinterStatus.Ready) {
            OutlinedButton(
                onClick = { repository.disconnect() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.action_disconnect))
            }
        }
    }
}

@Composable
fun HardwareMetricItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    isPositive: Boolean?,
    modifier: Modifier = Modifier
) {
    val containerBg = when (isPositive) {
        true -> StatusSuccess.copy(alpha = 0.12f)
        false -> StatusError.copy(alpha = 0.12f)
        null -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    }
    val contentColor = when (isPositive) {
        true -> StatusSuccess
        false -> StatusError
        null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

@Composable
fun PrinterMaintenanceSubTab(
    status: PrinterStatus,
    repository: PrinterRepository
) {
    val coroutineScope = rememberCoroutineScope()
    var isPrintingTest by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Test Sayfası Kartı
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.action_print_test_page),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "LBP2900 Hi-SCoA dahili test desenini yazdırarak lazer motorunun ve kağıt sürüş mekanizmasının sağlığını test edin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        isPrintingTest = true
                        coroutineScope.launch {
                            repository.printTestPattern(0, false)
                            isPrintingTest = false
                        }
                    },
                    enabled = !isPrintingTest && (status is PrinterStatus.Ready),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isPrintingTest) "Yazdırılıyor..." else stringResource(R.string.action_print_test_page))
                }
            }
        }

        // Sık Karşılaşılan Sorunlar Rehberi
        Text(
            text = stringResource(R.string.printer_troubleshoot_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        TroubleshootItem(
            question = stringResource(R.string.faq_q1),
            answer = stringResource(R.string.faq_a1)
        )
        TroubleshootItem(
            question = stringResource(R.string.faq_q2),
            answer = stringResource(R.string.faq_a2)
        )
        TroubleshootItem(
            question = stringResource(R.string.faq_q3),
            answer = stringResource(R.string.faq_a3)
        )
    }
}

@Composable
fun TroubleshootItem(
    question: String,
    answer: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = answer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PrinterInfoSubTab(
    repository: PrinterRepository
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Canon LBP2900 / LBP3000 Series",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(text = stringResource(R.string.info_connection_type), style = MaterialTheme.typography.bodyMedium)
                Text(text = stringResource(R.string.info_driver_version), style = MaterialTheme.typography.bodyMedium)
                Text(text = stringResource(R.string.info_app_version), style = MaterialTheme.typography.bodyMedium)
                Text(text = stringResource(R.string.info_toner_support), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        OutlinedButton(
            onClick = {
                val diagText = repository.queryStatus()
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, "ohmycapT Tanı Raporu:\n$diagText")
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, "Tanı Raporunu Paylaş"))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.action_share_diagnostics))
        }
    }
}
