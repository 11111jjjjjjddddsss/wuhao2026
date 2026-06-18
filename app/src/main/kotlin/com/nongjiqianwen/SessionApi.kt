package com.nongjiqianwen

import android.os.Build
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
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
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
    private const val STREAM_ACTIVE_RETRY_MAX = 6
    private const val STREAM_ACTIVE_RETRY_BASE_DELAY_MS = 550L
    private const val CLIENT_LOG_THROTTLE_MS = 60_000L
    private const val APP_UPDATE_MAX_APK_DOWNLOAD_BYTES = 200L * 1024L * 1024L
    private const val APP_UPDATE_OFFICIAL_APK_HOST = "download.nongjiqiancha.cn"
    private const val APP_UPDATE_OFFICIAL_APK_PATH_PREFIX = "/android/releases/"
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
    private val streamClient = client.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val currentStreamCall = AtomicReference<Call?>(null)
    private val runtimeGeneration = AtomicInteger(0)
    private val sessionGeneration = AtomicInteger(-1)
    private val clientLogLastSentAtMs = ConcurrentHashMap<String, Long>()
    private val authInvalidListeners = CopyOnWriteArraySet<() -> Unit>()

    data class StreamOptions(
        val clientMsgId: String,
        val text: String,
        val images: List<String>,
        val sessionGeneration: Int? = null,
        val region: String? = null,
        val regionSource: String? = null,
        val regionReliability: String? = null,
        val todayAgriContextDay: String? = null
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

    private fun buildStreamRequestJson(options: StreamOptions): String {
        val streamBody = mutableMapOf<String, Any>(
            "client_msg_id" to options.clientMsgId,
            "text" to options.text,
            "images" to options.images
        )
        (options.sessionGeneration ?: currentSessionGenerationOrNull())?.let {
            streamBody["session_generation"] = it
        }
        putTrimmedStreamField(streamBody, "region", options.region, maxLength = 96)
        putTrimmedStreamField(streamBody, "region_source", options.regionSource, maxLength = 32)
        putTrimmedStreamField(streamBody, "region_reliability", options.regionReliability, maxLength = 32)
        putTrimmedStreamField(streamBody, "today_agri_context_day", options.todayAgriContextDay, maxLength = 16)
        return gson.toJson(streamBody)
    }

    private fun putTrimmedStreamField(
        target: MutableMap<String, Any>,
        key: String,
        value: String?,
        maxLength: Int
    ) {
        val trimmed = value?.trim()
            ?.take(maxLength)
            ?.takeIf { it.isNotBlank() && it != "未知" }
            ?: return
        target[key] = trimmed
    }

    data class EntitlementSnapshot(
        val tier: String? = null,
        @SerializedName("tier_expire_at") val tierExpireAt: Long? = null,
        @SerializedName("daily_remaining") val dailyRemaining: Int? = null,
        @SerializedName("topup_remaining") val topupRemaining: Int? = null,
        @SerializedName("topup_earliest_expire_at") val topupEarliestExpireAt: Long? = null,
        @SerializedName("upgrade_remaining") val upgradeRemaining: Int? = null,
        @SerializedName("membership_source") val membershipSource: String? = null,
        @SerializedName("gift_card_redeemed_at") val giftCardRedeemedAt: Long? = null
    )

    data class AuthSessionSnapshot(
        @SerializedName("user_id") val userId: String? = null,
        @SerializedName("phone_mask") val phoneMask: String? = null,
        val token: String? = null,
        @SerializedName("expires_at") val expiresAt: Long? = null
    )

    private data class ApiErrorBody(
        val error: String? = null,
        @SerializedName("retry_after_seconds") val retryAfterSeconds: Int? = null
    )

    data class TodayAgriCardResponse(
        val status: String? = null,
        val card: TodayAgriCard? = null
    )

    data class TodayAgriCardsResponse(
        val status: String? = null,
        val cards: List<TodayAgriCard>? = null
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
        val source: String? = null
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

    data class SupportSendResult(
        val message: SupportMessage? = null,
        val autoReply: SupportMessage? = null,
        val errorMessage: String? = null
    ) {
        val visibleMessages: List<SupportMessage>
            get() = listOfNotNull(message, autoReply)
    }

    data class AppUpdateInfo(
        val platform: String? = null,
        @SerializedName("current_version_code") val currentVersionCode: Int? = null,
        @SerializedName("current_version_name") val currentVersionName: String? = null,
        @SerializedName("latest_version_code") val latestVersionCode: Int? = null,
        @SerializedName("latest_version_name") val latestVersionName: String? = null,
        @SerializedName("has_update") val hasUpdate: Boolean? = null,
        @SerializedName("force_update") val forceUpdate: Boolean? = null,
        @SerializedName("apk_url") val apkUrl: String? = null,
        @SerializedName("apk_sha256") val apkSha256: String? = null,
        @SerializedName("release_notes") val releaseNotes: String? = null,
        @SerializedName("file_size_bytes") val fileSizeBytes: Long? = null
    ) {
        val usableUpdate: Boolean
            get() {
                val latestCode = latestVersionCode ?: return false
                val url = apkUrl?.trim().orEmpty()
                val sizeBytes = fileSizeBytes ?: return false
                return hasUpdate == true &&
                    latestCode > BuildConfig.VERSION_CODE &&
                    url.startsWith("https://") &&
                    isStableAppUpdateApkUrl(url) &&
                    normalizeAppUpdateSha256(apkSha256) != null &&
                    sizeBytes > 0L &&
                    sizeBytes <= APP_UPDATE_MAX_APK_DOWNLOAD_BYTES
            }
    }

    data class GiftCardRedeemResult(
        val ok: Boolean = false,
        val replay: Boolean = false,
        @SerializedName("card_id") val cardId: String? = null,
        @SerializedName("batch_id") val batchId: String? = null,
        val tier: String? = null,
        @SerializedName("applied_tier") val appliedTier: String? = null,
        @SerializedName("duration_days") val durationDays: Int? = null,
        @SerializedName("membership_expire_at") val membershipExpireAt: Long? = null,
        @SerializedName("redeemed_at") val redeemedAt: Long? = null
    )

    enum class ClearSessionHistoryResult {
        Success,
        ActiveStream,
        Failure
    }

    private fun TodayAgriCard?.isValidTodayAgriCard(): Boolean {
        val candidate = this ?: return false
        val items = candidate.items.orEmpty().take(3)
        return items.size == 3 &&
            items.all { item ->
                !item.title.isNullOrBlank() &&
                    !item.summary.isNullOrBlank()
            }
    }

    private fun baseUrl(): String {
        val url = BuildConfig.UPLOAD_BASE_URL.trim()
        return if (url.endsWith("/")) url.dropLast(1) else url
    }

    fun hasBackendConfigured(): Boolean = baseUrl().isNotEmpty()

    private fun normalizeAppUpdateSha256(raw: String?): String? {
        val value = raw
            ?.trim()
            ?.replace(":", "")
            ?.lowercase(Locale.US)
            .orEmpty()
        if (value.length != 64) return null
        return value.takeIf { hex ->
            hex.all { ch -> ch in '0'..'9' || ch in 'a'..'f' }
        }
    }

    internal fun isStableAppUpdateApkUrl(raw: String): Boolean {
        val parsed = raw.toHttpUrlOrNull() ?: return false
        if (parsed.scheme != "https") return false
        if (!parsed.host.equals(APP_UPDATE_OFFICIAL_APK_HOST, ignoreCase = true)) return false
        val encodedPath = parsed.encodedPath.lowercase(Locale.US)
        val decodedPath = decodeAppUpdateUrlGuardValue(parsed.encodedPath).lowercase(Locale.US)
        if (!encodedPath.startsWith(APP_UPDATE_OFFICIAL_APK_PATH_PREFIX)) return false
        if (!decodedPath.startsWith(APP_UPDATE_OFFICIAL_APK_PATH_PREFIX)) return false
        if (!encodedPath.endsWith(".apk")) return false
        if (!decodedPath.endsWith(".apk")) return false
        if (encodedPath.contains("..") || decodedPath.contains("..")) return false
        val lowerUrl = raw.lowercase(Locale.US)
        val decodedUrl = decodeAppUpdateUrlGuardValue(raw).lowercase(Locale.US)
        val internalMarkers = listOf("test-apks", "debug", "internal", "staging")
        if (internalMarkers.any { marker ->
                lowerUrl.contains(marker) ||
                    decodedUrl.contains(marker) ||
                    encodedPath.contains(marker) ||
                    decodedPath.contains(marker)
            }
        ) {
            return false
        }
        val signedQueryNames = setOf(
            "expires",
            "signature",
            "ossaccesskeyid",
            "security-token",
            "x-oss-expires",
            "x-oss-signature",
            "x-oss-credential",
            "x-oss-security-token"
        )
        return parsed.queryParameterNames.none { it.lowercase(Locale.US) in signedQueryNames }
    }

    private fun decodeAppUpdateUrlGuardValue(value: String): String =
        try {
            URLDecoder.decode(value, "UTF-8")
        } catch (_: IllegalArgumentException) {
            value
        }

    fun sendSmsCode(phoneNumber: String, onResult: (Boolean, String?) -> Unit) {
        val base = baseUrl()
        if (base.isEmpty()) {
            onResult(false, "服务暂时不可用，请稍后再试")
            return
        }
        val payload = gson.toJson(mapOf("phone_number" to phoneNumber.trim()))
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$base/api/auth/sms/send")
            .addHeader("Content-Type", "application/json")
            .post(payload)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                reportAuthClientLog(
                    level = "warn",
                    event = "auth.sms_send_failed",
                    message = "sms send network failure",
                    attrs = mapOf("reason" to "network")
                )
                mainHandler.post { onResult(false, "网络连接失败，请稍后再试") }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val statusCode = it.code
                    val body = it.body?.string().orEmpty()
                    val errorCode = if (it.isSuccessful) "" else parseApiErrorCode(body)
                    if (!it.isSuccessful) {
                        reportAuthClientLog(
                            level = if (statusCode >= 500) "error" else "warn",
                            event = "auth.sms_send_failed",
                            message = "sms send failed",
                            attrs = mapOf(
                                "http_status" to statusCode,
                                "error" to errorCode
                            )
                        )
                    }
                    mainHandler.post {
                        onResult(
                            it.isSuccessful,
                            when {
                                it.isSuccessful -> null
                                statusCode == 429 -> "操作太频繁，请稍后再试"
                                statusCode == 400 -> "请输入正确的手机号"
                                errorCode == "sms_send_not_configured" -> "验证码暂时发送失败，请稍后再试"
                                errorCode == "sms_provider_config_invalid" -> "验证码暂时发送失败，请稍后再试"
                                errorCode == "sms_provider_rate_limited" -> "验证码发送太频繁，请稍后再试"
                                else -> "验证码暂时发送失败，请稍后再试"
                            }
                        )
                    }
                }
            }
        })
    }

    fun loginWithSms(phoneNumber: String, verifyCode: String, onResult: (Boolean, String?) -> Unit) {
        loginWithAuthPayload(
            endpoint = "/api/auth/sms/login",
            payload = mapOf(
                "phone_number" to phoneNumber.trim(),
                "verify_code" to verifyCode.trim(),
                "legacy_user_id" to IdManager.getLegacyUserId(),
                "device_id" to IdManager.getDeviceId()
            ),
            onResult = { ok, error ->
                if (!ok) {
                    reportAuthClientLog(
                        level = "warn",
                        event = "auth.sms_login_failed",
                        message = "sms login failed",
                        attrs = mapOf("reason" to "verify_or_network")
                    )
                }
                onResult(ok, error)
            }
        )
    }

    fun logoutCurrentSession(onResult: (Boolean) -> Unit) {
        val base = baseUrl()
        val token = authTokenSync()
        if (base.isEmpty() || token.isNullOrBlank()) {
            clearLocalAuthRuntimeSession(notifyListeners = true)
            mainHandler.post { onResult(true) }
            return
        }
        val request = applyIdentityHeaders(
            Request.Builder()
                .url("$base/api/auth/logout")
                .addHeader("Authorization", "Bearer $token")
                .post(ByteArray(0).toRequestBody(null))
        ).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                reportClientLog(
                    level = "warn",
                    event = "auth.logout_failed",
                    message = "Auth logout failed",
                    attrs = mapOf(
                        "reason" to "network",
                        "exception" to e.javaClass.simpleName
                    )
                )
                mainHandler.post { onResult(false) }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val ok = it.isSuccessful || it.code == 401
                    if (!ok) {
                        reportClientLog(
                            level = if (it.code >= 500) "error" else "warn",
                            event = "auth.logout_failed",
                            message = "Auth logout failed",
                            attrs = mapOf("http_status" to it.code)
                        )
                    }
                    if (ok) {
                        clearLocalAuthRuntimeSession(notifyListeners = true)
                    }
                    mainHandler.post { onResult(ok) }
                }
            }
        })
    }

    fun requestAccountDeletion(onResult: (Boolean) -> Unit) {
        val base = baseUrl()
        if (base.isEmpty() || !IdManager.isLoggedIn()) {
            postToMain { onResult(false) }
            return
        }
        val requestBody = gson.toJson(
            mapOf(
                "reason" to "app_request",
                "message" to "user_requested_from_android"
            )
        ).toRequestBody("application/json".toMediaType())
        enqueueWithRetry401(
            requestFactory = { token ->
                val builder = applyIdentityHeaders(
                    Request.Builder()
                        .url("$base/api/account/deletion-requests")
                        .addHeader("Content-Type", "application/json")
                        .post(requestBody)
                )
                if (!token.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $token")
                builder
            },
            onResult = { response ->
                response.use {
                    val ok = it.isSuccessful
                    if (!ok) {
                        reportClientLog(
                            level = if (it.code >= 500) "error" else "warn",
                            event = "account.deletion_request_failed",
                            message = "Account deletion request failed",
                            attrs = mapOf("http_status" to it.code)
                        )
                    }
                    if (ok) {
                        clearLocalAuthRuntimeSession(notifyListeners = true)
                    }
                    postToMain { onResult(ok) }
                }
            },
            onFailure = { error ->
                reportClientLog(
                    level = "warn",
                    event = "account.deletion_request_failed",
                    message = "Account deletion request failed",
                    attrs = mapOf(
                        "reason" to "network",
                        "exception" to error.javaClass.simpleName
                    )
                )
                postToMain { onResult(false) }
            }
        )
    }

    fun cancelCurrentStream() {
        currentStreamCall.getAndSet(null)?.cancel()
    }

    fun resetUiRuntimeForCleanState() {
        runtimeGeneration.incrementAndGet()
        currentStreamCall.getAndSet(null)?.cancel()
    }

    fun addAuthInvalidListener(listener: () -> Unit): () -> Unit {
        authInvalidListeners.add(listener)
        return { authInvalidListeners.remove(listener) }
    }

    private fun notifyAuthInvalid() {
        clearLocalAuthRuntimeSession(notifyListeners = true)
    }

    private fun clearLocalAuthRuntimeSession(notifyListeners: Boolean = false) {
        val previousAuthUserId = IdManager.getAuthenticatedUserId()
        val appContext = IdManager.applicationContextOrNull()
        if (!previousAuthUserId.isNullOrBlank() && appContext != null) {
            PendingChatSendWorkScheduler.cancelAllForAuthUserId(appContext, previousAuthUserId)
        }
        IdManager.clearAuthSession()
        sessionGeneration.set(-1)
        runtimeGeneration.incrementAndGet()
        currentStreamCall.getAndSet(null)?.cancel()
        if (notifyListeners) {
            postToMain {
                authInvalidListeners.forEach { listener -> listener() }
            }
        }
    }

    fun currentSessionGenerationOrNull(): Int? {
        val inMemory = sessionGeneration.get()
        if (inMemory >= 0) return inMemory
        val persisted = IdManager.getSessionGeneration()
        if (persisted >= 0) {
            sessionGeneration.compareAndSet(-1, persisted)
            return persisted
        }
        return null
    }

    private fun updateSessionGeneration(generation: Int) {
        if (generation < 0) return
        sessionGeneration.set(generation)
        IdManager.setSessionGeneration(generation)
    }

    fun reportClientLog(
        level: String,
        event: String,
        message: String,
        attrs: Map<String, Any?> = emptyMap()
    ) {
        reportClientLogToEndpoint(
            endpoint = "/api/app/logs",
            authenticated = true,
            level = level,
            event = event,
            message = message,
            attrs = attrs
        )
    }

    fun reportAuthClientLog(
        level: String,
        event: String,
        message: String,
        attrs: Map<String, Any?> = emptyMap()
    ) {
        val normalizedEvent = normalizeClientLogIdentifier(event, maxLength = 96)
        if (!normalizedEvent.startsWith("auth.")) return
        reportClientLogToEndpoint(
            endpoint = "/api/app/logs/preauth",
            authenticated = false,
            level = level,
            event = normalizedEvent,
            message = message,
            attrs = attrs
        )
    }

    private fun reportClientLogToEndpoint(
        endpoint: String,
        authenticated: Boolean,
        level: String,
        event: String,
        message: String,
        attrs: Map<String, Any?> = emptyMap()
    ) {
        val base = baseUrl()
        if (base.isEmpty()) return
        val normalizedLevel = when (level.trim().lowercase()) {
            "info", "warn", "error" -> level.trim().lowercase()
            else -> "warn"
        }
        val normalizedEvent = normalizeClientLogIdentifier(event, maxLength = 96)
        if (normalizedEvent.isEmpty()) return
        if (!isCrashClientLogEvent(normalizedEvent) && !shouldSendClientLog(normalizedEvent)) return
        val payload = mapOf(
            "level" to normalizedLevel,
            "event" to normalizedEvent,
            "message" to message.trim().take(255).ifEmpty { normalizedEvent },
            "attrs" to sanitizeClientLogAttrs(attrs),
            "platform" to "android",
            "build_type" to BuildConfig.BUILD_TYPE,
            "app_version_code" to BuildConfig.VERSION_CODE,
            "app_version_name" to BuildConfig.VERSION_NAME,
            "os_version" to "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            "device_model" to listOf(Build.MANUFACTURER, Build.MODEL)
                .joinToString(" ")
                .trim()
                .take(128),
            "client_time_ms" to System.currentTimeMillis()
        )
        val requestBody = gson.toJson(payload).toRequestBody("application/json".toMediaType())
        if (!authenticated) {
            val request = Request.Builder()
                .url("$base$endpoint")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "preauth client log upload failed: ${e.javaClass.simpleName}")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.close()
                }
            })
            return
        }
        authedRequest(
            builderFactory = { token ->
                val builder = applyIdentityHeaders(
                    Request.Builder()
                        .url("$base$endpoint")
                        .addHeader("Content-Type", "application/json")
                        .post(requestBody)
                )
                if (!token.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $token")
                builder
            },
            onReady = { request ->
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "client log upload failed: ${e.javaClass.simpleName}")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.close()
                    }
                })
            }
        )
    }

    private fun shouldSendClientLog(event: String): Boolean {
        val now = System.currentTimeMillis()
        val previous = clientLogLastSentAtMs[event]
        if (previous != null && now - previous < CLIENT_LOG_THROTTLE_MS) return false
        clientLogLastSentAtMs[event] = now
        return true
    }

    private fun isCrashClientLogEvent(event: String): Boolean =
        event == "app.crash" || event == "auth.app_crash"

    private fun normalizeClientLogIdentifier(raw: String, maxLength: Int): String =
        raw.trim()
            .lowercase()
            .filter { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' || it == ':' }
            .take(maxLength)

    private fun sanitizeClientLogAttrs(attrs: Map<String, Any?>): Map<String, Any?> {
        if (attrs.isEmpty()) return emptyMap()
        return attrs.entries
            .asSequence()
            .mapNotNull { (key, value) ->
                val normalizedKey = normalizeClientLogIdentifier(key, maxLength = 64)
                if (normalizedKey.isEmpty()) return@mapNotNull null
                if (isCrashDiagnosticClientLogAttrKey(normalizedKey)) {
                    val sanitizedCrashValue = sanitizeCrashDiagnosticClientLogValue(value) ?: return@mapNotNull null
                    return@mapNotNull normalizedKey to sanitizedCrashValue
                }
                if (isSensitiveClientLogAttrKey(normalizedKey)) return@mapNotNull null
                val sanitizedValue = sanitizeClientLogValue(value) ?: return@mapNotNull null
                normalizedKey to sanitizedValue
            }
            .take(20)
            .toMap()
    }

    private fun isCrashDiagnosticClientLogAttrKey(key: String): Boolean =
        key == "exception" ||
            key == "cause" ||
            key == "top_class" ||
            key == "top_method" ||
            key == "top_line" ||
            key == "stack_top" ||
            key == "stack_next" ||
            key == "stack_third"

    private fun sanitizeCrashDiagnosticClientLogValue(value: Any?): String? {
        val raw = value?.toString()?.trim()?.take(160).orEmpty()
        if (raw.isEmpty()) return null
        val safe = buildString(raw.length) {
            raw.forEach { char ->
                if (char.isLetterOrDigit() ||
                    char == '.' ||
                    char == '_' ||
                    char == '$' ||
                    char == '#' ||
                    char == ':' ||
                    char == '-' ||
                    char == ' '
                ) {
                    append(char)
                }
            }
        }.trim()
        return safe.takeUnless {
            it.isEmpty() ||
                it.contains("http://", ignoreCase = true) ||
                it.contains("https://", ignoreCase = true) ||
                it.length < raw.length / 2
        }
    }

    private fun isSensitiveClientLogAttrKey(key: String): Boolean {
        val normalized = key.lowercase()
        return normalized.contains("phone") ||
            normalized == "token" ||
            normalized == "key" ||
            normalized == "url" ||
            normalized == "uri" ||
            normalized == "body" ||
            normalized == "message" ||
            normalized == "content" ||
            normalized.contains("token") ||
            normalized.contains("password") ||
            normalized.contains("secret") ||
            normalized.contains("authorization") ||
            normalized.contains("api_key") ||
            normalized.contains("access_key") ||
            normalized.contains("model_key") ||
            normalized.endsWith("_url") ||
            normalized.endsWith("-url") ||
            normalized.endsWith(".url") ||
            normalized.endsWith(":url") ||
            normalized.endsWith("urls") ||
            normalized.endsWith("_uri") ||
            normalized.endsWith("-uri") ||
            normalized.endsWith(".uri") ||
            normalized.endsWith(":uri") ||
            normalized.endsWith("uris") ||
            normalized.endsWith("_body") ||
            normalized.endsWith("-body") ||
            normalized.endsWith(".body") ||
            normalized.endsWith(":body") ||
            normalized.endsWith("_message") ||
            normalized.endsWith("-message") ||
            normalized.endsWith(".message") ||
            normalized.endsWith(":message") ||
            normalized.endsWith("_content") ||
            normalized.endsWith("-content") ||
            normalized.endsWith(".content") ||
            normalized.endsWith(":content")
    }

    private fun sanitizeClientLogValue(value: Any?): Any? =
        when (value) {
            null -> null
            is Boolean -> value
            is Number -> {
                val text = value.toString()
                if (containsSensitiveClientLogText(text)) null else value
            }
            is String -> value.trim()
                .take(160)
                .takeUnless { it.isEmpty() || containsSensitiveClientLogText(it) }
            else -> value.toString()
                .trim()
                .take(160)
                .takeUnless { it.isEmpty() || containsSensitiveClientLogText(it) }
        }

    private fun containsSensitiveClientLogText(value: String): Boolean {
        val normalized = value.trim().lowercase()
        if (normalized.isEmpty()) return false
        val markers = listOf(
            "http://",
            "https://",
            "bearer ",
            "authorization",
            "token",
            "api_key",
            "access_key",
            "accesskey",
            "secret",
            "password",
        )
        if (markers.any { normalized.contains(it) }) return true
        var consecutiveDigits = 0
        for (char in normalized) {
            if (char.isDigit()) {
                consecutiveDigits += 1
                if (consecutiveDigits >= 11) return true
            } else {
                consecutiveDigits = 0
            }
        }
        return false
    }

    private fun ensureAuthToken(onResult: (String?) -> Unit) {
        onResult(IdManager.getAuthToken())
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
        IdManager.getAuthToken()

    private fun loginWithAuthPayload(
        endpoint: String,
        payload: Map<String, String>,
        shouldCommitSession: () -> Boolean = { true },
        onResult: (Boolean, String?) -> Unit
    ) {
        val base = baseUrl()
        if (base.isEmpty()) {
            onResult(false, "服务暂时不可用，请稍后再试")
            return
        }
        val requestBody = gson.toJson(payload).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(base + endpoint)
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                reportAuthClientLog(
                    level = "warn",
                    event = "auth.login_network_failed",
                    message = "auth login network failure",
                    attrs = mapOf(
                        "endpoint" to endpoint.substringAfterLast('/').take(48),
                        "reason" to e.javaClass.simpleName
                    )
                )
                mainHandler.post { onResult(false, "网络连接失败，请稍后再试") }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val statusCode = it.code
                    val body = it.body?.string().orEmpty()
                    val session = if (it.isSuccessful) {
                        runCatching { gson.fromJson(body, AuthSessionSnapshot::class.java) }.getOrNull()
                    } else {
                        null
                    }
                    val accountUserId = session?.userId.orEmpty().trim()
                    val sessionToken = session?.token.orEmpty().trim()
                    if (accountUserId.isNotBlank() && sessionToken.isNotBlank()) {
                        if (!accountUserId.startsWith("acct_")) {
                            reportAuthClientLog(
                                level = "error",
                                event = if (endpoint.contains("/sms/")) "auth.sms_login_failed" else "auth.login_failed",
                                message = "auth login returned non-account user id",
                                attrs = mapOf(
                                    "endpoint" to endpoint.substringAfterLast('/').take(48),
                                    "http_status" to statusCode,
                                    "error" to "non_account_user_id"
                                )
                            )
                            mainHandler.post { onResult(false, "登录状态异常，请稍后再试") }
                            return
                        }
                        if (!shouldCommitSession()) {
                            mainHandler.post { onResult(false, "登录已取消，请重新尝试") }
                            return
                        }
                        IdManager.saveAuthSession(
                            userId = accountUserId,
                            token = sessionToken,
                            expiresAt = session?.expiresAt ?: 0L,
                            phoneMask = session?.phoneMask
                        )
                        reportAuthClientLog(
                            level = "info",
                            event = if (endpoint.contains("/sms/")) "auth.sms_login_success" else "auth.login_success",
                            message = "auth login success",
                            attrs = mapOf(
                                "endpoint" to endpoint.substringAfterLast('/').take(48),
                                "http_status" to statusCode,
                                "account_id_kind" to "acct"
                            )
                        )
                        mainHandler.post { onResult(true, null) }
                    } else {
                        val errorCode = parseApiErrorCode(body)
                        reportAuthClientLog(
                            level = if (statusCode >= 500) "error" else "warn",
                            event = if (endpoint.contains("/sms/")) "auth.sms_login_failed" else "auth.login_failed",
                            message = "auth login failed",
                            attrs = mapOf(
                                "endpoint" to endpoint.substringAfterLast('/').take(48),
                                "http_status" to statusCode,
                                "error" to errorCode
                            )
                        )
                        mainHandler.post {
                            onResult(
                                false,
                                authLoginErrorMessage(statusCode, errorCode, endpoint)
                            )
                        }
                    }
                }
            }
        })
    }

    private fun parseApiErrorCode(body: String): String =
        runCatching {
            gson.fromJson(body, ApiErrorBody::class.java)
                ?.error
                .orEmpty()
                .trim()
                .take(96)
        }.getOrDefault("")

    private fun authLoginErrorMessage(statusCode: Int, errorCode: String, endpoint: String): String {
        val isSms = endpoint.contains("/sms/")
        return when {
            statusCode == 429 -> "操作太频繁，请稍后再试"
            statusCode == 400 && errorCode == "invalid_phone" -> "请输入正确的手机号"
            statusCode == 400 && errorCode == "invalid_code" -> "请填写验证码"
            statusCode == 401 && isSms -> "验证码不正确或已过期"
            statusCode == 401 -> "登录校验失败，请稍后再试"
            statusCode == 413 -> "请求内容过大，请重新尝试"
            statusCode >= 500 && isSms -> "验证码服务暂时不可用，请稍后再试"
            statusCode >= 500 -> "登录服务暂时不可用，请稍后再试"
            isSms -> "短信登录失败，请稍后再试"
            else -> "登录暂时失败，请稍后再试"
        }
    }

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
                                override fun onResponse(call: Call, response: Response) {
                                    if (response.code == 401) {
                                        notifyAuthInvalid()
                                    }
                                    onResult(response)
                                }
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
                        reportClientLog(
                            level = if (it.code >= 500) "error" else "warn",
                            event = "entitlement.fetch_failed",
                            message = "Entitlement fetch failed",
                            attrs = mapOf("http_status" to it.code)
                        )
                        onResult(null)
                        return@use
                    }
                    val body = it.body?.string()
                    if (body.isNullOrBlank()) {
                        reportClientLog(
                            level = "warn",
                            event = "entitlement.fetch_failed",
                            message = "Entitlement fetch failed",
                            attrs = mapOf("reason" to "empty_body")
                        )
                        onResult(null)
                        return@use
                    }
                    try {
                        onResult(gson.fromJson(body, EntitlementSnapshot::class.java))
                    } catch (e: Exception) {
                        Log.e(TAG, "parse entitlement", e)
                        reportClientLog(
                            level = "warn",
                            event = "entitlement.fetch_failed",
                            message = "Entitlement fetch failed",
                            attrs = mapOf(
                                "reason" to "parse",
                                "exception" to e.javaClass.simpleName
                            )
                        )
                        onResult(null)
                    }
                }
            },
            onFailure = { error ->
                reportClientLog(
                    level = "warn",
                    event = "entitlement.fetch_failed",
                    message = "Entitlement fetch failed",
                    attrs = mapOf(
                        "reason" to "network",
                        "exception" to error.javaClass.simpleName
                    )
                )
                onResult(null)
            }
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
                    if (isRuntimeStale()) {
                        onResult(null)
                        return@use
                    }
                    if (!it.isSuccessful) {
                        reportClientLog(
                            level = if (it.code >= 500) "error" else "warn",
                            event = "today_agri.fetch_failed",
                            message = "Today agri card fetch failed",
                            attrs = mapOf("http_status" to it.code)
                        )
                        onResult(null)
                        return@use
                    }
                    val body = it.body?.string()
                    if (body.isNullOrBlank()) {
                        reportClientLog(
                            level = "warn",
                            event = "today_agri.fetch_failed",
                            message = "Today agri card fetch failed",
                            attrs = mapOf("reason" to "empty_body")
                        )
                        onResult(null)
                        return@use
                    }
                    try {
                        val parsed = gson.fromJson(body, TodayAgriCardResponse::class.java)
                        val validCard = parsed
                            ?.takeIf { it.status == "ready" }
                            ?.card
                            ?.takeIf { it.isValidTodayAgriCard() }
                        if (parsed?.status == "ready" && validCard == null) {
                            reportClientLog(
                                level = "warn",
                                event = "today_agri.fetch_failed",
                                message = "Today agri card fetch failed",
                                attrs = mapOf("reason" to "invalid_card")
                            )
                        }
                        onResult(validCard)
                    } catch (e: Exception) {
                        Log.e(TAG, "parse today agri card", e)
                        reportClientLog(
                            level = "warn",
                            event = "today_agri.fetch_failed",
                            message = "Today agri card fetch failed",
                            attrs = mapOf(
                                "reason" to "parse",
                                "exception" to e.javaClass.simpleName
                            )
                        )
                        onResult(null)
                    }
                }
            },
            onFailure = { error ->
                reportClientLog(
                    level = "warn",
                    event = "today_agri.fetch_failed",
                    message = "Today agri card fetch failed",
                    attrs = mapOf(
                        "reason" to "network",
                        "exception" to error.javaClass.simpleName
                    )
                )
                onResult(null)
            }
        )
    }

    fun getRecentTodayAgriCards(onResult: (List<TodayAgriCard>?) -> Unit) {
        val base = baseUrl()
        if (base.isEmpty()) {
            postToMain { onResult(null) }
            return
        }
        enqueueWithRetry401(
            requestFactory = { token ->
                val builder = applyIdentityHeaders(Request.Builder().url("$base/api/today-agri-cards").get())
                if (!token.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $token")
                builder
            },
            onResult = { response ->
                response.use {
                    if (!it.isSuccessful) {
                        reportClientLog(
                            level = if (it.code >= 500) "error" else "warn",
                            event = "today_agri.recent_fetch_failed",
                            message = "Recent today agri cards fetch failed",
                            attrs = mapOf("http_status" to it.code)
                        )
                        postToMain { onResult(null) }
                        return@use
                    }
                    val body = it.body?.string()
                    if (body.isNullOrBlank()) {
                        postToMain { onResult(emptyList()) }
                        return@use
                    }
                    try {
                        val parsed = gson.fromJson(body, TodayAgriCardsResponse::class.java)
                        val validCards = parsed
                            ?.cards
                            .orEmpty()
                            .filter { card -> card.isValidTodayAgriCard() }
                            .distinctBy { card -> "${card.dateCn.orEmpty()}|${card.title.orEmpty()}" }
                            .sortedWith(
                                compareByDescending<TodayAgriCard> { it.generatedAt ?: 0L }
                                    .thenByDescending { it.dateCn.orEmpty() }
                            )
                            .take(30)
                        postToMain { onResult(validCards) }
                    } catch (e: Exception) {
                        Log.e(TAG, "parse recent today agri cards", e)
                        reportClientLog(
                            level = "warn",
                            event = "today_agri.recent_fetch_failed",
                            message = "Recent today agri cards fetch failed",
                            attrs = mapOf(
                                "reason" to "parse",
                                "exception" to e.javaClass.simpleName
                            )
                        )
                        postToMain { onResult(null) }
                    }
                }
            },
            onFailure = { error ->
                reportClientLog(
                    level = "warn",
                    event = "today_agri.recent_fetch_failed",
                    message = "Recent today agri cards fetch failed",
                    attrs = mapOf(
                        "reason" to "network",
                        "exception" to error.javaClass.simpleName
                    )
                )
                postToMain { onResult(null) }
            }
        )
    }

    fun saveTodayAgriItem(
        dayCn: String,
        anchorClientMsgId: String,
        sessionGeneration: Int? = currentSessionGenerationOrNull(),
        onResult: ((Boolean) -> Unit)? = null
    ) {
        val base = baseUrl()
        val normalizedDay = dayCn.filter { it.isDigit() }.takeIf { it.length == 8 }.orEmpty()
        val anchor = anchorClientMsgId.trim().take(128)
        if (base.isEmpty() || normalizedDay.isBlank() || anchor.isBlank()) {
            postToMain { onResult?.invoke(false) }
            return
        }
        val body = gson.toJson(
            buildMap<String, Any> {
                put("day_cn", normalizedDay)
                put("anchor_client_msg_id", anchor)
                sessionGeneration?.let { put("session_generation", it) }
            }
        )
        enqueueWithRetry401(
            requestFactory = { token ->
                val builder = applyIdentityHeaders(
                    Request.Builder()
                        .url("$base/api/today-agri-item")
                        .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                )
                if (!token.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $token")
                builder
            },
            onResult = { response ->
                response.use {
                    val ok = it.isSuccessful
                    if (!ok) {
                        reportClientLog(
                            level = if (it.code >= 500) "error" else "warn",
                            event = "today_agri.item_save_failed",
                            message = "Today agri main item save failed",
                            attrs = mapOf("http_status" to it.code)
                        )
                    }
                    postToMain { onResult?.invoke(ok) }
                }
            },
            onFailure = { error ->
                reportClientLog(
                    level = "warn",
                    event = "today_agri.item_save_failed",
                    message = "Today agri main item save failed",
                    attrs = mapOf(
                        "reason" to "network",
                        "exception" to error.javaClass.simpleName
                    )
                )
                postToMain { onResult?.invoke(false) }
            }
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
                        reportClientLog(
                            level = if (it.code >= 500) "error" else "warn",
                            event = "support.summary_fetch_failed",
                            message = "Support summary fetch failed",
                            attrs = mapOf("http_status" to it.code)
                        )
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
                        reportClientLog(
                            level = "warn",
                            event = "support.summary_fetch_failed",
                            message = "Support summary fetch failed",
                            attrs = mapOf(
                                "reason" to "parse",
                                "exception" to e.javaClass.simpleName
                            )
                        )
                        null
                    }
                    postToMain { onResult(parsed) }
                }
            },
            onFailure = { error ->
                reportClientLog(
                    level = "warn",
                    event = "support.summary_fetch_failed",
                    message = "Support summary fetch failed",
                    attrs = mapOf(
                        "reason" to "network",
                        "exception" to error.javaClass.simpleName
                    )
                )
                postToMain { onResult(null) }
            }
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
                        reportClientLog(
                            level = if (it.code >= 500) "error" else "warn",
                            event = "support.messages_fetch_failed",
                            message = "Support messages fetch failed",
                            attrs = mapOf("http_status" to it.code)
                        )
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
                        reportClientLog(
                            level = "warn",
                            event = "support.messages_fetch_failed",
                            message = "Support messages fetch failed",
                            attrs = mapOf(
                                "reason" to "parse",
                                "exception" to e.javaClass.simpleName
                            )
                        )
                        null
                    }
                    postToMain { onResult(parsed) }
                }
            },
            onFailure = { error ->
                reportClientLog(
                    level = "warn",
                    event = "support.messages_fetch_failed",
                    message = "Support messages fetch failed",
                    attrs = mapOf(
                        "reason" to "network",
                        "exception" to error.javaClass.simpleName
                    )
                )
                postToMain { onResult(null) }
            }
        )
    }

    fun sendSupportMessage(
        body: String,
        images: List<String> = emptyList(),
        onResult: (SupportSendResult?) -> Unit
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
                        val bodyText = it.body?.string().orEmpty()
                        val errorCode = parseApiErrorCode(bodyText)
                        reportClientLog(
                            level = "warn",
                            event = "support.send_failed",
                            message = "Support message send failed",
                            attrs = mapOf(
                                "http_status" to it.code,
                                "image_count" to images.size,
                                "body_length" to body.length
                            )
                        )
                        postToMain { onResult(SupportSendResult(errorMessage = supportSendErrorMessage(it.code, errorCode))) }
                        return@use
                    }
                    val bodyText = it.body?.string()
                    if (bodyText.isNullOrBlank()) {
                        postToMain { onResult(null) }
                        return@use
                    }
                    val parsed = try {
                        gson.fromJson(bodyText, SupportMessageResponse::class.java)?.toResult()
                    } catch (e: Exception) {
                        Log.e(TAG, "parse sent support message", e)
                        null
                    }
                    postToMain { onResult(parsed) }
                }
            },
            onFailure = { error ->
                reportClientLog(
                    level = "warn",
                    event = "support.send_failed",
                    message = "Support message send failed",
                    attrs = mapOf(
                        "reason" to "network",
                        "exception" to error.javaClass.simpleName,
                        "image_count" to images.size,
                        "body_length" to body.length
                    )
                )
                postToMain { onResult(null) }
            }
        )
    }

    fun markSupportRead(lastSeenMessageId: Long = 0L, onResult: (Boolean) -> Unit = {}) {
        val base = baseUrl()
        if (base.isEmpty()) {
            postToMain { onResult(false) }
            return
        }
        val payload = JsonObject().apply {
            if (lastSeenMessageId > 0L) {
                addProperty("last_seen_message_id", lastSeenMessageId)
            }
        }
        val requestBody = gson.toJson(payload).toRequestBody("application/json".toMediaType())
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
                    if (!it.isSuccessful) {
                        reportClientLog(
                            level = if (it.code >= 500) "error" else "warn",
                            event = "support.mark_read_failed",
                            message = "Support mark read failed",
                            attrs = mapOf("http_status" to it.code)
                        )
                    }
                    postToMain { onResult(it.isSuccessful) }
                }
            },
            onFailure = { error ->
                reportClientLog(
                    level = "warn",
                    event = "support.mark_read_failed",
                    message = "Support mark read failed",
                    attrs = mapOf(
                        "reason" to "network",
                        "exception" to error.javaClass.simpleName
                    )
                )
                postToMain { onResult(false) }
            }
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
                    if (it.isSuccessful) {
                        val body = it.body?.string().orEmpty()
                        runCatching {
                            gson.fromJson(body, SessionClearResponse::class.java)?.sessionGeneration
                        }.getOrNull()?.let(::updateSessionGeneration)
                    }
                    val result = when {
                        it.isSuccessful -> ClearSessionHistoryResult.Success
                        it.code == 409 -> ClearSessionHistoryResult.ActiveStream
                        else -> ClearSessionHistoryResult.Failure
                    }
                    if (result != ClearSessionHistoryResult.Success) {
                        reportClientLog(
                            level = if (it.code >= 500) "error" else "warn",
                            event = "session.clear_failed",
                            message = "Session clear failed",
                            attrs = mapOf(
                                "http_status" to it.code,
                                "reason" to if (it.code == 409) "active_stream" else "http"
                            )
                        )
                    }
                    postToMain { onResult(result) }
                }
            },
            onFailure = { error ->
                reportClientLog(
                    level = "warn",
                    event = "session.clear_failed",
                    message = "Session clear failed",
                    attrs = mapOf(
                        "reason" to "network",
                        "exception" to error.javaClass.simpleName
                    )
                )
                postToMain { onResult(ClearSessionHistoryResult.Failure) }
            }
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
                        reportClientLog(
                            level = "warn",
                            event = "app_update.check_failed",
                            message = "App update check failed",
                            attrs = mapOf("http_status" to it.code)
                        )
                        postToMain { onResult(null) }
                        return@use
                    }
                    val body = it.body?.string()
                    if (body.isNullOrBlank()) {
                        reportClientLog(
                            level = "warn",
                            event = "app_update.check_failed",
                            message = "App update check failed",
                            attrs = mapOf(
                                "http_status" to it.code,
                                "reason" to "empty_body"
                            )
                        )
                        postToMain { onResult(null) }
                        return@use
                    }
                    val parsed = try {
                        gson.fromJson(body, AppUpdateInfo::class.java)
                    } catch (e: Exception) {
                        Log.e(TAG, "parse app update", e)
                        reportClientLog(
                            level = "warn",
                            event = "app_update.check_failed",
                            message = "App update check failed",
                            attrs = mapOf("exception" to e.javaClass.simpleName)
                        )
                        null
                    }
                    postToMain { onResult(parsed) }
                }
            },
            onFailure = { error ->
                reportClientLog(
                    level = "warn",
                    event = "app_update.check_failed",
                    message = "App update check failed",
                    attrs = mapOf(
                        "reason" to "network",
                        "exception" to error.javaClass.simpleName
                    )
                )
                postToMain { onResult(null) }
            }
        )
    }

    fun redeemGiftCard(code: String, onResult: (GiftCardRedeemResult?, String?) -> Unit) {
        val base = baseUrl()
        val trimmedCode = code.trim()
        if (base.isEmpty()) {
            postToMain { onResult(null, "服务暂时不可用，请稍后再试") }
            return
        }
        if (trimmedCode.isEmpty()) {
            postToMain { onResult(null, "请输入礼品卡码") }
            return
        }
        if (!IdManager.isLoggedIn()) {
            postToMain { onResult(null, "请先登录后兑换") }
            return
        }
        val requestBody = gson.toJson(mapOf("code" to trimmedCode))
            .toRequestBody("application/json".toMediaType())
        enqueueWithRetry401(
            requestFactory = { token ->
                val builder = applyIdentityHeaders(
                    Request.Builder()
                        .url("$base/api/gift-cards/redeem")
                        .addHeader("Content-Type", "application/json")
                        .post(requestBody)
                )
                if (!token.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $token")
                builder
            },
            onResult = { response ->
                response.use {
                    val body = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        reportClientLog(
                            level = "warn",
                            event = "gift_card.redeem_failed",
                            message = "Gift card redeem failed",
                            attrs = mapOf("http_status" to it.code)
                        )
                        postToMain { onResult(null, giftCardRedeemErrorMessage(it.code, body)) }
                        return@use
                    }
                    val parsed = try {
                        gson.fromJson(body, GiftCardRedeemResult::class.java)
                    } catch (e: Exception) {
                        Log.e(TAG, "parse gift card redeem", e)
                        null
                    }
                    if (parsed?.ok == true) {
                        postToMain { onResult(parsed, null) }
                    } else {
                        postToMain { onResult(null, "兑换失败，请稍后再试") }
                    }
                }
            },
            onFailure = { error ->
                reportClientLog(
                    level = "warn",
                    event = "gift_card.redeem_failed",
                    message = "Gift card redeem failed",
                    attrs = mapOf(
                        "reason" to "network",
                        "exception" to error.javaClass.simpleName
                    )
                )
                postToMain { onResult(null, "网络连接失败，请稍后再试") }
            }
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
            if (isRuntimeStale()) {
                onResult(null)
                return
            }
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
                            onResult(null)
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
                            val snapshotGeneration = json.session_generation ?: 0
                            updateSessionGeneration(snapshotGeneration)
                            val memoryDocument = json.memory_document ?: json.b_summary ?: ""
                            val roundTotal = json.round_total ?: full.size.coerceAtLeast(forUi.size)
                            val todayAgriItems = json.today_agri_items
                                .orEmpty()
                                .mapNotNull { item ->
                                    val day = item.day_cn
                                        ?.filter { ch -> ch.isDigit() }
                                        ?.takeIf { it.length == 8 }
                                        ?: return@mapNotNull null
                                    val anchorId = item.anchor_client_msg_id
                                        ?.trim()
                                        ?.takeIf { it.isNotBlank() }
                                        ?: return@mapNotNull null
                                    val card = item.card
                                        ?.takeIf { it.isValidTodayAgriCard() }
                                        ?: return@mapNotNull null
                                    TodayAgriMainItem(
                                        day_cn = day,
                                        anchor_client_msg_id = anchorId,
                                        card = card,
                                        created_at = item.created_at ?: 0L,
                                        updated_at = item.updated_at ?: 0L
                                    )
                                }
                            onResult(
                                SessionSnapshot(
                                    memory_document = memoryDocument,
                                    b_summary = memoryDocument,
                                    a_rounds_full = full,
                                    a_rounds_for_ui = forUi,
                                    round_total = roundTotal,
                                    session_generation = snapshotGeneration,
                                    today_agri_items = todayAgriItems
                                )
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "parse snapshot", e)
                            reportClientLog(
                                level = "warn",
                                event = "session.snapshot_parse_failed",
                                message = "Session snapshot parse failed",
                                attrs = mapOf("exception" to e.javaClass.simpleName)
                            )
                            onResult(null)
                        }
                    }
                },
                onFailure = { error ->
                    if (isRuntimeStale()) {
                        onResult(null)
                        return@enqueueWithRetry401
                    }
                    if (networkRetry < SNAPSHOT_NETWORK_RETRY_MAX) {
                        val retryDelayMs = 300L * (networkRetry + 1)
                        mainHandler.postDelayed({
                            if (isRuntimeStale()) {
                                onResult(null)
                                return@postDelayed
                            }
                            attempt(networkRetry + 1)
                        }, retryDelayMs)
                    } else {
                        Log.w(TAG, "getSnapshot failed", error)
                        reportClientLog(
                            level = "warn",
                            event = "session.snapshot_failed",
                            message = "Session snapshot failed",
                            attrs = mapOf(
                                "reason" to "network",
                                "exception" to error.javaClass.simpleName
                            )
                        )
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
            if (!isRuntimeStale()) {
                if (reason !in setOf("canceled", "quota", "replay", "stale_session")) {
                    reportClientLog(
                        level = "warn",
                        event = "chat.stream_interrupted",
                        message = "Chat stream interrupted",
                        attrs = mapOf(
                            "reason" to reason,
                            "image_count" to options.images.size,
                            "text_length" to options.text.length
                        )
                    )
                }
                onInterrupted(reason)
            }
        }
        val base = baseUrl()
        if (base.isEmpty()) {
            deliverInterrupted("server")
            return
        }
        val body = buildStreamRequestJson(options)
        fun buildRequest(token: String?): Request {
            val builder = applyIdentityHeaders(
                Request.Builder()
                    .url("$base${ApiConfig.PATH_CHAT_STREAM}")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "text/event-stream")
                    .post(body.toRequestBody("application/json".toMediaType()))
            )
            if (!token.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $token")
            return builder.build()
        }

        var deliveredAnyChunk = false

        fun start(hasRetried: Boolean, networkRetry: Int, activeStreamRetry: Int) {
            if (isRuntimeStale()) return
            ensureAuthToken { token ->
                if (isRuntimeStale()) return@ensureAuthToken
                val request = try {
                    buildRequest(token)
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "build stream request", e)
                    reportClientLog(
                        level = "error",
                        event = "chat.stream_request_build_failed",
                        message = "Chat stream request build failed",
                        attrs = mapOf("exception" to e.javaClass.simpleName)
                    )
                    deliverInterrupted("bad_request")
                    return@ensureAuthToken
                }
                val call = streamClient.newCall(request)
                currentStreamCall.set(call)
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        currentStreamCall.compareAndSet(call, null)
                        if (isRuntimeStale()) return
                        if (!call.isCanceled() && !deliveredAnyChunk && networkRetry < STREAM_NETWORK_RETRY_MAX) {
                            val retryDelayMs = 350L * (networkRetry + 1)
                            mainHandler.postDelayed({
                                if (isRuntimeStale()) return@postDelayed
                                start(
                                    hasRetried = hasRetried,
                                    networkRetry = networkRetry + 1,
                                    activeStreamRetry = activeStreamRetry
                                )
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
                                        start(
                                            hasRetried = true,
                                            networkRetry = networkRetry,
                                            activeStreamRetry = activeStreamRetry
                                        )
                                    } else {
                                        notifyAuthInvalid()
                                        deliverInterrupted("auth")
                                    }
                                    return
                                }
                                if (!res.isSuccessful) {
                                    if (!deliveredAnyChunk && networkRetry < STREAM_NETWORK_RETRY_MAX && (res.code == 502 || res.code == 503 || res.code == 504)) {
                                        val retryDelayMs = 350L * (networkRetry + 1)
                                        mainHandler.postDelayed({
                                            if (isRuntimeStale()) return@postDelayed
                                            start(
                                                hasRetried = hasRetried,
                                                networkRetry = networkRetry + 1,
                                                activeStreamRetry = activeStreamRetry
                                            )
                                        }, retryDelayMs)
                                        return
                                    }
                                    val errorCode = runCatching {
                                        gson.fromJson(res.body?.string().orEmpty(), JsonObject::class.java)
                                            ?.get("error")
                                            ?.takeIf { it.isJsonPrimitive }
                                            ?.asString
                                    }.getOrNull()
                                    if (
                                        res.code == 409 &&
                                        errorCode != "STALE_SESSION_GENERATION" &&
                                        !deliveredAnyChunk &&
                                        activeStreamRetry < STREAM_ACTIVE_RETRY_MAX
                                    ) {
                                        val retryDelayMs =
                                            STREAM_ACTIVE_RETRY_BASE_DELAY_MS * (activeStreamRetry + 1)
                                        mainHandler.postDelayed({
                                            if (isRuntimeStale()) return@postDelayed
                                            start(
                                                hasRetried = hasRetried,
                                                networkRetry = networkRetry,
                                                activeStreamRetry = activeStreamRetry + 1
                                            )
                                        }, retryDelayMs)
                                        return
                                    }
                                    val reason = when {
                                        res.code == 409 && errorCode == "STALE_SESSION_GENERATION" -> "stale_session"
                                        res.code == 409 -> "stream_in_progress"
                                        res.code == 503 -> "model_unavailable"
                                        res.code == 429 -> "rate_limit"
                                        res.code == 402 -> "quota"
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
                                            start(
                                                hasRetried = hasRetried,
                                                networkRetry = networkRetry + 1,
                                                activeStreamRetry = activeStreamRetry
                                            )
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

        start(hasRetried = false, networkRetry = 0, activeStreamRetry = 0)
    }

    fun streamChatToCompletion(
        options: StreamOptions,
        shouldContinue: () -> Boolean = { true }
    ): StreamCompletionResult {
        val base = baseUrl()
        if (base.isEmpty()) {
            return StreamCompletionResult(StreamCompletionStatus.ServerFailure, "server")
        }
        val body = buildStreamRequestJson(options)
        fun buildRequest(token: String?): Request {
            val builder = applyIdentityHeaders(
                Request.Builder()
                    .url("$base${ApiConfig.PATH_CHAT_STREAM}")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "text/event-stream")
                    .post(body.toRequestBody("application/json".toMediaType()))
            )
            if (!token.isNullOrBlank()) builder.addHeader("Authorization", "Bearer $token")
            return builder.build()
        }

        fun runRequest(hasRetriedAuth: Boolean, networkRetry: Int): StreamCompletionResult {
            val request = try {
                buildRequest(authTokenSync())
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "build background stream request", e)
                return StreamCompletionResult(StreamCompletionStatus.BadRequest, "request_build_failed")
            }
            val call = streamClient.newCall(request)
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
                            notifyAuthInvalid()
                            StreamCompletionResult(StreamCompletionStatus.Auth, "auth")
                        }
                    }
                    if (!res.isSuccessful) {
                        if (networkRetry < STREAM_NETWORK_RETRY_MAX && (res.code == 502 || res.code == 503 || res.code == 504)) {
                            Thread.sleep(350L * (networkRetry + 1))
                            return runRequest(hasRetriedAuth = hasRetriedAuth, networkRetry = networkRetry + 1)
                        }
                        val errorCode = runCatching {
                            gson.fromJson(res.body?.string().orEmpty(), JsonObject::class.java)
                                ?.get("error")
                                ?.takeIf { it.isJsonPrimitive }
                                ?.asString
                        }.getOrNull()
                        val status = when {
                            res.code == 400 -> StreamCompletionStatus.BadRequest
                            res.code == 409 && errorCode == "STALE_SESSION_GENERATION" -> StreamCompletionStatus.BadRequest
                            res.code == 409 -> StreamCompletionStatus.RetryableFailure
                            res.code == 402 -> StreamCompletionStatus.Quota
                            res.code == 429 -> StreamCompletionStatus.RateLimited
                            res.code in setOf(500, 502, 503, 504) -> StreamCompletionStatus.RetryableFailure
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

        val result = runRequest(hasRetriedAuth = false, networkRetry = 0)
        if (
            result.status !in setOf(StreamCompletionStatus.Complete, StreamCompletionStatus.Replay, StreamCompletionStatus.Quota) &&
            result.reason != "stopped"
        ) {
            reportClientLog(
                level = "warn",
                event = "chat.background_stream_failed",
                message = "Background chat stream failed",
                attrs = mapOf(
                    "status" to result.status.name,
                    "reason" to result.reason.orEmpty(),
                    "image_count" to options.images.size,
                    "text_length" to options.text.length
                )
            )
        }
        return result
    }

    private data class SessionSnapshotJson(
        @SerializedName("memory_document") val memory_document: String? = null,
        @SerializedName("b_summary") val b_summary: String?,
        @SerializedName("a_json") val a_json: List<ARoundJson>? = null,
        @SerializedName("a_rounds_full") val a_rounds_full: List<ARoundJson>?,
        @SerializedName("a_rounds_for_ui") val a_rounds_for_ui: List<ARoundJson>?,
        @SerializedName("a_rounds") val a_rounds: List<ARoundJson>? = null,
        @SerializedName("round_total") val round_total: Int? = null,
        @SerializedName("session_generation") val session_generation: Int? = null,
        @SerializedName("today_agri_items") val today_agri_items: List<TodayAgriMainItemJson>? = null
    )

    private data class SessionClearResponse(
        @SerializedName("session_generation") val sessionGeneration: Int? = null
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

    private data class TodayAgriMainItemJson(
        @SerializedName("day_cn") val day_cn: String? = null,
        @SerializedName("anchor_client_msg_id") val anchor_client_msg_id: String? = null,
        @SerializedName("card") val card: TodayAgriCard? = null,
        @SerializedName("created_at") val created_at: Long? = null,
        @SerializedName("updated_at") val updated_at: Long? = null
    )

    private data class SupportMessagesResponse(
        val messages: List<SupportMessage>? = null
    )

    private data class SupportMessageResponse(
        val message: SupportMessage? = null,
        @SerializedName("auto_reply") val autoReply: SupportMessage? = null
    ) {
        fun toResult(): SupportSendResult = SupportSendResult(
            message = message,
            autoReply = autoReply
        )
    }

    private fun giftCardRedeemErrorMessage(statusCode: Int, body: String): String {
        val code = runCatching {
            gson.fromJson(body, JsonObject::class.java)
                ?.get("error")
                ?.asString
                .orEmpty()
        }.getOrDefault("")
        return when (code) {
            "gift_card_invalid_code", "gift_card_not_found" -> "礼品卡码不正确，请核对后重试"
            "gift_card_inactive" -> "这张礼品卡已被兑换或已作废"
            "gift_card_expired" -> "这张礼品卡已过期"
            "gift_card_lower_tier" -> "当前已是更高档会员，这张礼品卡不能叠加使用"
            "gift_card_not_configured" -> "礼品卡服务暂时不可用，请稍后再试"
            "rate_limited" -> "尝试太频繁，请稍后再试"
            "unauthorized" -> "请先登录后兑换"
            else -> if (statusCode >= 500) "服务暂时不可用，请稍后再试" else "兑换失败，请稍后再试"
        }
    }

    private fun supportSendErrorMessage(statusCode: Int, errorCode: String): String =
        when {
            statusCode == 429 || errorCode == "rate_limited" -> "发送太频繁，请稍后再试"
            statusCode == 409 || errorCode == "support_message_in_progress" -> "上一条反馈仍在处理中，请稍后再试"
            statusCode == 401 || errorCode == "unauthorized" -> "登录已失效，请重新登录后再发送"
            errorCode == "body_too_long" -> "反馈内容太长，请控制在2000字以内"
            errorCode == "too_many_images" -> "一次最多上传4张图片"
            errorCode == "body_or_images_required" -> "请先填写反馈内容或添加图片"
            else -> "发送失败，请稍后重试"
        }

    private fun postToMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
