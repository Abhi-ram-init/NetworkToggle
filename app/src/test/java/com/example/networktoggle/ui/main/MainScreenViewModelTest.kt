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
}
