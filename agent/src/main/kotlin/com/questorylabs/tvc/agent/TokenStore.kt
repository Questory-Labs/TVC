package com.questorylabs.tvc.agent

import android.content.Context
import java.security.SecureRandom

class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("agent", Context.MODE_PRIVATE)

    val token: String
        get() = prefs.getString(KEY, null) ?: regenerate()

    fun regenerate(): String {
        val bytes = ByteArray(20).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString(KEY, token).apply()
        return token
    }

    private companion object {
        const val KEY = "token"
    }
}
