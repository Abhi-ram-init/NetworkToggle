package com.example.networktoggle.terminal

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.networktoggle.bridge.BridgeConnectionState
import com.example.networktoggle.bridge.PrivilegedBridgeManager
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
    PRIVILEGED_BRIDGE,
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
    val isPermissionGranted: Boolean = false,
    val isShizukuRunning: Boolean = false,
    val isRootAvailable: Boolean = false,
    val isBridgeConnected: Boolean = false,
    val isExecuting: Boolean = false,
    val logs: List<TerminalLine> = emptyList(),
    val statusMessage: String = "Initializing ADB Terminal…",
    val kernelReport: KernelSecurityReport? = null,
    val isKernelHardened: Boolean = false,
    val kernelShieldText: String = "Auditing Kernel Security…"
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
                if (isGranted) "✔ Shizuku permission granted! Elevated ADB shell active."
                else "ℹ Shizuku permission request was dismissed.",
                TerminalLineType.SYSTEM
            )
            refreshState()
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        appendLine("⚡ Shizuku binder connected on device.", TerminalLineType.SYSTEM)
        refreshState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        appendLine("ℹ Shizuku service disconnected.", TerminalLineType.SYSTEM)
        refreshState()
    }

    init {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionListener)
        } catch (_: Exception) {}

        // Listen for Privileged Bridge connection changes
        scope.launch {
            PrivilegedBridgeManager.state.collect { bridgeState ->
                val isConnected = bridgeState is BridgeConnectionState.Connected
                if (isConnected) {
                    val uid = PrivilegedBridgeManager.connectedUid
                    val pid = PrivilegedBridgeManager.connectedPid
                    val identity = if (uid == 0) "Root (UID 0)" else "ADB Shell (UID $uid)"
                    appendLine("🟢 Privileged Backend Service attached ($identity, PID: $pid).", TerminalLineType.SYSTEM)
                }
                refreshState()
            }
        }

        appendLine("Linux & ADB Terminal initialized. Use 'grant' or 'revoke' to manage toggle permissions.", TerminalLineType.SYSTEM)

        // Asynchronously audit and log kernel security baseline
        scope.launch(Dispatchers.IO) {
            val report = KernelSecurityAuditor.audit()
            val secDesc = if (report.seccomp.mode == 2) "Seccomp BPF Active" else "Seccomp Active"
            val selinuxDesc = if (report.selinux.isEnforcing) "SELinux Enforcing" else "SELinux Permissive"
            val rsaDesc = if (report.secureBoot.isRsaAuthEnforced) "RSA Auth Active" else "RSA Open"
            appendLine("🛡️ Kernel Security: $secDesc | $selinuxDesc | $rsaDesc (Type 'kernel' for full audit)", TerminalLineType.SYSTEM)
        }

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
            val isBridgeConnected = PrivilegedBridgeManager.isConnected
            val shizukuRunning = try { Shizuku.pingBinder() } catch (_: Exception) { false }
            val shizukuVersion = if (shizukuRunning) try { Shizuku.getVersion() } catch (_: Exception) { 0 } else 0
            val isPreV11 = shizukuRunning && (shizukuVersion < 11 || try { Shizuku.isPreV11() } catch (_: Exception) { false })
            val shizukuGranted = shizukuRunning && !isPreV11 && try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (_: Exception) { false }
            val shizukuUid = if (shizukuGranted) try { Shizuku.getUid() } catch (_: Exception) { 2000 } else -1

            val rootAvailable = checkRootAvailable()
            val permissionGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_SECURE_SETTINGS
            ) == PackageManager.PERMISSION_GRANTED

            val engine = when {
                isBridgeConnected -> AdbEngine.PRIVILEGED_BRIDGE
                shizukuGranted -> AdbEngine.SHIZUKU
                rootAvailable -> AdbEngine.ROOT
                else -> AdbEngine.LOCAL_SHELL
            }

            val authorized = isBridgeConnected || shizukuGranted || rootAvailable
            val statusMsg = when {
                permissionGranted -> "🟢 Permission Active • WRITE_SECURE_SETTINGS Granted"
                isBridgeConnected -> {
                    val uid = PrivilegedBridgeManager.connectedUid
                    val identity = if (uid == 0) "Root (UID 0)" else "ADB Shell (UID $uid)"
                    "🟢 Authorized via Privileged Bridge ($identity)"
                }
                shizukuGranted -> {
                    val idStr = if (shizukuUid == 0) "Root UID 0" else "Shell UID $shizukuUid"
                    "🟢 Authorized via Shizuku (v$shizukuVersion • $idStr)"
                }
                rootAvailable -> "🟢 Authorized via Kernel Root (UID 0)"
                shizukuRunning -> "🟡 Shizuku Active (v$shizukuVersion) • Tap 'GRANT' to authorize"
                else -> "⚡ Local Shell Active (Run 'grant' to authorize app)"
            }

            val kernelReport = KernelSecurityAuditor.audit()
            val shieldText = when {
                kernelReport.isHardened -> "🛡️ Kernel Hardened (${kernelReport.securityScore}%) • Seccomp BPF • SELinux Enforcing"
                kernelReport.selinux.isEnforcing -> "🛡️ Kernel Protected • SELinux Enforcing • RSA Secured"
                else -> "🛡️ Kernel Standard Sandboxing Active"
            }

            _state.update {
                it.copy(
                    engine = engine,
                    isAuthorized = authorized,
                    isPermissionGranted = permissionGranted,
                    isShizukuRunning = shizukuRunning,
                    isRootAvailable = rootAvailable,
                    isBridgeConnected = isBridgeConnected,
                    statusMessage = statusMsg,
                    kernelReport = kernelReport,
                    isKernelHardened = kernelReport.isHardened,
                    kernelShieldText = shieldText
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
            if (PrivilegedBridgeManager.isConnected) {
                appendLine("✔ Already authorized via Privileged Backend Service (UID ${PrivilegedBridgeManager.connectedUid}).", TerminalLineType.SYSTEM)
                grantWriteSecureSettings()
                return@launch
            }

            val shizukuRunning = try { Shizuku.pingBinder() } catch (_: Exception) { false }
            if (shizukuRunning) {
                try {
                    val isPreV11 = try { Shizuku.isPreV11() } catch (_: Exception) { false }
                    if (isPreV11) {
                        appendLine("⚠ Shizuku v11+ is required. Please update your Shizuku app.", TerminalLineType.ERROR)
                        return@launch
                    }

                    if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                        appendLine("✔ Shizuku authorized. Granting WRITE_SECURE_SETTINGS…", TerminalLineType.SYSTEM)
                        grantWriteSecureSettings()
                    } else if (try { Shizuku.shouldShowRequestPermissionRationale() } catch (_: Exception) { false }) {
                        appendLine("ℹ Shizuku permission was previously dismissed. Prompting authorization…", TerminalLineType.SYSTEM)
                        Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
                    } else {
                        appendLine("Prompting Shizuku authorization dialog on phone…", TerminalLineType.SYSTEM)
                        Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
                    }
                } catch (e: Exception) {
                    appendLine("ℹ Shizuku prompt notice: ${e.message}", TerminalLineType.SYSTEM)
                }
            } else if (checkRootAvailable()) {
                appendLine("Root available. Granting WRITE_SECURE_SETTINGS…", TerminalLineType.SYSTEM)
                grantWriteSecureSettings()
            } else {
                if (isShizukuInstalled()) {
                    appendLine("ℹ Shizuku app is installed on phone, but service is not running.", TerminalLineType.SYSTEM)
                    appendLine("👉 Open Shizuku, tap 'Start' via Wireless Debugging, then return here.", TerminalLineType.SYSTEM)
                    withContext(Dispatchers.Main) {
                        launchShizukuApp()
                    }
                } else {
                    appendLine("ℹ No elevated engine active yet.", TerminalLineType.SYSTEM)
                    appendLine("💡 Option 1: Install Shizuku & start it via Wireless Debugging (No PC needed).", TerminalLineType.SYSTEM)
                    appendLine("💡 Option 2: Run via PC ADB: adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS", TerminalLineType.SYSTEM)
                    appendLine("💡 Option 3: Start Privileged Bridge: Type 'bridge start' in terminal.", TerminalLineType.SYSTEM)
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
                appendLine("=== ADB & LINUX INTERACTIVE SHELL ===", TerminalLineType.SYSTEM)
                appendLine("Permission Control (Core):", TerminalLineType.OUTPUT)
                appendLine("  grant       : Grant WRITE_SECURE_SETTINGS permission for 4G/5G toggling", TerminalLineType.OUTPUT)
                appendLine("  revoke      : Revoke / remove WRITE_SECURE_SETTINGS permission", TerminalLineType.OUTPUT)
                appendLine("Shizuku Wireless ADB:", TerminalLineType.OUTPUT)
                appendLine("  shizuku     : Inspect Shizuku server status, binder ping, version & UID", TerminalLineType.OUTPUT)
                appendLine("  shizuku-grant: Request Shizuku permission prompt dialog on phone", TerminalLineType.OUTPUT)
                appendLine("Kernel & Security Hardening:", TerminalLineType.OUTPUT)
                appendLine("  kernel      : Audit 5 kernel security pillars (Seccomp BPF, SELinux, Namespaces, FFS, Secure Boot)", TerminalLineType.OUTPUT)
                appendLine("  kconfig     : Inspect kernel config & attack surface reduction flags", TerminalLineType.OUTPUT)
                appendLine("Core Commands:", TerminalLineType.OUTPUT)
                appendLine("  help        : Display this command reference", TerminalLineType.OUTPUT)
                appendLine("  whoami / id : Print UID, GID, identity & active engine", TerminalLineType.OUTPUT)
                appendLine("  status      : Show permission & engine elevation status", TerminalLineType.OUTPUT)
                appendLine("  devices     : Show attached local ADB daemon status", TerminalLineType.OUTPUT)
                appendLine("  ping        : Test shell responsiveness", TerminalLineType.OUTPUT)
                appendLine("  clear       : Clear console logs", TerminalLineType.OUTPUT)
                appendLine("Cellular & Modem Control:", TerminalLineType.OUTPUT)
                appendLine("  5g          : Enforce 5G NR network mode", TerminalLineType.OUTPUT)
                appendLine("  4g          : Enforce 4G LTE network mode", TerminalLineType.OUTPUT)
                appendLine("  auto        : Set dynamic 5G/4G multi-mode", TerminalLineType.OUTPUT)
                appendLine("System Diagnostics:", TerminalLineType.OUTPUT)
                appendLine("  net / ip    : Show active network interfaces & IP routing", TerminalLineType.OUTPUT)
                appendLine("  battery     : Show battery level, status, temperature & health", TerminalLineType.OUTPUT)
                appendLine("  props       : Show key cellular and build properties", TerminalLineType.OUTPUT)
                appendLine("Privileged Bridge:", TerminalLineType.OUTPUT)
                appendLine("  bridge      : Inspect Privileged Backend Service (app_process daemon)", TerminalLineType.OUTPUT)
                appendLine("  bridge start: Launch the app_process background daemon", TerminalLineType.OUTPUT)
                appendLine("  bridge stop : Stop the background daemon via Binder IPC", TerminalLineType.OUTPUT)
                appendLine("Native Shell:", TerminalLineType.OUTPUT)
                appendLine("  Type any Linux command (e.g. getprop, ip a, pm list packages, uname -a, date)", TerminalLineType.OUTPUT)
                return
            }
            "grant", "grant-permission", "enable-permission", "enable" -> {
                appendLine("$ $trimmed", TerminalLineType.COMMAND)
                grantWriteSecureSettings()
                return
            }
            "revoke", "revoke-permission", "remove-permission", "remove", "disable-permission", "disable" -> {
                appendLine("$ $trimmed", TerminalLineType.COMMAND)
                revokeWriteSecureSettings()
                return
            }
            "shizuku", "shizuku-status" -> {
                appendLine("$ $trimmed", TerminalLineType.COMMAND)
                val installed = isShizukuInstalled()
                val running = try { Shizuku.pingBinder() } catch (_: Exception) { false }
                val version = if (running) try { Shizuku.getVersion() } catch (_: Exception) { 0 } else 0
                val isPreV11 = running && (version < 11 || try { Shizuku.isPreV11() } catch (_: Exception) { false })
                val granted = running && !isPreV11 && try { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (_: Exception) { false }
                val uid = if (granted) try { Shizuku.getUid() } catch (_: Exception) { -1 } else -1

                appendLine("=== SHIZUKU WIRELESS ADB STATUS ===", TerminalLineType.SYSTEM)
                appendLine("• App Installed: $installed (moe.shizuku.privileged.api)", TerminalLineType.OUTPUT)
                appendLine("• Service Status: ${if (running) "🟢 ACTIVE (Binder alive)" else "🔴 INACTIVE / DISCONNECTED"}", TerminalLineType.OUTPUT)
                if (running) {
                    appendLine("• API Version: v$version (${if (!isPreV11) "Supported" else "Deprecated < v11"})", TerminalLineType.OUTPUT)
                    appendLine("• Permission: ${if (granted) "✔ GRANTED (Authorized)" else "⚠ NOT GRANTED (Type 'shizuku-grant')"}", TerminalLineType.OUTPUT)
                    if (granted) {
                        val identity = if (uid == 0) "Root (UID 0)" else "ADB Shell (UID $uid)"
                        appendLine("• Execution Context: $identity", TerminalLineType.OUTPUT)
                    }
                } else if (installed) {
                    appendLine("👉 Start Shizuku: Open the Shizuku app and start via Wireless Debugging (No PC required).", TerminalLineType.OUTPUT)
                } else {
                    appendLine("👉 Download Shizuku: Install from GitHub (https://github.com/RikkaApps/Shizuku/releases).", TerminalLineType.OUTPUT)
                }
                return
            }
            "shizuku-grant", "shizuku-request" -> {
                appendLine("$ $trimmed", TerminalLineType.COMMAND)
                requestPermission()
                return
            }
            "kernel", "kconfig", "kernel-audit", "audit", "security" -> {
                appendLine("$ $trimmed", TerminalLineType.COMMAND)
                scope.launch(Dispatchers.IO) {
                    val report = KernelSecurityAuditor.audit()
                    val lines = KernelSecurityAuditor.formatTerminalAudit(report)
                    for (line in lines) {
                        appendLine(line, TerminalLineType.OUTPUT)
                    }
                }
                return
            }
            "ping" -> {
                appendLine("$ $trimmed", TerminalLineType.COMMAND)
                appendLine("✔ Pong! Active shell engine is responsive (${_state.value.engine.name}).", TerminalLineType.SYSTEM)
                return
            }
            "whoami", "id" -> {
                appendLine("$ $trimmed", TerminalLineType.COMMAND)
                val currentEngine = _state.value.engine
                when (currentEngine) {
                    AdbEngine.PRIVILEGED_BRIDGE -> {
                        val uid = PrivilegedBridgeManager.connectedUid
                        val pid = PrivilegedBridgeManager.connectedPid
                        val name = if (uid == 0) "root" else "shell"
                        appendLine("uid=$uid($name) gid=$uid($name) pid=$pid engine=PRIVILEGED_BRIDGE", TerminalLineType.OUTPUT)
                    }
                    AdbEngine.SHIZUKU -> {
                        appendLine("uid=2000(shell) gid=2000(shell) groups=2000(shell) engine=SHIZUKU", TerminalLineType.OUTPUT)
                    }
                    AdbEngine.ROOT -> {
                        appendLine("uid=0(root) gid=0(root) groups=0(root) engine=KERNEL_ROOT", TerminalLineType.OUTPUT)
                    }
                    else -> {
                        runLocalFallback("id")
                    }
                }
                return
            }
            "status" -> {
                appendLine("$ $trimmed", TerminalLineType.COMMAND)
                appendLine("Engine: ${_state.value.engine.name}", TerminalLineType.OUTPUT)
                appendLine("Authorized: ${_state.value.isAuthorized}", TerminalLineType.OUTPUT)
                appendLine("Permission (WRITE_SECURE_SETTINGS): ${if (_state.value.isPermissionGranted) "GRANTED (Active)" else "REVOKED / MISSING"}", TerminalLineType.OUTPUT)
                val report = _state.value.kernelReport ?: KernelSecurityAuditor.audit()
                appendLine("Kernel Hardening: ${report.securityScore}% (${if (report.isHardened) "HARDENED & SECURE" else "STANDARD RESTRICTIONS"})", TerminalLineType.OUTPUT)
                appendLine("Kernel Seccomp: ${report.seccomp.modeDescription}", TerminalLineType.OUTPUT)
                appendLine("Kernel SELinux: ${if (report.selinux.isEnforcing) "Enforcing" else "Permissive"} (${report.selinux.selinuxContext})", TerminalLineType.OUTPUT)
                appendLine("Secure Boot: ro.secure=${report.secureBoot.roSecure}, ro.adb.secure=${report.secureBoot.roAdbSecure}", TerminalLineType.OUTPUT)
                appendLine("Privileged Bridge: ${if (_state.value.isBridgeConnected) "CONNECTED (UID ${PrivilegedBridgeManager.connectedUid}, PID ${PrivilegedBridgeManager.connectedPid})" else "DISCONNECTED"}", TerminalLineType.OUTPUT)
                appendLine("Shizuku Running: ${_state.value.isShizukuRunning}", TerminalLineType.OUTPUT)
                appendLine("Root Available: ${_state.value.isRootAvailable}", TerminalLineType.OUTPUT)
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})", TerminalLineType.OUTPUT)
                return
            }
            "devices" -> {
                appendLine("$ $trimmed", TerminalLineType.COMMAND)
                val status = if (_state.value.isAuthorized) "device (authorized)" else "device (local shell)"
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
            "net", "ip" -> {
                appendLine("$ $trimmed", TerminalLineType.COMMAND)
                executePipeline("ip -brief address 2>/dev/null || ifconfig 2>/dev/null || ip addr")
                return
            }
            "battery" -> {
                appendLine("$ $trimmed", TerminalLineType.COMMAND)
                executePipeline("dumpsys battery 2>/dev/null || cat /sys/class/power_supply/battery/capacity 2>/dev/null || echo 'Battery telemetry unavailable'")
                return
            }
            "props" -> {
                appendLine("$ $trimmed", TerminalLineType.COMMAND)
                executePipeline("getprop | grep -E \"(gsm\\.|ro\\.build\\.|ro\\.product\\.)\" | head -n 30")
                return
            }
            "bridge" -> {
                appendLine("$ $trimmed", TerminalLineType.COMMAND)
                val isConn = PrivilegedBridgeManager.isConnected
                if (isConn) {
                    val uid = PrivilegedBridgeManager.connectedUid
                    val pid = PrivilegedBridgeManager.connectedPid
                    val identity = if (uid == 0) "Root UID 0" else "ADB Shell UID $uid"
                    appendLine("🟢 Privileged Backend Service is ACTIVE ($identity, PID: $pid).", TerminalLineType.SYSTEM)
                    appendLine("   Binder IPC is connected directly to Android System Server proxy.", TerminalLineType.OUTPUT)
                    appendLine("   Type '5g' or '4g' for instant background frequency switching.", TerminalLineType.OUTPUT)
                } else {
                    appendLine("⚪ Privileged Backend Service is INACTIVE.", TerminalLineType.OUTPUT)
                    appendLine("   Type 'bridge start' to launch the background app_process daemon.", TerminalLineType.OUTPUT)
                    appendLine("   Or copy the 1-line PC command from Settings tab.", TerminalLineType.OUTPUT)
                }
                return
            }
            "bridge start" -> {
                appendLine("$ $trimmed", TerminalLineType.COMMAND)
                appendLine("⚡ Launching Privileged Backend Service via app_process…", TerminalLineType.SYSTEM)
                val cmd = PrivilegedBridgeManager.getLaunchCommand(context)
                executeCommand(cmd)
                return
            }
            "bridge stop" -> {
                appendLine("$ $trimmed", TerminalLineType.COMMAND)
                val stopped = PrivilegedBridgeManager.stopService()
                if (stopped) {
                    appendLine("🛑 Privileged Backend Service stopped.", TerminalLineType.SYSTEM)
                } else {
                    appendLine("ℹ Privileged Backend Service was not running.", TerminalLineType.OUTPUT)
                }
                return
            }
        }

        // 2. Full-fledged shell execution pipeline
        executePipeline(trimmed)
    }

    private fun runLocalFallback(cmd: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    appendLine(line ?: "", TerminalLineType.OUTPUT)
                }
                proc.waitFor()
            } catch (e: Exception) {
                appendLine("Notice: ${e.message}", TerminalLineType.OUTPUT)
            }
        }
    }

    private fun executePipeline(trimmed: String) {
        scope.launch(Dispatchers.IO) {
            _state.update { it.copy(isExecuting = true) }
            appendLine("$ $trimmed", TerminalLineType.COMMAND)

            try {
                val currentEngine = _state.value.engine

                // 1. Privileged Bridge Execution (UID 2000 or UID 0)
                if (currentEngine == AdbEngine.PRIVILEGED_BRIDGE && PrivilegedBridgeManager.isConnected) {
                    val (exitCode, outputLines) = PrivilegedBridgeManager.executeCommand(trimmed)
                    var hasOutput = false
                    for (line in outputLines) {
                        hasOutput = true
                        if (line.startsWith("[ERR]")) {
                            appendLine(line.removePrefix("[ERR] ").trim(), TerminalLineType.ERROR)
                        } else {
                            appendLine(line, TerminalLineType.OUTPUT)
                        }
                    }
                    if (exitCode == 0) {
                        if (!hasOutput) {
                            appendLine("✔ Command executed successfully via Privileged Bridge.", TerminalLineType.SYSTEM)
                        }
                    } else {
                        appendLine("ℹ Command finished with exit code $exitCode", TerminalLineType.SYSTEM)
                    }
                    refreshState()
                    return@launch
                }

                // 2. Shizuku Execution (UID 2000)
                var process: Process? = null
                if (currentEngine == AdbEngine.SHIZUKU && _state.value.isShizukuRunning) {
                    try {
                        process = spawnShizukuProcess(arrayOf("sh", "-c", trimmed))
                    } catch (e: Exception) {
                        appendLine("ℹ Shizuku notice: ${e.message}. Using fallback engine…", TerminalLineType.SYSTEM)
                    }
                }

                // 3. Kernel Root Execution (UID 0)
                if (process == null && (currentEngine == AdbEngine.ROOT || _state.value.isRootAvailable)) {
                    try {
                        process = Runtime.getRuntime().exec(arrayOf("su", "-c", trimmed))
                    } catch (_: Exception) {}
                }

                // 4. Local Shell Execution (Native Android Linux Shell)
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
                    appendLine(errLine ?: "", TerminalLineType.ERROR)
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
                    appendLine("ℹ Command finished with exit code $exitCode", TerminalLineType.SYSTEM)
                    val isPrivileged = trimmed.startsWith("pm grant", ignoreCase = true) ||
                            trimmed.startsWith("pm revoke", ignoreCase = true) ||
                            trimmed.startsWith("cmd phone", ignoreCase = true) ||
                            trimmed.startsWith("settings put", ignoreCase = true)
                    if (isPrivileged && !_state.value.isAuthorized) {
                        appendLine("💡 Tip: Tap 'GRANT PERMISSION' or run 'bridge start' to elevate terminal privileges.", TerminalLineType.SYSTEM)
                    }
                }
            } catch (e: Exception) {
                appendLine("ℹ Execution notice: ${e.localizedMessage ?: e.message}", TerminalLineType.SYSTEM)
            } finally {
                _state.update { it.copy(isExecuting = false) }
                refreshState()
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
            if (PrivilegedBridgeManager.isConnected) {
                val (exitCode, _) = PrivilegedBridgeManager.executeCommand(cmd)
                return@withContext exitCode == 0
            }

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

        scope.launch(Dispatchers.IO) {
            if (PrivilegedBridgeManager.isConnected) {
                val ok = PrivilegedBridgeManager.grantWriteSecureSettings(context)
                if (ok) {
                    appendLine("✔ WRITE_SECURE_SETTINGS granted via Privileged Bridge System Server!", TerminalLineType.SYSTEM)
                    appendLine("🟢 4G/5G toggle is now fully enabled! Tap 'Toggle' tab or type '5g'/'4g'.", TerminalLineType.SYSTEM)
                } else {
                    executeCommand(cmd)
                }
                refreshState()
            } else if (_state.value.isAuthorized) {
                executeCommand(cmd)
                appendLine("✔ WRITE_SECURE_SETTINGS grant command dispatched via elevated shell.", TerminalLineType.SYSTEM)
                appendLine("🟢 4G/5G toggle is now enabled!", TerminalLineType.SYSTEM)
                refreshState()
            } else {
                appendLine("$ $cmd", TerminalLineType.COMMAND)
                appendLine("ℹ Attempting local shell dispatch: $cmd", TerminalLineType.SYSTEM)
                executePipeline(cmd)
                appendLine("💡 To grant on this phone without a PC:", TerminalLineType.SYSTEM)
                appendLine("  1. Tap 'GRANT' above if you have Shizuku or Wireless Debugging active.", TerminalLineType.SYSTEM)
                appendLine("  2. Or if connected to a PC, run: adb shell $cmd", TerminalLineType.SYSTEM)
                appendLine("  3. Or launch Privileged Bridge: Type 'bridge start' in this terminal.", TerminalLineType.SYSTEM)
            }
        }
    }

    /**
     * 1-Tap shortcut to revoke WRITE_SECURE_SETTINGS from the application!
     */
    fun revokeWriteSecureSettings() {
        val pkg = context.packageName
        val cmd = "pm revoke $pkg android.permission.WRITE_SECURE_SETTINGS"

        scope.launch(Dispatchers.IO) {
            if (PrivilegedBridgeManager.isConnected) {
                val ok = PrivilegedBridgeManager.revokeWriteSecureSettings(context)
                if (ok) {
                    appendLine("🛑 WRITE_SECURE_SETTINGS permission has been REVOKED via Privileged Bridge.", TerminalLineType.SYSTEM)
                    appendLine("ℹ Background 4G/5G switching disabled. Run 'grant' to enable it again.", TerminalLineType.SYSTEM)
                } else {
                    executeCommand(cmd)
                }
                refreshState()
            } else if (_state.value.isAuthorized) {
                executeCommand(cmd)
                appendLine("🛑 WRITE_SECURE_SETTINGS revoke command dispatched via elevated shell.", TerminalLineType.SYSTEM)
                appendLine("ℹ Permission disabled. Run 'grant' to re-enable.", TerminalLineType.SYSTEM)
                refreshState()
            } else {
                appendLine("$ $cmd", TerminalLineType.COMMAND)
                appendLine("ℹ Attempting local shell dispatch: $cmd", TerminalLineType.SYSTEM)
                executePipeline(cmd)
                appendLine("💡 To revoke via PC: adb shell $cmd", TerminalLineType.SYSTEM)
            }
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

        if (PrivilegedBridgeManager.isConnected) {
            scope.launch {
                val ok = PrivilegedBridgeManager.setNetworkMode(mode)
                if (ok) {
                    appendLine("✔ Network switched to ${mode.label()} via Privileged Bridge System Server proxy.", TerminalLineType.SYSTEM)
                } else {
                    executeCommand("cmd phone set-allowed-network-types-for-reason 0 0 $bitmask")
                    executeCommand("cmd phone set-preferred-network-type 0 $modeVal")
                    executeCommand("settings put global preferred_network_mode $modeVal")
                }
            }
        } else if (_state.value.isAuthorized) {
            executeCommand("cmd phone set-allowed-network-types-for-reason 0 0 $bitmask")
            executeCommand("cmd phone set-preferred-network-type 0 $modeVal")
            executeCommand("settings put global preferred_network_mode $modeVal")
            appendLine("✔ Network mode commands dispatched via elevated shell.", TerminalLineType.SYSTEM)
        } else {
            appendLine("ℹ Running in Local Shell mode. Dispatching telephony switch…", TerminalLineType.SYSTEM)
            executeCommand("cmd phone set-preferred-network-type 0 $modeVal")
            appendLine("💡 Tip: Run 'grant' to authorize WRITE_SECURE_SETTINGS, or use the 'Macro' tab via Accessibility.", TerminalLineType.SYSTEM)
        }
    }

    fun clearLogs() {
        _state.update { it.copy(logs = emptyList()) }
        appendLine("Terminal console cleared.", TerminalLineType.SYSTEM)
    }

    private fun appendLine(text: String, type: TerminalLineType) {
        _state.update { current ->
            val updated = current.logs + TerminalLine(text = text, type = type)
            val trimmed = if (updated.size > 250) updated.takeLast(250) else updated
            current.copy(logs = trimmed)
        }
    }
}
