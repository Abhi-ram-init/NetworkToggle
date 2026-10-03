package com.example.networktoggle.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.networktoggle.network.NetworkMode
import com.example.networktoggle.network.NetworkModeManager
import com.example.networktoggle.network.ToggleResult

class NetworkToggleTileService : TileService() {

    private lateinit var networkModeManager: NetworkModeManager

    override fun onCreate() {
        super.onCreate()
        networkModeManager = NetworkModeManager(applicationContext)
    }

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        qsTile?.state = Tile.STATE_UNAVAILABLE
        qsTile?.updateTile()

        val result = networkModeManager.toggleNetworkMode()
        refreshTile()

        when (result) {
            is ToggleResult.DirectSuccess -> {
                // Switched directly via privileged/root/ADB
            }
            is ToggleResult.RequiresAction -> {
                // Open testing menu or settings so user can switch on non-rooted Android
                val intent = Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val pendingIntent = PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    startActivityAndCollapse(pendingIntent)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(intent)
                }
            }
        }
    }

    private fun refreshTile() {
        val mode = networkModeManager.getSavedMode()
        val tile = qsTile ?: return
        tile.label = if (mode == NetworkMode.FIVE_G) "5G Mode" else "4G LTE"
        tile.subtitle = if (mode == NetworkMode.FIVE_G) "Tap for 4G" else "Tap for 5G"
        tile.state = Tile.STATE_ACTIVE
        tile.updateTile()
    }
}
