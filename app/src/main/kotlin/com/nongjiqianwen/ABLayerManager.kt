package com.nongjiqianwen

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class ABConfig(
    val aWindowRounds: Int,
    val bEveryRounds: Int,
    val cEveryRounds: Int,
)

object ABLayerManager {
    private const val TAG = "ABLayerManager"
    private const val PREFS_NAME = "ab_layer"
    private const val KEY_B_SUMMARY_PREFIX = "b_summary_"
    private const val KEY_C_SUMMARY_PREFIX = "c_summary_"
    private const val KEY_ROUND_TOTAL_PREFIX = "round_total_"
    private const val KEY_PENDING_RETRY_LEGACY_PREFIX = "pending_retry_"
    private const val KEY_PENDING_RETRY_B_PREFIX = "pending_retry_b_"
    private const val KEY_PENDING_RETRY_C_PREFIX = "pending_retry_c_"
    private const val BACKEND_WRITE_TIMEOUT_SEC = 30L

    private enum class SummaryLayer(
        val tag: String,
        val summaryKeyPrefix: String,
        val pendingRetryKeyPrefix: String,
        val legacyPendingRetryKeyPrefix: String? = null,
    ) {
        B("B", KEY_B_SUMMARY_PREFIX, KEY_PENDING_RETRY_B_PREFIX, KEY_PENDING_RETRY_LEGACY_PREFIX),
        C("C", KEY_C_SUMMARY_PREFIX, KEY_PENDING_RETRY_C_PREFIX),
    }

    private data class TriggerPlan(
        val layer: SummaryLayer,
        val shouldTrigger: Boolean,
        val reason: String,
        val roundTotal: Int,
    )

    private data class TriggerBatch(
        val snapshot: List<Pair<String, String>>,
        val plans: List<TriggerPlan>,
    )

    private var appContext: Context? = null

    private val aRoundsBySession = mutableMapOf<String, MutableList<Pair<String, String>>>()
    private val aLock = Any()

    private var serverBSummary: String = ""
    private var serverCSummary: String = ""
    private val serverARoundsCache = mutableListOf<Pair<String, String>>()
    private val serverLock = Any()

    private val extractLock = Any()
    private val inFlightKeys = mutableSetOf<String>()

    @Volatile private var testSessionIdOverride: String? = null
    @Volatile private var testUserIdOverride: String? = null
    @Volatile private var testBackendModeOverride: Boolean? = null
    @Volatile private var testBExtractExecutor: ((oldB: String, dialogueText: String, prompt: String) -> Result<String>)? = null
    @Volatile private var testCExtractExecutor: ((oldC: String, dialogueText: String, prompt: String) -> Result<String>)? = null
    @Volatile private var testBackendSnapshotProvider: ((userId: String, sessionId: String) -> SessionSnapshot?)? = null

