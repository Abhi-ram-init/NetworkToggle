package com.example.networktoggle.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.networktoggle.network.DetectedNetwork
import com.example.networktoggle.network.NetworkMode
import com.example.networktoggle.network.NetworkModeManager
import com.example.networktoggle.network.ToggleResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val uiStyle: com.example.networktoggle.network.AppUiStyle = com.example.networktoggle.network.AppUiStyle.CYBER_NEON,
    val isAutoScrollEnabled: Boolean = true,
    val isAccessibilityEnabled: Boolean = false,
) {
    // Current truthful display mode: driven strictly by live hardware connection
    val currentConnectedMode: NetworkMode
        get() = if (liveNetwork.is5G) NetworkMode.FIVE_G else NetworkMode.FOUR_G
}

class MainScreenViewModel(
    private val networkModeManager: NetworkModeManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MainScreenUiState(
            liveNetwork = networkModeManager.detectCurrentNetwork(),
            hasPermission = networkModeManager.hasPermission(),
            selectedChoice = networkModeManager.getSavedMode(),
            uiStyle = networkModeManager.getSavedUiStyle(),
            isAutoScrollEnabled = networkModeManager.getAutoScrollEnabled(),
            isAccessibilityEnabled = networkModeManager.isAccessibilityEnabled()
        )
    )
    val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()

    private var verificationJob: Job? = null

    init {
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
    }

    fun onAppResumed() {
        refreshPermission()
        refreshAccessibilityStatus()
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

    fun refreshPermission() {
        val granted = networkModeManager.hasPermission()
        _uiState.update { it.copy(hasPermission = granted) }
        if (granted) {
            networkModeManager.refreshNetworkState()
        }
    }

    fun refreshAccessibilityStatus() {
        val enabled = networkModeManager.isAccessibilityEnabled()
        val autoScroll = networkModeManager.getAutoScrollEnabled()
        _uiState.update { it.copy(isAccessibilityEnabled = enabled, isAutoScrollEnabled = autoScroll) }
    }

    fun toggleAutoScroll(enabled: Boolean) {
        networkModeManager.saveAutoScrollEnabled(enabled)
        _uiState.update { it.copy(isAutoScrollEnabled = enabled) }
    }

    fun openAccessibilitySettings() {
        networkModeManager.openAccessibilitySettings()
    }

    fun onPermissionGranted() {
        networkModeManager.refreshNetworkState()
        _uiState.update { it.copy(hasPermission = true) }
    }

    /**
     * User explicitly chooses a mode (5G NR, Auto, or 4G LTE)
     */
    fun selectChoice(mode: NetworkMode) {
        _uiState.update { it.copy(selectedChoice = mode, switchStatus = SwitchStatus.Idle) }
    }

    fun setUiStyle(style: com.example.networktoggle.network.AppUiStyle) {
        networkModeManager.saveUiStyle(style)
        _uiState.update { it.copy(uiStyle = style) }
    }

    /**
     * Executes the switch to the user's selected choice
     */
    fun applySelectedSwitch(context: Context? = null) {
        val target = _uiState.value.selectedChoice
        executeSwitch(target, context)
    }

    /**
     * Quick toggle button
     */
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
                it.copy(switchStatus = SwitchStatus.Switching(targetMode, 10))
            }

            val directResult = networkModeManager.tryDirectSwitch(targetMode)
            if (directResult is ToggleResult.DirectSuccess) {
                delay(1500)
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
            }

            // If direct automated switch is blocked (non-rooted device) and context is provided:
            // Open the Force Menu directly for the user without any dialogs!
            if (context != null) {
                networkModeManager.openRadioInfo(context)
            }

            // Start verification timeout countdown
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

            // Verification window finished: Failed to switch
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
        networkModeManager.openRadioInfo(context)
    }

    fun openMobileSettings(context: Context) {
        networkModeManager.openMobileNetworkSettings(context)
    }

    fun refreshLiveNetwork() {
        networkModeManager.refreshNetworkState()
    }
}
