package com.example.captprint.feature.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.captprint.CaptApplication
import com.example.captprint.R
import com.example.captprint.data.db.AppDatabase
import com.example.captprint.data.db.PrintJobEntity
import com.example.captprint.domain.model.PrinterStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    onLaunchOnboarding: () -> Unit = {},
    onOpenSampleConfig: () -> Unit = {},
    onRunSimulatedPrint: (manualFeedStepByStep: Boolean, simulateError: Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val app = CaptApplication.instance
    val soundManager = app.soundManager
    val isSoundsEnabled by soundManager.isSoundsEnabled.collectAsState()
    val isUsingFake by app.isUsingFake.collectAsState()
    val isDevModeUnlocked by app.isDevModeUnlocked.collectAsState()
    val activeRepo by app.activeRepository.collectAsState()
    val currentStatus by (activeRepo ?: app.realRepository).printerStatus.collectAsState()
    val historyJobs by AppDatabase.getInstance(context).printHistoryDao().getAllJobs().collectAsState(initial = emptyList())

    var devClickCount by remember { mutableIntStateOf(0) }
    var fastSim by remember { mutableStateOf(app.fakeRepository.fastSimulation) }
    var simErrorMid by remember { mutableStateOf(app.fakeRepository.simulateErrorMidPrint) }
    var isPlayingSequence by remember { mutableStateOf(false) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val mediaVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
    val mediaMaxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 1
    val mediaVolPercent = (mediaVol * 100) / mediaMaxVol
    val hasUsbHost = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // 1. Yazdırma Varsayılanları
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.settings_title_defaults),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(text = "Varsayılan Kağıt: A4 (210 x 297 mm)", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Varsayılan Yoğunluk: 7 (Koyu)", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Dither Yöntemi: Floyd-Steinberg", style = MaterialTheme.typography.bodyMedium)
            }
        }

        // 1b. Canon Sürücü Sesleri
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.settings_title_sounds),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_sounds_enable),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.settings_sounds_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isSoundsEnabled,
                        onCheckedChange = { soundManager.setSoundsEnabled(it) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { soundManager.playPrintDone(forcePreview = true) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.sound_test_done), style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { soundManager.playStepPageReady(forcePreview = true) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.sound_test_step), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { soundManager.playPrintError(forcePreview = true) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.sound_test_error), style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { soundManager.playWarning(forcePreview = true) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.sound_test_warn), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // 2. Hakkında & 7 Tık Geliştirici Kilidi
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_about_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_about_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/tereyagburak/ohmycapT"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("⭐ GitHub'da İncele & Yıldızla (Star Ver!)")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isDevModeUnlocked) {
                        "Sürüm: 1.0 (Derleme 20261115) • Geliştirici Modu Açık"
                    } else {
                        "Sürüm: 1.0 (Derleme 20261115) - Geliştirici için 7 kez dokunun"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        devClickCount++
                        if (devClickCount >= 7 && !isDevModeUnlocked) {
                            app.setDevModeUnlocked(true)
                            Toast.makeText(context, context.getString(R.string.toast_dev_unlocked), Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        // 3. GELİŞTİRİCİ ARAÇLARI & TEST MERKEZİ
        AnimatedVisibility(visible = isDevModeUnlocked) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_developer_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(onClick = {
                            devClickCount = 0
                            app.setDevModeUnlocked(false)
                        }) {
                            Text("Kilitle", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    // A. SİMÜLATÖR MOTORU AYARLARI
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.settings_use_fake_repo), fontWeight = FontWeight.SemiBold)
                            Text(
                                text = stringResource(R.string.settings_use_fake_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isUsingFake,
                            onCheckedChange = { app.switchToFake(it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Hızlı Simülasyon Modu", fontWeight = FontWeight.Medium)
                            Text(
                                text = "Sayfa gecikmelerini kısaltarak hızlı test yapar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = fastSim,
                            onCheckedChange = {
                                fastSim = it
                                app.fakeRepository.fastSimulation = it
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Sayfa 2'de Hata Simüle Et", fontWeight = FontWeight.Medium)
                            Text(
                                text = "Simülatörde 2. sayfada USB timeout hatası üretir.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = simErrorMid,
                            onCheckedChange = {
                                simErrorMid = it
                                app.fakeRepository.simulateErrorMidPrint = it
                            }
                        )
                    }

                    HorizontalDivider()

                    // B. UÇTAN UCA BASKI & HATA SENARYOLARI
                    Text(text = "Uçtan Uca Baskı Senaryoları (PDF Seçmeden Test):", fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                simErrorMid = false
                                onRunSimulatedPrint(false, false)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Normal Baskı (3s)", style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = {
                                simErrorMid = false
                                onRunSimulatedPrint(true, false)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Tek Tek Baskı", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                simErrorMid = true
                                onRunSimulatedPrint(false, true)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Hata Senaryosu", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = onOpenSampleConfig,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Örnek PDF Ayarları", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    HorizontalDivider()

                    // C. SES TEST MERKEZİ (CANON WAV DİAGNOSTİK)
                    Text(
                        text = "Ses Test Merkezi (Medya Sesi: %$mediaVolPercent)",
                        fontWeight = FontWeight.Bold
                    )
                    if (mediaVol == 0) {
                        Text(
                            text = "Uyarı: Cihaz medya sesi %0. Sesleri duymak için medya sesini açın.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { soundManager.playPrintDone(forcePreview = true) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("cnab4dn (Bitti)", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { soundManager.playStepPageReady(forcePreview = true) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("cnab4rm (Sayfa)", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { soundManager.playPrintError(forcePreview = true) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("cnab4er (Hata)", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { soundManager.playWarning(forcePreview = true) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("cnab4se (Uyarı)", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Button(
                        onClick = {
                            if (!isPlayingSequence) {
                                isPlayingSequence = true
                                coroutineScope.launch {
                                    soundManager.playStepPageReady(forcePreview = true)
                                    delay(900)
                                    soundManager.playPrintDone(forcePreview = true)
                                    delay(1100)
                                    soundManager.playWarning(forcePreview = true)
                                    delay(1000)
                                    soundManager.playPrintError(forcePreview = true)
                                    isPlayingSequence = false
                                }
                            }
                        },
                        enabled = !isPlayingSequence,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isPlayingSequence) "Sesler Sırayla Çalınıyor..." else "Tüm Sürücü Seslerini Sırayla Çal")
                    }

                    HorizontalDivider()

                    // D. TÜM YAZICI DURUM TETİKLEYİCİLERİ
                    Text(text = "Tüm Yazıcı Durum Tetikleyicileri:", fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                app.switchToFake(true)
                                app.fakeRepository.setFakeStatus(PrinterStatus.Ready("Canon LBP2900 (Simülatör)", "USB 1-1.2"))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Hazır", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = {
                                app.switchToFake(true)
                                app.fakeRepository.setFakeStatus(PrinterStatus.WarmingUp("Canon LBP2900 (Simülatör)"))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Isınıyor", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = {
                                app.switchToFake(true)
                                app.fakeRepository.setFakeStatus(PrinterStatus.Connecting("USB 1-1.2"))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Bağlanıyor", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                app.switchToFake(true)
                                app.fakeRepository.setFakeStatus(PrinterStatus.PaperOut)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Kağıt Bitti", style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = {
                                app.switchToFake(true)
                                app.fakeRepository.setFakeStatus(PrinterStatus.PaperJam)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Sıkıştı", style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = {
                                app.switchToFake(true)
                                app.fakeRepository.setFakeStatus(PrinterStatus.CoverOpen)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Kapak Açık", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                app.switchToFake(true)
                                app.fakeRepository.setFakeStatus(PrinterStatus.TonerLow)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Toner Az", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = {
                                app.switchToFake(true)
                                app.fakeRepository.setFakeStatus(PrinterStatus.PermissionNeeded("USB 1-1.2"))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("İzin Bekle", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = {
                                app.switchToFake(true)
                                app.fakeRepository.setFakeStatus(PrinterStatus.Disconnected)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Kesildi", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Button(
                        onClick = {
                            app.switchToFake(true)
                            app.fakeRepository.setFakeStatus(
                                PrinterStatus.Error(
                                    code = -10,
                                    userMessage = "Simüle Edilmiş Donanım Hatası",
                                    technicalDetail = "CAPT_STATUS_ENGINE_ERROR (0xA0A8: 0x0010)"
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Kritik Hata Durumu Tetikle (Hata Sesi + Durum)")
                    }

                    HorizontalDivider()

                    // E. UI, VERİTABANI & TANI ARAÇLARI
                    Text(text = "UI, Veritabanı & Sistem Tanı:", fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onLaunchOnboarding,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Onboarding Aç", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    val dao = AppDatabase.getInstance(context).printHistoryDao()
                                    val now = System.currentTimeMillis()
                                    dao.insertJob(PrintJobEntity(title = "Fatura_Ornek.pdf", pageCount = 2, timestamp = now - 60_000, isSuccess = true, copies = 1, manualFeedUsed = false))
                                    dao.insertJob(PrintJobEntity(title = "Kimlik_TekTek.pdf", pageCount = 4, timestamp = now - 30_000, isSuccess = true, copies = 1, manualFeedUsed = true))
                                    dao.insertJob(PrintJobEntity(title = "Bozuk_Aktarim.pdf", pageCount = 5, timestamp = now, isSuccess = false, copies = 1, manualFeedUsed = false, failureReason = "USB Timeout (-7)"))
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "3 örnek geçmiş kaydı eklendi", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Örnek Geçmiş (+3)", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                AppDatabase.getInstance(context).printHistoryDao().clearHistory()
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Geçmiş veritabanı temizlendi", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Geçmiş Veritabanını Temizle")
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "Canlı Tanı Özeti", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text(text = "Repository: ${if (isUsingFake) "FakePrinterRepository (Simülatör)" else "RealPrinterRepository (USB)"}", style = MaterialTheme.typography.bodySmall)
                            Text(text = "Durum: $currentStatus", style = MaterialTheme.typography.bodySmall)
                            Text(text = "USB Host Desteği: ${if (hasUsbHost) "VAR" else "YOK"}", style = MaterialTheme.typography.bodySmall)
                            Text(text = "Sesler: ${if (isSoundsEnabled) "AÇIK" else "KAPALI"} (Medya: %$mediaVolPercent)", style = MaterialTheme.typography.bodySmall)
                            Text(text = "Geçmiş Kayıt Sayısı: ${historyJobs.size}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
