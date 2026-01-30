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

    /** install_id：持久化 UUID，作为 userId */
    fun getInstallId(): String {
        val ctx = appContext ?: return UUID.randomUUID().toString()
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_INSTALL_ID, null)
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_INSTALL_ID, id).apply()
        }
        return id
    }

    /** session_id：本次启动的 UUID */
    fun getSessionId(): String = _sessionId ?: UUID.randomUUID().toString()

    /** Debug：重置 install_id（仅用于测试） */
    fun resetInstallId(): String {
        val ctx = appContext ?: return UUID.randomUUID().toString()
        val newId = UUID.randomUUID().toString()
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_INSTALL_ID, newId)
            .apply()
        return newId
    }
}
