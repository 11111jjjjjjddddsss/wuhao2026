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
    private const val KEY_SESSION_GENERATION = "session_generation"

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
            .remove(KEY_SESSION_GENERATION)
            .apply()
        return newUserId
    }

    fun getSessionGeneration(): Int =
        prefs()?.getInt(KEY_SESSION_GENERATION, -1) ?: -1

    fun setSessionGeneration(generation: Int) {
        val prefs = prefs() ?: return
        prefs.edit().putInt(KEY_SESSION_GENERATION, generation).apply()
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
