package com.example.networktoggle.terminal

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.example.networktoggle.network.NetworkMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AdbEngine {
    SHIZUKU,
    ROOT,
    LOCAL_SHELL,
    NONE
}

enum class TerminalLineType {
    COMMAND,
    OUTPUT,
    ERROR,
    SYSTEM
}

data class TerminalLine(
    val id: Long = System.nanoTime(),
    val text: String,
    val type: TerminalLineType = TerminalLineType.OUTPUT,
    val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
)

data class AdbTerminalState(
    val engine: AdbEngine = AdbEngine.NONE,
    val isAuthorized: Boolean = false,
    val isShizukuRunning: Boolean = false,
    val isRootAvailable: Boolean = false,
    val isExecuting: Boolean = false,
    val logs: List<TerminalLine> = emptyList(),
    val statusMessage: String = "Initializing ADB Terminal…"
)

class AdbTerminalManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        const val SHIZUKU_REQUEST_CODE = 4101
        private const val PREFERRED_5G_NR = 1576960
        private const val PREFERRED_4G_LTE = 1048700
        private const val GLOBAL_MODE_5G = 26
        private const val GLOBAL_MODE_AUTO = 33
        private const val GLOBAL_MODE_4G = 10
    }

    private val _state = MutableStateFlow(AdbTerminalState())
    val state: StateFlow<AdbTerminalState> = _state.asStateFlow()

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_REQUEST_CODE) {
            val isGranted = grantResult == PackageManager.PERMISSION_GRANTED
            appendLine(
                if (isGranted) "✔ Shizuku permission granted! Terminal authorized."
                else "ℹ️ Shizuku permission request was dismissed.",
                TerminalLineType.SYSTEM
            )
            refreshState()
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        appendLine("⚡ Shizuku service connected on device.", TerminalLineType.SYSTEM)
        refreshState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        appendLine("ℹ️ Shizuku service disconnected.", TerminalLineType.SYSTEM)
        refreshState()
    }

    init {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionListener)
        } catch (_: Exception) {}

        appendLine("ADB Terminal Initialized. Type 'help' for available commands.", TerminalLineType.SYSTEM)
        refreshState()
    }

    fun destroy() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(permissionListener)
        } catch (_: Exception) {}
    }

    fun refreshState() {
        scope.launch(Dispatchers.IO) {
            val shizukuRunning = try { Shizuku.pingBinder() } catch (_: Exception) { false }
            val shizukuGranted = shizukuRunning && try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (_: Exception) { false }

            val rootAvailable = checkRootAvailable()

            val engine = when {
                shizukuGranted -> AdbEngine.SHIZUKU
                rootAvailable -> AdbEngine.ROOT
                else -> AdbEngine.LOCAL_SHELL
            }

            val authorized = shizukuGranted || rootAvailable
            val statusMsg = when {
                shizukuGranted -> "🟢 Authorized via Shizuku (On-Device ADB Active)"
                rootAvailable -> "🟢 Authorized via Kernel Root"
                shizukuRunning -> "🟡 Shizuku Active • Tap 'Grant' to authorize"
                else -> "⚪ Ready (Standard & Built-in commands supported)"
            }

            _state.update {
                it.copy(
                    engine = engine,
                    isAuthorized = authorized,
                    isShizukuRunning = shizukuRunning,
                    isRootAvailable = rootAvailable,
                    statusMessage = statusMsg
                )
            }
        }
    }

    fun isShizukuInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun launchShizukuApp() {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        } catch (_: Exception) {}
    }

    private fun checkRootAvailable(): Boolean {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            val line = reader.readLine() ?: ""
            proc.waitFor() == 0 && (line.contains("uid=0") || line.contains("root"))
        } catch (_: Exception) {
            false
        }
    }

    fun requestPermission() {
        scope.launch(Dispatchers.IO) {
            val shizukuRunning = try { Shizuku.pingBinder() } catch (_: Exception) { false }
            if (shizukuRunning) {
                try {
                    if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                        appendLine("✔ Permission already granted via Shizuku.", TerminalLineType.SYSTEM)
                        refreshState()
                    } else {
                        appendLine("Prompting Shizuku authorization dialog on phone…", TerminalLineType.SYSTEM)
                        Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
                    }
                } catch (e: Exception) {
                    appendLine("ℹ️ Shizuku prompt notice: ${e.message}", TerminalLineType.SYSTEM)
                }
            } else if (checkRootAvailable()) {
                appendLine("Checking Root authorization on device…", TerminalLineType.SYSTEM)
                try {
                    val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                    proc.waitFor()
                    appendLine("✔ Root permission confirmed.", TerminalLineType.SYSTEM)
                    refreshState()
                } catch (e: Exception) {
                    appendLine("ℹ️ Root check notice: ${e.message}", TerminalLineType.SYSTEM)
                }
            } else {
                if (isShizukuInstalled()) {
                    appendLine("ℹ️ Shizuku app is installed on phone, but service is not running.", TerminalLineType.SYSTEM)
                    appendLine("👉 Open Shizuku, tap 'Start' via Wireless Debugging, then return here.", TerminalLineType.SYSTEM)
                    withContext(Dispatchers.Main) {
                        launchShizukuApp()
                    }
                } else {
                    appendLine("ℹ️ Shizuku is not running on this phone.", TerminalLineType.SYSTEM)
                    appendLine("💡 Option 1: Install Shizuku & start it via Wireless Debugging (No PC needed).", TerminalLineType.SYSTEM)
                    appendLine("💡 Option 2: Run once via PC: adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS", TerminalLineType.SYSTEM)
                    appendLine("💡 Option 3: Use the 'Toggle' or 'Macro' tab which works via Accessibility without any ADB.", TerminalLineType.SYSTEM)
                }
            }
        }
    }

    fun executeCommand(commandStr: String) {
        val trimmed = commandStr.trim()
        if (trimmed.isBlank()) return

        // 1. Built-in errorless commands that never fail
        when (trimmed.lowercase(Locale.getDefault())) {
            "clear" -> {
                clearLogs()
                return
            }
            "help" -> {
                appendLine("$ $trimmed", TerminalLineType.COMMAND)
                appendLine("=== ADB TERMINAL HELP & COMMANDS ===", TerminalLineType.SYSTEM)
                appendLine("  help      : Display this list of commands", TerminalLineType.OUTPUT)
                appendLine("  status    : Inspect terminal & elevation engine status", TerminalLineType.OUTPUT)
                appendLine("  ping      : Test shell responsiveness", TerminalLineType.OUTPUT)
                appendLine("  5g        : Enforce 5G NR network mode", TerminalLineType.OUTPUT)
                appendLine("  4g        : Enforce 4G LTE network mode", TerminalLineType.OUTPUT)
                appendLine("  auto      : Set dynamic 5G/4G multi-mode", TerminalLineType.OUTPUT)
                appendLine("  grant     : Request WRITE_SECURE_SETTINGS permission", TerminalLineType.OUTPUT)
                appendLine("  devices   : Show current connected engine status", TerminalLineType.OUTPUT)
                appendLine("  clear     : Clear terminal console output", TerminalLineType.OUTPUT)
                appendLine("  You can also type standard shell commands (e.g. getprop, id, date)", TerminalLineType.OUTPUT)
                return
            }
            "ping" -> {
                appendLine("$ $trimmed", TerminalLineType.COMMAND)
                appendLine("✔ Pong! Active shell engine is responsive (${_state.value.engine.name}).", TerminalLineType.SYSTEM)
                return
            }
            "status" -> {
                appendLine("$ $trimmed", TerminalLineType.COMMAND)
                appendLine("Engine: ${_state.value.engine.name}", TerminalLineType.OUTPUT)
                appendLine("Authorized: ${_state.value.isAuthorized}", TerminalLineType.OUTPUT)
                appendLine("Shizuku Running: ${_state.value.isShizukuRunning}", TerminalLineType.OUTPUT)
                appendLine("Root Available: ${_state.value.isRootAvailable}", TerminalLineType.OUTPUT)
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})", TerminalLineType.OUTPUT)
                return
            }
            "devices" -> {
                appendLine("$ $trimmed", TerminalLineType.COMMAND)
                val status = if (_state.value.isAuthorized) "device (authorized)" else "unauthorized"
                appendLine("List of devices attached:", TerminalLineType.OUTPUT)
                appendLine("localhost:5555\t$status [${_state.value.engine.name}]", TerminalLineType.OUTPUT)
                return
            }
            "5g" -> {
                appendLine("$ $trimmed", TerminalLineType.COMMAND)
                setNetworkModeViaAdb(NetworkMode.FIVE_G)
                return
            }
            "4g" -> {
                appendLine("$ $trimmed", TerminalLineType.COMMAND)
                setNetworkModeViaAdb(NetworkMode.FOUR_G)
                return
            }
            "auto" -> {
                appendLine("$ $trimmed", TerminalLineType.COMMAND)
                setNetworkModeViaAdb(NetworkMode.AUTO)
                return
            }
            "grant" -> {
                appendLine("$ $trimmed", TerminalLineType.COMMAND)
                grantWriteSecureSettings()
                return
            }
        }

        // 2. Intercept privileged commands gracefully if not authorized yet
        val isPrivileged = trimmed.startsWith("pm grant", ignoreCase = true) ||
                trimmed.startsWith("pm revoke", ignoreCase = true) ||
                trimmed.startsWith("cmd phone", ignoreCase = true) ||
                trimmed.startsWith("settings put", ignoreCase = true) ||
                trimmed.startsWith("svc ", ignoreCase = true) ||
                trimmed.startsWith("su ", ignoreCase = true)

        if (isPrivileged && !_state.value.isAuthorized) {
            appendLine("$ $trimmed", TerminalLineType.COMMAND)
            appendLine("ℹ️ Elevated ADB privileges are required for system command: $trimmed", TerminalLineType.SYSTEM)
            if (_state.value.isShizukuRunning) {
                appendLine("👉 Shizuku is active! Tap 'GRANT' above to authorize this terminal.", TerminalLineType.SYSTEM)
                requestPermission()
            } else {
                appendLine("👉 Option A: Tap 'GRANT' above to start Shizuku (Wireless Debugging, No PC).", TerminalLineType.SYSTEM)
                appendLine("👉 Option B: Run via PC: adb shell $trimmed", TerminalLineType.SYSTEM)
                appendLine("👉 Option C: Use the 'Toggle' or 'Macro' tab which works via Accessibility without ADB.", TerminalLineType.SYSTEM)
            }
            return
        }

        // 3. Resilient execution pipeline with automatic cascading fallback
        scope.launch(Dispatchers.IO) {
            _state.update { it.copy(isExecuting = true) }
            appendLine("$ $trimmed", TerminalLineType.COMMAND)

            try {
                val currentEngine = _state.value.engine
                var process: Process? = null

                // Attempt Shizuku if authorized
                if (currentEngine == AdbEngine.SHIZUKU && _state.value.isShizukuRunning) {
                    try {
                        process = spawnShizukuProcess(arrayOf("sh", "-c", trimmed))
                    } catch (e: Exception) {
                        appendLine("ℹ️ Shizuku notice: ${e.message}. Using fallback engine…", TerminalLineType.SYSTEM)
                    }
                }

                // Attempt Root if available
                if (process == null && (currentEngine == AdbEngine.ROOT || _state.value.isRootAvailable)) {
                    try {
                        process = Runtime.getRuntime().exec(arrayOf("su", "-c", trimmed))
                    } catch (_: Exception) {}
                }

                // Fallback to local shell
                if (process == null) {
                    process = Runtime.getRuntime().exec(arrayOf("sh", "-c", trimmed))
                }

                // Safely read output streams
                val stdOutReader = BufferedReader(InputStreamReader(process.inputStream))
                val stdErrReader = BufferedReader(InputStreamReader(process.errorStream))

                var outLine: String?
                var hasOutput = false
                while (stdOutReader.readLine().also { outLine = it } != null) {
                    hasOutput = true
                    appendLine(outLine ?: "", TerminalLineType.OUTPUT)
                }

                var errLine: String?
                var hasError = false
                while (stdErrReader.readLine().also { errLine = it } != null) {
                    hasError = true
                    appendLine(errLine ?: "", TerminalLineType.OUTPUT)
                }

                val exitCode = try {
                    process.waitFor()
                } catch (_: Exception) {
                    0
                }

                if (exitCode == 0) {
                    if (!hasOutput && !hasError) {
                        appendLine("✔ Command executed successfully.", TerminalLineType.SYSTEM)
                    }
                } else {
                    appendLine("ℹ️ Command completed (code $exitCode)", TerminalLineType.SYSTEM)
                }
            } catch (e: Exception) {
                appendLine("ℹ️ Execution notice: ${e.localizedMessage ?: e.message}", TerminalLineType.SYSTEM)
            } finally {
                _state.update { it.copy(isExecuting = false) }
            }
        }
    }

    private fun spawnShizukuProcess(cmdArray: Array<String>): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true
        return method.invoke(null, cmdArray, null, null) as Process
    }

    /**
     * Executes elevated command silently and returns success boolean.
     */
    suspend fun executeSilent(cmd: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val process: Process = when {
                _state.value.engine == AdbEngine.SHIZUKU && _state.value.isShizukuRunning ->
                    spawnShizukuProcess(arrayOf("sh", "-c", cmd))
                _state.value.isRootAvailable ->
                    Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                else ->
                    Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            }
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 1-Tap shortcut to grant WRITE_SECURE_SETTINGS directly on the device!
     */
    fun grantWriteSecureSettings() {
        val pkg = context.packageName
        val cmd = "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS"
        if (_state.value.isAuthorized) {
            executeCommand(cmd)
            appendLine("✔ WRITE_SECURE_SETTINGS command dispatched.", TerminalLineType.SYSTEM)
        } else {
            appendLine("$ $cmd", TerminalLineType.COMMAND)
            appendLine("ℹ️ To grant WRITE_SECURE_SETTINGS without a PC:", TerminalLineType.SYSTEM)
            appendLine("  1. Tap 'GRANT' above if you have Shizuku / Wireless Debugging running.", TerminalLineType.SYSTEM)
            appendLine("  2. Or if using a PC terminal, run: adb shell $cmd", TerminalLineType.SYSTEM)
        }
    }

    /**
     * Directly switches network modes via telephony service command line.
     */
    fun setNetworkModeViaAdb(mode: NetworkMode) {
        val bitmask = when (mode) {
            NetworkMode.FIVE_G -> PREFERRED_5G_NR
            NetworkMode.AUTO -> PREFERRED_5G_NR
            NetworkMode.FOUR_G -> PREFERRED_4G_LTE
        }
        val modeVal = when (mode) {
            NetworkMode.FIVE_G -> GLOBAL_MODE_5G
            NetworkMode.AUTO -> GLOBAL_MODE_AUTO
            NetworkMode.FOUR_G -> GLOBAL_MODE_4G
        }

        if (_state.value.isAuthorized) {
            executeCommand("cmd phone set-allowed-network-types-for-reason 0 0 $bitmask")
            executeCommand("cmd phone set-preferred-network-type 0 $modeVal")
            executeCommand("settings put global preferred_network_mode $modeVal")
            appendLine("✔ Network mode applied via ADB telephony service.", TerminalLineType.SYSTEM)
        } else {
            appendLine("ℹ️ Elevated terminal authorization required to switch modem via ADB.", TerminalLineType.SYSTEM)
            appendLine("👉 Use the 'Toggle' or 'Macro' tab to switch automatically without ADB, or tap 'GRANT' above.", TerminalLineType.SYSTEM)
        }
    }

    fun clearLogs() {
        _state.update { it.copy(logs = emptyList()) }
        appendLine("Terminal cleared.", TerminalLineType.SYSTEM)
    }

    private fun appendLine(text: String, type: TerminalLineType) {
        _state.update { current ->
            val updated = current.logs + TerminalLine(text = text, type = type)
            val trimmed = if (updated.size > 200) updated.takeLast(200) else updated
            current.copy(logs = trimmed)
        }
    }
}

