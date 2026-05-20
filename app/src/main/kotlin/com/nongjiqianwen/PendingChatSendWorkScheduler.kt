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

    fun enqueue(context: Context, pending: PendingChatSend) {
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
            .setInitialDelay(BACKUP_INITIAL_DELAY_MS, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            uniqueName(pending.chatScopeId, pending.userMessageId),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun markRemoteStarted(context: Context, chatScopeId: String, userMessageId: String) {
        PendingChatSendStore.markRemoteStarted(context, chatScopeId, userMessageId)
    }

    fun complete(context: Context, chatScopeId: String, userMessageId: String) {
        PendingChatSendRuntime.markInactive(userMessageId)
        if (!PendingChatSendStore.has(context, chatScopeId, userMessageId)) return
        PendingChatSendStore.remove(context, chatScopeId, userMessageId)
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(uniqueName(chatScopeId, userMessageId))
    }

    fun cancelAndRemove(context: Context, chatScopeId: String, userMessageId: String) {
        PendingChatSendRuntime.markInactive(userMessageId)
        if (!PendingChatSendStore.has(context, chatScopeId, userMessageId)) return
        PendingChatSendStore.remove(context, chatScopeId, userMessageId)
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(uniqueName(chatScopeId, userMessageId))
    }

    fun cancelAllForScope(context: Context, chatScopeId: String) {
        PendingChatSendStore.userMessageIdsForScope(context, chatScopeId).forEach { userMessageId ->
            cancelAndRemove(context, chatScopeId, userMessageId)
        }
    }

    private fun uniqueName(chatScopeId: String, userMessageId: String): String =
        "$UNIQUE_PREFIX$chatScopeId:$userMessageId"
}
