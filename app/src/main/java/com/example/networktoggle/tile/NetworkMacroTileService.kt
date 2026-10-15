package com.example.networktoggle.tile

import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.example.networktoggle.macro.MacroExecutionState
import com.example.networktoggle.macro.MacroManager
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
        val primaryMacro = macroManager.savedMacros.value.firstOrNull()

        if (primaryMacro == null) {
            Toast.makeText(
                applicationContext,
                "⏺ No macros recorded. Open Network Toggle and tap 'Record Macro' first.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Set tile to running state
        tile.state = Tile.STATE_UNAVAILABLE
        tile.subtitle = "Running '${primaryMacro.name}'…"
        tile.updateTile()

        serviceScope.launch {
            val success = macroManager.executeMacro(primaryMacro)
            refreshTile(isBusy = false, isCompleted = true)

            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    applicationContext,
                    if (success) "✅ Macro '${primaryMacro.name}' completed!"
                    else "Macro execution finished.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun refreshTile(isBusy: Boolean, isCompleted: Boolean = false) {
        val tile = qsTile ?: return
        val primaryMacro = macroManager.savedMacros.value.firstOrNull()
        tile.label = primaryMacro?.name ?: "Network Macro"
        tile.subtitle = when {
            isBusy -> "Running Macro…"
            isCompleted -> "Completed"
            primaryMacro != null -> "Tap to Run"
            else -> "Record in App"
        }
        tile.state = if (isBusy) Tile.STATE_UNAVAILABLE else Tile.STATE_ACTIVE
        tile.updateTile()
    }
}
