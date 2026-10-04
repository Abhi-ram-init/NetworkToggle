package com.example.networktoggle.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

class AutoScrollService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoScrollService"
        private const val MAX_SCROLL_ATTEMPTS = 9
        private const val SESSION_TIMEOUT_MS = 15_000L
        private const val SCROLL_DEBOUNCE_MS = 400L

        private val isSessionActive = AtomicBoolean(false)
        private var sessionStartTime = 0L
        private var currentScrollCount = 0
        private var lastActionTimestamp = 0L
        private var serviceInstance: WeakReference<AutoScrollService>? = null

        private val PRIMARY_KEYWORDS = listOf(
            "set preferred network type",
            "preferred network type",
            "preferred network mode",
            "network mode",
            "preferred network",
            "preferred type",
            "nr/lte",
            "nr / lte",
            "lte/nr",
            "nr only",
            "lte only",
            "5g network mode"
        )

        private val SECONDARY_KEYWORDS = listOf(
            "preferred",
            "5g",
            "4g lte",
            "lte"
        )

        fun isServiceRunning(): Boolean = serviceInstance?.get() != null

        fun isAccessibilityEnabled(context: Context): Boolean {
            return try {
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false
                val myService = "${context.packageName}/${AutoScrollService::class.java.name}"
                val simpleName = AutoScrollService::class.java.simpleName
                enabledServices.contains(myService, ignoreCase = true) ||
                    enabledServices.contains(simpleName, ignoreCase = true)
            } catch (_: Exception) {
                false
            }
        }

        fun openAccessibilitySettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open accessibility settings: ${e.message}")
            }
        }

        fun startAutoScrollSession(context: Context) {
            sessionStartTime = System.currentTimeMillis()
            currentScrollCount = 0
            lastActionTimestamp = 0L
            isSessionActive.set(true)
            Log.d(TAG, "Auto-scroll session activated")
        }

        fun stopSession() {
            isSessionActive.set(false)
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isPerformingScroll = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInstance = WeakReference(this)
        Log.d(TAG, "AutoScrollService connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceInstance = null
    }

    override fun onInterrupt() {
        isSessionActive.set(false)
        isPerformingScroll = false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isSessionActive.get()) return

        // Check session timeout
        if (System.currentTimeMillis() - sessionStartTime > SESSION_TIMEOUT_MS) {
            Log.d(TAG, "Session timed out")
            isSessionActive.set(false)
            return
        }

        val packageName = event.packageName?.toString() ?: ""
        // Only target settings, telephony, or phone packages
        if (!isRelevantPackage(packageName)) return

        val now = System.currentTimeMillis()
        if (now - lastActionTimestamp < SCROLL_DEBOUNCE_MS || isPerformingScroll) {
            return
        }

        processActiveWindow()
    }

    private fun isRelevantPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("settings") ||
            lower.contains("phone") ||
            lower.contains("telephony") ||
            lower.contains("carrier")
    }

    private fun processActiveWindow() {
        val root = rootInActiveWindow ?: return

        // 1. Search for matching toggle options
        val targetNode = findTargetNode(root)
        if (targetNode != null) {
            onTargetFound(targetNode)
            return
        }

        // 2. If target not visible yet, scroll down towards the option or end
        if (currentScrollCount < MAX_SCROLL_ATTEMPTS) {
            performScrollDown(root)
        } else {
            // Reached maximum scroll attempts (end of screen)
            finishSession("Reached end of settings")
        }
    }

    private fun findTargetNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Priority 1: Match primary exact phrases
        for (kw in PRIMARY_KEYWORDS) {
            val nodes = root.findAccessibilityNodeInfosByText(kw)
            if (!nodes.isNullOrEmpty()) {
                val best = nodes.firstOrNull { isValidTarget(it) } ?: nodes[0]
                return best
            }
        }

        // Priority 2: Recursive scan for compound text or view IDs
        return scanRecursive(root)
    }

    private fun scanRecursive(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        for (kw in PRIMARY_KEYWORDS) {
            if (text.contains(kw) || desc.contains(kw) || viewId.contains(kw.replace(" ", "_"))) {
                return node
            }
        }

        // Check if node is a dropdown / spinner in RadioInfo (e.g. preferredNetworkType)
        if (viewId.contains("preferrednetworktype") || viewId.contains("preferred_network")) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = scanRecursive(child)
            if (found != null) return found
        }
        return null
    }

    private fun isValidTarget(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        return PRIMARY_KEYWORDS.any { text.contains(it) || desc.contains(it) }
    }

    private fun onTargetFound(targetNode: AccessibilityNodeInfo) {
        lastActionTimestamp = System.currentTimeMillis()
        isSessionActive.set(false)

        Log.d(TAG, "Target node found: ${targetNode.text}")

        // Bring into view
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            targetNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        }
        targetNode.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)

        // If target is in RadioInfo and is a label, check if adjacent child/sibling is the spinner
        val parent = targetNode.parent
        if (parent != null) {
            for (i in 0 until parent.childCount) {
                val sibling = parent.getChild(i) ?: continue
                if (sibling.className?.contains("Spinner") == true) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        sibling.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                    }
                    sibling.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                    break
                }
            }
        }

        mainHandler.post {
            Toast.makeText(applicationContext, "✓ Scrolled to Network Toggle Option", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performScrollDown(root: AccessibilityNodeInfo) {
        isPerformingScroll = true
        lastActionTimestamp = System.currentTimeMillis()
        currentScrollCount++

        Log.d(TAG, "Scrolling down attempt $currentScrollCount / $MAX_SCROLL_ATTEMPTS")

        // Strategy A: Find scrollable container and invoke ACTION_SCROLL_FORWARD
        val scrollableNode = findScrollableNode(root)
        var scrolledViaAction = false

        if (scrollableNode != null) {
            scrolledViaAction = scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        }

        if (!scrolledViaAction && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Strategy B: Universal swipe gesture (swipes up from lower 75% to upper 25% of screen)
            dispatchSwipeUpGesture()
        }

        // Schedule next check after scroll settle animation
        mainHandler.postDelayed({
            isPerformingScroll = false
            if (isSessionActive.get()) {
                processActiveWindow()
            }
        }, 400)
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollableNode(child)
            if (found != null) return found
        }
        return null
    }

    private fun dispatchSwipeUpGesture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels.toFloat()
        val height = displayMetrics.heightPixels.toFloat()

        val startX = width / 2f
        val startY = height * 0.72f
        val endX = width / 2f
        val endY = height * 0.28f

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 220))
            .build()

        dispatchGesture(gesture, null, null)
    }

    private fun finishSession(reason: String) {
        isSessionActive.set(false)
        isPerformingScroll = false
        Log.d(TAG, "Session finished: $reason")
    }
}
