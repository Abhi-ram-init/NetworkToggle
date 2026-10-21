package com.example.networktoggle.ui.main

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.networktoggle.R
import com.example.networktoggle.macro.MacroActionType
import com.example.networktoggle.macro.MacroExecutionState
import com.example.networktoggle.macro.MacroLogEntry
import com.example.networktoggle.macro.MacroManager
import com.example.networktoggle.macro.MacroStep
import com.example.networktoggle.macro.ProcessTelephonyInfo
import com.example.networktoggle.macro.RecordedMacro
import com.example.networktoggle.network.DetectedNetwork
import com.example.networktoggle.network.NetworkMode
import com.example.networktoggle.network.NetworkModeManager
import com.example.networktoggle.network.ToggleResult
import com.example.networktoggle.terminal.AdbTerminalManager
import com.example.networktoggle.terminal.AdbTerminalState
import com.example.networktoggle.tile.NetworkMacroTileService
import com.example.networktoggle.tile.NetworkToggleTileService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class SwitchStatus {
    object Idle : SwitchStatus()
    data class Switching(val targetMode: NetworkMode, val remainingSeconds: Int) : SwitchStatus()
    data class Success(val mode: NetworkMode) : SwitchStatus()
    data class Failed(val targetMode: NetworkMode, val currentNetwork: String) : SwitchStatus()
}

data class MainScreenUiState(
    val liveNetwork: DetectedNetwork = DetectedNetwork(),
    val hasPermission: Boolean = false,
    val selectedChoice: NetworkMode = NetworkMode.FIVE_G,
    val switchStatus: SwitchStatus = SwitchStatus.Idle,
    val isDirectToggleGranted: Boolean = false,
    val isKernelRootDetected: Boolean = false,
    val adbGrantCommand: String = "",
    val savedMacros: List<RecordedMacro> = emptyList(),
    val isRecordingMacro: Boolean = false,
    val recordingMacroName: String = "",
    val currentlyRecordedSteps: List<MacroStep> = emptyList(),
    val macroState: MacroExecutionState = MacroExecutionState.Idle,
    val macroLogs: List<MacroLogEntry> = emptyList(),
    val inspectedProcess: ProcessTelephonyInfo? = null,
    val terminalState: AdbTerminalState = AdbTerminalState(),
    val popupMessage: String? = null,
    val bridgeState: com.example.networktoggle.bridge.BridgeConnectionState = com.example.networktoggle.bridge.BridgeConnectionState.Disconnected,
) {
    val currentConnectedMode: NetworkMode
        get() = if (liveNetwork.is5G) NetworkMode.FIVE_G else NetworkMode.FOUR_G
}

