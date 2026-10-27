package com.example.networktoggle.terminal

import android.os.ParcelFileDescriptor
import moe.shizuku.server.IRemoteProcess
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Direct typed Process wrapper for Shizuku's IRemoteProcess AIDL interface.
 * Implements standard java.lang.Process by streaming through Binder ParcelFileDescriptors,
 * avoiding any reflection overhead.
 */
class ShizukuAidlProcess(private val remote: IRemoteProcess) : Process() {
    private var os: OutputStream? = null
    private var inputStreamObj: InputStream? = null
    private var es: InputStream? = null

    override fun getOutputStream(): OutputStream {
        if (os == null) {
            val pfd = try { remote.outputStream } catch (_: Exception) { null }
            os = if (pfd != null) ParcelFileDescriptor.AutoCloseOutputStream(pfd)
                 else ByteArrayOutputStream()
        }
        return os!!
    }

    override fun getInputStream(): InputStream {
        if (inputStreamObj == null) {
            val pfd = try { remote.inputStream } catch (_: Exception) { null }
            inputStreamObj = if (pfd != null) ParcelFileDescriptor.AutoCloseInputStream(pfd)
                             else ByteArrayInputStream(ByteArray(0))
        }
        return inputStreamObj!!
    }

    override fun getErrorStream(): InputStream {
        if (es == null) {
            val pfd = try { remote.errorStream } catch (_: Exception) { null }
            es = if (pfd != null) ParcelFileDescriptor.AutoCloseInputStream(pfd)
                 else ByteArrayInputStream(ByteArray(0))
        }
        return es!!
    }

    override fun waitFor(): Int {
        return try {
            remote.waitFor()
        } catch (_: Exception) {
            -1
        }
    }

    override fun exitValue(): Int {
        return try {
            remote.exitValue()
        } catch (_: Exception) {
            -1
        }
    }

    override fun destroy() {
        try {
            remote.destroy()
        } catch (_: Exception) {}
    }
}
