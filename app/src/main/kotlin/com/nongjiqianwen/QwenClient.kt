package com.nongjiqianwen

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 通义千问客户端
 * 负责 HTTP 请求和 JSON 解析；支持取消、读超时（Watchdog）、callTimeout。
 * 使用阿里云百炼 DashScope API
 */
object QwenClient {
    private val TAG = "QwenClient"
    private const val CONNECT_TIMEOUT_SEC = 30L
    /** 连续 X 秒无 chunk 才判定中断；chunk 到来（每次 read 成功）即刷新计时器。与 readTimeout 对齐。 */
    private const val READ_TIMEOUT_SEC = 30L
    private const val CALL_TIMEOUT_SEC = 120L  // 单次请求总时长上限
    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val currentCall = AtomicReference<Call?>(null)
    private val currentRequestId = AtomicReference<String?>(null)
    private val gson = Gson()
    private val apiKey = BuildConfig.API_KEY
    private val apiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
    private val model = "qwen3-vl-flash"
    private val handler = Handler(Looper.getMainLooper())
    private var isFirstRequest = true
    
    init {
        // 启动或首次请求时打印调试信息
        val keyLength = apiKey.length
        Log.d(TAG, "=== DashScope API 初始化 ===")
        Log.d(TAG, "BAILIAN_API_KEY 读取状态: ${if (keyLength > 0) "成功" else "失败"} (长度: $keyLength)")
        Log.d(TAG, "当前使用的 model: $model")
        Log.d(TAG, "API URL: $apiUrl")
    }
    
    /**
     * 取消当前进行中的请求（仅在新请求开始前或用户点停止时调用；切后台不 cancel）。
     */
    fun cancelCurrentRequest() {
        val oldRequestId = currentRequestId.getAndSet(null)
        currentCall.getAndSet(null)?.cancel()
        if (oldRequestId != null) Log.d(TAG, "requestId=$oldRequestId 被取消（新请求/用户停止）")
    }

