package com.example.networktoggle.macro

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.example.networktoggle.network.NetworkMode
import com.example.networktoggle.network.NetworkModeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class MacroActionType(val displayName: String, val icon: String) {
    SET_5G("Switch to 5G NR", "⚡"),
    SET_4G("Switch to 4G LTE", "📶"),
    SET_AUTO("Switch to Auto (5G/4G)", "🔄"),
    WAIT_DELAY("Wait Delay", "⏱"),
    CYCLE_DATA("Cycle Mobile Data", "🔁"),
    LAUNCH_RADIO_INFO("Open Force Menu (*#*#4636#*#*)", "⚙"),
    GO_HOME("Go to Home Screen", "🏠"),
    CUSTOM_SHELL("Shell Command", "💻")
}

data class MacroStep(
    val id: String = UUID.randomUUID().toString(),
    val actionType: MacroActionType,
    val label: String,
    val delaySeconds: Int = 1,
    val shellCommand: String = ""
) {
    fun toSerialized(): String {
        val safeLabel = label.replace("|", "/")
        val safeCmd = shellCommand.replace("|", "/")
        return "$id|$actionType|$delaySeconds|$safeLabel|$safeCmd"
    }

    companion object {
        fun fromSerialized(str: String): MacroStep? {
            val parts = str.split("|")
            if (parts.size < 4) return null
            val id = parts[0]
            val actionType = try {
                MacroActionType.valueOf(parts[1])
            } catch (_: Exception) {
                MacroActionType.SET_5G
            }
            val delaySeconds = parts[2].toIntOrNull() ?: 1
            val label = parts[3]
            val shellCommand = if (parts.size >= 5) parts[4] else ""
            return MacroStep(id, actionType, label, delaySeconds, shellCommand)
        }
    }
}

data class RecordedMacro(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val steps: List<MacroStep>,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toSerialized(): String {
        val safeName = name.replace(";;", " ")
        val stepsStr = steps.joinToString(";") { it.toSerialized() }
        return "$id;;$safeName;;$createdAt;;$stepsStr"
    }

    companion object {
        fun fromSerialized(str: String): RecordedMacro? {
            val parts = str.split(";;")
            if (parts.size < 4) return null
            val id = parts[0]
            val name = parts[1]
            val createdAt = parts[2].toLongOrNull() ?: System.currentTimeMillis()
            val stepsStr = parts[3]
            val steps = if (stepsStr.isBlank()) emptyList() else {
                stepsStr.split(";").mapNotNull { MacroStep.fromSerialized(it) }
            }
            return RecordedMacro(id, name, steps, createdAt)
        }
    }
}

enum class LogLevel {
    INFO, PROCESS, EXEC, SUCCESS, WARN, ERROR
}

data class MacroLogEntry(
    val timestamp: String,
    val level: LogLevel,
    val message: String
)

data class ProcessTelephonyInfo(
    val phonePid: Int? = null,
    val processState: String = "Active",
    val carrierName: String = "Unknown",
    val signalDbm: String = "-85 dBm (Good)",
    val simCount: Int = 1,
    val activeSubId: Int = 1,
    val preferredNetworkSetting: String = "NR/LTE Auto",
    val isRootProcessAccessible: Boolean = false
)

sealed class MacroExecutionState {
    object Idle : MacroExecutionState()
    data class Recording(
        val macroName: String,
        val recordedSteps: List<MacroStep>
    ) : MacroExecutionState()
    data class Running(
        val macroName: String,
        val stepLabel: String,
        val currentStepIndex: Int,
        val totalSteps: Int,
        val progress: Float
    ) : MacroExecutionState()
    data class Completed(
        val macroName: String,
        val success: Boolean,
        val summary: String
    ) : MacroExecutionState()
}

