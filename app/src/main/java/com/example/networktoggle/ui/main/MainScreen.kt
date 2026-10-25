package com.example.networktoggle.ui.main

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.networktoggle.bridge.BridgeConnectionState
import com.example.networktoggle.bridge.PrivilegedBridgeManager
import com.example.networktoggle.macro.MacroAccessibilityService
import com.example.networktoggle.macro.MacroActionType
import com.example.networktoggle.macro.MacroExecutionState
import com.example.networktoggle.macro.MacroStep
import com.example.networktoggle.macro.RecordedMacro
import com.example.networktoggle.network.NetworkMode
import com.example.networktoggle.terminal.AdbEngine
import com.example.networktoggle.terminal.TerminalLineType

enum class NavigationTab(val title: String, val icon: String, val label: String) {
    SWITCHER("Toggle", "⚡", "Toggle 5G/4G"),
    TERMINAL("Terminal", "💻", "ADB Terminal"),
    MACRO("Macro", "🪄", "Macro Studio"),
    SETTINGS("Settings", "⚙️", "Settings")
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = run {
        val context = LocalContext.current.applicationContext
        val lifecycleOwner = LocalLifecycleOwner.current
        val nmm = com.example.networktoggle.network.NetworkModeManager(context)
        val atm = com.example.networktoggle.terminal.AdbTerminalManager(
            context,
            lifecycleOwner.lifecycleScope
        )
        nmm.adbTerminalManager = atm
        val mm = com.example.networktoggle.macro.MacroManager(context, nmm, atm)
        androidx.lifecycle.viewmodel.compose.viewModel(
            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    MainScreenViewModel(nmm, mm, atm) as T
            }
        )
    },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Flutter-grade Navigation: Default to Switcher hero screen
    var selectedTab by remember { mutableStateOf(NavigationTab.SWITCHER) }
    var showGuidedAssistantDialog by remember { mutableStateOf(false) }
    var showCreateMacroDialog by remember { mutableStateOf(false) }

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
        // Ambient background glow orb
        Box(
            modifier = Modifier
                .size(340.dp)
                .align(Alignment.TopCenter)
                .offset(y = (-60).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.14f), Color.Transparent)
                    )
                )
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                ModernTopBar(
                    primaryColor = primaryColor,
                    is5G = is5G,
                    carrierName = uiState.liveNetwork.displayName,
                    isElevated = uiState.isDirectToggleGranted || uiState.bridgeState is BridgeConnectionState.Connected,
                    onRefresh = viewModel::refreshLiveNetwork
                )
            },
            bottomBar = {
                ModernBottomNavBar(
                    currentTab = selectedTab,
                    accentColor = primaryColor,
                    onTabSelected = { selectedTab = it }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(10.dp))

                    when (selectedTab) {
                        NavigationTab.SWITCHER -> {
                            SwitcherScreen(
                                uiState = uiState,
                                primaryColor = primaryColor,
                                accentGlow = accentGlow,
                                is5G = is5G,
                                onRequestPhonePermission = {
                                    permissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                                },
                                onToggleQuick = {
                                    val isElevated = uiState.isDirectToggleGranted ||
                                            uiState.bridgeState is BridgeConnectionState.Connected ||
                                            uiState.terminalState.isAuthorized ||
                                            uiState.isKernelRootDetected
                                    if (isElevated) {
                                        viewModel.toggleQuick(context)
                                    } else {
                                        showGuidedAssistantDialog = true
                                    }
                                },
                                onSelectMode = { mode ->
                                    val isElevated = uiState.isDirectToggleGranted ||
                                            uiState.bridgeState is BridgeConnectionState.Connected ||
                                            uiState.terminalState.isAuthorized ||
                                            uiState.isKernelRootDetected
                                    viewModel.selectChoice(mode)
                                    if (isElevated) {
                                        viewModel.applySelectedSwitch(context)
                                    } else {
                                        showGuidedAssistantDialog = true
                                    }
                                },
                                onOpenRadioInfo = { viewModel.openRadioInfo(context) },
                                onOpenSettings = { viewModel.openMobileSettings(context) },
                                onDismissStatus = viewModel::dismissStatus,
                                onRequestAddTile = {
                                    viewModel.requestAddTileToControlPanel(false, context) { msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onOpenAssistant = { showGuidedAssistantDialog = true },
                                onNavigateToTerminal = { selectedTab = NavigationTab.TERMINAL }
                            )
                        }
                        NavigationTab.TERMINAL -> {
                            AdbTerminalScreen(
                                uiState = uiState,
                                onRequestPermission = viewModel::requestAdbPermission,
                                onExecuteCommand = viewModel::executeAdbCommand,
                                onGrantSecureSettings = viewModel::grantAdbSecureSettings,
                                onRevokeSecureSettings = viewModel::revokeAdbSecureSettings,
                                onClearLogs = viewModel::clearAdbLogs,
                                onSetMode = viewModel::setNetworkModeViaAdb,
                                onNavigateToToggle = { selectedTab = NavigationTab.SWITCHER }
                            )
                        }
                        NavigationTab.MACRO -> {
                            MacroScreen(
                                uiState = uiState,
                                accentColor = primaryColor,
                                onRunMacro = { mode -> viewModel.runModeMacro(mode) },
                                onRunSpecificMacro = { macro ->
                                    viewModel.runMacro(
                                        macro = macro,
                                        onPromptAddTile = {
                                            viewModel.requestAddTileToControlPanel(true, context) {}
                                        }
                                    )
                                },
                                onCreateMacro = { showCreateMacroDialog = true },
                                onDeleteMacro = viewModel::deleteMacro,
                                onClearLogs = viewModel::clearMacroLogs,
                                onOpenAccessibilitySettings = {
                                    MacroAccessibilityService.openAccessibilitySettings(context)
                                }
                            )
                        }
                        NavigationTab.SETTINGS -> {
                            SettingsScreen(
                                uiState = uiState,
                                accentColor = primaryColor,
                                onRequestPhonePermission = {
                                    permissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                                },
                                onRequestAdbPermission = viewModel::requestAdbPermission,
                                onOpenAccessibilitySettings = {
                                    MacroAccessibilityService.openAccessibilitySettings(context)
                                },
                                onOpenRadioInfo = { viewModel.openRadioInfo(context) },
                                onOpenMobileSettings = { viewModel.openMobileSettings(context) },
                                onRequestAddTile = { isMacro ->
                                    viewModel.requestAddTileToControlPanel(isMacro, context) { msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onStartBridgeViaAdb = { viewModel.startPrivilegedBridgeViaAdb(context) },
                                onStartBridgeViaRoot = { viewModel.startPrivilegedBridgeViaRoot(context) },
                                onStopBridge = viewModel::stopPrivilegedBridge,
                                onTestBridgePing = viewModel::testPrivilegedBridgePing
                            )
                        }
                    }
                }
            }
        }

        // Floating Top Notification Alert
        AnimatedVisibility(
            visible = uiState.popupMessage != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -50 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -50 }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 10.dp, start = 16.dp, end = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f)),
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "⚠️", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = uiState.popupMessage ?: "",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "DISMISS",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.clickable { viewModel.dismissPopupMessage() }
                    )
                }
            }
        }

        // Interactive Guided Switcher Dialog
        if (showGuidedAssistantDialog) {
            GuidedToggleAssistantDialog(
                targetMode = uiState.selectedChoice,
                onDismiss = { showGuidedAssistantDialog = false },
                onRunAccessibilityMacro = {
                    showGuidedAssistantDialog = false
                    viewModel.runModeMacro(uiState.selectedChoice)
                },
                onOpenRadioInfo = {
                    showGuidedAssistantDialog = false
                    viewModel.openRadioInfo(context)
                },
                onOpenTerminalBridge = {
                    showGuidedAssistantDialog = false
                    selectedTab = NavigationTab.TERMINAL
                }
            )
        }

        // Create Custom Macro Dialog
        if (showCreateMacroDialog) {
            CreateCustomMacroDialog(
                onDismiss = { showCreateMacroDialog = false },
                onSave = { name, targetMode, autoReturnHome ->
                    showCreateMacroDialog = false
                    val steps = mutableListOf<MacroStep>()
                    steps.add(MacroStep(actionType = MacroActionType.OPEN_NETWORK_SETTINGS, label = "Open Network & Internet"))
                    steps.add(MacroStep(actionType = MacroActionType.WAIT_DELAY, label = "Wait for UI load", delaySeconds = 1))
                    steps.add(MacroStep(actionType = MacroActionType.READ_SIM_SETTINGS, label = "Inspect SIM settings"))
                    steps.add(MacroStep(actionType = if (targetMode == NetworkMode.FIVE_G) MacroActionType.SET_5G else MacroActionType.SET_4G, label = "Enforce ${targetMode.label()}"))
                    if (autoReturnHome) {
                        steps.add(MacroStep(actionType = MacroActionType.GO_HOME, label = "Return to Home Screen"))
                    }
                    viewModel.createCustomMacro(name, steps)
                }
            )
        }
    }
}

