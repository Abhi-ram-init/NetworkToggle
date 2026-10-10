package com.example.networktoggle.ui.main

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.networktoggle.macro.LogLevel
import com.example.networktoggle.macro.MacroExecutionState
import com.example.networktoggle.macro.MacroManager
import com.example.networktoggle.macro.MacroType
import com.example.networktoggle.network.AppUiStyle
import com.example.networktoggle.network.NetworkMode
import com.example.networktoggle.network.NetworkModeManager
import com.example.networktoggle.widget.CompactToggleWidget
import com.example.networktoggle.widget.DashboardToggleWidget
import com.example.networktoggle.widget.NetworkToggleWidget

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = run {
        val context = LocalContext.current.applicationContext
        val nmm = NetworkModeManager(context)
        val mm = MacroManager(context, nmm)
        androidx.lifecycle.viewmodel.compose.viewModel(
            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    MainScreenViewModel(nmm, mm) as T
            }
        )
    },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAppResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onPermissionGranted()
        }
    }

    val onKernelGrant: () -> Unit = {
        viewModel.requestKernelRootGrant { granted ->
            Toast.makeText(
                context,
                if (granted) "⚡ Kernel permission granted! Switch executed."
                else "Kernel root request denied or not available.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    MainScreenContent(
        uiState = uiState,
        onSetUiStyle = viewModel::setUiStyle,
        onSelectChoice = { mode ->
            viewModel.selectChoice(mode)
            viewModel.applySelectedSwitch(context)
        },
        onToggleDial = { viewModel.toggleQuick(context) },
        onRequestPermission = {
            permissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        },
        onRequestKernelGrant = onKernelGrant,
        onOpenRadioInfo = { viewModel.openRadioInfo(context) },
        onOpenSettings = { viewModel.openMobileSettings(context) },
        onRefresh = viewModel::refreshLiveNetwork,
        onDismissStatus = viewModel::dismissStatus,
        onSelectMacro = viewModel::selectMacroType,
        onRunMacro = viewModel::runSelectedMacro,
        onClearMacroLogs = viewModel::clearMacroLogs,
        onRequestAddControlPanelTile = { isMacro ->
            viewModel.requestAddTileToControlPanel(isMacro, context) { msg ->
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        },
        modifier = modifier
    )
}

@Composable
private fun MainScreenContent(
    uiState: MainScreenUiState,
    onSetUiStyle: (AppUiStyle) -> Unit,
    onSelectChoice: (NetworkMode) -> Unit,
    onToggleDial: () -> Unit,
    onRequestPermission: () -> Unit,
    onRequestKernelGrant: () -> Unit,
    onOpenRadioInfo: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onDismissStatus: () -> Unit,
    onSelectMacro: (MacroType) -> Unit,
    onRunMacro: () -> Unit,
    onClearMacroLogs: () -> Unit,
    onRequestAddControlPanelTile: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val is5G = uiState.currentConnectedMode == NetworkMode.FIVE_G
    val primaryColor = when (uiState.uiStyle) {
        AppUiStyle.MINIMAL_CLEAN -> if (is5G) Color(0xFF10B981) else Color(0xFF38BDF8)
        AppUiStyle.CYBER_NEON -> if (is5G) Color(0xFF00F5D4) else Color(0xFF7B2CBF)
        AppUiStyle.SPEEDOMETER -> if (is5G) Color(0xFFFF0055) else Color(0xFF00E5FF)
    }

    val bgColor = when (uiState.uiStyle) {
        AppUiStyle.MINIMAL_CLEAN -> Color(0xFF0A0E17)
        AppUiStyle.CYBER_NEON -> Color(0xFF07080D)
        AppUiStyle.SPEEDOMETER -> Color(0xFF05070D)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Minimal Header Bar
            MinimalTopBar(
                uiState = uiState,
                primaryColor = primaryColor,
                onRefresh = onRefresh,
                onSetUiStyle = onSetUiStyle
            )

            // Permission Warning Pill (Compact & only if needed)
            if (!uiState.hasPermission) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E293B),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRequestPermission() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable phone permission for real-time 5G/4G detection",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "ENABLE",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Hero Connection Card
            when (uiState.uiStyle) {
                AppUiStyle.MINIMAL_CLEAN -> {
                    MinimalHeroCard(
                        uiState = uiState,
                        primaryColor = primaryColor,
                        onToggle = onToggleDial
                    )
                }
                AppUiStyle.CYBER_NEON -> {
                    CyberHeroCard(
                        uiState = uiState,
                        accentColor = primaryColor,
                        onToggle = onToggleDial
                    )
                }
                AppUiStyle.SPEEDOMETER -> {
                    SpeedometerHeroCard(
                        uiState = uiState,
                        meterColor = primaryColor,
                        onToggle = onToggleDial
                    )
                }
            }

            // Inline Status Feedback
            InlineStatusIndicator(
                status = uiState.switchStatus,
                switchingColor = Color(0xFFF59E0B),
                failedColor = Color(0xFFEF4444),
                onOpenRadioInfo = onOpenRadioInfo,
                onDismissStatus = onDismissStatus
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Segmented Mode Selector
            SegmentedModeSelector(
                selectedMode = uiState.selectedChoice,
                accentColor = primaryColor,
                onSelectMode = onSelectChoice
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Clean Macro Hub
            CleanMacroSection(
                uiState = uiState,
                accentColor = primaryColor,
                onSelectMacro = onSelectMacro,
                onRunMacro = onRunMacro,
                onClearLogs = onClearMacroLogs
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Clean Tiles & Widgets Hub
            CleanWidgetsSection(
                accentColor = primaryColor,
                onRequestAddTile = onRequestAddControlPanelTile
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Direct Hardware Tools & 1-Tap Toggle Setup
            CleanToolsSection(
                uiState = uiState,
                accentColor = primaryColor,
                onRequestKernelGrant = onRequestKernelGrant,
                onOpenRadioInfo = onOpenRadioInfo,
                onOpenSettings = onOpenSettings
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

// -------------------------------------------------------------
// COMPACT TOP BAR
// -------------------------------------------------------------
@Composable
private fun MinimalTopBar(
    uiState: MainScreenUiState,
    primaryColor: Color,
    onRefresh: () -> Unit,
    onSetUiStyle: (AppUiStyle) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(primaryColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Network Toggle",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Minimal UI Style Switcher Pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.06f),
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Row(modifier = Modifier.padding(2.dp)) {
                    AppUiStyle.entries.forEach { style ->
                        val isSelected = uiState.uiStyle == style
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) primaryColor.copy(alpha = 0.2f) else Color.Transparent,
                            modifier = Modifier
                                .clickable { onSetUiStyle(style) }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = style.label(),
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) primaryColor else Color.White.copy(alpha = 0.45f)
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// HERO CARDS (MINIMAL, CYBER, SPEEDOMETER)
// -------------------------------------------------------------
@Composable
private fun MinimalHeroCard(
    uiState: MainScreenUiState,
    primaryColor: Color,
    onToggle: () -> Unit,
) {
    val is5G = uiState.currentConnectedMode == NetworkMode.FIVE_G
    val isBusy = uiState.switchStatus is SwitchStatus.Switching

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF131926),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = uiState.inspectedProcess?.carrierName?.uppercase() ?: "CELLULAR",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.45f),
                    letterSpacing = 1.sp
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = primaryColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = if (is5G) "NR STANDALONE" else "LTE HIGH-SPEED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = uiState.currentConnectedMode.label(),
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )

            Text(
                text = uiState.liveNetwork.displayName,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onToggle,
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = if (is5G) "Switch to 4G LTE" else "Switch to 5G NR",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
private fun CyberHeroCard(
    uiState: MainScreenUiState,
    accentColor: Color,
    onToggle: () -> Unit,
) {
    val is5G = uiState.currentConnectedMode == NetworkMode.FIVE_G
    val isBusy = uiState.switchStatus is SwitchStatus.Switching
    val dialScale by animateFloatAsState(targetValue = if (isBusy) 0.95f else 1f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "cyberScale")

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(vertical = 10.dp)
            .scale(dialScale)
            .size(190.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(accentColor.copy(alpha = 0.18f), Color.Transparent)))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onToggle, enabled = !isBusy)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .border(BorderStroke(2.dp, accentColor.copy(alpha = 0.6f)), CircleShape)
                .background(Color(0xFF0B0E17))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.SignalCellularAlt, contentDescription = null, tint = accentColor, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = uiState.currentConnectedMode.shortLabel(), fontSize = 42.sp, fontWeight = FontWeight.Black, color = accentColor)
                Text(text = if (is5G) "NR STANDALONE" else "LTE ACTIVE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f), letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun SpeedometerHeroCard(
    uiState: MainScreenUiState,
    meterColor: Color,
    onToggle: () -> Unit,
) {
    val is5G = uiState.currentConnectedMode == NetworkMode.FIVE_G

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF0F1320),
        border = BorderStroke(1.dp, meterColor.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Speed, contentDescription = null, tint = meterColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (is5G) "TURBO 5G BANDWIDTH" else "STANDARD 4G BANDWIDTH",
                    color = meterColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(text = if (is5G) "5G NR" else "4G LTE", fontSize = 38.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text(text = uiState.liveNetwork.displayName, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onToggle,
                colors = ButtonDefaults.buttonColors(containerColor = meterColor),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text(
                    text = if (is5G) "SWITCH TO 4G LTE" else "SWITCH TO 5G TURBO",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.Black
                )
            }
        }
    }
}

// -------------------------------------------------------------
// SEGMENTED MODE SELECTOR
// -------------------------------------------------------------
@Composable
private fun SegmentedModeSelector(
    selectedMode: NetworkMode,
    accentColor: Color,
    onSelectMode: (NetworkMode) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF131722),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            listOf(
                Triple(NetworkMode.FIVE_G, "5G NR", Color(0xFF00F5D4)),
                Triple(NetworkMode.AUTO, "Auto", Color(0xFF38BDF8)),
                Triple(NetworkMode.FOUR_G, "4G LTE", Color(0xFF818CF8))
            ).forEach { (mode, label, _) ->
                val isSelected = selectedMode == mode
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) accentColor.copy(alpha = 0.18f) else Color.Transparent,
                    border = if (isSelected) BorderStroke(1.dp, accentColor) else null,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectMode(mode) }
                ) {
                    Text(
                        text = label,
                        textAlign = TextAlign.Center,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp,
                        color = if (isSelected) accentColor else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// CLEAN MACRO SECTION
// -------------------------------------------------------------
@Composable
private fun CleanMacroSection(
    uiState: MainScreenUiState,
    accentColor: Color,
    onSelectMacro: (MacroType) -> Unit,
    onRunMacro: () -> Unit,
    onClearLogs: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val isRunning = uiState.macroState is MacroExecutionState.Running
    var isConsoleExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF111520),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header with process indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = accentColor, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "AUTOMATED MACRO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f), letterSpacing = 1.sp)
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "PID: ${uiState.inspectedProcess?.phonePid ?: "ACTIVE"} • phone",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Macro Presets Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MacroType.entries.forEach { type ->
                    val isSelected = uiState.selectedMacroType == type
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) accentColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f),
                        border = if (isSelected) BorderStroke(1.dp, accentColor) else null,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectMacro(type) }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = type.icon, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (type) {
                                    MacroType.TURBO_5G_LOCK -> "5G Lock"
                                    MacroType.TOWER_REFRESH -> "Reseat"
                                    MacroType.BATTERY_ECO_4G -> "Eco 4G"
                                },
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) accentColor else Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = uiState.selectedMacroType.shortDesc,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.45f),
                lineHeight = 14.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Run Button
            Button(
                onClick = onRunMacro,
                enabled = !isRunning,
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
            ) {
                if (isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.Black, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    val runningState = uiState.macroState as? MacroExecutionState.Running
                    Text(text = runningState?.step ?: "EXECUTING…", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "RUN ${uiState.selectedMacroType.displayName.uppercase()}", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Black)
                }
            }

            // Compact Log Strip & Expandable Console
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isConsoleExpanded = !isConsoleExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val latestLog = uiState.macroLogs.lastOrNull()?.message ?: "Terminal ready"
                Text(
                    text = "▶ $latestLog",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (isConsoleExpanded) "Hide" else "Console",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            }

            AnimatedVisibility(
                visible = isConsoleExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF07090F),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "LOG TRACE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.4f))
                                Row {
                                    Text(
                                        text = "COPY",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accentColor,
                                        modifier = Modifier.clickable {
                                            val text = uiState.macroLogs.joinToString("\n") { "[${it.timestamp}] ${it.message}" }
                                            clipboardManager.setText(AnnotatedString(text))
                                            Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "CLEAR",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.4f),
                                        modifier = Modifier.clickable { onClearLogs() }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 140.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    uiState.macroLogs.forEach { log ->
                                        val color = when (log.level) {
                                            LogLevel.SUCCESS -> Color(0xFF10B981)
                                            LogLevel.EXEC -> Color(0xFFF59E0B)
                                            LogLevel.PROCESS -> Color(0xFF38BDF8)
                                            else -> Color.White.copy(alpha = 0.65f)
                                        }
                                        Text(text = "[${log.timestamp}] ${log.message}", fontSize = 9.sp, color = color, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// CLEAN TILES & WIDGETS SECTION
// -------------------------------------------------------------
@Composable
private fun CleanWidgetsSection(
    accentColor: Color,
    onRequestAddTile: (Boolean) -> Unit,
) {
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF111520),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Widgets, contentDescription = null, tint = accentColor, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "TILES & WIDGETS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f), letterSpacing = 1.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Control Panel Tiles Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactAddButton(
                    title = "Control Panel: Toggle",
                    badge = "Tile",
                    accentColor = Color(0xFF00F5D4),
                    modifier = Modifier.weight(1f),
                    onClick = { onRequestAddTile(false) }
                )
                CompactAddButton(
                    title = "Control Panel: Macro",
                    badge = "Tile",
                    accentColor = Color(0xFFA855F7),
                    modifier = Modifier.weight(1f),
                    onClick = { onRequestAddTile(true) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Home Screen Widgets Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactAddButton(
                    title = "1x1 Dial",
                    badge = "Home",
                    accentColor = Color(0xFF38BDF8),
                    modifier = Modifier.weight(1f),
                    onClick = { pinWidgetToHomeScreen(context, CompactToggleWidget::class.java) }
                )
                CompactAddButton(
                    title = "2x1 Pill",
                    badge = "Home",
                    accentColor = Color(0xFF38BDF8),
                    modifier = Modifier.weight(1f),
                    onClick = { pinWidgetToHomeScreen(context, NetworkToggleWidget::class.java) }
                )
                CompactAddButton(
                    title = "4x2 Dash",
                    badge = "Home",
                    accentColor = Color(0xFF8B5CF6),
                    modifier = Modifier.weight(1f),
                    onClick = { pinWidgetToHomeScreen(context, DashboardToggleWidget::class.java) }
                )
            }
        }
    }
}

@Composable
private fun CompactAddButton(
    title: String,
    badge: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                Text(text = badge, fontSize = 8.sp, color = accentColor)
            }
            Icon(Icons.Default.Add, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
        }
    }
}

// -------------------------------------------------------------
// CLEAN TOOLS & 1-TAP TOGGLE SETUP
// -------------------------------------------------------------
@Composable
private fun CleanToolsSection(
    uiState: MainScreenUiState,
    accentColor: Color,
    onRequestKernelGrant: () -> Unit,
    onOpenRadioInfo: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var isSetupExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Force Menu Button
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF111520),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpenRadioInfo() }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color(0xFF00F5D4), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = "Force Menu", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(text = "*#*#4636#*#*", fontSize = 9.sp, color = Color.White.copy(alpha = 0.45f))
                    }
                }
            }

            // SIM Settings Button
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF111520),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpenSettings() }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = "SIM Settings", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(text = "Android System", fontSize = 9.sp, color = Color.White.copy(alpha = 0.45f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Direct Toggle Status / Setup Pill
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF111520),
            border = BorderStroke(1.dp, if (uiState.isDirectToggleGranted) Color(0xFF10B981).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.06f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { if (!uiState.isDirectToggleGranted) isSetupExpanded = !isSetupExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (uiState.isDirectToggleGranted) Icons.Default.CheckCircle else Icons.Default.Tune,
                            contentDescription = null,
                            tint = if (uiState.isDirectToggleGranted) Color(0xFF10B981) else Color(0xFFF59E0B),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.isDirectToggleGranted) "1-Tap Direct Toggle: Active" else "1-Tap Direct Toggle: Setup",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    if (!uiState.isDirectToggleGranted) {
                        Icon(
                            imageVector = if (isSetupExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (!uiState.isDirectToggleGranted && isSetupExpanded) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Grant permission once via Kernel Root or ADB to switch without opening menus:",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onRequestKernelGrant,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Text(text = "Grant via Root", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                        }
                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(uiState.adbGrantCommand))
                                Toast.makeText(context, "ADB command copied!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Text(text = "Copy ADB Command", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00F5D4))
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// INLINE STATUS INDICATOR
// -------------------------------------------------------------
@Composable
private fun InlineStatusIndicator(
    status: SwitchStatus,
    switchingColor: Color,
    failedColor: Color,
    onOpenRadioInfo: () -> Unit,
    onDismissStatus: () -> Unit,
) {
    AnimatedVisibility(
        visible = status !is SwitchStatus.Idle,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        when (status) {
            is SwitchStatus.Switching -> {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = switchingColor.copy(alpha = 0.12f),
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(11.dp), strokeWidth = 2.dp, color = switchingColor)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Switching to ${status.targetMode.label()} (${status.remainingSeconds}s)", fontSize = 11.sp, color = switchingColor)
                    }
                }
            }
            is SwitchStatus.Success -> {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clickable { onDismissStatus() }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Switched to ${status.mode.label()}", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                    }
                }
            }
            is SwitchStatus.Failed -> {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = failedColor.copy(alpha = 0.15f),
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clickable { onOpenRadioInfo() }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = failedColor, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Not Switched • On ${status.currentNetwork} (Tap for Force Menu)", fontSize = 11.sp, color = failedColor, fontWeight = FontWeight.Medium)
                    }
                }
            }
            else -> Unit
        }
    }
}

private fun pinWidgetToHomeScreen(context: android.content.Context, widgetClass: Class<*>) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        if (appWidgetManager.isRequestPinAppWidgetSupported) {
            val provider = ComponentName(context, widgetClass)
            appWidgetManager.requestPinAppWidget(provider, null, null)
            Toast.makeText(context, "Adding widget to home screen…", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Long-press your home screen to place this widget", Toast.LENGTH_LONG).show()
        }
    } else {
        Toast.makeText(context, "Long-press your home screen to place this widget", Toast.LENGTH_LONG).show()
    }
}
