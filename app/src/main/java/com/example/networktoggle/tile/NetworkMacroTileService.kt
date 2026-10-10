package com.example.networktoggle.tile

import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.example.networktoggle.macro.MacroExecutionState
import com.example.networktoggle.macro.MacroManager
import com.example.networktoggle.macro.MacroType
import com.example.networktoggle.network.NetworkModeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NetworkMacroTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var networkModeManager: NetworkModeManager
    private lateinit var macroManager: MacroManager

    override fun onCreate() {
        super.onCreate()
        val appContext = applicationContext
        networkModeManager = NetworkModeManager(appContext)
        macroManager = MacroManager(appContext, networkModeManager)
    }

    override fun onStartListening() {
        super.onStartListening()
        refreshTile(isBusy = false)
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return

        // Set tile to switching state
        tile.state = Tile.STATE_UNAVAILABLE
        tile.subtitle = "Reading & Executing…"
        tile.updateTile()

        serviceScope.launch {
            val success = macroManager.executeMacro(MacroType.TURBO_5G_LOCK)
            refreshTile(isBusy = false, isCompleted = true)

            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    applicationContext,
                    if (success) "⚡ 5G Macro: Process inspected & 5G Ultra-Lock applied!"
                    else "5G Macro completed.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun refreshTile(isBusy: Boolean, isCompleted: Boolean = false) {
        val tile = qsTile ?: return
        tile.label = "5G Macro"
        tile.subtitle = when {
            isBusy -> "Running Macro…"
            isCompleted -> "5G Locked"
            else -> "Tap to Optimize"
        }
        tile.state = if (isBusy) Tile.STATE_UNAVAILABLE else Tile.STATE_ACTIVE
        tile.updateTile()
    }
}
