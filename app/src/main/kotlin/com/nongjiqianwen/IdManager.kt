package com.nongjiqianwen

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * Unified identity manager.
 *
 * - user_id: local stable user identity for request headers and app-level continuity
 */
object IdManager {
    private const val PREFS_NAME = "app_ids"
    private const val KEY_USER_ID = "user_id"

    private var appContext: Context? = null

    private fun prefs(): SharedPreferences? =
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
        ensureUserId()
    }

    fun getUserId(): String = ensureUserId()

    fun resetUserId(): String {
        val ctx = appContext ?: return UUID.randomUUID().toString()
        val newUserId = UUID.randomUUID().toString()
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER_ID, newUserId)
            .apply()
        return newUserId
    }

    private fun ensureUserId(): String {
        val prefs = prefs() ?: return UUID.randomUUID().toString()
        val existing = prefs.getString(KEY_USER_ID, null)?.trim()
        if (!existing.isNullOrEmpty()) return existing
        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_USER_ID, newId).apply()
        return newId
    }
}
