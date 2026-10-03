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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.networktoggle.widget.CompactToggleWidget
import com.example.networktoggle.widget.DashboardToggleWidget
import com.example.networktoggle.widget.NetworkToggleWidget
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.networktoggle.network.AppUiStyle
import com.example.networktoggle.network.NetworkMode
import com.example.networktoggle.network.NetworkModeManager

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = run {
        val context = LocalContext.current.applicationContext
        androidx.lifecycle.viewmodel.compose.viewModel(
            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    MainScreenViewModel(NetworkModeManager(context)) as T
            }
        )
    },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // On resume, automatically verify if hardware network transitioned
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

    // Permission launcher for READ_PHONE_STATE
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onPermissionGranted()
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
        onOpenRadioInfo = { viewModel.openRadioInfo(context) },
        onOpenSettings = { viewModel.openMobileSettings(context) },
        onRefresh = viewModel::refreshLiveNetwork,
        onDismissStatus = viewModel::dismissStatus,
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
    onOpenRadioInfo: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onDismissStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = when (uiState.uiStyle) {
        AppUiStyle.CYBER_NEON -> Color(0xFF08090D)
        AppUiStyle.MINIMAL_CLEAN -> Color(0xFF0F172A)
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ISOLATED & SECURE",
                        color = Color(0xFF10B981),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                }

                Row {
                    IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onOpenSettings, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // UI Style Switcher (User Option to Choose UI Style)
            Surface(
                shape = RoundedCornerShape(25.dp),
                color = Color.White.copy(alpha = 0.06f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AppUiStyle.entries.forEach { style ->
                        val isSelected = uiState.uiStyle == style
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) Color(0xFF00F5D4).copy(alpha = 0.18f) else Color.Transparent,
                            border = if (isSelected) BorderStroke(1.dp, Color(0xFF00F5D4)) else null,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSetUiStyle(style) }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 7.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(style.icon(), fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = style.label(),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFF00F5D4) else Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Permission Strip (Only if not granted)
            if (!uiState.hasPermission) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
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
                            text = "⚠️ Tap to enable real-time 5G/4G detection",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
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
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Render Selected UI Style
            when (uiState.uiStyle) {
                AppUiStyle.CYBER_NEON -> {
                    CyberNeonUi(
                        uiState = uiState,
                        onSelectChoice = onSelectChoice,
                        onToggleDial = onToggleDial,
                        onOpenRadioInfo = onOpenRadioInfo,
                        onOpenSettings = onOpenSettings,
                        onDismissStatus = onDismissStatus
                    )
                }
                AppUiStyle.MINIMAL_CLEAN -> {
                    MinimalCleanUi(
                        uiState = uiState,
                        onSelectChoice = onSelectChoice,
                        onToggleDial = onToggleDial,
                        onOpenRadioInfo = onOpenRadioInfo,
                        onOpenSettings = onOpenSettings,
                        onDismissStatus = onDismissStatus
                    )
                }
                AppUiStyle.SPEEDOMETER -> {
                    SpeedometerUi(
                        uiState = uiState,
                        onSelectChoice = onSelectChoice,
                        onToggleDial = onToggleDial,
                        onOpenRadioInfo = onOpenRadioInfo,
                        onOpenSettings = onOpenSettings,
                        onDismissStatus = onDismissStatus
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// UI STYLE 1: CYBER NEON
// -------------------------------------------------------------
@Composable
private fun CyberNeonUi(
    uiState: MainScreenUiState,
    onSelectChoice: (NetworkMode) -> Unit,
    onToggleDial: () -> Unit,
    onOpenRadioInfo: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissStatus: () -> Unit,
) {
    val is5G = uiState.currentConnectedMode == NetworkMode.FIVE_G
    val isFailed = uiState.switchStatus is SwitchStatus.Failed
    val isBusy = uiState.switchStatus is SwitchStatus.Switching

    val color5G = Color(0xFF00F5D4)
    val color4G = Color(0xFF7B2CBF)
    val colorFailed = Color(0xFFEF4444)
    val colorSwitching = Color(0xFFF59E0B)

    val activeColor = when {
        isFailed -> colorFailed
        isBusy -> colorSwitching
        is5G -> color5G
        else -> color4G
    }

    val animatedAccent by animateColorAsState(targetValue = activeColor, animationSpec = tween(500), label = "cyberAccent")
    val dialScale by animateFloatAsState(targetValue = if (isBusy) 0.94f else 1f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "cyberScale")

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Live Network Pill
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = Color.White.copy(alpha = 0.05f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (is5G) color5G else color4G))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "LIVE: ${uiState.liveNetwork.displayName.uppercase()}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            }
        }

        // Central Glowing Cyber Dial
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .scale(dialScale)
                .size(210.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(animatedAccent.copy(alpha = 0.22f), Color.Transparent)))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onToggleDial, enabled = !isBusy)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(180.dp)
                    .clip(CircleShape)
                    .border(BorderStroke(2.dp, animatedAccent.copy(alpha = 0.6f)), CircleShape)
                    .background(Color(0xFF0F1118))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.SignalCellularAlt, contentDescription = null, tint = animatedAccent, modifier = Modifier.size(34.dp))
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = uiState.currentConnectedMode.shortLabel(), fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = animatedAccent)
                    Text(text = if (is5G) "NR STANDALONE" else "LTE NETWORK", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f), letterSpacing = 1.2.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Inline Status Indicator (Zero Popups)
        InlineStatusIndicator(uiState.switchStatus, colorSwitching, colorFailed, onOpenRadioInfo, onDismissStatus)

        Spacer(modifier = Modifier.height(20.dp))

        // Mode Choice Cards
        Text(text = "CHOOSE NETWORK MODE", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeChoiceCard(title = "5G NR", subtitle = "5G Only", isSelected = uiState.selectedChoice == NetworkMode.FIVE_G, accentColor = color5G, onClick = { onSelectChoice(NetworkMode.FIVE_G) }, modifier = Modifier.weight(1f))
            ModeChoiceCard(title = "AUTO", subtitle = "5G/4G Hybrid", isSelected = uiState.selectedChoice == NetworkMode.AUTO, accentColor = Color(0xFF38BDF8), onClick = { onSelectChoice(NetworkMode.AUTO) }, modifier = Modifier.weight(1f))
            ModeChoiceCard(title = "4G LTE", subtitle = "4G Only", isSelected = uiState.selectedChoice == NetworkMode.FOUR_G, accentColor = color4G, onClick = { onSelectChoice(NetworkMode.FOUR_G) }, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Hardware Switch Tools
        Text(text = "HARDWARE SWITCH TOOLS", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(8.dp))

        HardwareToolsRow(onOpenRadioInfo, onOpenSettings)
        WidgetOptionsSection()
    }
}

