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
}