// =====================================================================
// FLUTTER-GRADE TOP BAR
// =====================================================================
@Composable
private fun ModernTopBar(
    primaryColor: Color,
    is5G: Boolean,
    carrierName: String,
    isElevated: Boolean,
    onRefresh: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "topPulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "topDotAlpha"
    )

    Surface(
        color = Color(0xFF0C101D).copy(alpha = 0.85f),
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(primaryColor.copy(alpha = dotAlpha))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Network Toggle",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = (-0.4).sp
                    )
                    Text(
                        text = carrierName,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                if (isElevated) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "PRO",
                            color = Color(0xFF10B981),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = primaryColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = if (is5G) "5G NR" else "4G LTE",
                        color = primaryColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// =====================================================================
// FLUTTER-GRADE MATERIAL 3 BOTTOM NAVIGATION BAR
// =====================================================================
@Composable
private fun ModernBottomNavBar(
    currentTab: NavigationTab,
    accentColor: Color,
    onTabSelected: (NavigationTab) -> Unit
) {
    Surface(
        color = Color(0xFF090D18).copy(alpha = 0.95f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = 16.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationTab.entries.forEach { tab ->
                val isSelected = currentTab == tab
                val targetText = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) accentColor.copy(alpha = 0.2f) else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab.icon,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = tab.title,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                        color = targetText
                    )
                }
            }
        }
    }
}

