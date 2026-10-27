package com.example.networktoggle.terminal

import android.util.Log
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * Native ADB Socket Client adapted from Shizuku Manager (moe.shizuku.manager.adb.AdbClient).
 * Communicates directly with the adbd daemon via ADB TCP/IP protocol (default localhost:5555).
 */
object AdbSocketProtocol {
    const val A_SYNC = 0x434e5953
    const val A_CNXN = 0x4e584e43
    const val A_AUTH = 0x48545541
    const val A_OPEN = 0x4e45504f
    const val A_OKAY = 0x59414b4f
    const val A_CLSE = 0x45534c43
    const val A_WRTE = 0x45545257

    const val A_VERSION = 0x01000000
    const val A_MAXDATA = 4096

    fun crc32(bytes: ByteArray?): Int {
        if (bytes == null) return 0
        val crc = CRC32()
        crc.update(bytes)
        return crc.value.toInt()
    }
}

class AdbSocketClient(
    private val host: String = "127.0.0.1",
    private val port: Int = 5555,
    private val timeoutMs: Int = 4000
) : Closeable {
    private val tag = "AdbSocketClient"
    private var socket: Socket? = null
    private var inStream: DataInputStream? = null
    private var outStream: DataOutputStream? = null

    fun connect(): Boolean {
        return try {
            val s = Socket()
            s.tcpNoDelay = true
            s.connect(InetSocketAddress(host, port), timeoutMs)
            s.soTimeout = timeoutMs
            socket = s
            inStream = DataInputStream(s.getInputStream())
            outStream = DataOutputStream(s.getOutputStream())

            // Send A_CNXN packet
            writePacket(AdbSocketProtocol.A_CNXN, AdbSocketProtocol.A_VERSION, AdbSocketProtocol.A_MAXDATA, "host::")
            val resp = readPacket()
            resp.command == AdbSocketProtocol.A_CNXN || resp.command == AdbSocketProtocol.A_AUTH
        } catch (e: Exception) {
            Log.d(tag, "connect failed: ${e.message}")
            close()
            false
        }
    }

    fun execCommand(command: String, onLine: (String) -> Unit): Int {
        return try {
            val localId = 1
            writePacket(AdbSocketProtocol.A_OPEN, localId, 0, "shell:$command")
            var resp = readPacket()
            if (resp.command == AdbSocketProtocol.A_OKAY) {
                while (true) {
                    resp = readPacket()
                    val remoteId = resp.arg0
                    if (resp.command == AdbSocketProtocol.A_WRTE) {
                        if (resp.data != null && resp.data.isNotEmpty()) {
                            val text = String(resp.data, Charsets.UTF_8)
                            text.lines().forEach { line ->
                                if (line.isNotEmpty()) onLine(line)
                            }
                        }
                        writePacket(AdbSocketProtocol.A_OKAY, localId, remoteId)
                    } else if (resp.command == AdbSocketProtocol.A_CLSE) {
                        writePacket(AdbSocketProtocol.A_CLSE, localId, remoteId)
                        break
                    } else {
                        break
                    }
                }
                0
            } else {
                -1
            }
        } catch (e: Exception) {
            Log.d(tag, "execCommand failed: ${e.message}")
            -1
        }
    }

    private fun writePacket(command: Int, arg0: Int, arg1: Int, payloadStr: String) {
        val bytes = "$payloadStr\u0000".toByteArray(Charsets.UTF_8)
        writePacket(command, arg0, arg1, bytes)
    }

    private fun writePacket(command: Int, arg0: Int, arg1: Int, data: ByteArray? = null) {
        val dataLen = data?.size ?: 0
        val crc = AdbSocketProtocol.crc32(data)
        val magic = (command.toLong() xor 0xFFFFFFFFL).toInt()

        val buf = ByteBuffer.allocate(24 + dataLen).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(command)
        buf.putInt(arg0)
        buf.putInt(arg1)
        buf.putInt(dataLen)
        buf.putInt(crc)
        buf.putInt(magic)
        if (data != null) {
            buf.put(data)
        }
        outStream?.write(buf.array())
        outStream?.flush()
    }

    private fun readPacket(): Packet {
        val inS = inStream ?: throw IllegalStateException("Socket not connected")
        val header = ByteArray(24)
        inS.readFully(header, 0, 24)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = buf.int
        val arg0 = buf.int
        val arg1 = buf.int
        val dataLen = buf.int
        val crc = buf.int
        val magic = buf.int
        val data = if (dataLen in 1..65536) {
            val d = ByteArray(dataLen)
            inS.readFully(d, 0, dataLen)
            d
        } else {
            null
        }
        return Packet(command, arg0, arg1, dataLen, crc, magic, data)
    }

    override fun close() {
        try { inStream?.close() } catch (_: Exception) {}
        try { outStream?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        inStream = null
        outStream = null
        socket = null
    }

    private data class Packet(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val dataLen: Int,
        val crc: Int,
        val magic: Int,
        val data: ByteArray?
    )
}
