package com.nongjiqianwen

import android.content.Context
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A/B 层状态机：A 层累计完整轮次，达 24 轮后每轮尝试 B 提取；成功后原子清空 A 并写入 B。
 * 禁止：B 未写入却清空 A；B 已写入却未清空 A。
 */
object ABLayerManager {
    private const val TAG = "ABLayerManager"
    private const val PREFS_NAME = "ab_layer"
    private const val KEY_B_SUMMARY = "b_summary"
    private const val A_MIN_ROUNDS = 24


    private var appContext: Context? = null

    /** A 层：完整轮次 (user, assistant) */
    private val aRounds = mutableListOf<Pair<String, String>>()
    private val aLock = Any()

    /** B 层摘要（持久化） */
    @Volatile
    private var bSummary: String = ""

    /** 防止并发提取：extracting=true 时本轮跳过，不阻塞不排队 */
    private val extracting = AtomicBoolean(false)

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            bSummary = appContext!!.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_B_SUMMARY, "") ?: ""
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "ABLayerManager init, B摘要长度=${bSummary.length}")
            }
        }
    }

    /** 当前 B 摘要，供主对话注入 */
    fun getBSummary(): String = bSummary

    /**
     * 完整轮次完成时调用（仅 onComplete 时，非 interrupted）
     * 加入 A，若 A>=24 则异步尝试 B 提取；成功则原子清空 A 并写入 B。
     */
    fun onRoundComplete(userMessage: String, assistantMessage: String) {
        val (shouldExtract, snapshot) = synchronized(aLock) {
            aRounds.add(userMessage to assistantMessage)
            val size = aRounds.size
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "A层+1轮，当前${size}轮")
            }
            val doExtract = size >= A_MIN_ROUNDS
            val snap = if (doExtract) aRounds.map { it } else emptyList()
            doExtract to snap
        }
        if (shouldExtract && snapshot.isNotEmpty()) {
            tryExtractAndUpdateB(snapshot)
        }
    }

    private fun tryExtractAndUpdateB(aRoundsSnapshot: List<Pair<String, String>>) {
        if (!extracting.compareAndSet(false, true)) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "B提取进行中，跳过")
            }
            return
        }
        Thread {
            try {
                val oldB = bSummary
                val dialogueText = buildDialogueText(aRoundsSnapshot)
                val prompt = BExtractionPrompt.getText()
                if (prompt.isBlank()) {
                    Log.w(TAG, "B提取提示词为空，跳过")
                    return@Thread
                }
                val newSummary = QwenClient.extractBSummary(oldB, dialogueText, prompt)
                if (!validateBSummary(newSummary)) {
                    Log.w(TAG, "B摘要校验不通过，不写入不清空A")
                    return@Thread
                }
                val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val committed = prefs?.edit()?.putString(KEY_B_SUMMARY, newSummary)?.commit() ?: false
                if (committed) {
                    synchronized(aLock) {
                        aRounds.clear()
                        bSummary = newSummary
                    }
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "B写入成功(commit=true)，已清空A，新摘要长度=${newSummary.length}")
                    }
                } else {
                    Log.w(TAG, "B写入commit=false，不写B不清空A")
                }
            } catch (e: Exception) {
                Log.e(TAG, "B提取失败，A层保持", e)
            } finally {
                extracting.set(false)
            }
        }.start()
    }

    /** 校验 B 摘要有效性：仅要求非空 */
    private fun validateBSummary(summary: String?): Boolean {
        return !summary.isNullOrBlank()
    }

    private fun buildDialogueText(rounds: List<Pair<String, String>>): String {
        return rounds.joinToString("\n\n") { (user, assistant) ->
            "user: $user\nassistant: $assistant"
        }
    }
}
