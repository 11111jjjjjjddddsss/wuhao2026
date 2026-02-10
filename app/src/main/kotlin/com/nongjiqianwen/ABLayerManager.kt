package com.nongjiqianwen

import android.content.Context
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A/B 层状态机：A 层累计完整轮次，达 24 轮后每轮尝试 B 提取；成功后原子清空 A 并写入 B。
 * 两条线分离：USE_BACKEND_AB 时 A/B 真相在后端，仅通过 GET snapshot / append-a / update-b 同步。
 */
object ABLayerManager {
    private const val TAG = "ABLayerManager"
    private const val PREFS_NAME = "ab_layer"
    private const val KEY_B_SUMMARY_PREFIX = "b_summary_"
    private const val A_MIN_ROUNDS = 24
    private const val B_SUMMARY_MAX_LENGTH = 600

    private var appContext: Context? = null
    private val aRoundsBySession = mutableMapOf<String, MutableList<Pair<String, String>>>()
    private val aLock = Any()
    private val extracting = AtomicBoolean(false)

    /** 后端模式：GET snapshot 后写入；update-b 成功后续写 */
    private var serverBSummary: String = ""
    private val serverARoundsCache = mutableListOf<Pair<String, String>>()
    private val serverLock = Any()

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "ABLayerManager init, USE_BACKEND_AB=${BuildConfig.USE_BACKEND_AB}")
            }
        }
    }

    /** 后端模式：拉取 snapshot 后调用。B 提取用 a_rounds_full（全量）；UI 注入只用 a_rounds_for_ui（最近 24） */
    fun loadSnapshot(snapshot: SessionSnapshot?) {
        if (snapshot == null) return
        synchronized(serverLock) {
            serverBSummary = snapshot.b_summary
            serverARoundsCache.clear()
            serverARoundsCache.addAll(snapshot.a_rounds_full.map { it.user to it.assistant })
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "loadSnapshot b_len=${serverBSummary.length} a_rounds_full=${snapshot.a_rounds_full.size} a_rounds_for_ui=${snapshot.a_rounds_for_ui.size}")
        }
    }

    /** 当前会话 B 摘要 */
    fun getBSummary(): String {
        if (BuildConfig.USE_BACKEND_AB) {
            synchronized(serverLock) { return serverBSummary }
        }
        val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) ?: return ""
        return prefs.getString(KEY_B_SUMMARY_PREFIX + IdManager.getSessionId(), "") ?: ""
    }

    /** 当前会话 A 层历史对话文本（供主对话注入） */
    fun getARoundsTextForMainDialogue(): String {
        val snapshot = if (BuildConfig.USE_BACKEND_AB) {
            synchronized(serverLock) { serverARoundsCache.toList() }
        } else {
            synchronized(aLock) { aRoundsBySession[IdManager.getSessionId()]?.toList() ?: emptyList() }
        }
                if (snapshot.isEmpty()) return ""
        return buildDialogueText(snapshot)
    }

    /**
     * 完整轮次完成时调用（仅 done=true/onComplete；中断不写 A、不扣费）。
     * 后端模式：先 POST append-a，成功则加入 serverARoundsCache，若 A>=24 再尝试 B 提取 → update-b 成功才清 A。
     */
    fun onRoundComplete(userMessage: String, assistantMessage: String) {
        val sessionId = IdManager.getSessionId()
        val userId = IdManager.getClientId()
        if (BuildConfig.USE_BACKEND_AB) {
            SessionApi.appendA(userId, sessionId, userMessage, assistantMessage) { ok ->
                if (!ok) return@appendA
                synchronized(serverLock) {
                    serverARoundsCache.add(userMessage to assistantMessage)
                    val size = serverARoundsCache.size
                    if (BuildConfig.DEBUG) Log.d(TAG, "appendA ok session=$sessionId a_rounds=$size")
                    if (size >= A_MIN_ROUNDS) {
                        tryExtractAndUpdateBBackend(userId, sessionId, serverARoundsCache.toList())
                    }
                }
            }
            return
        }
        val (shouldExtract, snapshot) = synchronized(aLock) {
            val list = aRoundsBySession.getOrPut(sessionId) { mutableListOf() }
            list.add(userMessage to assistantMessage)
            val size = list.size
            if (BuildConfig.DEBUG) Log.d(TAG, "A层+1轮(session=$sessionId)，当前$size 轮")
            val doExtract = size >= A_MIN_ROUNDS
            (doExtract to if (doExtract) list.map { it } else emptyList())
        }
        if (shouldExtract && snapshot.isNotEmpty()) {
            tryExtractAndUpdateBLocal(sessionId, snapshot)
        }
    }

    private fun tryExtractAndUpdateBBackend(userId: String, sessionId: String, aRoundsSnapshot: List<Pair<String, String>>) {
        if (!extracting.compareAndSet(false, true)) return
        Thread {
            try {
                val oldB = synchronized(serverLock) { serverBSummary }
                val dialogueText = buildDialogueText(aRoundsSnapshot)
                val prompt = BExtractionPrompt.getText()
                if (prompt.isBlank()) { return@Thread }
                val newSummary = QwenClient.extractBSummary(oldB, dialogueText, prompt)
                val normalizedSummary = normalizeBSummaryForStore(newSummary)
                if (normalizedSummary == null) {
                    Log.w(TAG, "B摘要校验不通过，不写B不清A")
                    return@Thread
                }
                SessionApi.updateB(userId, sessionId, normalizedSummary) { ok ->
                    if (ok) {
                        synchronized(serverLock) {
                            serverBSummary = normalizedSummary
                            val removeCount = aRoundsSnapshot.size.coerceAtMost(serverARoundsCache.size)
                            repeat(removeCount) { serverARoundsCache.removeAt(0) }
                        }
                        if (BuildConfig.DEBUG) Log.d(TAG, "updateB ok session=$sessionId b_len=${normalizedSummary.length} a_cleared")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "B提取失败", e)
            } finally {
                extracting.set(false)
            }
        }.start()
    }

    private fun tryExtractAndUpdateBLocal(sessionId: String, aRoundsSnapshot: List<Pair<String, String>>) {
        if (!extracting.compareAndSet(false, true)) return
        Thread {
            try {
                val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val keyB = KEY_B_SUMMARY_PREFIX + sessionId
                val oldB = prefs?.getString(keyB, "") ?: ""
                val dialogueText = buildDialogueText(aRoundsSnapshot)
                val prompt = BExtractionPrompt.getText()
                if (prompt.isBlank()) return@Thread
                val newSummary = QwenClient.extractBSummary(oldB, dialogueText, prompt)
                val normalizedSummary = normalizeBSummaryForStore(newSummary) ?: return@Thread
                val committed = prefs?.edit()?.putString(keyB, normalizedSummary)?.commit() ?: false
                if (committed) {
                    synchronized(aLock) {
                        val list = aRoundsBySession[sessionId]
                        if (list != null) {
                            val removeCount = aRoundsSnapshot.size.coerceAtMost(list.size)
                            repeat(removeCount) { list.removeAt(0) }
                        }
                    }
                    if (BuildConfig.DEBUG) Log.d(TAG, "B写入成功(session=$sessionId) 已清空A")
                }
            } catch (e: Exception) {
                Log.e(TAG, "B提取失败", e)
            } finally {
                extracting.set(false)
            }
        }.start()
    }

    /** 校验 B 摘要：仅要求非空（长度由提示词约束），避免因硬编码长度导致提取失败 */
    private fun normalizeBSummaryForStore(summary: String?): String? {
        val trimmed = summary?.trim() ?: return null
        if (trimmed.isEmpty()) return null
        return if (trimmed.length <= B_SUMMARY_MAX_LENGTH) trimmed else trimmed.substring(0, B_SUMMARY_MAX_LENGTH)
    }

    private fun buildDialogueText(rounds: List<Pair<String, String>>): String {
        return rounds.joinToString("\n\n") { (user, assistant) ->
            "user: $user\nassistant: $assistant"
        }
    }
}
