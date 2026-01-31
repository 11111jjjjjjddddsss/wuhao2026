package com.nongjiqianwen

import android.content.Context
import android.util.Log
import java.io.InputStreamReader

/**
 * 系统锚点：从 assets/system_anchor.txt 加载，供每次请求注入为 system role。
 * 需在 MainActivity.onCreate 中调用 init(applicationContext)。
 */
object SystemAnchor {
    private const val TAG = "SystemAnchor"
    private const val ASSET_PATH = "system_anchor.txt"

    @Volatile
    private var cachedText: String = ""

    /** 必须在 Application 或 MainActivity 启动时调用一次 */
    fun init(context: Context) {
        if (cachedText.isNotEmpty()) return
        try {
            context.assets.open(ASSET_PATH).use { input ->
                cachedText = InputStreamReader(input, Charsets.UTF_8).readText().trim()
                Log.d(TAG, "系统锚点已加载，长度=${cachedText.length}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载 system_anchor.txt 失败", e)
            cachedText = ""
        }
    }

    /** 供 QwenClient 每次请求注入；未 init 或加载失败时返回空字符串 */
    fun getText(): String = cachedText
}
