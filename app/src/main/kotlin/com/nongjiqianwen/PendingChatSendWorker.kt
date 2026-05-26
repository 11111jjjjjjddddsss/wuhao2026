package com.nongjiqianwen

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PendingChatSendWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
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
            PendingChatSendStore.remove(applicationContext, chatScopeId, userMessageId)
            return@withContext Result.failure()
        }
        if (isStopped) return@withContext Result.retry()

        val imageUrls = if (pending.imageUrls.isNotEmpty()) {
            pending.imageUrls
        } else {
            val uploadBytes = readUploadBytes(pending.imageUris)
                ?: run {
                    if (isStopped) return@withContext Result.retry()
                    PendingChatSendStore.remove(applicationContext, chatScopeId, userMessageId)
                    return@withContext Result.failure()
                }
            ImageUploader.uploadImages(uploadBytes)
                ?: return@withContext retryRecoverableOrFail(chatScopeId, userMessageId)
        }
        if (isStopped) return@withContext Result.retry()
        if (PendingChatSendStore.get(applicationContext, chatScopeId, userMessageId) == null) {
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
        PendingChatSendStore.markRemoteStarted(applicationContext, chatScopeId, userMessageId)
        if (PendingChatSendStore.get(applicationContext, chatScopeId, userMessageId) == null) {
            return@withContext Result.success()
        }
        val result = SessionApi.streamChatToCompletion(
            SessionApi.StreamOptions(
                clientMsgId = userMessageId,
                text = pending.text,
                images = imageUrls,
                sessionGeneration = pending.sessionGeneration
            )
        ) {
            !isStopped && PendingChatSendStore.has(applicationContext, chatScopeId, userMessageId)
        }
        when (result.status) {
            SessionApi.StreamCompletionStatus.Complete,
            SessionApi.StreamCompletionStatus.Replay -> {
                PendingChatSendStore.remove(applicationContext, chatScopeId, userMessageId)
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
                PendingChatSendStore.remove(applicationContext, chatScopeId, userMessageId)
                Result.failure()
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
            PendingChatSendStore.remove(applicationContext, chatScopeId, userMessageId)
            Result.failure()
        } else {
            Result.retry()
        }
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
                File(path).takeIf { it.isFile }?.readBytes()
            } else {
                contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
        }.getOrNull()
    }

    private fun ByteArray.hasJpegStartMarkerForPendingSend(): Boolean =
        size >= 2 && this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte()

    private companion object {
        const val MAX_IMAGE_SIZE_BYTES = 1024 * 1024
        const val MAX_RECOVERABLE_FAILURES = 5
    }
}
