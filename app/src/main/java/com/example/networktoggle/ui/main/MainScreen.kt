package com.example.networktoggle.ui.main

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.networktoggle.network.NetworkMode
import com.example.networktoggle.terminal.TerminalLine
import com.example.networktoggle.terminal.TerminalLineType

enum class MainScreenTab {
    SWITCHER,
    TERMINAL
}

/**
 * Flutter-grade Material 3 Glassmorphism UI for NetworkToggle.
 * Features:
 * 1. Minimal 4G ↔ 5G toggle powered directly by Shizuku Server API.
 * 2. Full-fledged interactive ADB Terminal directly connected to Shizuku shell (UID 2000).
 */
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var currentTab by remember { mutableStateOf(MainScreenTab.SWITCHER) }

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

    LaunchedEffect(uiState.popupMessage) {
        val msg = uiState.popupMessage
        if (msg != null) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            kotlinx.coroutines.delay(4500)
            viewModel.dismissPopupMessage()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) viewModel.onPermissionGranted()
    }

    val is5G = uiState.currentConnectedMode == NetworkMode.FIVE_G
    val primaryColor = if (is5G) Color(0xFF10B981) else Color(0xFF06B6D4)
    val accentGlow = if (is5G) Color(0xFF059669) else Color(0xFF0284C7)
    val bgColor = Color(0xFF080C16)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Ambient background glow orb (Flutter-style fluid illumination)
        Box(
            modifier = Modifier
                .size(380.dp)
                .align(Alignment.TopCenter)
                .offset(y = (-70).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.16f), Color.Transparent)
                    )
                )
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                MinimalTopBar(
                    primaryColor = primaryColor,
                    uiState = uiState,
                    onRequestShizuku = {
                        viewModel.connectShizuku(context)
                    },
                    onRefresh = viewModel::refreshLiveNetwork
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // Smooth Segmented Tab Selector (4G/5G Switcher ↔ Shizuku Terminal)
                TabSelector(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it },
                    primaryColor = primaryColor
                )

                Spacer(modifier = Modifier.height(16.dp))

                when (currentTab) {
                    MainScreenTab.SWITCHER -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // 1. Flutter-Grade Pulsing Concentric Radar Hero Card
                            MinimalHeroCard(
                                uiState = uiState,
                                primaryColor = primaryColor,
                                accentGlow = accentGlow,
                                is5G = is5G,
                                onRequestPhonePermission = {
                                    permissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                                }
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // 2. Minimalist 2-Way Tactile Segmented Switcher (4G LTE ↔ 5G NR)
                            MinimalTwoWayToggle(
                                currentMode = uiState.selectedChoice,
                                isSwitching = uiState.switchStatus is SwitchStatus.Switching,
                                onSelectMode = { mode ->
                                    viewModel.switchNetworkViaShizuku(mode, context)
                                }
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // 3. Shizuku Status & Adaptive Action Banner
                            ShizukuActionCard(
                                uiState = uiState,
                                primaryColor = primaryColor,
                                onRequestPermission = viewModel::requestAdbPermission,
                                onConnectShizuku = { viewModel.connectShizuku(context) },
                                onGrantWriteSecureSettings = viewModel::grantAdbSecureSettings,
                                onOpenTerminal = { currentTab = MainScreenTab.TERMINAL }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // 4. Switching Progress Feedback (if active)
                            AnimatedVisibility(
                                visible = uiState.switchStatus !is SwitchStatus.Idle,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                when (val status = uiState.switchStatus) {
                                    is SwitchStatus.Switching -> {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = Color(0xFF1E293B).copy(alpha = 0.9f),
                                            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.4f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    color = primaryColor,
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = "Enforcing ${status.targetMode.label()} via Shizuku (${status.remainingSeconds}s)...",
                                                    fontSize = 12.sp,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                    is SwitchStatus.Success -> {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = "✔ Switched to ${status.mode.label()} successfully!",
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF10B981),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    is SwitchStatus.Failed -> {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = Color(0xFFEF4444).copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = "Target mode not reached. Current: ${status.currentNetwork}",
                                                    fontSize = 12.sp,
                                                    color = Color(0xFFEF4444),
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                    else -> {}
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    MainScreenTab.TERMINAL -> {
                        // Full-fledged interactive Shizuku ADB terminal view
                        ShizukuTerminalView(
                            uiState = uiState,
                            primaryColor = primaryColor,
                            onConnectShizuku = { viewModel.connectShizuku(context) },
                            onRequestPermission = viewModel::requestAdbPermission,
                            onClearLogs = viewModel::clearAdbLogs,
                            onExecuteCommand = viewModel::executeAdbCommand
                        )
                    }
                }
            }
        }
    }
}

// =====================================================================
// SEGMENTED FLUTTER-STYLE TAB SELECTOR
// =====================================================================
@Composable
private fun TabSelector(
    currentTab: MainScreenTab,
    onTabSelected: (MainScreenTab) -> Unit,
    primaryColor: Color
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.85f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Tab 1: 4G/5G Switcher
            val isSwitcher = currentTab == MainScreenTab.SWITCHER
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isSwitcher) primaryColor else Color.Transparent,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onTabSelected(MainScreenTab.SWITCHER) }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapCalls,
                        contentDescription = null,
                        tint = if (isSwitcher) Color.Black else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "4G / 5G Toggle",
                        fontSize = 12.sp,
                        fontWeight = if (isSwitcher) FontWeight.Black else FontWeight.SemiBold,
                        color = if (isSwitcher) Color.Black else Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            // Tab 2: Shizuku ADB Terminal
            val isTerminal = currentTab == MainScreenTab.TERMINAL
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isTerminal) primaryColor else Color.Transparent,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onTabSelected(MainScreenTab.TERMINAL) }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = if (isTerminal) Color.Black else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Shizuku Terminal",
                        fontSize = 12.sp,
                        fontWeight = if (isTerminal) FontWeight.Black else FontWeight.SemiBold,
                        color = if (isTerminal) Color.Black else Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

