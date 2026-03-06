package com.nongjiqianwen

import android.content.Context
import android.util.Log
import java.io.InputStreamReader

/** 主对话系统锚点：从 assets/system_anchor.txt 加载。 */
object SystemAnchorPrompt {
    private const val TAG = "SystemAnchorPrompt"
    private const val ASSET_PATH = "system_anchor.txt"

    @Volatile
    private var cachedText: String = ""

    fun init(context: Context) {
        if (cachedText.isNotEmpty()) return
        try {
            context.assets.open(ASSET_PATH).use { input ->
                val raw = InputStreamReader(input, Charsets.UTF_8).readText()
                cachedText = raw.lines()
                    .filter { !it.trimStart().startsWith("#") }
                    .joinToString("\n")
                    .trim()
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "系统锚点已加载，长度=${cachedText.length}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载 system_anchor.txt 失败", e)
            cachedText = ""
        }
    }

    fun getText(): String = cachedText
}
