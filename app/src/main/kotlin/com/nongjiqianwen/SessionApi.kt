package com.nongjiqianwen

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Backend source-of-truth API wrapper.
 */
object SessionApi {
    private const val TAG = "SessionApi"
    private const val SNAPSHOT_NETWORK_RETRY_MAX = 2
    private const val STREAM_NETWORK_RETRY_MAX = 0
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
    private val currentStreamCall = AtomicReference<Call?>(null)
    private val runtimeGeneration = AtomicInteger(0)

    data class StreamOptions(
        val clientMsgId: String,
        val text: String,
        val images: List<String>,
        val region: String? = null,
        val regionSource: String? = null,
        val regionReliability: String? = null
    )

    enum class StreamCompletionStatus {
        Complete,
        Replay,
        RetryableFailure,
        Quota,
        RateLimited,
        Auth,
        BadRequest,
        ServerFailure
    }

    data class StreamCompletionResult(
        val status: StreamCompletionStatus,
        val reason: String? = null
    ) {
        val shouldRetry: Boolean
            get() = status == StreamCompletionStatus.RetryableFailure
    }

    data class EntitlementSnapshot(
        val tier: String? = null,
        @SerializedName("tier_expire_at") val tierExpireAt: Long? = null,
        @SerializedName("daily_remaining") val dailyRemaining: Int? = null,
        @SerializedName("topup_remaining") val topupRemaining: Int? = null,
        @SerializedName("topup_earliest_expire_at") val topupEarliestExpireAt: Long? = null,
        @SerializedName("upgrade_remaining") val upgradeRemaining: Int? = null
    )

    data class TodayAgriCardResponse(
        val status: String? = null,
        val card: TodayAgriCard? = null
    )

    data class TodayAgriCard(
        @SerializedName("date_cn") val dateCn: String? = null,
        val title: String? = null,
        val items: List<TodayAgriCardItem>? = null,
        @SerializedName("generated_at") val generatedAt: Long? = null
    )

    data class TodayAgriCardItem(
        val title: String? = null,
        val summary: String? = null,
        val url: String? = null,
        val source: String? = null,
        @SerializedName("published_date") val publishedDate: String? = null
    )

    data class SupportMessage(
        val id: Long? = null,
        @SerializedName("user_id") val userId: String? = null,
        @SerializedName("sender_type") val senderType: String? = null,
        val body: String? = null,
        @SerializedName("image_urls") val imageUrls: List<String>? = null,
        @SerializedName("created_at") val createdAt: Long? = null,
        @SerializedName("read_by_user_at") val readByUserAt: Long? = null
    )

    data class SupportSummary(
        @SerializedName("unread_count") val unreadCount: Int? = null,
        @SerializedName("latest_message") val latestMessage: SupportMessage? = null
    )

    data class AppUpdateInfo(
        val platform: String? = null,
        @SerializedName("current_version_code") val currentVersionCode: Int? = null,
        @SerializedName("current_version_name") val currentVersionName: String? = null,
        @SerializedName("latest_version_code") val latestVersionCode: Int? = null,
        @SerializedName("latest_version_name") val latestVersionName: String? = null,
        @SerializedName("has_update") val hasUpdate: Boolean? = null,
        @SerializedName("force_update") val forceUpdate: Boolean? = null,
        @SerializedName("apk_url") val apkUrl: String? = null,
        @SerializedName("release_notes") val releaseNotes: String? = null,
        @SerializedName("file_size_bytes") val fileSizeBytes: Long? = null
    ) {
        val usableUpdate: Boolean
            get() = hasUpdate == true && !apkUrl.isNullOrBlank() && apkUrl.trim().startsWith("https://")
    }

    enum class ClearSessionHistoryResult {
        Success,
        ActiveStream,
        Failure
    }

    private fun TodayAgriCard?.isValidTodayAgriCard(): Boolean {
        val candidate = this ?: return false
        val items = candidate.items.orEmpty()
        return candidate.title == "今日农情" &&
            items.size == 3 &&
            items.all { item ->
                !item.title.isNullOrBlank() &&
                    !item.summary.isNullOrBlank() &&
                    !item.url.isNullOrBlank() &&
                    item.url.trim().startsWith("https://")
            }
    }

