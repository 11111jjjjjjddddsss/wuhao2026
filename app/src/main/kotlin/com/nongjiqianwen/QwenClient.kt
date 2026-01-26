package com.nongjiqianwen

import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 通义千问客户端
 * 负责 HTTP 请求和 JSON 解析
 */
object QwenClient {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val apiKey = BuildConfig.API_KEY
    private val apiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
    private val handler = Handler(Looper.getMainLooper())
    
    /**
     * 调用通义千问 API（流式返回）
     * @param userMessage 用户输入的消息
     * @param onChunk 流式输出回调，每次接收到数据块时调用
     */
    fun callApi(userMessage: String, onChunk: (String) -> Unit) {
        Thread {
            try {
                // 构建请求体
                val requestBody = JsonObject().apply {
                    addProperty("model", "qwen3-vl-flash")
                    add("messages", gson.toJsonTree(listOf(
                        mapOf("role" to "user", "content" to userMessage)
                    )))
                }
                
                // 创建请求
                val request = Request.Builder()
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                
                // 执行请求
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
                    val choices = jsonResponse.getAsJsonArray("choices")
                    if (choices.size() > 0) {
                        val message = choices[0].asJsonObject.getAsJsonObject("message")
                        val content = message.get("content").asString
                        
                        // 将完整内容拆分成多个 chunk，模拟流式返回
                        val chunkSize = 3 // 每 3 个字符为一个 chunk
                        for (i in content.indices step chunkSize) {
                            val endIndex = minOf(i + chunkSize, content.length)
                            val chunk = content.substring(i, endIndex)
                            
                            // 延迟发送，模拟真实流式
                            handler.postDelayed({
                                onChunk(chunk)
                            }, (i / chunkSize * 50).toLong()) // 每个 chunk 间隔 50ms
                        }
                    } else {
                        handler.post {
                            onChunk("模型暂不可用")
                        }
                    }
                } else {
                    handler.post {
                        onChunk("模型暂不可用")
                    }
                }
            } catch (e: Exception) {
                handler.post {
                    onChunk("模型暂不可用")
                }
            }
        }.start()
    }
}
