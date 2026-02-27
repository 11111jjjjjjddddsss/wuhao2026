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
 * 会话两条线：GET snapshot / POST round_complete / POST b。
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

    private fun applyAuth(builder: Request.Builder): Request.Builder {
        val token = BuildConfig.SESSION_API_TOKEN.trim()
        if (token.isNotEmpty()) {
            builder.addHeader("Authorization", "Bearer $token")
        }
        return builder
    }

    fun getEntitlement(onResult: (String?) -> Unit) {
        val base = baseUrl()
        if (base.isEmpty()) {
            onResult(null)
            return
        }
        val request = applyAuth(Request.Builder())
            .url("$base/api/me")
            .get()
            .build()
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

    fun getSnapshot(sessionId: String, onResult: (SessionSnapshot?) -> Unit) {
        if (!BuildConfig.USE_BACKEND_AB) {
            onResult(null)
            return
        }
        val base = baseUrl()
        if (base.isEmpty()) {
            onResult(null)
            return
        }
        val request = applyAuth(Request.Builder())
            .url("$base/api/session/snapshot?session_id=${android.net.Uri.encode(sessionId)}")
            .get()
            .build()
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

    fun appendA(
        sessionId: String,
        clientMsgId: String,
        userMessage: String,
        assistantMessage: String,
        onResult: (Boolean) -> Unit
    ) {
        if (!BuildConfig.USE_BACKEND_AB) {
            onResult(false)
            return
        }
        val base = baseUrl()
        if (base.isEmpty()) {
            onResult(false)
            return
        }
        val bodyMap = mutableMapOf(
            "session_id" to sessionId,
            "client_msg_id" to clientMsgId,
            "user_text" to userMessage,
            "assistant_text" to assistantMessage
        )
        val body = gson.toJson(bodyMap)
        val request = applyAuth(Request.Builder())
            .url("$base/api/session/round_complete")
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

    fun updateB(sessionId: String, bSummary: String, onResult: (Boolean) -> Unit) {
        if (!BuildConfig.USE_BACKEND_AB) {
            onResult(false)
            return
        }
        val base = baseUrl()
        if (base.isEmpty()) {
            onResult(false)
            return
        }
        val bodyMap = mutableMapOf(
            "session_id" to sessionId,
            "b_summary" to bSummary
        )
        val body = gson.toJson(bodyMap)
        val request = applyAuth(Request.Builder())
            .url("$base/api/session/b")
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
