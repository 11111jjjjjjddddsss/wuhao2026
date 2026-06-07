package com.nongjiqianwen

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * Unified identity manager.
 *
 * - user_id: local stable pre-login identity used only as a migration bridge
 * - auth_user_id/auth_token: backend account ID and session after phone login
 */
object IdManager {
    private const val PREFS_NAME = "app_ids"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_AUTH_USER_ID = "auth_user_id"
    private const val KEY_AUTH_TOKEN = "auth_token"
    private const val KEY_AUTH_EXPIRES_AT = "auth_expires_at"
    private const val KEY_AUTH_PHONE_MASK = "auth_phone_mask"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_SESSION_GENERATION = "session_generation"

    private var appContext: Context? = null

    private fun prefs(): SharedPreferences? =
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
        ensureUserId()
        ensureDeviceId()
    }

    fun getUserId(): String =
        getAuthenticatedUserId() ?: ensureUserId()

    fun getLegacyUserId(): String = ensureUserId()

    fun getDeviceId(): String = ensureDeviceId()

    fun isLoggedIn(): Boolean =
        !getAuthToken().isNullOrBlank() && !getAuthenticatedUserId().isNullOrBlank()

    fun getAuthenticatedUserId(): String? =
        prefs()
            ?.takeIf { it.hasValidAuthSession() }
            ?.getString(KEY_AUTH_USER_ID, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    fun getAuthToken(): String? =
        prefs()
            ?.takeIf { it.hasValidAuthSession() }
            ?.getString(KEY_AUTH_TOKEN, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    fun getAuthPhoneMask(): String? =
        prefs()
            ?.takeIf { it.hasValidAuthSession() }
            ?.getString(KEY_AUTH_PHONE_MASK, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    fun saveAuthSession(
        userId: String,
        token: String,
        expiresAt: Long,
        phoneMask: String?
    ) {
        val prefs = prefs() ?: return
        val normalizedUserId = userId.trim()
        val normalizedToken = token.trim()
        if (normalizedUserId.isEmpty() || normalizedToken.isEmpty()) return
        prefs.edit()
            .putString(KEY_AUTH_USER_ID, normalizedUserId)
            .putString(KEY_AUTH_TOKEN, normalizedToken)
            .putLong(KEY_AUTH_EXPIRES_AT, expiresAt)
            .putString(KEY_AUTH_PHONE_MASK, phoneMask.orEmpty())
            .apply()
    }

    fun clearAuthSession() {
        val prefs = prefs() ?: return
        prefs.edit()
            .remove(KEY_AUTH_USER_ID)
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_AUTH_EXPIRES_AT)
            .remove(KEY_AUTH_PHONE_MASK)
            .remove(KEY_SESSION_GENERATION)
            .apply()
    }

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

    private fun ensureDeviceId(): String {
        val prefs = prefs() ?: return UUID.randomUUID().toString()
        val existing = prefs.getString(KEY_DEVICE_ID, null)?.trim()
        if (!existing.isNullOrEmpty()) return existing
        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
        return newId
    }

    private fun SharedPreferences.hasValidAuthSession(): Boolean {
        val expiresAt = getLong(KEY_AUTH_EXPIRES_AT, 0L)
        return expiresAt > System.currentTimeMillis()
    }
}