class MainScreenViewModel(
    private val networkModeManager: NetworkModeManager,
    private val macroManager: MacroManager? = null,
    val adbTerminalManager: AdbTerminalManager? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MainScreenUiState(
            liveNetwork = networkModeManager.detectCurrentNetwork(),
            hasPermission = networkModeManager.hasPermission(),
            selectedChoice = networkModeManager.getSavedMode(),
            isDirectToggleGranted = networkModeManager.hasSecureSettingsPermission(),
            isKernelRootDetected = networkModeManager.isKernelRootAvailable(),
            adbGrantCommand = networkModeManager.getAdbCommand(),
            savedMacros = macroManager?.savedMacros?.value ?: emptyList(),
            inspectedProcess = macroManager?.lastInspectedProcess?.value,
            terminalState = adbTerminalManager?.state?.value ?: AdbTerminalState()
        )
    )
    val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()

    private var verificationJob: Job? = null

    init {
        // Link ADB terminal manager to network and macro managers
        if (adbTerminalManager != null) {
            networkModeManager.adbTerminalManager = adbTerminalManager
            macroManager?.adbTerminalManager = adbTerminalManager
        }

        // 1. Observe live modem network changes
        viewModelScope.launch {
            networkModeManager.liveNetwork.collect { liveNet ->
                _uiState.update { currentState ->
                    val status = currentState.switchStatus
                    if (status is SwitchStatus.Switching) {
                        val matched = when (status.targetMode) {
                            NetworkMode.FIVE_G -> liveNet.is5G
                            NetworkMode.FOUR_G -> liveNet.is4G
                            NetworkMode.AUTO -> liveNet.is5G || liveNet.is4G
                        }
                        if (matched) {
                            verificationJob?.cancel()
                            networkModeManager.saveMode(status.targetMode)
                            currentState.copy(
                                liveNetwork = liveNet,
                                switchStatus = SwitchStatus.Success(status.targetMode)
                            )
                        } else {
                            currentState.copy(liveNetwork = liveNet)
                        }
                    } else {
                        currentState.copy(liveNetwork = liveNet)
                    }
                }
            }
        }

        // 2. Observe Macro Manager streams
        macroManager?.let { mm ->
            viewModelScope.launch {
                mm.savedMacros.collect { macros ->
                    _uiState.update { it.copy(savedMacros = macros) }
                }
            }
            viewModelScope.launch {
                mm.logs.collect { logs ->
                    _uiState.update { it.copy(macroLogs = logs) }
                }
            }
            viewModelScope.launch {
                mm.macroState.collect { state ->
                    val isRec = state is MacroExecutionState.Recording
                    val recName = if (state is MacroExecutionState.Recording) state.macroName else ""
                    val steps = if (state is MacroExecutionState.Recording) state.recordedSteps else emptyList()
                    _uiState.update {
                        it.copy(
                            macroState = state,
                            isRecordingMacro = isRec,
                            recordingMacroName = recName,
                            currentlyRecordedSteps = steps
                        )
                    }
                }
            }
            viewModelScope.launch {
                mm.lastInspectedProcess.collect { proc ->
                    _uiState.update { it.copy(inspectedProcess = proc) }
                }
            }
        }

        // 3. Observe ADB Terminal Manager state
        viewModelScope.launch {
            com.example.networktoggle.bridge.PrivilegedBridgeManager.state.collect { bState ->
                _uiState.update { it.copy(bridgeState = bState) }
            }
        }
        adbTerminalManager?.let { atm ->
            viewModelScope.launch {
                atm.state.collect { termState ->
                    _uiState.update {
                        it.copy(
                            terminalState = termState,
                            isDirectToggleGranted = networkModeManager.hasSecureSettingsPermission() || termState.isAuthorized,
                            isKernelRootDetected = termState.isRootAvailable
                        )
                    }
                }
            }
        }

        // 4. Background permission poller
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(2000)
                val wasGranted = _uiState.value.isDirectToggleGranted
                val isNowGranted = networkModeManager.hasSecureSettingsPermission() ||
                        (adbTerminalManager?.state?.value?.isAuthorized == true)
                if (!wasGranted && isNowGranted) {
                    _uiState.update {
                        it.copy(
                            isDirectToggleGranted = true,
                            isKernelRootDetected = networkModeManager.isKernelRootAvailable()
                        )
                    }
                }
            }
        }
    }

    fun onAppResumed() {
        refreshPermission()
        adbTerminalManager?.refreshState()
        networkModeManager.refreshNetworkState()
    }

    fun refreshPermission() {
        val granted = networkModeManager.hasPermission()
        _uiState.update { it.copy(hasPermission = granted) }
        if (granted) {
            networkModeManager.refreshNetworkState()
        }
    }

    fun onPermissionGranted() {
        networkModeManager.refreshNetworkState()
        _uiState.update { it.copy(hasPermission = true) }
    }

    fun selectChoice(mode: NetworkMode) {
        _uiState.update { it.copy(selectedChoice = mode, switchStatus = SwitchStatus.Idle) }
    }

    fun applySelectedSwitch(context: Context? = null) {
        val target = _uiState.value.selectedChoice
        executeSwitch(target, context)
    }

    fun toggleQuick(context: Context? = null) {
        val current = _uiState.value.currentConnectedMode
        val next = if (current == NetworkMode.FIVE_G) NetworkMode.FOUR_G else NetworkMode.FIVE_G
        _uiState.update { it.copy(selectedChoice = next) }
        executeSwitch(next, context)
    }

    fun setMode(mode: NetworkMode) {
        _uiState.update { it.copy(selectedChoice = mode) }
        executeSwitch(mode, null)
    }

    private fun executeSwitch(targetMode: NetworkMode, context: Context?) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(switchStatus = SwitchStatus.Switching(targetMode, 8))
            }

            val directResult = networkModeManager.tryDirectSwitch(targetMode)
            if (directResult is ToggleResult.DirectSuccess) {
                startVerification(targetMode)
                return@launch
            }

            if (context != null) {
                networkModeManager.openRadioInfo(context)
            }

            startVerification(targetMode)
        }
    }

    private fun startVerification(targetMode: NetworkMode) {
        verificationJob?.cancel()
        verificationJob = viewModelScope.launch {
            for (i in 8 downTo 1) {
                delay(1000)
                val live = networkModeManager.detectCurrentNetwork()
                val matched = when (targetMode) {
                    NetworkMode.FIVE_G -> live.is5G
                    NetworkMode.FOUR_G -> live.is4G
                    NetworkMode.AUTO -> live.is5G || live.is4G
                }
                if (matched) {
                    networkModeManager.saveMode(targetMode)
                    _uiState.update {
                        it.copy(
                            liveNetwork = live,
                            switchStatus = SwitchStatus.Success(targetMode)
                        )
                    }
                    return@launch
                }
                _uiState.update {
                    if (it.switchStatus is SwitchStatus.Switching) {
                        it.copy(switchStatus = SwitchStatus.Switching(targetMode, i - 1))
                    } else it
                }
            }

            val finalLive = networkModeManager.detectCurrentNetwork()
            val matched = when (targetMode) {
                NetworkMode.FIVE_G -> finalLive.is5G
                NetworkMode.FOUR_G -> finalLive.is4G
                NetworkMode.AUTO -> finalLive.is5G || finalLive.is4G
            }
            if (!matched) {
                _uiState.update {
                    it.copy(
                        liveNetwork = finalLive,
                        switchStatus = SwitchStatus.Failed(targetMode, finalLive.displayName),
                        popupMessage = "âš ï¸ Network toggle could not switch to ${targetMode.label()}. Modem remained on ${finalLive.displayName}. Tap Force Menu (*#*#4636#*#*) if needed."
                    )
                }
            } else {
                networkModeManager.saveMode(targetMode)
                _uiState.update {
                    it.copy(
                        liveNetwork = finalLive,
                        switchStatus = SwitchStatus.Success(targetMode)
                    )
                }
            }
        }
    }

    fun showPopupMessage(msg: String) {
        _uiState.update { it.copy(popupMessage = msg) }
    }

    fun startPrivilegedBridgeViaRoot(context: Context) {
        viewModelScope.launch {
            val started = com.example.networktoggle.bridge.PrivilegedBridgeManager.startViaRoot(context)
            if (started) {
                showPopupMessage("⚡ Launched Privileged Backend Service via Root!")
            } else {
                showPopupMessage("⚠️ Failed to start via Root. Verify SU availability.")
            }
        }
    }

    fun startPrivilegedBridgeViaAdb(context: Context) {
        val terminal = adbTerminalManager
        if (terminal != null) {
            val dispatched = com.example.networktoggle.bridge.PrivilegedBridgeManager.startViaAdbTerminal(context, terminal)
            if (dispatched) {
                showPopupMessage("💻 Launch command dispatched to ADB Terminal.")
            }
        } else {
            showPopupMessage("⚠️ ADB Terminal not ready.")
        }
    }

    fun stopPrivilegedBridge() {
        val stopped = com.example.networktoggle.bridge.PrivilegedBridgeManager.stopService()
        if (stopped) {
            showPopupMessage("🛑 Privileged Backend Service stopped.")
        }
    }

    fun testPrivilegedBridgePing() {
        viewModelScope.launch {
            val ok = com.example.networktoggle.bridge.PrivilegedBridgeManager.ping()
            if (ok) {
                val uid = com.example.networktoggle.bridge.PrivilegedBridgeManager.connectedUid
                val pid = com.example.networktoggle.bridge.PrivilegedBridgeManager.connectedPid
                val idStr = if (uid == 0) "Root" else "ADB Shell ()"
                showPopupMessage("✔ Bridge Ping OK! Connected to  (PID: 3776)")
            } else {
                showPopupMessage("⚠️ Privileged bridge is not connected.")
            }
        }
    }

    fun getPrivilegedBridgeLaunchCommand(context: Context): String {
        return com.example.networktoggle.bridge.PrivilegedBridgeManager.getLaunchCommand(context)
    }

    fun getPrivilegedBridgeAdbPcCommand(context: Context): String {
        return com.example.networktoggle.bridge.PrivilegedBridgeManager.getAdbPcCommand(context)
    }

    fun dismissPopupMessage() {
        _uiState.update { it.copy(popupMessage = null) }
    }

    fun dismissStatus() {
        _uiState.update { it.copy(switchStatus = SwitchStatus.Idle) }
    }

    fun openRadioInfo(context: Context) {
        networkModeManager.openRadioInfo(context)
    }

    fun openMobileSettings(context: Context) {
        networkModeManager.openMobileNetworkSettings(context)
    }

    fun refreshLiveNetwork() {
        networkModeManager.refreshNetworkState()
    }

    // -------------------------------------------------------------
    // MACRO AUTOMATIONS
    // -------------------------------------------------------------

    fun runMacro(
        macro: RecordedMacro,
        onPromptAddTile: (() -> Unit)? = null,
        onExitApp: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            macroManager?.executeMacro(
                macro = macro,
                onPromptAddTile = onPromptAddTile,
                onExitApp = onExitApp,
                onToggleFailedMessage = { msg ->
                    showPopupMessage(msg)
                }
            )
        }
    }

    fun runModeMacro(targetMode: NetworkMode) {
        val macro = _uiState.value.savedMacros.firstOrNull {
            if (targetMode == NetworkMode.FIVE_G) it.name.contains("5G", ignoreCase = true)
            else it.name.contains("4G", ignoreCase = true)
        } ?: RecordedMacro(
            name = "Switch to ${targetMode.label()}",
            steps = listOf(
                MacroStep(
                    actionType = if (targetMode == NetworkMode.FIVE_G) MacroActionType.SET_5G else MacroActionType.SET_4G,
                    label = "Enforce ${targetMode.label()}"
                )
            )
        )
        runMacro(macro)
    }

    fun clearMacroLogs() {
        macroManager?.clearLogs()
    }

    // -------------------------------------------------------------
    // ON-DEVICE ADB TERMINAL
    // -------------------------------------------------------------

    fun requestAdbPermission() {
        adbTerminalManager?.requestPermission()
    }

    fun executeAdbCommand(cmd: String) {
        adbTerminalManager?.executeCommand(cmd)
    }

    fun grantAdbSecureSettings() {
        adbTerminalManager?.grantWriteSecureSettings()
    }

    fun clearAdbLogs() {
        adbTerminalManager?.clearLogs()
    }

    fun setNetworkModeViaAdb(mode: NetworkMode) {
        adbTerminalManager?.setNetworkModeViaAdb(mode)
    }

    fun requestAddTileToControlPanel(
        isMacroTile: Boolean,
        context: Context,
        onResult: (String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val sbm = context.getSystemService(StatusBarManager::class.java)
            val component = if (isMacroTile) {
                ComponentName(context, NetworkMacroTileService::class.java)
            } else {
                ComponentName(context, NetworkToggleTileService::class.java)
            }
            val label = if (isMacroTile) "Network Macro" else context.getString(R.string.tile_label)
            val icon = Icon.createWithResource(
                context,
                if (isMacroTile) R.drawable.ic_macro_tile else R.drawable.ic_network_5g
            )
            sbm?.requestAddTileService(
                component,
                label,
                icon,
                context.mainExecutor
            ) { result ->
                val msg = when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                        "âœ… Added to Control Panel / Quick Settings!"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                        "â„¹ï¸ Tile is already in your Control Panel."
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED ->
                        "âš ï¸ Request dismissed. You can add it anytime from Edit Tiles."
                    else -> "Tile status updated."
                }
                onResult(msg)
            } ?: onResult("âš ï¸ Control Panel manager not available.")
        } else {
            onResult("ðŸ’¡ Android 12 and below: Swipe down notification shade twice, tap Edit (âœï¸), and drag the tile into your active shade.")
        }
    }
}
