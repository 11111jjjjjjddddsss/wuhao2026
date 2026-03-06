package com.nongjiqianwen

import android.content.Context
import android.util.Log
import java.io.InputStreamReader

/** C 层长期记忆提取提示词：从 assets/c_extraction_prompt.txt 加载。 */
object CExtractionPrompt {
    private const val TAG = "CExtractionPrompt"
    private const val ASSET_PATH = "c_extraction_prompt.txt"

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
                    Log.d(TAG, "C 提取提示词已加载，长度=${cachedText.length}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载 c_extraction_prompt.txt 失败", e)
            cachedText = ""
        }
    }

    fun getText(): String = cachedText
}
