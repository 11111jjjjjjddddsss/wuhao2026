package com.nongjiqianwen

import android.content.Context

object PrivacyConsentStore {
    private const val PREFS_NAME = "privacy_consent"
    private const val KEY_ACCEPTED_VERSION = "accepted_version"
    private const val KEY_ACCEPTED_AT_MS = "accepted_at_ms"
    private const val CURRENT_VERSION = 1

    fun isAccepted(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_ACCEPTED_VERSION, 0) >= CURRENT_VERSION

    fun accept(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_ACCEPTED_VERSION, CURRENT_VERSION)
            .putLong(KEY_ACCEPTED_AT_MS, System.currentTimeMillis())
            .apply()
    }
}
