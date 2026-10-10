package com.example.networktoggle.ui.main

import com.example.networktoggle.network.NetworkMode
import org.junit.Assert.assertEquals
import org.junit.Test

class MainScreenViewModelTest {

    @Test
    fun networkMode_labels_are_correct() {
        assertEquals("5G", NetworkMode.FIVE_G.shortLabel())
        assertEquals("4G LTE", NetworkMode.FOUR_G.label())
        assertEquals("5G NR", NetworkMode.FIVE_G.label())
        assertEquals("5G/4G Auto", NetworkMode.AUTO.label())
    }

    @Test
    fun networkMode_next_toggles_correctly() {
        assertEquals(NetworkMode.FOUR_G, NetworkMode.FIVE_G.next())
        assertEquals(NetworkMode.FIVE_G, NetworkMode.FOUR_G.next())
        assertEquals(NetworkMode.FIVE_G, NetworkMode.AUTO.next())
    }

    @Test
    fun macroType_presets_are_configured() {
        val presets = com.example.networktoggle.macro.MacroType.entries
        assertEquals(3, presets.size)
        assertEquals("Turbo 5G Ultra-Lock", com.example.networktoggle.macro.MacroType.TURBO_5G_LOCK.displayName)
        assertEquals("Cell Tower Reseat", com.example.networktoggle.macro.MacroType.TOWER_REFRESH.displayName)
        assertEquals("Battery Saver 4G Eco", com.example.networktoggle.macro.MacroType.BATTERY_ECO_4G.displayName)
    }

    @Test
    fun macroState_transitions_work() {
        val idle = com.example.networktoggle.macro.MacroExecutionState.Idle
        val running = com.example.networktoggle.macro.MacroExecutionState.Running(
            com.example.networktoggle.macro.MacroType.TURBO_5G_LOCK,
            "Scanning telephony processes...",
            0.2f
        )
        val completed = com.example.networktoggle.macro.MacroExecutionState.Completed(
            com.example.networktoggle.macro.MacroType.TURBO_5G_LOCK,
            true,
            "5G Ultra-Lock active"
        )
        assertEquals(com.example.networktoggle.macro.MacroType.TURBO_5G_LOCK, running.type)
        assertEquals(true, completed.success)
    }
}
