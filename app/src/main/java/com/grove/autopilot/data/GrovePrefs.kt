package com.grove.autopilot.data

import android.content.Context

class GrovePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("grove", Context.MODE_PRIVATE)

    var crossfade: Boolean
        get() = prefs.getBoolean("crossfade", true)
        set(value) {
            prefs.edit().putBoolean("crossfade", value).apply()
        }

    var clientId: String
        get() = prefs.getString("client_id", "").orEmpty()
        set(value) {
            prefs.edit().putString("client_id", value.trim()).apply()
        }

    var accessToken: String
        get() = prefs.getString("access", "").orEmpty()
        set(value) {
            prefs.edit().putString("access", value).apply()
        }

    var refreshToken: String
        get() = prefs.getString("refresh", "").orEmpty()
        set(value) {
            prefs.edit().putString("refresh", value).apply()
        }

    var expiryMs: Long
        get() = prefs.getLong("expiry", 0L)
        set(value) {
            prefs.edit().putLong("expiry", value).apply()
        }

    var verifier: String
        get() = prefs.getString("verifier", "").orEmpty()
        set(value) {
            prefs.edit().putString("verifier", value).apply()
        }

    var displayName: String
        get() = prefs.getString("name", "").orEmpty()
        set(value) {
            prefs.edit().putString("name", value).apply()
        }

    fun clearSession() {
        prefs.edit()
            .remove("access")
            .remove("refresh")
            .remove("expiry")
            .remove("verifier")
            .remove("name")
            .apply()
    }
}
