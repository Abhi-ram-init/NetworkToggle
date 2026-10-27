package com.example.networktoggle

import org.junit.Test
import org.junit.Assert.assertNotNull

class ShizukuClasspathTest {
    @Test
    fun testShizukuServiceClassAvailable() {
        val clazz = Class.forName("moe.shizuku.server.IShizukuService")
        assertNotNull(clazz)
        println("Found IShizukuService: " + clazz.name)
    }

    @Test
    fun testRemoteProcessClassAvailable() {
        val clazz = Class.forName("moe.shizuku.server.IRemoteProcess")
        assertNotNull(clazz)
        println("Found IRemoteProcess: " + clazz.name)
    }
}
