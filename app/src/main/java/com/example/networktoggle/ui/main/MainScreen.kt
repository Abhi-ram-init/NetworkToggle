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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TouchApp
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
import com.example.networktoggle.macro.BlockCategory
import com.example.networktoggle.macro.LogLevel
import com.example.networktoggle.macro.MacroAccessibilityService
import com.example.networktoggle.macro.MacroActionType
import com.example.networktoggle.macro.MacroBlockCatalog
import com.example.networktoggle.macro.MacroBlockCatalogItem
import com.example.networktoggle.macro.MacroExecutionState
import com.example.networktoggle.macro.MacroManager
import com.example.networktoggle.macro.MacroStep
import com.example.networktoggle.macro.RecordedMacro
import com.example.networktoggle.network.NetworkMode
import com.example.networktoggle.network.NetworkModeManager
import com.example.networktoggle.widget.CompactToggleWidget

enum class MainTab(val title: String, val icon: String) {
    SWITCHER("Quick Switch", "⚡"),
    AUTOMATIONS("Automations", "🪄")
}

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
                if (granted) "⚡ Root permission granted! Switch executed."
                else "Root request denied or unavailable.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    MainScreenContent(
        uiState = uiState,
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
        onStartRecording = viewModel::startRecordingMacro,
        onRecordAction = viewModel::recordMacroAction,
        onFinishRecording = viewModel::finishRecordingMacro,
        onCancelRecording = viewModel::cancelRecordingMacro,
        onDeleteMacro = viewModel::deleteMacro,
        onRunMacro = { macro ->
            viewModel.runMacro(
                macro = macro,
                onPromptAddTile = {
                    viewModel.requestAddTileToControlPanel(isMacroTile = true, context = context) { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                },
                onExitApp = {
                    (context as? android.app.Activity)?.finish()
                }
            )
        },
        onBuildAndRunGuidedMacro = { carrier, targetMode ->
            val macro = viewModel.buildAndSaveGuidedSimToggleMacro(carrier, targetMode)
            if (macro != null) {
                viewModel.runMacro(
                    macro = macro,
                    onPromptAddTile = {
                        viewModel.requestAddTileToControlPanel(isMacroTile = true, context = context) { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    onExitApp = {
                        (context as? android.app.Activity)?.finish()
                    }
                )
            }
        },
        onCreateCustomBlockMacro = { name, steps ->
            viewModel.createCustomBlockMacro(name, steps)
        },
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
    onSelectChoice: (NetworkMode) -> Unit,
    onToggleDial: () -> Unit,
    onRequestPermission: () -> Unit,
    onRequestKernelGrant: () -> Unit,
    onOpenRadioInfo: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onDismissStatus: () -> Unit,
    onStartRecording: (String) -> Unit,
    onRecordAction: (MacroActionType, Int, String) -> Unit,
    onFinishRecording: (String?) -> Unit,
    onCancelRecording: () -> Unit,
    onDeleteMacro: (String) -> Unit,
    onRunMacro: (RecordedMacro) -> Unit,
    onBuildAndRunGuidedMacro: (String, String) -> Unit,
    onCreateCustomBlockMacro: (String, List<MacroStep>) -> Unit,
    onClearMacroLogs: () -> Unit,
    onRequestAddControlPanelTile: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val is5G = uiState.currentConnectedMode == NetworkMode.FIVE_G
    val primaryColor = if (is5G) Color(0xFF10B981) else Color(0xFF06B6D4)
    val bgColor = Color(0xFF090D16)
    var currentTab by remember { mutableStateOf(MainTab.SWITCHER) }

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
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Sleek Top App Bar with Live State Badge
            TopNavigationBar(
                primaryColor = primaryColor,
                is5G = is5G,
                onRefresh = onRefresh
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Modern Fluid Tab Switcher
            SegmentedTabSwitcher(
                currentTab = currentTab,
                accentColor = primaryColor,
                savedMacroCount = uiState.savedMacros.size,
                onTabSelected = { currentTab = it }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Main Tab Content (Scrollable)
            Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (currentTab) {
                        MainTab.SWITCHER -> {
                            SwitcherTabContent(
                                uiState = uiState,
                                primaryColor = primaryColor,
                                is5G = is5G,
                                onRequestPermission = onRequestPermission,
                                onToggleDial = onToggleDial,
                                onSelectChoice = onSelectChoice,
                                onOpenRadioInfo = onOpenRadioInfo,
                                onOpenSettings = onOpenSettings,
                                onDismissStatus = onDismissStatus,
                                onRequestAddControlPanelTile = onRequestAddControlPanelTile,
                                onClearMacroLogs = onClearMacroLogs
                            )
                        }
                        MainTab.AUTOMATIONS -> {
                            AutomationsTabContent(
                                uiState = uiState,
                                accentColor = primaryColor,
                                onBuildAndRunGuidedMacro = onBuildAndRunGuidedMacro,
                                onStartRecording = onStartRecording,
                                onRecordAction = onRecordAction,
                                onFinishRecording = onFinishRecording,
                                onCancelRecording = onCancelRecording,
                                onDeleteMacro = onDeleteMacro,
                                onRunMacro = onRunMacro,
                                onCreateCustomBlockMacro = onCreateCustomBlockMacro
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TOP BAR
// -------------------------------------------------------------
@Composable
private fun TopNavigationBar(
    primaryColor: Color,
    is5G: Boolean,
    onRefresh: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
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
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(primaryColor.copy(alpha = dotAlpha))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Network Toggle",
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = (-0.4).sp
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = primaryColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.28f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(primaryColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (is5G) "5G ACTIVE" else "4G ACTIVE",
                        color = primaryColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh Telemetry",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// DUAL TAB NAVIGATION SWITCHER
// -------------------------------------------------------------
@Composable
private fun SegmentedTabSwitcher(
    currentTab: MainTab,
    accentColor: Color,
    savedMacroCount: Int,
    onTabSelected: (MainTab) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF111726),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MainTab.entries.forEach { tab ->
                val isSelected = currentTab == tab
                val targetBg = if (isSelected) accentColor else Color.Transparent
                val targetText = if (isSelected) Color.Black else Color.White.copy(alpha = 0.65f)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = targetBg,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelected(tab) }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = tab.icon, fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tab.title,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                            color = targetText
                        )
                        if (tab == MainTab.AUTOMATIONS && savedMacroCount > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color.Black.copy(alpha = 0.15f) else accentColor.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "$savedMacroCount",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else accentColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 1: SWITCHER / DASHBOARD CONTENT
// -------------------------------------------------------------
@Composable
private fun SwitcherTabContent(
    uiState: MainScreenUiState,
    primaryColor: Color,
    is5G: Boolean,
    onRequestPermission: () -> Unit,
    onToggleDial: () -> Unit,
    onSelectChoice: (NetworkMode) -> Unit,
    onOpenRadioInfo: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissStatus: () -> Unit,
    onRequestAddControlPanelTile: (Boolean) -> Unit,
    onClearMacroLogs: () -> Unit,
) {
    // Permission Banner (subtle & non-intrusive)
    if (!uiState.hasPermission) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF1E293B).copy(alpha = 0.8f),
            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onRequestPermission() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Phone State Permission",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Grant permission for real-time 5G/4G hardware telemetry",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                }
                Text(
                    text = "GRANT",
                    color = Color(0xFF38BDF8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
    }

    // Main Interactive Hero Toggle Card with Glowing Gradient
    TactileHeroCard(
        uiState = uiState,
        primaryColor = primaryColor,
        is5G = is5G,
        onToggle = onToggleDial
    )

    // Inline Switching & Verification Status
    InlineStatusIndicator(
        status = uiState.switchStatus,
        switchingColor = Color(0xFFF59E0B),
        failedColor = Color(0xFFEF4444),
        onOpenRadioInfo = onOpenRadioInfo,
        onDismissStatus = onDismissStatus
    )

    Spacer(modifier = Modifier.height(16.dp))

    // 3-Pill Network Mode Selector
    SegmentedModeSelector(
        selectedMode = uiState.selectedChoice,
        accentColor = primaryColor,
        onSelectMode = onSelectChoice
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Quick Hardware & Control Tools Grid (2x2)
    QuickToolsGrid(
        primaryColor = primaryColor,
        onOpenRadioInfo = onOpenRadioInfo,
        onOpenSettings = onOpenSettings,
        onRequestAddTile = { onRequestAddControlPanelTile(false) }
    )

    Spacer(modifier = Modifier.height(18.dp))

    // Diagnostics & Process Trace Console
    DiagnosticsConsole(
        uiState = uiState,
        accentColor = primaryColor,
        onClearLogs = onClearMacroLogs
    )

    Spacer(modifier = Modifier.height(24.dp))
}

// -------------------------------------------------------------
// INTERACTIVE TACTILE HERO CARD
// -------------------------------------------------------------
@Composable
private fun TactileHeroCard(
    uiState: MainScreenUiState,
    primaryColor: Color,
    is5G: Boolean,
    onToggle: () -> Unit,
) {
    val carrier = uiState.inspectedProcess?.carrierName?.takeIf { it.isNotBlank() } ?: "Cellular Modem"
    val displayType = uiState.liveNetwork.displayName
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "heroScale"
    )

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF141D33),
            Color(0xFF0F1626)
        )
    )

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onToggle
            )
    ) {
        Box(
            modifier = Modifier
                .background(gradientBrush)
                .padding(22.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Carrier & Telemetry Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.SignalCellularAlt,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$carrier • $displayType",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Network Speed/Technology Moniker
                Text(
                    text = if (is5G) "5G NR" else "4G LTE",
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = (-1.2).sp
                )

                Text(
                    text = if (is5G) "Ultra-High Speed NR Active" else "Standard Power LTE Active",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Tactile Action Switch Button
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = primaryColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FlashOn,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
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
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Column(modifier = Modifier.padding(top = 10.dp)) {
            when (status) {
                is SwitchStatus.Switching -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = switchingColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, switchingColor.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = switchingColor,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Verifying ${status.targetMode.label()} switch (${status.remainingSeconds}s)...",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = switchingColor,
                                modifier = Modifier.weight(1f)
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
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Switched to ${status.mode.label()} successfully",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF10B981),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onDismissStatus, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(13.dp))
                            }
                        }
                    }
                }

                is SwitchStatus.Failed -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = failedColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, failedColor.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Modem remained on ${status.currentNetwork}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = failedColor,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "FORCE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8),
                                modifier = Modifier
                                    .clickable { onOpenRadioInfo() }
                                    .padding(4.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(onClick = onDismissStatus, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(13.dp))
                            }
                        }
                    }
                }

                SwitchStatus.Idle -> {}
            }
        }
    }
}

