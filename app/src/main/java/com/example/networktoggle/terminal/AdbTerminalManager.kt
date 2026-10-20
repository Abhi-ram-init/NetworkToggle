package com.example.networktoggle.terminal

import android.content.Context
import android.content.pm.PackageManager
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
                else "✖ Shizuku permission denied by user.",
                if (isGranted) TerminalLineType.SYSTEM else TerminalLineType.ERROR
            )
            refreshState()
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        appendLine("⚡ Shizuku service connected on device.", TerminalLineType.SYSTEM)
        refreshState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        appendLine("⚠ Shizuku service disconnected.", TerminalLineType.ERROR)
        refreshState()
    }

    init {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionListener)
        } catch (_: Exception) {}

        appendLine("ADB Terminal Initialized. Ready for commands.", TerminalLineType.SYSTEM)
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
                shizukuRunning -> "🟡 Shizuku Active • Tap 'Grant Permission' below"
                else -> "⚪ Local Shell • Permission required for system commands"
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
                    } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                        appendLine("Prompting Shizuku authorization dialog on phone…", TerminalLineType.SYSTEM)
                        Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
                    } else {
                        appendLine("Requesting Shizuku authorization dialog on phone…", TerminalLineType.SYSTEM)
                        Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
                    }
                } catch (e: Exception) {
                    appendLine("Failed to request Shizuku permission: ${e.message}", TerminalLineType.ERROR)
                }
            } else if (checkRootAvailable()) {
                appendLine("Requesting Root authorization on phone…", TerminalLineType.SYSTEM)
                try {
                    val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                    proc.waitFor()
                    appendLine("✔ Root permission confirmed.", TerminalLineType.SYSTEM)
                    refreshState()
                } catch (e: Exception) {
                    appendLine("Root request error: ${e.message}", TerminalLineType.ERROR)
                }
            } else {
                appendLine("⚠ Shizuku is not running on this phone. Follow the setup steps below to start Shizuku with Wireless Debugging (No PC needed).", TerminalLineType.ERROR)
            }
        }
    }

    fun executeCommand(commandStr: String) {
        val trimmed = commandStr.trim()
        if (trimmed.isBlank()) return

        if (trimmed.equals("clear", ignoreCase = true)) {
            clearLogs()
            return
        }

        scope.launch(Dispatchers.IO) {
            _state.update { it.copy(isExecuting = true) }
            appendLine("$ $trimmed", TerminalLineType.COMMAND)

            try {
                val currentEngine = _state.value.engine
                val process: Process = when (currentEngine) {
                    AdbEngine.SHIZUKU -> {
                        spawnShizukuProcess(arrayOf("sh", "-c", trimmed))
                    }
                    AdbEngine.ROOT -> {
                        Runtime.getRuntime().exec(arrayOf("su", "-c", trimmed))
                    }
                    else -> {
                        Runtime.getRuntime().exec(arrayOf("sh", "-c", trimmed))
                    }
                }

                // Read output streams
                val stdOutReader = BufferedReader(InputStreamReader(process.inputStream))
                val stdErrReader = BufferedReader(InputStreamReader(process.errorStream))

                var outLine: String?
                while (stdOutReader.readLine().also { outLine = it } != null) {
                    appendLine(outLine ?: "", TerminalLineType.OUTPUT)
                }

                var errLine: String?
                while (stdErrReader.readLine().also { errLine = it } != null) {
                    appendLine(errLine ?: "", TerminalLineType.ERROR)
                }

                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    appendLine("[Process exited with code $exitCode]", TerminalLineType.ERROR)
                }
            } catch (e: Exception) {
                appendLine("Execution error: ${e.message}", TerminalLineType.ERROR)
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
     * Executes elevated command silently and returns output or success boolean.
     */
    suspend fun executeSilent(cmd: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val process: Process = when (_state.value.engine) {
                AdbEngine.SHIZUKU -> spawnShizukuProcess(arrayOf("sh", "-c", cmd))
                AdbEngine.ROOT -> Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                else -> Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
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
        executeCommand("pm grant $pkg android.permission.WRITE_SECURE_SETTINGS")
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

        executeCommand("cmd phone set-allowed-network-types-for-reason 0 0 $bitmask")
        executeCommand("cmd phone set-preferred-network-type 0 $modeVal")
        executeCommand("settings put global preferred_network_mode $modeVal")
    }

    fun clearLogs() {
        _state.update { it.copy(logs = emptyList()) }
        appendLine("Terminal cleared.", TerminalLineType.SYSTEM)
    }

    private fun appendLine(text: String, type: TerminalLineType) {
        _state.update { current ->
            val updated = current.logs + TerminalLine(text = text, type = type)
            // Limit to last 200 lines for efficiency
            val trimmed = if (updated.size > 200) updated.takeLast(200) else updated
            current.copy(logs = trimmed)
        }
    }
}
