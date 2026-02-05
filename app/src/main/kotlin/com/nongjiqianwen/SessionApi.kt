package com.nongjiqianwen

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 会话两条线：GET snapshot / POST append-a / POST update-b。
 * 仅当 BuildConfig.USE_BACKEND_AB 且 baseUrl 非空时真正请求；否则回调 onFailure。
 */
object SessionApi {
    private const val TAG = "SessionApi"
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun baseUrl(): String {
        val url = BuildConfig.UPLOAD_BASE_URL?.trim() ?: ""
        return if (url.endsWith("/")) url.dropLast(1) else url
    }

    fun getEntitlement(userId: String, onResult: (String?) -> Unit) {
        val base = baseUrl()
        if (base.isEmpty()) {
            onResult(null)
            return
        }
        val url = "$base/api/entitlement?user_id=${android.net.Uri.encode(userId)}"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(null)
            }
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    onResult(null)
                    return
                }
                onResult(response.body?.string())
            }
        })
    }

    fun getSnapshot(userId: String, sessionId: String, onResult: (SessionSnapshot?) -> Unit) {
        if (!BuildConfig.USE_BACKEND_AB) {
            onResult(null)
            return
        }
        val base = baseUrl()
        if (base.isEmpty()) {
            onResult(null)
            return
        }
        val url = "$base/api/session/snapshot?user_id=${android.net.Uri.encode(userId)}&session_id=${android.net.Uri.encode(sessionId)}"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "getSnapshot failed", e)
                onResult(null)
            }
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    onResult(null)
                    return
                }
                val body = response.body?.string() ?: ""
                try {
                    val json = gson.fromJson(body, SessionSnapshotJson::class.java)
                    val legacyList = json.a_rounds ?: emptyList()
                    val fullList = json.a_rounds_full ?: legacyList
                    val forUiList = json.a_rounds_for_ui ?: fullList
                    val full = fullList.map { r -> ARound(r.user ?: "", r.assistant ?: "") }
                    val forUi = forUiList.map { r -> ARound(r.user ?: "", r.assistant ?: "") }
                    onResult(SessionSnapshot(json.b_summary ?: "", full, forUi))
                } catch (e: Exception) {
                    Log.e(TAG, "parse snapshot", e)
                    onResult(null)
                }
            }
        })
    }

    fun appendA(userId: String, sessionId: String, userMessage: String, assistantMessage: String, onResult: (Boolean) -> Unit) {
        if (!BuildConfig.USE_BACKEND_AB) {
            onResult(false)
            return
        }
        val base = baseUrl()
        if (base.isEmpty()) {
            onResult(false)
            return
        }
        val body = gson.toJson(mapOf(
            "user_id" to userId,
            "session_id" to sessionId,
            "user_message" to userMessage,
            "assistant_message" to assistantMessage
        ))
        val request = Request.Builder()
            .url("$base/api/session/append-a")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "appendA failed", e)
                onResult(false)
            }
            override fun onResponse(call: Call, response: Response) {
                onResult(response.isSuccessful)
            }
        })
    }

    fun updateB(userId: String, sessionId: String, bSummary: String, onResult: (Boolean) -> Unit) {
        if (!BuildConfig.USE_BACKEND_AB) {
            onResult(false)
            return
        }
        val base = baseUrl()
        if (base.isEmpty()) {
            onResult(false)
            return
        }
        val body = gson.toJson(mapOf(
            "user_id" to userId,
            "session_id" to sessionId,
            "b_summary" to bSummary
        ))
        val request = Request.Builder()
            .url("$base/api/session/update-b")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "updateB failed", e)
                onResult(false)
            }
            override fun onResponse(call: Call, response: Response) {
                onResult(response.isSuccessful)
            }
        })
    }

    private data class SessionSnapshotJson(
        @SerializedName("b_summary") val b_summary: String?,
        @SerializedName("a_rounds_full") val a_rounds_full: List<ARoundJson>?,
        @SerializedName("a_rounds_for_ui") val a_rounds_for_ui: List<ARoundJson>?,
        @SerializedName("a_rounds") val a_rounds: List<ARoundJson>? = null
    )
    private data class ARoundJson(
        @SerializedName("user") val user: String?,
        @SerializedName("assistant") val assistant: String?
    )
}
