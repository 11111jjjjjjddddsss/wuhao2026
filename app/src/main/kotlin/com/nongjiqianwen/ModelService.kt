package com.nongjiqianwen

import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * 模型服务接口
 * 支持流式输出（通过回调方式）
 */
object ModelService {
    private val handler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient()
    private val gson = Gson()
    private val apiKey = "sk-e6865ea00577486eacfdd7b98bb1ed03"
    private val apiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
    
    /**
     * 获取模型回复
     * @param userMessage 用户输入的消息
     * @param onChunk 流式输出回调，每次接收到数据块时调用
     */
    fun getReply(userMessage: String, onChunk: (String) -> Unit) {
        // 在后台线程执行网络请求
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
                        // 在主线程回调返回完整内容
                        handler.post {
                            onChunk(content)
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
                // 请求失败，返回固定文本
                handler.post {
                    onChunk("模型暂不可用")
                }
            }
        }.start()
    }
}
