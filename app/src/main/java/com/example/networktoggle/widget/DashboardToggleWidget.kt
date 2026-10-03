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

class DashboardToggleWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_SET_5G = "com.example.networktoggle.ACTION_DASH_5G"
        const val ACTION_SET_AUTO = "com.example.networktoggle.ACTION_DASH_AUTO"
        const val ACTION_SET_4G = "com.example.networktoggle.ACTION_DASH_4G"
        const val ACTION_FORCE_MENU = "com.example.networktoggle.ACTION_DASH_FORCE_MENU"
        const val ACTION_SETTINGS = "com.example.networktoggle.ACTION_DASH_SETTINGS"
        const val ACTION_REFRESH = "com.example.networktoggle.ACTION_DASH_REFRESH"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, DashboardToggleWidget::class.java))
            val intent = Intent(context, DashboardToggleWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
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
        val manager = NetworkModeManager(context.applicationContext)

        when (intent.action) {
            ACTION_SET_5G -> {
                val res = manager.tryDirectSwitch(NetworkMode.FIVE_G)
                if (res is ToggleResult.RequiresAction) manager.openRadioInfo(context)
                manager.saveMode(NetworkMode.FIVE_G)
                NetworkToggleWidget.updateAllWidgets(context)
            }
            ACTION_SET_AUTO -> {
                val res = manager.tryDirectSwitch(NetworkMode.AUTO)
                if (res is ToggleResult.RequiresAction) manager.openRadioInfo(context)
                manager.saveMode(NetworkMode.AUTO)
                NetworkToggleWidget.updateAllWidgets(context)
            }
            ACTION_SET_4G -> {
                val res = manager.tryDirectSwitch(NetworkMode.FOUR_G)
                if (res is ToggleResult.RequiresAction) manager.openRadioInfo(context)
                manager.saveMode(NetworkMode.FOUR_G)
                NetworkToggleWidget.updateAllWidgets(context)
            }
            ACTION_FORCE_MENU -> {
                manager.openRadioInfo(context)
            }
            ACTION_SETTINGS -> {
                manager.openMobileNetworkSettings(context)
            }
            ACTION_REFRESH -> {
                manager.refreshNetworkState()
                NetworkToggleWidget.updateAllWidgets(context)
            }
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

        val views = RemoteViews(context.packageName, R.layout.widget_dashboard_toggle)

        // Live status
        views.setTextViewText(R.id.dash_widget_live_status, "LIVE: ${liveNet.displayName.uppercase()}")

        // Highlight selected mode button
        views.setInt(
            R.id.dash_btn_5g,
            "setBackgroundResource",
            if (mode == NetworkMode.FIVE_G) R.drawable.widget_chip_active else R.drawable.widget_chip_inactive
        )
        views.setTextColor(R.id.dash_btn_5g, if (mode == NetworkMode.FIVE_G) 0xFF00F5D4.toInt() else 0xFFFFFFFF.toInt())

        views.setInt(
            R.id.dash_btn_auto,
            "setBackgroundResource",
            if (mode == NetworkMode.AUTO) R.drawable.widget_chip_active else R.drawable.widget_chip_inactive
        )
        views.setTextColor(R.id.dash_btn_auto, if (mode == NetworkMode.AUTO) 0xFF00F5D4.toInt() else 0xFFFFFFFF.toInt())

        views.setInt(
            R.id.dash_btn_4g,
            "setBackgroundResource",
            if (mode == NetworkMode.FOUR_G) R.drawable.widget_chip_active else R.drawable.widget_chip_inactive
        )
        views.setTextColor(R.id.dash_btn_4g, if (mode == NetworkMode.FOUR_G) 0xFF00F5D4.toInt() else 0xFFFFFFFF.toInt())

        // Widget background tint
        views.setInt(
            R.id.dash_widget_root,
            "setBackgroundResource",
            if (is5G) R.drawable.widget_bg_5g else R.drawable.widget_bg_4g
        )

        // Pending Intents for each button
        views.setOnClickPendingIntent(R.id.dash_btn_5g, getActionPending(context, widgetId, ACTION_SET_5G))
        views.setOnClickPendingIntent(R.id.dash_btn_auto, getActionPending(context, widgetId, ACTION_SET_AUTO))
        views.setOnClickPendingIntent(R.id.dash_btn_4g, getActionPending(context, widgetId, ACTION_SET_4G))
        views.setOnClickPendingIntent(R.id.dash_btn_force_menu, getActionPending(context, widgetId, ACTION_FORCE_MENU))
        views.setOnClickPendingIntent(R.id.dash_btn_settings, getActionPending(context, widgetId, ACTION_SETTINGS))
        views.setOnClickPendingIntent(R.id.dash_btn_refresh, getActionPending(context, widgetId, ACTION_REFRESH))

        appWidgetManager.updateAppWidget(widgetId, views)
    }

    private fun getActionPending(context: Context, widgetId: Int, action: String): PendingIntent {
        val intent = Intent(context, DashboardToggleWidget::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            widgetId xor action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