    private fun baseUrl(): String {
        val url = BuildConfig.UPLOAD_BASE_URL.trim()
        return if (url.endsWith("/")) url.dropLast(1) else url
    }

    fun hasBackendConfigured(): Boolean = baseUrl().isNotEmpty()

    fun cancelCurrentStream() {
        currentStreamCall.getAndSet(null)?.cancel()
    }

    fun resetUiRuntimeForCleanState() {
        runtimeGeneration.incrementAndGet()
        mainHandler.removeCallbacksAndMessages(null)
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

    private fun authTokenSync(): String? =
        BuildConfig.SESSION_API_TOKEN.trim().takeIf { it.isNotEmpty() }

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

    fun getEntitlement(onResult: (EntitlementSnapshot?) -> Unit) {
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
                    val body = it.body?.string()
                    if (body.isNullOrBlank()) {
                        onResult(null)
                        return@use
                    }
                    try {
                        onResult(gson.fromJson(body, EntitlementSnapshot::class.java))
                    } catch (e: Exception) {
                        Log.e(TAG, "parse entitlement", e)
                        onResult(null)
                    }
                }
            },
            onFailure = { onResult(null) }
        )
    }

    fun getTodayAgriCard(onResult: (TodayAgriCard?) -> Unit) {
        val requestGeneration = runtimeGeneration.get()
        fun isRuntimeStale(): Boolean = requestGeneration != runtimeGeneration.get()
        val base = baseUrl()
        if (base.isEmpty()) {
            onResult(null)
            return
        }
        enqueueWithRetry401(
            requestFactory = { token ->
                val builder = applyIdentityHeaders(Request.Builder().url("$base/api/today-agri-card").get())
                if (!token.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $token")
                builder
            },
            onResult = { response ->
                response.use {
                    if (isRuntimeStale()) return@use
                    if (!it.isSuccessful) {
                        onResult(null)
                        return@use
                    }
                    val body = it.body?.string()
                    if (body.isNullOrBlank()) {
                        onResult(null)
                        return@use
                    }
                    try {
                        val parsed = gson.fromJson(body, TodayAgriCardResponse::class.java)
                        val validCard = parsed
                            ?.takeIf { it.status == "ready" }
                            ?.card
                            ?.takeIf { it.isValidTodayAgriCard() }
                        onResult(validCard)
                    } catch (e: Exception) {
                        Log.e(TAG, "parse today agri card", e)
                        onResult(null)
                    }
                }
            },
            onFailure = { onResult(null) }
        )
    }

    fun getSupportSummary(onResult: (SupportSummary?) -> Unit) {
        val base = baseUrl()
        if (base.isEmpty()) {
            postToMain { onResult(null) }
            return
        }
        enqueueWithRetry401(
            requestFactory = { token ->
                val builder = applyIdentityHeaders(Request.Builder().url("$base/api/support/summary").get())
                if (!token.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $token")
                builder
            },
            onResult = { response ->
                response.use {
                    if (!it.isSuccessful) {
                        postToMain { onResult(null) }
                        return@use
                    }
                    val body = it.body?.string()
                    if (body.isNullOrBlank()) {
                        postToMain { onResult(null) }
                        return@use
                    }
                    val parsed = try {
                        gson.fromJson(body, SupportSummary::class.java)
                    } catch (e: Exception) {
                        Log.e(TAG, "parse support summary", e)
                        null
                    }
                    postToMain { onResult(parsed) }
                }
            },
            onFailure = { postToMain { onResult(null) } }
        )
    }

    fun getSupportMessages(onResult: (List<SupportMessage>?) -> Unit) {
        val base = baseUrl()
        if (base.isEmpty()) {
            postToMain { onResult(null) }
            return
        }
        enqueueWithRetry401(
            requestFactory = { token ->
                val builder = applyIdentityHeaders(Request.Builder().url("$base/api/support/messages").get())
                if (!token.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $token")
                builder
            },
            onResult = { response ->
                response.use {
                    if (!it.isSuccessful) {
                        postToMain { onResult(null) }
                        return@use
                    }
                    val body = it.body?.string()
                    if (body.isNullOrBlank()) {
                        postToMain { onResult(emptyList()) }
                        return@use
                    }
                    val parsed = try {
                        gson.fromJson(body, SupportMessagesResponse::class.java)?.messages.orEmpty()
                    } catch (e: Exception) {
                        Log.e(TAG, "parse support messages", e)
                        null
                    }
                    postToMain { onResult(parsed) }
                }
            },
            onFailure = { postToMain { onResult(null) } }
        )
    }

    fun sendSupportMessage(
        body: String,
        images: List<String> = emptyList(),
        onResult: (SupportMessage?) -> Unit
    ) {
        val base = baseUrl()
        if (base.isEmpty()) {
            postToMain { onResult(null) }
            return
        }
        val requestBody = gson.toJson(
            mapOf(
                "body" to body,
                "images" to images
            )
        ).toRequestBody("application/json".toMediaType())
        enqueueWithRetry401(
            requestFactory = { token ->
                val builder = applyIdentityHeaders(
                    Request.Builder()
                        .url("$base/api/support/messages")
                        .addHeader("Content-Type", "application/json")
                        .post(requestBody)
                )
                if (!token.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $token")
                builder
            },
            onResult = { response ->
                response.use {
                    if (!it.isSuccessful) {
                        postToMain { onResult(null) }
                        return@use
                    }
                    val bodyText = it.body?.string()
                    if (bodyText.isNullOrBlank()) {
                        postToMain { onResult(null) }
                        return@use
                    }
                    val parsed = try {
                        gson.fromJson(bodyText, SupportMessageResponse::class.java)?.message
                    } catch (e: Exception) {
                        Log.e(TAG, "parse sent support message", e)
                        null
                    }
                    postToMain { onResult(parsed) }
                }
            },
            onFailure = { postToMain { onResult(null) } }
        )
    }

    fun markSupportRead(onResult: (Boolean) -> Unit = {}) {
        val base = baseUrl()
        if (base.isEmpty()) {
            postToMain { onResult(false) }
            return
        }
        val requestBody = "{}".toRequestBody("application/json".toMediaType())
        enqueueWithRetry401(
            requestFactory = { token ->
                val builder = applyIdentityHeaders(
                    Request.Builder()
                        .url("$base/api/support/read")
                        .addHeader("Content-Type", "application/json")
                        .post(requestBody)
                )
                if (!token.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $token")
                builder
            },
            onResult = { response ->
                response.use {
                    postToMain { onResult(it.isSuccessful) }
                }
            },
            onFailure = { postToMain { onResult(false) } }
        )
    }

    fun clearSessionHistory(onResult: (ClearSessionHistoryResult) -> Unit) {
        val base = baseUrl()
        if (base.isEmpty()) {
            postToMain { onResult(ClearSessionHistoryResult.Failure) }
            return
        }
        val requestBody = "{}".toRequestBody("application/json".toMediaType())
        enqueueWithRetry401(
            requestFactory = { token ->
                val builder = applyIdentityHeaders(
                    Request.Builder()
                        .url("$base/api/session/clear")
                        .addHeader("Content-Type", "application/json")
                        .post(requestBody)
                )
                if (!token.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $token")
                builder
            },
            onResult = { response ->
                response.use {
                    val result = when {
                        it.isSuccessful -> ClearSessionHistoryResult.Success
                        it.code == 409 -> ClearSessionHistoryResult.ActiveStream
                        else -> ClearSessionHistoryResult.Failure
                    }
                    postToMain { onResult(result) }
                }
            },
            onFailure = { postToMain { onResult(ClearSessionHistoryResult.Failure) } }
        )
    }

    fun getAppUpdate(onResult: (AppUpdateInfo?) -> Unit) {
        val base = baseUrl()
        if (base.isEmpty()) {
            postToMain { onResult(null) }
            return
        }
        val url = "$base/api/app/update"
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("platform", "android")
            ?.addQueryParameter("version_code", BuildConfig.VERSION_CODE.toString())
            ?.addQueryParameter("version_name", BuildConfig.VERSION_NAME)
            ?.build()
        if (url == null) {
            postToMain { onResult(null) }
            return
        }
        enqueueWithRetry401(
            requestFactory = { token ->
                val builder = applyIdentityHeaders(Request.Builder().url(url).get())
                if (!token.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $token")
                builder
            },
            onResult = { response ->
                response.use {
                    if (!it.isSuccessful) {
                        postToMain { onResult(null) }
                        return@use
                    }
                    val body = it.body?.string()
                    if (body.isNullOrBlank()) {
                        postToMain { onResult(null) }
                        return@use
                    }
                    val parsed = try {
                        gson.fromJson(body, AppUpdateInfo::class.java)
                    } catch (e: Exception) {
                        Log.e(TAG, "parse app update", e)
                        null
                    }
                    postToMain { onResult(parsed) }
                }
            },
            onFailure = { postToMain { onResult(null) } }
        )
    }

    fun getSnapshot(onResult: (SessionSnapshot?) -> Unit) {
        val requestGeneration = runtimeGeneration.get()
        fun isRuntimeStale(): Boolean = requestGeneration != runtimeGeneration.get()
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
            if (isRuntimeStale()) return
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
                        if (isRuntimeStale()) {
                            return@use
                        }
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
                            val full = fullList.map { r ->
                                ARound(
                                    client_msg_id = r.client_msg_id,
                                    user = r.user ?: "",
                                    user_images = r.user_images ?: emptyList(),
                                    assistant = r.assistant ?: "",
                                    created_at = r.created_at ?: 0L,
                                    region = r.region,
                                    region_source = r.region_source,
                                    region_reliability = r.region_reliability
                                )
                            }
                            val forUi = forUiList.map { r ->
                                ARound(
                                    client_msg_id = r.client_msg_id,
                                    user = r.user ?: "",
                                    user_images = r.user_images ?: emptyList(),
                                    assistant = r.assistant ?: "",
                                    created_at = r.created_at ?: 0L,
                                    region = r.region,
                                    region_source = r.region_source,
                                    region_reliability = r.region_reliability
                                )
                            }
                            onResult(SessionSnapshot(json.b_summary ?: "", json.c_summary ?: "", full, forUi))
                        } catch (e: Exception) {
                            Log.e(TAG, "parse snapshot", e)
                            onResult(null)
                        }
                    }
                },
                onFailure = { error ->
                    if (isRuntimeStale()) return@enqueueWithRetry401
                    if (networkRetry < SNAPSHOT_NETWORK_RETRY_MAX) {
                        val retryDelayMs = 300L * (networkRetry + 1)
                        mainHandler.postDelayed({
                            if (isRuntimeStale()) return@postDelayed
                            attempt(networkRetry + 1)
                        }, retryDelayMs)
                    } else {
                        Log.w(TAG, "getSnapshot failed", error)
                        onResult(null)
                    }
                }
            )
        }
        attempt(networkRetry = 0)
    }

    fun streamChat(
        options: StreamOptions,
        onChunk: (String) -> Unit,
        onComplete: () -> Unit,
        onInterrupted: (String) -> Unit
    ) {
        val requestGeneration = runtimeGeneration.get()
        fun isRuntimeStale(): Boolean = requestGeneration != runtimeGeneration.get()
        fun deliverChunk(piece: String) {
            if (!isRuntimeStale()) onChunk(piece)
        }
        fun deliverComplete() {
            if (!isRuntimeStale()) onComplete()
        }
        fun deliverInterrupted(reason: String) {
            if (!isRuntimeStale()) onInterrupted(reason)
        }
        val base = baseUrl()
        if (base.isEmpty()) {
            deliverInterrupted("server")
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
            if (isRuntimeStale()) return
            ensureAuthToken { token ->
                if (isRuntimeStale()) return@ensureAuthToken
                val request = buildRequest(token)
                val call = client.newCall(request)
                currentStreamCall.set(call)
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        currentStreamCall.compareAndSet(call, null)
                        if (isRuntimeStale()) return
                        if (!call.isCanceled() && !deliveredAnyChunk && networkRetry < STREAM_NETWORK_RETRY_MAX) {
                            val retryDelayMs = 350L * (networkRetry + 1)
                            mainHandler.postDelayed({
                                if (isRuntimeStale()) return@postDelayed
                                start(hasRetried = hasRetried, networkRetry = networkRetry + 1)
                            }, retryDelayMs)
                            return
                        }
                        val reason = if (call.isCanceled()) "canceled" else "network"
                        deliverInterrupted(reason)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            response.use { res ->
                                if (isRuntimeStale()) return@use
                                if (res.code == 401) {
                                    if (!hasRetried) {
                                        start(hasRetried = true, networkRetry = networkRetry)
                                    } else {
                                        deliverInterrupted("auth")
                                    }
                                    return
                                }
                                if (!res.isSuccessful) {
                                    if (!deliveredAnyChunk && networkRetry < STREAM_NETWORK_RETRY_MAX && (res.code == 502 || res.code == 503 || res.code == 504)) {
                                        val retryDelayMs = 350L * (networkRetry + 1)
                                        mainHandler.postDelayed({
                                            if (isRuntimeStale()) return@postDelayed
                                            start(hasRetried = hasRetried, networkRetry = networkRetry + 1)
                                        }, retryDelayMs)
                                        return
                                    }
                                    val reason = when (res.code) {
                                        409 -> "stream_in_progress"
                                        503 -> "model_unavailable"
                                        429 -> "rate_limit"
                                        402 -> "quota"
                                        else -> "server"
                                    }
                                    deliverInterrupted(reason)
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
                                    deliverInterrupted(if (replay) "replay" else "server")
                                    return
                                }

                                val source = res.body?.source()
                                if (source == null) {
                                    deliverInterrupted("server")
                                    return
                                }

                                try {
                                    var sawReplay = false
                                    while (!source.exhausted()) {
                                        if (isRuntimeStale()) return
                                        if (call.isCanceled()) {
                                            deliverInterrupted("canceled")
                                            return
                                        }
                                        val line = source.readUtf8Line() ?: continue
                                        val normalized = line.trimStart()
                                        if (normalized.isBlank() || normalized.startsWith(":") || normalized.startsWith("event:")) continue
                                        if (!normalized.startsWith("data:")) continue
                                        val data = normalized.removePrefix("data:").trimStart()
                                        if (data == "[DONE]") {
                                            if (sawReplay) {
                                                deliverInterrupted("replay")
                                            } else {
                                                deliverComplete()
                                            }
                                            return
                                        }
                                        try {
                                            val obj = gson.fromJson(data, JsonObject::class.java) ?: continue
                                            if (obj.get("replay")?.takeIf { it.isJsonPrimitive }?.asBoolean == true) {
                                                sawReplay = true
                                                continue
                                            }
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
                                                deliverChunk(piece)
                                            }
                                        } catch (_: Exception) {
                                            // skip malformed chunk and continue
                                        }
                                    }
                                    deliverInterrupted("server")
                                } catch (_: Exception) {
                                    if (isRuntimeStale()) return
                                    if (!call.isCanceled() && !deliveredAnyChunk && networkRetry < STREAM_NETWORK_RETRY_MAX) {
                                        val retryDelayMs = 350L * (networkRetry + 1)
                                        mainHandler.postDelayed({
                                            if (isRuntimeStale()) return@postDelayed
                                            start(hasRetried = hasRetried, networkRetry = networkRetry + 1)
                                        }, retryDelayMs)
                                        return
                                    }
                                    deliverInterrupted(if (call.isCanceled()) "canceled" else "server")
                                }
                            }
                        } finally {
                            currentStreamCall.compareAndSet(call, null)
                        }
                    }
                })
            }
        }

        start(hasRetried = false, networkRetry = 0)
    }

    fun streamChatToCompletion(
        options: StreamOptions,
        shouldContinue: () -> Boolean = { true }
    ): StreamCompletionResult {
        val base = baseUrl()
        if (base.isEmpty()) {
            return StreamCompletionResult(StreamCompletionStatus.ServerFailure, "server")
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

        fun runRequest(hasRetriedAuth: Boolean, networkRetry: Int): StreamCompletionResult {
            val request = buildRequest(authTokenSync())
            val call = client.newCall(request)
            return try {
                if (!shouldContinue()) {
                    call.cancel()
                    return StreamCompletionResult(StreamCompletionStatus.RetryableFailure, "stopped")
                }
                call.execute().use { res ->
                    if (res.code == 401) {
                        return if (!hasRetriedAuth) {
                            runRequest(hasRetriedAuth = true, networkRetry = networkRetry)
                        } else {
                            StreamCompletionResult(StreamCompletionStatus.Auth, "auth")
                        }
                    }
                    if (!res.isSuccessful) {
                        if (networkRetry < STREAM_NETWORK_RETRY_MAX && (res.code == 502 || res.code == 503 || res.code == 504)) {
                            Thread.sleep(350L * (networkRetry + 1))
                            return runRequest(hasRetriedAuth = hasRetriedAuth, networkRetry = networkRetry + 1)
                        }
                        val status = when (res.code) {
                            400 -> StreamCompletionStatus.BadRequest
                            409 -> StreamCompletionStatus.RetryableFailure
                            402 -> StreamCompletionStatus.Quota
                            429 -> StreamCompletionStatus.RateLimited
                            500, 502, 503, 504 -> StreamCompletionStatus.RetryableFailure
                            else -> StreamCompletionStatus.ServerFailure
                        }
                        return StreamCompletionResult(status, "http_${res.code}")
                    }
                    val contentType = res.header("Content-Type").orEmpty().lowercase()
                    if (!contentType.contains("text/event-stream")) {
                        val bodyText = res.body?.string().orEmpty()
                        val replay = try {
                            gson.fromJson(bodyText, JsonObject::class.java)?.get("replay")?.asBoolean == true
                        } catch (_: Exception) {
                            false
                        }
                        return StreamCompletionResult(
                            if (replay) StreamCompletionStatus.Replay else StreamCompletionStatus.ServerFailure,
                            if (replay) "replay" else "server"
                        )
                    }
                    val source = res.body?.source()
                        ?: return StreamCompletionResult(StreamCompletionStatus.ServerFailure, "server")
                    var sawReplay = false
                    while (!source.exhausted()) {
                        if (!shouldContinue()) {
                            call.cancel()
                            return StreamCompletionResult(StreamCompletionStatus.RetryableFailure, "stopped")
                        }
                        val line = source.readUtf8Line() ?: continue
                        val normalized = line.trimStart()
                        if (normalized.isBlank() || normalized.startsWith(":") || normalized.startsWith("event:")) continue
                        if (!normalized.startsWith("data:")) continue
                        val data = normalized.removePrefix("data:").trimStart()
                        if (data == "[DONE]") {
                            return StreamCompletionResult(
                                if (sawReplay) StreamCompletionStatus.Replay else StreamCompletionStatus.Complete,
                                if (sawReplay) "replay" else null
                            )
                        }
                        try {
                            val obj = gson.fromJson(data, JsonObject::class.java) ?: continue
                            if (obj.get("replay")?.takeIf { it.isJsonPrimitive }?.asBoolean == true) {
                                sawReplay = true
                            }
                        } catch (_: Exception) {
                            // Ignore malformed streaming chunks; completion is driven by [DONE].
                        }
                    }
                    StreamCompletionResult(StreamCompletionStatus.RetryableFailure, "stream_ended")
                }
            } catch (e: IOException) {
                if (networkRetry < STREAM_NETWORK_RETRY_MAX) {
                    Thread.sleep(350L * (networkRetry + 1))
                    runRequest(hasRetriedAuth = hasRetriedAuth, networkRetry = networkRetry + 1)
                } else {
                    Log.w(TAG, "streamChatToCompletion failed", e)
                    StreamCompletionResult(StreamCompletionStatus.RetryableFailure, "network")
                }
            }
        }

        return runRequest(hasRetriedAuth = false, networkRetry = 0)
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
        @SerializedName("client_msg_id") val client_msg_id: String? = null,
        @SerializedName("user") val user: String?,
        @SerializedName("user_images") val user_images: List<String>? = null,
        @SerializedName("assistant") val assistant: String?,
        @SerializedName("created_at") val created_at: Long? = null,
        @SerializedName("region") val region: String? = null,
        @SerializedName("region_source") val region_source: String? = null,
        @SerializedName("region_reliability") val region_reliability: String? = null
    )

    private data class SupportMessagesResponse(
        val messages: List<SupportMessage>? = null
    )

    private data class SupportMessageResponse(
        val message: SupportMessage? = null
    )

    private fun postToMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
