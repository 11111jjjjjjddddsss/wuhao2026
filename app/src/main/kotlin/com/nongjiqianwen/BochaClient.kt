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
import java.net.URL
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
     * 返回 Pair(success, text)：success==true 且 text 非空时才可展示灰块；失败/空返回 (false, null)。
     * 取消时抛出 IOException。
     */
    fun webSearch(query: String, callRef: AtomicReference<Call?>? = null): Pair<Boolean, String?> {
        if (query.isBlank()) return Pair(false, null)
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
                if (!response.isSuccessful) return Pair(false, null)
                val bodyStr = response.body?.string() ?: ""
                if (parseBodyCode(bodyStr) != null) return Pair(false, null)
                parseResult(bodyStr)
            }
        } catch (e: SocketTimeoutException) {
            if (BuildConfig.DEBUG) Log.w(TAG, "web-search timeout")
            Pair(false, null)
        } catch (e: IOException) {
            if (e.message?.contains("Canceled", ignoreCase = true) == true) throw e
            if (BuildConfig.DEBUG) Log.w(TAG, "web-search network", e)
            Pair(false, null)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "web-search error", e)
            Pair(false, null)
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

    /** 规范格式：顶部「联网搜索（仅供参考）」；最多 5 条「- 标题 | 域名 | URL」；标题截断 60 字；URL 纯文本。返回 (true,text) 仅当有结果。 */
    private fun parseResult(bodyStr: String): Pair<Boolean, String?> {
        return try {
            val root = gson.fromJson(bodyStr, JsonObject::class.java) ?: return Pair(false, null)
            val data = root.getAsJsonObject("data") ?: return Pair(false, null)
            val webPages = data.getAsJsonObject("webPages") ?: return Pair(false, null)
            val value = webPages.getAsJsonArray("value") ?: return Pair(false, null)
            if (value.size() == 0) return Pair(false, null)
            val header = "联网搜索（仅供参考）"
            val lines = mutableListOf<String>()
            val limit = value.size().coerceAtMost(5)
            for (i in 0 until limit) {
                val item = value.get(i).asJsonObject
                val name = item.get("name")?.takeIf { it.isJsonPrimitive }?.asString?.trim() ?: ""
                val url = item.get("url")?.takeIf { it.isJsonPrimitive }?.asString?.trim() ?: ""
                val title = name.take(60)
                val domain = try { URL(url).host ?: "-" } catch (_: Exception) { "-" }
                lines.add("- $title | $domain | $url")
            }
            val body = lines.joinToString("\n")
            val full = "$header\n$body".trim()
            Pair(true, full)
        } catch (_: Exception) {
            Pair(false, null)
        }
    }
}
