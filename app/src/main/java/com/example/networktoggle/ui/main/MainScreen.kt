package com.example.networktoggle.ui.main

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.networktoggle.network.NetworkMode

/**
 * Minimal, Flutter-grade Material 3 Single-Screen UI for NetworkToggle.
 * Focuses exclusively on 4G ↔ 5G switching powered directly by Shizuku.
 * Unwanted buttons, complex macro editors, and multi-tab clutter are removed.
 */
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
                .size(360.dp)
                .align(Alignment.TopCenter)
                .offset(y = (-60).dp)
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
                    onRequestShizuku = viewModel::requestAdbPermission,
                    onRefresh = viewModel::refreshLiveNetwork
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

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

                Spacer(modifier = Modifier.height(28.dp))

                // 2. Minimalist 2-Way Tactile Segmented Switcher (4G LTE ↔ 5G NR)
                MinimalTwoWayToggle(
                    currentMode = uiState.selectedChoice,
                    isSwitching = uiState.switchStatus is SwitchStatus.Switching,
                    onSelectMode = { mode ->
                        viewModel.switchNetworkViaShizuku(mode, context)
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 3. Shizuku Status & Action Banner
                ShizukuActionCard(
                    uiState = uiState,
                    primaryColor = primaryColor,
                    onRequestPermission = viewModel::requestAdbPermission,
                    onGrantWriteSecureSettings = viewModel::grantAdbSecureSettings
                )

                Spacer(modifier = Modifier.height(20.dp))

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
            .padding(horizontal = 20.dp, vertical = 12.dp),
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
                text = "Minimal Shizuku Edition",
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
                            else -> "Shizuku Offline"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isAuthorized -> Color(0xFF10B981)
                            isShizukuRunning -> Color(0xFFF59E0B)
                            else -> Color(0xFFEF4444)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Refresh Button
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier
                    .size(36.dp)
                    .clickable { onRefresh() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// =====================================================================
// FLUTTER-STYLE CONCENTRIC RADAR HERO CARD
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
    val techLabel = if (liveNet.detail.isNotBlank() && liveNet.detail != "UNKNOWN") {
        liveNet.detail
    } else {
        if (is5G) "STANDALONE / NSA" else "LTE ADVANCED"
    }

    // Concentric Radar Infinite Breathing Pulsing Animation
    val infiniteTransition = rememberInfiniteTransition(label = "RadarTransition")
    val ringScale1 by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarRing1"
    )
    val ringAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarAlpha1"
    )

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.75f),
        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f)),
        shadowElevation = 16.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 32.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Live Carrier Pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.06f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Text(
                    text = liveNet.displayName.ifBlank { "Mobile Network" }.uppercase(),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
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
                            imageVector = if (is5G) Icons.Default.SignalCellularAlt else Icons.Default.SignalCellularAlt,
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
    onGrantWriteSecureSettings: () -> Unit
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

            Spacer(modifier = Modifier.height(10.dp))

            when {
                isAuthorized -> {
                    Text(
                        text = "✔ Shizuku shell is connected with elevated permissions (UID 2000). Direct 0ms modem frequency switching is active.",
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
                        text = "Shizuku is running on your phone, but NetworkToggle has not yet been authorized. Tap below to prompt the permission dialog.",
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
                        text = "Shizuku service is not running. Start Shizuku on your phone via Wireless Debugging (Android 11+ requires no PC) to enable direct toggling.",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clickable { onRequestPermission() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "START / OPEN SHIZUKU APP",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }
                }
            }
        }
    }
}