// =====================================================================
// SCREEN 1: ⚡ FLUTTER-GRADE HERO TOGGLE SCREEN
// =====================================================================
@Composable
private fun SwitcherScreen(
    uiState: MainScreenUiState,
    primaryColor: Color,
    accentGlow: Color,
    is5G: Boolean,
    onRequestPhonePermission: () -> Unit,
    onToggleQuick: () -> Unit,
    onSelectMode: (NetworkMode) -> Unit,
    onOpenRadioInfo: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissStatus: () -> Unit,
    onRequestAddTile: () -> Unit,
    onOpenAssistant: () -> Unit,
    onNavigateToTerminal: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fluidPulse")
    val radarScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fluidScale"
    )

    // 1. Permission Reminder Banner (If not yet authorized)
    if (!uiState.isDirectToggleGranted) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E293B).copy(alpha = 0.9f),
            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToTerminal() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF38BDF8).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(15.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Elevated ADB Access Recommended",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Grant WRITE_SECURE_SETTINGS via Terminal for 0ms direct toggling",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 9.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF38BDF8),
                    modifier = Modifier.clickable { onNavigateToTerminal() }
                ) {
                    Text(
                        text = "AUTHORIZE",
                        color = Color.Black,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
    }

    // 2. High-End Fluid Glassmorphic Hero Card
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = Color(0xFF101626).copy(alpha = 0.85f),
        border = BorderStroke(1.5.dp, primaryColor.copy(alpha = 0.35f)),
        shadowElevation = 18.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleQuick() }
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF131D33),
                            Color(0xFF0A0F1D)
                        )
                    )
                )
                .padding(26.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Status Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Default.SignalCellularAlt,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = uiState.liveNetwork.displayName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Concentric Fluid Radar Rings
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(150.dp)
                ) {
                    // Outer fluid pulse
                    Box(
                        modifier = Modifier
                            .size(145.dp)
                            .scale(radarScale)
                            .clip(CircleShape)
                            .background(primaryColor.copy(alpha = 0.08f))
                    )
                    // Mid glow ring
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(primaryColor.copy(alpha = 0.12f))
                    )
                    // Inner Obsidian Core
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF060912))
                            .border(BorderStroke(2.5.dp, primaryColor.copy(alpha = 0.6f)), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (is5G) "5G" else "4G",
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black,
                                color = primaryColor,
                                letterSpacing = (-1.5).sp
                            )
                            Text(
                                text = if (is5G) "STANDALONE" else "LTE POWER",
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White.copy(alpha = 0.65f),
                                letterSpacing = 0.6.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = if (is5G) "Ultra-Fast 5G NR Active" else "Battery-Saver 4G LTE Active",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                Text(
                    text = if (is5G) "Maximum bandwidth & minimum latency mode" else "Conserves up to 35% battery life during daily use",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(22.dp))

                // Large Tactile Button (Flutter-grade Material 3 Pill)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = primaryColor,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(primaryColor, accentGlow)
                                )
                            ),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FlashOn,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (is5G) "SWITCH TO 4G LTE" else "SWITCH TO 5G NR",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black,
                            letterSpacing = 0.6.sp
                        )
                    }
                }
            }
        }
    }

    // 3. Inline Switch Verification Status
    AnimatedVisibility(
        visible = uiState.switchStatus !is SwitchStatus.Idle,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Column(modifier = Modifier.padding(top = 12.dp)) {
            when (val s = uiState.switchStatus) {
                is SwitchStatus.Switching -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(15.dp), color = Color(0xFFF59E0B), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Applying ${s.targetMode.label()} switch (${s.remainingSeconds}s)…",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF59E0B)
                            )
                        }
                    }
                }
                is SwitchStatus.Success -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Successfully switched to ${s.mode.label()}!",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onDismissStatus, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
                is SwitchStatus.Failed -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Modem remained on ${s.currentNetwork}",
                                fontSize = 11.sp,
                                color = Color(0xFFEF4444),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "OPTIONS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF38BDF8),
                                modifier = Modifier.clickable { onOpenAssistant() }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = onDismissStatus, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
                SwitchStatus.Idle -> {}
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // 4. Flutter-grade 3-Way Mode Segmented Selector
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF0F1526),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            val modes = listOf(
                Triple(NetworkMode.FIVE_G, "5G NR", "⚡"),
                Triple(NetworkMode.AUTO, "Auto", "🔄"),
                Triple(NetworkMode.FOUR_G, "4G LTE", "📶")
            )
            modes.forEach { (mode, label, icon) ->
                val isSelected = uiState.selectedChoice == mode
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) primaryColor else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectMode(mode) }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = icon, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                            color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // 5. Live Telemetry Strip (3 Glass Cards)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val isElevated = uiState.isDirectToggleGranted || uiState.bridgeState is BridgeConnectionState.Connected
        TelemetryGlassCard(
            label = "OPERATOR",
            value = uiState.liveNetwork.displayName.take(12),
            accentColor = primaryColor,
            modifier = Modifier.weight(1f)
        )
        TelemetryGlassCard(
            label = "TECHNOLOGY",
            value = if (is5G) "NR Standalone" else "LTE Advanced",
            accentColor = primaryColor,
            modifier = Modifier.weight(1f)
        )
        TelemetryGlassCard(
            label = "ENGINE",
            value = if (isElevated) "Elevated Pro" else "Standard",
            accentColor = if (isElevated) Color(0xFF10B981) else Color(0xFFF59E0B),
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // 6. Modern Quick Tools (3 Essential Cards)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MinimalToolCard(
            icon = Icons.Default.Tune,
            title = "Force Menu",
            subtitle = "*#*#4636#*#*",
            accentColor = primaryColor,
            onClick = onOpenRadioInfo,
            modifier = Modifier.weight(1f)
        )
        MinimalToolCard(
            icon = Icons.Default.Settings,
            title = "SIM Settings",
            subtitle = "Network type",
            accentColor = primaryColor,
            onClick = onOpenSettings,
            modifier = Modifier.weight(1f)
        )
        MinimalToolCard(
            icon = Icons.Default.Widgets,
            title = "Quick Tile",
            subtitle = "Control Panel",
            accentColor = primaryColor,
            onClick = onRequestAddTile,
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun TelemetryGlassCard(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF101626).copy(alpha = 0.7f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                color = Color.White.copy(alpha = 0.45f),
                letterSpacing = 0.6.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MinimalToolCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF101626).copy(alpha = 0.7f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        modifier = modifier
            .height(64.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(text = subtitle, fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f))
            }
        }
    }
}

