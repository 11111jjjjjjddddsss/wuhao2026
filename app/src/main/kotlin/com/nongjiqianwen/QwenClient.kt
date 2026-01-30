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
    private const val READ_TIMEOUT_SEC = 15L   // 连续 N 秒无 delta 则断开（Watchdog）
    private const val CALL_TIMEOUT_SEC = 120L  // 单次请求总时长上限
    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SEC, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val currentCall = AtomicReference<Call?>(null)
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
     * 取消当前进行中的请求（如切后台/onPause 时调用）。中断后 UI 显示“已中断”，可重试。
     */
    fun cancelCurrentRequest() {
        currentCall.getAndSet(null)?.cancel()
        Log.d(TAG, "cancelCurrentRequest 已调用")
    }

    /**
     * 调用通义千问 API（真流式 SSE）
     * @param onInterrupted 流被取消或读超时时调用（用于 UI 标记“已中断”）
     */
    fun callApi(
        requestId: String,
        userMessage: String,
        imageUrlList: List<String> = emptyList(),
        onChunk: (String) -> Unit,
        onComplete: (() -> Unit)? = null,
        onInterrupted: (() -> Unit)? = null
    ) {
        val startMs = System.currentTimeMillis()
        Log.d(TAG, "=== requestId=$requestId 开始 ===")
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
                
                // 强制校验请求结构并打印最终payload（脱敏）
                val requestJsonString = requestBody.toString()
                Log.d(TAG, "=== requestId=$requestId FINAL_REQUEST_JSON（脱敏）===")
                val maskedJson = requestJsonString
                    .replace(Regex("\"url\":\\s*\"https://[^\"]+\""), "\"url\": \"https://***\"")
                Log.d(TAG, maskedJson)
                
                // 验证请求结构（OpenAI 兼容格式）
                val modelCheck = requestBody.get("model")?.asString
                val streamCheck = requestBody.get("stream")?.asBoolean
                val messagesCheck = requestBody.getAsJsonArray("messages")
                val firstMessage = messagesCheck?.get(0)?.asJsonObject
                val contentCheck = firstMessage?.getAsJsonArray("content")
                
                Log.d(TAG, "=== 请求结构验证（OpenAI兼容格式）===")
                Log.d(TAG, "model: $modelCheck")
                Log.d(TAG, "stream: $streamCheck")
                Log.d(TAG, "messages 是数组: ${messagesCheck != null}")
                Log.d(TAG, "messages[0].content 是数组: ${contentCheck != null}")
                
                var imageUrlCount = 0
                var hasText = false
                
                if (contentCheck != null) {
                    Log.d(TAG, "content 数组大小: ${contentCheck.size()}")
                    contentCheck.forEachIndexed { index, element ->
                        if (element.isJsonObject) {
                            val item = element.asJsonObject
                            val type = item.get("type")?.asString
                            Log.d(TAG, "content[$index]: type=$type")
                            
                            if (type == "image_url") {
                                imageUrlCount++
                                val imageUrlObj = item.getAsJsonObject("image_url")
                                val url = imageUrlObj?.get("url")?.asString
                                val isHttps = url?.startsWith("https://") == true
                                Log.d(TAG, "content[$index].image_url.url: $url (https: $isHttps)")
                            } else if (type == "text") {
                                hasText = true
                                val text = item.get("text")?.asString
                                Log.d(TAG, "content[$index].text: ${text?.take(50)}...")
                            }
                        }
                    }
                    
                    // 验证：最多4张图，必须带文字
                    if (imageUrlCount > 4) {
                        throw Exception("图片数量超过限制：$imageUrlCount 张，最多4张")
                    }
                    if (imageUrlCount > 0 && !hasText) {
                        throw Exception("有图片时必须带文字描述")
                    }
                    
                    Log.d(TAG, "验证通过：图片数量=$imageUrlCount，有文字=$hasText")
                }
                val request = Request.Builder()
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "text/event-stream")
                    .post(requestJsonString.toRequestBody("application/json".toMediaType()))
                    .build()
                
                call = client.newCall(request)
                currentCall.set(call)
                val response = call!!.execute()
                val statusCode = response.code
                
                Log.d(TAG, "requestId=$requestId HTTP Status Code: $statusCode")
                
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
                                        Log.d(TAG, "requestId=$requestId STREAM_DONE finish_reason=$lastFinishReason")
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
                            Log.d(TAG, "requestId=$requestId 状态=complete finish_reason=$lastFinishReason 耗时=${elapsed}ms")
                            handler.post { onComplete?.invoke() }
                        } finally {
                            reader.close()
                            inputStream.close()
                            currentCall.set(null)
                        }
                    } catch (e: Exception) {
                        currentCall.set(null)
                        val elapsed = System.currentTimeMillis() - startMs
                        val isCanceled = e.message?.contains("Canceled", ignoreCase = true) == true
                        val isInterrupted = e is SocketTimeoutException
                        if (isCanceled) {
                            Log.w(TAG, "requestId=$requestId 状态=canceled 耗时=${elapsed}ms")
                            handler.post {
                                onChunk("已取消，可重新发送")
                                onInterrupted?.invoke()
                                onComplete?.invoke()
                            }
                        } else if (isInterrupted) {
                            Log.w(TAG, "requestId=$requestId 状态=interrupted 耗时=${elapsed}ms")
                            handler.post {
                                onChunk("网络不稳定，已中断，可重试")
                                onInterrupted?.invoke()
                                onComplete?.invoke()
                            }
                        } else {
                            Log.e(TAG, "requestId=$requestId 状态=error 耗时=${elapsed}ms", e)
                            handler.post {
                                onChunk("服务返回异常，请稍后重试")
                                onComplete?.invoke()
                            }
                        }
                        return@Thread
                    }
                } else {
                    currentCall.set(null)
                    val responseBody = response.body?.string() ?: ""
                    val elapsed = System.currentTimeMillis() - startMs
                    Log.e(TAG, "requestId=$requestId 状态=error HTTP=$statusCode 耗时=${elapsed}ms")
                    handleErrorResponse(requestId, statusCode, responseBody, onChunk, onComplete)
                }
            } catch (e: IOException) {
                currentCall.set(null)
                val elapsed = System.currentTimeMillis() - startMs
                val isCanceled = e.message?.contains("Canceled", ignoreCase = true) == true
                val isInterrupted = e is SocketTimeoutException
                if (isCanceled) {
                    Log.w(TAG, "requestId=$requestId 状态=canceled 耗时=${elapsed}ms")
                    handler.post {
                        onChunk("已取消，可重新发送")
                        onInterrupted?.invoke()
                        onComplete?.invoke()
                    }
                } else if (isInterrupted) {
                    Log.w(TAG, "requestId=$requestId 状态=interrupted 耗时=${elapsed}ms")
                    handler.post {
                        onChunk("网络不稳定，已中断，可重试")
                        onInterrupted?.invoke()
                        onComplete?.invoke()
                    }
                } else {
                    Log.e(TAG, "requestId=$requestId 状态=error 耗时=${elapsed}ms", e)
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
                val elapsed = System.currentTimeMillis() - startMs
                Log.e(TAG, "requestId=$requestId 状态=error 耗时=${elapsed}ms", e)
                handler.post {
                    onChunk("服务异常，请稍后重试")
                    onComplete?.invoke()
                }
            }
        }.start()
    }
    
    /**
     * 处理错误响应：中文可读提示 + onComplete 必到，日志含 requestId/HTTP/摘要
     */
    private fun handleErrorResponse(requestId: String, statusCode: Int, responseBody: String, onChunk: (String) -> Unit, onComplete: (() -> Unit)? = null) {
        val bodySummary = responseBody.take(200) + if (responseBody.length > 200) "..." else ""
        Log.e(TAG, "requestId=$requestId HTTP=$statusCode body=$bodySummary")
        
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
            Log.e(TAG, "requestId=$requestId 无法解析错误响应 statusCode=$statusCode")
            handler.post {
                onChunk(errorMsg)
                onComplete?.invoke()
            }
        }
    }
}
