package com.nongjiqianwen

import android.os.Handler
import android.os.Looper
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
    private const val SNAPSHOT_NETWORK_RETRY_MAX = 2
    private const val STREAM_NETWORK_RETRY_MAX = 2
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
    private val currentStreamCall = AtomicReference<Call?>(null)

    data class StreamOptions(
        val clientMsgId: String,
        val text: String,
        val images: List<String>,
        val region: String? = null,
        val regionSource: String? = null,
        val regionReliability: String? = null
    )

    private fun baseUrl(): String {
        val url = BuildConfig.UPLOAD_BASE_URL.trim()
        return if (url.endsWith("/")) url.dropLast(1) else url
    }

    fun hasBackendConfigured(): Boolean = baseUrl().isNotEmpty()

    fun cancelCurrentStream() {
        currentStreamCall.getAndSet(null)?.cancel()
    }

    private fun ensureAuthToken(onResult: (String?) -> Unit) {
        val staticToken = BuildConfig.SESSION_API_TOKEN.trim()
        if (staticToken.isNotEmpty()) {
            onResult(staticToken)
        } else {
            onResult(null)
        }
    }

    private fun authedRequest(
        builderFactory: (String?) -> Request.Builder,
        onReady: (Request) -> Unit
    ) {
        ensureAuthToken { token ->
            val builder = builderFactory(token)
            onReady(builder.build())
        }
    }

    private fun applyIdentityHeaders(builder: Request.Builder): Request.Builder =
        builder.addHeader("X-User-Id", IdManager.getUserId())

    private fun enqueueWithRetry401(
        requestFactory: (String?) -> Request.Builder,
        onResult: (Response) -> Unit,
        onFailure: (IOException) -> Unit
    ) {
        authedRequest(builderFactory = requestFactory) { request ->
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    onFailure(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.code == 401) {
                        response.close()
                        authedRequest(builderFactory = requestFactory) { retryReq ->
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
                val builder = applyIdentityHeaders(Request.Builder().url("$base/api/me").get())
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

    fun getSnapshot(onResult: (SessionSnapshot?) -> Unit) {
        if (!BuildConfig.USE_BACKEND_AB) {
            onResult(null)
            return
        }
        val base = baseUrl()
        if (base.isEmpty()) {
            onResult(null)
            return
        }
        fun attempt(networkRetry: Int) {
            enqueueWithRetry401(
                requestFactory = { token ->
                    val builder = applyIdentityHeaders(
                        Request.Builder()
                            .url("$base/api/session/snapshot")
                            .get()
                    )
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
                            onResult(SessionSnapshot(json.b_summary ?: "", json.c_summary ?: "", full, forUi))
                        } catch (e: Exception) {
                            Log.e(TAG, "parse snapshot", e)
                            onResult(null)
                        }
                    }
                },
                onFailure = { error ->
                    if (networkRetry < SNAPSHOT_NETWORK_RETRY_MAX) {
                        val retryDelayMs = 300L * (networkRetry + 1)
                        mainHandler.postDelayed({ attempt(networkRetry + 1) }, retryDelayMs)
                    } else {
                        Log.w(TAG, "getSnapshot failed", error)
                        onResult(null)
                    }
                }
            )
        }
        attempt(networkRetry = 0)
    }

    fun appendA(
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
                "client_msg_id" to clientMsgId,
                "user_text" to userMessage,
                "assistant_text" to assistantMessage
            )
        )
        enqueueWithRetry401(
            requestFactory = { token ->
                val builder = applyIdentityHeaders(
                    Request.Builder()
                        .url("$base/api/session/round_complete")
                        .post(body.toRequestBody("application/json".toMediaType()))
                )
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

    fun updateB(bSummary: String, onResult: (Boolean) -> Unit) {
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
                "b_summary" to bSummary
            )
        )
        enqueueWithRetry401(
            requestFactory = { token ->
                val builder = applyIdentityHeaders(
                    Request.Builder()
                        .url("$base/api/session/b")
                        .post(body.toRequestBody("application/json".toMediaType()))
                )
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

    fun updateC(cSummary: String, onResult: (Boolean) -> Unit) {
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
                "c_summary" to cSummary
            )
        )
        enqueueWithRetry401(
            requestFactory = { token ->
                val builder = applyIdentityHeaders(
                    Request.Builder()
                        .url("$base/api/session/c")
                        .post(body.toRequestBody("application/json".toMediaType()))
                )
                if (!token.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $token")
                builder
            },
            onResult = { response -> response.use { onResult(it.isSuccessful) } },
            onFailure = {
                Log.w(TAG, "updateC failed", it)
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
                "client_msg_id" to options.clientMsgId,
                "text" to options.text,
                "images" to options.images
            )
        )
        fun buildRequest(token: String?): Request {
            val builder = applyIdentityHeaders(
                Request.Builder()
                    .url("$base${ApiConfig.PATH_CHAT_STREAM}")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "text/event-stream")
                    .post(body.toRequestBody("application/json".toMediaType()))
            )
            if (!token.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $token")
            if (!options.region.isNullOrBlank()) builder.addHeader("X-User-Region", options.region)
            if (!options.regionSource.isNullOrBlank()) builder.addHeader("X-Region-Source", options.regionSource)
            if (!options.regionReliability.isNullOrBlank()) builder.addHeader("X-Region-Reliability", options.regionReliability)
            return builder.build()
        }

        var deliveredAnyChunk = false

        fun start(hasRetried: Boolean, networkRetry: Int) {
            ensureAuthToken { token ->
                val request = buildRequest(token)
                val call = client.newCall(request)
                currentStreamCall.set(call)
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        currentStreamCall.compareAndSet(call, null)
                        if (!call.isCanceled() && !deliveredAnyChunk && networkRetry < STREAM_NETWORK_RETRY_MAX) {
                            val retryDelayMs = 350L * (networkRetry + 1)
                            mainHandler.postDelayed({
                                start(hasRetried = hasRetried, networkRetry = networkRetry + 1)
                            }, retryDelayMs)
                            return
                        }
                        val reason = if (call.isCanceled()) "canceled" else "network"
                        onInterrupted(reason)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        currentStreamCall.compareAndSet(call, null)
                        response.use { res ->
                            if (res.code == 401) {
                                if (!hasRetried) {
                                    start(hasRetried = true, networkRetry = networkRetry)
                                } else {
                                    onInterrupted("auth")
                                }
                                return
                            }
                            if (!res.isSuccessful) {
                                if (!deliveredAnyChunk && networkRetry < STREAM_NETWORK_RETRY_MAX && (res.code == 502 || res.code == 503 || res.code == 504)) {
                                    val retryDelayMs = 350L * (networkRetry + 1)
                                    mainHandler.postDelayed({
                                        start(hasRetried = hasRetried, networkRetry = networkRetry + 1)
                                    }, retryDelayMs)
                                    return
                                }
                                val reason = when (res.code) {
                                    503 -> "model_unavailable"
                                    429 -> "rate_limit"
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
                                                ?.takeIf { it.isNotEmpty() }
                                                ?: choice0.getAsJsonObject("message")
                                                    ?.get("content")
                                                    ?.takeIf { it.isJsonPrimitive }
                                                    ?.asString
                                                    ?.takeIf { it.isNotEmpty() }
                                        if (!piece.isNullOrEmpty()) {
                                            deliveredAnyChunk = true
                                            onChunk(piece)
                                        }
                                    } catch (_: Exception) {
                                        // skip malformed chunk and continue
                                    }
                                }
                                onInterrupted("server")
                            } catch (_: Exception) {
                                if (!call.isCanceled() && !deliveredAnyChunk && networkRetry < STREAM_NETWORK_RETRY_MAX) {
                                    val retryDelayMs = 350L * (networkRetry + 1)
                                    mainHandler.postDelayed({
                                        start(hasRetried = hasRetried, networkRetry = networkRetry + 1)
                                    }, retryDelayMs)
                                    return
                                }
                                onInterrupted(if (call.isCanceled()) "canceled" else "server")
                            }
                        }
                    }
                })
            }
        }

        start(hasRetried = false, networkRetry = 0)
    }

    private data class SessionSnapshotJson(
        @SerializedName("b_summary") val b_summary: String?,
        @SerializedName("c_summary") val c_summary: String? = null,
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
