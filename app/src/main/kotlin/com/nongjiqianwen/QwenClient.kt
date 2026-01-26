package com.nongjiqianwen

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
    
    /**
     * 调用通义千问 API
     * @param userMessage 用户输入的消息
     * @return 模型返回的完整内容，失败时返回 null
     */
    fun callApi(userMessage: String): String? {
        return try {
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
                    message.get("content").asString
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