// =====================================================================
// FLUTTER-STYLE MINIMAL TOP BAR
// =====================================================================
@Composable
private fun MinimalTopBar(
    primaryColor: Color,
    uiState: MainScreenUiState,
    onRequestShizuku: () -> Unit,
    onRefresh: () -> Unit
) {
    val termState = uiState.terminalState
    val isShizukuRunning = termState.isShizukuRunning
    val isAuthorized = termState.isAuthorized

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Network Toggle",
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 0.3.sp
            )
            Text(
                text = "Shizuku Privileged Edition",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryColor
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Shizuku Connection Pill Badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = when {
                    isAuthorized -> Color(0xFF10B981).copy(alpha = 0.15f)
                    isShizukuRunning -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                    else -> Color(0xFFEF4444).copy(alpha = 0.12f)
                },
                border = BorderStroke(
                    1.dp,
                    when {
                        isAuthorized -> Color(0xFF10B981).copy(alpha = 0.4f)
                        isShizukuRunning -> Color(0xFFF59E0B).copy(alpha = 0.4f)
                        else -> Color(0xFFEF4444).copy(alpha = 0.3f)
                    }
                ),
                modifier = Modifier.clickable { onRequestShizuku() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isAuthorized -> Color(0xFF10B981)
                                    isShizukuRunning -> Color(0xFFF59E0B)
                                    else -> Color(0xFFEF4444)
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            isAuthorized -> "Shizuku Active"
                            isShizukuRunning -> "Authorize Shizuku"
                            else -> "Connect Shizuku"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isAuthorized -> Color(0xFF10B981)
                            isShizukuRunning -> Color(0xFFF59E0B)
                            else -> Color(0xFFEF4444)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Quick Refresh Button
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.06f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                modifier = Modifier
                    .size(34.dp)
                    .clickable { onRefresh() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// =====================================================================
// FLUTTER-GRADE PULSING CONCENTRIC RADAR HERO CARD
// =====================================================================
@Composable
private fun MinimalHeroCard(
    uiState: MainScreenUiState,
    primaryColor: Color,
    accentGlow: Color,
    is5G: Boolean,
    onRequestPhonePermission: () -> Unit
) {
    val liveNet = uiState.liveNetwork
    val modeLabel = if (is5G) "5G NR" else "4G LTE"
    val carrierName = liveNet.displayName.ifBlank { "Mobile Network" }
    val techLabel = liveNet.detail.ifBlank { if (is5G) "NR NSA / SA Active" else "LTE Standard" }

    // Breathing Radar Animation
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val ringScale1 by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RingScale1"
    )
    val ringAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RingAlpha1"
    )

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.75f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Carrier Pill Header
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = primaryColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f))
            ) {
                Text(
                    text = carrierName.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = primaryColor,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Radar Ring & Center Orb
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(150.dp)
            ) {
                // Expanding Radar Wave Ring
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(ringScale1)
                        .clip(CircleShape)
                        .border(BorderStroke(2.dp, primaryColor.copy(alpha = ringAlpha1)), CircleShape)
                )

                // Static Center Ambient Glow
                Box(
                    modifier = Modifier
                        .size(105.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(primaryColor.copy(alpha = 0.35f), Color.Transparent)
                            )
                        )
                )

                // Tactical Center Core
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF080C16),
                    border = BorderStroke(2.5.dp, primaryColor),
                    shadowElevation = 8.dp,
                    modifier = Modifier.size(88.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.SignalCellularAlt,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Large Network Mode Text
            Text(
                text = modeLabel,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Radio Technology Subtitle
            Text(
                text = techLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryColor,
                letterSpacing = 0.8.sp
            )

            // Permission check if phone state not permitted
            if (!uiState.hasPermission) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                    modifier = Modifier.clickable { onRequestPhonePermission() }
                ) {
                    Text(
                        text = "Tap to grant Phone Permission for precise live carrier stats",
                        fontSize = 10.sp,
                        color = Color(0xFFF59E0B),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

// =====================================================================
// MINIMALIST 2-WAY TACTILE SWITCHER (4G LTE ↔ 5G NR)
// =====================================================================
@Composable
private fun MinimalTwoWayToggle(
    currentMode: NetworkMode,
    isSwitching: Boolean,
    onSelectMode: (NetworkMode) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF0A0F1D),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 4G LTE Pill Button
            val is4GSelected = currentMode == NetworkMode.FOUR_G
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (is4GSelected) Color(0xFF06B6D4) else Color.Transparent,
                border = if (!is4GSelected) BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)) else null,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clickable(enabled = !isSwitching) {
                        onSelectMode(NetworkMode.FOUR_G)
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.SignalCellularAlt,
                        contentDescription = null,
                        tint = if (is4GSelected) Color.Black else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "4G LTE",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = if (is4GSelected) Color.Black else Color.White
                        )
                        Text(
                            text = "Battery Saver",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (is4GSelected) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            // 5G NR Pill Button
            val is5GSelected = currentMode == NetworkMode.FIVE_G || currentMode == NetworkMode.AUTO
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (is5GSelected) Color(0xFF10B981) else Color.Transparent,
                border = if (!is5GSelected) BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)) else null,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clickable(enabled = !isSwitching) {
                        onSelectMode(NetworkMode.FIVE_G)
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = if (is5GSelected) Color.Black else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "5G NR",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = if (is5GSelected) Color.Black else Color.White
                        )
                        Text(
                            text = "Maximum Speed",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (is5GSelected) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}

