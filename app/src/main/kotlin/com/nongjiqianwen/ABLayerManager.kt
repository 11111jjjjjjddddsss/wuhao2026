package com.nongjiqianwen

import android.content.Context
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A/B 层状态机：A 层累计完整轮次，达 24 轮后每轮尝试 B 提取；成功后原子清空 A 并写入 B。
 * 禁止：B 未写入却清空 A；B 已写入却未清空 A。
 * B 摘要长度硬门槛：trim 后 > 600 视为提取失败（不写 B、不注入、不清空 A）；≤ 600 才写入并清空 A。
 */
object ABLayerManager {
    private const val TAG = "ABLayerManager"
    private const val PREFS_NAME = "ab_layer"
    private const val KEY_B_SUMMARY_PREFIX = "b_summary_"
    private const val A_MIN_ROUNDS = 24
    /** B 摘要 trim 后长度上限；超过视为提取失败，不写入 B、不清空 A */
    private const val B_SUMMARY_MAX_LENGTH = 600

    private var appContext: Context? = null

    /** A 层：按 session 隔离，sessionId -> 完整轮次 (user, assistant) */
    private val aRoundsBySession = mutableMapOf<String, MutableList<Pair<String, String>>>()
    private val aLock = Any()

    /** 防止并发提取：extracting=true 时本轮跳过，不阻塞不排队 */
    private val extracting = AtomicBoolean(false)

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "ABLayerManager init, B 按 sessionId 隔离")
            }
        }
    }

    /** 当前会话 B 摘要，供主对话注入；key = b_summary_<sessionId> */
    fun getBSummary(): String {
        val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return ""
        val sessionId = IdManager.getSessionId()
        return prefs.getString(KEY_B_SUMMARY_PREFIX + sessionId, "") ?: ""
    }

    /** 当前会话 A 层历史对话文本（带标记），供主对话「中等参考性」注入；无则返回空字符串 */
    fun getARoundsTextForMainDialogue(): String {
        val sessionId = IdManager.getSessionId()
        val snapshot = synchronized(aLock) {
            aRoundsBySession[sessionId]?.toList() ?: emptyList()
        }
        if (snapshot.isEmpty()) return ""
        return "[中等参考性·A层历史对话]\n" + buildDialogueText(snapshot)
    }

    /**
     * 完整轮次完成时调用（仅 onComplete 时，非 interrupted）
     * 加入 A，若 A>=24 则异步尝试 B 提取；成功则原子清空 A 并写入 B。按 session 隔离。
     */
    fun onRoundComplete(userMessage: String, assistantMessage: String) {
        val sessionId = IdManager.getSessionId()
        val (shouldExtract, snapshot) = synchronized(aLock) {
            val list = aRoundsBySession.getOrPut(sessionId) { mutableListOf() }
            list.add(userMessage to assistantMessage)
            val size = list.size
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "A层+1轮(session=$sessionId)，当前${size}轮")
            }
            val doExtract = size >= A_MIN_ROUNDS
            val snap = if (doExtract) list.map { it } else emptyList()
            doExtract to snap
        }
        if (shouldExtract && snapshot.isNotEmpty()) {
            tryExtractAndUpdateB(sessionId, snapshot)
        }
    }

    private fun tryExtractAndUpdateB(sessionId: String, aRoundsSnapshot: List<Pair<String, String>>) {
        if (!extracting.compareAndSet(false, true)) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "B提取进行中，跳过")
            }
            return
        }
        Thread {
            try {
                val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val keyB = KEY_B_SUMMARY_PREFIX + sessionId
                val oldB = prefs?.getString(keyB, "") ?: ""
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
                val committed = prefs?.edit()?.putString(keyB, newSummary)?.commit() ?: false
                if (committed) {
                    synchronized(aLock) {
                        aRoundsBySession[sessionId]?.clear()
                    }
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "B写入成功(session=$sessionId)，已清空A，新摘要长度=${newSummary.length}")
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

    /** 校验 B 摘要：非空且 trim 后长度 ≤ B_SUMMARY_MAX_LENGTH 才通过；>600 视为提取失败，不写 B、不清空 A */
    private fun validateBSummary(summary: String?): Boolean {
        if (summary.isNullOrBlank()) return false
        val trimmed = summary.trim()
        return trimmed.length <= B_SUMMARY_MAX_LENGTH
    }

    private fun buildDialogueText(rounds: List<Pair<String, String>>): String {
        return rounds.joinToString("\n\n") { (user, assistant) ->
            "user: $user\nassistant: $assistant"
        }
    }
}
