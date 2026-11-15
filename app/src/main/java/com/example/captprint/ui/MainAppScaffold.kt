package com.example.captprint.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.captprint.data.DocumentPreparationEngine
import com.example.captprint.data.PreparedDocument
import com.example.captprint.data.repository.PrinterRepository
import com.example.captprint.domain.model.PrinterStatus
import com.example.captprint.feature.history.HistoryScreen
import com.example.captprint.feature.home.HomeScreen
import com.example.captprint.feature.printconfig.PrintConfigScreen
import com.example.captprint.feature.printer.PrinterScreen
import com.example.captprint.feature.progress.PrintProgressScreen
import com.example.captprint.feature.settings.SettingsScreen
import com.example.captprint.ui.components.GlobalStatusBar
import com.example.captprint.ui.navigation.TopLevelDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ScreenState {
    ONBOARDING,
    MAIN_TABS,
    PRINT_CONFIG,
    PRINT_PROGRESS
}

@Composable
fun MainAppScaffold(
    repository: PrinterRepository,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val status by repository.printerStatus.collectAsState(initial = PrinterStatus.Disconnected)

    var screenState by rememberSaveable { mutableStateOf(ScreenState.MAIN_TABS) }
    var selectedDestination by rememberSaveable { mutableStateOf(TopLevelDestination.PRINT) }

    var preparedDoc by remember { mutableStateOf<PreparedDocument?>(null) }
    var isPrintCompleted by remember { mutableStateOf(false) }

    when (screenState) {
        ScreenState.ONBOARDING -> {
            com.example.captprint.feature.onboarding.OnboardingScreen(
                onFinish = { screenState = ScreenState.MAIN_TABS }
            )
        }
        ScreenState.PRINT_CONFIG -> {
            preparedDoc?.let { doc ->
                PrintConfigScreen(
                    document = doc,
                    printerStatus = status,
                    onBackClick = { screenState = ScreenState.MAIN_TABS },
                    onStartPrint = { settings ->
                        isPrintCompleted = false
                        screenState = ScreenState.PRINT_PROGRESS
                        coroutineScope.launch {
                            val res = repository.printDocument(
                                uri = Uri.fromFile(doc.pdfFile),
                                settings = settings,
                                onProgress = { _, _, _, _ -> }
                            )
                            val job = com.example.captprint.data.db.PrintJobEntity(
                                title = doc.title,
                                pageCount = doc.pageCount,
                                timestamp = System.currentTimeMillis(),
                                isSuccess = res.isSuccess,
                                copies = settings.copies,
                                manualFeedUsed = settings.manualFeedStepByStep,
                                failureReason = res.exceptionOrNull()?.message
                            )
                            withContext(Dispatchers.IO) {
                                com.example.captprint.data.db.AppDatabase.getInstance(context).printHistoryDao().insertJob(job)
                            }
                            if (res.isSuccess) {
                                isPrintCompleted = true
                            }
                        }
                    }
                )
            }
        }
        ScreenState.PRINT_PROGRESS -> {
            preparedDoc?.let { doc ->
                PrintProgressScreen(
                    repository = repository,
                    documentTitle = doc.title,
                    totalPages = doc.pageCount,
                    isCompleted = isPrintCompleted,
                    onDoneClick = {
                        screenState = ScreenState.MAIN_TABS
                        preparedDoc = null
                    },
                    onReprintClick = {
                        isPrintCompleted = false
                    }
                )
            }
        }
        ScreenState.MAIN_TABS -> {
            val useNavigationRail = widthSizeClass != WindowWidthSizeClass.Compact

            if (useNavigationRail) {
                // Tablet ve Katlanabilir Ekranlar: Sol NavigationRail
                Row(modifier = modifier.fillMaxSize()) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        TopLevelDestination.entries.forEach { destination ->
                            NavigationRailItem(
                                selected = selectedDestination == destination,
                                onClick = { selectedDestination = destination },
                                icon = { Icon(destination.icon, contentDescription = stringResource(destination.titleRes)) },
                                label = { Text(stringResource(destination.titleRes)) }
                            )
                        }
                    }
                    Column(modifier = Modifier.fillMaxSize()) {
                        GlobalStatusBar(
                            status = status,
                            onStatusBarClick = { selectedDestination = TopLevelDestination.PRINTER },
                            onConnectClick = { repository.connect() }
                        )
                        AppScreenContent(
                            destination = selectedDestination,
                            repository = repository,
                            onDocumentReady = { doc ->
                                preparedDoc = doc
                                screenState = ScreenState.PRINT_CONFIG
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                // 7. İSTEK: TELEFON EKRANLARI İÇİN HAP BAR (FLOATING CAPSULE NAVIGATION BAR)
                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        FloatingPillNavigationBar(
                            selectedDestination = selectedDestination,
                            onDestinationSelected = { selectedDestination = it }
                        )
                    },
                    modifier = modifier.fillMaxSize()
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        GlobalStatusBar(
                            status = status,
                            onStatusBarClick = { selectedDestination = TopLevelDestination.PRINTER },
                            onConnectClick = { repository.connect() }
                        )
                        AppScreenContent(
                            destination = selectedDestination,
                            repository = repository,
                            onDocumentReady = { doc ->
                                preparedDoc = doc
                                screenState = ScreenState.PRINT_CONFIG
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = 76.dp) // Hap barın içeriği kapatmaması için alt boşluk
                        )
                    }
                }
            }
        }
    }
}

/**
 * 7. İSTEK: HAP BAR (FLOATING CAPSULE NAVIGATION BAR)
 * Modern, şık ve yüzen kapsül gezinti çubuğu.
 */
@Composable
fun FloatingPillNavigationBar(
    selectedDestination: TopLevelDestination,
    onDestinationSelected: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
            shadowElevation = 10.dp,
            tonalElevation = 6.dp,
            modifier = Modifier
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TopLevelDestination.entries.forEach { destination ->
                    val isSelected = selectedDestination == destination
                    PillNavItem(
                        destination = destination,
                        isSelected = isSelected,
                        onClick = { onDestinationSelected(destination) }
                    )
                }
            }
        }
    }
}

@Composable
fun PillNavItem(
    destination: TopLevelDestination,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val animatedBgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pillBgColor"
    )
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(animatedBgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = if (isSelected) 14.dp else 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = stringResource(destination.titleRes),
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            AnimatedVisibility(visible = isSelected) {
                Row {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(destination.titleRes),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
fun AppScreenContent(
    destination: TopLevelDestination,
    repository: PrinterRepository,
    onDocumentReady: (PreparedDocument) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        when (destination) {
            TopLevelDestination.PRINT -> HomeScreen(
                repository = repository,
                onFilesSelected = { uris ->
                    coroutineScope.launch(Dispatchers.IO) {
                        val doc = DocumentPreparationEngine.prepareDocument(context, uris)
                        if (doc != null) {
                            withContext(Dispatchers.Main) {
                                onDocumentReady(doc)
                            }
                        }
                    }
                }
            )
            TopLevelDestination.PRINTER -> PrinterScreen(repository = repository)
            TopLevelDestination.HISTORY -> HistoryScreen()
            TopLevelDestination.SETTINGS -> SettingsScreen()
        }
    }
}
