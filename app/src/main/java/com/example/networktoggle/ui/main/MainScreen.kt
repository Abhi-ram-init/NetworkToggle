package com.example.networktoggle.ui.main

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.lifecycle.lifecycleScope
import com.example.networktoggle.macro.MacroAccessibilityService
import com.example.networktoggle.macro.MacroExecutionState
import com.example.networktoggle.network.NetworkMode
import com.example.networktoggle.terminal.AdbEngine
import com.example.networktoggle.terminal.TerminalLineType

enum class NavigationTab(val title: String, val icon: String) {
    SWITCHER("Toggle", "âš¡"),
    MACRO("Macro", "ðŸª„"),
    TERMINAL("ADB", "ðŸ’»"),
    SETTINGS("Settings", "âš™ï¸")
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
    var selectedTab by remember { mutableStateOf(NavigationTab.SWITCHER) }

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
    val bgColor = Color(0xFF080C14)

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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Sleek Minimal Top Bar
            MinimalTopBar(
                primaryColor = primaryColor,
                is5G = is5G,
                onRefresh = viewModel::refreshLiveNetwork
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Minimalist 4-Pill Navigation Bar
            MinimalSegmentedNavBar(
                currentTab = selectedTab,
                accentColor = primaryColor,
                onTabSelected = { selectedTab = it }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Main Tab Viewport (Scrollable)
            Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (selectedTab) {
                        NavigationTab.SWITCHER -> {
                            SwitcherScreen(
                                uiState = uiState,
                                primaryColor = primaryColor,
                                is5G = is5G,
                                onRequestPhonePermission = {
                                    permissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                                },
                                onToggleQuick = { viewModel.toggleQuick(context) },
                                onSelectMode = { mode ->
                                    viewModel.selectChoice(mode)
                                    viewModel.applySelectedSwitch(context)
                                },
                                onOpenRadioInfo = { viewModel.openRadioInfo(context) },
                                onOpenSettings = { viewModel.openMobileSettings(context) },
                                onDismissStatus = viewModel::dismissStatus,
                                onRequestAddTile = {
                                    viewModel.requestAddTileToControlPanel(false, context) { msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                        NavigationTab.MACRO -> {
                            MacroScreen(
                                uiState = uiState,
                                accentColor = primaryColor,
                                onRunMacro = { mode -> viewModel.runModeMacro(mode) },
                                onClearLogs = viewModel::clearMacroLogs,
                                onOpenAccessibilitySettings = {
                                    MacroAccessibilityService.openAccessibilitySettings(context)
                                }
                            )
                        }
                        NavigationTab.TERMINAL -> {
                            AdbTerminalScreen(
                                uiState = uiState,
                                onRequestPermission = viewModel::requestAdbPermission,
                                onExecuteCommand = viewModel::executeAdbCommand,
                                onGrantSecureSettings = viewModel::grantAdbSecureSettings,
                                onClearLogs = viewModel::clearAdbLogs,
                                onSetMode = viewModel::setNetworkModeViaAdb
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

        // Floating Top Popup Alert (Shows on toggle failure or critical notices; DOES NOT CLOSE APP)
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
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "âš ï¸", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = uiState.popupMessage ?: "",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DISMISS",
                        color = Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { viewModel.dismissPopupMessage() }
                    )
                }
            }
        }
    }
}

// =====================================================================
// TOP BAR
// =====================================================================
@Composable
private fun MinimalTopBar(
    primaryColor: Color,
    is5G: Boolean,
    onRefresh: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

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
                    .background(primaryColor.copy(alpha = dotAlpha))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Network Toggle",
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = (-0.3).sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = primaryColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.25f))
            ) {
                Text(
                    text = if (is5G) "5G NR" else "4G LTE",
                    color = primaryColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// =====================================================================
// SEGMENTED NAVIGATION BAR
// =====================================================================
@Composable
private fun MinimalSegmentedNavBar(
    currentTab: NavigationTab,
    accentColor: Color,
    onTabSelected: (NavigationTab) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF101624),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            NavigationTab.entries.forEach { tab ->
                val isSelected = currentTab == tab
                val targetBg = if (isSelected) accentColor else Color.Transparent
                val targetText = if (isSelected) Color.Black else Color.White.copy(alpha = 0.65f)

                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = targetBg,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelected(tab) }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = tab.icon, fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = tab.title,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                            color = targetText,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// =====================================================================
// SCREEN 1: âš¡ SWITCHER
// =====================================================================
@Composable
private fun SwitcherScreen(
    uiState: MainScreenUiState,
    primaryColor: Color,
    is5G: Boolean,
    onRequestPhonePermission: () -> Unit,
    onToggleQuick: () -> Unit,
    onSelectMode: (NetworkMode) -> Unit,
    onOpenRadioInfo: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissStatus: () -> Unit,
    onRequestAddTile: () -> Unit
) {
    // 1. Permission Reminder (Subtle)
    if (!uiState.hasPermission) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1E293B).copy(alpha = 0.8f),
            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onRequestPhonePermission() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Grant Telephony Permission for live 5G/4G hardware detection",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(text = "GRANT", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    // 2. Tactile Hero Status Card
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleQuick() }
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF131B2E), Color(0xFF0D1322))
                    )
                )
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.SignalCellularAlt, contentDescription = null, tint = primaryColor, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = uiState.liveNetwork.displayName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = if (is5G) "5G NR" else "4G LTE",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = (-1).sp
                )

                Text(
                    text = if (is5G) "High-Speed 5G Standalone Active" else "Standard 4G LTE Battery-Saver Active",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Single primary switch button
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = primaryColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color.Black, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (is5G) "TAP TO SWITCH TO 4G LTE" else "TAP TO SWITCH TO 5G NR",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black,
                            letterSpacing = 0.5.sp
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
        Column(modifier = Modifier.padding(top = 10.dp)) {
            when (val s = uiState.switchStatus) {
                is SwitchStatus.Switching -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(13.dp), color = Color(0xFFF59E0B), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Applying ${s.targetMode.label()} switch (${s.remainingSeconds}s)â€¦",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFF59E0B)
                            )
                        }
                    }
                }
                is SwitchStatus.Success -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Successfully switched to ${s.mode.label()}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF10B981),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onDismissStatus, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
                is SwitchStatus.Failed -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Modem remained on ${s.currentNetwork}",
                                fontSize = 11.sp,
                                color = Color(0xFFEF4444),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "FORCE (*#*#4636#*#*)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8),
                                modifier = Modifier.clickable { onOpenRadioInfo() }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(onClick = onDismissStatus, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
                SwitchStatus.Idle -> {}
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 4. Clean 3-Way Mode Selector
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF101624),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val modes = listOf(
                Triple(NetworkMode.FIVE_G, "5G NR", "âš¡"),
                Triple(NetworkMode.AUTO, "Auto", "ðŸ”„"),
                Triple(NetworkMode.FOUR_G, "4G LTE", "ðŸ“¶")
            )
            modes.forEach { (mode, label, icon) ->
                val isSelected = uiState.selectedChoice == mode
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = if (isSelected) primaryColor else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectMode(mode) }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = icon, fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                            color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 5. Clear, Helpful Instructions Box
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0F172A),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "ðŸ’¡ HOW TOGGLING WORKS ON ANDROID",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF38BDF8),
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "â€¢ 1-Click Background Toggle: Works automatically if elevated permission is granted via Shizuku, ADB, or Root.\n" +
                        "â€¢ Universal Force Menu: Tap 'Force Menu' below to set 'NR only' or 'LTE only' directly in Android's hardware radio menu.\n" +
                        "â€¢ On-Device Terminal: Switch to the ADB Terminal tab above to grant permission directly on your phone without a PC.",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.65f),
                lineHeight = 15.sp
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 6. Minimal Quick Tools (3 essential buttons)
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

    Spacer(modifier = Modifier.height(20.dp))
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
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF101624),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        modifier = modifier
            .height(58.dp)
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
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
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
// SCREEN 2: ðŸª„ MACRO (STREAMLINED & FIXED)
// =====================================================================
@Composable
private fun MacroScreen(
    uiState: MainScreenUiState,
    accentColor: Color,
    onRunMacro: (NetworkMode) -> Unit,
    onClearLogs: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    val context = LocalContext.current
    val isAdbAuthorized = uiState.terminalState.isAuthorized || uiState.isDirectToggleGranted
    val isAccessibilityActive = MacroAccessibilityService.isAccessibilityEnabled(context)
    val isRunning = uiState.macroState is MacroExecutionState.Running

    var targetMacroMode by remember { mutableStateOf(NetworkMode.FIVE_G) }

    // Instructions Box
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF101624),
        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "ðŸª„ AUTOMATED NETWORK MACRO",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF38BDF8),
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Executes complete network mode enforcement in 1 tap without manual interaction.\n" +
                        "â€¢ If ADB/Shizuku permission is active: Mode switches instantly in the background (0ms delay).\n" +
                        "â€¢ If Accessibility is enabled: Automatically navigates Settings and taps Preferred Network Type for you.",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.7f),
                lineHeight = 15.sp
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Engine Status Indicator
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isAdbAuthorized || isAccessibilityActive) Color(0xFF10B981).copy(alpha = 0.08f) else Color(0xFFF59E0B).copy(alpha = 0.08f),
        border = BorderStroke(1.dp, if (isAdbAuthorized || isAccessibilityActive) Color(0xFF10B981).copy(alpha = 0.25f) else Color(0xFFF59E0B).copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = if (isAdbAuthorized) "âš¡" else if (isAccessibilityActive) "ðŸ‘†" else "âš ï¸", fontSize = 13.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isAdbAuthorized -> "Direct Shell Engine Active"
                        isAccessibilityActive -> "Accessibility Tap Engine Active"
                        else -> "Permission Setup Required"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isAdbAuthorized || isAccessibilityActive) Color(0xFF10B981) else Color(0xFFF59E0B)
                )
                Text(
                    text = when {
                        isAdbAuthorized -> "Instant background frequency switching operational"
                        isAccessibilityActive -> "Automated touch gesture navigation active"
                        else -> "Enable Accessibility or authorize ADB Terminal to automate"
                    },
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }
            if (!isAdbAuthorized && !isAccessibilityActive) {
                Text(
                    text = "ENABLE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF59E0B),
                    modifier = Modifier.clickable { onOpenAccessibilitySettings() }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Mode Selector (5G vs 4G)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF101624),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(NetworkMode.FIVE_G to "Enforce 5G NR", NetworkMode.FOUR_G to "Enforce 4G LTE").forEach { (mode, label) ->
                val isSelected = targetMacroMode == mode
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = if (isSelected) accentColor else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { targetMacroMode = mode }
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                        color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Large Primary "RUN MACRO" Action Button
    Button(
        onClick = { onRunMacro(targetMacroMode) },
        enabled = !isRunning,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (isRunning) "MACRO EXECUTINGâ€¦" else "RUN ${targetMacroMode.label().uppercase()} MACRO",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = Color.Black,
            letterSpacing = 0.4.sp
        )
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Live Execution Banner
    if (uiState.macroState is MacroExecutionState.Running) {
        val runState = uiState.macroState
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF10B981).copy(alpha = 0.12f),
            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "â–¶ EXECUTING: ${runState.stepLabel}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    Text(text = "${(runState.progress * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { runState.progress },
                    color = Color(0xFF10B981),
                    trackColor = Color.White.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    // Diagnostics / Macro Logs Console
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF070A12),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (uiState.macroLogs.isEmpty()) {
                    Text(
                        text = "Ready. Tap 'RUN MACRO' to execute.",
                        fontSize = 9.sp,
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

    Spacer(modifier = Modifier.height(20.dp))
}

// =====================================================================
// SCREEN 3: ðŸ’» ON-DEVICE ADB TERMINAL
// =====================================================================
@Composable
private fun AdbTerminalScreen(
    uiState: MainScreenUiState,
    onRequestPermission: () -> Unit,
    onExecuteCommand: (String) -> Unit,
    onGrantSecureSettings: () -> Unit,
    onClearLogs: () -> Unit,
    onSetMode: (NetworkMode) -> Unit
) {
    val termState = uiState.terminalState
    var inputCmd by remember { mutableStateOf("") }

    // Instructions Box
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF101624),
        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "ðŸ’» ON-DEVICE ADB TERMINAL",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFF59E0B),
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Run privileged shell commands directly on this device without needing a computer.\n" +
                        "1. Wireless Debugging / Shizuku: Tap 'Grant Permission' below to authorize the terminal.\n" +
                        "2. Once granted, execute network mode bitmasks, telephony tests, or custom ADB commands.",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.7f),
                lineHeight = 15.sp
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Terminal Authorization Status Card
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (termState.isAuthorized) Color(0xFF10B981).copy(alpha = 0.08f) else Color(0xFFF59E0B).copy(alpha = 0.08f),
        border = BorderStroke(1.dp, if (termState.isAuthorized) Color(0xFF10B981).copy(alpha = 0.25f) else Color(0xFFF59E0B).copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (termState.isAuthorized) Color(0xFF10B981) else Color(0xFFF59E0B))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (termState.isAuthorized) "Terminal Authorized" else "Authorization Required",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (termState.isAuthorized) Color(0xFF10B981) else Color(0xFFF59E0B)
                )
                Text(
                    text = termState.statusMessage,
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }
            if (!termState.isAuthorized) {
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("GRANT", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Interactive Terminal Window (Monospace Console)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF050811),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "SHELL CONSOLE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                }
                Text(
                    text = "CLEAR",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.clickable { onClearLogs() }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val scrollState = rememberScrollState()
            LaunchedEffect(termState.logs.size) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .verticalScroll(scrollState)
            ) {
                if (termState.logs.isEmpty()) {
                    Text(
                        text = "$ Ready for ADB commands.\nTap a quick action below or type any command.",
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

    Spacer(modifier = Modifier.height(10.dp))

    // Command Input Line
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedTextField(
            value = inputCmd,
            onValueChange = { inputCmd = it },
            placeholder = { Text("Enter command (e.g. pm grant â€¦)", fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f)) },
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
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF59E0B))
        ) {
            Icon(Icons.Default.Send, contentDescription = "Run", tint = Color.Black, modifier = Modifier.size(16.dp))
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Essential 1-Tap Quick Action Buttons (Minimal & Purposeful)
    Text(
        text = "QUICK ACTIONS",
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White.copy(alpha = 0.5f),
        letterSpacing = 0.6.sp
    )

    Spacer(modifier = Modifier.height(6.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        QuickActionButton(
            label = "Grant Secure Settings",
            onClick = onGrantSecureSettings,
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            label = "Force 5G NR",
            onClick = { onSetMode(NetworkMode.FIVE_G) },
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            label = "Force 4G LTE",
            onClick = { onSetMode(NetworkMode.FOUR_G) },
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(modifier = Modifier.height(20.dp))
}

@Composable
private fun QuickActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF101624),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = modifier
            .height(38.dp)
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

// =====================================================================
// SCREEN 4: âš™ï¸ SETTINGS & PERMISSIONS HUB
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
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
    )

    val bridgeState = uiState.bridgeState
    val isBridgeConnected = bridgeState is com.example.networktoggle.bridge.BridgeConnectionState.Connected
    val bridgeStatusColor = if (isBridgeConnected) Color(0xFF10B981) else Color(0xFFF59E0B)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF101624),
        border = BorderStroke(1.dp, bridgeStatusColor.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(bridgeStatusColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isBridgeConnected) {
                            val conn = bridgeState as com.example.networktoggle.bridge.BridgeConnectionState.Connected
                            val identity = if (conn.isRoot) "Root (UID 0)" else "ADB Shell (UID ${conn.uid})"
                            "Bridge Active • $identity"
                        } else {
                            "Bridge Inactive • app_process Daemon"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = bridgeStatusColor
                    )
                    Text(
                        text = if (isBridgeConnected) {
                            val conn = bridgeState as com.example.networktoggle.bridge.BridgeConnectionState.Connected
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
                fontSize = 9.sp,
                color = Color.White.copy(alpha = 0.65f),
                lineHeight = 13.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isBridgeConnected) {
                    Button(
                        onClick = onTestBridgePing,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f).height(32.dp)
                    ) {
                        Text("TEST PING", color = Color(0xFF38BDF8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onStopBridge,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f).height(32.dp)
                    ) {
                        Text("STOP DAEMON", color = Color(0xFFEF4444), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onStartBridgeViaAdb,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f).height(32.dp)
                    ) {
                        Text("START VIA ADB", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                    if (uiState.isKernelRootDetected) {
                        Button(
                            onClick = onStartBridgeViaRoot,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f).height(32.dp)
                        ) {
                            Text("START VIA ROOT", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val pcCmd = com.example.networktoggle.bridge.PrivilegedBridgeManager.getAdbPcCommand(context)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF070A12),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
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
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text("COPY", color = Color(0xFF38BDF8), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Section 1: Permissions & Access Status
    Text(
        text = "PERMISSIONS & ACCESS STATUS",
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        color = Color(0xFF38BDF8),
        letterSpacing = 0.5.sp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF101624),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 1. Telephony Permission
            PermissionStatusRow(
                title = "Telephony Hardware Access",
                subtitle = if (uiState.hasPermission) "Granted â€¢ Live 5G/4G radio telemetry operational" else "Action needed â€¢ Required for live network reading",
                isGranted = uiState.hasPermission,
                actionLabel = if (!uiState.hasPermission) "GRANT" else null,
                onAction = onRequestPhonePermission
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

            // 2. On-Device ADB / Shizuku
            PermissionStatusRow(
                title = "On-Device ADB (Shizuku)",
                subtitle = if (isAdbAuthorized) "Authorized â€¢ Instant background frequency switching ready" else "Not authorized â€¢ Enables direct 1-tap mode toggles",
                isGranted = isAdbAuthorized,
                actionLabel = if (!isAdbAuthorized) "AUTHORIZE" else null,
                onAction = onRequestAdbPermission
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

            // 3. Accessibility Automation
            PermissionStatusRow(
                title = "Accessibility Automation",
                subtitle = if (isAccessibilityActive) "Active â€¢ Automated touch navigation & SIM clicks ready" else "Inactive â€¢ Automates Settings navigation without ADB",
                isGranted = isAccessibilityActive,
                actionLabel = if (!isAccessibilityActive) "ENABLE" else null,
                onAction = onOpenAccessibilitySettings
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

            // 4. Kernel Root Access
            PermissionStatusRow(
                title = "Kernel Root (SU)",
                subtitle = if (uiState.isKernelRootDetected) "Detected â€¢ Elevated SU shell command execution available" else "Not detected â€¢ Standard secure Android sandbox active",
                isGranted = uiState.isKernelRootDetected,
                actionLabel = null,
                onAction = {}
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Section 2: Quick Settings / Control Panel Tiles
    Text(
        text = "CONTROL PANEL TILES",
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        color = Color(0xFF38BDF8),
        letterSpacing = 0.5.sp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF101624),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

    Spacer(modifier = Modifier.height(16.dp))

    // Section 3: Cellular Testing & Diagnostic Tools
    Text(
        text = "SYSTEM RADIO MENUS",
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        color = Color(0xFF38BDF8),
        letterSpacing = 0.5.sp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
    )

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF101624),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.weight(1f).clickable { onOpenRadioInfo() }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = "ðŸ”§ Force Radio Menu", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(text = "*#*#4636#*#* Testing activity", fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f))
            }
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF101624),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.weight(1f).clickable { onOpenMobileSettings() }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(text = "ðŸŒ SIMs & Network", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(text = "System operator settings", fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f))
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Section 4: One-time PC ADB Command
    Text(
        text = "MANUAL PC ADB COMMAND",
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        color = Color(0xFF38BDF8),
        letterSpacing = 0.5.sp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF070A12),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "If not using Shizuku, run this once via computer terminal to grant background switching:",
                fontSize = 9.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(6.dp))
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
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("COPY", color = Color(0xFF38BDF8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))
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
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isGranted) Color(0xFF10B981) else Color(0xFFF59E0B))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(text = subtitle, fontSize = 9.sp, color = Color.White.copy(alpha = 0.55f))
        }
        if (actionLabel != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                modifier = Modifier.clickable { onAction() }
            ) {
                Text(
                    text = actionLabel,
                    color = Color(0xFF38BDF8),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
            Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(text = subtitle, fontSize = 9.sp, color = Color.White.copy(alpha = 0.55f))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onAdd,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = Modifier.height(28.dp)
        ) {
            Text(buttonText, color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
    }
}
