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
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Standalone AccessibilityService enabling touch simulation, clicks, swipes,
 * text-targeted node clicking, and automated screen navigation without root.
 */
class MacroAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceActive.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Can monitor window changes and text changes during macro recording
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val text = event.text.joinToString(" ")
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

    /**
     * Dispatches a simulated tap/click gesture at the specified screen coordinates without root.
     */
    fun clickAt(x: Float, y: Float, durationMs: Long = 50L, callback: ((Boolean) -> Unit)? = null): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            callback?.invoke(false)
            return false
        }
        val clickPath = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, durationMs)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                callback?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                callback?.invoke(false)
            }
        }, null)
    }

    /**
     * Dispatches a simulated swipe gesture from start coordinates to end coordinates without root.
     */
    fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300L,
        callback: ((Boolean) -> Unit)? = null
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            callback?.invoke(false)
            return false
        }
        val swipePath = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(swipePath, 0, durationMs)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                callback?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                callback?.invoke(false)
            }
        }, null)
    }

    /**
     * Finds a UI element displaying the given text (case-insensitive) and clicks it.
     * Falls back to tapping its screen center coordinates if node click action fails.
     */
    fun findAndClickText(text: String, exactMatch: Boolean = false): Boolean {
        val root = rootInActiveWindow ?: return false
        val foundNodes = mutableListOf<AccessibilityNodeInfo>()
        searchNodesByText(root, text.trim().lowercase(), exactMatch, foundNodes)

        for (node in foundNodes) {
            // Try performing ACTION_CLICK on the node or its clickable ancestors
            var target: AccessibilityNodeInfo? = node
            while (target != null) {
                if (target.isClickable) {
                    if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        return true
                    }
                }
                target = target.parent
            }

            // Fallback: Click center coordinates of node bounding box
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
     * Recursively traverses nodes looking for matching text.
     */
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

        if (matches) {
            results.add(node)
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                searchNodesByText(child, queryLower, exactMatch, results)
            }
        }
    }

    /**
     * Performs a scroll down gesture on the screen (swipe up).
     */
    fun scrollDown(): Boolean {
        val root = rootInActiveWindow
        if (root != null && tryScrollNode(root, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            return true
        }
        // Fallback swipe: 540, 1500 to 540, 600
        return swipe(540f, 1500f, 540f, 600f, 350L)
    }

    /**
     * Performs a scroll up gesture on the screen (swipe down).
     */
    fun scrollUp(): Boolean {
        val root = rootInActiveWindow
        if (root != null && tryScrollNode(root, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
            return true
        }
        return swipe(540f, 600f, 540f, 1500f, 350L)
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

    /**
     * Reads all visible text on the active window.
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

    fun pressBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun pressHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun openNotifications(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    fun openQuickSettings(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
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

        /**
         * Checks whether the user has enabled this Accessibility Service in Android Settings.
         */
        fun isAccessibilityEnabled(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
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
