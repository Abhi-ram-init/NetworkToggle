package com.example.networktoggle.bridge

import android.content.Context
import android.content.SharedPreferences
import java.security.SecureRandom
import java.util.UUID

object PrivilegedAuthStore {
    private const val PREFS_NAME = "privileged_bridge_auth"
    private const val KEY_SECURITY_TOKEN = "bridge_security_token"

    private var cachedToken: String? = null

    @Synchronized
    fun getSecurityToken(context: Context): String {
        cachedToken?.let { return it }

        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var token = prefs.getString(KEY_SECURITY_TOKEN, null)
        if (token.isNullOrBlank()) {
            val randomBytes = ByteArray(16)
            SecureRandom().nextBytes(randomBytes)
            val randomHex = randomBytes.joinToString("") { "%02x".format(it) }
            token = "${UUID.randomUUID()}-$randomHex"
            prefs.edit().putString(KEY_SECURITY_TOKEN, token).apply()
        }
        cachedToken = token
        return token
    }

    @Synchronized
    fun validateToken(context: Context, token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        val expected = getSecurityToken(context)
        return expected == token
    }

    @Synchronized
    fun regenerateToken(context: Context): String {
        val randomBytes = ByteArray(16)
        SecureRandom().nextBytes(randomBytes)
        val randomHex = randomBytes.joinToString("") { "%02x".format(it) }
        val newToken = "${UUID.randomUUID()}-$randomHex"
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SECURITY_TOKEN, newToken).apply()
        cachedToken = newToken
        return newToken
    }
}
