package com.nongjiqianwen

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * 统一 ID 三件套：仅用于日志与请求 header/meta，严禁写入 prompt/messages。
 * - install_id: SharedPreferences 持久化 UUID（作为 userId）
 * - session_id: 每次启动生成一个 UUID
 * - request_id: 每次请求生成（由 ApiConfig.nextRequestId()）
 */
object IdManager {
    private const val PREFS_NAME = "app_ids"
    private const val KEY_CLIENT_ID = "client_id"
    private const val KEY_ACCOUNT_ID = "account_id"
    // 兼容历史版本：沿用旧 key，首次迁移到 client_id 后继续可读
    private const val KEY_INSTALL_ID = "install_id"

    private var appContext: Context? = null
    private var _sessionId: String? = null

    private fun prefs(): SharedPreferences? =
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 必须在 Application 或 MainActivity.onCreate 中调用一次 */
    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            _sessionId = UUID.randomUUID().toString()
        }
    }

    /** client_id：匿名设备身份，首次启动生成并持久化；卸载重装后变化 */
    fun getClientId(): String {
        val ctx = appContext ?: return UUID.randomUUID().toString()
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_CLIENT_ID, null)
        if (id.isNullOrBlank()) {
            id = prefs.getString(KEY_INSTALL_ID, null) // 兼容旧 install_id
        }
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_CLIENT_ID, id).putString(KEY_INSTALL_ID, id).apply()
        } else {
            // 确保新 key 存在，避免旧版本升级后身份漂移
            if (prefs.getString(KEY_CLIENT_ID, null).isNullOrBlank()) {
                prefs.edit().putString(KEY_CLIENT_ID, id).apply()
            }
        }
        return id
    }

    /** 兼容旧调用：install_id 与 client_id 同值 */
    fun getInstallId(): String = getClientId()

    /** session_id：本次启动的 UUID */
    fun getSessionId(): String = _sessionId ?: UUID.randomUUID().toString()

    /** account_id：未来登录态（手机号/微信等），当前仅预留结构 */
    fun getAccountId(): String? {
        val ctx = appContext ?: return null
        val v = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_ACCOUNT_ID, null)
        return v?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** 预留绑定接口：未来登录成功后调用 */
    fun bindAccountId(accountId: String?) {
        val ctx = appContext ?: return
        val clean = accountId?.trim()?.takeIf { it.isNotEmpty() }
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_ACCOUNT_ID, clean).apply()
    }

    data class UserIdentity(
        val clientId: String,
        val accountId: String?
    )

    fun getUserIdentity(): UserIdentity = UserIdentity(
        clientId = getClientId(),
        accountId = getAccountId()
    )

    /** Debug：重置 client_id（仅用于测试） */
    fun resetClientId(): String {
        val ctx = appContext ?: return UUID.randomUUID().toString()
        val newId = UUID.randomUUID().toString()
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CLIENT_ID, newId)
            .putString(KEY_INSTALL_ID, newId)
            .apply()
        return newId
    }

    /** 兼容旧调试入口 */
    fun resetInstallId(): String = resetClientId()
}
