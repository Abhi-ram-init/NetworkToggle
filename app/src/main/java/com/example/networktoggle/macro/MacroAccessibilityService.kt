package com.example.networktoggle.macro

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Standalone AccessibilityService enabling touch simulation, clicks, swipes,
 * text-targeted node clicking, and automated screen navigation without root.
 *
 * KEY IMPROVEMENTS over previous version:
 * - Event-driven window transition detection via [waitForWindowPackage]
 * - Retry + scroll-fallback on [findAndClickTextSuspend]
 * - OEM-adaptive multi-text search via [findAndClickAnyOf]
 * - Display-relative swipe coordinates (no hardcoded pixels)
 * - Records clicked text for macro capture
 */
class MacroAccessibilityService : AccessibilityService() {

    // Delivers incoming AccessibilityEvents to coroutine callers waiting on them
    private val eventChannel = Channel<AccessibilityEvent>(capacity = Channel.UNLIMITED)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceActive.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Track clicked text for recording
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val text = event.text.joinToString(" ").trim()
            if (text.isNotBlank()) {
                _lastClickedText.value = text
            }
        }

        // Forward ALL events to any coroutine waiting for a window change
        eventChannel.trySend(event)
    }

    override fun onInterrupt() {
        // Required override
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
            _isServiceActive.value = false
        }
        eventChannel.close()
    }

    // ------------------------------------------------------------------
    // GESTURE DISPATCH
    // ------------------------------------------------------------------

    /**
     * Dispatches a simulated tap/click gesture at the specified screen coordinates without root.
     */
    fun clickAt(x: Float, y: Float, durationMs: Long = 60L, callback: ((Boolean) -> Unit)? = null): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            callback?.invoke(false)
            return false
        }
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                callback?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                callback?.invoke(false)
            }
        }, null)
    }

    /**
     * Dispatches a simulated swipe gesture from start to end coordinates without root.
     */
    fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 350L,
        callback: ((Boolean) -> Unit)? = null
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            callback?.invoke(false)
            return false
        }
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                callback?.invoke(true)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                callback?.invoke(false)
            }
        }, null)
    }

    // ------------------------------------------------------------------
    // NODE SEARCH & CLICKING
    // ------------------------------------------------------------------

    /**
     * Finds a UI element displaying the given text (case-insensitive, partial match by default)
     * and clicks it. Tries performAction(ACTION_CLICK) first, then falls back to coordinate tap.
     */
    fun findAndClickText(text: String, exactMatch: Boolean = false): Boolean {
        val root = rootInActiveWindow ?: return false
        val query = text.trim().lowercase()
        val foundNodes = mutableListOf<AccessibilityNodeInfo>()
        searchNodesByText(root, query, exactMatch, foundNodes)

        for (node in foundNodes) {
            // Walk up the tree to find the clickable parent
            var target: AccessibilityNodeInfo? = node
            while (target != null) {
                if (target.isClickable) {
                    if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        return true
                    }
                }
                target = target.parent
            }
            // Fallback: Tap the centre of the node bounding rect
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.width() > 0 && bounds.height() > 0) {
                clickAt(bounds.centerX().toFloat(), bounds.centerY().toFloat(), 80L)
                return true
            }
        }
        return false
    }

    /**
     * Suspend version: retries up to [maxAttempts] times with [retryDelayMs] gaps.
     * On each failed attempt scrolls down once so off-screen nodes are reachable.
     */
    suspend fun findAndClickTextSuspend(
        text: String,
        exactMatch: Boolean = false,
        maxAttempts: Int = 4,
        retryDelayMs: Long = 600L
    ): Boolean {
        repeat(maxAttempts) { attempt ->
            if (findAndClickText(text, exactMatch)) return true
            if (attempt < maxAttempts - 1) {
                scrollDown()
                delay(retryDelayMs)
            }
        }
        return false
    }

    /**
     * Tries each candidate text in order. Returns true as soon as any one is found & clicked.
     * Useful for OEM-adaptive navigation where different manufacturers use different labels.
     * e.g. "Network & Internet" vs "Connections" vs "SIM cards & mobile networks"
     */
    suspend fun findAndClickAnyOf(
        candidates: List<String>,
        exactMatch: Boolean = false,
        maxScrollAttempts: Int = 3,
        retryDelayMs: Long = 500L
    ): Boolean {
        repeat(maxScrollAttempts) { scrollTry ->
            for (candidate in candidates) {
                if (findAndClickText(candidate, exactMatch)) return true
            }
            if (scrollTry < maxScrollAttempts - 1) {
                scrollDown()
                delay(retryDelayMs)
            }
        }
        return false
    }

    // ------------------------------------------------------------------
    // EVENT-DRIVEN WINDOW WAITING
    // ------------------------------------------------------------------

    /**
     * Waits (suspends) until an AccessibilityEvent indicates that a window
     * belonging to [packageName] has become the active foreground window,
     * or until [timeoutMs] elapses. Returns true if the window appeared.
     *
     * This replaces the unreliable raw delay() approach.
     */
    suspend fun waitForWindowPackage(packageName: String, timeoutMs: Long = 5000L): Boolean {
        return withTimeoutOrNull(timeoutMs) {
            // Drain any stale events first
            while (eventChannel.tryReceive().isSuccess) { /* consume */ }

            // Wait for a window state change event from the desired package
            for (event in eventChannel) {
                if (
                    event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                    event.packageName?.toString() == packageName
                ) {
                    return@withTimeoutOrNull true
                }
            }
            false
        } ?: false
    }

    /**
     * Waits until any text node matching [text] appears in the active window.
     * Retries every [retryDelayMs] until [timeoutMs] has elapsed.
     */
    suspend fun waitForText(text: String, timeoutMs: Long = 5000L, retryDelayMs: Long = 300L): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        val query = text.trim().lowercase()
        while (System.currentTimeMillis() < deadline) {
            val root = rootInActiveWindow
            if (root != null) {
                val results = mutableListOf<AccessibilityNodeInfo>()
                searchNodesByText(root, query, false, results)
                if (results.isNotEmpty()) return true
            }
            delay(retryDelayMs)
        }
        return false
    }

    // ------------------------------------------------------------------
    // SCROLLING
    // ------------------------------------------------------------------

    /**
     * Performs a scroll-forward (down) on the most scrollable node in the window,
     * or falls back to a display-relative swipe gesture.
     */
    fun scrollDown(): Boolean {
        val root = rootInActiveWindow
        if (root != null && tryScrollNode(root, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            return true
        }
        val (cx, cy, _, height) = displayCenter()
        return swipe(cx, cy + height * 0.25f, cx, cy - height * 0.25f, 350L)
    }

    /**
     * Performs a scroll-backward (up) on the most scrollable node in the window,
     * or falls back to a display-relative swipe gesture.
     */
    fun scrollUp(): Boolean {
        val root = rootInActiveWindow
        if (root != null && tryScrollNode(root, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
            return true
        }
        val (cx, cy, _, height) = displayCenter()
        return swipe(cx, cy - height * 0.25f, cx, cy + height * 0.25f, 350L)
    }

    private fun tryScrollNode(node: AccessibilityNodeInfo, action: Int): Boolean {
        if (node.isScrollable) {
            if (node.performAction(action)) return true
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                if (tryScrollNode(child, action)) return true
            }
        }
        return false
    }

    // ------------------------------------------------------------------
    // SCREEN READING
    // ------------------------------------------------------------------

    /**
     * Reads all visible text nodes on the active window (for READ_SIM_SETTINGS).
     */
    fun readActiveWindowTexts(): List<String> {
        val root = rootInActiveWindow ?: return emptyList()
        val list = mutableListOf<String>()
        collectTexts(root, list)
        return list
    }

    private fun collectTexts(node: AccessibilityNodeInfo, list: MutableList<String>) {
        val t = node.text?.toString()?.trim()
        if (!t.isNullOrBlank() && !list.contains(t)) {
            list.add(t)
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectTexts(it, list) }
        }
    }

    // ------------------------------------------------------------------
    // GLOBAL ACTIONS
    // ------------------------------------------------------------------

    fun pressBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun pressHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun openNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun openQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    // ------------------------------------------------------------------
    // HELPERS
    // ------------------------------------------------------------------

    private fun searchNodesByText(
        node: AccessibilityNodeInfo,
        queryLower: String,
        exactMatch: Boolean,
        results: MutableList<AccessibilityNodeInfo>
    ) {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val matches = if (exactMatch) {
            text == queryLower || desc == queryLower
        } else {
            text.contains(queryLower) || desc.contains(queryLower)
        }
        if (matches) results.add(node)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                searchNodesByText(child, queryLower, exactMatch, results)
            }
        }
    }

    /** Returns (centerX, centerY, screenWidth, screenHeight) as display-relative values. */
    private fun displayCenter(): Array<Float> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
            val metrics = wm.currentWindowMetrics
            val w = metrics.bounds.width().toFloat()
            val h = metrics.bounds.height().toFloat()
            arrayOf(w / 2f, h / 2f, w, h)
        } else {
            @Suppress("DEPRECATION")
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm?.defaultDisplay?.getMetrics(dm)
            val w = dm.widthPixels.toFloat().takeIf { it > 0 } ?: 1080f
            val h = dm.heightPixels.toFloat().takeIf { it > 0 } ?: 2340f
            arrayOf(w / 2f, h / 2f, w, h)
        }
    }

    // ------------------------------------------------------------------
    // COMPANION
    // ------------------------------------------------------------------

    companion object {
        @Volatile
        var instance: MacroAccessibilityService? = null
            private set

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

        private val _lastClickedText = MutableStateFlow<String?>(null)
        val lastClickedText: StateFlow<String?> = _lastClickedText.asStateFlow()

        val isServiceRunning: Boolean
            get() = instance != null

        /**
         * Checks whether the user has enabled this Accessibility Service in Android Settings.
         */
        fun isAccessibilityEnabled(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                ?: return false
            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            for (enabledService in enabledServices) {
                val serviceInfo = enabledService.resolveInfo.serviceInfo
                if (serviceInfo.packageName == context.packageName &&
                    serviceInfo.name == MacroAccessibilityService::class.java.name
                ) {
                    return true
                }
            }
            return false
        }

        /**
         * Opens the Android Accessibility Settings screen for the user to enable the service.
         */
        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
}
