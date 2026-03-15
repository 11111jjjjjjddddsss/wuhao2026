package com.nongjiqianwen

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * Unified identity manager.
 *
 * - user_id: local stable user identity for request headers and app-level continuity
 * - account_id: reserved for future signed-in account binding
 */
object IdManager {
    private const val PREFS_NAME = "app_ids"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_ACCOUNT_ID = "account_id"

    private var appContext: Context? = null

    private fun prefs(): SharedPreferences? =
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
        ensureUserId()
    }

    fun getUserId(): String = getAccountId() ?: ensureUserId()

    fun getAccountId(): String? {
        val ctx = appContext ?: return null
        val value = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_ACCOUNT_ID, null)
        return value?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun bindAccountId(accountId: String?) {
        val ctx = appContext ?: return
        val clean = accountId?.trim()?.takeIf { it.isNotEmpty() }
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACCOUNT_ID, clean)
            .apply()
    }

    data class UserIdentity(
        val userId: String,
        val accountId: String?,
    )

    fun getUserIdentity(): UserIdentity = UserIdentity(
        userId = getUserId(),
        accountId = getAccountId(),
    )

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