    /**
     * 调用通义千问 API（真流式 SSE）
     * userId/sessionId/requestId/streamId 仅用于日志与请求 header，严禁写入 prompt/messages。
     * 终态不写正文：仅 onInterrupted(reason) 用于 badge，不通过 onChunk 追加“已取消/已中断”等。
     */
    fun callApi(
        userId: String,
        sessionId: String,
        requestId: String,
        streamId: String,
        userMessage: String,
        imageUrlList: List<String> = emptyList(),
        onChunk: (String) -> Unit,
        onComplete: (() -> Unit)? = null,
        onInterrupted: (reason: String) -> Unit
    ) {
        val startMs = System.currentTimeMillis()
        Log.d(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId streamId=$streamId 开始")
        val completed = AtomicBoolean(false)
        val fireComplete: () -> Unit = {
            if (completed.compareAndSet(false, true)) handler.post { onComplete?.invoke() }
        }
        Thread {
            var call: Call? = null
            var outputCharCount = 0
            val inLen = userMessage.length
            val imgCount = imageUrlList.count { it.isNotBlank() && (it.startsWith("http://") || it.startsWith("https://")) }
            try {
                // 构建请求体 - OpenAI 兼容格式（真流式SSE）
                val requestBody = JsonObject().apply {
                    addProperty("model", model)
                    addProperty("stream", true)
                    
                    val messagesArray = com.google.gson.JsonArray()
                    val userMessageObj = JsonObject().apply {
                        addProperty("role", "user")
                        
                        // content 必须是数组格式
                        val contentArray = com.google.gson.JsonArray()
                        
                        // 1. 添加图片项（如果有图片URL，图在前）
                        imageUrlList.forEach { imageUrl ->
                            if (imageUrl.isNotBlank() && (imageUrl.startsWith("http://") || imageUrl.startsWith("https://"))) {
                                val imageItem = JsonObject().apply {
                                    addProperty("type", "image_url")
                                    val imageUrlObj = JsonObject().apply {
                                        addProperty("url", imageUrl)
                                    }
                                    add("image_url", imageUrlObj)
                                }
                                contentArray.add(imageItem)
                            }
                        }
                        
                        // 2. 添加文本项（文字在后，必须带文字）
                        if (userMessage.isNotBlank()) {
                            val textItem = JsonObject().apply {
                                addProperty("type", "text")
                                addProperty("text", userMessage)
                            }
                            contentArray.add(textItem)
                        }
                        
                        add("content", contentArray)
                    }
                    messagesArray.add(userMessageObj)
                    add("messages", messagesArray)
                }
                
                Log.d(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId streamId=$streamId 摘要: 图数=$imgCount 字符数=$inLen")
                
                if (imgCount > 4) throw Exception("图片数量超过限制：$imgCount 张，最多4张")
                if (imgCount > 0 && userMessage.isBlank()) throw Exception("有图片时必须带文字描述")
                
                val requestJsonString = requestBody.toString()
                val request = Request.Builder()
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "text/event-stream")
                    .addHeader("X-User-Id", userId)
                    .addHeader("X-Session-Id", sessionId)
                    .addHeader("X-Request-Id", requestId)
                    .post(requestJsonString.toRequestBody("application/json".toMediaType()))
                    .build()
                
                call = client.newCall(request)
                currentCall.set(call)
                currentRequestId.set(requestId)
                val response = call!!.execute()
                val statusCode = response.code
                
                Log.d(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId streamId=$streamId HTTP=$statusCode")
                
                if (response.isSuccessful) {
                    try {
                        val responseBody = response.body
                        if (responseBody == null) {
                            throw Exception("响应体为空")
                        }
                        
                        val inputStream = responseBody.byteStream()
                        val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
                        var lastFinishReason: String? = null
                        try {
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.startsWith("data: ")) {
                                    if (line.trim() == "data: [DONE]") {
                                        Log.d(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId streamId=$streamId STREAM_DONE finish_reason=$lastFinishReason")
                                        break
                                    }
                                    val jsonStr = line.substring(6).trim()
                                    if (jsonStr.isEmpty() || jsonStr == "{}") continue
                                    try {
                                        val jsonResponse = gson.fromJson(jsonStr, JsonObject::class.java)
                                        if (jsonResponse.has("choices") && jsonResponse.getAsJsonArray("choices").size() > 0) {
                                            val firstChoice = jsonResponse.getAsJsonArray("choices").get(0).asJsonObject
                                            firstChoice.get("finish_reason")?.takeIf { it.isJsonPrimitive }?.asString?.let { lastFinishReason = it }
                                            if (firstChoice.has("delta")) {
                                                val delta = firstChoice.getAsJsonObject("delta")
                                                    if (delta.has("content")) {
                                                        val content = delta.get("content")
                                                        if (content.isJsonPrimitive && content.asJsonPrimitive.isString) {
                                                            val deltaText = content.asString
                                                            if (deltaText.isNotBlank()) {
                                                                if (currentRequestId.get() != requestId) {
                                                                    Log.d(TAG, "drop stale chunk")
                                                                    continue
                                                                }
                                                                outputCharCount += deltaText.length
                                                                handler.post { onChunk(deltaText) }
                                                            }
                                                        }
                                                    }
                                            }
                                        }
                                    } catch (_: Exception) { /* 忽略单行解析错误 */ }
                                }
                            }
                            val elapsed = System.currentTimeMillis() - startMs
                            Log.d(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId streamId=$streamId 状态=complete reason=complete finish_reason=$lastFinishReason 耗时=${elapsed}ms 入=$inLen img=$imgCount 出=$outputCharCount")
                            fireComplete()
                        } finally {
                            reader.close()
                            inputStream.close()
                            currentCall.set(null)
                            currentRequestId.set(null)
                        }
                    } catch (e: Exception) {
                        currentCall.set(null)
                        currentRequestId.set(null)
                        val elapsed = System.currentTimeMillis() - startMs
                        val isCanceled = e.message?.contains("Canceled", ignoreCase = true) == true
                        val isInterrupted = e is SocketTimeoutException
                        if (isCanceled) {
                            Log.w(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId streamId=$streamId 状态=canceled reason=canceled 耗时=${elapsed}ms 入=$inLen img=$imgCount 出=$outputCharCount")
                            handler.post {
                                onInterrupted("canceled")
                                fireComplete()
                            }
                        } else if (isInterrupted) {
                            Log.w(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId streamId=$streamId 状态=interrupted reason=timeout 耗时=${elapsed}ms 入=$inLen img=$imgCount 出=$outputCharCount")
                            handler.post {
                                onInterrupted("interrupted")
                                fireComplete()
                            }
                        } else {
                            Log.e(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId streamId=$streamId 状态=error reason=error 耗时=${elapsed}ms 入=$inLen img=$imgCount 出=$outputCharCount", e)
                            handler.post {
                                onInterrupted("error")
                                fireComplete()
                            }
                        }
                        return@Thread
                    }
                } else {
                    currentCall.set(null)
                    currentRequestId.set(null)
                    val responseBody = response.body?.string() ?: ""
                    val elapsed = System.currentTimeMillis() - startMs
                    Log.e(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId streamId=$streamId 状态=error HTTP=$statusCode 耗时=${elapsed}ms 入=$inLen img=$imgCount 出=$outputCharCount")
                    handleErrorResponse(userId, sessionId, requestId, streamId, statusCode, responseBody, inLen, imgCount, outputCharCount, onInterrupted, fireComplete)
                }
            } catch (e: IOException) {
                currentCall.set(null)
                currentRequestId.set(null)
                val elapsed = System.currentTimeMillis() - startMs
                val isCanceled = e.message?.contains("Canceled", ignoreCase = true) == true
                val isInterrupted = e is SocketTimeoutException
                if (isCanceled) {
                    Log.w(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId streamId=$streamId 状态=canceled reason=canceled 耗时=${elapsed}ms 入=$inLen img=$imgCount 出=$outputCharCount")
                    handler.post {
                        onInterrupted("canceled")
                        fireComplete()
                    }
                } else if (isInterrupted) {
                    Log.w(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId streamId=$streamId 状态=interrupted reason=timeout 耗时=${elapsed}ms 入=$inLen img=$imgCount 出=$outputCharCount")
                    handler.post {
                        onInterrupted("interrupted")
                        fireComplete()
                    }
                } else {
                    Log.e(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId streamId=$streamId 状态=error reason=error 耗时=${elapsed}ms 入=$inLen img=$imgCount 出=$outputCharCount", e)
                    handler.post {
                        onInterrupted("error")
                        fireComplete()
                    }
                }
            } catch (e: Exception) {
                currentCall.set(null)
                currentRequestId.set(null)
                val elapsed = System.currentTimeMillis() - startMs
                val inC = try { userMessage.length } catch (_: Exception) { 0 }
                val imgC = try { imageUrlList.count { it.isNotBlank() && (it.startsWith("http://") || it.startsWith("https://")) } } catch (_: Exception) { 0 }
                Log.e(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId streamId=$streamId 状态=error reason=error 耗时=${elapsed}ms 入=$inC img=$imgC 出=$outputCharCount", e)
                handler.post {
                    onInterrupted("error")
                    fireComplete()
                }
            }
        }.start()
    }
    
    /**
     * 处理错误响应：仅 onInterrupted("error") + fireComplete，不写正文；日志含 userId/sessionId/requestId/streamId/HTTP
     */
    private fun handleErrorResponse(userId: String, sessionId: String, requestId: String, streamId: String, statusCode: Int, responseBody: String, inLen: Int, imgCount: Int, outputCharCount: Int, onInterrupted: (reason: String) -> Unit, fireComplete: () -> Unit) {
        try {
            val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
            val errorCode = jsonResponse.get("code")?.asString ?: ""
            @Suppress("UNUSED_VARIABLE") val errorMessage = jsonResponse.get("message")?.asString ?: responseBody
            // 仅日志用，不写正文
        } catch (_: Exception) {
            Log.e(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId streamId=$streamId 无法解析错误响应 statusCode=$statusCode")
        }
        handler.post {
            onInterrupted("error")
            fireComplete()
        }
    }
}