// -------------------------------------------------------------
// UI STYLE 2: MINIMAL CLEAN (Material 3 / Clean Slate)
// -------------------------------------------------------------
@Composable
private fun MinimalCleanUi(
    uiState: MainScreenUiState,
    onSelectChoice: (NetworkMode) -> Unit,
    onToggleDial: () -> Unit,
    onOpenRadioInfo: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissStatus: () -> Unit,
) {
    val is5G = uiState.currentConnectedMode == NetworkMode.FIVE_G
    val primaryColor = if (is5G) Color(0xFF10B981) else Color(0xFF3B82F6)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(10.dp))

        // Main Minimal Card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E293B),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "CURRENT CONNECTION", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = uiState.liveNetwork.displayName, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                Spacer(modifier = Modifier.height(16.dp))

                // Large Minimal Action Button
                Button(
                    onClick = onToggleDial,
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text(text = if (is5G) "Switch to 4G LTE" else "Switch to 5G NR", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Inline Status Indicator
        InlineStatusIndicator(uiState.switchStatus, Color(0xFFF59E0B), Color(0xFFEF4444), onOpenRadioInfo, onDismissStatus)

        Spacer(modifier = Modifier.height(20.dp))

        // Minimal Segmented Mode Selector
        Text(text = "SELECT TARGET NETWORK", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(8.dp))

        Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF1E293B), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(6.dp)) {
                listOf(
                    Triple(NetworkMode.FIVE_G, "5G NR", Color(0xFF10B981)),
                    Triple(NetworkMode.AUTO, "Auto", Color(0xFF38BDF8)),
                    Triple(NetworkMode.FOUR_G, "4G LTE", Color(0xFF3B82F6))
                ).forEach { (mode, label, color) ->
                    val selected = uiState.selectedChoice == mode
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) color else Color.Transparent,
                        modifier = Modifier.weight(1f).clickable { onSelectChoice(mode) }
                    ) {
                        Text(text = label, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (selected) Color.White else Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 10.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Direct Tools
        Text(text = "SHORTCUTS", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(8.dp))

        HardwareToolsRow(onOpenRadioInfo, onOpenSettings)
        WidgetOptionsSection()
    }
}

