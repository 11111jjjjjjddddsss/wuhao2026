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
    private val apiUrl = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation"
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
                // 构建请求体 - 严格按照指令规范
                val requestBody = JsonObject().apply {
                    addProperty("model", model)
                    val inputObject = JsonObject().apply {
                        val messagesArray = com.google.gson.JsonArray()
                        val userMessageObj = JsonObject().apply {
                            addProperty("role", "user")
                            
                            // content 必须是数组格式
                            val contentArray = com.google.gson.JsonArray()
                            
                            // 1. 添加文本项（纯文本时也必须用数组格式）
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
                    add("input", inputObject)
                    // 不添加 parameters（非流式，不使用 incremental_output）
                }
                
                // 必须的调试日志：FINAL_REQUEST_JSON
                val requestJsonString = requestBody.toString()
                Log.d(TAG, "FINAL_REQUEST_JSON: $requestJsonString")
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
                
                // 必须的调试日志：FINAL_RESPONSE_JSON
                Log.d(TAG, "FINAL_RESPONSE_JSON: $responseBody")
                
                if (response.isSuccessful) {
                    try {
                        val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
                        
                        // 严格按照指令解析：output.choices[0].message.content
                        if (jsonResponse.has("output")) {
                            val output = jsonResponse.getAsJsonObject("output")
                            
                            if (output.has("choices") && output.getAsJsonArray("choices").size() > 0) {
                                val choices = output.getAsJsonArray("choices")
                                val firstChoice = choices[0].asJsonObject
                                
                                if (firstChoice.has("message")) {
                                    val message = firstChoice.getAsJsonObject("message")
                                    
                                    if (message.has("content")) {
                                        val content = message.get("content")
                                        
                                        // 解析 content：字符串或数组
                                        val fullText = when {
                                            content.isJsonPrimitive && content.asJsonPrimitive.isString -> {
                                                // content 是字符串
                                                content.asString
                                            }
                                            content.isJsonArray -> {
                                                // content 是数组，找到 type=text 的项
                                                val contentArray = content.asJsonArray
                                                contentArray.firstOrNull { item ->
                                                    item.isJsonObject && 
                                                    item.asJsonObject.has("type") && 
                                                    item.asJsonObject.get("type").asString == "text"
                                                }?.asJsonObject?.get("text")?.asString ?: ""
                                            }
                                            else -> {
                                                Log.w(TAG, "未知的 content 格式")
                                                ""
                                            }
                                        }
                                        
                                        // 返回完整文本
                                        handler.post {
                                            onChunk(fullText)
                                            onComplete?.invoke()
                                        }
                                        return@Thread
                                    } else {
                                        Log.e(TAG, "message 中缺少 content 字段")
                                        handler.post {
                                            onChunk("响应格式错误：message 中缺少 content 字段")
                                            onComplete?.invoke()
                                        }
                                        return@Thread
                                    }
                                } else {
                                    Log.e(TAG, "choice 中缺少 message 字段")
                                    handler.post {
                                        onChunk("响应格式错误：choice 中缺少 message 字段")
                                        onComplete?.invoke()
                                    }
                                    return@Thread
                                }
                            } else {
                                Log.e(TAG, "output 中缺少 choices 字段或 choices 为空")
                                handler.post {
                                    onChunk("响应格式错误：output 中缺少 choices 字段")
                                    onComplete?.invoke()
                                }
                                return@Thread
                            }
                        } else {
                            Log.e(TAG, "响应中缺少 output 字段")
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
