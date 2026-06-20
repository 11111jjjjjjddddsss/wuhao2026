package com.nongjiqianwen

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

internal object PendingChatSendWorkScheduler {
    const val KEY_CHAT_SCOPE_ID = "chat_scope_id"
    const val KEY_USER_MESSAGE_ID = "user_message_id"
    private const val UNIQUE_PREFIX = "pending_chat_send_"
    private const val BACKUP_INITIAL_DELAY_MS = 45_000L

    fun enqueue(
        context: Context,
        pending: PendingChatSend,
        replaceExisting: Boolean = false,
        initialDelayMs: Long = BACKUP_INITIAL_DELAY_MS
    ) {
        PendingChatSendStore.upsert(context, pending)
        val request = OneTimeWorkRequestBuilder<PendingChatSendWorker>()
            .setInputData(
                workDataOf(
                    KEY_CHAT_SCOPE_ID to pending.chatScopeId,
                    KEY_USER_MESSAGE_ID to pending.userMessageId
                )
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .setInitialDelay(initialDelayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            uniqueName(pending.chatScopeId, pending.userMessageId),
            if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun markRemoteStarted(context: Context, chatScopeId: String, userMessageId: String) {
        PendingChatSendStore.markRemoteStarted(context, chatScopeId, userMessageId)
    }

    fun complete(context: Context, chatScopeId: String, userMessageId: String) {
        PendingChatSendRuntime.markInactive(userMessageId)
        PendingChatSendStore.clear(context, chatScopeId, userMessageId)
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(uniqueName(chatScopeId, userMessageId))
    }

    fun cancelAndRemove(context: Context, chatScopeId: String, userMessageId: String) {
        PendingChatSendRuntime.markInactive(userMessageId)
        PendingChatSendStore.clear(context, chatScopeId, userMessageId)
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(uniqueName(chatScopeId, userMessageId))
    }

    fun cancelAndMarkTerminalFailure(
        context: Context,
        chatScopeId: String,
        userMessageId: String,
        reason: String
    ) {
        PendingChatSendRuntime.markInactive(userMessageId)
        val uploadedImageUrls = PendingChatSendStore.get(context, chatScopeId, userMessageId)
            ?.imageUrls
            .orEmpty()
        PendingChatSendStore.markTerminalFailureAndRemovePending(
            context = context,
            chatScopeId = chatScopeId,
            userMessageId = userMessageId,
            reason = reason,
            imageUrls = uploadedImageUrls
        )
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(uniqueName(chatScopeId, userMessageId))
    }

    fun cancelAllForScope(context: Context, chatScopeId: String) {
        PendingChatSendStore.userMessageIdsForScope(context, chatScopeId).forEach { userMessageId ->
            cancelAndRemove(context, chatScopeId, userMessageId)
        }
    }

    fun cancelAllForAuthUserId(context: Context, authUserId: String) {
        PendingChatSendStore.keysForAuthUserId(context, authUserId).forEach { (chatScopeId, userMessageId) ->
            cancelAndMarkTerminalFailure(context, chatScopeId, userMessageId, "auth")
        }
    }

    private fun uniqueName(chatScopeId: String, userMessageId: String): String =
        "$UNIQUE_PREFIX$chatScopeId:$userMessageId"
}
