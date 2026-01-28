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
                // 构建请求体 - OpenAI 兼容格式
                val requestBody = JsonObject().apply {
                    addProperty("model", model)
                    addProperty("stream", false)
                    
                    val messagesArray = com.google.gson.JsonArray()
                    val userMessageObj = JsonObject().apply {
                        addProperty("role", "user")
                        
                        // content 必须是数组格式
                        val contentArray = com.google.gson.JsonArray()
                        
                        // 1. 添加文本项
                        if (userMessage.isNotBlank()) {
                            val textItem = JsonObject().apply {
                                addProperty("type", "text")
                                addProperty("text", userMessage)
                            }
                            contentArray.add(textItem)
                        }
                        
                        // 2. 添加图片项（如果有图片URL）
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
                        
                        add("content", contentArray)
                    }
                    messagesArray.add(userMessageObj)
                    add("messages", messagesArray)
                }
                
                // 强制校验请求结构
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
                if (contentCheck != null) {
                    Log.d(TAG, "content 数组大小: ${contentCheck.size()}")
                    contentCheck.forEachIndexed { index, element ->
                        if (element.isJsonObject) {
                            val item = element.asJsonObject
                            val type = item.get("type")?.asString
                            Log.d(TAG, "content[$index]: type=$type")
                            if (type == "text") {
                                val text = item.get("text")?.asString
                                Log.d(TAG, "content[$index].text: ${text?.take(50)}...")
                            }
                        }
                    }
                }
                val request = Request.Builder()
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(requestJsonString.toRequestBody("application/json".toMediaType()))
                    .build()
                
                // 执行请求（非流式）
                val response = client.newCall(request).execute()
                val statusCode = response.code
                val responseBody = response.body?.string() ?: ""
                
                // 强制打印完整响应JSON（原样）
                Log.d(TAG, "=== FINAL_RESPONSE_JSON ===")
                Log.d(TAG, responseBody)
                
                if (response.isSuccessful) {
                    try {
                        val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
                        
                        // OpenAI 兼容格式解析：choices[0].message.content（字符串）
                        if (jsonResponse.has("choices") && jsonResponse.getAsJsonArray("choices").size() > 0) {
                            val choices = jsonResponse.getAsJsonArray("choices")
                            val firstChoice = choices[0].asJsonObject
                            
                            if (firstChoice.has("message")) {
                                val message = firstChoice.getAsJsonObject("message")
                                
                                if (message.has("content")) {
                                    val content = message.get("content")
                                    
                                    Log.d(TAG, "=== 开始解析 content（OpenAI兼容格式）===")
                                    Log.d(TAG, "content 类型: ${if (content.isJsonPrimitive) "Primitive" else if (content.isJsonArray) "Array" else "Other"}")
                                    
                                    // OpenAI 兼容接口：content 是字符串
                                    val fullText = if (content.isJsonPrimitive && content.asJsonPrimitive.isString) {
                                        val text = content.asString
                                        Log.d(TAG, "content 是字符串，长度: ${text.length}")
                                        text
                                    } else {
                                        // 如果不是字符串，打印完整内容并抛出错误
                                        Log.e(TAG, "content 不是字符串格式: $content")
                                        throw Exception("响应解析失败：content不是字符串格式，content=$content")
                                    }
                                    
                                    // 最终验证：fullText不允许为空
                                    if (fullText.isBlank()) {
                                        Log.e(TAG, "最终解析结果为空，抛出错误")
                                        throw Exception("响应解析失败：fullText为空，请检查响应结构")
                                    }
                                    
                                    Log.d(TAG, "=== 解析成功 ===")
                                    Log.d(TAG, "FINAL_TEXT 长度: ${fullText.length}")
                                    Log.d(TAG, "FINAL_TEXT 预览: ${fullText.take(100)}...")
                                    
                                    // 返回完整文本
                                    handler.post {
                                        onChunk(fullText)
                                        onComplete?.invoke()
                                    }
                                    return@Thread
                                } else {
                                    Log.e(TAG, "message 中缺少 content 字段，完整message: $message")
                                    handler.post {
                                        onChunk("响应格式错误：message 中缺少 content 字段，完整响应: $responseBody")
                                        onComplete?.invoke()
                                    }
                                    return@Thread
                                }
                            } else {
                                Log.e(TAG, "choice 中缺少 message 字段，完整choice: $firstChoice")
                                handler.post {
                                    onChunk("响应格式错误：choice 中缺少 message 字段，完整响应: $responseBody")
                                    onComplete?.invoke()
                                }
                                return@Thread
                            }
                        } else {
                            Log.e(TAG, "响应中缺少 choices 字段或 choices 为空，完整响应: $responseBody")
                            handler.post {
                                onChunk("响应格式错误：缺少 choices 字段，完整响应: $responseBody")
                                onComplete?.invoke()
                            }
                            return@Thread
                        }
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "解析响应失败", e)
                        Log.e(TAG, "完整响应内容: $responseBody")
                        e.printStackTrace()
                        handler.post {
                            onChunk("解析模型响应失败: ${e.message}，完整响应: $responseBody")
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