    fun init(context: Context) {
        val applicationContext = context.applicationContext
        if (appContext == null) {
            appContext = applicationContext
        }
        BExtractionPrompt.init(applicationContext)
        CExtractionPrompt.init(applicationContext)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "ABLayerManager init, USE_BACKEND_AB=${BuildConfig.USE_BACKEND_AB}")
        }
    }

    fun loadSnapshot(snapshot: SessionSnapshot?) {
        if (snapshot == null) return
        synchronized(serverLock) {
            serverBSummary = snapshot.b_summary
            serverCSummary = snapshot.c_summary
            serverARoundsCache.clear()
            serverARoundsCache.addAll(snapshot.a_rounds_full.map { it.user to it.assistant })
        }
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "loadSnapshot b_len=${serverBSummary.length} c_len=${serverCSummary.length} a_rounds_full=${snapshot.a_rounds_full.size} a_rounds_for_ui=${snapshot.a_rounds_for_ui.size}",
            )
        }
    }

    fun getBSummary(): String = getSummary(SummaryLayer.B)

    fun getCSummary(): String = getSummary(SummaryLayer.C)

    fun getARoundsTextForMainDialogue(): String {
        val snapshot = if (isBackendMode()) {
            synchronized(serverLock) { serverARoundsCache.toList() }
        } else {
            synchronized(aLock) { aRoundsBySession[currentSessionId()]?.toList() ?: emptyList() }
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

        val batch = synchronized(aLock) {
            val list = aRoundsBySession.getOrPut(sessionId) { mutableListOf() }
            list.add(userMessage to assistantMessage)
            trimRounds(list, config.aWindowRounds)
            logTrimState(sessionId, "local", list.size, config.aWindowRounds)
            val roundTotal = incrementRoundTotal(sessionId)
            TriggerBatch(list.toList(), buildTriggerPlans(sessionId, roundTotal, config, chatModel, "local"))
        }

        batch.plans.filter { it.shouldTrigger }.forEach { plan ->
            tryExtractAndUpdateSummary(
                sessionId = sessionId,
                userId = userId,
                aRoundsSnapshot = batch.snapshot,
                layer = plan.layer,
                triggerReason = plan.reason,
                backendMode = false,
            )
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
        val clientMsgId = "ab_${sessionId}_${System.currentTimeMillis()}"
        SessionApi.appendA(sessionId, clientMsgId, userMessage, assistantMessage) { ok ->
            if (!ok) {
                setPendingRetry(sessionId, SummaryLayer.B, true)
                setPendingRetry(sessionId, SummaryLayer.C, true)
                return@appendA
            }

            val batch = synchronized(serverLock) {
                serverARoundsCache.add(userMessage to assistantMessage)
                trimRounds(serverARoundsCache, config.aWindowRounds)
                logTrimState(sessionId, "backend", serverARoundsCache.size, config.aWindowRounds)
                val roundTotal = incrementRoundTotal(sessionId)
                TriggerBatch(serverARoundsCache.toList(), buildTriggerPlans(sessionId, roundTotal, config, chatModel, "backend"))
            }

            batch.plans.filter { it.shouldTrigger }.forEach { plan ->
                tryExtractAndUpdateSummary(
                    sessionId = sessionId,
                    userId = userId,
                    aRoundsSnapshot = batch.snapshot,
                    layer = plan.layer,
                    triggerReason = plan.reason,
                    backendMode = true,
                )
            }
        }
    }

    private fun tryExtractAndUpdateSummary(
        sessionId: String,
        userId: String,
        aRoundsSnapshot: List<Pair<String, String>>,
        layer: SummaryLayer,
        triggerReason: String,
        backendMode: Boolean,
    ) {
        if (!acquireInFlight(sessionId, layer)) {
            setPendingRetry(sessionId, layer, true)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "${layer.tag} trigger skipped(session=$sessionId): inFlight=true reason=$triggerReason")
            }
            return
        }

        val runBlock = runBlock@{
            var result = "fail"
            var failReason = "unknown"
            try {
                val prompt = getPromptText(layer)
                if (prompt.isBlank()) {
                    setPendingRetry(sessionId, layer, true)
                    failReason = "prompt_blank"
                    return@runBlock
                }

                val oldSummary = getStoredSummary(sessionId, layer)
                val dialogueText = buildDialogueText(aRoundsSnapshot)
                val newSummary = executeExtract(layer, oldSummary, dialogueText, prompt)
                val normalizedSummary = normalizeSummaryForStore(newSummary)
                if (normalizedSummary == null) {
                    setPendingRetry(sessionId, layer, true)
                    failReason = "empty_summary"
                    return@runBlock
                }

                val committedLocal = writeSummaryToPrefs(sessionId, layer, normalizedSummary)
                if (!committedLocal) {
                    setPendingRetry(sessionId, layer, true)
                    failReason = "commit_failed"
                    return@runBlock
                }

                if (backendMode) {
                    val remoteCommitted = writeSummaryToBackend(sessionId, normalizedSummary, layer)
                    if (!remoteCommitted) {
                        setPendingRetry(sessionId, layer, true)
                        failReason = "remote_commit_failed"
                        return@runBlock
                    }
                    updateServerSummary(layer, normalizedSummary)
                }

                setPendingRetry(sessionId, layer, false)
                result = "success"
                failReason = ""
            } catch (e: Exception) {
                setPendingRetry(sessionId, layer, true)
                failReason = "exception"
                Log.e(TAG, "${layer.tag} extract failed(session=$sessionId)", e)
            } finally {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "${layer.tag} trigger(session=$sessionId) reason=$triggerReason result=$result failReason=$failReason backend=$backendMode")
                }
                releaseInFlight(sessionId, layer)
            }
        }

        if (getTestExecutor(layer) != null) {
            runBlock()
        } else {
            Thread { runBlock() }.start()
        }
    }

    private fun executeExtract(layer: SummaryLayer, oldSummary: String, dialogueText: String, prompt: String): String {
        val testExecutor = getTestExecutor(layer)
        val result = if (testExecutor != null) {
            testExecutor.invoke(oldSummary, dialogueText, prompt)
        } else {
            when (layer) {
                SummaryLayer.B -> runCatching { QwenClient.extractBSummary(oldSummary, dialogueText, prompt) }
                SummaryLayer.C -> runCatching { QwenClient.extractCSummary(oldSummary, dialogueText, prompt) }
            }
        }
        return result.getOrElse { throw it }
    }

    private fun writeSummaryToBackend(sessionId: String, summary: String, layer: SummaryLayer): Boolean {
        val latch = CountDownLatch(1)
        val success = AtomicBoolean(false)
        when (layer) {
            SummaryLayer.B -> SessionApi.updateB(sessionId, summary) {
                success.set(it)
                latch.countDown()
            }
            SummaryLayer.C -> SessionApi.updateC(sessionId, summary) {
                success.set(it)
                latch.countDown()
            }
        }
        latch.await(BACKEND_WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
        return success.get()
    }

    private fun getPromptText(layer: SummaryLayer): String = when (layer) {
        SummaryLayer.B -> BExtractionPrompt.getText()
        SummaryLayer.C -> CExtractionPrompt.getText()
    }

    private fun getSummary(layer: SummaryLayer): String {
        val sessionId = currentSessionId()
        if (isBackendMode()) {
            val serverValue = synchronized(serverLock) {
                when (layer) {
                    SummaryLayer.B -> serverBSummary
                    SummaryLayer.C -> serverCSummary
                }
            }.trim()
            if (serverValue.isNotEmpty()) return serverValue
        }
        return getSummaryFromPrefs(sessionId, layer)
    }

    private fun getStoredSummary(sessionId: String, layer: SummaryLayer): String {
        val serverValue = synchronized(serverLock) {
            when (layer) {
                SummaryLayer.B -> serverBSummary
                SummaryLayer.C -> serverCSummary
            }
        }.trim()
        if (serverValue.isNotEmpty()) return serverValue
        return getSummaryFromPrefs(sessionId, layer)
    }

    private fun getSummaryFromPrefs(sessionId: String, layer: SummaryLayer): String {
        val prefs = prefs() ?: return ""
        return prefs.getString(layer.summaryKeyPrefix + sessionId, "")?.trim().orEmpty()
    }

    private fun writeSummaryToPrefs(sessionId: String, layer: SummaryLayer, summary: String): Boolean {
        val prefs = prefs() ?: return false
        return prefs.edit().putString(layer.summaryKeyPrefix + sessionId, summary).commit()
    }

    private fun updateServerSummary(layer: SummaryLayer, summary: String) {
        synchronized(serverLock) {
            when (layer) {
                SummaryLayer.B -> serverBSummary = summary
                SummaryLayer.C -> serverCSummary = summary
            }
        }
    }

    private fun normalizeSummaryForStore(summary: String?): String? {
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
            cEveryRounds = plan.cExtractRounds,
        )
    }

    private fun buildTriggerPlans(
        sessionId: String,
        roundTotal: Int,
        config: ABConfig,
        chatModel: String?,
        mode: String,
    ): List<TriggerPlan> {
        return SummaryLayer.values().map { layer ->
            val everyRounds = getEveryRounds(layer, config)
            val periodicEligible = everyRounds > 0 && roundTotal > 0 && roundTotal % everyRounds == 0
            if (periodicEligible) {
                setPendingRetry(sessionId, layer, true)
            }
            val pendingRetry = getPendingRetry(sessionId, layer)
            logRoundState(layer, chatModel, sessionId, roundTotal, everyRounds, periodicEligible, pendingRetry, isInFlight(sessionId, layer), mode)
            if (pendingRetry) {
                TriggerPlan(
                    layer = layer,
                    shouldTrigger = true,
                    reason = if (periodicEligible) "periodic" else "retry",
                    roundTotal = roundTotal,
                )
            } else {
                TriggerPlan(layer = layer, shouldTrigger = false, reason = "", roundTotal = roundTotal)
            }
        }
    }

    private fun getEveryRounds(layer: SummaryLayer, config: ABConfig): Int = when (layer) {
        SummaryLayer.B -> config.bEveryRounds
        SummaryLayer.C -> config.cEveryRounds
    }

    private fun acquireInFlight(sessionId: String, layer: SummaryLayer): Boolean = synchronized(extractLock) {
        val key = inFlightKey(sessionId, layer)
        if (inFlightKeys.contains(key)) return@synchronized false
        inFlightKeys.add(key)
        true
    }

    private fun releaseInFlight(sessionId: String, layer: SummaryLayer) = synchronized(extractLock) {
        inFlightKeys.remove(inFlightKey(sessionId, layer))
    }

    private fun isInFlight(sessionId: String, layer: SummaryLayer): Boolean = synchronized(extractLock) {
        inFlightKeys.contains(inFlightKey(sessionId, layer))
    }

    private fun inFlightKey(sessionId: String, layer: SummaryLayer): String = "${sessionId}_${layer.tag}"

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

    private fun getPendingRetry(sessionId: String, layer: SummaryLayer): Boolean {
        val prefs = prefs() ?: return false
        val current = prefs.getBoolean(layer.pendingRetryKeyPrefix + sessionId, false)
        if (current) return true
        val legacyKey = layer.legacyPendingRetryKeyPrefix ?: return false
        return prefs.getBoolean(legacyKey + sessionId, false)
    }

    private fun setPendingRetry(sessionId: String, layer: SummaryLayer, value: Boolean) {
        val prefs = prefs() ?: return
        prefs.edit().apply {
            putBoolean(layer.pendingRetryKeyPrefix + sessionId, value)
            if (layer.legacyPendingRetryKeyPrefix != null) {
                putBoolean(layer.legacyPendingRetryKeyPrefix + sessionId, value)
            }
        }.apply()
    }

    private fun logRoundState(
        layer: SummaryLayer,
        chatModel: String?,
        sessionId: String,
        roundTotal: Int,
        everyRounds: Int,
        eligible: Boolean,
        pendingRetry: Boolean,
        inFlight: Boolean,
        mode: String,
    ) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            TAG,
            "layer=${layer.tag}, tier=${chatModel ?: "free"}, session_id=$sessionId, roundCount_total=$roundTotal, everyRounds=$everyRounds, eligible=$eligible, pendingRetry=$pendingRetry, inFlight=$inFlight, mode=$mode",
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

    private fun getTestExecutor(layer: SummaryLayer): ((String, String, String) -> Result<String>)? = when (layer) {
        SummaryLayer.B -> testBExtractExecutor
        SummaryLayer.C -> testCExtractExecutor
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @JvmSynthetic
    internal fun setTestHooks(
        sessionId: String? = null,
        userId: String? = null,
        backendMode: Boolean? = null,
        extractExecutor: ((oldB: String, dialogueText: String, prompt: String) -> Result<String>)? = null,
        cExtractExecutor: ((oldC: String, dialogueText: String, prompt: String) -> Result<String>)? = null,
        backendSnapshotProvider: ((userId: String, sessionId: String) -> SessionSnapshot?)? = null,
    ) {
        requireDebugTestHook("setTestHooks")
        testSessionIdOverride = sessionId
        testUserIdOverride = userId
        testBackendModeOverride = backendMode
        testBExtractExecutor = extractExecutor
        testCExtractExecutor = cExtractExecutor
        testBackendSnapshotProvider = backendSnapshotProvider
        val snapshot = if (backendMode == true) backendSnapshotProvider?.invoke(userId ?: "", sessionId ?: "") else null
        if (snapshot != null) {
            loadSnapshot(snapshot)
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @JvmSynthetic
    internal fun clearTestHooks() {
        requireDebugTestHook("clearTestHooks")
        testSessionIdOverride = null
        testUserIdOverride = null
        testBackendModeOverride = null
        testBExtractExecutor = null
        testCExtractExecutor = null
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
        return getPendingRetry(sessionId, SummaryLayer.B)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @JvmSynthetic
    internal fun debugGetPendingRetryC(sessionId: String): Boolean {
        requireDebugTestHook("debugGetPendingRetryC")
        return getPendingRetry(sessionId, SummaryLayer.C)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    @JvmSynthetic
    internal fun resetForTest(sessionId: String) {
        requireDebugTestHook("resetForTest")
        synchronized(aLock) { aRoundsBySession.remove(sessionId) }
        synchronized(serverLock) {
            serverARoundsCache.clear()
            serverBSummary = ""
            serverCSummary = ""
        }
        synchronized(extractLock) {
            inFlightKeys.remove(inFlightKey(sessionId, SummaryLayer.B))
            inFlightKeys.remove(inFlightKey(sessionId, SummaryLayer.C))
        }
        val prefs = prefs() ?: return
        prefs.edit()
            .remove(KEY_B_SUMMARY_PREFIX + sessionId)
            .remove(KEY_C_SUMMARY_PREFIX + sessionId)
            .remove(KEY_ROUND_TOTAL_PREFIX + sessionId)
            .remove(KEY_PENDING_RETRY_LEGACY_PREFIX + sessionId)
            .remove(KEY_PENDING_RETRY_B_PREFIX + sessionId)
            .remove(KEY_PENDING_RETRY_C_PREFIX + sessionId)
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
            serverCSummary = ""
        }
        synchronized(extractLock) {
            inFlightKeys.remove(inFlightKey(sessionId, SummaryLayer.B))
            inFlightKeys.remove(inFlightKey(sessionId, SummaryLayer.C))
        }
    }

    private fun requireDebugTestHook(apiName: String) {
        if (!BuildConfig.DEBUG) {
            throw IllegalStateException("$apiName is debug-only")
        }
    }
}
