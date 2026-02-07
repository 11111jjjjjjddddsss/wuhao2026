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
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 博查 Web Search API 客户端。
 * 密钥仅从 BuildConfig 读取，严禁在日志或异常中打印。
 * 超时：connect 10s / read 30s / call 40s。
 */
object BochaClient {
    private const val TAG = "BochaClient"
    private const val CONNECT_TIMEOUT_SEC = 10L
    private const val READ_TIMEOUT_SEC = 30L
    private const val CALL_TIMEOUT_SEC = 40L
    private const val URL = "https://api.bocha.cn/v1/web-search"

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
        .callTimeout(CALL_TIMEOUT_SEC, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val handler = Handler(Looper.getMainLooper())

    /**
     * 纯工具函数：只做请求 → 返回结果 或 null。
     * 搜索失败/超时/空结果 → 返回 null；callRef 用于外部 cancel，取消时抛出 IOException。
     */
    fun webSearch(query: String, callRef: AtomicReference<Call?>? = null): String? {
        if (query.isBlank()) return null
        val body = JsonObject().apply {
            addProperty("query", query.trim())
            addProperty("freshness", "noLimit")
            addProperty("summary", true)
            addProperty("count", 5)
        }
        val request = Request.Builder()
            .url(URL)
            .addHeader("Authorization", "Bearer ${BuildConfig.BOCHA_API_KEY}")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val call = client.newCall(request)
        callRef?.set(call)
        return try {
            call.execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string() ?: ""
                if (parseBodyCode(bodyStr) != null) return null
                parseResult(bodyStr)
            }
        } catch (e: IOException) {
            if (e.message?.contains("Canceled", ignoreCase = true) == true) throw e
            if (BuildConfig.DEBUG) Log.w(TAG, "web-search network", e)
            null
        } catch (e: SocketTimeoutException) {
            if (BuildConfig.DEBUG) Log.w(TAG, "web-search timeout")
            null
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "web-search error", e)
            null
        }
    }

    /** 官方异常码：HTTP 200 时 body 可能带 code!=200（文档中 code 可为数字或字符串）。返回非 null 表示业务失败，走 onFailure(reason)，不泄露响应体/密钥。 */
    private fun parseBodyCode(bodyStr: String): String? {
        return try {
            val root = gson.fromJson(bodyStr, JsonObject::class.java) ?: return null
            val c = root.get("code") ?: return null
            if (!c.isJsonPrimitive) return null
            val p = c.asJsonPrimitive
            val code = try {
                p.asInt
            } catch (_: Exception) {
                p.asString.toIntOrNull() ?: 200
            }
            when (code) {
                200 -> null
                401 -> "auth"      // Invalid API KEY
                403 -> "quota"    // 余额不足
                429 -> "rate_limit"
                400 -> "bad_request"
                else -> "server"  // 500 等
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 解析博查返回 JSON。返回 null=解析/结构异常走 server；""=无结果静默；非空=成功展示（最多 5 条）。不打印 key/响应体。 */
    private fun parseResult(bodyStr: String): String? {
        return try {
            val root = gson.fromJson(bodyStr, JsonObject::class.java) ?: return null
            val data = root.getAsJsonObject("data") ?: return null
            val webPages = data.getAsJsonObject("webPages") ?: return null
            val value = webPages.getAsJsonArray("value") ?: return null
            if (value.size() == 0) return ""
            val sb = StringBuilder()
            sb.append("【联网搜索结果（Bocha）】\n")
            val limit = value.size().coerceAtMost(5)
            for (i in 0 until limit) {
                val item = value.get(i).asJsonObject
                val name = item.get("name")?.takeIf { it.isJsonPrimitive }?.asString?.trim() ?: ""
                val url = item.get("url")?.takeIf { it.isJsonPrimitive }?.asString?.trim() ?: ""
                val summary = item.get("summary")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
                    ?: item.get("snippet")?.takeIf { it.isJsonPrimitive }?.asString?.trim() ?: ""
                sb.append("${i + 1}) $name - $url\n")
                if (summary.isNotBlank()) sb.append("   摘要：$summary\n")
            }
            sb.toString().trim()
        } catch (_: Exception) {
            null
        }
    }
}
