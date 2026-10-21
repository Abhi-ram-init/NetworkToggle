package com.example.networktoggle.ui.main

import com.example.networktoggle.macro.MacroActionType
import com.example.networktoggle.macro.MacroExecutionState
import com.example.networktoggle.macro.MacroStep
import com.example.networktoggle.macro.RecordedMacro
import com.example.networktoggle.network.NetworkMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun macroActionType_entries_are_configured() {
        val actions = MacroActionType.entries
        assertTrue(actions.contains(MacroActionType.SET_5G))
        assertTrue(actions.contains(MacroActionType.SET_4G))
        assertTrue(actions.contains(MacroActionType.SET_AUTO))
        assertTrue(actions.contains(MacroActionType.WAIT_DELAY))
        assertTrue(actions.contains(MacroActionType.CYCLE_DATA))
        assertTrue(actions.contains(MacroActionType.LAUNCH_RADIO_INFO))
        assertTrue(actions.contains(MacroActionType.GO_HOME))
        assertTrue(actions.contains(MacroActionType.SCREEN_TAP))
        assertTrue(actions.contains(MacroActionType.SCREEN_SWIPE))
        assertTrue(actions.contains(MacroActionType.CLICK_TEXT))
        assertTrue(actions.contains(MacroActionType.FIND_AND_CLICK_ANY_OF))
        assertTrue(actions.contains(MacroActionType.SCROLL_DOWN))
        assertTrue(actions.contains(MacroActionType.OPEN_NETWORK_SETTINGS))
        assertTrue(actions.contains(MacroActionType.READ_SIM_SETTINGS))
        assertTrue(actions.contains(MacroActionType.PROMPT_ADD_TILE))
        assertTrue(actions.contains(MacroActionType.EXIT_APP))
    }

    @Test
    fun blockCatalog_contains_items_across_categories() {
        val items = com.example.networktoggle.macro.MacroBlockCatalog.items
        assertTrue(items.isNotEmpty())
        assertTrue(items.any { it.category == com.example.networktoggle.macro.BlockCategory.TRIGGERS })
        assertTrue(items.any { it.category == com.example.networktoggle.macro.BlockCategory.CONNECTIVITY })
        assertTrue(items.any { it.category == com.example.networktoggle.macro.BlockCategory.GESTURES })
        assertTrue(items.any { it.category == com.example.networktoggle.macro.BlockCategory.NAVIGATION })
        assertTrue(items.any { it.category == com.example.networktoggle.macro.BlockCategory.FLOW })
        assertTrue(items.any { it.category == com.example.networktoggle.macro.BlockCategory.SYSTEM })
    }

    @Test
    fun recordedMacro_serialization_and_steps_work() {
        val step1 = MacroStep(actionType = MacroActionType.SET_5G, label = "Switch to 5G NR")
        val step2 = MacroStep(actionType = MacroActionType.WAIT_DELAY, label = "Wait 1s", delaySeconds = 1)
        val step3 = MacroStep(actionType = MacroActionType.CYCLE_DATA, label = "Cycle Data")

        val macro = RecordedMacro(
            name = "Test 5G Optimizer",
            steps = listOf(step1, step2, step3)
        )

        assertEquals("Test 5G Optimizer", macro.name)
        assertEquals(3, macro.steps.size)
        assertEquals(MacroActionType.SET_5G, macro.steps[0].actionType)
        assertEquals(MacroActionType.WAIT_DELAY, macro.steps[1].actionType)
        assertEquals(MacroActionType.CYCLE_DATA, macro.steps[2].actionType)

        val serialized = macro.toSerialized()
        val restored = RecordedMacro.fromSerialized(serialized)

        org.junit.Assert.assertNotNull(restored)
        assertEquals(macro.id, restored!!.id)
        assertEquals(macro.name, restored.name)
        assertEquals(3, restored.steps.size)
        assertEquals(macro.steps[0].label, restored.steps[0].label)
    }

    @Test
    fun macroState_transitions_work() {
        val idle = MacroExecutionState.Idle
        val running = MacroExecutionState.Running(
            macroName = "My 5G Macro",
            stepLabel = "Switch to 5G NR",
            currentStepIndex = 1,
            totalSteps = 2,
            progress = 0.5f
        )
        val completed = MacroExecutionState.Completed(
            macroName = "My 5G Macro",
            success = true,
            summary = "Completed 2 steps"
        )
        assertEquals("My 5G Macro", running.macroName)
        assertEquals(1, running.currentStepIndex)
        assertEquals(true, completed.success)
    }

    @Test
    fun navigationTabs_include_settings_and_terminal() {
        val tabs = NavigationTab.entries
        assertEquals(4, tabs.size)
        assertTrue(tabs.contains(NavigationTab.SWITCHER))
        assertTrue(tabs.contains(NavigationTab.MACRO))
        assertTrue(tabs.contains(NavigationTab.TERMINAL))
        assertTrue(tabs.contains(NavigationTab.SETTINGS))
    }

    @Test
    fun uiState_supports_popup_message_for_failed_toggles() {
        var state = MainScreenUiState()
        assertEquals(null, state.popupMessage)

        val failMessage = "Ã¢Å¡Â Ã¯Â¸Â Network toggle could not complete automatically. Staying in app."
        state = state.copy(popupMessage = failMessage)
        assertEquals(failMessage, state.popupMessage)

        // Dismiss popup
        state = state.copy(popupMessage = null)
        assertEquals(null, state.popupMessage)
    }

    @Test
    fun macro_completed_with_warning_holds_failure_summary() {
        val completedFailed = MacroExecutionState.Completed(
            macroName = "Auto-Toggle Jio to 4G",
            success = false,
            summary = "Finished with warning: Option 'Preferred network type' not found on screen"
        )
        assertEquals(false, completedFailed.success)
        assertTrue(completedFailed.summary.contains("warning"))
    }

    @Test
    fun bridgeConnectionState_transitions_work() {
        val disconnected = com.example.networktoggle.bridge.BridgeConnectionState.Disconnected
        val shellConnected = com.example.networktoggle.bridge.BridgeConnectionState.Connected(
            uid = 2000,
            pid = 8842,
            isRoot = false,
            isShell = true
        )
        val rootConnected = com.example.networktoggle.bridge.BridgeConnectionState.Connected(
            uid = 0,
            pid = 9101,
            isRoot = true,
            isShell = false
        )

        assertEquals(2000, shellConnected.uid)
        assertEquals(8842, shellConnected.pid)
        assertTrue(shellConnected.isShell)
        org.junit.Assert.assertFalse(shellConnected.isRoot)

        assertEquals(0, rootConnected.uid)
        assertEquals(9101, rootConnected.pid)
        assertTrue(rootConnected.isRoot)
        org.junit.Assert.assertFalse(rootConnected.isShell)
    }

    @Test
    fun uiState_tracks_bridgeState() {
        val state = MainScreenUiState()
        assertEquals(com.example.networktoggle.bridge.BridgeConnectionState.Disconnected, state.bridgeState)

        val updated = state.copy(
            bridgeState = com.example.networktoggle.bridge.BridgeConnectionState.Connected(
                uid = 2000,
                pid = 1234,
                isRoot = false,
                isShell = true
            )
        )
        assertTrue(updated.bridgeState is com.example.networktoggle.bridge.BridgeConnectionState.Connected)
        assertEquals(2000, (updated.bridgeState as com.example.networktoggle.bridge.BridgeConnectionState.Connected).uid)
    }
}
