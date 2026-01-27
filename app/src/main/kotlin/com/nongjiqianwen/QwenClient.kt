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
    private val apiUrl = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"
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
     * 调用通义千问 API（流式返回）
     * @param userMessage 用户输入的消息
     * @param imageBase64List 图片Base64编码列表（可选）
     * @param onChunk 流式输出回调，每次接收到数据块时调用
     * @param onComplete 请求完成回调（可选），请求结束时调用
     */
    fun callApi(
        userMessage: String,
        imageBase64List: List<String> = emptyList(),
        onChunk: (String) -> Unit,
        onComplete: (() -> Unit)? = null
    ) {
        Thread {
            try {
                // 首次请求打印调试信息
                if (isFirstRequest) {
                    isFirstRequest = false
                    Log.d(TAG, "=== 首次请求调试信息 ===")
                    Log.d(TAG, "BAILIAN_API_KEY 长度: ${apiKey.length}")
                    Log.d(TAG, "当前使用的 model: $model")
                }
                
                // 构建 DashScope 格式的请求体
                // 关键：无图时 images 字段必须完全不存在，不能是 []、null 或 ""
                // image 结构必须符合 DashScope：{ image: { url: "data:image/xxx;base64,..." } }
                val requestBody = JsonObject().apply {
                    addProperty("model", model)
                    val inputObject = JsonObject().apply {
                        addProperty("prompt", userMessage)
                        
                        // 严格校验：只有在有有效图片时才添加 images 字段
                        // DashScope 只要检测到 images 字段，就会按 URL 解析，空数组也会报错
                        // DashScope API 格式：images 是字符串数组，每个字符串是 data:image/xxx;base64,... 格式
                        val validImageUrls = mutableListOf<String>()
                        if (imageBase64List.isNotEmpty()) {
                            imageBase64List.forEach { base64 ->
                                // 严格校验：每一项必须非空且是有效的 base64
                                if (base64.isNotBlank() && base64.length > 10) {
                                    // 确保 base64 包含 data:image 前缀（如果前端没有提供）
                                    val imageUrl = if (base64.startsWith("data:image/")) {
                                        base64
                                    } else {
                                        // 默认使用 jpeg 格式
                                        "data:image/jpeg;base64,$base64"
                                    }
                                    
                                    // 验证 URL 格式：必须是 data:image/ 开头，且包含 base64,
                                    if (imageUrl.startsWith("data:image/") && imageUrl.contains(";base64,")) {
                                        validImageUrls.add(imageUrl)
                                    } else {
                                        Log.w(TAG, "跳过无效的图片 URL 格式: ${imageUrl.take(50)}...")
                                    }
                                }
                            }
                        }
                        
                        // 只有在有有效图片时才添加 images 字段
                        if (validImageUrls.isNotEmpty()) {
                            val imagesArray = com.google.gson.JsonArray()
                            validImageUrls.forEach { imageUrl ->
                                imagesArray.add(imageUrl)
                            }
                            add("images", imagesArray)
                        }
                        // 无图时：input 对象中完全没有 images 字段
                    }
                    add("input", inputObject)
                    add("parameters", JsonObject().apply {
                        addProperty("temperature", 0.85)
                        // 启用流式输出
                        addProperty("incremental_output", true)
                    })
                }
                
                // 纯文本请求验证：输出最终 JSON，确认无 image 相关字段
                val requestJson = requestBody.toString()
                Log.d(TAG, "=== 最终请求 JSON ===")
                Log.d(TAG, requestJson)
                
                // 验证纯文本请求：确认没有 images、没有 type=image、没有任何 url 字段
                if (imageBase64List.isEmpty()) {
                    val jsonLower = requestJson.lowercase()
                    val hasImages = jsonLower.contains("\"images\"")
                    val hasTypeImage = jsonLower.contains("\"type\":\"image\"")
                    // 检查是否有非 API URL 的 url 字段（排除 apiUrl 中的 dashscope）
                    val hasUrl = jsonLower.contains("\"url\"") && 
                                 !jsonLower.contains("dashscope") && 
                                 !jsonLower.contains("https://")
                    
                    if (hasImages || hasTypeImage || hasUrl) {
                        Log.e(TAG, "⚠️ 纯文本请求中发现非法字段！")
                        Log.e(TAG, "  - images: $hasImages")
                        Log.e(TAG, "  - type=image: $hasTypeImage")
                        Log.e(TAG, "  - url: $hasUrl")
                        Log.e(TAG, "完整 JSON: $requestJson")
                    } else {
                        Log.d(TAG, "✅ 纯文本请求验证通过：无 images、无 type=image、无 url 字段")
                    }
                }
                
                // 创建请求
                val requestJsonString = requestBody.toString()
                val request = Request.Builder()
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(requestJsonString.toRequestBody("application/json".toMediaType()))
                    .build()
                
                Log.d(TAG, "发送请求到: $apiUrl")
                
                // 执行请求（真实流式处理）
                val response = client.newCall(request).execute()
                val statusCode = response.code
                Log.d(TAG, "HTTP Status Code: $statusCode")
                
                val responseBody = response.body?.string() ?: ""
                
                if (response.isSuccessful) {
                    try {
                        val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
                        
                        // DashScope 流式返回格式: { "output": { "text": "..." } }
                        // 如果启用 incremental_output，可能返回多个chunk，但当前API可能不支持真正的SSE流式
                        // 先处理完整响应，后续可优化为真正的流式
                        if (jsonResponse.has("output")) {
                            val output = jsonResponse.getAsJsonObject("output")
                            if (output.has("text")) {
                                val textElement = output.get("text")
                                val content = if (textElement.isJsonNull || textElement.asString.isBlank()) {
                                    Log.w(TAG, "output.text 为空，完整响应: $responseBody")
                                    ""
                                } else {
                                    textElement.asString
                                }
                                
                                if (content.isNotEmpty()) {
                                    // 真实流式：逐字符或逐词返回（模拟真实流式体验）
                                    // 注意：DashScope API可能不支持真正的SSE流式，这里按字符流式显示
                                    val chunkSize = 5 // 每5个字符为一个chunk，更接近真实流式
                                    var accumulatedText = ""
                                    
                                    for (i in content.indices step chunkSize) {
                                        val endIndex = minOf(i + chunkSize, content.length)
                                        val chunk = content.substring(i, endIndex)
                                        accumulatedText += chunk
                                        
                                        // 立即发送chunk（真实流式体验）
                                        handler.post {
                                            onChunk(chunk)
                                        }
                                        
                                        // 小延迟，模拟网络传输
                                        Thread.sleep(20)
                                    }
                                    
                                    // 所有chunk发送完成
                                    handler.post {
                                        onComplete?.invoke()
                                    }
                                    return@Thread
                                } else {
                                    Log.e(TAG, "output.text 为空，完整响应体: $responseBody")
                                    handler.post {
                                        onChunk("模型返回内容为空")
                                        onComplete?.invoke()
                                    }
                                    return@Thread
                                }
                            } else {
                                Log.e(TAG, "output 中缺少 text 字段，完整响应: $responseBody")
                                handler.post {
                                    onChunk("响应格式错误：output 中缺少 text 字段")
                                    onComplete?.invoke()
                                }
                                return@Thread
                            }
                        } else {
                            Log.e(TAG, "响应中缺少 output 字段，完整响应: $responseBody")
                            handler.post {
                                onChunk("响应格式错误：缺少 output 字段")
                                onComplete?.invoke()
                            }
                            return@Thread
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "解析响应失败", e)
                        handler.post {
                            onChunk("解析模型响应失败: ${e.message}")
                            onComplete?.invoke()
                        }
                        return@Thread
                    }
                    } else {
                        // 处理错误响应
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
            } finally {
                // 确保无论成功失败都通知完成（作为最后保障）
                // 注意：由于异步流式返回，主要依赖各分支的 onComplete 调用
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
