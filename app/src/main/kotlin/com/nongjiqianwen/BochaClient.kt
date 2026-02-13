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
    data class SearchMemoResult(
        val success: Boolean,
        val memo: String?,
        val fetchedCount: Int,
        val selectedCount: Int,
        val hasSummaryField: Boolean,
        val memoChars: Int
    )

    private data class Candidate(
        val title: String,
        val site: String,
        val summary: String
    )

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
        val result = webSearchMemo(query, callRef)
        return Pair(result.success, result.memo)
    }

    fun webSearchMemo(query: String, callRef: AtomicReference<Call?>? = null): SearchMemoResult {
        if (query.isBlank()) return SearchMemoResult(false, null, 0, 0, false, 0)
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
                if (!response.isSuccessful) return SearchMemoResult(false, null, 0, 0, false, 0)
                val bodyStr = response.body?.string() ?: ""
                if (parseBodyCode(bodyStr) != null) return SearchMemoResult(false, null, 0, 0, false, 0)
                parseResultAsMemo(bodyStr)
            }
        } catch (e: SocketTimeoutException) {
            if (BuildConfig.DEBUG) Log.w(TAG, "web-search timeout")
            SearchMemoResult(false, null, 0, 0, false, 0)
        } catch (e: IOException) {
            if (e.message?.contains("Canceled", ignoreCase = true) == true) throw e
            if (BuildConfig.DEBUG) Log.w(TAG, "web-search network", e)
            SearchMemoResult(false, null, 0, 0, false, 0)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w(TAG, "web-search error", e)
            SearchMemoResult(false, null, 0, 0, false, 0)
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

    private fun parseResultAsMemo(bodyStr: String): SearchMemoResult {
        return try {
            val root = gson.fromJson(bodyStr, JsonObject::class.java) ?: return SearchMemoResult(false, null, 0, 0, false, 0)
            val data = root.getAsJsonObject("data") ?: return SearchMemoResult(false, null, 0, 0, false, 0)
            val webPages = data.getAsJsonObject("webPages") ?: return SearchMemoResult(false, null, 0, 0, false, 0)
            val value = webPages.getAsJsonArray("value") ?: return SearchMemoResult(false, null, 0, 0, false, 0)
            val fetchedCount = value.size().coerceAtMost(5)
            if (fetchedCount <= 0) return SearchMemoResult(false, null, 0, 0, false, 0)

            val selected = mutableListOf<Candidate>()
            var hasSummaryField = false
            val seenSites = LinkedHashSet<String>()

            fun buildCandidate(item: JsonObject, index: Int): Candidate? {
                val title = item.get("name")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty().take(40)
                val url = item.get("url")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
                val siteName = item.get("siteName")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
                val domain = try {
                    URL(url).host?.removePrefix("www.").orEmpty()
                } catch (_: Exception) {
                    ""
                }
                val site = (siteName.ifBlank { domain }).ifBlank { "未知来源" }.take(24)
                val summary = item.get("summary")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
                if (summary.isNotBlank()) hasSummaryField = true
                val snippet = item.get("snippet")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
                val text = summary.ifBlank { snippet }.trim().take(150)
                if (text.isBlank()) return null
                return Candidate(
                    title = title.ifBlank { "结果${index + 1}" },
                    site = site,
                    summary = text
                )
            }

            for (i in 0 until fetchedCount) {
                val candidate = buildCandidate(value.get(i).asJsonObject, i) ?: continue
                val siteKey = candidate.site.lowercase()
                if (!seenSites.add(siteKey)) continue
                selected.add(candidate)
                if (selected.size >= 3) break
            }
            if (selected.size < 3) {
                for (i in 0 until fetchedCount) {
                    val candidate = buildCandidate(value.get(i).asJsonObject, i) ?: continue
                    if (selected.any { it.title == candidate.title && it.site == candidate.site && it.summary == candidate.summary }) continue
                    selected.add(candidate)
                    if (selected.size >= 3) break
                }
            }

            if (selected.isEmpty()) return SearchMemoResult(false, null, fetchedCount, 0, hasSummaryField, 0)

            val body = selected.mapIndexed { index, c ->
                "${index + 1}) ${c.title}（${c.site}）：${c.summary}"
            }.joinToString("\n")
            var memo = "【联网搜索备忘录｜低权重】\n$body".trim()
            if (memo.length > 700) memo = memo.take(700)
            SearchMemoResult(true, memo, fetchedCount, selected.size, hasSummaryField, memo.length)
        } catch (_: Exception) {
            SearchMemoResult(false, null, 0, 0, false, 0)
        }
    }
}
