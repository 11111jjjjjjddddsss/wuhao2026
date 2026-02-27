package com.nongjiqianwen

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting

data class ABConfig(val aWindowRounds: Int, val bEveryRounds: Int)

object ABLayerManager {
    private const val TAG = "ABLayerManager"
    private const val PREFS_NAME = "ab_layer"
    private const val KEY_B_SUMMARY_PREFIX = "b_summary_"
    private const val KEY_ROUND_TOTAL_PREFIX = "round_total_"
    private const val KEY_PENDING_RETRY_PREFIX = "pending_retry_"

    private var appContext: Context? = null

    private val aRoundsBySession = mutableMapOf<String, MutableList<Pair<String, String>>>()
    private val aLock = Any()

    private var serverBSummary: String = ""
    private val serverARoundsCache = mutableListOf<Pair<String, String>>()
    private val serverLock = Any()

    private val extractLock = Any()
    private val inFlightSessions = mutableSetOf<String>()
    @Volatile private var testSessionIdOverride: String? = null
    @Volatile private var testUserIdOverride: String? = null
    @Volatile private var testBackendModeOverride: Boolean? = null
    @Volatile private var testExtractExecutor: ((oldB: String, dialogueText: String, prompt: String) -> Result<String>)? = null
    @Volatile private var testBackendSnapshotProvider: ((userId: String, sessionId: String) -> SessionSnapshot?)? = null

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "ABLayerManager init, USE_BACKEND_AB=${BuildConfig.USE_BACKEND_AB}")
            }
        }
    }

    fun loadSnapshot(snapshot: SessionSnapshot?) {
        if (snapshot == null) return
        synchronized(serverLock) {
            serverBSummary = snapshot.b_summary
            serverARoundsCache.clear()
            serverARoundsCache.addAll(snapshot.a_rounds_full.map { it.user to it.assistant })
        }
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "loadSnapshot b_len=${serverBSummary.length} a_rounds_full=${snapshot.a_rounds_full.size} a_rounds_for_ui=${snapshot.a_rounds_for_ui.size}",
            )
        }
    }

    fun getBSummary(): String {
        if (BuildConfig.USE_BACKEND_AB) {
            synchronized(serverLock) { return serverBSummary }
        }
        val prefs = prefs() ?: return ""
        return prefs.getString(KEY_B_SUMMARY_PREFIX + IdManager.getSessionId(), "") ?: ""
    }

    fun getARoundsTextForMainDialogue(): String {
        val snapshot = if (BuildConfig.USE_BACKEND_AB) {
            synchronized(serverLock) { serverARoundsCache.toList() }
        } else {
            synchronized(aLock) { aRoundsBySession[IdManager.getSessionId()]?.toList() ?: emptyList() }
        }
        if (snapshot.isEmpty()) return ""
        return buildDialogueText(snapshot)
    }

    fun onRoundComplete(userMessage: String, assistantMessage: String, chatModel: String? = null) {
        val sessionId = currentSessionId()
        val userId = currentUserId()
        val config = getABConfig(chatModel)

        if (isBackendMode()) {
            onRoundCompleteBackend(sessionId, userId, userMessage, assistantMessage, config, chatModel)
            return
        }

        val trigger = synchronized(aLock) {
            val list = aRoundsBySession.getOrPut(sessionId) { mutableListOf() }
            list.add(userMessage to assistantMessage)
            trimRounds(list, config.aWindowRounds)
            logTrimState(sessionId, "local", list.size, config.aWindowRounds)
            val roundTotal = incrementRoundTotal(sessionId)
            val periodicEligible = (roundTotal % config.bEveryRounds == 0)
            if (periodicEligible) {
                setPendingRetry(sessionId, true)
            }
            val pendingRetry = getPendingRetry(sessionId)
            logRoundState(chatModel, sessionId, roundTotal, config.bEveryRounds, periodicEligible, pendingRetry, isInFlight(sessionId), "local")
            if (pendingRetry) {
                val reason = if (periodicEligible) "periodic" else "retry"
                TriggerPlan(true, reason, list.toList(), roundTotal)
            } else {
                TriggerPlan(false, "", emptyList(), roundTotal)
            }
        }

        if (trigger.shouldTrigger) {
            tryExtractAndUpdateBLocal(sessionId, trigger.snapshot, trigger.reason)
        }
    }

    private fun onRoundCompleteBackend(
        sessionId: String,
        userId: String,
        userMessage: String,
        assistantMessage: String,
        config: ABConfig,
        chatModel: String?,
    ) {
        val testSnapshotProvider = testBackendSnapshotProvider
        if (testSnapshotProvider != null) {
            val trigger = synchronized(serverLock) {
                serverARoundsCache.add(userMessage to assistantMessage)
                trimRounds(serverARoundsCache, config.aWindowRounds)
                logTrimState(sessionId, "backend", serverARoundsCache.size, config.aWindowRounds)
                val roundTotal = incrementRoundTotal(sessionId)
                val periodicEligible = (roundTotal % config.bEveryRounds == 0)
                if (periodicEligible) {
                    setPendingRetry(sessionId, true)
                }
                val pendingRetry = getPendingRetry(sessionId)
                logRoundState(chatModel, sessionId, roundTotal, config.bEveryRounds, periodicEligible, pendingRetry, isInFlight(sessionId), "backend")
                if (pendingRetry) {
                    val reason = if (periodicEligible) "periodic" else "retry"
                    TriggerPlan(true, reason, emptyList(), roundTotal)
                } else {
                    TriggerPlan(false, "", emptyList(), roundTotal)
                }
            }
            if (trigger.shouldTrigger) {
                tryUpdateBFromBackend(sessionId, userId, trigger.reason)
            }
            return
        }
        val clientMsgId = "ab_${sessionId}_${System.currentTimeMillis()}"
        SessionApi.appendA(sessionId, clientMsgId, userMessage, assistantMessage) { ok ->
            if (!ok) {
                setPendingRetry(sessionId, true)
                return@appendA
            }

            val trigger = synchronized(serverLock) {
                serverARoundsCache.add(userMessage to assistantMessage)
                trimRounds(serverARoundsCache, config.aWindowRounds)
                logTrimState(sessionId, "backend", serverARoundsCache.size, config.aWindowRounds)
                val roundTotal = incrementRoundTotal(sessionId)
                val periodicEligible = (roundTotal % config.bEveryRounds == 0)
                if (periodicEligible) {
                    setPendingRetry(sessionId, true)
                }
                val pendingRetry = getPendingRetry(sessionId)
                logRoundState(chatModel, sessionId, roundTotal, config.bEveryRounds, periodicEligible, pendingRetry, isInFlight(sessionId), "backend")
                if (pendingRetry) {
                    val reason = if (periodicEligible) "periodic" else "retry"
                    TriggerPlan(true, reason, emptyList(), roundTotal)
                } else {
                    TriggerPlan(false, "", emptyList(), roundTotal)
                }
            }

            if (trigger.shouldTrigger) {
                tryUpdateBFromBackend(sessionId, userId, trigger.reason)
            }
        }
    }

    private fun tryExtractAndUpdateBLocal(sessionId: String, aRoundsSnapshot: List<Pair<String, String>>, triggerReason: String) {
        if (isBackendMode()) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "B local extract blocked(session=$sessionId): backend mode is enabled")
            }
            return
        }
        if (!acquireInFlight(sessionId)) {
            setPendingRetry(sessionId, true)
            if (BuildConfig.DEBUG) Log.d(TAG, "B trigger skipped(session=$sessionId): inFlight=true reason=$triggerReason")
            return
        }

        val runBlock = runBlock@{
            var result = "fail"
            var failReason = "unknown"
            try {
                val prompt = BExtractionPrompt.getText()
                if (prompt.isBlank()) {
                    setPendingRetry(sessionId, true)
                    failReason = "prompt_blank"
                    return@runBlock
                }
                val prefs = prefs()
                val oldB = prefs?.getString(KEY_B_SUMMARY_PREFIX + sessionId, "") ?: ""
                val dialogueText = buildDialogueText(aRoundsSnapshot)
                val testExecutor = testExtractExecutor
                val summaryResult = if (testExecutor != null) {
                    testExecutor.invoke(oldB, dialogueText, prompt)
                } else {
                    runCatching { QwenClient.extractBSummary(oldB, dialogueText, prompt) }
                }
                val newSummary = summaryResult.getOrElse { throw it }
                val normalizedSummary = normalizeBSummaryForStore(newSummary)
                if (normalizedSummary == null) {
                    setPendingRetry(sessionId, true)
                    failReason = "empty_summary"
                    return@runBlock
                }
                val committed = prefs?.edit()?.putString(KEY_B_SUMMARY_PREFIX + sessionId, normalizedSummary)?.commit() ?: false
                if (!committed) {
                    setPendingRetry(sessionId, true)
                    failReason = "commit_failed"
                    return@runBlock
                }
                setPendingRetry(sessionId, false)
                result = "success"
                failReason = ""
            } catch (e: Exception) {
                setPendingRetry(sessionId, true)
                failReason = "exception"
                Log.e(TAG, "B extract failed(session=$sessionId)", e)
            } finally {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "B trigger(session=$sessionId) reason=$triggerReason result=$result failReason=$failReason")
                }
                releaseInFlight(sessionId)
            }
        }
        if (testExtractExecutor != null) {
            runBlock()
        } else {
            Thread { runBlock() }.start()
        }
    }

    private fun tryUpdateBFromBackend(sessionId: String, userId: String, triggerReason: String) {
        if (!acquireInFlight(sessionId)) {
            setPendingRetry(sessionId, true)
            if (BuildConfig.DEBUG) Log.d(TAG, "B backend trigger skipped(session=$sessionId): inFlight=true reason=$triggerReason")
            return
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "B trigger(session=$sessionId) reason=$triggerReason mode=backend result=request_snapshot")
        }
        val testSnapshotProvider = testBackendSnapshotProvider
        if (testSnapshotProvider != null) {
            val snapshot = testSnapshotProvider.invoke(userId, sessionId)
            try {
                if (snapshot != null && snapshot.b_summary.isNotBlank()) {
                    loadSnapshot(snapshot)
                    setPendingRetry(sessionId, false)
                    if (BuildConfig.DEBUG) Log.d(TAG, "B trigger(session=$sessionId) reason=$triggerReason result=success")
                } else {
                    setPendingRetry(sessionId, true)
                    if (BuildConfig.DEBUG) Log.d(TAG, "B trigger(session=$sessionId) reason=$triggerReason result=fail failReason=backend_unavailable")
                }
            } finally {
                releaseInFlight(sessionId)
            }
            return
        }
        SessionApi.getSnapshot(sessionId) { snapshot ->
            try {
                if (snapshot != null && snapshot.b_summary.isNotBlank()) {
                    loadSnapshot(snapshot)
                    setPendingRetry(sessionId, false)
                    if (BuildConfig.DEBUG) Log.d(TAG, "B trigger(session=$sessionId) reason=$triggerReason result=success")
                } else {
                    setPendingRetry(sessionId, true)
                    if (BuildConfig.DEBUG) Log.d(TAG, "B trigger(session=$sessionId) reason=$triggerReason result=fail failReason=backend_unavailable")
                }
            } finally {
                releaseInFlight(sessionId)
            }
        }
    }

    private fun normalizeBSummaryForStore(summary: String?): String? {
        val trimmed = summary?.trim() ?: return null
        return trimmed.takeIf { it.isNotEmpty() }
    }

    private fun buildDialogueText(rounds: List<Pair<String, String>>): String {
        return rounds.joinToString("\n\n") { (user, assistant) ->
            "user: $user\nassistant: $assistant"
        }
    }

    private fun trimRounds(rounds: MutableList<Pair<String, String>>, maxRounds: Int) {
        if (maxRounds <= 0) {
            rounds.clear()
            return
        }
        while (rounds.size > maxRounds) {
            rounds.removeAt(0)
        }
    }

    private fun getABConfig(chatModel: String?): ABConfig {
        val plan = PlanConfig.fromChatModel(chatModel)
        return ABConfig(
            aWindowRounds = plan.aWindow,
            bEveryRounds = plan.bExtractRounds,
        )
    }

    private fun acquireInFlight(sessionId: String): Boolean = synchronized(extractLock) {
        if (inFlightSessions.contains(sessionId)) return@synchronized false
        inFlightSessions.add(sessionId)
        true
    }

    private fun releaseInFlight(sessionId: String) = synchronized(extractLock) {
        inFlightSessions.remove(sessionId)
    }

    private fun isInFlight(sessionId: String): Boolean = synchronized(extractLock) {
        inFlightSessions.contains(sessionId)
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun currentSessionId(): String = testSessionIdOverride ?: IdManager.getSessionId()

    private fun currentUserId(): String = testUserIdOverride ?: IdManager.getClientId()

    private fun isBackendMode(): Boolean = testBackendModeOverride ?: BuildConfig.USE_BACKEND_AB

    private fun incrementRoundTotal(sessionId: String): Int {
        val prefs = prefs() ?: return 0
        val key = KEY_ROUND_TOTAL_PREFIX + sessionId
        val prev = prefs.getInt(key, 0)
        val next = prev + 1
        prefs.edit().putInt(key, next).apply()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "round_total(session=$sessionId): prev=$prev next=$next")
        }
        return next
    }

    private fun getPendingRetry(sessionId: String): Boolean {
        val prefs = prefs() ?: return false
        return prefs.getBoolean(KEY_PENDING_RETRY_PREFIX + sessionId, false)
    }

    private fun setPendingRetry(sessionId: String, value: Boolean) {
        val prefs = prefs() ?: return
        prefs.edit().putBoolean(KEY_PENDING_RETRY_PREFIX + sessionId, value).apply()
    }

    private fun logRoundState(
        chatModel: String?,
        sessionId: String,
        roundTotal: Int,
        bEveryRounds: Int,
        eligible: Boolean,
        pendingRetry: Boolean,
        inFlight: Boolean,
        mode: String,
    ) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            TAG,
            "tier=${chatModel ?: "free"}, session_id=$sessionId, roundCount_total=$roundTotal, bEveryRounds=$bEveryRounds, eligible=$eligible, pendingRetry=$pendingRetry, inFlight=$inFlight, mode=$mode",
        )
    }

    private fun logTrimState(
        sessionId: String,
        mode: String,
        currentSize: Int,
        maxRounds: Int,
    ) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            TAG,
            "a_window_check session_id=$sessionId mode=$mode size=$currentSize max=$maxRounds pass=${currentSize <= maxRounds}",
        )
    }

    private data class TriggerPlan(
        val shouldTrigger: Boolean,
        val reason: String,
        val snapshot: List<Pair<String, String>>,
        val roundTotal: Int,
    )

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @JvmSynthetic
    internal fun setTestHooks(
        sessionId: String? = null,
        userId: String? = null,
        backendMode: Boolean? = null,
        extractExecutor: ((oldB: String, dialogueText: String, prompt: String) -> Result<String>)? = null,
        backendSnapshotProvider: ((userId: String, sessionId: String) -> SessionSnapshot?)? = null,
    ) {
        requireDebugTestHook("setTestHooks")
        testSessionIdOverride = sessionId
        testUserIdOverride = userId
        testBackendModeOverride = backendMode
        testExtractExecutor = extractExecutor
        testBackendSnapshotProvider = backendSnapshotProvider
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @JvmSynthetic
    internal fun clearTestHooks() {
        requireDebugTestHook("clearTestHooks")
        testSessionIdOverride = null
        testUserIdOverride = null
        testBackendModeOverride = null
        testExtractExecutor = null
        testBackendSnapshotProvider = null
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @JvmSynthetic
    internal fun debugGetRoundTotal(sessionId: String): Int {
        requireDebugTestHook("debugGetRoundTotal")
        val prefs = prefs() ?: return 0
        return prefs.getInt(KEY_ROUND_TOTAL_PREFIX + sessionId, 0)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @JvmSynthetic
    internal fun debugGetPendingRetry(sessionId: String): Boolean {
        requireDebugTestHook("debugGetPendingRetry")
        val prefs = prefs() ?: return false
        return prefs.getBoolean(KEY_PENDING_RETRY_PREFIX + sessionId, false)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @JvmSynthetic
    internal fun resetForTest(sessionId: String) {
        requireDebugTestHook("resetForTest")
        synchronized(aLock) { aRoundsBySession.remove(sessionId) }
        synchronized(serverLock) {
            serverARoundsCache.clear()
            serverBSummary = ""
        }
        synchronized(extractLock) { inFlightSessions.remove(sessionId) }
        val prefs = prefs() ?: return
        prefs.edit()
            .remove(KEY_B_SUMMARY_PREFIX + sessionId)
            .remove(KEY_ROUND_TOTAL_PREFIX + sessionId)
            .remove(KEY_PENDING_RETRY_PREFIX + sessionId)
            .commit()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @JvmSynthetic
    internal fun dropInMemoryStateForTest(sessionId: String) {
        requireDebugTestHook("dropInMemoryStateForTest")
        synchronized(aLock) { aRoundsBySession.remove(sessionId) }
        synchronized(serverLock) {
            serverARoundsCache.clear()
            serverBSummary = ""
        }
        synchronized(extractLock) { inFlightSessions.remove(sessionId) }
    }

    private fun requireDebugTestHook(apiName: String) {
        if (!BuildConfig.DEBUG) {
            throw IllegalStateException("$apiName is debug-only")
        }
    }
}
