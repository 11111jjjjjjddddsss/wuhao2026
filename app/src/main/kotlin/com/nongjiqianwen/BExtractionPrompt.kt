package com.nongjiqianwen

import android.content.Context
import android.util.Log
import java.io.InputStreamReader

/**
 * B 层摘要提取提示词：从 assets/b_extraction_prompt.txt 加载。
 * 格式：以 # 开头的行视为注释；其余为提示词正文。
 */
object BExtractionPrompt {
    private const val TAG = "BExtractionPrompt"
    private const val ASSET_PATH = "b_extraction_prompt.txt"

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
                Log.d(TAG, "B提取提示词已加载，长度=${cachedText.length}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载 b_extraction_prompt.txt 失败", e)
            cachedText = ""
        }
    }

    fun getText(): String = cachedText
}
