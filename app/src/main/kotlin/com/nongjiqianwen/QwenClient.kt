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
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 通义千问客户端
 * 非流式：一次性返回完整答案；取消/停止仅占位，不要求真正中断。
 * 使用阿里云百炼 DashScope API
 */
object QwenClient {
    private val TAG = "QwenClient"
    private const val CONNECT_TIMEOUT_SEC = 10L
    private const val READ_TIMEOUT_SEC = 60L
    private const val WRITE_TIMEOUT_SEC = 10L
    private const val CALL_TIMEOUT_SEC = 60L   // 单次请求总时长上限（1 分钟体验）
    private const val B_EXTRACT_ERROR_LOG_INTERVAL_MS = 60_000L
    @Volatile private var lastBExtractErrorLogMs = 0L
    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val currentCall = AtomicReference<Call?>(null)
    private val currentRequestId = AtomicReference<String?>(null)
    private val gson = Gson()
    private val apiKey = BuildConfig.API_KEY
    private val apiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
    private val modelFlash = "qwen3-vl-flash"
    private val modelPlus = "qwen3-vl-plus"
    private val handler = Handler(Looper.getMainLooper())
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
        val oldRequestId = currentRequestId.getAndSet(null)
        currentCall.getAndSet(null)?.cancel()
        if (BuildConfig.DEBUG && oldRequestId != null) {
            Log.d(TAG, "requestId=$oldRequestId 被取消（新请求/用户停止）")
        }
    }

    /**
     * 调用通义千问 API（非流式，一次返回完整 response_text）
     * 返回：一次 onChunk(完整文本) + onComplete；错误时 onInterrupted(reason)。
     * stop/cancel 占位保留，当前不要求真正中断。
     */
    /**
     * @param chatModel 主对话模型：传 "plus" 为专家模式（qwen3-vl-plus），否则 qwen3-vl-flash。B 层摘要不由此控制。
     */
    fun callApi(
        userId: String,
        sessionId: String,
        requestId: String,
        streamId: String,
        systemAnchorText: String,
        userMessage: String,
        imageUrlList: List<String> = emptyList(),
        chatModel: String? = null,
        onChunk: (String) -> Unit,
        onComplete: (() -> Unit)? = null,
        onInterrupted: (reason: String) -> Unit
    ) {
        val model = if (chatModel == "plus") modelPlus else modelFlash
        val startMs = System.currentTimeMillis()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId streamId=$streamId 开始")
        }
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
                // 构建请求体 - 非流式，一次返回完整内容
                val requestBody = JsonObject().apply {
                    addProperty("model", model)  // 专家=qwen3-vl-plus，其余=qwen3-vl-flash
                    addProperty("stream", false)
                    addProperty("temperature", ModelParams.TEMPERATURE)
                    addProperty("top_p", ModelParams.TOP_P)
                    addProperty("max_tokens", ModelParams.MAX_TOKENS)
                    addProperty("frequency_penalty", ModelParams.FREQUENCY_PENALTY)
                    addProperty("presence_penalty", ModelParams.PRESENCE_PENALTY)
                    val messagesArray = com.google.gson.JsonArray()
                    var systemContent = systemAnchorText
                    val bSum = com.nongjiqianwen.ABLayerManager.getBSummary()
                    if (bSum.isNotBlank()) {
                        systemContent = if (systemContent.isNotBlank()) "$systemContent\n\n[B层累计摘要]\n$bSum" else "[B层累计摘要]\n$bSum"
                    }
                    if (systemContent.isNotBlank()) {
                        val systemObj = JsonObject().apply {
                            addProperty("role", "system")
                            addProperty("content", systemContent)
                        }
                        messagesArray.add(systemObj)
                    }
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
                    com.nongjiqianwen.SystemAnchor.ensureSystemRole(messagesArray)
                    if (bSum.isNotBlank() && messagesArray.size() > 0) {
                        val first = messagesArray.get(0).asJsonObject
                        if (first.get("role")?.asString == "system") {
                            var cur = first.get("content")?.asString ?: ""
                            if (cur.contains("[B层累计摘要]")) {
                                cur = cur.substringBefore("[B层累计摘要]").trimEnd()
                            }
                            first.addProperty("content", "$cur\n\n[B层累计摘要]\n$bSum")
                        }
                    }
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
                    .addHeader("Accept", "application/json")
                    .addHeader("X-User-Id", userId)
                    .addHeader("X-Session-Id", sessionId)
                    .addHeader("X-Request-Id", requestId)
                    .addHeader("X-Client-Msg-Id", requestId)  // 后端计费/去重/对账主键，与 requestId 一致
                    .post(requestJsonString.toRequestBody("application/json".toMediaType()))
                    .build()
                
                call = client.newCall(request)
                currentCall.set(call)
                currentRequestId.set(requestId)
                val response = call!!.execute()
                val statusCode = response.code
                
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId streamId=$streamId HTTP=$statusCode")
                }
                
                if (response.isSuccessful) {
                    try {
                        val responseBodyStr = response.body?.string() ?: throw Exception("响应体为空")
                        currentCall.set(null)
                        currentRequestId.set(null)
                        val json = gson.fromJson(responseBodyStr, JsonObject::class.java)
                        val choices = json.getAsJsonArray("choices") ?: run {
                            handler.post { onInterrupted("error"); fireComplete() }
                            return@Thread
                        }
                        if (choices.size() == 0) {
                            handler.post { onInterrupted("error"); fireComplete() }
                            return@Thread
                        }
                        val message = choices.get(0).asJsonObject.getAsJsonObject("message") ?: run {
                            handler.post { onInterrupted("error"); fireComplete() }
                            return@Thread
                        }
                        val content = message.get("content")?.takeIf { it.isJsonPrimitive }?.asString?.trim() ?: ""
                        outputCharCount = content.length
                        val elapsed = System.currentTimeMillis() - startMs
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId streamId=$streamId 非流式完成 耗时=${elapsed}ms 入=$inLen img=$imgCount 出=$outputCharCount")
                        }
                        handler.post {
                            onChunk(content)
                            fireComplete()
                        }
                    } catch (e: Exception) {
                        currentCall.set(null)
                        currentRequestId.set(null)
                        val elapsed = System.currentTimeMillis() - startMs
                        val isCanceled = e.message?.contains("Canceled", ignoreCase = true) == true
                        val isInterrupted = e is SocketTimeoutException
                        if (isCanceled) {
                            Log.w(TAG, "callApi canceled, elapsed=${elapsed}ms")
                            handler.post {
                                onInterrupted("canceled")
                                fireComplete()
                            }
                        } else if (isInterrupted) {
                            Log.w(TAG, "callApi timeout, elapsed=${elapsed}ms")
                            handler.post {
                                onInterrupted("timeout")
                                fireComplete()
                            }
                        } else if (e is IOException) {
                            Log.e(TAG, "callApi network error, elapsed=${elapsed}ms", e)
                            handler.post {
                                onInterrupted("network")
                                fireComplete()
                            }
                        } else {
                            Log.e(TAG, "callApi error, elapsed=${elapsed}ms", e)
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
                    Log.e(TAG, "callApi HTTP error status=$statusCode, elapsed=${elapsed}ms")
                    handleErrorResponse(userId, sessionId, requestId, streamId, statusCode, responseBody, inLen, imgCount, outputCharCount, onInterrupted, fireComplete)
                }
            } catch (e: IOException) {
                currentCall.set(null)
                currentRequestId.set(null)
                val elapsed = System.currentTimeMillis() - startMs
                val isCanceled = e.message?.contains("Canceled", ignoreCase = true) == true
                val isInterrupted = e is SocketTimeoutException
                if (isCanceled) {
                    Log.w(TAG, "callApi canceled(IOException), elapsed=${elapsed}ms")
                    handler.post {
                        onInterrupted("canceled")
                        fireComplete()
                    }
                } else if (isInterrupted) {
                    Log.w(TAG, "callApi timeout(IOException), elapsed=${elapsed}ms")
                    handler.post {
                        onInterrupted("timeout")
                        fireComplete()
                    }
                } else {
                    Log.e(TAG, "callApi network error(IOException), elapsed=${elapsed}ms", e)
                    handler.post {
                        onInterrupted("network")
                        fireComplete()
                    }
                }
            } catch (e: Exception) {
                currentCall.set(null)
                currentRequestId.set(null)
                val elapsed = System.currentTimeMillis() - startMs
                val inC = try { userMessage.length } catch (_: Exception) { 0 }
                val imgC = try { imageUrlList.count { it.isNotBlank() && (it.startsWith("http://") || it.startsWith("https://")) } } catch (_: Exception) { 0 }
                Log.e(TAG, "callApi error, elapsed=${elapsed}ms", e)
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
            Log.e(TAG, "callApi 无法解析错误响应 statusCode=$statusCode")
        }
        handler.post {
            onInterrupted("server")
            fireComplete()
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
                addProperty("temperature", ModelParams.TEMPERATURE)
                addProperty("top_p", ModelParams.TOP_P)
                addProperty("max_tokens", ModelParams.MAX_TOKENS)
                addProperty("frequency_penalty", ModelParams.FREQUENCY_PENALTY)
                addProperty("presence_penalty", ModelParams.PRESENCE_PENALTY)
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
            // Release 节流：60s 内只打一条简短错误，避免 A>=24 每轮失败时刷屏
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
