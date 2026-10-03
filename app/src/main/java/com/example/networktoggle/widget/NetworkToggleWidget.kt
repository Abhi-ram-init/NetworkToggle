package com.example.networktoggle.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.networktoggle.R
import com.example.networktoggle.network.NetworkMode
import com.example.networktoggle.network.NetworkModeManager
import com.example.networktoggle.network.ToggleResult

class NetworkToggleWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE = "com.example.networktoggle.ACTION_TOGGLE_NETWORK"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            
            // Update 2x1 Standard widgets
            val stdIds = appWidgetManager.getAppWidgetIds(ComponentName(context, NetworkToggleWidget::class.java))
            val stdIntent = Intent(context, NetworkToggleWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, stdIds)
            }
            context.sendBroadcast(stdIntent)

            // Update 1x1 Compact widgets
            CompactToggleWidget.updateAllWidgets(context)

            // Update 4x2 Dashboard widgets
            DashboardToggleWidget.updateAllWidgets(context)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
            val manager = NetworkModeManager(context.applicationContext)
            val result = manager.toggleNetworkMode()
            if (result is ToggleResult.RequiresAction) {
                manager.openRadioInfo(context)
            }
            updateAllWidgets(context)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int,
    ) {
        val manager = NetworkModeManager(context.applicationContext)
        val mode = manager.getSavedMode()
        val is5G = mode == NetworkMode.FIVE_G
        val liveNet = manager.detectCurrentNetwork()

        val views = RemoteViews(context.packageName, R.layout.widget_network_toggle)

        // Labels
        views.setTextViewText(R.id.widget_mode_label, mode.shortLabel())
        val statusText = if (liveNet.displayName.isNotEmpty() && !liveNet.displayName.contains("Detecting")) {
            liveNet.displayName
        } else {
            if (is5G) "5G NR Connected" else "4G LTE Connected"
        }
        views.setTextViewText(R.id.widget_status_text, statusText)
        views.setTextViewText(
            R.id.widget_sub_text,
            if (is5G) "Tap to switch to 4G LTE" else "Tap to switch to 5G NR"
        )

        // Background styling
        views.setInt(
            R.id.widget_root,
            "setBackgroundResource",
            if (is5G) R.drawable.widget_bg_5g else R.drawable.widget_bg_4g
        )
        views.setTextColor(R.id.widget_mode_label, if (is5G) 0xFF00F5D4.toInt() else 0xFF8B5CF6.toInt())
        views.setTextColor(R.id.widget_toggle_btn, if (is5G) 0xFF00F5D4.toInt() else 0xFF8B5CF6.toInt())

        // Toggle pending intent
        val toggleIntent = Intent(context, NetworkToggleWidget::class.java).apply {
            action = ACTION_TOGGLE
        }
        val pendingToggle = PendingIntent.getBroadcast(
            context,
            widgetId,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingToggle)
        views.setOnClickPendingIntent(R.id.widget_toggle_btn, pendingToggle)

        appWidgetManager.updateAppWidget(widgetId, views)
    }
}