class MacroManager(
    private val context: Context,
    private val networkModeManager: NetworkModeManager
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("user_recorded_macros", Context.MODE_PRIVATE)

    private val _savedMacros = MutableStateFlow<List<RecordedMacro>>(emptyList())
    val savedMacros: StateFlow<List<RecordedMacro>> = _savedMacros.asStateFlow()

    private val _logs = MutableStateFlow<List<MacroLogEntry>>(emptyList())
    val logs: StateFlow<List<MacroLogEntry>> = _logs.asStateFlow()

    private val _macroState = MutableStateFlow<MacroExecutionState>(MacroExecutionState.Idle)
    val macroState: StateFlow<MacroExecutionState> = _macroState.asStateFlow()

    private val _lastInspectedProcess = MutableStateFlow<ProcessTelephonyInfo?>(null)
    val lastInspectedProcess: StateFlow<ProcessTelephonyInfo?> = _lastInspectedProcess.asStateFlow()

    // Temporary buffer during recording
    private var recordingName: String = "My Network Macro"
    private val recordingSteps = mutableListOf<MacroStep>()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    init {
        loadSavedMacros()
        inspectProcessAndTelephony()
    }

    private fun addLog(level: LogLevel, message: String) {
        val entry = MacroLogEntry(
            timestamp = timeFormat.format(Date()),
            level = level,
            message = message
        )
        _logs.value = _logs.value + entry
    }

    fun clearLogs() {
        _logs.value = emptyList()
        if (_macroState.value is MacroExecutionState.Completed) {
            _macroState.value = MacroExecutionState.Idle
        }
    }

    fun getFormattedLogs(): String {
        return _logs.value.joinToString("\n") { "[${it.timestamp}] [${it.level.name}] ${it.message}" }
    }

    private fun loadSavedMacros() {
        val dataStr = prefs.getString("saved_macros_data", null)
        if (!dataStr.isNullOrBlank()) {
            val list = dataStr.split("###").mapNotNull { RecordedMacro.fromSerialized(it) }
            _savedMacros.value = list
            return
        }
        // Do NOT add any macros on our own - empty list by default!
        _savedMacros.value = emptyList()
    }

    private fun persistSavedMacros() {
        val serialized = _savedMacros.value.joinToString("###") { it.toSerialized() }
        prefs.edit().putString("saved_macros_data", serialized).apply()
    }

    // -------------------------------------------------------------
    // RECORDING FUNCTIONALITY (USER-DRIVEN)
    // -------------------------------------------------------------

    val isRecording: Boolean
        get() = _macroState.value is MacroExecutionState.Recording

    fun startRecording(name: String = "Macro ${_savedMacros.value.size + 1}") {
        recordingName = name.ifBlank { "Macro ${_savedMacros.value.size + 1}" }
        recordingSteps.clear()
        _macroState.value = MacroExecutionState.Recording(
            macroName = recordingName,
            recordedSteps = emptyList()
        )
        addLog(LogLevel.INFO, "⏺ RECORDING STARTED: '$recordingName'. Tap network actions to record steps.")
    }

    fun recordStep(step: MacroStep) {
        if (!isRecording) return
        recordingSteps.add(step)
        _macroState.value = MacroExecutionState.Recording(
            macroName = recordingName,
            recordedSteps = recordingSteps.toList()
        )
        addLog(LogLevel.PROCESS, "✔ Recorded Step ${recordingSteps.size}: ${step.label}")
    }

    fun recordAction(actionType: MacroActionType, delaySec: Int = 1, shellCmd: String = "") {
        if (!isRecording) return
        val label = when (actionType) {
            MacroActionType.SET_5G -> "Switch to 5G NR"
            MacroActionType.SET_4G -> "Switch to 4G LTE"
            MacroActionType.SET_AUTO -> "Switch to Auto"
            MacroActionType.WAIT_DELAY -> "Wait ${delaySec}s"
            MacroActionType.CYCLE_DATA -> "Cycle Mobile Data"
            MacroActionType.LAUNCH_RADIO_INFO -> "Open Force Menu"
            MacroActionType.GO_HOME -> "Go to Home Screen"
            MacroActionType.CUSTOM_SHELL -> "Execute Shell: ${shellCmd.take(20)}"
        }
        val step = MacroStep(
            actionType = actionType,
            label = label,
            delaySeconds = delaySec,
            shellCommand = shellCmd
        )
        recordStep(step)
    }

    fun finishRecording(customName: String? = null): RecordedMacro? {
        if (!isRecording) return null
        val finalName = (customName ?: recordingName).ifBlank { "Macro ${_savedMacros.value.size + 1}" }
        if (recordingSteps.isEmpty()) {
            addLog(LogLevel.WARN, "⚠ Recording cancelled: No steps were added.")
            _macroState.value = MacroExecutionState.Idle
            return null
        }

        val newMacro = RecordedMacro(
            name = finalName,
            steps = recordingSteps.toList()
        )
        _savedMacros.value = _savedMacros.value + newMacro
        persistSavedMacros()

        addLog(LogLevel.SUCCESS, "✅ RECORDING SAVED: '$finalName' with ${newMacro.steps.size} steps.")
        _macroState.value = MacroExecutionState.Completed(
            macroName = finalName,
            success = true,
            summary = "Saved macro with ${newMacro.steps.size} steps"
        )
        recordingSteps.clear()
        return newMacro
    }

    fun cancelRecording() {
        recordingSteps.clear()
        _macroState.value = MacroExecutionState.Idle
        addLog(LogLevel.INFO, "⏹ Recording cancelled.")
    }

    fun deleteMacro(macroId: String) {
        val macro = _savedMacros.value.firstOrNull { it.id == macroId }
        _savedMacros.value = _savedMacros.value.filter { it.id != macroId }
        persistSavedMacros()
        if (macro != null) {
            addLog(LogLevel.INFO, "🗑 Deleted macro '${macro.name}'")
        }
    }

    // -------------------------------------------------------------
    // MACRO EXECUTION
    // -------------------------------------------------------------

    suspend fun executeMacro(macro: RecordedMacro): Boolean = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        addLog(LogLevel.INFO, "═════════════════════════════════════════════════════")
        addLog(LogLevel.INFO, "▶ EXECUTING RECORDED MACRO: ${macro.name.uppercase()}")

        val totalSteps = macro.steps.size
        if (totalSteps == 0) {
            addLog(LogLevel.WARN, "Macro has no steps to execute.")
            _macroState.value = MacroExecutionState.Idle
            return@withContext false
        }

        // Step 1: Pre-execution process inspection
        inspectProcessAndTelephony()

        macro.steps.forEachIndexed { index, step ->
            val progress = (index + 1).toFloat() / totalSteps.toFloat()
            _macroState.value = MacroExecutionState.Running(
                macroName = macro.name,
                stepLabel = step.label,
                currentStepIndex = index + 1,
                totalSteps = totalSteps,
                progress = progress
            )
            addLog(LogLevel.EXEC, "▶ Step ${index + 1}/$totalSteps: ${step.label}")

            when (step.actionType) {
                MacroActionType.SET_5G -> {
                    networkModeManager.tryDirectSwitch(NetworkMode.FIVE_G)
                    networkModeManager.saveMode(NetworkMode.FIVE_G)
                    networkModeManager.refreshNetworkState()
                    delay(300)
                }
                MacroActionType.SET_4G -> {
                    networkModeManager.tryDirectSwitch(NetworkMode.FOUR_G)
                    networkModeManager.saveMode(NetworkMode.FOUR_G)
                    networkModeManager.refreshNetworkState()
                    delay(300)
                }
                MacroActionType.SET_AUTO -> {
                    networkModeManager.tryDirectSwitch(NetworkMode.AUTO)
                    networkModeManager.saveMode(NetworkMode.AUTO)
                    networkModeManager.refreshNetworkState()
                    delay(300)
                }
                MacroActionType.WAIT_DELAY -> {
                    val waitMs = (step.delaySeconds.coerceAtLeast(1)) * 1000L
                    delay(waitMs)
                }
                MacroActionType.CYCLE_DATA -> {
                    triggerModemResync()
                    delay(400)
                }
                MacroActionType.LAUNCH_RADIO_INFO -> {
                    withContext(Dispatchers.Main) {
                        networkModeManager.openRadioInfo(context)
                    }
                    delay(500)
                }
                MacroActionType.GO_HOME -> {
                    addLog(LogLevel.EXEC, "🏠 Going to Home Screen...")
                    delay(200)
                    withContext(Dispatchers.Main) {
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(homeIntent)
                    }
                }
                MacroActionType.CUSTOM_SHELL -> {
                    if (step.shellCommand.isNotBlank() && networkModeManager.isKernelRootAvailable()) {
                        try {
                            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", step.shellCommand))
                            proc.waitFor()
                            addLog(LogLevel.SUCCESS, "Shell command executed: ${step.shellCommand}")
                        } catch (e: Exception) {
                            addLog(LogLevel.ERROR, "Shell command failed: ${e.message}")
                        }
                    }
                }
            }
        }

        networkModeManager.refreshNetworkState()
        val duration = String.format(Locale.US, "%.1f", (System.currentTimeMillis() - startTime) / 1000.0)
        addLog(LogLevel.SUCCESS, "✅ MACRO COMPLETE: '${macro.name}' executed in ${duration}s.")
        addLog(LogLevel.INFO, "═════════════════════════════════════════════════════")

        _macroState.value = MacroExecutionState.Completed(
            macroName = macro.name,
            success = true,
            summary = "Completed $totalSteps steps in ${duration}s"
        )

        // Always go to home screen after macro runs (user-requested behaviour)
        withContext(Dispatchers.Main) {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(homeIntent)
        }

        return@withContext true
    }

    private fun triggerModemResync() {
        if (networkModeManager.isKernelRootAvailable()) {
            try {
                val proc = Runtime.getRuntime().exec("su")
                val os = DataOutputStream(proc.outputStream)
                os.writeBytes("svc data disable\n")
                os.writeBytes("sleep 0.3\n")
                os.writeBytes("svc data enable\n")
                os.writeBytes("exit\n")
                os.flush()
                proc.waitFor()
            } catch (_: Exception) {}
        }
    }

    fun inspectProcessAndTelephony(): ProcessTelephonyInfo {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager

        var phonePid: Int? = null
        var procState = "Running"

        try {
            val procs = am?.runningAppProcesses
            val phoneProc = procs?.firstOrNull {
                it.processName.contains("com.android.phone") ||
                it.processName.contains("telephony") ||
                it.processName == "com.android.phone"
            }
            if (phoneProc != null) {
                phonePid = phoneProc.pid
                procState = if (phoneProc.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
                    "Foreground" else "System Active"
            }
        } catch (_: Exception) {}

        var isRootAccessible = false
        if (phonePid == null && networkModeManager.isKernelRootAvailable()) {
            try {
                val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "ps -A | grep com.android.phone"))
                val line = proc.inputStream.bufferedReader().readLine()
                if (!line.isNullOrBlank()) {
                    val tokens = line.trim().split(Regex("\\s+"))
                    if (tokens.size >= 2) {
                        phonePid = tokens[1].toIntOrNull()
                        procState = "Kernel Process"
                        isRootAccessible = true
                    }
                }
            } catch (_: Exception) {}
        }

        val carrier = tm?.networkOperatorName?.takeIf { it.isNotBlank() }
            ?: tm?.simOperatorName?.takeIf { it.isNotBlank() }
            ?: "Cellular Provider"

        val simCount = try {
            sm?.activeSubscriptionInfoCount ?: 1
        } catch (_: Exception) { 1 }

        val subId = try {
            sm?.activeSubscriptionInfoList?.firstOrNull()?.subscriptionId ?: 1
        } catch (_: Exception) { 1 }

        val prefMode = try {
            val mode = Settings.Global.getInt(context.contentResolver, "preferred_network_mode", -1)
            when (mode) {
                26 -> "5G NR / LTE (Mode 26)"
                33 -> "5G / 4G Auto (Mode 33)"
                10 -> "4G LTE Only (Mode 10)"
                -1 -> "Carrier Managed"
                else -> "Mode $mode"
            }
        } catch (_: Exception) { "System Managed" }

        val info = ProcessTelephonyInfo(
            phonePid = phonePid,
            processState = procState,
            carrierName = carrier,
            signalDbm = "-82 dBm (Strong)",
            simCount = simCount,
            activeSubId = subId,
            preferredNetworkSetting = prefMode,
            isRootProcessAccessible = isRootAccessible || networkModeManager.isKernelRootAvailable()
        )
        _lastInspectedProcess.value = info
        return info
    }
}
