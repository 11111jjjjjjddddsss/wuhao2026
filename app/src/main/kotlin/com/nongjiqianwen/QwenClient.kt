package com.nongjiqianwen

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 通义千问客户端
 * 非流式：一次性返回完整答案；取消/停止仅占位，不要求真正中断。
 * 使用阿里云百炼 DashScope API
 *
 * 会员路由（P0 冻结）：
 * - Free/Plus/Pro 主对话 → Flash；专家模式主对话 → PLUS；B 层摘要固定 Flash。
 * - Flash 与 PLUS 走同一套 callApi：同一 tools、同一 messages 拼接、同一 tool-call 闭环与兜底，仅 model 不同。
 */
object QwenClient {
    private val TAG = "QwenClient"
    private const val CONNECT_TIMEOUT_SEC = 10L
    private const val READ_TIMEOUT_SEC = 60L
    private const val WRITE_TIMEOUT_SEC = 10L
    private const val CALL_TIMEOUT_SEC = 60L   // 单次请求总时长上限（1 分钟体验）
    private const val B_EXTRACT_ERROR_LOG_INTERVAL_MS = 60_000L
    private const val TOOL_INFO_MAX_CHARS = 1500
    private const val B_EXTRACT_TEMPERATURE = 0.85
    private const val B_EXTRACT_TOP_P = 0.9
    private const val B_EXTRACT_MAX_TOKENS = 4000
    private const val B_EXTRACT_FREQUENCY_PENALTY = 0.0
    private const val B_EXTRACT_PRESENCE_PENALTY = 0.0
    // P0: search daily cap (5/day), silent degrade
    private const val SEARCH_DAILY_CAP = 5
    // P0: search daily cap (5/day), silent degrade
    private const val SEARCH_PREFS_NAME = "search_usage_prefs"
    // P0: search daily cap (5/day), silent degrade
    private const val SEARCH_USAGE_DATE_KEY = "search_usage_date"
    // P0: search daily cap (5/day), silent degrade
    private const val SEARCH_USED_TODAY_KEY = "search_used_today"
    // P0: search daily cap (5/day), silent degrade
    private const val SEARCH_STARTED_TODAY_KEY = "search_started_today"
    // P0: search daily cap (5/day), silent degrade
    private const val SEARCH_TRIGGERED_CACHE_MAX = 2000
    @Volatile private var lastBExtractErrorLogMs = 0L
    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val currentCall = AtomicReference<Call?>(null)
    private val currentBochaCall = AtomicReference<Call?>(null)
    private val currentRequestId = AtomicReference<String?>(null)
    private val clientMsgIdByStreamId = ConcurrentHashMap<String, String>()
    // P0: search daily cap (5/day), silent degrade
    private val searchTriggeredClientMsgIds = object : LinkedHashMap<String, Boolean>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
            return size > SEARCH_TRIGGERED_CACHE_MAX
        }
    }
    // P0: search daily cap (5/day), silent degrade
    private val searchUsageLock = Any()
    // P0: search daily cap (5/day), silent degrade
    private val lastSearchDateMem = AtomicReference("")
    // P0: search daily cap (5/day), silent degrade
    private val searchUsedTodayMem = AtomicInteger(0)
    // P0: search daily cap (5/day), silent degrade
    private val searchStartedTodayMem = AtomicInteger(0)
    // P0: search daily cap (5/day), silent degrade
    private val searchInFlightMem = AtomicInteger(0)
    private val canceledFlag = AtomicBoolean(false)
    private val streamingRunnableRef = AtomicReference<Runnable?>(null)
    private val gson = Gson()
    private val apiKey = BuildConfig.API_KEY
    private val apiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
    private val modelFlash = "qwen3-vl-flash"
    private val modelPlus = "qwen3-vl-plus"
    private val handler = Handler(Looper.getMainLooper())

    fun setClientMsgIdForStream(streamId: String, clientMsgId: String) {
        if (streamId.isBlank() || clientMsgId.isBlank()) return
        clientMsgIdByStreamId[streamId] = clientMsgId
    }

    fun clearClientMsgIdForStream(streamId: String) {
        if (streamId.isBlank()) return
        clientMsgIdByStreamId.remove(streamId)
    }

    private fun resolveClientMsgId(streamId: String, requestId: String): String {
        if (requestId.isBlank()) {
            // requestId intentionally ignored for search idempotency; keep signature stable.
        }
        return clientMsgIdByStreamId[streamId]?.takeIf { it.isNotBlank() } ?: ""
    }

    // P0: search daily cap (5/day), silent degrade
    private fun getApplicationContextSafely(): Context? {
        return try {
            val cls = Class.forName("android.app.ActivityThread")
            val method = cls.getMethod("currentApplication")
            method.invoke(null) as? Context
        } catch (_: Exception) {
            null
        }
    }

    // P0: search daily cap (5/day), silent degrade
    private fun currentDateKey(): String {
        // Uses local device date; can be bypassed by manual clock change.
        // Server-side date gating should replace this in production.
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    // P0: search daily cap (5/day), silent degrade
    private fun getSearchPrefsLocked(): SharedPreferences? {
        val ctx = getApplicationContextSafely()?.applicationContext ?: return null
        return ctx.getSharedPreferences(SEARCH_PREFS_NAME, Context.MODE_PRIVATE)
    }

    // P0: search daily cap (5/day), silent degrade
    private fun resetDailyIfNeededLocked(prefs: SharedPreferences) {
        val today = currentDateKey()
        val savedDate = prefs.getString(SEARCH_USAGE_DATE_KEY, null)
        if (savedDate == null) {
            lastSearchDateMem.set(today)
            searchUsedTodayMem.set(0)
            searchStartedTodayMem.set(0)
            searchInFlightMem.set(0)
            prefs.edit()
                .putString(SEARCH_USAGE_DATE_KEY, today)
                .putInt(SEARCH_USED_TODAY_KEY, 0)
                .putInt(SEARCH_STARTED_TODAY_KEY, 0)
                .apply()
            return
        }
        if (savedDate != today) {
            prefs.edit()
                .putString(SEARCH_USAGE_DATE_KEY, today)
                .putInt(SEARCH_USED_TODAY_KEY, 0)
                .putInt(SEARCH_STARTED_TODAY_KEY, 0)
                .apply()
            lastSearchDateMem.set(today)
            searchUsedTodayMem.set(0)
            searchStartedTodayMem.set(0)
            searchInFlightMem.set(0)
            return
        }
        if (lastSearchDateMem.get() != today) {
            lastSearchDateMem.set(today)
            searchUsedTodayMem.set(prefs.getInt(SEARCH_USED_TODAY_KEY, 0))
            searchStartedTodayMem.set(prefs.getInt(SEARCH_STARTED_TODAY_KEY, 0))
        }
    }

    // P0: search daily cap (5/day), silent degrade
    private fun tryStartSearchForClientMsgId(clientMsgId: String): Boolean {
        val normalizedClientMsgId = clientMsgId.trim()
        if (normalizedClientMsgId.isEmpty()) return false
        synchronized(searchUsageLock) {
            val prefs = getSearchPrefsLocked() ?: return false
            resetDailyIfNeededLocked(prefs)
            if (searchTriggeredClientMsgIds.containsKey(normalizedClientMsgId)) return false
            if (searchStartedTodayMem.get() >= SEARCH_DAILY_CAP) return false
            searchTriggeredClientMsgIds[normalizedClientMsgId] = true
            searchStartedTodayMem.incrementAndGet()
            searchInFlightMem.incrementAndGet()
            prefs.edit()
                .putString(SEARCH_USAGE_DATE_KEY, lastSearchDateMem.get())
                .putInt(SEARCH_USED_TODAY_KEY, searchUsedTodayMem.get())
                .putInt(SEARCH_STARTED_TODAY_KEY, searchStartedTodayMem.get())
                .apply()
            return true
        }
    }

    // P0: search daily cap (5/day), silent degrade
    private fun finishSearchAttempt(hasEffectiveResult: Boolean) {
        synchronized(searchUsageLock) {
            if (searchInFlightMem.get() > 0) searchInFlightMem.decrementAndGet()
            if (hasEffectiveResult) {
                val bounded = (searchUsedTodayMem.get() + 1).coerceAtMost(SEARCH_DAILY_CAP)
                searchUsedTodayMem.set(bounded)
            }
            val prefs = getSearchPrefsLocked() ?: return
            resetDailyIfNeededLocked(prefs)
            prefs.edit()
                .putString(SEARCH_USAGE_DATE_KEY, lastSearchDateMem.get())
                .putInt(SEARCH_USED_TODAY_KEY, searchUsedTodayMem.get())
                .putInt(SEARCH_STARTED_TODAY_KEY, searchStartedTodayMem.get())
                .apply()
        }
    }

    /** Flash 与 PLUS 共用同一份 tools schema（会员路由一致性） */
    private fun buildWebSearchTools(): JsonArray {
        return JsonArray().apply {
            add(JsonObject().apply {
                addProperty("type", "function")
                add("function", JsonObject().apply {
                    addProperty("name", "web_search")
                    addProperty("description", "Web search via Bocha. Use ONLY when you truly need up-to-date facts or verification.")
                    add("parameters", JsonObject().apply {
                        addProperty("type", "object")
                        add("properties", JsonObject().apply {
                            add("query", JsonObject().apply {
                                addProperty("type", "string")
                                addProperty("description", "Search query")
                            })
                            add("freshness", JsonObject().apply {
                                addProperty("type", "string")
                                addProperty("description", "noLimit or date range")
                            })
                            add("summary", JsonObject().apply {
                                addProperty("type", "boolean")
                            })
                            add("count", JsonObject().apply {
                                addProperty("type", "integer")
                                addProperty("description", "1-50")
                            })
                        })
                        add("required", JsonArray().apply { add("query") })
                    })
                })
            })
        }
    }
    private var isFirstRequest = true
    
    init {
        if (BuildConfig.DEBUG) {
            val keyLength = apiKey.length
            Log.d(TAG, "=== DashScope API 初始化 ===")
            Log.d(TAG, "BAILIAN_API_KEY 读取状态: ${if (keyLength > 0) "成功" else "失败"} (长度: $keyLength)")
            Log.d(TAG, "API URL: $apiUrl (主对话 model 按专家/非专家路由)")
        }
    }
    
    /**
     * 取消当前进行中的请求（仅在新请求开始前或用户点停止时调用；切后台不 cancel）。
     */
    fun cancelCurrentRequest() {
        canceledFlag.set(true)
        streamingRunnableRef.getAndSet(null)?.let { handler.removeCallbacks(it) }
        val oldRequestId = currentRequestId.getAndSet(null)
        currentCall.getAndSet(null)?.cancel()
        currentBochaCall.getAndSet(null)?.cancel()
        if (BuildConfig.DEBUG && oldRequestId != null) {
            Log.d(TAG, "requestId=$oldRequestId 被取消（新请求/用户停止）")
        }
    }

    /** 本地假流式：首段立刻吐，余下分批 postDelayed；isCanceled 为 true 或 phaseEnded 时停止。 */
    private fun emitFakeStream(fullText: String, onChunk: (String) -> Unit, isCanceled: () -> Boolean, onDone: () -> Unit) {
        if (fullText.isBlank()) { onDone(); return }
        if (fullText.length <= 160) {
            onChunk(fullText)
            onDone()
            return
        }
        val burstLen = minOf(32, fullText.length)
        onChunk(fullText.substring(0, burstLen))
        if (BuildConfig.DEBUG) Log.d(TAG, "P0_SMOKE: fake_stream start len=${fullText.length} burst=$burstLen")
        val remaining = fullText.substring(burstLen)
        if (remaining.isEmpty()) {
            if (BuildConfig.DEBUG) Log.d(TAG, "P0_SMOKE: fake_stream done cancelled=false")
            onDone()
            return
        }
        var offset = 0
        val runnable = object : Runnable {
            override fun run() {
                if (isCanceled()) {
                    streamingRunnableRef.set(null)
                    if (BuildConfig.DEBUG) Log.d(TAG, "P0_SMOKE: fake_stream done cancelled=true")
                    onDone()
                    return
                }
                if (offset >= remaining.length) {
                    streamingRunnableRef.set(null)
                    if (BuildConfig.DEBUG) Log.d(TAG, "P0_SMOKE: fake_stream done cancelled=false")
                    onDone()
                    return
                }
                val chunkLen = (remaining.length - offset).coerceAtLeast(0).let { rest -> minOf(24, rest).coerceAtLeast(1) }
                if (chunkLen <= 0) {
                    streamingRunnableRef.set(null)
                    if (BuildConfig.DEBUG) Log.d(TAG, "P0_SMOKE: fake_stream done cancelled=false")
                    onDone()
                    return
                }
                onChunk(remaining.substring(offset, offset + chunkLen))
                offset += chunkLen
                if (offset >= remaining.length) {
                    streamingRunnableRef.set(null)
                    if (BuildConfig.DEBUG) Log.d(TAG, "P0_SMOKE: fake_stream done cancelled=false")
                    onDone()
                    return
                }
                handler.postDelayed(this, 30)
            }
        }
        streamingRunnableRef.set(runnable)
        handler.postDelayed(runnable, 30)
    }

    /** 合并多条 Bocha 成功文本：单标题行 + 最多 5 条「- 标题 | 域名 | URL」，总长 <= TOOL_INFO_MAX_CHARS。 */
    private fun normalizeToolInfo(successTexts: List<String>): String {
        val header = "联网搜索（仅供参考）"
        val itemLines = successTexts.flatMap { it.lines().filter { line -> line.startsWith("- ") } }.take(5)
        val body = itemLines.joinToString("\n")
        val full = if (body.isBlank()) header else "$header\n$body"
        return if (full.length <= TOOL_INFO_MAX_CHARS) full else full.take(TOOL_INFO_MAX_CHARS) + "…(已截断)"
    }

    /** 构建主对话 messages（四层在 user；system 仅锚点）。toolInfo 为 null 时不含【工具信息】段。 */
    private fun buildMainDialogueMessages(userMessage: String, imageUrlList: List<String>, toolInfo: String?): JsonArray {
        val aText = com.nongjiqianwen.ABLayerManager.getARoundsTextForMainDialogue()
        val bSum = com.nongjiqianwen.ABLayerManager.getBSummary()
        val messagesArray = JsonArray()
        messagesArray.add(JsonObject().apply {
            addProperty("role", "system")
            addProperty("content", com.nongjiqianwen.SystemAnchor.getAnchorForPreFill())
        })
        val layer1 = "【当前优先处理的问题】\n${userMessage.ifBlank { "" }}"
        val parts = mutableListOf<String>()
        parts.add(layer1.trim())
        if (aText.isNotBlank()) parts.add("【A层历史对话（中等参考性）】\n$aText")
        if (bSum.isNotBlank()) parts.add("【B层累计摘要（低参考性）】\n$bSum")
        if (toolInfo != null && toolInfo.isNotBlank()) parts.add("【工具信息（极低参考性）】\n${toolInfo.trim()}")
        val userTextContent = parts.joinToString("\n\n").trim()
        val userMessageObj = JsonObject().apply {
            addProperty("role", "user")
            val contentArray = JsonArray()
            imageUrlList.forEach { imageUrl ->
                if (imageUrl.isNotBlank() && (imageUrl.startsWith("http://") || imageUrl.startsWith("https://"))) {
                    contentArray.add(JsonObject().apply {
                        addProperty("type", "image_url")
                        add("image_url", JsonObject().apply { addProperty("url", imageUrl) })
                    })
                }
            }
            if (userTextContent.isNotBlank()) {
                contentArray.add(JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", userTextContent)
                })
            }
            add("content", contentArray)
        }
        messagesArray.add(userMessageObj)
        com.nongjiqianwen.SystemAnchor.ensureSystemRole(messagesArray)
        return messagesArray
    }

    /**
     * 调用通义千问 API（非流式）。会员路由：Flash/Plus/Pro 主对话=Flash，专家=PLUS；B 摘要固定 Flash。
     * 工具闭环：首次请求带 tools=web_search；若模型返回 tool_calls 则执行 web_search、二次请求带【工具信息】；否则直接返回首次内容。
     */
    fun callApi(
        userId: String,
        sessionId: String,
        requestId: String,
        streamId: String,
        userMessage: String,
        imageUrlList: List<String> = emptyList(),
        chatModel: String? = null,
        toolInfo: String? = null,
        onChunk: (String) -> Unit,
        onComplete: (() -> Unit)? = null,
        onInterrupted: (reason: String) -> Unit,
        onToolInfo: ((streamId: String, toolName: String, text: String) -> Unit)? = null,
        onInterruptedResumable: ((streamId: String, reason: String) -> Unit)? = null
    ) {
        val model = if (chatModel == "plus") modelPlus else modelFlash
        val effectiveClientMsgId = resolveClientMsgId(streamId, requestId)
        val startMs = System.currentTimeMillis()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId streamId=$streamId model=$model 开始")
        }
        val completed = AtomicBoolean(false)
        val fireComplete: () -> Unit = {
            if (completed.compareAndSet(false, true)) handler.post { onComplete?.invoke() }
        }
        canceledFlag.set(false)
        Thread {
            var call: Call? = null
            var phase = 0
            var outputCharCount = 0
            val phaseEnded = AtomicBoolean(false)
            var bochaMs = 0L
            var secondMs = 0L
            val inLen = userMessage.length
            val imgCount = imageUrlList.count { it.isNotBlank() && (it.startsWith("http://") || it.startsWith("https://")) }
            try {
                if (imgCount > 4) throw Exception("图片数量超过限制：$imgCount 张，最多4张")
                if (imgCount > 0 && userMessage.isBlank()) throw Exception("有图片时必须带文字描述")

                val useTools = (toolInfo == null)
                val messagesFirst = buildMainDialogueMessages(userMessage, imageUrlList, toolInfo)
                if (BuildConfig.DEBUG) {
                    val first = if (messagesFirst.size() > 0 && messagesFirst.get(0).isJsonObject) messagesFirst.get(0).asJsonObject else null
                    val role = first?.get("role")?.takeIf { it.isJsonPrimitive }?.asString
                    val content = first?.get("content")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
                    val trimmed = content.trim()
                    if (role != "system" || trimmed.isEmpty()) {
                        Log.w(TAG, "DEBUG: system role check failed role=$role content_preview=${content.take(60)}")
                    }
                    if (toolInfo != null && toolInfo.isNotBlank()) Log.d(TAG, "P0_SMOKE: 联网成功 工具信息（极低参考性）已注入本轮")
                    else Log.d(TAG, "P0_SMOKE: 主对话 ${if (useTools) "首次带 tools" else "未联网"}")
                }
                val requestBody = JsonObject().apply {
                    addProperty("model", model)
                    addProperty("stream", false)
                    addProperty("temperature", ModelParams.TEMPERATURE)
                    addProperty("top_p", ModelParams.TOP_P)
                    addProperty("max_tokens", ModelParams.MAX_TOKENS)
                    addProperty("frequency_penalty", ModelParams.FREQUENCY_PENALTY)
                    addProperty("presence_penalty", ModelParams.PRESENCE_PENALTY)
                    add("messages", messagesFirst)
                    if (useTools) add("tools", buildWebSearchTools())
                }
                val request1 = Request.Builder()
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .addHeader("X-User-Id", userId)
                    .addHeader("X-Session-Id", sessionId)
                    .addHeader("X-Request-Id", requestId)
                    .addHeader("X-Client-Msg-Id", effectiveClientMsgId)
                    .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                call = client.newCall(request1)
                currentCall.set(call)
                currentRequestId.set(requestId)
                val response1 = call!!.execute()
                val code1 = response1.code
                val body1 = response1.body?.string() ?: ""
                currentCall.set(null)
                currentRequestId.set(null)

                if (code1 != 200) {
                    Log.e(TAG, "callApi HTTP error status=$code1")
                    val body1Lower = body1.lowercase()
                    val isToolsUnsupported = (code1 == 400 || code1 == 422) || body1Lower.contains("unsupported tools") || body1Lower.contains("tools not allowed")
                    if (useTools && isToolsUnsupported) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "P0_SMOKE: first request tools unsupported retry without tools")
                        val bodyRetry = JsonObject().apply {
                            addProperty("model", model)
                            addProperty("stream", false)
                            addProperty("temperature", ModelParams.TEMPERATURE)
                            addProperty("top_p", ModelParams.TOP_P)
                            addProperty("max_tokens", ModelParams.MAX_TOKENS)
                            addProperty("frequency_penalty", ModelParams.FREQUENCY_PENALTY)
                            addProperty("presence_penalty", ModelParams.PRESENCE_PENALTY)
                            add("messages", messagesFirst)
                        }
                        val reqRetry = Request.Builder()
                            .url(apiUrl)
                            .addHeader("Authorization", "Bearer $apiKey")
                            .addHeader("Content-Type", "application/json")
                            .addHeader("Accept", "application/json")
                            .addHeader("X-User-Id", userId)
                            .addHeader("X-Session-Id", sessionId)
                            .addHeader("X-Request-Id", requestId)
                            .addHeader("X-Client-Msg-Id", effectiveClientMsgId)
                            .post(bodyRetry.toString().toRequestBody("application/json".toMediaType()))
                            .build()
                        val callRetry = client.newCall(reqRetry)
                        currentCall.set(callRetry)
                        try {
                            val rspRetry = callRetry.execute()
                            currentCall.set(null)
                            if (rspRetry.code != 200) {
                                handleErrorResponse(userId, sessionId, requestId, streamId, rspRetry.code, rspRetry.body?.string() ?: "", inLen, imgCount, outputCharCount, onInterrupted, fireComplete, onInterruptedResumable)
                                return@Thread
                            }
                            val jsonRetry = gson.fromJson(rspRetry.body?.string() ?: "", JsonObject::class.java)
                            val choicesRetry = jsonRetry.getAsJsonArray("choices")
                            val contentRetry = if (choicesRetry != null && choicesRetry.size() > 0) {
                                choicesRetry.get(0).asJsonObject.getAsJsonObject("message")?.get("content")?.takeIf { it.isJsonPrimitive }?.asString?.trim() ?: ""
                            } else ""
                            outputCharCount = contentRetry.length
                            handler.post {
                                if (!phaseEnded.compareAndSet(false, true)) return@post
                                if (contentRetry.isNotBlank()) emitFakeStream(contentRetry, onChunk, { canceledFlag.get() || phaseEnded.get() }, { fireComplete() })
                                else { onInterrupted("error"); fireComplete() }
                            }
                        } catch (e: Exception) {
                            currentCall.set(null)
                            val isCanceled = canceledFlag.get() || callRetry.isCanceled()
                            if (BuildConfig.DEBUG) Log.d(TAG, "P0_SMOKE: tools_unsupported_retry cancelled=$isCanceled")
                            handler.post {
                                if (!phaseEnded.compareAndSet(false, true)) return@post
                                if (onInterruptedResumable != null) onInterruptedResumable(streamId, if (isCanceled) "canceled" else "error")
                                else { if (isCanceled) onInterrupted("canceled") else onInterrupted("error"); fireComplete() }
                            }
                        }
                        return@Thread
                    }
                    handleErrorResponse(userId, sessionId, requestId, streamId, code1, body1, inLen, imgCount, outputCharCount, onInterrupted, fireComplete, onInterruptedResumable)
                    return@Thread
                }

                val json1 = gson.fromJson(body1, JsonObject::class.java)
                val choices1 = json1.getAsJsonArray("choices") ?: run {
                    handler.post { if (onInterruptedResumable != null) onInterruptedResumable(streamId, "error") else { onInterrupted("error"); fireComplete() } }
                    return@Thread
                }
                if (choices1.size() == 0) {
                    handler.post { if (onInterruptedResumable != null) onInterruptedResumable(streamId, "error") else { onInterrupted("error"); fireComplete() } }
                    return@Thread
                }
                val message1 = choices1.get(0).asJsonObject.getAsJsonObject("message") ?: run {
                    handler.post { if (onInterruptedResumable != null) onInterruptedResumable(streamId, "error") else { onInterrupted("error"); fireComplete() } }
                    return@Thread
                }
                val toolCalls = message1.getAsJsonArray("tool_calls")?.takeIf { it.size() > 0 }

                if (useTools && toolCalls != null) {
                    var selectedQuery: String? = null
                    for (i in 0 until toolCalls.size()) {
                        val tc = toolCalls.get(i).asJsonObject
                        val fn = tc.getAsJsonObject("function") ?: continue
                        if (fn.get("name")?.asString != "web_search") continue
                        val argsEl = fn.get("arguments") ?: continue
                        val argsObj = when {
                            argsEl.isJsonPrimitive && argsEl.asJsonPrimitive.isString ->
                                try { gson.fromJson(argsEl.asString, JsonObject::class.java) } catch (_: Exception) { if (BuildConfig.DEBUG) Log.d(TAG, "tool_calls arguments string parse fail"); continue }
                            argsEl.isJsonObject -> argsEl.asJsonObject
                            else -> { if (BuildConfig.DEBUG) Log.d(TAG, "tool_calls arguments unsupported type"); continue }
                        }
                        val q = argsObj.get("query")?.takeIf { it.isJsonPrimitive }?.asString?.trim() ?: continue
                        if (q.length >= 2) {
                            if (tryStartSearchForClientMsgId(effectiveClientMsgId)) {
                                selectedQuery = q
                            }
                            break
                        }
                    }
                    if (selectedQuery == null) {
                        val contentFirst = message1.get("content")?.takeIf { it.isJsonPrimitive }?.asString?.trim() ?: ""
                        if (contentFirst.isNotBlank()) {
                            if (BuildConfig.DEBUG) Log.d(TAG, "P0_SMOKE: phase=0 tool_calls=true parsed_queries_count=0 action=SKIP_TOOL_CALL show_tool_block=false cancelled=false")
                            outputCharCount = contentFirst.length
                            handler.post {
                                if (!phaseEnded.compareAndSet(false, true)) return@post
                                emitFakeStream(contentFirst, onChunk, { canceledFlag.get() || phaseEnded.get() }, { fireComplete() })
                            }
                            return@Thread
                        }
                        if (BuildConfig.DEBUG) Log.d(TAG, "P0_SMOKE: phase=0 tool_calls=true parsed_queries_count=0 action=SKIP_TOOL_CALL_FALLBACK_DIRECT_ANSWER show_tool_block=false cancelled=false")
                        val msgFb = buildMainDialogueMessages(userMessage, imageUrlList, null)
                        val bodyFb = JsonObject().apply {
                            addProperty("model", model); addProperty("stream", false)
                            addProperty("temperature", ModelParams.TEMPERATURE); addProperty("top_p", ModelParams.TOP_P)
                            addProperty("max_tokens", ModelParams.MAX_TOKENS)
                            addProperty("frequency_penalty", ModelParams.FREQUENCY_PENALTY); addProperty("presence_penalty", ModelParams.PRESENCE_PENALTY)
                            add("messages", msgFb)
                        }
                        val reqFb = Request.Builder().url(apiUrl)
                            .addHeader("Authorization", "Bearer $apiKey").addHeader("Content-Type", "application/json").addHeader("Accept", "application/json")
                            .addHeader("X-User-Id", userId).addHeader("X-Session-Id", sessionId).addHeader("X-Request-Id", requestId).addHeader("X-Client-Msg-Id", effectiveClientMsgId)
                            .post(bodyFb.toString().toRequestBody("application/json".toMediaType())).build()
                        val callFb = client.newCall(reqFb)
                        currentCall.set(callFb)
                        try {
                            val rspFb = callFb.execute()
                            currentCall.set(null)
                            if (rspFb.code != 200) {
                                handler.post { if (!phaseEnded.compareAndSet(false, true)) return@post; if (onInterruptedResumable != null) onInterruptedResumable(streamId, "server") else { onInterrupted("server"); fireComplete() } }
                                return@Thread
                            }
                            val jsFb = gson.fromJson(rspFb.body?.string() ?: "", JsonObject::class.java)
                            val choicesFb = jsFb.getAsJsonArray("choices")
                            val contentFb = if (choicesFb != null && choicesFb.size() > 0) choicesFb.get(0).asJsonObject.getAsJsonObject("message")?.get("content")?.takeIf { it.isJsonPrimitive }?.asString?.trim() ?: "" else ""
                            outputCharCount = contentFb.length
                            handler.post {
                                if (!phaseEnded.compareAndSet(false, true)) return@post
                                if (contentFb.isNotBlank()) emitFakeStream(contentFb, onChunk, { canceledFlag.get() || phaseEnded.get() }, { fireComplete() })
                                else { onInterrupted("error"); fireComplete() }
                            }
                        } catch (e: Exception) {
                            val isCanceled = canceledFlag.get() || callFb.isCanceled()
                            currentCall.set(null)
                            if (BuildConfig.DEBUG) Log.d(TAG, "P0_SMOKE: phase=0 tool_calls=true action=SKIP_TOOL_CALL_FALLBACK_DIRECT_ANSWER show_tool_block=false cancelled=$isCanceled")
                            handler.post {
                                if (!phaseEnded.compareAndSet(false, true)) return@post
                                if (onInterruptedResumable != null) onInterruptedResumable(streamId, if (isCanceled) "canceled" else "error")
                                else { if (isCanceled) onInterrupted("canceled") else onInterrupted("error"); fireComplete() }
                            }
                        }
                        return@Thread
                    }
                    phase = 1
                    currentBochaCall.set(null)
                    val bochaStartMs = System.currentTimeMillis()
                    val results = try {
                        selectedQuery?.let { listOf(BochaClient.webSearch(it, currentBochaCall)) } ?: emptyList()
                    } catch (_: Exception) {
                        emptyList()
                    } finally {
                        currentBochaCall.set(null)
                    }
                    bochaMs = System.currentTimeMillis() - bochaStartMs
                    val successTexts = results.filter { it.first && !it.second.isNullOrBlank() }.map { it.second!! }
                    val hasEffectiveResult = successTexts.isNotEmpty()
                    // P0: search daily cap (5/day), silent degrade
                    finishSearchAttempt(hasEffectiveResult)
                    val normalizedText = if (hasEffectiveResult) normalizeToolInfo(successTexts) else ""
                    val toolInfoFormatted = if (hasEffectiveResult) normalizedText else "联网搜索失败/未命中（仅供参考）"
                    val showToolBlock = hasEffectiveResult && normalizedText.isNotBlank()
                    val messagesSecond = buildMainDialogueMessages(userMessage, imageUrlList, toolInfoFormatted)
                    val body2 = JsonObject().apply {
                        addProperty("model", model)
                        addProperty("stream", false)
                        addProperty("temperature", ModelParams.TEMPERATURE)
                        addProperty("top_p", ModelParams.TOP_P)
                        addProperty("max_tokens", ModelParams.MAX_TOKENS)
                        addProperty("frequency_penalty", ModelParams.FREQUENCY_PENALTY)
                        addProperty("presence_penalty", ModelParams.PRESENCE_PENALTY)
                        add("messages", messagesSecond)
                    }
                    val request2 = Request.Builder()
                        .url(apiUrl)
                        .addHeader("Authorization", "Bearer $apiKey")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Accept", "application/json")
                        .addHeader("X-User-Id", userId)
                        .addHeader("X-Session-Id", sessionId)
                        .addHeader("X-Request-Id", requestId)
                        .addHeader("X-Client-Msg-Id", effectiveClientMsgId)
                        .post(body2.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                    phase = 2
                    val secondStartMs = System.currentTimeMillis()
                    val call2 = client.newCall(request2)
                    currentCall.set(call2)
                    val response2 = call2.execute()
                    currentCall.set(null)
                    secondMs = System.currentTimeMillis() - secondStartMs
                    val code2 = response2.code
                    val body2Str = response2.body?.string() ?: ""
                    if (code2 != 200) {
                        Log.e(TAG, "callApi 二次请求 HTTP error status=$code2")
                        if (BuildConfig.DEBUG) Log.d(TAG, "P0_SMOKE: phase=2 tool_calls=true bocha_ms=${bochaMs} second_ms=${secondMs} show_tool_block=$showToolBlock cancelled=false")
                        handler.post {
                            if (!phaseEnded.compareAndSet(false, true)) return@post
                            if (onInterruptedResumable != null) onInterruptedResumable(streamId, "server") else { onInterrupted("server"); fireComplete() }
                        }
                        return@Thread
                    }
                    val json2 = gson.fromJson(body2Str, JsonObject::class.java)
                    val choices2 = json2.getAsJsonArray("choices") ?: run {
                        handler.post {
                            if (!phaseEnded.compareAndSet(false, true)) return@post
                            if (onInterruptedResumable != null) onInterruptedResumable(streamId, "error") else { onInterrupted("error"); fireComplete() }
                        }
                        return@Thread
                    }
                    if (choices2.size() == 0) {
                        handler.post {
                            if (!phaseEnded.compareAndSet(false, true)) return@post
                            if (onInterruptedResumable != null) onInterruptedResumable(streamId, "error") else { onInterrupted("error"); fireComplete() }
                        }
                        return@Thread
                    }
                    val message2 = choices2.get(0).asJsonObject.getAsJsonObject("message") ?: run {
                        handler.post {
                            if (!phaseEnded.compareAndSet(false, true)) return@post
                            if (onInterruptedResumable != null) onInterruptedResumable(streamId, "error") else { onInterrupted("error"); fireComplete() }
                        }
                        return@Thread
                    }
                    val content = message2.get("content")?.takeIf { it.isJsonPrimitive }?.asString?.trim() ?: ""
                    outputCharCount = content.length
                    if (BuildConfig.DEBUG) Log.d(TAG, "P0_SMOKE: phase=2 tool_calls=true bocha_ms=${bochaMs} second_ms=${secondMs} show_tool_block=$showToolBlock cancelled=false")
                    handler.post {
                        if (!phaseEnded.compareAndSet(false, true)) return@post
                        emitFakeStream(content, onChunk, { canceledFlag.get() || phaseEnded.get() }, {
                            if (showToolBlock) onToolInfo?.invoke(streamId, "web_search", toolInfoFormatted)
                            fireComplete()
                        })
                    }
                    return@Thread
                }

                val content = message1.get("content")?.takeIf { it.isJsonPrimitive }?.asString?.trim() ?: ""
                outputCharCount = content.length
                val elapsed = System.currentTimeMillis() - startMs
                if (BuildConfig.DEBUG) Log.d(TAG, "P0_SMOKE: phase=0 tool_calls=false bocha_ms=0 second_ms=0 show_tool_block=false cancelled=false")
                handler.post {
                    if (!phaseEnded.compareAndSet(false, true)) return@post
                    emitFakeStream(content, onChunk, { canceledFlag.get() || phaseEnded.get() }, { fireComplete() })
                }
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startMs
                val isCanceled = canceledFlag.get() || currentCall.get()?.isCanceled() == true || currentBochaCall.get()?.isCanceled() == true
                val isInterrupted = e is SocketTimeoutException
                if (BuildConfig.DEBUG) Log.d(TAG, "P0_SMOKE: phase=$phase tool_calls=${phase >= 1} bocha_ms=$bochaMs second_ms=$secondMs show_tool_block=false cancelled=$isCanceled")
                if (phase == 2) {
                    handler.post {
                        if (!phaseEnded.compareAndSet(false, true)) return@post
                        if (onInterruptedResumable != null) onInterruptedResumable(streamId, "error") else { onInterrupted("error"); fireComplete() }
                    }
                } else {
                    if (isCanceled) {
                        Log.w(TAG, "callApi canceled, elapsed=${elapsed}ms")
                        handler.post {
                            if (!phaseEnded.compareAndSet(false, true)) return@post
                            if (onInterruptedResumable != null) onInterruptedResumable(streamId, "canceled") else { onInterrupted("canceled"); fireComplete() }
                        }
                    } else if (isInterrupted) {
                        Log.w(TAG, "callApi timeout, elapsed=${elapsed}ms")
                        handler.post {
                            if (!phaseEnded.compareAndSet(false, true)) return@post
                            if (onInterruptedResumable != null) onInterruptedResumable(streamId, "timeout") else { onInterrupted("timeout"); fireComplete() }
                        }
                    } else if (e is IOException) {
                        Log.e(TAG, "callApi network error, elapsed=${elapsed}ms", e)
                        handler.post {
                            if (!phaseEnded.compareAndSet(false, true)) return@post
                            if (onInterruptedResumable != null) onInterruptedResumable(streamId, "network") else { onInterrupted("network"); fireComplete() }
                        }
                    } else {
                        Log.e(TAG, "callApi error, elapsed=${elapsed}ms", e)
                        handler.post {
                            if (!phaseEnded.compareAndSet(false, true)) return@post
                            if (onInterruptedResumable != null) onInterruptedResumable(streamId, "error") else { onInterrupted("error"); fireComplete() }
                        }
                    }
                }
                return@Thread
            } finally {
                currentCall.set(null)
                currentBochaCall.set(null)
                currentRequestId.set(null)
            }
        }.start()
    }
    
    /**
     * 处理错误响应：仅 onInterrupted("error") + fireComplete，不写正文；日志含 userId/sessionId/requestId/streamId/HTTP
     */
    private fun handleErrorResponse(userId: String, sessionId: String, requestId: String, streamId: String, statusCode: Int, responseBody: String, inLen: Int, imgCount: Int, outputCharCount: Int, onInterrupted: (reason: String) -> Unit, fireComplete: () -> Unit, onInterruptedResumable: ((streamId: String, reason: String) -> Unit)? = null) {
        try {
            val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
            val errorCode = jsonResponse.get("code")?.asString ?: ""
            @Suppress("UNUSED_VARIABLE") val errorMessage = jsonResponse.get("message")?.asString ?: responseBody
            // 仅日志用，不写正文
        } catch (_: Exception) {
            Log.e(TAG, "callApi 无法解析错误响应 statusCode=$statusCode")
        }
        handler.post {
            if (onInterruptedResumable != null) onInterruptedResumable(streamId, "server") else { onInterrupted("server"); fireComplete() }
        }
    }

    /**
     * B 层摘要提取：非流式，同步返回摘要文本。
     * 参数冻结：temperature=0.85, top_p=0.9, max_tokens=4000, frequency_penalty=0, presence_penalty=0
     */
    fun extractBSummary(oldB: String, dialogueText: String, systemPrompt: String): String {
        return try {
            val userContent = if (oldB.isNotBlank()) {
                "[历史摘要]\n$oldB\n\n[对话]\n$dialogueText"
            } else {
                "[对话]\n$dialogueText"
            }
            // B 层提取必须保留 system role：system = b_extraction_prompt.txt；锚点剔旧仅作用于主对话，不约束此处
            if (BuildConfig.DEBUG) Log.d(TAG, "P0_SMOKE: B extract system=b_extraction_prompt")
            val messagesArray = com.google.gson.JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", systemPrompt)
                })
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", userContent)
                })
            }
            val body = JsonObject().apply {
                addProperty("model", modelFlash)  // B 层摘要固定 Flash，不随专家切换
                addProperty("stream", false)
                addProperty("temperature", B_EXTRACT_TEMPERATURE)
                addProperty("top_p", B_EXTRACT_TOP_P)
                addProperty("max_tokens", B_EXTRACT_MAX_TOKENS)
                addProperty("frequency_penalty", B_EXTRACT_FREQUENCY_PENALTY)
                addProperty("presence_penalty", B_EXTRACT_PRESENCE_PENALTY)
                add("messages", messagesArray)
            }
            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("B提取 HTTP ${response.code}")
                }
                val json = gson.fromJson(response.body?.string() ?: "", JsonObject::class.java)
                val choices = json.getAsJsonArray("choices") ?: return ""
                if (choices.size() == 0) return ""
                val msg = choices.get(0).asJsonObject.getAsJsonObject("message") ?: return ""
                val content = msg.get("content")?.takeIf { it.isJsonPrimitive }?.asString?.trim() ?: return ""
                return content
            }
        } catch (e: Exception) {
            // Release 节流：60s 内只打一条简短错误，避免 A>=30 每轮失败时刷屏
            val now = System.currentTimeMillis()
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "B提取异常，返回空字符串", e)
            } else if (now - lastBExtractErrorLogMs > B_EXTRACT_ERROR_LOG_INTERVAL_MS) {
                lastBExtractErrorLogMs = now
                Log.e(TAG, "B提取失败", e)
            }
            ""
        }
    }
}