// =====================================================================
// SCREEN 2: 💻 ADB & LINUX TERMINAL (MATERIAL 3 GLASS STUDIO)
// =====================================================================
@Composable
private fun AdbTerminalScreen(
    uiState: MainScreenUiState,
    onRequestPermission: () -> Unit,
    onExecuteCommand: (String) -> Unit,
    onGrantSecureSettings: () -> Unit,
    onRevokeSecureSettings: () -> Unit,
    onClearLogs: () -> Unit,
    onSetMode: (NetworkMode) -> Unit,
    onNavigateToToggle: () -> Unit
) {
    val termState = uiState.terminalState
    var inputCmd by remember { mutableStateOf("") }
    val isPermGranted = uiState.isDirectToggleGranted || termState.isPermissionGranted

    // 1. Permission Status Glass Card
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isPermGranted) Color(0xFF10B981).copy(alpha = 0.08f) else Color(0xFFEF4444).copy(alpha = 0.08f),
        border = BorderStroke(1.dp, if (isPermGranted) Color(0xFF10B981).copy(alpha = 0.35f) else Color(0xFFEF4444).copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (isPermGranted) Color(0xFF10B981) else Color(0xFFEF4444))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isPermGranted) "PERMISSION ACTIVE (WRITE_SECURE_SETTINGS)" else "PERMISSION REQUIRED (WRITE_SECURE_SETTINGS)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isPermGranted) Color(0xFF10B981) else Color(0xFFEF4444),
                    letterSpacing = 0.4.sp
                )
                Text(
                    text = if (isPermGranted) "0ms background switching is operational" else "Tap 'GRANT PERMISSION' below or run 'grant'",
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // 1.5. Kernel Security Shield Banner
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0B132B).copy(alpha = 0.85f),
        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onExecuteCommand("kernel") }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Security,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "KERNEL HARDENING & SECURE ADB",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF38BDF8),
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = termState.kernelShieldText,
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF38BDF8).copy(alpha = 0.15f)
            ) {
                Text(
                    text = "AUDIT",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // 2. The Two Primary Command Buttons (Grant & Revoke)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // GRANT BUTTON
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF10B981),
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .clickable { onGrantSecureSettings() }
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("GRANT PERMISSION", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Black)
                    Text("Command: grant", fontSize = 8.sp, color = Color.Black.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace)
                }
            }
        }

        // REVOKE BUTTON
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFEF4444).copy(alpha = 0.15f),
            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .clickable { onRevokeSecureSettings() }
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.FlashOff, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("REVOKE PERMISSION", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFFEF4444))
                    Text("Command: revoke", fontSize = 8.sp, color = Color.White.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace)
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Quick Command Chips Bar
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val quickChips = listOf(
            "grant",
            "revoke",
            "kernel",
            "kconfig",
            "5g",
            "4g",
            "whoami",
            "status",
            "devices",
            "net",
            "battery",
            "props",
            "bridge",
            "ping",
            "clear"
        )
        quickChips.forEach { chip ->
            val isGrantChip = chip == "grant"
            val isRevokeChip = chip == "revoke"
            val isKernelChip = chip == "kernel" || chip == "kconfig"
            val chipColor = when {
                isGrantChip -> Color(0xFF10B981)
                isRevokeChip -> Color(0xFFEF4444)
                isKernelChip -> Color(0xFF38BDF8)
                else -> Color(0xFF94A3B8)
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = chipColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, chipColor.copy(alpha = 0.35f)),
                modifier = Modifier.clickable {
                    if (chip == "clear") onClearLogs()
                    else onExecuteCommand(chip)
                }
            ) {
                Text(
                    text = chip,
                    color = chipColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Interactive Terminal Window (Monospace Glass Console)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF060912),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "INTERACTIVE CONSOLE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                }
                Text(
                    text = "CLEAR",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.clickable { onClearLogs() }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val scrollState = rememberScrollState()
            LaunchedEffect(termState.logs.size) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .verticalScroll(scrollState)
            ) {
                if (termState.logs.isEmpty()) {
                    Text(
                        text = "$ Ready for shell commands.\nTap 'GRANT' or 'REVOKE' above, or type any command.",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        termState.logs.forEach { line ->
                            val color = when (line.type) {
                                TerminalLineType.COMMAND -> Color(0xFFF59E0B)
                                TerminalLineType.OUTPUT -> Color(0xFF38BDF8)
                                TerminalLineType.ERROR -> Color(0xFFEF4444)
                                TerminalLineType.SYSTEM -> Color(0xFF10B981)
                            }
                            Text(
                                text = line.text,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = color
                            )
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Command Input Line
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = inputCmd,
            onValueChange = { inputCmd = it },
            placeholder = { Text("Type command (e.g. grant, revoke, 5g, 4g, whoami)...", fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFF59E0B),
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        IconButton(
            onClick = {
                if (inputCmd.isNotBlank()) {
                    onExecuteCommand(inputCmd)
                    inputCmd = ""
                }
            },
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF59E0B))
        ) {
            Icon(Icons.Default.Send, contentDescription = "Run", tint = Color.Black, modifier = Modifier.size(18.dp))
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Proceed to Toggle Screen Button
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0F172A),
        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToToggle() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "PROCEED TO 4G / 5G TOGGLE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF38BDF8)
                )
                Text(
                    text = if (isPermGranted) "Permission active • Ready to toggle modem" else "Switch to hero toggle screen",
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
}

// =====================================================================
// SCREEN 3: 🪄 MACRO (STREAMLINED & INTERACTIVE)
// =====================================================================
@Composable
private fun MacroScreen(
    uiState: MainScreenUiState,
    accentColor: Color,
    onRunMacro: (NetworkMode) -> Unit,
    onRunSpecificMacro: (RecordedMacro) -> Unit,
    onCreateMacro: () -> Unit,
    onDeleteMacro: (String) -> Unit,
    onClearLogs: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    val context = LocalContext.current
    val isBridgeConnected = uiState.bridgeState is BridgeConnectionState.Connected
    val isAdbAuthorized = uiState.terminalState.isAuthorized || uiState.isDirectToggleGranted || isBridgeConnected
    val isAccessibilityActive = MacroAccessibilityService.isAccessibilityEnabled(context)
    val isRunning = uiState.macroState is MacroExecutionState.Running

    // Engine Status Indicator
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isAdbAuthorized || isAccessibilityActive) Color(0xFF10B981).copy(alpha = 0.08f) else Color(0xFFF59E0B).copy(alpha = 0.08f),
        border = BorderStroke(1.dp, if (isAdbAuthorized || isAccessibilityActive) Color(0xFF10B981).copy(alpha = 0.25f) else Color(0xFFF59E0B).copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = if (isBridgeConnected) "🟢" else if (isAdbAuthorized) "⚡" else if (isAccessibilityActive) "👆" else "⚠️", fontSize = 14.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isBridgeConnected -> "Privileged System Server Proxy Active"
                        isAdbAuthorized -> "Direct Shell Engine Active"
                        isAccessibilityActive -> "Accessibility Tap Engine Active"
                        else -> "Accessibility Permission Required"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isAdbAuthorized || isAccessibilityActive) Color(0xFF10B981) else Color(0xFFF59E0B)
                )
                Text(
                    text = when {
                        isBridgeConnected -> "Instant 0ms background switching via daemon IPC"
                        isAdbAuthorized -> "Instant background frequency switching ready"
                        isAccessibilityActive -> "Automated touch gesture navigation active"
                        else -> "Enable Accessibility to automate Settings navigation"
                    },
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }
            if (!isAdbAuthorized && !isAccessibilityActive) {
                Text(
                    text = "ENABLE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFF59E0B),
                    modifier = Modifier.clickable { onOpenAccessibilitySettings() }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Header & Create Macro Button
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "SAVED MACROS & RECIPES",
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            color = Color.White.copy(alpha = 0.5f),
            letterSpacing = 0.6.sp
        )
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = accentColor.copy(alpha = 0.15f),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
            modifier = Modifier.clickable { onCreateMacro() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = accentColor, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "NEW MACRO", fontSize = 10.sp, fontWeight = FontWeight.Black, color = accentColor)
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Saved Macros List
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        uiState.savedMacros.forEach { macro ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF101626).copy(alpha = 0.75f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accentColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (macro.name.contains("5G", ignoreCase = true)) "⚡" else "📶",
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = macro.name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${macro.steps.size} steps • ${macro.steps.firstOrNull()?.label ?: "Ready"}",
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onRunSpecificMacro(macro) },
                        enabled = !isRunning,
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("RUN", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                    if (uiState.savedMacros.size > 2) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { onDeleteMacro(macro.id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Live Execution Banner
    if (uiState.macroState is MacroExecutionState.Running) {
        val runState = uiState.macroState
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF10B981).copy(alpha = 0.12f),
            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "▶ EXECUTING: ${runState.stepLabel}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    Text(text = "${(runState.progress * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { runState.progress },
                    color = Color(0xFF10B981),
                    trackColor = Color.White.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
    }

    // Diagnostics / Macro Logs Console
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF070A12),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "EXECUTION TRACE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
                Text(
                    text = "CLEAR",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    modifier = Modifier.clickable { onClearLogs() }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (uiState.macroLogs.isEmpty()) {
                    Text(
                        text = "Ready. Tap 'RUN' on any recipe to execute.",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        uiState.macroLogs.forEach { log ->
                            Text(
                                text = "[${log.timestamp}] ${log.message}",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
}

// =====================================================================
// SCREEN 4: ⚙️ SETTINGS & CONTROL PANEL
// =====================================================================
@Composable
private fun SettingsScreen(
    uiState: MainScreenUiState,
    accentColor: Color,
    onRequestPhonePermission: () -> Unit,
    onRequestAdbPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenRadioInfo: () -> Unit,
    onOpenMobileSettings: () -> Unit,
    onRequestAddTile: (Boolean) -> Unit,
    onStartBridgeViaAdb: () -> Unit = {},
    onStartBridgeViaRoot: () -> Unit = {},
    onStopBridge: () -> Unit = {},
    onTestBridgePing: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val isAccessibilityActive = MacroAccessibilityService.isAccessibilityEnabled(context)
    val isAdbAuthorized = uiState.terminalState.isAuthorized || uiState.isDirectToggleGranted

    // Section 0: Privileged Backend Service (app_process Binder Bridge)
    Text(
        text = "PRIVILEGED BACKEND SERVICE (APP_PROCESS BINDER)",
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        color = Color(0xFFF59E0B),
        letterSpacing = 0.5.sp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    )

    val bridgeState = uiState.bridgeState
    val isBridgeConnected = bridgeState is BridgeConnectionState.Connected
    val bridgeStatusColor = if (isBridgeConnected) Color(0xFF10B981) else Color(0xFFF59E0B)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF101626).copy(alpha = 0.85f),
        border = BorderStroke(1.dp, bridgeStatusColor.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(bridgeStatusColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isBridgeConnected) {
                            val conn = bridgeState as BridgeConnectionState.Connected
                            val identity = if (conn.isRoot) "Root (UID 0)" else "ADB Shell (UID ${conn.uid})"
                            "Bridge Active • $identity"
                        } else {
                            "Bridge Inactive • app_process Daemon"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = bridgeStatusColor
                    )
                    Text(
                        text = if (isBridgeConnected) {
                            val conn = bridgeState as BridgeConnectionState.Connected
                            "Process PID: ${conn.pid} • Binder IPC connected"
                        } else {
                            "Runs directly on device with ADB shell (UID 2000) or Root (UID 0)"
                        },
                        fontSize = 9.sp,
                        color = Color.White.copy(alpha = 0.55f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Acts as an elevated proxy between this app and Android System Server via Binder IPC. Switches frequencies directly without user interaction.",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.65f),
                lineHeight = 14.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isBridgeConnected) {
                    Button(
                        onClick = onTestBridgePing,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Text("TEST PING", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onStopBridge,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Text("STOP DAEMON", color = Color(0xFFEF4444), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onStartBridgeViaAdb,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Text("START VIA ADB", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                    if (uiState.isKernelRootDetected) {
                        Button(
                            onClick = onStartBridgeViaRoot,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Text("START VIA ROOT", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val pcCmd = PrivilegedBridgeManager.getAdbPcCommand(context)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF070A12),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PC launch command (Wireless / USB Debugging):",
                        fontSize = 9.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(pcCmd))
                            Toast.makeText(context, "Copied app_process launch command to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("COPY", color = Color(0xFF38BDF8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Section 1: Permissions & Access Status
    Text(
        text = "PERMISSIONS & ACCESS STATUS",
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        color = Color(0xFF38BDF8),
        letterSpacing = 0.5.sp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF101626).copy(alpha = 0.85f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PermissionStatusRow(
                title = "Telephony Hardware Access",
                subtitle = if (uiState.hasPermission) "Granted • Live 5G/4G radio telemetry operational" else "Action needed • Required for live network reading",
                isGranted = uiState.hasPermission,
                actionLabel = if (!uiState.hasPermission) "GRANT" else null,
                onAction = onRequestPhonePermission
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

            PermissionStatusRow(
                title = "On-Device ADB (Shizuku)",
                subtitle = if (isAdbAuthorized) "Authorized • Instant background frequency switching ready" else "Not authorized • Enables direct 1-tap mode toggles",
                isGranted = isAdbAuthorized,
                actionLabel = if (!isAdbAuthorized) "AUTHORIZE" else null,
                onAction = onRequestAdbPermission
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

            PermissionStatusRow(
                title = "Accessibility Automation",
                subtitle = if (isAccessibilityActive) "Active • Automated touch navigation & SIM clicks ready" else "Inactive • Automates Settings navigation without ADB",
                isGranted = isAccessibilityActive,
                actionLabel = if (!isAccessibilityActive) "ENABLE" else null,
                onAction = onOpenAccessibilitySettings
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

            PermissionStatusRow(
                title = "Kernel Root Access",
                subtitle = if (uiState.isKernelRootDetected) "Detected • Elevated SU shell command execution available" else "Not detected • Standard secure Android sandbox active",
                isGranted = uiState.isKernelRootDetected,
                actionLabel = null,
                onAction = {}
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Section 2: Quick Settings / Control Panel Tiles
    Text(
        text = "CONTROL PANEL TILES",
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        color = Color(0xFF38BDF8),
        letterSpacing = 0.5.sp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF101626).copy(alpha = 0.85f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TileOptionRow(
                title = "Network Toggle Tile",
                subtitle = "1-Tap toggle 5G / 4G directly in notification panel",
                buttonText = "ADD TILE",
                onAdd = { onRequestAddTile(false) }
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
            TileOptionRow(
                title = "Network Macro Tile",
                subtitle = "Trigger automated guided SIM routine from control panel",
                buttonText = "ADD TILE",
                onAdd = { onRequestAddTile(true) }
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Section 3: Cellular Testing & Diagnostic Tools
    Text(
        text = "SYSTEM RADIO MENUS",
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        color = Color(0xFF38BDF8),
        letterSpacing = 0.5.sp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    )

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF101626).copy(alpha = 0.85f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.weight(1f).clickable { onOpenRadioInfo() }
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(text = "🔧 Force Radio Menu", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(text = "*#*#4636#*#* Testing activity", fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f))
            }
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF101626).copy(alpha = 0.85f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.weight(1f).clickable { onOpenMobileSettings() }
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(text = "🌐 SIMs & Network", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(text = "System operator settings", fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f))
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Section 4: Manual PC ADB Command
    Text(
        text = "MANUAL PC ADB COMMAND",
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        color = Color(0xFF38BDF8),
        letterSpacing = 0.5.sp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF070A12),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "If not using Shizuku, run this once via computer terminal to grant background switching:",
                fontSize = 9.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            val adbCmd = "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = adbCmd,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF38BDF8),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(adbCmd))
                        Toast.makeText(context, "Copied ADB command to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("COPY", color = Color(0xFF38BDF8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun PermissionStatusRow(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (isGranted) Color(0xFF10B981) else Color(0xFFF59E0B))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(text = subtitle, fontSize = 9.sp, color = Color.White.copy(alpha = 0.55f))
        }
        if (actionLabel != null) {
            Spacer(modifier = Modifier.width(10.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                modifier = Modifier.clickable { onAction() }
            ) {
                Text(
                    text = actionLabel,
                    color = Color(0xFF38BDF8),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun TileOptionRow(
    title: String,
    subtitle: String,
    buttonText: String,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(text = subtitle, fontSize = 9.sp, color = Color.White.copy(alpha = 0.55f))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Button(
            onClick = onAdd,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier.height(30.dp)
        ) {
            Text(buttonText, color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
    }
}

// =====================================================================
// GUIDED TOGGLE ASSISTANT MODAL (FOR GUARANTEED NON-ROOT FUNCTIONALITY)
// =====================================================================
@Composable
private fun GuidedToggleAssistantDialog(
    targetMode: NetworkMode,
    onDismiss: () -> Unit,
    onRunAccessibilityMacro: () -> Unit,
    onOpenRadioInfo: () -> Unit,
    onOpenTerminalBridge: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0F172A),
            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f)),
            shadowElevation = 18.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚡ Switch to ${targetMode.label()}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Select your preferred method to apply this network mode change on your device:",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.65f),
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Option 1: Automated Accessibility Macro
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRunAccessibilityMacro() }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🪄", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Automated Touch Macro", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                            Text(text = "Automatically taps Settings -> SIMs -> Preferred Network without root or ADB", fontSize = 9.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Option 2: Force Radio Menu
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenRadioInfo() }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🔧", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Force Radio Menu (*#*#4636#*#*)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                            Text(text = "Directly set NR only or LTE only in Android phone testing", fontSize = 9.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Option 3: Terminal & Privileged Bridge
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenTerminalBridge() }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "💻", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Terminal (Step 1 Permission Setup)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                            Text(text = "Run 'grant' via Shizuku / ADB for permanent 0ms silent background switching", fontSize = 9.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Text("CANCEL", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// =====================================================================
// CREATE CUSTOM MACRO DIALOG
// =====================================================================
@Composable
private fun CreateCustomMacroDialog(
    onDismiss: () -> Unit,
    onSave: (String, NetworkMode, Boolean) -> Unit
) {
    var macroName by remember { mutableStateOf("Quick Network Switch") }
    var selectedTarget by remember { mutableStateOf(NetworkMode.FIVE_G) }
    var returnToHome by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0F172A),
            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
            shadowElevation = 18.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "🪄 Create Custom Macro",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = macroName,
                    onValueChange = { macroName = it },
                    label = { Text("Macro Name", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "TARGET NETWORK MODE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(NetworkMode.FIVE_G to "5G NR", NetworkMode.FOUR_G to "4G LTE").forEach { (mode, label) ->
                        val isSel = selectedTarget == mode
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSel) Color(0xFF10B981) else Color(0xFF1E293B),
                            modifier = Modifier.weight(1f).clickable { selectedTarget = mode }
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.Black else FontWeight.Bold,
                                color = if (isSel) Color.Black else Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = returnToHome,
                        onCheckedChange = { returnToHome = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF10B981))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Auto-minimize & return to Home when complete",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                        Text("CANCEL", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onSave(macroName, selectedTarget, returnToHome) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                        Text("SAVE MACRO", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}
