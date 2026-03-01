package com.nongjiqianwen

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
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
import java.util.concurrent.atomic.AtomicReference

/**
 * Backend source-of-truth API wrapper.
 */
object SessionApi {
    private const val TAG = "SessionApi"
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
    private val currentStreamCall = AtomicReference<Call?>(null)
    @Volatile
    private var cachedToken: String? = null

    data class StreamOptions(
        val sessionId: String,
        val clientMsgId: String,
        val text: String,
        val images: List<String>,
        val region: String? = null,
        val regionSource: String? = null,
        val regionReliability: String? = null
    )

    private fun baseUrl(): String {
        val url = BuildConfig.UPLOAD_BASE_URL?.trim() ?: ""
        return if (url.endsWith("/")) url.dropLast(1) else url
    }

    fun hasBackendConfigured(): Boolean = baseUrl().isNotEmpty()

    fun cancelCurrentStream() {
        currentStreamCall.getAndSet(null)?.cancel()
    }

    private fun ensureAuthToken(forceRefresh: Boolean = false, onResult: (String?) -> Unit) {
        val staticToken = BuildConfig.SESSION_API_TOKEN.trim()
        if (!forceRefresh && staticToken.isNotEmpty()) {
            onResult(staticToken)
            return
        }
        if (!forceRefresh && !cachedToken.isNullOrBlank()) {
            onResult(cachedToken)
            return
        }
        val base = baseUrl()
        if (base.isEmpty()) {
            onResult(null)
            return
        }
        val body = gson.toJson(
            mapOf(
                "install_id" to IdManager.getInstallId()
            )
        )
        val request = Request.Builder()
            .url("$base/api/auth/anonymous")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "auth/anonymous failed", e)
                onResult(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        onResult(null)
                        return
                    }
                    val raw = it.body?.string().orEmpty()
                    val token = try {
                        gson.fromJson(raw, JsonObject::class.java)
                            ?.get("token")
                            ?.asString
                            ?.trim()
                            ?.takeIf { value -> value.isNotEmpty() }
                    } catch (_: Exception) {
                        null
                    }
                    cachedToken = token
                    onResult(token)
                }
            }
        })
    }

    private fun authedRequest(
        forceRefresh: Boolean = false,
        builderFactory: (String?) -> Request.Builder,
        onReady: (Request) -> Unit
    ) {
        ensureAuthToken(forceRefresh) { token ->
            val builder = builderFactory(token)
            onReady(builder.build())
        }
    }

    private fun enqueueWithRetry401(
        requestFactory: (String?) -> Request.Builder,
        onResult: (Response) -> Unit,
        onFailure: (IOException) -> Unit
    ) {
        authedRequest(forceRefresh = false, builderFactory = requestFactory) { request ->
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    onFailure(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.code == 401) {
                        response.close()
                        authedRequest(forceRefresh = true, builderFactory = requestFactory) { retryReq ->
                            client.newCall(retryReq).enqueue(object : Callback {
                                override fun onFailure(call: Call, e: IOException) = onFailure(e)
                                override fun onResponse(call: Call, response: Response) = onResult(response)
                            })
                        }
                    } else {
                        onResult(response)
                    }
                }
            })
        }
    }

    fun getEntitlement(onResult: (String?) -> Unit) {
        val base = baseUrl()
        if (base.isEmpty()) {
            onResult(null)
            return
        }
        enqueueWithRetry401(
            requestFactory = { token ->
                val builder = Request.Builder().url("$base/api/me").get()
                if (!token.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $token")
                builder
            },
            onResult = { response ->
                response.use {
                    if (!it.isSuccessful) {
                        onResult(null)
                        return@use
                    }
                    onResult(it.body?.string())
                }
            },
            onFailure = { onResult(null) }
        )
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
        enqueueWithRetry401(
            requestFactory = { token ->
                val builder = Request.Builder()
                    .url("$base/api/session/snapshot?session_id=${android.net.Uri.encode(sessionId)}")
                    .get()
                if (!token.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $token")
                builder
            },
            onResult = { response ->
                response.use {
                    if (!it.isSuccessful) {
                        onResult(null)
                        return@use
                    }
                    val body = it.body?.string() ?: ""
                    try {
                        val json = gson.fromJson(body, SessionSnapshotJson::class.java)
                        val listFromAJson = json.a_json ?: emptyList()
                        val legacyList = json.a_rounds ?: emptyList()
                        val fullList = json.a_rounds_full ?: if (listFromAJson.isNotEmpty()) listFromAJson else legacyList
                        val forUiList = json.a_rounds_for_ui ?: fullList
                        val full = fullList.map { r -> ARound(r.user ?: "", r.assistant ?: "") }
                        val forUi = forUiList.map { r -> ARound(r.user ?: "", r.assistant ?: "") }
                        onResult(SessionSnapshot(json.b_summary ?: "", full, forUi))
                    } catch (e: Exception) {
                        Log.e(TAG, "parse snapshot", e)
                        onResult(null)
                    }
                }
            },
            onFailure = {
                Log.w(TAG, "getSnapshot failed", it)
                onResult(null)
            }
        )
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
        val body = gson.toJson(
            mapOf(
                "session_id" to sessionId,
                "client_msg_id" to clientMsgId,
                "user_text" to userMessage,
                "assistant_text" to assistantMessage
            )
        )
        enqueueWithRetry401(
            requestFactory = { token ->
                val builder = Request.Builder()
                    .url("$base/api/session/round_complete")
                    .post(body.toRequestBody("application/json".toMediaType()))
                if (!token.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $token")
                builder
            },
            onResult = { response -> response.use { onResult(it.isSuccessful) } },
            onFailure = {
                Log.w(TAG, "appendA failed", it)
                onResult(false)
            }
        )
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
        val body = gson.toJson(
            mapOf(
                "session_id" to sessionId,
                "b_summary" to bSummary
            )
        )
        enqueueWithRetry401(
            requestFactory = { token ->
                val builder = Request.Builder()
                    .url("$base/api/session/b")
                    .post(body.toRequestBody("application/json".toMediaType()))
                if (!token.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $token")
                builder
            },
            onResult = { response -> response.use { onResult(it.isSuccessful) } },
            onFailure = {
                Log.w(TAG, "updateB failed", it)
                onResult(false)
            }
        )
    }

    fun streamChat(
        options: StreamOptions,
        onChunk: (String) -> Unit,
        onComplete: () -> Unit,
        onInterrupted: (String) -> Unit
    ) {
        val base = baseUrl()
        if (base.isEmpty()) {
            onInterrupted("server")
            return
        }
        val body = gson.toJson(
            mapOf(
                "session_id" to options.sessionId,
                "client_msg_id" to options.clientMsgId,
                "text" to options.text,
                "images" to options.images
            )
        )
        fun buildRequest(token: String?): Request {
            val builder = Request.Builder()
                .url("$base${ApiConfig.PATH_CHAT_STREAM}")
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "text/event-stream")
                .post(body.toRequestBody("application/json".toMediaType()))
            if (!token.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $token")
            if (!options.region.isNullOrBlank()) builder.addHeader("X-User-Region", options.region)
            if (!options.regionSource.isNullOrBlank()) builder.addHeader("X-Region-Source", options.regionSource)
            if (!options.regionReliability.isNullOrBlank()) builder.addHeader("X-Region-Reliability", options.regionReliability)
            return builder.build()
        }

        fun start(forceRefresh: Boolean, hasRetried: Boolean) {
            ensureAuthToken(forceRefresh = forceRefresh) { token ->
                val request = buildRequest(token)
                val call = client.newCall(request)
                currentStreamCall.set(call)
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        currentStreamCall.compareAndSet(call, null)
                        val reason = if (call.isCanceled()) "canceled" else "network"
                        onInterrupted(reason)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        currentStreamCall.compareAndSet(call, null)
                        response.use { res ->
                            if (res.code == 401) {
                                if (!hasRetried) {
                                    start(forceRefresh = true, hasRetried = true)
                                } else {
                                    onInterrupted("auth")
                                }
                                return
                            }
                            if (!res.isSuccessful) {
                                val reason = when (res.code) {
                                    402 -> "quota"
                                    else -> "server"
                                }
                                onInterrupted(reason)
                                return
                            }
                            val contentType = res.header("Content-Type").orEmpty().lowercase()
                            if (!contentType.contains("text/event-stream")) {
                                val bodyText = res.body?.string().orEmpty()
                                val replay = try {
                                    gson.fromJson(bodyText, JsonObject::class.java)?.get("replay")?.asBoolean == true
                                } catch (_: Exception) {
                                    false
                                }
                                onInterrupted(if (replay) "replay" else "server")
                                return
                            }

                            val source = res.body?.source()
                            if (source == null) {
                                onInterrupted("server")
                                return
                            }

                            try {
                                while (!source.exhausted()) {
                                    if (call.isCanceled()) {
                                        onInterrupted("canceled")
                                        return
                                    }
                                    val line = source.readUtf8Line() ?: continue
                                    val normalized = line.trimStart()
                                    if (normalized.isBlank() || normalized.startsWith(":") || normalized.startsWith("event:")) continue
                                    if (!normalized.startsWith("data:")) continue
                                    val data = normalized.removePrefix("data:").trimStart()
                                    if (data == "[DONE]") {
                                        onComplete()
                                        return
                                    }
                                    try {
                                        val obj = gson.fromJson(data, JsonObject::class.java) ?: continue
                                        val choices = obj.getAsJsonArray("choices") ?: continue
                                        if (choices.size() == 0) continue
                                        val choice0 = choices[0].asJsonObject
                                        val piece =
                                            choice0.getAsJsonObject("delta")
                                                ?.get("content")
                                                ?.takeIf { it.isJsonPrimitive }
                                                ?.asString
                                                ?.takeIf { it.isNotBlank() }
                                                ?: choice0.getAsJsonObject("message")
                                                    ?.get("content")
                                                    ?.takeIf { it.isJsonPrimitive }
                                                    ?.asString
                                                    ?.takeIf { it.isNotBlank() }
                                        if (!piece.isNullOrBlank()) {
                                            onChunk(piece)
                                        }
                                    } catch (_: Exception) {
                                        // skip malformed chunk and continue
                                    }
                                }
                                onInterrupted("server")
                            } catch (_: Exception) {
                                onInterrupted(if (call.isCanceled()) "canceled" else "server")
                            }
                        }
                    }
                })
            }
        }

        start(forceRefresh = false, hasRetried = false)
    }

    private data class SessionSnapshotJson(
        @SerializedName("b_summary") val b_summary: String?,
        @SerializedName("a_json") val a_json: List<ARoundJson>? = null,
        @SerializedName("a_rounds_full") val a_rounds_full: List<ARoundJson>?,
        @SerializedName("a_rounds_for_ui") val a_rounds_for_ui: List<ARoundJson>?,
        @SerializedName("a_rounds") val a_rounds: List<ARoundJson>? = null
    )

    private data class ARoundJson(
        @SerializedName("user") val user: String?,
        @SerializedName("assistant") val assistant: String?
    )
}

