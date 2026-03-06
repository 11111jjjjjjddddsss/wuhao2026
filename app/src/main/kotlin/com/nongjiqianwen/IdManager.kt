package com.nongjiqianwen

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * 统一 ID 管理：
 * - client_id: 安装实例级匿名身份，持久化
 * - session_id: 全 App 唯一长期会话，持久化
 * - account_id: 未来手机号登录后的账号身份，当前仅预留
 */
object IdManager {
    private const val PREFS_NAME = "app_ids"
    private const val KEY_CLIENT_ID = "client_id"
    private const val KEY_ACCOUNT_ID = "account_id"
    private const val KEY_SESSION_ID = "session_id"
    private const val KEY_INSTALL_ID = "install_id"

    private var appContext: Context? = null
    private var cachedSessionId: String? = null

    private fun prefs(): SharedPreferences? =
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 必须在 Application 或 MainActivity.onCreate 中调用一次。 */
    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
        cachedSessionId = ensureSessionId()
    }

    /** client_id：匿名设备身份，首次启动生成并持久化；卸载重装后变化。 */
    fun getClientId(): String {
        val ctx = appContext ?: return UUID.randomUUID().toString()
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_CLIENT_ID, null)
        if (id.isNullOrBlank()) {
            id = prefs.getString(KEY_INSTALL_ID, null)
        }
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_CLIENT_ID, id).putString(KEY_INSTALL_ID, id).apply()
        } else if (prefs.getString(KEY_CLIENT_ID, null).isNullOrBlank()) {
            prefs.edit().putString(KEY_CLIENT_ID, id).apply()
        }
        return id
    }

    /** 兼容旧调用：install_id 与 client_id 等价。 */
    fun getInstallId(): String = getClientId()

    /** session_id：全 App 唯一长期会话，除非清数据或显式重置，否则不变。 */
    fun getSessionId(): String = cachedSessionId ?: ensureSessionId()

    /** account_id：未来手机号登录后的主身份，当前仅预留结构。 */
    fun getAccountId(): String? {
        val ctx = appContext ?: return null
        val value = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_ACCOUNT_ID, null)
        return value?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** 预留绑定接口：未来登录成功后调用。 */
    fun bindAccountId(accountId: String?) {
        val ctx = appContext ?: return
        val clean = accountId?.trim()?.takeIf { it.isNotEmpty() }
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACCOUNT_ID, clean)
            .apply()
    }

    data class UserIdentity(
        val clientId: String,
        val accountId: String?,
    )

    fun getUserIdentity(): UserIdentity = UserIdentity(
        clientId = getClientId(),
        accountId = getAccountId(),
    )

    /** Debug：重置 client_id，并同步重置唯一会话。 */
    fun resetClientId(): String {
        val ctx = appContext ?: return UUID.randomUUID().toString()
        val newClientId = UUID.randomUUID().toString()
        val newSessionId = UUID.randomUUID().toString()
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CLIENT_ID, newClientId)
            .putString(KEY_INSTALL_ID, newClientId)
            .putString(KEY_SESSION_ID, newSessionId)
            .apply()
        cachedSessionId = newSessionId
        return newClientId
    }

    /** 兼容旧调试入口。 */
    fun resetInstallId(): String = resetClientId()

    private fun ensureSessionId(): String {
        val prefs = prefs() ?: return UUID.randomUUID().toString()
        val existing = prefs.getString(KEY_SESSION_ID, null)?.trim()
        if (!existing.isNullOrEmpty()) return existing
        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_SESSION_ID, newId).apply()
        return newId
    }
}
