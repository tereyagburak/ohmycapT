package com.example.captprint.feature.settings

import android.content.Intent
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.captprint.CaptApplication
import com.example.captprint.R
import com.example.captprint.domain.model.PrinterStatus

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = CaptApplication.instance

    var devClickCount by remember { mutableIntStateOf(0) }
    var isDevModeUnlocked by remember { mutableStateOf(false) }
    var isUsingFake by remember { mutableStateOf(app.isUsingFakeRepository) }

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
                    text = "Sürüm: 1.0 (Derleme 20261111) - Geliştirici için 7 kez dokunun",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        devClickCount++
                        if (devClickCount >= 7 && !isDevModeUnlocked) {
                            isDevModeUnlocked = true
                            Toast.makeText(context, context.getString(R.string.toast_dev_unlocked), Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        // 3. GELİŞTİRİCİ SEÇENEKLERİ & FAKE SIMULATOR (7 TIK KİLİDİ AÇILINCA)
        AnimatedVisibility(visible = isDevModeUnlocked) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.settings_developer_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

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
                            onCheckedChange = {
                                isUsingFake = it
                                app.switchToFake(it)
                            }
                        )
                    }

                    if (isUsingFake) {
                        Text(text = "Simülatör Durum Tetikleyicileri:", fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { app.fakeRepository.setFakeStatus(PrinterStatus.PaperOut) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Kağıt Bitti", style = MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                onClick = { app.fakeRepository.setFakeStatus(PrinterStatus.PaperJam) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Sıkıştı", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { app.fakeRepository.setFakeStatus(PrinterStatus.Ready("Canon LBP2900 (Simülatör)", "USB 1-1.2")) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Hazır Yap", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = { app.fakeRepository.setFakeStatus(PrinterStatus.Disconnected) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Bağlantı Kes", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
