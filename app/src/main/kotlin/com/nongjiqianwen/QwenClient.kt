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
import java.util.concurrent.atomic.AtomicInteger
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
    private val runtimeGeneration = AtomicInteger(0)
    private val gson = Gson()
    private val apiKey = BuildConfig.API_KEY
    private val apiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
    private const val MODEL_MAIN = "qwen3.5-plus"
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

    fun resetUiRuntimeForCleanState() {
        runtimeGeneration.incrementAndGet()
        canceledFlag.set(true)
        streamingRunnableRef.getAndSet(null)?.let { handler.removeCallbacks(it) }
        handler.removeCallbacksAndMessages(null)
        currentRequestId.set(null)
        currentCall.getAndSet(null)?.cancel()
        clientMsgIdByStreamId.clear()
        isFirstRequest = true
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
            handler.post {
                if (isCanceled()) return@post
                onChunk(text)
            }
        }

        fun appendChunk(piece: String) {
            if (piece.isEmpty()) return
            synchronized(pendingLock) {
                pending.append(piece)
                val elapsed = System.currentTimeMillis() - lastDispatchMs
                if (elapsed >= SSE_CHUNK_THROTTLE_MS && !flushScheduled) {
                    val out = pending.toString()
                    pending.setLength(0)
                    lastDispatchMs = System.currentTimeMillis()
                    handler.post {
                        if (isCanceled()) return@post
                        onChunk(out)
                    }
                    return
                }
                if (!flushScheduled) {
                    flushScheduled = true
                    val delay = (SSE_CHUNK_THROTTLE_MS - elapsed).coerceAtLeast(1L)
                    handler.postDelayed({
                        if (isCanceled()) return@postDelayed
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
                        if (!out.isNullOrEmpty() && !isCanceled()) onChunk(out)
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
                    if (piece.isEmpty()) continue
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
                        for (index in dataPayloads.lastIndex downTo 0) {
                            val payload = dataPayloads[index]
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

    /** 构建主对话 messages：历史/摘要/锚点以后端注入为准。 */
    private fun buildMainDialogueMessages(userMessage: String, imageUrlList: List<String>): JsonArray {
        val messagesArray = JsonArray()
        val hasValidImages = imageUrlList.any { imageUrl ->
            imageUrl.isNotBlank() && (imageUrl.startsWith("http://") || imageUrl.startsWith("https://"))
        }
        val effectiveUserText = when {
            userMessage.isNotBlank() -> userMessage
            hasValidImages -> "用户本轮只上传了图片，未补充文字描述。请先基于图片可见信息给出参考判断，并追问必要信息。"
            else -> ""
        }
        val userTextContent = "【当前优先处理的问题】\n$effectiveUserText".trim()
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
        return messagesArray
    }

    /**
     * 调用通义千问 API。会员路由：Free/Plus/Pro 主对话固定 MODEL_MAIN，B/C 摘要固定 qwen-flash。
     */
    fun callApi(
        userId: String,
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
        val requestGeneration = runtimeGeneration.get()
        fun isRuntimeStale(): Boolean = requestGeneration != runtimeGeneration.get()
        val effectiveClientMsgId = resolveClientMsgId(streamId, requestId)
        val startMs = System.currentTimeMillis()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "userId=$userId requestId=$requestId streamId=$streamId model=$model 开始")
        }
        val completed = AtomicBoolean(false)
        val fireComplete: () -> Unit = {
            if (completed.compareAndSet(false, true)) {
                handler.post {
                    if (isRuntimeStale()) return@post
                    onComplete?.invoke()
                }
            }
        }
        canceledFlag.set(false)
        Thread {
            var call: Call? = null
            var outputCharCount = 0
            val phaseEnded = AtomicBoolean(false)
            val inLen = userMessage.length
            val imgCount = imageUrlList.count { it.isNotBlank() && (it.startsWith("http://") || it.startsWith("https://")) }
            try {
                if (isRuntimeStale()) return@Thread
                if (imgCount > 4) throw Exception("图片数量超过限制：$imgCount 张，最多4张")

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
                            isCanceled = { canceledFlag.get() || phaseEnded.get() || call?.isCanceled() == true || isRuntimeStale() },
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
                    handleErrorResponse(
                        userId,
                        requestId,
                        streamId,
                        code,
                        body,
                        inLen,
                        imgCount,
                        outputCharCount,
                        onInterrupted,
                        fireComplete,
                        onInterruptedResumable,
                        shouldDeliver = { !isRuntimeStale() }
                    )
                    return@Thread
                }

                outputCharCount = streamResult.fullText.length
                handler.post {
                    if (isRuntimeStale()) return@post
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
                            emitFakeStream(contentFallback, onChunk, { canceledFlag.get() || phaseEnded.get() || isRuntimeStale() }, {
                                if (isRuntimeStale()) return@emitFakeStream
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
                        if (isRuntimeStale()) return@post
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
                        if (isRuntimeStale()) return@post
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
                        if (isRuntimeStale()) return@post
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
                        if (isRuntimeStale()) return@post
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
    private fun handleErrorResponse(userId: String, requestId: String, streamId: String, statusCode: Int, responseBody: String, inLen: Int, imgCount: Int, outputCharCount: Int, onInterrupted: (reason: String) -> Unit, fireComplete: () -> Unit, onInterruptedResumable: ((streamId: String, reason: String) -> Unit)? = null, shouldDeliver: () -> Boolean = { true }) {
        var reason = "server"
        try {
            val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
            val errorCode = jsonResponse.get("code")?.asString ?: ""
            @Suppress("UNUSED_VARIABLE") val errorMessage = jsonResponse.get("message")?.asString ?: responseBody
            // 仅日志用，不写正式字段
            reason = when {
                statusCode == 503 -> "model_unavailable"
                statusCode == 429 || errorCode.equals("RATE_LIMITED", ignoreCase = true) -> "rate_limit"
                statusCode == 402 -> "quota"
                else -> "server"
            }
        } catch (_: Exception) {
            Log.e(TAG, "callApi 无法解析错误响应 statusCode=$statusCode")
            reason = when (statusCode) {
                503 -> "model_unavailable"
                429 -> "rate_limit"
                402 -> "quota"
                else -> "server"
            }
        }
        handler.post {
            if (!shouldDeliver()) return@post
            if (onInterruptedResumable != null) onInterruptedResumable(streamId, reason) else { onInterrupted(reason); fireComplete() }
        }
    }

}
