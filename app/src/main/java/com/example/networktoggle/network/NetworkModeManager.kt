package com.example.networktoggle.network

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.networktoggle.service.AutoScrollService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.DataOutputStream

data class DetectedNetwork(
    val displayName: String = "Detecting…",
    val is5G: Boolean = false,
    val is4G: Boolean = false,
    val isCellular: Boolean = true,
    val detail: String = "",
)

class NetworkModeManager(private val context: Context) {

    companion object {
        private const val TAG = "NetworkModeManager"
        const val PREF_NAME = "network_toggle_prefs"
        const val PREF_KEY_MODE = "preferred_network_mode"
        const val MODE_5G = "5G"
        const val MODE_AUTO = "AUTO"
        const val MODE_4G = "4G"

        // Custom Signature-Level Permission exclusive to this application
        const val PERMISSION_EXCLUSIVE_CONTROL = "com.example.networktoggle.permission.CONTROL_NETWORK_TOGGLE"

        // Telephony bitmasks (5G NR vs 4G LTE)
        private const val PREFERRED_5G_NR = 1576960   // NR | LTE | WCDMA | GSM
        private const val PREFERRED_4G_LTE = 1048700  // LTE | WCDMA | GSM (NR disabled)

        // Settings.Global network mode values
        private const val GLOBAL_MODE_5G = 26        // NR/LTE/GSM/WCDMA
        private const val GLOBAL_MODE_AUTO = 33      // NR/LTE Auto
        private const val GLOBAL_MODE_4G = 10        // LTE/GSM/WCDMA
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _targetMode = MutableStateFlow(getSavedMode())
    val targetMode: StateFlow<NetworkMode> = _targetMode.asStateFlow()

    private val _liveNetwork = MutableStateFlow(detectCurrentNetwork())
    val liveNetwork: StateFlow<DetectedNetwork> = _liveNetwork.asStateFlow()

    private var telephonyCallback: Any? = null
    private var phoneStateListener: PhoneStateListener? = null

    init {
        registerNetworkListener()
    }

    fun hasPermission(): Boolean {
        val phoneState = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        val basicPhoneState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, "android.permission.READ_BASIC_PHONE_STATE"
            ) == PackageManager.PERMISSION_GRANTED
        } else false
        return phoneState || basicPhoneState
    }

    fun getSavedMode(): NetworkMode {
        return when (prefs.getString(PREF_KEY_MODE, MODE_5G)) {
            MODE_5G -> NetworkMode.FIVE_G
            MODE_AUTO -> NetworkMode.AUTO
            else -> NetworkMode.FOUR_G
        }
    }

    fun saveMode(mode: NetworkMode) {
        val prefVal = when (mode) {
            NetworkMode.FIVE_G -> MODE_5G
            NetworkMode.AUTO -> MODE_AUTO
            NetworkMode.FOUR_G -> MODE_4G
        }
        prefs.edit().putString(PREF_KEY_MODE, prefVal).apply()
        _targetMode.value = mode
    }

    fun getSavedUiStyle(): AppUiStyle {
        return when (prefs.getString("selected_ui_style", AppUiStyle.CYBER_NEON.name)) {
            AppUiStyle.MINIMAL_CLEAN.name -> AppUiStyle.MINIMAL_CLEAN
            AppUiStyle.SPEEDOMETER.name -> AppUiStyle.SPEEDOMETER
            else -> AppUiStyle.CYBER_NEON
        }
    }

    fun saveUiStyle(style: AppUiStyle) {
        prefs.edit().putString("selected_ui_style", style.name).apply()
    }

    fun getAutoScrollEnabled(): Boolean {
        return prefs.getBoolean("auto_scroll_enabled", true)
    }

    fun saveAutoScrollEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_scroll_enabled", enabled).apply()
    }

    fun isAccessibilityEnabled(): Boolean {
        return AutoScrollService.isAccessibilityEnabled(context)
    }

    fun openAccessibilitySettings() {
        AutoScrollService.openAccessibilitySettings(context)
    }

    fun tryDirectSwitch(mode: NetworkMode): ToggleResult {
        val is5G = mode == NetworkMode.FIVE_G

        // Strategy 1: Root command if user's device is rooted
        if (tryRootSwitch(is5G)) {
            refreshNetworkState()
            return ToggleResult.DirectSuccess(mode, "Switched automatically via Root access")
        }

        // Strategy 2: WRITE_SECURE_SETTINGS if granted via ADB
        if (trySecureSettingsSwitch(is5G)) {
            refreshNetworkState()
            return ToggleResult.DirectSuccess(mode, "Switched automatically via Secure Settings")
        }

        // Strategy 3: TelephonyManager reflection
        if (tryReflectionSwitch(is5G)) {
            refreshNetworkState()
            return ToggleResult.DirectSuccess(mode, "Switched automatically via Telephony Manager")
        }

        // Strategy 4: Standard secure Android sandbox requires user authorization
        return ToggleResult.RequiresAction(mode)
    }

    fun toggleNetworkMode(): ToggleResult {
        val currentLive = detectCurrentNetwork()
        val target = if (currentLive.is5G) NetworkMode.FOUR_G else NetworkMode.FIVE_G
        return tryDirectSwitch(target)
    }

    fun setNetworkMode(mode: NetworkMode): ToggleResult {
        return tryDirectSwitch(mode)
    }

    private fun tryRootSwitch(is5G: Boolean): Boolean {
        return try {
            val bitmask = if (is5G) PREFERRED_5G_NR else PREFERRED_4G_LTE
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("cmd phone set-allowed-network-types-for-reason 0 0 $bitmask\n")
            os.writeBytes("exit\n")
            os.flush()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun trySecureSettingsSwitch(is5G: Boolean): Boolean {
        return try {
            val modeVal = if (is5G) GLOBAL_MODE_5G else GLOBAL_MODE_4G
            Settings.Global.putInt(context.contentResolver, "preferred_network_mode", modeVal)
            Settings.Global.putInt(context.contentResolver, "preferred_network_mode0", modeVal)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun tryReflectionSwitch(is5G: Boolean): Boolean {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val targetBitmask = if (is5G) PREFERRED_5G_NR else PREFERRED_4G_LTE
            val method = tm.javaClass.getDeclaredMethod(
                "setAllowedNetworkTypeBitmask", Long::class.javaPrimitiveType
            )
            method.isAccessible = true
            method.invoke(tm, targetBitmask.toLong())
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Opens the hidden Android RadioInfo testing menu (*#*#4636#*#*).
     * This is the universal direct method used by 5G/4G switcher apps on Android
     * that allows users to pick "NR only", "NR/LTE", or "LTE only" directly.
     */
    fun openRadioInfo(ctx: Context = context): Boolean {
        if (getAutoScrollEnabled()) {
            AutoScrollService.startAutoScrollSession(ctx)
            triggerRootSwipeIfApplicable()
        }
        val intents = listOf(
            Intent("android.intent.action.MAIN").setClassName(
                "com.android.settings", "com.android.settings.RadioInfo"
            ),
            Intent().setComponent(
                ComponentName("com.android.settings", "com.android.settings.RadioInfo")
            ),
            Intent("android.intent.action.VIEW").setComponent(
                ComponentName("com.android.settings", "com.android.settings.RadioInfo")
            ),
            Intent().setComponent(
                ComponentName("com.android.phone", "com.android.phone.settings.RadioInfoPreference")
            ),
            Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
            Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
        )
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    /**
     * Opens the device's Mobile Network Settings page directly.
     */
    fun openMobileNetworkSettings(ctx: Context = context): Boolean {
        if (getAutoScrollEnabled()) {
            AutoScrollService.startAutoScrollSession(ctx)
            triggerRootSwipeIfApplicable()
        }

        val fragmentArgs = Bundle().apply {
            putString(":settings:fragment_args_key", "enabled_networks_key")
        }

        val intents = listOf(
            Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
            Intent().setComponent(
                ComponentName("com.android.phone", "com.android.phone.MobileNetworkSettings")
            ),
            Intent().setComponent(
                ComponentName(
                    "com.android.settings",
                    "com.android.settings.network.telephony.MobileNetworkActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.android.settings",
                    "com.android.settings.Settings\$MobileNetworkActivity"
                )
            ),
            Intent(Settings.ACTION_DATA_ROAMING_SETTINGS),
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        )
        for (intent in intents) {
            try {
                intent.putExtra(":settings:fragment_args_key", "enabled_networks_key")
                intent.putExtra(":settings:show_fragment_args", fragmentArgs)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    private fun triggerRootSwipeIfApplicable() {
        if (!AutoScrollService.isAccessibilityEnabled(context)) {
            Thread {
                try {
                    Thread.sleep(700)
                    for (i in 1..4) {
                        val process = Runtime.getRuntime().exec("su")
                        val os = DataOutputStream(process.outputStream)
                        os.writeBytes("input swipe 500 1600 500 400 250\n")
                        os.writeBytes("exit\n")
                        os.flush()
                        process.waitFor()
                        Thread.sleep(300)
                    }
                } catch (_: Exception) {
                }
            }.start()
        }
    }

    fun refreshNetworkState() {
        _liveNetwork.value = detectCurrentNetwork()
        registerNetworkListener()
    }

    fun detectCurrentNetwork(): DetectedNetwork {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return DetectedNetwork("No Telephony", false, false, false, "Unknown")

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = cm?.activeNetwork
        val caps = activeNet?.let { cm.getNetworkCapabilities(it) }

        val hasCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val hasWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        if (!hasPermission()) {
            val status = if (hasCellular) "Cellular Active" else if (hasWifi) "Wi-Fi Active" else "No Connection"
            return DetectedNetwork(
                displayName = status,
                is5G = false,
                is4G = false,
                isCellular = hasCellular,
                detail = "Grant permission for real-time 5G/4G detection"
            )
        }

        return try {
            val networkType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                tm.dataNetworkType
            } else {
                @Suppress("DEPRECATION")
                tm.networkType
            }

            when (networkType) {
                TelephonyManager.NETWORK_TYPE_NR -> {
                    DetectedNetwork("5G NR Connected", is5G = true, is4G = false, isCellular = true, "5G Standalone")
                }
                TelephonyManager.NETWORK_TYPE_LTE -> {
                    DetectedNetwork("4G LTE Connected", is5G = false, is4G = true, isCellular = true, "LTE High-Speed")
                }
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_UMTS -> {
                    DetectedNetwork("3G HSPA Connected", is5G = false, is4G = false, isCellular = true, "3G Network")
                }
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_GPRS -> {
                    DetectedNetwork("2G Connected", is5G = false, is4G = false, isCellular = true, "2G Network")
                }
                else -> {
                    if (hasCellular) {
                        DetectedNetwork("Mobile Data Active", is5G = false, is4G = false, isCellular = true, "Cellular Connected")
                    } else if (hasWifi) {
                        DetectedNetwork("Wi-Fi Connected", is5G = false, is4G = false, isCellular = false, "Mobile standby")
                    } else {
                        DetectedNetwork("Searching Network…", is5G = false, is4G = false, isCellular = false, "No signal")
                    }
                }
            }
        } catch (e: SecurityException) {
            DetectedNetwork("Permission Needed", false, false, hasCellular, "Tap Grant Permission below")
        }
    }

    private fun registerNetworkListener() {
        if (!hasPermission()) return
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (telephonyCallback == null) {
                    val cb = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener,
                        TelephonyCallback.DataConnectionStateListener {
                        override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
                            when (info.overrideNetworkType) {
                                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> {
                                    _liveNetwork.value = DetectedNetwork(
                                        "5G Connected", is5G = true, is4G = false, isCellular = true, "5G Non-Standalone / Advanced"
                                    )
                                }
                                else -> {
                                    _liveNetwork.value = detectCurrentNetwork()
                                }
                            }
                        }

                        override fun onDataConnectionStateChanged(state: Int, networkType: Int) {
                            _liveNetwork.value = detectCurrentNetwork()
                        }
                    }
                    tm.registerTelephonyCallback(context.mainExecutor, cb)
                    telephonyCallback = cb
                }
            } else {
                if (phoneStateListener == null) {
                    val listener = object : PhoneStateListener() {
                        @Deprecated("Deprecated in Java")
                        override fun onDataConnectionStateChanged(state: Int, networkType: Int) {
                            _liveNetwork.value = detectCurrentNetwork()
                        }
                    }
                    @Suppress("DEPRECATION")
                    tm.listen(listener, PhoneStateListener.LISTEN_DATA_CONNECTION_STATE)
                    phoneStateListener = listener
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not register telephony listener: ${e.message}")
        }
    }
}

enum class NetworkMode {
    FIVE_G, AUTO, FOUR_G;

    fun label(): String = when (this) {
        FIVE_G -> "5G NR"
        AUTO -> "5G/4G Auto"
        FOUR_G -> "4G LTE"
    }

    fun shortLabel(): String = when (this) {
        FIVE_G -> "5G"
        AUTO -> "Auto"
        FOUR_G -> "4G"
    }

    fun next(): NetworkMode = when (this) {
        FIVE_G -> FOUR_G
        AUTO -> FIVE_G
        FOUR_G -> FIVE_G
    }

    fun nextLabel(): String = next().label()
}

enum class AppUiStyle {
    CYBER_NEON,
    MINIMAL_CLEAN,
    SPEEDOMETER;

    fun label(): String = when (this) {
        CYBER_NEON -> "Cyber"
        MINIMAL_CLEAN -> "Minimal"
        SPEEDOMETER -> "Meter"
    }

    fun icon(): String = when (this) {
        CYBER_NEON -> "⚡"
        MINIMAL_CLEAN -> "🎨"
        SPEEDOMETER -> "🏎️"
    }
}

sealed class ToggleResult {
    data class DirectSuccess(val mode: NetworkMode, val message: String) : ToggleResult()
    data class RequiresAction(val mode: NetworkMode) : ToggleResult()
}
