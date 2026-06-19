package com.nongjiqianwen

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

class PendingChatSendWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!PrivacyConsentStore.isAccepted(applicationContext)) {
            return@withContext Result.retry()
        }
        IdManager.init(applicationContext)
        val chatScopeId = inputData.getString(PendingChatSendWorkScheduler.KEY_CHAT_SCOPE_ID).orEmpty()
        val userMessageId = inputData.getString(PendingChatSendWorkScheduler.KEY_USER_MESSAGE_ID).orEmpty()
        if (chatScopeId.isBlank() || userMessageId.isBlank()) return@withContext Result.failure()

        if (PendingChatSendRuntime.isActive(userMessageId)) {
            return@withContext Result.retry()
        }

        val pending = PendingChatSendStore.get(applicationContext, chatScopeId, userMessageId)
            ?: return@withContext Result.success()
        if (PendingChatSendStore.isStaleForCurrentSession(pending)) {
            PendingChatSendStore.remove(applicationContext, chatScopeId, userMessageId)
            PendingChatSendRuntime.markInactive(userMessageId)
            return@withContext Result.success()
        }
        val now = System.currentTimeMillis()
        if (
            pending.remoteStartedAtMs > 0 &&
            now - pending.remoteStartedAtMs < PendingChatSendStore.REMOTE_STARTED_GRACE_MS
        ) {
            return@withContext Result.retry()
        }

        if (!SessionApi.hasBackendConfigured()) {
            return@withContext failTerminal(chatScopeId, userMessageId, "backend_not_configured")
        }
        if (!applicationContext.hasActiveNetworkConnection()) {
            return@withContext Result.retry()
        }
        if (isStopped) return@withContext Result.retry()

        val imageUrls = if (pending.imageUrls.isNotEmpty()) {
            pending.imageUrls
        } else {
            val uploadBytes = readUploadBytes(pending.imageUris)
                ?: run {
                    if (isStopped) return@withContext Result.retry()
                    return@withContext failTerminal(chatScopeId, userMessageId, "image_read_failed")
                }
            ImageUploader.uploadImages(uploadBytes)
                ?: return@withContext retryRecoverableOrFail(chatScopeId, userMessageId)
        }
        if (isStopped) return@withContext Result.retry()
        if (PendingChatSendStore.get(applicationContext, chatScopeId, userMessageId) == null) {
            return@withContext Result.success()
        }
        if (PendingChatSendStore.isStaleForCurrentSession(pending)) {
            PendingChatSendStore.remove(applicationContext, chatScopeId, userMessageId)
            PendingChatSendRuntime.markInactive(userMessageId)
            return@withContext Result.success()
        }

        PendingChatSendStore.updateImageUrls(
            applicationContext,
            chatScopeId,
            userMessageId,
            imageUrls
        )
        if (PendingChatSendStore.get(applicationContext, chatScopeId, userMessageId) == null) {
            return@withContext Result.success()
        }
        if (PendingChatSendStore.isStaleForCurrentSession(pending)) {
            PendingChatSendStore.remove(applicationContext, chatScopeId, userMessageId)
            PendingChatSendRuntime.markInactive(userMessageId)
            return@withContext Result.success()
        }
        PendingChatSendStore.markRemoteStarted(applicationContext, chatScopeId, userMessageId)
        if (PendingChatSendStore.get(applicationContext, chatScopeId, userMessageId) == null) {
            return@withContext Result.success()
        }
        val result = SessionApi.streamChatToCompletion(
            SessionApi.StreamOptions(
                clientMsgId = userMessageId,
                text = pending.text,
                images = imageUrls,
                sessionGeneration = pending.sessionGeneration,
                region = pending.region,
                regionSource = pending.regionSource,
                regionReliability = pending.regionReliability,
                todayAgriContextDay = pending.todayAgriContextDay
            )
        ) {
            !isStopped && PendingChatSendStore.has(applicationContext, chatScopeId, userMessageId)
        }
        when (result.status) {
            SessionApi.StreamCompletionStatus.Complete,
            SessionApi.StreamCompletionStatus.Replay -> {
                PendingChatSendStore.clear(applicationContext, chatScopeId, userMessageId)
                Result.success()
            }
            SessionApi.StreamCompletionStatus.RetryableFailure -> {
                retryRecoverableOrFail(chatScopeId, userMessageId)
            }
            SessionApi.StreamCompletionStatus.RateLimited -> {
                retryRecoverableOrFail(chatScopeId, userMessageId)
            }
            SessionApi.StreamCompletionStatus.Quota,
            SessionApi.StreamCompletionStatus.Auth,
            SessionApi.StreamCompletionStatus.BadRequest,
            SessionApi.StreamCompletionStatus.ServerFailure -> {
                failTerminal(
                    chatScopeId = chatScopeId,
                    userMessageId = userMessageId,
                    reason = when (result.status) {
                        SessionApi.StreamCompletionStatus.Quota -> "quota"
                        SessionApi.StreamCompletionStatus.Auth -> "auth"
                        SessionApi.StreamCompletionStatus.BadRequest -> "bad_request"
                        else -> "server_failure"
                    }
                )
            }
        }
    }

    private fun retryRecoverableOrFail(chatScopeId: String, userMessageId: String): Result {
        val failureCount = PendingChatSendStore.incrementRecoverableFailureCount(
            applicationContext,
            chatScopeId,
            userMessageId
        ) ?: return Result.success()
        return if (failureCount > MAX_RECOVERABLE_FAILURES) {
            failTerminal(chatScopeId, userMessageId, "retry_exhausted")
        } else {
            Result.retry()
        }
    }

    private fun failTerminal(chatScopeId: String, userMessageId: String, reason: String): Result {
        PendingChatSendStore.markTerminalFailure(
            context = applicationContext,
            chatScopeId = chatScopeId,
            userMessageId = userMessageId,
            reason = reason
        )
        PendingChatSendStore.remove(applicationContext, chatScopeId, userMessageId)
        PendingChatSendRuntime.markInactive(userMessageId)
        return Result.failure()
    }

    private fun readUploadBytes(imageUris: List<String>): List<ByteArray>? {
        if (imageUris.isEmpty() || imageUris.size > 4) return null
        return imageUris.map { source ->
            if (isStopped) return null
            val originalBytes = applicationContext.readImageBytesForPendingSend(source) ?: return null
            if (originalBytes.hasJpegStartMarkerForPendingSend() && originalBytes.size <= MAX_IMAGE_SIZE_BYTES) {
                originalBytes
            } else {
                ImageUploader.compressImage(originalBytes)?.bytes ?: return null
            }
        }
    }

    private fun Context.readImageBytesForPendingSend(source: String): ByteArray? {
        return runCatching {
            val uri = Uri.parse(source)
            if (uri.scheme == "file") {
                val path = uri.path ?: return@runCatching null
                val file = File(path).takeIf { it.isFile } ?: return@runCatching null
                if (file.length() > MAX_ORIGINAL_IMAGE_BYTES) return@runCatching null
                file.inputStream().use { it.readBytesWithLimit(MAX_ORIGINAL_IMAGE_BYTES) }
            } else {
                contentResolver.openInputStream(uri)?.use { it.readBytesWithLimit(MAX_ORIGINAL_IMAGE_BYTES) }
            }
        }.getOrNull()
    }

    private fun InputStream.readBytesWithLimit(maxBytes: Int): ByteArray? {
        val buffer = ByteArray(8 * 1024)
        val output = ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun ByteArray.hasJpegStartMarkerForPendingSend(): Boolean =
        size >= 2 && this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte()

    private companion object {
        const val MAX_IMAGE_SIZE_BYTES = 1024 * 1024
        const val MAX_ORIGINAL_IMAGE_BYTES = 32 * 1024 * 1024
        const val MAX_RECOVERABLE_FAILURES = 5
    }
}
