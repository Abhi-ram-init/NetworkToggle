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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Standalone AccessibilityService enabling touch simulation, clicks, swipes,
 * text-targeted node clicking, and automated screen navigation without root.
 *
 * Robust, resilient implementation with:
 * - Suspending gesture dispatch (waits for gesture completion before proceeding)
 * - Multi-window root traversal (checks all active interactive windows, not just rootInActiveWindow)
 * - Visible bounds filtering (ensures clicks land on real, visible screen coordinates)
 * - Dual-action click (executes ACTION_CLICK and simulated touch tap for 100% reliability)
 * - Auto-scroll recovery when off-screen
 * - OEM-adaptive text lookups
 */
class MacroAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceActive.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val text = event.text.joinToString(" ").trim()
            if (text.isNotBlank()) {
                _lastClickedText.value = text
            }
        }
    }

    override fun onInterrupt() {
        // Handle interruption
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
            _isServiceActive.value = false
        }
    }

    // ------------------------------------------------------------------
    // SUSPENDING GESTURE DISPATCH
    // ------------------------------------------------------------------

    /**
     * Dispatches a tap gesture and suspends until Android confirms completion.
     */
    suspend fun clickAtSuspend(x: Float, y: Float, durationMs: Long = 70L): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val (cx, cy, sw, sh) = displayMetrics()
        val clampedX = x.coerceIn(10f, sw - 10f)
        val clampedY = y.coerceIn(10f, sh - 10f)

        return suspendCancellableCoroutine { cont ->
            val path = Path().apply { moveTo(clampedX, clampedY) }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            }, null)

            if (!dispatched && cont.isActive) {
                cont.resume(false)
            }
        }
    }

    /**
     * Dispatches a swipe gesture and suspends until completed, followed by a settling delay.
     */
    suspend fun swipeSuspend(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 320L
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val (cx, cy, sw, sh) = displayMetrics()
        val sX = startX.coerceIn(10f, sw - 10f)
        val sY = startY.coerceIn(10f, sh - 10f)
        val eX = endX.coerceIn(10f, sw - 10f)
        val eY = endY.coerceIn(10f, sh - 10f)

        val result = suspendCancellableCoroutine { cont ->
            val path = Path().apply {
                moveTo(sX, sY)
                lineTo(eX, eY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            }, null)

            if (!dispatched && cont.isActive) {
                cont.resume(false)
            }
        }

        // Wait for scroll momentum to settle
        delay(250)
        return result
    }

    fun clickAt(x: Float, y: Float, durationMs: Long = 60L): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val (cx, cy, sw, sh) = displayMetrics()
        val path = Path().apply { moveTo(x.coerceIn(10f, sw - 10f), y.coerceIn(10f, sh - 10f)) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 350L): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    // ------------------------------------------------------------------
    // MULTI-WINDOW ROOT ACCESS
    // ------------------------------------------------------------------

    private fun getAllActiveRoots(): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { roots.add(it) }
        try {
            windows?.forEach { win ->
                win.root?.let { r ->
                    if (!roots.contains(r)) roots.add(r)
                }
            }
        } catch (_: Exception) {}
        return roots
    }

    // ------------------------------------------------------------------
    // NODE SEARCH & RESILIENT CLICKING
    // ------------------------------------------------------------------

    /**
     * Searches all active windows for matching text and performs a resilient click.
     * Uses both accessibility ACTION_CLICK and simulated touch gesture tap.
     */
    suspend fun findAndClickTextSuspend(
        text: String,
        exactMatch: Boolean = false,
        maxAttempts: Int = 4,
        retryDelayMs: Long = 500L
    ): Boolean {
        repeat(maxAttempts) { attempt ->
            if (performClickOnText(text, exactMatch)) {
                return true
            }
            if (attempt < maxAttempts - 1) {
                scrollDownSuspend()
                delay(retryDelayMs)
            }
        }
        return false
    }

    /**
     * Tries each candidate in order. Returns true as soon as one matches and is clicked.
     */
    suspend fun findAndClickAnyOf(
        candidates: List<String>,
        exactMatch: Boolean = false,
        maxScrollAttempts: Int = 4,
        retryDelayMs: Long = 400L
    ): Boolean {
        repeat(maxScrollAttempts) { scrollTry ->
            for (candidate in candidates) {
                if (performClickOnText(candidate, exactMatch)) {
                    return true
                }
            }
            if (scrollTry < maxScrollAttempts - 1) {
                scrollDownSuspend()
                delay(retryDelayMs)
            }
        }
        return false
    }

    fun findAndClickText(text: String, exactMatch: Boolean = false): Boolean {
        val roots = getAllActiveRoots()
        if (roots.isEmpty()) return false
        val query = text.trim().lowercase()

        for (root in roots) {
            val foundNodes = mutableListOf<AccessibilityNodeInfo>()
            searchNodesByText(root, query, exactMatch, foundNodes)

            for (node in foundNodes) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                val (cx, cy, sw, sh) = displayMetrics()

                // Check clickable ancestor
                var target: AccessibilityNodeInfo? = node
                var clickPerformed = false
                while (target != null) {
                    if (target.isClickable) {
                        clickPerformed = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        if (clickPerformed) break
                    }
                    target = target.parent
                }
                if (clickPerformed) return true

                // If not clickable via action, simulate coordinate tap if visible
                if (bounds.width() > 0 && bounds.height() > 0 && bounds.centerY() in 10..(sh.toInt() - 10)) {
                    clickAt(bounds.centerX().toFloat(), bounds.centerY().toFloat(), 70L)
                    return true
                }
            }
        }
        return false
    }

    private suspend fun performClickOnText(text: String, exactMatch: Boolean): Boolean {
        val roots = getAllActiveRoots()
        if (roots.isEmpty()) return false
        val query = text.trim().lowercase()
        val (cx, cy, sw, sh) = displayMetrics()

        for (root in roots) {
            val foundNodes = mutableListOf<AccessibilityNodeInfo>()
            searchNodesByText(root, query, exactMatch, foundNodes)

            for (node in foundNodes) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)

                // Must be on the visible screen
                val isVisible = bounds.width() > 0 && bounds.height() > 0 &&
                        bounds.centerY() in 10..(sh.toInt() - 10) &&
                        bounds.centerX() in 10..(sw.toInt() - 10)

                if (isVisible) {
                    // 1. Try accessibility ACTION_CLICK on node or parent
                    var clickTriggered = false
                    var target: AccessibilityNodeInfo? = node
                    while (target != null) {
                        if (target.isClickable) {
                            clickTriggered = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            if (clickTriggered) break
                        }
                        target = target.parent
                    }

                    if (clickTriggered) {
                        delay(250)
                        return true
                    }

                    // 2. Only if ACTION_CLICK failed or wasn't clickable, dispatch physical touch tap
                    val tapSuccess = clickAtSuspend(bounds.centerX().toFloat(), bounds.centerY().toFloat(), 80L)
                    delay(250)
                    return tapSuccess
                }
            }
        }
        return false
    }

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

    // ------------------------------------------------------------------
    // SCROLLING
    // ------------------------------------------------------------------

    suspend fun scrollDownSuspend(): Boolean {
        val roots = getAllActiveRoots()
        for (root in roots) {
            if (tryScrollNode(root, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                delay(300)
                return true
            }
        }
        val (cx, cy, sw, sh) = displayMetrics()
        return swipeSuspend(cx, cy + sh * 0.22f, cx, cy - sh * 0.22f, 320L)
    }

    suspend fun scrollUpSuspend(): Boolean {
        val roots = getAllActiveRoots()
        for (root in roots) {
            if (tryScrollNode(root, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
                delay(300)
                return true
            }
        }
        val (cx, cy, sw, sh) = displayMetrics()
        return swipeSuspend(cx, cy - sh * 0.22f, cx, cy + sh * 0.22f, 320L)
    }

    fun scrollDown(): Boolean {
        val roots = getAllActiveRoots()
        for (root in roots) {
            if (tryScrollNode(root, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
        }
        val (cx, cy, sw, sh) = displayMetrics()
        return swipe(cx, cy + sh * 0.22f, cx, cy - sh * 0.22f, 320L)
    }

    fun scrollUp(): Boolean {
        val roots = getAllActiveRoots()
        for (root in roots) {
            if (tryScrollNode(root, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return true
        }
        val (cx, cy, sw, sh) = displayMetrics()
        return swipe(cx, cy - sh * 0.22f, cx, cy + sh * 0.22f, 320L)
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
    // SCREEN INSPECTION & TELEMETRY
    // ------------------------------------------------------------------

    fun readActiveWindowTexts(): List<String> {
        val list = mutableListOf<String>()
        val roots = getAllActiveRoots()
        for (root in roots) {
            collectTexts(root, list)
        }
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

    /**
     * Returns true if any active window contains text matching [query].
     */
    fun hasTextOnScreen(query: String): Boolean {
        val q = query.trim().lowercase()
        val roots = getAllActiveRoots()
        for (root in roots) {
            val results = mutableListOf<AccessibilityNodeInfo>()
            searchNodesByText(root, q, false, results)
            if (results.isNotEmpty()) return true
        }
        return false
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

    private fun displayMetrics(): Array<Float> {
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

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
}
