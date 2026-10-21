package com.example.networktoggle.bridge

import android.content.Context
import android.os.IBinder
import com.example.networktoggle.network.NetworkMode
import com.example.networktoggle.terminal.AdbTerminalManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

sealed class BridgeConnectionState {
    object Disconnected : BridgeConnectionState()
    data class Connected(
        val uid: Int,
        val pid: Int,
        val isRoot: Boolean,
        val isShell: Boolean
    ) : BridgeConnectionState()
}

object PrivilegedBridgeManager {
    private const val PREFERRED_5G_NR = 1576960L
    private const val PREFERRED_4G_LTE = 1048700L
    private const val GLOBAL_MODE_5G = 26
    private const val GLOBAL_MODE_AUTO = 33
    private const val GLOBAL_MODE_4G = 10

    private val _state = MutableStateFlow<BridgeConnectionState>(BridgeConnectionState.Disconnected)
    val state: StateFlow<BridgeConnectionState> = _state.asStateFlow()

    private var activeService: IPrivilegedService? = null
    private var activeToken: String = ""

    val isConnected: Boolean
        get() = _state.value is BridgeConnectionState.Connected

    val connectedUid: Int
        get() = (_state.value as? BridgeConnectionState.Connected)?.uid ?: -1

    val connectedPid: Int
        get() = (_state.value as? BridgeConnectionState.Connected)?.pid ?: -1

    private val deathRecipient = IBinder.DeathRecipient {
        synchronized(this) {
            activeService = null
            _state.value = BridgeConnectionState.Disconnected
        }
    }

    @Synchronized
    fun onServiceConnected(service: IPrivilegedService, uid: Int, pid: Int, token: String) {
        try {
            activeService?.asBinder()?.unlinkToDeath(deathRecipient, 0)
        } catch (_: Exception) {}

        activeService = service
        activeToken = token

        try {
            service.asBinder().linkToDeath(deathRecipient, 0)
        } catch (_: Exception) {}

        _state.value = BridgeConnectionState.Connected(
            uid = uid,
            pid = pid,
            isRoot = uid == 0,
            isShell = uid == 2000
        )
    }

    fun getLaunchCommand(context: Context): String {
        val pkg = context.packageName
        val token = PrivilegedAuthStore.getSecurityToken(context)
        return "export CLASSPATH=\$(pm path $pkg | cut -d: -f2 | head -n 1) && nohup app_process /system/bin com.example.networktoggle.bridge.server.PrivilegedServer \"$token\" $pkg > /dev/null 2>&1 &"
    }

    fun getAdbPcCommand(context: Context): String {
        val pkg = context.packageName
        val token = PrivilegedAuthStore.getSecurityToken(context)
        return "adb shell \"export CLASSPATH=\\\$(pm path $pkg | cut -d: -f2 | head -n 1) && nohup app_process /system/bin com.example.networktoggle.bridge.server.PrivilegedServer \\\"$token\\\" $pkg > /dev/null 2>&1 &\""
    }

    suspend fun startViaRoot(context: Context): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val cmd = getLaunchCommand(context)
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val exitCode = proc.waitFor()
            exitCode == 0
        } catch (_: Exception) {
            false
        }
    }

    fun startViaAdbTerminal(context: Context, terminal: AdbTerminalManager): Boolean {
        return try {
            val cmd = getLaunchCommand(context)
            terminal.executeCommand(cmd)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        val service = activeService ?: return@withContext false
        return@withContext try {
            service.ping(activeToken)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun setNetworkMode(mode: NetworkMode): Boolean = withContext(Dispatchers.IO) {
        val service = activeService ?: return@withContext false
        return@withContext try {
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

            // Execute telephony type setting across active SIM slots via elevated binder proxy
            service.setPreferredNetworkType(activeToken, -1, modeVal)
            service.setAllowedNetworkTypesForReason(activeToken, -1, 0, bitmask)
            service.putGlobalSetting(activeToken, "preferred_network_mode", modeVal.toString())
            true
        } catch (_: Exception) {
            false
        }
    }

    fun setNetworkModeSync(mode: NetworkMode): Boolean {
        val service = activeService ?: return false
        return try {
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

            service.setPreferredNetworkType(activeToken, -1, modeVal)
            service.setAllowedNetworkTypesForReason(activeToken, -1, 0, bitmask)
            service.putGlobalSetting(activeToken, "preferred_network_mode", modeVal.toString())
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun grantWriteSecureSettings(context: Context): Boolean = withContext(Dispatchers.IO) {
        val service = activeService ?: return@withContext false
        return@withContext try {
            service.grantRuntimePermission(
                activeToken,
                context.packageName,
                "android.permission.WRITE_SECURE_SETTINGS"
            )
        } catch (_: Exception) {
            false
        }
    }

    suspend fun executeCommand(command: String): Pair<Int, List<String>> = withContext(Dispatchers.IO) {
        val service = activeService ?: return@withContext Pair(-1, listOf("Privileged service not connected"))
        return@withContext try {
            val output = mutableListOf<String>()
            val exitCode = service.executeShell(activeToken, command, output)
            Pair(exitCode, output)
        } catch (e: Exception) {
            Pair(-1, listOf("IPC Error: ${e.message}"))
        }
    }

    fun stopService(): Boolean {
        val service = activeService ?: return false
        return try {
            service.exit(activeToken)
            activeService = null
            _state.value = BridgeConnectionState.Disconnected
            true
        } catch (_: Exception) {
            false
        }
    }
}
