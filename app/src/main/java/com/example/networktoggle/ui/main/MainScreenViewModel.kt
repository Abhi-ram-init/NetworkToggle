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
    val uiStyle: com.example.networktoggle.network.AppUiStyle = com.example.networktoggle.network.AppUiStyle.MINIMAL_CLEAN,
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
) {
    val currentConnectedMode: NetworkMode
        get() = if (liveNetwork.is5G) NetworkMode.FIVE_G else NetworkMode.FOUR_G
}

class MainScreenViewModel(
    private val networkModeManager: NetworkModeManager,
    private val macroManager: MacroManager? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MainScreenUiState(
            liveNetwork = networkModeManager.detectCurrentNetwork(),
            hasPermission = networkModeManager.hasPermission(),
            selectedChoice = networkModeManager.getSavedMode(),
            uiStyle = networkModeManager.getSavedUiStyle(),
            isDirectToggleGranted = networkModeManager.hasSecureSettingsPermission(),
            isKernelRootDetected = networkModeManager.isKernelRootAvailable(),
            adbGrantCommand = networkModeManager.getAdbCommand(),
            savedMacros = macroManager?.savedMacros?.value ?: emptyList(),
            inspectedProcess = macroManager?.lastInspectedProcess?.value
        )
    )
    val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()

    private var verificationJob: Job? = null

    init {
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

        // 2. Real-time permission observer: runs network toggle commands instant permission is granted
        viewModelScope.launch(Dispatchers.IO) {
            if (networkModeManager.isKernelRootAvailable() && !networkModeManager.hasSecureSettingsPermission()) {
                val autoGranted = networkModeManager.requestKernelPermission(_uiState.value.selectedChoice)
                if (autoGranted) {
                    val target = _uiState.value.selectedChoice
                    _uiState.update {
                        it.copy(
                            isDirectToggleGranted = true,
                            isKernelRootDetected = true,
                            switchStatus = SwitchStatus.Switching(target, 10)
                        )
                    }
                    startVerification(target)
                }
            }

            while (true) {
                delay(1200)
                val wasGranted = _uiState.value.isDirectToggleGranted
                val isNowGranted = networkModeManager.hasSecureSettingsPermission()
                if (!wasGranted && isNowGranted) {
                    val root = networkModeManager.isKernelRootAvailable()
                    val target = _uiState.value.selectedChoice
                    _uiState.update {
                        it.copy(
                            isDirectToggleGranted = true,
                            isKernelRootDetected = root,
                            switchStatus = SwitchStatus.Switching(target, 10)
                        )
                    }
                    networkModeManager.tryDirectSwitch(target)
                    startVerification(target)
                }
            }
        }

        // 3. Observe Macro Manager streams
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
    }

    fun onAppResumed() {
        refreshPermission()
        refreshDirectPermissions()
        val currentStatus = _uiState.value.switchStatus
        if (currentStatus is SwitchStatus.Switching) {
            viewModelScope.launch {
                delay(1000)
                val live = networkModeManager.detectCurrentNetwork()
                val target = currentStatus.targetMode
                val matched = when (target) {
                    NetworkMode.FIVE_G -> live.is5G
                    NetworkMode.FOUR_G -> live.is4G
                    NetworkMode.AUTO -> live.is5G || live.is4G
                }
                verificationJob?.cancel()
                if (matched) {
                    networkModeManager.saveMode(target)
                    _uiState.update {
                        it.copy(
                            liveNetwork = live,
                            switchStatus = SwitchStatus.Success(target)
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            liveNetwork = live,
                            switchStatus = SwitchStatus.Failed(target, live.displayName)
                        )
                    }
                }
            }
        } else {
            networkModeManager.refreshNetworkState()
        }
    }

    fun refreshDirectPermissions() {
        val wasGranted = _uiState.value.isDirectToggleGranted
        val granted = networkModeManager.hasSecureSettingsPermission()
        val isRoot = networkModeManager.isKernelRootAvailable()
        _uiState.update {
            it.copy(
                isDirectToggleGranted = granted,
                isKernelRootDetected = isRoot,
                adbGrantCommand = networkModeManager.getAdbCommand()
            )
        }
        if (!wasGranted && granted) {
            val target = _uiState.value.selectedChoice
            _uiState.update { it.copy(switchStatus = SwitchStatus.Switching(target, 10)) }
            networkModeManager.tryDirectSwitch(target)
            startVerification(target)
        }
    }

    fun requestKernelRootGrant(onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val targetMode = _uiState.value.selectedChoice
            _uiState.update { it.copy(switchStatus = SwitchStatus.Switching(targetMode, 10)) }

            val granted = networkModeManager.requestKernelPermission(targetMode)
            val root = networkModeManager.isKernelRootAvailable()
            _uiState.update {
                it.copy(
                    isDirectToggleGranted = granted,
                    isKernelRootDetected = root
                )
            }
            if (granted) {
                startVerification(targetMode)
            } else {
                _uiState.update { it.copy(switchStatus = SwitchStatus.Idle) }
            }
            withContext(Dispatchers.Main) {
                onResult(granted)
            }
        }
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

    fun setUiStyle(style: com.example.networktoggle.network.AppUiStyle) {
        networkModeManager.saveUiStyle(style)
        _uiState.update { it.copy(uiStyle = style) }
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
        // If recording a macro, automatically capture this action!
        if (macroManager?.isRecording == true) {
            val action = when (targetMode) {
                NetworkMode.FIVE_G -> MacroActionType.SET_5G
                NetworkMode.FOUR_G -> MacroActionType.SET_4G
                NetworkMode.AUTO -> MacroActionType.SET_AUTO
            }
            macroManager.recordAction(action)
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(switchStatus = SwitchStatus.Switching(targetMode, 10))
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
            for (i in 10 downTo 1) {
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
                        switchStatus = SwitchStatus.Failed(targetMode, finalLive.displayName)
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

    fun dismissStatus() {
        _uiState.update { it.copy(switchStatus = SwitchStatus.Idle) }
    }

    fun openRadioInfo(context: Context) {
        if (macroManager?.isRecording == true) {
            macroManager.recordAction(MacroActionType.LAUNCH_RADIO_INFO)
        }
        networkModeManager.openRadioInfo(context)
    }

    fun openMobileSettings(context: Context) {
        networkModeManager.openMobileNetworkSettings(context)
    }

    fun refreshLiveNetwork() {
        networkModeManager.refreshNetworkState()
    }

    // -------------------------------------------------------------
    // USER-DRIVEN MACRO RECORDING & EXECUTION
    // -------------------------------------------------------------

    fun startRecordingMacro(name: String = "") {
        macroManager?.startRecording(name)
    }

    fun recordMacroAction(actionType: MacroActionType, delaySec: Int = 1, shellCmd: String = "") {
        macroManager?.recordAction(actionType, delaySec, shellCmd)
    }

    fun finishRecordingMacro(name: String? = null) {
        macroManager?.finishRecording(name)
    }

    fun cancelRecordingMacro() {
        macroManager?.cancelRecording()
    }

    fun deleteMacro(macroId: String) {
        macroManager?.deleteMacro(macroId)
    }

    fun buildAndSaveGuidedSimToggleMacro(carrierName: String = "Jio", targetModeName: String = "4G"): RecordedMacro? {
        return macroManager?.buildAndSaveGuidedSimToggleMacro(carrierName, targetModeName)
    }

    fun createCustomBlockMacro(name: String, steps: List<MacroStep>): RecordedMacro? {
        return macroManager?.createCustomBlockMacro(name, steps)
    }

    fun runMacro(
        macro: RecordedMacro,
        onPromptAddTile: (() -> Unit)? = null,
        onExitApp: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            macroManager?.executeMacro(
                macro = macro,
                onPromptAddTile = onPromptAddTile,
                onExitApp = onExitApp
            )
        }
    }

    fun clearMacroLogs() {
        macroManager?.clearLogs()
    }

    fun getFormattedMacroLogs(): String {
        return macroManager?.getFormattedLogs() ?: ""
    }

    fun inspectProcess() {
        val info = macroManager?.inspectProcessAndTelephony()
        _uiState.update { it.copy(inspectedProcess = info) }
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
                        "✅ Added to Control Panel / Quick Settings!"
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                        "ℹ️ Tile is already in your Control Panel."
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED ->
                        "⚠️ Request dismissed. You can add it anytime from Edit Tiles."
                    else -> "Tile status updated."
                }
                onResult(msg)
            } ?: onResult("⚠️ Control Panel manager not available.")
        } else {
            onResult("💡 Android 12 and below: Swipe down Control Panel twice, tap Edit (✏️), and drag the tile into your active shade.")
        }
    }
}