// -------------------------------------------------------------
// UI STYLE 3: SPEEDOMETER / METER GAUGE
// -------------------------------------------------------------
@Composable
private fun SpeedometerUi(
    uiState: MainScreenUiState,
    onSelectChoice: (NetworkMode) -> Unit,
    onToggleDial: () -> Unit,
    onOpenRadioInfo: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissStatus: () -> Unit,
) {
    val is5G = uiState.currentConnectedMode == NetworkMode.FIVE_G
    val meterColor = if (is5G) Color(0xFFFF0055) else Color(0xFF00E5FF)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(6.dp))

        // Speedometer Gauge Display Card
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0B101D),
            border = BorderStroke(1.dp, meterColor.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = meterColor, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = if (is5G) "TURBO 5G BANDWIDTH" else "STANDARD 4G BANDWIDTH", color = meterColor, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Big Meter Value
                Text(
                    text = if (is5G) "5G NR" else "4G LTE",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                Text(
                    text = uiState.liveNetwork.displayName,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Turbo Toggle Action Button
                Button(
                    onClick = onToggleDial,
                    colors = ButtonDefaults.buttonColors(containerColor = meterColor),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(
                        text = if (is5G) "SWITCH TO 4G LTE" else "SWITCH TO 5G TURBO",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Inline Status Indicator
        InlineStatusIndicator(uiState.switchStatus, Color(0xFFF59E0B), Color(0xFFEF4444), onOpenRadioInfo, onDismissStatus)

        Spacer(modifier = Modifier.height(18.dp))

        // Mode Choice Tabs
        Text(text = "CHOOSE SPEED PROFILE", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeChoiceCard(title = "5G MAX", subtitle = "Unlimited", isSelected = uiState.selectedChoice == NetworkMode.FIVE_G, accentColor = Color(0xFFFF0055), onClick = { onSelectChoice(NetworkMode.FIVE_G) }, modifier = Modifier.weight(1f))
            ModeChoiceCard(title = "SMART", subtitle = "Dynamic", isSelected = uiState.selectedChoice == NetworkMode.AUTO, accentColor = Color(0xFF00E5FF), onClick = { onSelectChoice(NetworkMode.AUTO) }, modifier = Modifier.weight(1f))
            ModeChoiceCard(title = "ECO 4G", subtitle = "Battery Save", isSelected = uiState.selectedChoice == NetworkMode.FOUR_G, accentColor = Color(0xFFA855F7), onClick = { onSelectChoice(NetworkMode.FOUR_G) }, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Hardware Switch Tools
        Text(text = "HARDWARE TOOLS", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(8.dp))

        HardwareToolsRow(onOpenRadioInfo, onOpenSettings)
        WidgetOptionsSection()
    }
}

// -------------------------------------------------------------
// REUSABLE COMPONENTS
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
                    shape = RoundedCornerShape(20.dp),
                    color = switchingColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, switchingColor.copy(alpha = 0.3f))
                ) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = switchingColor)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Switching to ${status.targetMode.label()}… (${status.remainingSeconds}s)", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = switchingColor)
                    }
                }
            }

            is SwitchStatus.Success -> {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF065F46).copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                    modifier = Modifier.clickable { onDismissStatus() }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Switched to ${status.mode.label()}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    }
                }
            }

            is SwitchStatus.Failed -> {
                // CLEAN FAILURE INDICATION - ZERO POPUPS
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = failedColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, failedColor.copy(alpha = 0.5f)),
                    modifier = Modifier.clickable { onOpenRadioInfo() }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = failedColor, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Not Switched • Hardware is on ${status.currentNetwork}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = failedColor)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "(Tap for Force Menu)", fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }

            else -> Unit
        }
    }
}

@Composable
private fun ModeChoiceCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isSelected) accentColor else Color.White.copy(alpha = 0.08f)
    val bgColor = if (isSelected) accentColor.copy(alpha = 0.12f) else Color(0xFF12141C)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = if (isSelected) accentColor else Color.White)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 10.sp, color = if (isSelected) accentColor.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.45f), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun HardwareToolsRow(
    onOpenRadioInfo: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // Force Menu
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF13151F),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.weight(1f).clickable { onOpenRadioInfo() }
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color(0xFF00F5D4), modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = "Force Menu", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Pick NR / LTE", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                }
            }
        }

        // SIM Settings
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF13151F),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.weight(1f).clickable { onOpenSettings() }
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF7B2CBF), modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = "SIM Settings", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Preferred Type", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun WidgetOptionsSection() {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(28.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Widgets, contentDescription = null, tint = Color(0xFF00F5D4), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "HOME SCREEN WIDGET OPTIONS",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Widget Option 1: 1x1 Compact Quick Toggle
            WidgetOptionCard(
                title = "1x1 Compact Quick-Toggle",
                subtitle = "Minimalist circular dial for fast 1-tap toggling",
                sizeBadge = "1×1",
                badgeColor = Color(0xFF00F5D4),
                onAdd = { pinWidgetToHomeScreen(context, CompactToggleWidget::class.java) }
            )

            // Widget Option 2: 2x1 Standard Cyber Pill
            WidgetOptionCard(
                title = "2x1 Cyber Pill Widget",
                subtitle = "Horizontal glass pill with live carrier & switch button",
                sizeBadge = "2×1",
                badgeColor = Color(0xFF38BDF8),
                onAdd = { pinWidgetToHomeScreen(context, NetworkToggleWidget::class.java) }
            )

            // Widget Option 3: 4x2 Multi-Option Dashboard
            WidgetOptionCard(
                title = "4x2 Multi-Option Dashboard",
                subtitle = "Direct buttons for 5G, Auto, 4G, and Force Menu",
                sizeBadge = "4×2",
                badgeColor = Color(0xFF8B5CF6),
                onAdd = { pinWidgetToHomeScreen(context, DashboardToggleWidget::class.java) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun WidgetOptionCard(
    title: String,
    subtitle: String,
    sizeBadge: String,
    badgeColor: Color,
    onAdd: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF13151F),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = badgeColor.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
            ) {
                Text(
                    text = sizeBadge,
                    color = badgeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(text = subtitle, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = badgeColor),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text(text = "ADD", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
            }
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