// -------------------------------------------------------------
// 3-WAY SEGMENTED MODE SELECTOR
// -------------------------------------------------------------
@Composable
private fun SegmentedModeSelector(
    selectedMode: NetworkMode,
    accentColor: Color,
    onSelectMode: (NetworkMode) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF131926),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val modes = listOf(
                Triple(NetworkMode.FIVE_G, "5G NR", "⚡"),
                Triple(NetworkMode.AUTO, "Auto", "🔄"),
                Triple(NetworkMode.FOUR_G, "4G LTE", "📶")
            )

            modes.forEach { (mode, label, icon) ->
                val isSelected = selectedMode == mode
                val targetBg = if (isSelected) accentColor else Color.Transparent
                val targetText = if (isSelected) Color.Black else Color.White.copy(alpha = 0.7f)

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = targetBg,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectMode(mode) }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 11.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = icon, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                            color = targetText
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// QUICK HARDWARE & CONTROL TOOLS GRID
// -------------------------------------------------------------
@Composable
private fun QuickToolsGrid(
    primaryColor: Color,
    onOpenRadioInfo: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestAddTile: () -> Unit,
) {
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ToolGridItem(
                icon = Icons.Default.Tune,
                title = "Force Menu",
                subtitle = "*#*#4636#*#*",
                accentColor = primaryColor,
                onClick = onOpenRadioInfo,
                modifier = Modifier.weight(1f)
            )

            ToolGridItem(
                icon = Icons.Default.Settings,
                title = "SIM Settings",
                subtitle = "Carrier config",
                accentColor = primaryColor,
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ToolGridItem(
                icon = Icons.Default.Widgets,
                title = "Quick Tile",
                subtitle = "Control Panel",
                accentColor = primaryColor,
                onClick = onRequestAddTile,
                modifier = Modifier.weight(1f)
            )

            ToolGridItem(
                icon = Icons.Default.Add,
                title = "Home Widget",
                subtitle = "Pin to launcher",
                accentColor = primaryColor,
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val awm = context.getSystemService(AppWidgetManager::class.java)
                        val provider = ComponentName(context, CompactToggleWidget::class.java)
                        if (awm != null && awm.isRequestPinAppWidgetSupported) {
                            awm.requestPinAppWidget(provider, null, null)
                            Toast.makeText(context, "Pinning widget to home screen...", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Long-press home screen to add widget.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ToolGridItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF111726),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = modifier
            .height(58.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 2: AUTOMATION & MACRO STUDIO
// -------------------------------------------------------------
@Composable
private fun AutomationsTabContent(
    uiState: MainScreenUiState,
    accentColor: Color,
    onBuildAndRunGuidedMacro: (String, String) -> Unit,
    onStartRecording: (String) -> Unit,
    onRecordAction: (MacroActionType, Int, String) -> Unit,
    onFinishRecording: (String?) -> Unit,
    onCancelRecording: () -> Unit,
    onDeleteMacro: (String) -> Unit,
    onRunMacro: (RecordedMacro) -> Unit,
    onCreateCustomBlockMacro: (String, List<MacroStep>) -> Unit,
) {
    val context = LocalContext.current
    val serviceActiveFlow by MacroAccessibilityService.isServiceActive.collectAsStateWithLifecycle()
    val isAccessibilityActive = serviceActiveFlow || MacroAccessibilityService.isAccessibilityEnabled(context)
    val isRunning = uiState.macroState is MacroExecutionState.Running

    val detectedCarrier = uiState.inspectedProcess?.carrierName?.takeIf { it.isNotBlank() } ?: "Jio"

    var isBlockEditorOpen by remember { mutableStateOf(false) }
    var blockEditorName by remember { mutableStateOf("") }
    val blockEditorSteps = remember { mutableStateListOf<MacroStep>() }

    var isCatalogDialogVisible by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf<BlockCategory?>(null) }
    var isPermissionDialogVisible by remember { mutableStateOf(false) }

    // Accessibility Onboarding Dialog
    if (isPermissionDialogVisible) {
        AlertDialog(
            onDismissRequest = { isPermissionDialogVisible = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🛡️", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enable Macro Accessibility",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "To automate SIM toggles, screen clicks, and touch gestures without root, Android requires the Accessibility Service permission.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E293B),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "What this enables:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• Automated Settings ➔ SIM ➔ 4G toggle\n• Screen gestures & touch simulation\n• 120+ Triggers & 200+ Block actions",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                lineHeight = 16.sp
                            )
                        }
                    }
                    Text(
                        text = "💡 Android 13+ Note: If the setting appears greyed out, go to App Info ➔ tap 3 dots (top right) ➔ tap 'Allow restricted settings' first.",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isPermissionDialogVisible = false
                        MacroAccessibilityService.openAccessibilitySettings(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text("OPEN SETTINGS", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { isPermissionDialogVisible = false }) {
                    Text("LATER", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                }
            },
            containerColor = Color(0xFF111726),
            shape = RoundedCornerShape(18.dp)
        )
    }

    // Block Catalog Browser Dialog (120+ Triggers & 200+ Actions)
    if (isCatalogDialogVisible) {
        AlertDialog(
            onDismissRequest = { isCatalogDialogVisible = false },
            title = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🧩", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Macro Block Catalog",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "120+ Triggers & 200+ Actions without root",
                        fontSize = 11.sp,
                        color = Color(0xFF38BDF8)
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    // Category Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val categories = listOf(null) + BlockCategory.entries.take(4)
                        categories.forEach { cat ->
                            val isSelected = selectedCategoryFilter == cat
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) accentColor else Color.White.copy(alpha = 0.08f),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedCategoryFilter = cat }
                            ) {
                                Text(
                                    text = cat?.icon ?: "All",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else Color.White,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Blocks List
                    val filteredItems = MacroBlockCatalog.items.filter {
                        selectedCategoryFilter == null || it.category == selectedCategoryFilter
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filteredItems.forEach { item ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.05f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val newStep = MacroStep(
                                            actionType = item.actionType,
                                            label = item.title,
                                            delaySeconds = item.defaultDelay,
                                            shellCommand = item.defaultParam
                                        )
                                        blockEditorSteps.add(newStep)
                                        isCatalogDialogVisible = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = item.icon, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = item.description,
                                            fontSize = 10.sp,
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Add Block",
                                        tint = accentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isCatalogDialogVisible = false }) {
                    Text("CLOSE", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                }
            },
            containerColor = Color(0xFF111726),
            shape = RoundedCornerShape(18.dp)
        )
    }

    // 1. LIVE MACRO EXECUTION ACTIVE BANNER
    if (uiState.macroState is MacroExecutionState.Running) {
        val runState = uiState.macroState
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF10B981).copy(alpha = 0.12f),
            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF10B981)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "RUNNING: ${runState.macroName.uppercase()}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF10B981),
                            letterSpacing = 0.5.sp
                        )
                    }
                    Text(
                        text = "Step ${runState.currentStepIndex}/${runState.totalSteps}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = runState.stepLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { runState.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF10B981),
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
    } else if (uiState.macroState is MacroExecutionState.Completed) {
        val compState = uiState.macroState
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (compState.success) Color(0xFF10B981).copy(alpha = 0.1f) else Color(0xFFEF4444).copy(alpha = 0.1f),
            border = BorderStroke(1.dp, if (compState.success) Color(0xFF10B981).copy(alpha = 0.35f) else Color(0xFFEF4444).copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = if (compState.success) "✅" else "⚠️", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (compState.success) "Macro Finished Successfully" else "Macro Finished with Warnings",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (compState.success) Color(0xFF10B981) else Color(0xFFEF4444)
                    )
                    Text(
                        text = compState.summary,
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
    }

    // 2. ACCESSIBILITY STATUS BADGE
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isAccessibilityActive) Color(0xFF10B981).copy(alpha = 0.08f) else Color(0xFFF59E0B).copy(alpha = 0.08f),
        border = BorderStroke(1.dp, if (isAccessibilityActive) Color(0xFF10B981).copy(alpha = 0.25f) else Color(0xFFF59E0B).copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (!isAccessibilityActive) isPermissionDialogVisible = true
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = if (isAccessibilityActive) "🛡️" else "⚠️", fontSize = 15.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isAccessibilityActive) "Accessibility Service Active" else "Accessibility Permission Required",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isAccessibilityActive) Color(0xFF10B981) else Color(0xFFF59E0B)
                )
                Text(
                    text = if (isAccessibilityActive) "Gestures, clicks, and 120+ triggers operational without root" else "Tap to enable accessibility service in Android Settings",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
            if (!isAccessibilityActive) {
                Text(
                    text = "ENABLE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFF59E0B)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 3. FEATURED: GUIDED SIM NETWORK TOGGLE (1-TAP AUTOMATION)
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF111726),
        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "📲", fontSize = 15.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GUIDED SIM TOGGLE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF38BDF8),
                        letterSpacing = 0.6.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF38BDF8).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "CARRIER: $detectedCarrier",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Automates the entire sequence on your device without root:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Visual Step Flow Breadcrumbs
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.04f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    GuidedStepItem(number = "1", title = "Open Android Settings")
                    GuidedStepItem(number = "2", title = "Open Network & Internet / Connections")
                    GuidedStepItem(number = "3", title = "Select SIMs & Mobile Network")
                    GuidedStepItem(number = "4", title = "Select $detectedCarrier SIM")
                    GuidedStepItem(number = "5", title = "Read SIM Settings & Telemetry")
                    GuidedStepItem(number = "6", title = "Scroll to Preferred Network Type")
                    GuidedStepItem(number = "7", title = "Enforce 4G LTE Mode")
                    GuidedStepItem(number = "8", title = "Prompt to Add Control Panel Tile & Exit")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    if (!isAccessibilityActive) {
                        isPermissionDialogVisible = true
                    } else {
                        onBuildAndRunGuidedMacro(detectedCarrier, "4G")
                    }
                },
                enabled = !isRunning,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "RUN GUIDED SIM TOGGLE ($detectedCarrier ➔ 4G)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                    letterSpacing = 0.4.sp
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // 4. ACTION BAR: BUILD CUSTOM OR RECORD GESTURES
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF111726),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
            modifier = Modifier
                .weight(1f)
                .height(60.dp)
                .clickable {
                    isBlockEditorOpen = true
                    blockEditorName = "Custom Macro ${uiState.savedMacros.size + 1}"
                    blockEditorSteps.clear()
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🧩", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = "Block Builder", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(text = "120+ Triggers", fontSize = 10.sp, color = accentColor)
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF111726),
            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f)),
            modifier = Modifier
                .weight(1f)
                .height(60.dp)
                .clickable {
                    if (!isAccessibilityActive) {
                        isPermissionDialogVisible = true
                    } else {
                        onStartRecording("Touch Macro ${uiState.savedMacros.size + 1}")
                    }
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "⏺", fontSize = 18.sp, color = Color(0xFFEF4444))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = "Record Touch", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(text = "Capture gestures", fontSize = 10.sp, color = Color(0xFFEF4444))
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // 5. BLOCK-BASED MACRO BUILDER (EXPANDABLE)
    if (isBlockEditorOpen) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF111726),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🧩", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Block-Based Builder",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }
                    Text(
                        text = "${blockEditorSteps.size} Blocks",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = blockEditorName,
                    onValueChange = { blockEditorName = it },
                    label = { Text("Macro Name", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Block Sequence Chain
                if (blockEditorSteps.isEmpty()) {
                    Text(
                        text = "No blocks added yet. Tap '+ Add Block from Catalog' or use the quick chips below.",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        blockEditorSteps.forEachIndexed { index, step ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.05f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}.",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.4f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = step.actionType.icon, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = step.label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                        if (step.shellCommand.isNotBlank()) {
                                            Text(
                                                text = "Param: ${step.shellCommand}",
                                                fontSize = 9.sp,
                                                color = Color.White.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { blockEditorSteps.removeAt(index) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = Color.White.copy(alpha = 0.5f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Catalog Button
                Button(
                    onClick = { isCatalogDialogVisible = true },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("+ Add Block from Catalog (120+ Triggers)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Chips Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RecordActionChip(
                        label = "5G",
                        icon = "⚡",
                        onClick = { blockEditorSteps.add(MacroStep(actionType = MacroActionType.SET_5G, label = "Switch to 5G NR")) },
                        modifier = Modifier.weight(1f)
                    )
                    RecordActionChip(
                        label = "4G",
                        icon = "📶",
                        onClick = { blockEditorSteps.add(MacroStep(actionType = MacroActionType.SET_4G, label = "Switch to 4G LTE")) },
                        modifier = Modifier.weight(1f)
                    )
                    RecordActionChip(
                        label = "Wait 1s",
                        icon = "⏱",
                        onClick = { blockEditorSteps.add(MacroStep(actionType = MacroActionType.WAIT_DELAY, label = "Wait 1s", delaySeconds = 1)) },
                        modifier = Modifier.weight(1f)
                    )
                    RecordActionChip(
                        label = "Tap",
                        icon = "👆",
                        onClick = { blockEditorSteps.add(MacroStep(actionType = MacroActionType.SCREEN_TAP, label = "Tap Screen", shellCommand = "540,960")) },
                        modifier = Modifier.weight(1f)
                    )
                    RecordActionChip(
                        label = "Home",
                        icon = "🏠",
                        onClick = { blockEditorSteps.add(MacroStep(actionType = MacroActionType.GO_HOME, label = "Go to Home Screen")) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Save & Cancel Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (blockEditorSteps.isNotEmpty()) {
                                onCreateCustomBlockMacro(blockEditorName, blockEditorSteps.toList())
                                isBlockEditorOpen = false
                            }
                        },
                        enabled = blockEditorSteps.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Text("SAVE CUSTOM MACRO", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Black)
                    }

                    OutlinedButton(
                        onClick = { isBlockEditorOpen = false },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.7f)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text("CANCEL", fontSize = 11.sp)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    // 6. TOUCH RECORDING ACTIVE BANNER
    if (uiState.isRecordingMacro) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFFEF4444).copy(alpha = 0.1f),
            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RECORDING: ${uiState.recordingMacroName}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFEF4444),
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Actions performed in the app or tapped below are recorded in sequence:",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Live Steps List
                if (uiState.currentlyRecordedSteps.isEmpty()) {
                    Text(
                        text = "No steps recorded yet. Tap any button below to add a step.",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        uiState.currentlyRecordedSteps.forEachIndexed { idx, stp ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${idx + 1}.",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = stp.actionType.icon, fontSize = 11.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = stp.label, fontSize = 10.sp, color = Color.White)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Action Recorder Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RecordActionChip(
                        label = "5G Mode",
                        icon = "⚡",
                        onClick = { onRecordAction(MacroActionType.SET_5G, 1, "") },
                        modifier = Modifier.weight(1f)
                    )
                    RecordActionChip(
                        label = "4G Mode",
                        icon = "📶",
                        onClick = { onRecordAction(MacroActionType.SET_4G, 1, "") },
                        modifier = Modifier.weight(1f)
                    )
                    RecordActionChip(
                        label = "Wait 1s",
                        icon = "⏱",
                        onClick = { onRecordAction(MacroActionType.WAIT_DELAY, 1, "") },
                        modifier = Modifier.weight(1f)
                    )
                    RecordActionChip(
                        label = "Cycle Data",
                        icon = "🔄",
                        onClick = { onRecordAction(MacroActionType.CYCLE_DATA, 1, "") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RecordActionChip(
                        label = "Go Home",
                        icon = "🏠",
                        onClick = { onRecordAction(MacroActionType.GO_HOME, 0, "") },
                        modifier = Modifier.weight(1f)
                    )
                    RecordActionChip(
                        label = "Tap Screen",
                        icon = "👆",
                        onClick = { onRecordAction(MacroActionType.SCREEN_TAP, 0, "540,960") },
                        modifier = Modifier.weight(1f)
                    )
                    RecordActionChip(
                        label = "Swipe Up",
                        icon = "👉",
                        onClick = { onRecordAction(MacroActionType.SCREEN_SWIPE, 0, "540,1400,540,600") },
                        modifier = Modifier.weight(1f)
                    )
                    RecordActionChip(
                        label = "Force Menu",
                        icon = "⚙",
                        onClick = { onRecordAction(MacroActionType.LAUNCH_RADIO_INFO, 0, "") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Finish & Cancel Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onFinishRecording(uiState.recordingMacroName) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Text(
                            text = "SAVE RECORDED MACRO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }

                    OutlinedButton(
                        onClick = onCancelRecording,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White.copy(alpha = 0.7f)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text(text = "CANCEL", fontSize = 11.sp)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    // 7. SAVED MACROS LIBRARY
    Text(
        text = "SAVED AUTOMATIONS (${uiState.savedMacros.size})",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White.copy(alpha = 0.5f),
        letterSpacing = 0.8.sp
    )

    Spacer(modifier = Modifier.height(8.dp))

    if (uiState.savedMacros.isEmpty()) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.03f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "🪄", fontSize = 24.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "No Saved Automations Yet",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap 'RUN GUIDED SIM TOGGLE' above or use the Block Builder to create automated workflows.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            uiState.savedMacros.forEach { macro ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF111726),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = macro.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            val stepsSummary = macro.steps.take(3).joinToString(" ➔ ") { it.label }
                            val extra = if (macro.steps.size > 3) " +${macro.steps.size - 3} more" else ""
                            Text(
                                text = "$stepsSummary$extra",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.5f),
                                maxLines = 1
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Play Button
                        Button(
                            onClick = {
                                if (!isAccessibilityActive) {
                                    isPermissionDialogVisible = true
                                } else {
                                    onRunMacro(macro)
                                }
                            },
                            enabled = !isRunning,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "RUN", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Black)
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Delete Button
                        IconButton(
                            onClick = { onDeleteMacro(macro.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Macro", tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun GuidedStepItem(number: String, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(Color(0xFF38BDF8).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = number, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = title, fontSize = 10.sp, color = Color.White.copy(alpha = 0.75f))
    }
}

@Composable
private fun RecordActionChip(
    label: String,
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = 0.08f),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, fontSize = 10.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

// -------------------------------------------------------------
// COLLAPSIBLE DIAGNOSTICS CONSOLE
// -------------------------------------------------------------
@Composable
private fun DiagnosticsConsole(
    uiState: MainScreenUiState,
    accentColor: Color,
    onClearLogs: () -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF111726),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Diagnostics & Process Trace",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "COPY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier
                                .clickable {
                                    val text = uiState.macroLogs.joinToString("\n") { "[${it.timestamp}] ${it.message}" }
                                    clipboardManager.setText(AnnotatedString(text))
                                    Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show()
                                }
                                .padding(4.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "CLEAR",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier
                                .clickable { onClearLogs() }
                                .padding(4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF07090F),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(10.dp)
                        ) {
                            if (uiState.macroLogs.isEmpty()) {
                                Text(
                                    text = "Ready. No actions logged yet.",
                                    fontSize = 9.sp,
                                    color = Color.White.copy(alpha = 0.35f),
                                    fontFamily = FontFamily.Monospace
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    uiState.macroLogs.forEach { log ->
                                        val color = when (log.level) {
                                            LogLevel.SUCCESS -> Color(0xFF10B981)
                                            LogLevel.EXEC -> Color(0xFFF59E0B)
                                            LogLevel.PROCESS -> Color(0xFF38BDF8)
                                            else -> Color.White.copy(alpha = 0.65f)
                                        }
                                        Text(
                                            text = "[${log.timestamp}] ${log.message}",
                                            fontSize = 9.sp,
                                            color = color,
                                            fontFamily = FontFamily.Monospace
                                        )
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
