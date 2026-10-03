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

class CompactToggleWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE_COMPACT = "com.example.networktoggle.ACTION_TOGGLE_COMPACT"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, CompactToggleWidget::class.java))
            val intent = Intent(context, CompactToggleWidget::class.java).apply {
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
        if (intent.action == ACTION_TOGGLE_COMPACT) {
            val manager = NetworkModeManager(context.applicationContext)
            val result = manager.toggleNetworkMode()
            if (result is ToggleResult.RequiresAction) {
                manager.openRadioInfo(context)
            }
            NetworkToggleWidget.updateAllWidgets(context)
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

        val views = RemoteViews(context.packageName, R.layout.widget_compact_toggle)

        views.setTextViewText(R.id.compact_widget_label, mode.shortLabel())
        views.setTextColor(R.id.compact_widget_label, if (is5G) 0xFF00F5D4.toInt() else 0xFF8B5CF6.toInt())

        views.setInt(
            R.id.compact_widget_root,
            "setBackgroundResource",
            if (is5G) R.drawable.widget_compact_bg_5g else R.drawable.widget_compact_bg_4g
        )

        val toggleIntent = Intent(context, CompactToggleWidget::class.java).apply {
            action = ACTION_TOGGLE_COMPACT
        }
        val pendingToggle = PendingIntent.getBroadcast(
            context,
            widgetId,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.compact_widget_root, pendingToggle)

        appWidgetManager.updateAppWidget(widgetId, views)
    }
}
