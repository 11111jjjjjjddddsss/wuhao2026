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

/**
 * 通义千问客户端
 * 负责 HTTP 请求和 JSON 解析
 * 使用阿里云百炼 DashScope API
 */
object QwenClient {
    private val TAG = "QwenClient"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
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
     * 调用通义千问 API（非流式）
     * @param userMessage 用户输入的消息
     * @param imageUrlList 图片URL列表（可选，必须是公网可访问的URL）
     * @param onChunk 回调函数，返回完整响应文本
     * @param onComplete 请求完成回调（可选）
     */
    fun callApi(
        userMessage: String,
        imageUrlList: List<String> = emptyList(),
        onChunk: (String) -> Unit,
        onComplete: (() -> Unit)? = null
    ) {
        Thread {
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
                
                // 强制校验请求结构并打印最终payload
                val requestJsonString = requestBody.toString()
                Log.d(TAG, "=== FINAL_REQUEST_JSON ===")
                Log.d(TAG, requestJsonString)
                
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
                
                // 执行SSE流式请求
                val response = client.newCall(request).execute()
                val statusCode = response.code
                
                Log.d(TAG, "HTTP Status Code: $statusCode")
                
                if (response.isSuccessful) {
                    try {
                        val responseBody = response.body
                        if (responseBody == null) {
                            throw Exception("响应体为空")
                        }
                        
                        // SSE流式解析
                        val inputStream = responseBody.byteStream()
                        val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
                        
                        try {
                            while (true) {
                                val line = reader.readLine() ?: break
                                
                                Log.d(TAG, "=== SSE_LINE ===")
                                Log.d(TAG, line)
                                
                                // 检查结束标记
                                if (line.trim() == "data: [DONE]") {
                                    Log.d(TAG, "=== STREAM_DONE ===")
                                    break
                                }
                                
                                // 解析 data: 开头的行
                                if (line.startsWith("data: ")) {
                                    val jsonStr = line.substring(6) // 移除 "data: " 前缀
                                    
                                    // 跳过空JSON对象
                                    if (jsonStr.trim().isEmpty() || jsonStr.trim() == "{}") {
                                        continue
                                    }
                                    
                                    try {
                                        val jsonResponse = gson.fromJson(jsonStr, JsonObject::class.java)
                                        
                                        // 解析 choices[0].delta.content
                                        if (jsonResponse.has("choices") && jsonResponse.getAsJsonArray("choices").size() > 0) {
                                            val choices = jsonResponse.getAsJsonArray("choices")
                                            val firstChoice = choices[0].asJsonObject
                                            
                                            if (firstChoice.has("delta")) {
                                                val delta = firstChoice.getAsJsonObject("delta")
                                                
                                                if (delta.has("content")) {
                                                    val content = delta.get("content")
                                                    
                                                    if (content.isJsonPrimitive && content.asJsonPrimitive.isString) {
                                                        val deltaText = content.asString
                                                        
                                                        if (deltaText.isNotBlank()) {
                                                            Log.d(TAG, "=== DELTA_CONTENT ===")
                                                            Log.d(TAG, "\"$deltaText\"")
                                                            
                                                            // 立即回调前端渲染
                                                            handler.post {
                                                                onChunk(deltaText)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        // 忽略单行JSON解析错误（可能是空行或其他格式）
                                        Log.w(TAG, "解析SSE行失败: $line", e)
                                    }
                                }
                            }
                            
                            // 流式完成
                            Log.d(TAG, "=== STREAM_COMPLETE ===")
                            handler.post {
                                onComplete?.invoke()
                            }
                            
                        } finally {
                            reader.close()
                            inputStream.close()
                        }
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "SSE流式解析失败", e)
                        e.printStackTrace()
                        handler.post {
                            onChunk("流式解析失败: ${e.message}")
                            onComplete?.invoke()
                        }
                        return@Thread
                    }
                } else {
                    // 处理错误响应
                    val responseBody = response.body?.string() ?: ""
                    handleErrorResponse(statusCode, responseBody, onChunk, onComplete)
                }
            } catch (e: IOException) {
                Log.e(TAG, "网络请求失败", e)
                val errorMsg = when {
                    e.message?.contains("timeout", ignoreCase = true) == true -> 
                        "网络请求超时，请检查网络连接"
                    e.message?.contains("DNS", ignoreCase = true) == true -> 
                        "DNS 解析失败，请检查网络设置"
                    e.message?.contains("connect", ignoreCase = true) == true -> 
                        "连接失败，请检查网络连接或代理设置"
                    else -> 
                        "网络错误: ${e.message}"
                }
                handler.post {
                    onChunk(errorMsg)
                    onComplete?.invoke()
                }
            } catch (e: Exception) {
                Log.e(TAG, "未知错误", e)
                handler.post {
                    onChunk("请求失败: ${e.message}")
                    onComplete?.invoke()
                }
            }
        }.start()
    }
    
    /**
     * 处理错误响应，明确分类错误类型
     * 只有在 HTTP 请求真实失败且 error.code 明确来自 DashScope 时才显示"模型不可用"
     */
    private fun handleErrorResponse(statusCode: Int, responseBody: String, onChunk: (String) -> Unit, onComplete: (() -> Unit)? = null) {
        Log.e(TAG, "请求失败 - Status: $statusCode, Body: $responseBody")
        
        try {
            val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
            // DashScope 错误格式: { "code": "...", "message": "..." }
            val errorCode = jsonResponse.get("code")?.asString ?: ""
            val errorMessage = jsonResponse.get("message")?.asString ?: responseBody
            
            // 只有在明确是 DashScope 返回的错误码时才显示"模型不可用"
            val errorMsg = when {
                // 权限问题
                statusCode == 401 || statusCode == 403 || 
                errorCode.contains("InvalidApiKey", ignoreCase = true) ||
                errorCode.contains("ModelAccessDenied", ignoreCase = true) -> {
                    Log.e(TAG, "权限问题 - Code: $errorCode, Message: $errorMessage")
                    "API 权限错误 (${errorCode}): $errorMessage"
                }
                // 参数问题
                statusCode == 400 || 
                errorCode.contains("InvalidParameter", ignoreCase = true) -> {
                    Log.e(TAG, "参数问题 - Code: $errorCode, Message: $errorMessage")
                    "请求参数错误 (${errorCode}): $errorMessage"
                }
                // 模型状态问题 - 只有在 DashScope 明确返回这些错误码时才显示"模型不可用"
                (errorCode.isNotEmpty() && (
                    errorCode.contains("ModelNotFound", ignoreCase = true) ||
                    errorCode.contains("ModelUnavailable", ignoreCase = true)
                )) -> {
                    Log.e(TAG, "模型状态问题 - Code: $errorCode, Message: $errorMessage")
                    "模型不可用 (${errorCode}): $errorMessage"
                }
                // 其他错误 - 不显示"模型不可用"
                else -> {
                    Log.e(TAG, "其他错误 - Status: $statusCode, Code: $errorCode, Message: $errorMessage")
                    "请求失败 (HTTP $statusCode${if (errorCode.isNotEmpty()) ", $errorCode" else ""}): $errorMessage"
                }
            }
            
            handler.post {
                onChunk(errorMsg)
                onComplete?.invoke()
            }
        } catch (e: Exception) {
            // 如果无法解析错误响应，使用状态码判断，但不显示"模型不可用"
            val errorMsg = when (statusCode) {
                401, 403 -> "API 权限错误 (HTTP $statusCode): 请检查 API Key 是否正确"
                400 -> "请求参数错误 (HTTP $statusCode): $responseBody"
                404 -> "接口不存在 (HTTP $statusCode): 请检查 API URL"
                500, 502, 503 -> "服务器错误 (HTTP $statusCode): 请稍后重试"
                else -> "请求失败 (HTTP $statusCode): $responseBody"
            }
            Log.e(TAG, "无法解析错误响应: $errorMsg")
            handler.post {
                onChunk(errorMsg)
                onComplete?.invoke()
            }
        }
    }
}
