package com.nongjiqianwen

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
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 通义千问客户端
 * 主对话使用真 SSE 流式输出；支持取消与中断恢复。
 * 使用阿里云百炼 DashScope API。
 *
 * 会员路由（P0 冻结）：
 * - Free/Plus/Pro 主对话固定主模型；B 层摘要固定 qwen-flash。
 */
object QwenClient {
    private val TAG = "QwenClient"
    private const val CONNECT_TIMEOUT_SEC = 10L
    private const val READ_TIMEOUT_SEC = 60L
    private const val WRITE_TIMEOUT_SEC = 10L
    private const val CALL_TIMEOUT_SEC = 60L   // 单次请求总时长上限（1 分钟）
    private const val SSE_TTFT_TIMEOUT_MS = 8_000L
    private const val SSE_TOTAL_TIMEOUT_MS = 25_000L
    private const val SSE_CHUNK_THROTTLE_MS = 24L
    private const val B_EXTRACT_ERROR_LOG_INTERVAL_MS = 60_000L
    private const val B_EXTRACT_TEMPERATURE = 0.8
    private const val B_EXTRACT_TOP_P = 0.9
    private const val B_EXTRACT_FREQUENCY_PENALTY = 0.0
    private const val B_EXTRACT_PRESENCE_PENALTY = 0.0
    @Volatile private var lastBExtractErrorLogMs = 0L
    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val currentCall = AtomicReference<Call?>(null)
    private val currentRequestId = AtomicReference<String?>(null)
    private val clientMsgIdByStreamId = ConcurrentHashMap<String, String>()
    private val canceledFlag = AtomicBoolean(false)
    private val streamingRunnableRef = AtomicReference<Runnable?>(null)
    private val gson = Gson()
    private val apiKey = BuildConfig.API_KEY
    private val apiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
    private const val MODEL_MAIN = "qwen3.5-plus"
    private const val MODEL_B_SUMMARY = "qwen-flash"
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
            // requestId intentionally ignored; streamId -> clientMsgId mapping is authoritative.
        }
        return clientMsgIdByStreamId[streamId]?.takeIf { it.isNotBlank() } ?: ""
    }

    private var isFirstRequest = true
    
    init {
        if (BuildConfig.DEBUG) {
            val keyLength = apiKey.length
            Log.d(TAG, "=== DashScope API 初始化 ===")
            Log.d(TAG, "BAILIAN_API_KEY 读取状态: ${if (keyLength > 0) "成功" else "失败"} (长度: $keyLength)")
            Log.d(TAG, "API URL: $apiUrl")
        }
    }
    
    /**
     * 取消当前进行中的请求（仅在新请求开始前或用户点停止时调用；切后台不 cancel）
     */
    fun cancelCurrentRequest() {
        canceledFlag.set(true)
        streamingRunnableRef.getAndSet(null)?.let { handler.removeCallbacks(it) }
        val oldRequestId = currentRequestId.getAndSet(null)
        currentCall.getAndSet(null)?.cancel()
        if (BuildConfig.DEBUG && oldRequestId != null) {
            Log.d(TAG, "requestId=$oldRequestId canceled")
        }
    }

    /** 本地假流式：首段立刻吐，余下分批 postDelayed；取消或 phaseEnded 时停止。 */
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

    private data class StreamReadResult(
        val fullText: String,
        val usedEventStream: Boolean,
        val interruptedByCancel: Boolean = false
    )

    private fun readMainDialogueStreamOrFallback(
        response: Response,
        onChunk: (String) -> Unit,
        isCanceled: () -> Boolean,
        onTimeoutCancel: () -> Unit = {}
    ): StreamReadResult {
        val contentType = response.header("Content-Type")?.lowercase(Locale.ROOT).orEmpty()
        val isEventStream = contentType.contains("text/event-stream")
        val body = response.body ?: return StreamReadResult("", false)
        if (!isEventStream) {
            val bodyText = body.string()
            return try {
                val json = gson.fromJson(bodyText, JsonObject::class.java)
                val choices = json.getAsJsonArray("choices")
                val content = if (choices != null && choices.size() > 0) {
                    choices.get(0).asJsonObject.getAsJsonObject("message")
                        ?.get("content")
                        ?.takeIf { it.isJsonPrimitive }
                        ?.asString
                        ?.trim()
                        ?: ""
                } else ""
                StreamReadResult(content, false)
            } catch (_: Exception) {
                StreamReadResult("", false)
            }
        }

        val source = body.source()
        val buffer = StringBuilder()
        val raw = StringBuilder()
        val dataPayloads = mutableListOf<String>()
        val pending = StringBuilder()
        val pendingLock = Any()
        var flushScheduled = false
        var lastDispatchMs = 0L
        val startedAtMs = System.currentTimeMillis()
        var seenDataLine = false
        var seenValidChunk = false

        fun flushPendingNow() {
            val text = synchronized(pendingLock) {
                if (pending.isEmpty()) return
                val out = pending.toString()
                pending.setLength(0)
                lastDispatchMs = System.currentTimeMillis()
                out
            }
            handler.post { onChunk(text) }
        }

        fun appendChunk(piece: String) {
            if (piece.isBlank()) return
            synchronized(pendingLock) {
                pending.append(piece)
                val elapsed = System.currentTimeMillis() - lastDispatchMs
                if (elapsed >= SSE_CHUNK_THROTTLE_MS && !flushScheduled) {
                    val out = pending.toString()
                    pending.setLength(0)
                    lastDispatchMs = System.currentTimeMillis()
                    handler.post { onChunk(out) }
                    return
                }
                if (!flushScheduled) {
                    flushScheduled = true
                    val delay = (SSE_CHUNK_THROTTLE_MS - elapsed).coerceAtLeast(1L)
                    handler.postDelayed({
                        val out: String? = synchronized(pendingLock) {
                            flushScheduled = false
                            if (pending.isEmpty()) {
                                null
                            } else {
                                val text = pending.toString()
                                pending.setLength(0)
                                lastDispatchMs = System.currentTimeMillis()
                                text
                            }
                        }
                        if (!out.isNullOrEmpty()) onChunk(out)
                    }, delay)
                }
            }
        }

        return try {
            while (!source.exhausted()) {
                val elapsed = System.currentTimeMillis() - startedAtMs
                if (!seenValidChunk && elapsed > SSE_TTFT_TIMEOUT_MS) {
                    onTimeoutCancel()
                    return StreamReadResult(buffer.toString(), true, interruptedByCancel = true)
                }
                if (elapsed > SSE_TOTAL_TIMEOUT_MS) {
                    onTimeoutCancel()
                    return StreamReadResult(buffer.toString(), true, interruptedByCancel = true)
                }
                if (isCanceled()) {
                    return StreamReadResult(buffer.toString(), true, interruptedByCancel = true)
                }
                val line = source.readUtf8Line() ?: continue
                raw.append(line).append('\n')
                val normalized = line.trimStart()
                if (normalized.isBlank() || normalized.startsWith(":") || normalized.startsWith("event:")) continue
                if (!normalized.startsWith("data:")) continue
                seenDataLine = true
                val data = normalized.removePrefix("data:").trim()
                if (data == "[DONE]") break
                if (data.isBlank()) continue
                dataPayloads.add(data)
                try {
                    val json = gson.fromJson(data, JsonObject::class.java)
                    val choices = json.getAsJsonArray("choices") ?: continue
                    if (choices.size() == 0) continue
                    val choice0 = choices.get(0).asJsonObject
                    val delta = choice0.getAsJsonObject("delta")
                    val piece = when {
                        delta != null -> delta.get("content")
                            ?.takeIf { it.isJsonPrimitive }
                            ?.asString
                            .orEmpty()
                        else -> choice0.getAsJsonObject("message")
                            ?.get("content")
                            ?.takeIf { it.isJsonPrimitive }
                            ?.asString
                            .orEmpty()
                    }
                    if (piece.isBlank()) continue
                    seenValidChunk = true
                    buffer.append(piece)
                    appendChunk(piece)
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "SSE parse skip: ${e.message}")
                }
            }

            flushPendingNow()
            if (buffer.isNotEmpty()) {
                return StreamReadResult(buffer.toString(), true)
            }
            if (!seenDataLine || !seenValidChunk) {
                if (BuildConfig.DEBUG) {
                    Log.d(
                        TAG,
                        "SSE fallback triggered: seenDataLine=$seenDataLine, seenValidChunk=$seenValidChunk, " +
                            "bufferLen=${buffer.length}, payloads=${dataPayloads.size}"
                    )
                }
                val fallbackText = try {
                    val jsonCandidate = run {
                        var candidate: String? = null
                        for (payload in dataPayloads.asReversed()) {
                            try {
                                val payloadJson = gson.fromJson(payload, JsonObject::class.java) ?: continue
                                val payloadChoices = payloadJson.getAsJsonArray("choices") ?: continue
                                if (payloadChoices.size() == 0) continue
                                val choice0 = payloadChoices.get(0).asJsonObject
                                val hasDeltaContent = choice0.getAsJsonObject("delta")
                                    ?.get("content")
                                    ?.takeIf { it.isJsonPrimitive }
                                    ?.asString
                                    ?.trim()
                                    ?.isNotBlank() == true
                                val hasMessageContent = choice0.getAsJsonObject("message")
                                    ?.get("content")
                                    ?.takeIf { it.isJsonPrimitive }
                                    ?.asString
                                    ?.trim()
                                    ?.isNotBlank() == true
                                if (hasDeltaContent || hasMessageContent) {
                                    candidate = payload
                                    break
                                }
                            } catch (_: Exception) {
                            }
                        }
                        candidate ?: raw.toString().trim()
                    }
                    val json = gson.fromJson(jsonCandidate, JsonObject::class.java)
                    val choices = json?.getAsJsonArray("choices")
                    if (choices != null && choices.size() > 0) {
                        val choice0 = choices.get(0).asJsonObject
                        val deltaContent = choice0.getAsJsonObject("delta")
                            ?.get("content")
                            ?.takeIf { it.isJsonPrimitive }
                            ?.asString
                            ?.trim()
                            .orEmpty()
                        if (deltaContent.isNotBlank()) {
                            deltaContent
                        } else {
                            choice0.getAsJsonObject("message")
                                ?.get("content")
                                ?.takeIf { it.isJsonPrimitive }
                                ?.asString
                                ?.trim()
                                .orEmpty()
                        }
                    } else ""
                } catch (_: Exception) {
                    ""
                }
                return StreamReadResult(fallbackText.ifBlank { buffer.toString() }, false)
            }
            StreamReadResult(buffer.toString(), true)
        } finally {
            try { source.close() } catch (_: Exception) {}
            try { body.close() } catch (_: Exception) {}
        }
    }

    /** 构建主对话 messages（四层在 user；system 仅锚点）。 */
    private fun buildMainDialogueMessages(userMessage: String, imageUrlList: List<String>): JsonArray {
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
     * 调用通义千问 API。会员路由：Free/Plus/Pro 主对话固定 MODEL_MAIN，B 摘要固定 MODEL_B_SUMMARY。
     */
    fun callApi(
        userId: String,
        sessionId: String,
        requestId: String,
        streamId: String,
        userMessage: String,
        imageUrlList: List<String> = emptyList(),
        chatModel: String? = null,
        onChunk: (String) -> Unit,
        onComplete: (() -> Unit)? = null,
        onInterrupted: (reason: String) -> Unit,
        onInterruptedResumable: ((streamId: String, reason: String) -> Unit)? = null
    ) {
        val model = MODEL_MAIN
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
            var outputCharCount = 0
            val phaseEnded = AtomicBoolean(false)
            val inLen = userMessage.length
            val imgCount = imageUrlList.count { it.isNotBlank() && (it.startsWith("http://") || it.startsWith("https://")) }
            try {
                if (imgCount > 4) throw Exception("图片数量超过限制：$imgCount 张，最多4张")
                if (imgCount > 0 && userMessage.isBlank()) throw Exception("有图片时必须带文字描述")

                val messages = buildMainDialogueMessages(userMessage, imageUrlList)
                val requestBody = JsonObject().apply {
                    addProperty("model", model)
                    addProperty("stream", true)
                    addProperty("temperature", ModelParams.TEMPERATURE)
                    addProperty("top_p", ModelParams.TOP_P)
                    addProperty("frequency_penalty", ModelParams.FREQUENCY_PENALTY)
                    addProperty("presence_penalty", ModelParams.PRESENCE_PENALTY)
                    add("extra_body", JsonObject().apply {
                        addProperty("enable_thinking", false)
                    })
                    add("messages", messages)
                }
                val request = Request.Builder()
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "text/event-stream")
                    .addHeader("Cache-Control", "no-cache")
                    .addHeader("Accept-Encoding", "identity")
                    .addHeader("X-User-Id", userId)
                    .addHeader("X-Session-Id", sessionId)
                    .addHeader("X-Request-Id", requestId)
                    .addHeader("X-Client-Msg-Id", effectiveClientMsgId)
                    .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                call = client.newCall(request)
                currentCall.set(call)
                currentRequestId.set(requestId)

                var code = 0
                var body = ""
                var streamResult = StreamReadResult("", false)
                call.execute().use { response ->
                    code = response.code
                    if (code == 200) {
                        streamResult = readMainDialogueStreamOrFallback(
                            response = response,
                            onChunk = onChunk,
                            isCanceled = { canceledFlag.get() || phaseEnded.get() || call?.isCanceled() == true },
                            onTimeoutCancel = { call?.cancel() }
                        )
                    } else {
                        body = response.body?.string() ?: ""
                    }
                }
                currentCall.set(null)
                currentRequestId.set(null)

                if (code != 200) {
                    Log.e(TAG, "callApi HTTP error status=$code")
                    handleErrorResponse(userId, sessionId, requestId, streamId, code, body, inLen, imgCount, outputCharCount, onInterrupted, fireComplete, onInterruptedResumable)
                    return@Thread
                }

                outputCharCount = streamResult.fullText.length
                handler.post {
                    if (phaseEnded.get()) return@post
                    when {
                        streamResult.interruptedByCancel -> {
                            if (!phaseEnded.compareAndSet(false, true)) return@post
                            if (onInterruptedResumable != null) onInterruptedResumable(streamId, "canceled")
                            else {
                                onInterrupted("canceled")
                                fireComplete()
                            }
                        }
                        streamResult.usedEventStream -> {
                            if (!phaseEnded.compareAndSet(false, true)) return@post
                            fireComplete()
                        }
                        streamResult.fullText.trim().isNotBlank() -> {
                            val contentFallback = streamResult.fullText.trim()
                            emitFakeStream(contentFallback, onChunk, { canceledFlag.get() || phaseEnded.get() }, {
                                if (!phaseEnded.compareAndSet(false, true)) return@emitFakeStream
                                fireComplete()
                            })
                        }
                        else -> {
                            if (!phaseEnded.compareAndSet(false, true)) return@post
                            if (onInterruptedResumable != null) onInterruptedResumable(streamId, "error")
                            else {
                                onInterrupted("error")
                                fireComplete()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startMs
                val isCanceled = canceledFlag.get() || currentCall.get()?.isCanceled() == true
                val isInterrupted = e is SocketTimeoutException
                if (isCanceled) {
                    Log.w(TAG, "callApi canceled, elapsed=${elapsed}ms")
                    handler.post {
                        if (!phaseEnded.compareAndSet(false, true)) return@post
                        if (onInterruptedResumable != null) onInterruptedResumable(streamId, "canceled")
                        else {
                            onInterrupted("canceled")
                            fireComplete()
                        }
                    }
                } else if (isInterrupted) {
                    Log.w(TAG, "callApi timeout, elapsed=${elapsed}ms")
                    handler.post {
                        if (!phaseEnded.compareAndSet(false, true)) return@post
                        if (onInterruptedResumable != null) onInterruptedResumable(streamId, "timeout")
                        else {
                            onInterrupted("timeout")
                            fireComplete()
                        }
                    }
                } else if (e is IOException) {
                    Log.e(TAG, "callApi network error, elapsed=${elapsed}ms", e)
                    handler.post {
                        if (!phaseEnded.compareAndSet(false, true)) return@post
                        if (onInterruptedResumable != null) onInterruptedResumable(streamId, "network")
                        else {
                            onInterrupted("network")
                            fireComplete()
                        }
                    }
                } else {
                    Log.e(TAG, "callApi error, elapsed=${elapsed}ms", e)
                    handler.post {
                        if (!phaseEnded.compareAndSet(false, true)) return@post
                        if (onInterruptedResumable != null) onInterruptedResumable(streamId, "error")
                        else {
                            onInterrupted("error")
                            fireComplete()
                        }
                    }
                }
                return@Thread
            } finally {
                currentCall.set(null)
                currentRequestId.set(null)
            }
        }.start()
    }
    private fun handleErrorResponse(userId: String, sessionId: String, requestId: String, streamId: String, statusCode: Int, responseBody: String, inLen: Int, imgCount: Int, outputCharCount: Int, onInterrupted: (reason: String) -> Unit, fireComplete: () -> Unit, onInterruptedResumable: ((streamId: String, reason: String) -> Unit)? = null) {
        try {
            val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
            val errorCode = jsonResponse.get("code")?.asString ?: ""
            @Suppress("UNUSED_VARIABLE") val errorMessage = jsonResponse.get("message")?.asString ?: responseBody
            // 仅日志用，不写正式字段
        } catch (_: Exception) {
            Log.e(TAG, "callApi 无法解析错误响应 statusCode=$statusCode")
        }
        handler.post {
            if (onInterruptedResumable != null) onInterruptedResumable(streamId, "server") else { onInterrupted("server"); fireComplete() }
        }
    }

    /**
     * B 层摘要提取：非流式，同步返回摘要文本
     * 参数冻结：temperature=0.85, top_p=0.9, frequency_penalty=0, presence_penalty=0
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
                addProperty("model", MODEL_B_SUMMARY)  // B 层摘要固定模型
                addProperty("stream", false)
                addProperty("temperature", B_EXTRACT_TEMPERATURE)
                addProperty("top_p", B_EXTRACT_TOP_P)
                addProperty("frequency_penalty", B_EXTRACT_FREQUENCY_PENALTY)
                addProperty("presence_penalty", B_EXTRACT_PRESENCE_PENALTY)
                add("extra_body", JsonObject().apply {
                    addProperty("enable_thinking", false)
                })
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
            // Release 节流：60s 内只打一条简短错误，避免高频刷屏
            val now = System.currentTimeMillis()
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "B summary extract failed, return empty", e)
            } else if (now - lastBExtractErrorLogMs > B_EXTRACT_ERROR_LOG_INTERVAL_MS) {
                lastBExtractErrorLogMs = now
                Log.e(TAG, "B提取失败", e)
            }
            ""
        }
    }
}



