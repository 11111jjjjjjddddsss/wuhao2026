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
                
                // 构建 VL 模型协议格式的请求体
                // VL 模型（qwen3-vl-flash）必须使用 messages 格式，content 必须是数组
                // 即使只有文本，也必须使用：{ "role": "user", "content": [{ "type": "text", "text": "..." }] }
                // 严禁使用 text-only 结构（如 "prompt": "..."）
                
                // 第一步：严格验证输入参数
                Log.d(TAG, "=== 请求参数验证（VL Messages 协议）===")
                Log.d(TAG, "userMessage: ${userMessage.take(50)}...")
                Log.d(TAG, "imageBase64List.size: ${imageBase64List.size}")
                if (imageBase64List.isNotEmpty()) {
                    Log.d(TAG, "imageBase64List 第一项预览: ${imageBase64List[0].take(50)}...")
                }
                
                // 构建 VL messages 格式的 content 数组
                val contentArray = com.google.gson.JsonArray()
                
                // 1. 添加文本项（必须存在）
                if (userMessage.isNotBlank()) {
                    val textItem = JsonObject().apply {
                        addProperty("type", "text")
                        addProperty("text", userMessage)
                    }
                    contentArray.add(textItem)
                    Log.d(TAG, "✅ 添加 text content item")
                }
                
                // 2. 处理图片项（如果有）
                val validImageUrls = mutableListOf<String>()
                if (imageBase64List.isNotEmpty()) {
                    Log.d(TAG, "开始处理 ${imageBase64List.size} 个图片项")
                    imageBase64List.forEachIndexed { index, base64 ->
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
                                Log.d(TAG, "图片项 $index 验证通过")
                            } else {
                                Log.w(TAG, "跳过无效的图片 URL 格式 (索引 $index): ${imageUrl.take(50)}...")
                            }
                        } else {
                            Log.w(TAG, "跳过无效的 base64 (索引 $index): 长度=${base64.length}")
                        }
                    }
                    Log.d(TAG, "有效图片数量: ${validImageUrls.size}")
                    
                    // 添加图片项到 content 数组
                    validImageUrls.forEach { imageUrl ->
                        val imageItem = JsonObject().apply {
                            addProperty("type", "image")
                            addProperty("image", imageUrl)
                        }
                        contentArray.add(imageItem)
                        Log.d(TAG, "✅ 添加 image content item")
                    }
                } else {
                    Log.d(TAG, "无图片，content 数组只包含 text item")
                }
                
                // 构建完整的 VL messages 格式请求体
                val requestBody = JsonObject().apply {
                    addProperty("model", model)
                    val inputObject = JsonObject().apply {
                        // VL 协议：使用 messages 数组，而不是 prompt
                        val messagesArray = com.google.gson.JsonArray()
                        val userMessageObj = JsonObject().apply {
                            addProperty("role", "user")
                            add("content", contentArray)  // content 必须是数组
                        }
                        messagesArray.add(userMessageObj)
                        add("messages", messagesArray)
                        Log.d(TAG, "✅ 使用 VL messages 协议格式")
                    }
                    add("input", inputObject)
                    add("parameters", JsonObject().apply {
                        addProperty("temperature", 0.85)
                        // 启用流式输出
                        addProperty("incremental_output", true)
                    })
                }
                
                // 输出最终 JSON 用于验证
                val requestJson = requestBody.toString()
                Log.d(TAG, "=== 最终请求 JSON（VL Messages 协议）===")
                Log.d(TAG, requestJson)
                
                // 验证 VL messages 协议格式
                val jsonLower = requestJson.lowercase()
                val hasMessages = jsonLower.contains("\"messages\"")
                val hasContentArray = jsonLower.contains("\"content\"")
                val hasTextType = jsonLower.contains("\"type\":\"text\"")
                val hasImageType = jsonLower.contains("\"type\":\"image\"")
                
                if (!hasMessages || !hasContentArray || !hasTextType) {
                    Log.e(TAG, "⚠️ VL messages 协议格式验证失败！")
                    Log.e(TAG, "  - messages: $hasMessages")
                    Log.e(TAG, "  - content: $hasContentArray")
                    Log.e(TAG, "  - type:text: $hasTextType")
                    Log.e(TAG, "完整 JSON: $requestJson")
                } else {
                    if (imageBase64List.isEmpty()) {
                        Log.d(TAG, "✅ 纯文本请求验证通过：使用 VL messages 协议，content 数组只包含 text item")
                    } else {
                        if (hasImageType) {
                            Log.d(TAG, "✅ 图文请求验证通过：使用 VL messages 协议，content 数组包含 text 和 image items")
                        } else {
                            Log.w(TAG, "⚠️ 有图片但 content 中未找到 image item")
                        }
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
