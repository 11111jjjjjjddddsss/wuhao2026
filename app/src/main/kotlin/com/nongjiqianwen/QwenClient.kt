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
import java.util.concurrent.atomic.AtomicReference

/**
 * 通义千问客户端
 * 负责 HTTP 请求和 JSON 解析；支持取消、读超时（Watchdog）、callTimeout。
 * 使用阿里云百炼 DashScope API
 */
object QwenClient {
    private val TAG = "QwenClient"
    private const val CONNECT_TIMEOUT_SEC = 30L
    private const val READ_TIMEOUT_SEC = 30L   // 连续 30 秒无 chunk 才判定中断（Watchdog）
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
     * 取消当前进行中的请求（新请求开始前或切后台时调用），保证旧流 chunk 不写到新消息。
     */
    fun cancelCurrentRequest() {
        val oldRequestId = currentRequestId.getAndSet(null)
        currentCall.getAndSet(null)?.cancel()
        if (oldRequestId != null) Log.d(TAG, "requestId=$oldRequestId 被取消（新请求/切后台）")
    }

    /**
     * 调用通义千问 API（真流式 SSE）
     * userId/sessionId/requestId 仅用于日志与请求 header，严禁写入 prompt/messages。
     */
    fun callApi(
        userId: String,
        sessionId: String,
        requestId: String,
        userMessage: String,
        imageUrlList: List<String> = emptyList(),
        onChunk: (String) -> Unit,
        onComplete: (() -> Unit)? = null,
        onInterrupted: (() -> Unit)? = null
    ) {
        val startMs = System.currentTimeMillis()
        Log.d(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId 开始")
        Thread {
            var call: Call? = null
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
                
                val imageCount = imageUrlList.count { it.isNotBlank() && (it.startsWith("http://") || it.startsWith("https://")) }
                val textLen = userMessage.length
                Log.d(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId 摘要: 图数=$imageCount 字符数=$textLen")
                
                if (imageCount > 4) throw Exception("图片数量超过限制：$imageCount 张，最多4张")
                if (imageCount > 0 && userMessage.isBlank()) throw Exception("有图片时必须带文字描述")
                
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
                
                Log.d(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId HTTP=$statusCode")
                
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
                                        Log.d(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId STREAM_DONE finish_reason=$lastFinishReason")
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
                            Log.d(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId 状态=complete finish_reason=$lastFinishReason 耗时=${elapsed}ms")
                            handler.post { onComplete?.invoke() }
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
                            Log.w(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId 状态=canceled 耗时=${elapsed}ms")
                            handler.post {
                                onChunk("已取消，可重新发送")
                                onInterrupted?.invoke()
                                onComplete?.invoke()
                            }
                        } else if (isInterrupted) {
                            Log.w(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId 状态=interrupted 耗时=${elapsed}ms")
                            handler.post {
                                onChunk("网络不稳定，已中断，可重试")
                                onInterrupted?.invoke()
                                onComplete?.invoke()
                            }
                        } else {
                            Log.e(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId 状态=error 耗时=${elapsed}ms", e)
                            handler.post {
                                onChunk("服务返回异常，请稍后重试")
                                onComplete?.invoke()
                            }
                        }
                        return@Thread
                    }
                } else {
                    currentCall.set(null)
                    currentRequestId.set(null)
                    val responseBody = response.body?.string() ?: ""
                    val elapsed = System.currentTimeMillis() - startMs
                    Log.e(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId 状态=error HTTP=$statusCode 耗时=${elapsed}ms")
                    handleErrorResponse(userId, sessionId, requestId, statusCode, responseBody, onChunk, onComplete)
                }
            } catch (e: IOException) {
                currentCall.set(null)
                currentRequestId.set(null)
                val elapsed = System.currentTimeMillis() - startMs
                val isCanceled = e.message?.contains("Canceled", ignoreCase = true) == true
                val isInterrupted = e is SocketTimeoutException
                if (isCanceled) {
                    Log.w(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId 状态=canceled 耗时=${elapsed}ms")
                    handler.post {
                        onChunk("已取消，可重新发送")
                        onInterrupted?.invoke()
                        onComplete?.invoke()
                    }
                } else if (isInterrupted) {
                    Log.w(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId 状态=interrupted 耗时=${elapsed}ms")
                    handler.post {
                        onChunk("网络不稳定，已中断，可重试")
                        onInterrupted?.invoke()
                        onComplete?.invoke()
                    }
                } else {
                    Log.e(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId 状态=error 耗时=${elapsed}ms", e)
                    val errorMsg = when {
                        e.message?.contains("timeout", ignoreCase = true) == true -> "网络请求超时，请稍后重试"
                        e.message?.contains("DNS", ignoreCase = true) == true -> "网络异常，请检查网络设置"
                        e.message?.contains("connect", ignoreCase = true) == true -> "连接失败，请检查网络"
                        else -> "网络异常，请稍后重试"
                    }
                    handler.post {
                        onChunk(errorMsg)
                        onComplete?.invoke()
                    }
                }
            } catch (e: Exception) {
                currentCall.set(null)
                currentRequestId.set(null)
                val elapsed = System.currentTimeMillis() - startMs
                Log.e(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId 状态=error 耗时=${elapsed}ms", e)
                handler.post {
                    onChunk("服务异常，请稍后重试")
                    onComplete?.invoke()
                }
            }
        }.start()
    }
    
    /**
     * 处理错误响应：中文可读提示 + onComplete 必到，日志含 userId/sessionId/requestId/HTTP/摘要
     */
    private fun handleErrorResponse(userId: String, sessionId: String, requestId: String, statusCode: Int, responseBody: String, onChunk: (String) -> Unit, onComplete: (() -> Unit)? = null) {
        Log.e(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId 状态=error HTTP=$statusCode 耗时见上")
        
        try {
            val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
            // DashScope 错误格式: { "code": "...", "message": "..." }
            val errorCode = jsonResponse.get("code")?.asString ?: ""
            val errorMessage = jsonResponse.get("message")?.asString ?: responseBody
            
            // 只有在明确是 DashScope 返回的错误码时才显示"模型不可用"
            val errorMsg = when {
                statusCode == 401 || statusCode == 403 ||
                errorCode.contains("InvalidApiKey", ignoreCase = true) ||
                errorCode.contains("ModelAccessDenied", ignoreCase = true) -> "鉴权失败，请检查配置"
                statusCode == 400 || errorCode.contains("InvalidParameter", ignoreCase = true) -> "请求参数错误，请稍后重试"
                errorCode.isNotEmpty() && (
                    errorCode.contains("ModelNotFound", ignoreCase = true) ||
                    errorCode.contains("ModelUnavailable", ignoreCase = true)
                ) -> "服务暂不可用，请稍后重试"
                statusCode in 500..599 -> "服务异常，请稍后重试"
                statusCode == 429 -> "请求过于频繁，请稍后重试"
                else -> "请求失败，请稍后重试"
            }
            
            handler.post {
                onChunk(errorMsg)
                onComplete?.invoke()
            }
        } catch (e: Exception) {
            val errorMsg = when (statusCode) {
                401, 403 -> "鉴权失败，请检查配置"
                400 -> "请求参数错误，请稍后重试"
                404 -> "接口不存在，请检查配置"
                429 -> "请求过于频繁，请稍后重试"
                500, 502, 503 -> "服务异常，请稍后重试"
                else -> "请求失败，请稍后重试"
            }
            Log.e(TAG, "userId=$userId sessionId=$sessionId requestId=$requestId 无法解析错误响应 statusCode=$statusCode")
            handler.post {
                onChunk(errorMsg)
                onComplete?.invoke()
            }
        }
    }
}