// =====================================================================
// SHIZUKU STATUS & ADAPTIVE ACTION CARD
// =====================================================================
@Composable
private fun ShizukuActionCard(
    uiState: MainScreenUiState,
    primaryColor: Color,
    onRequestPermission: () -> Unit,
    onConnectShizuku: () -> Unit,
    onGrantWriteSecureSettings: () -> Unit,
    onOpenTerminal: () -> Unit
) {
    val termState = uiState.terminalState
    val isRunning = termState.isShizukuRunning
    val isAuthorized = termState.isAuthorized

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.7f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = if (isAuthorized) Color(0xFF10B981) else Color(0xFFF59E0B),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "SHIZUKU PRIVILEGED ENGINE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White.copy(alpha = 0.9f),
                        letterSpacing = 0.6.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = primaryColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f)),
                    modifier = Modifier.clickable { onOpenTerminal() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = primaryColor, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Terminal", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when {
                isAuthorized -> {
                    Text(
                        text = "✔ Shizuku shell connected with elevated permissions (UID ${if (termState.shizukuUid != -1) termState.shizukuUid else 2000}). Direct 0ms modem frequency switching is active.",
                        fontSize = 11.sp,
                        color = Color(0xFF10B981),
                        lineHeight = 16.sp
                    )
                    if (!uiState.isDirectToggleGranted) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF10B981),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .clickable { onGrantWriteSecureSettings() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "GRANT WRITE_SECURE_SETTINGS (VIA SHIZUKU)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }
                isRunning -> {
                    Text(
                        text = "Shizuku server is online on your device, but NetworkToggle has not yet been authorized. Tap below to prompt the authorization dialog.",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF59E0B),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clickable { onRequestPermission() }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AUTHORIZE SHIZUKU PERMISSION",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.Black
                            )
                        }
                    }
                }
                else -> {
                    Text(
                        text = "Shizuku service is not active. Connect to Shizuku server or start it on your phone via Wireless Debugging (Android 11+ requires no PC).",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clickable { onConnectShizuku() }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "CONNECT TO SERVER",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clickable { onRequestPermission() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "OPEN SHIZUKU APP",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================
// FULL-FLEDGED SHIZUKU ADB TERMINAL VIEW
// =====================================================================
@Composable
private fun ShizukuTerminalView(
    uiState: MainScreenUiState,
    primaryColor: Color,
    onConnectShizuku: () -> Unit,
    onRequestPermission: () -> Unit,
    onClearLogs: () -> Unit,
    onExecuteCommand: (String) -> Unit
) {
    val termState = uiState.terminalState
    val isRunning = termState.isShizukuRunning
    val isAuthorized = termState.isAuthorized
    var commandInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(termState.logs.size) {
        if (termState.logs.isNotEmpty()) {
            listState.animateScrollToItem(termState.logs.size - 1)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Connection & Server Diagnostics Header Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF0F172A).copy(alpha = 0.85f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = when {
                                isAuthorized -> Color(0xFF10B981).copy(alpha = 0.2f)
                                isRunning -> Color(0xFFF59E0B).copy(alpha = 0.2f)
                                else -> Color(0xFFEF4444).copy(alpha = 0.2f)
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isAuthorized) Icons.Default.Terminal else Icons.Default.VpnKey,
                                    contentDescription = null,
                                    tint = when {
                                        isAuthorized -> Color(0xFF10B981)
                                        isRunning -> Color(0xFFF59E0B)
                                        else -> Color(0xFFEF4444)
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = when {
                                    isAuthorized -> "Shizuku Shell Connected"
                                    isRunning -> "Shizuku Server Online"
                                    else -> "Shizuku Server Inactive"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = when {
                                    isAuthorized -> "UID ${if (termState.shizukuUid != -1) termState.shizukuUid else 2000} (shell) • API v${termState.shizukuVersion}"
                                    isRunning -> "Authorization required for elevated shell"
                                    else -> "Connect API or start via Wireless Debugging"
                                },
                                fontSize = 10.sp,
                                color = when {
                                    isAuthorized -> Color(0xFF10B981)
                                    isRunning -> Color(0xFFF59E0B)
                                    else -> Color.White.copy(alpha = 0.5f)
                                }
                            )
                        }
                    }

                    // Top Action Buttons (Connect, Auth, Clear)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Connect Button
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                            modifier = Modifier.clickable { onConnectShizuku() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Connect", fontSize = 10.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                            }
                        }

                        // Auth Button (if not yet granted)
                        if (!isAuthorized) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                                modifier = Modifier.clickable { onRequestPermission() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Auth", fontSize = 10.sp, color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Clear Button
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            modifier = Modifier.clickable { onClearLogs() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Clear", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }

        // 2. Horizontally scrollable 1-tap quick command chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            QuickCommandChip("grant", "Grant Perm", primaryColor) { onExecuteCommand("grant") }
            QuickCommandChip("revoke", "Revoke Perm", Color(0xFFEF4444)) { onExecuteCommand("revoke") }
            QuickCommandChip("5g", "Force 5G NR", Color(0xFF10B981)) { onExecuteCommand("5g") }
            QuickCommandChip("4g", "Force 4G LTE", Color(0xFF06B6D4)) { onExecuteCommand("4g") }
            QuickCommandChip("shizuku", "Shizuku Info", Color(0xFF38BDF8)) { onExecuteCommand("shizuku") }
            QuickCommandChip("connect", "Connect API", Color(0xFF38BDF8)) { onConnectShizuku() }
            QuickCommandChip("whoami", "Shell UID", Color(0xFFA855F7)) { onExecuteCommand("whoami") }
            QuickCommandChip("status", "Diagnostics", Color.White) { onExecuteCommand("status") }
            QuickCommandChip("net", "IP Routing", Color(0xFFF59E0B)) { onExecuteCommand("net") }
            QuickCommandChip("kernel", "Kernel Audit", Color(0xFF34D399)) { onExecuteCommand("kernel") }
        }

        // 3. Terminal Console Output Window
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF070B14),
            border = BorderStroke(1.dp, Color(0xFF1E293B)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Window Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A).copy(alpha = 0.9f))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFF10B981)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "shizuku@android:~$",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }

                    if (termState.isExecuting) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(11.dp),
                                color = primaryColor,
                                strokeWidth = 1.5.dp
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Executing...",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = primaryColor
                            )
                        }
                    }
                }

                // Output logs stream
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    items(termState.logs, key = { it.id }) { log ->
                        TerminalLineItem(log)
                    }
                }
            }
        }

        // 4. Command Input Bar
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0F172A),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$",
                    color = primaryColor,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 6.dp)
                )

                OutlinedTextField(
                    value = commandInput,
                    onValueChange = { commandInput = it },
                    placeholder = {
                        Text(
                            text = "Type command (e.g. grant, 5g, getprop)...",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.35f)
                        )
                    },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = primaryColor
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (commandInput.isNotBlank()) {
                                onExecuteCommand(commandInput.trim())
                                commandInput = ""
                            }
                        }
                    ),
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        if (commandInput.isNotBlank()) {
                            onExecuteCommand(commandInput.trim())
                            commandInput = ""
                        }
                    },
                    enabled = commandInput.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send Command",
                        tint = if (commandInput.isNotBlank()) primaryColor else Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun TerminalLineItem(line: TerminalLine) {
    val textColor = when (line.type) {
        TerminalLineType.COMMAND -> Color(0xFF38BDF8)
        TerminalLineType.SYSTEM -> Color(0xFF34D399)
        TerminalLineType.ERROR -> Color(0xFFF87171)
        TerminalLineType.OUTPUT -> Color(0xFFE2E8F0)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = line.timestamp,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.White.copy(alpha = 0.25f),
            modifier = Modifier.padding(end = 6.dp, top = 2.dp)
        )
        Text(
            text = line.text,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = textColor,
            lineHeight = 15.sp,
            fontWeight = if (line.type == TerminalLineType.COMMAND) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun QuickCommandChip(
    command: String,
    label: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accentColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
    }
}
