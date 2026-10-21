package com.example.networktoggle.bridge

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.util.Log

class PrivilegedBridgeProvider : ContentProvider() {

    companion object {
        private const val TAG = "PrivilegedBridgeProvider"
        const val AUTHORITY = "com.example.networktoggle.bridge.provider"
        const val ACTION_REGISTER = "register_service"
        const val EXTRA_BINDER = "privileged_binder"
        const val EXTRA_TOKEN = "auth_token"
        const val EXTRA_PID = "server_pid"
        const val EXTRA_UID = "server_uid"
    }

    override fun onCreate(): Boolean {
        Log.i(TAG, "PrivilegedBridgeProvider initialized")
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (extras == null) return null

        if (method == ACTION_REGISTER) {
            val ctx = context ?: return null
            val token = extras.getString(EXTRA_TOKEN)

            // Strict security authorization check
            if (!PrivilegedAuthStore.validateToken(ctx, token)) {
                Log.w(TAG, "Rejected unauthorized binder registration attempt: token mismatch")
                return Bundle().apply {
                    putBoolean("success", false)
                    putString("error", "Unauthorized: invalid security token")
                }
            }

            val binder = extras.getBinder(EXTRA_BINDER)
            if (binder != null) {
                val pid = extras.getInt(EXTRA_PID, -1)
                val uid = extras.getInt(EXTRA_UID, -1)
                val service = IPrivilegedService.Stub.asInterface(binder)

                PrivilegedBridgeManager.onServiceConnected(service, uid, pid, token ?: "")
                Log.i(TAG, "Privileged backend service connected! (UID: $uid, PID: $pid)")

                return Bundle().apply {
                    putBoolean("success", true)
                }
            }
        }

        return super.call(method, arg, extras)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
