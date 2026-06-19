package com.nongjiqianwen

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.util.Collections

internal data class PendingChatSend(
    val chatScopeId: String,
    val userMessageId: String,
    val text: String,
    val imageUris: List<String>,
    val imageUrls: List<String> = emptyList(),
    val authUserId: String? = null,
    val sessionGeneration: Int? = null,
    val region: String? = null,
    val regionSource: String? = null,
    val regionReliability: String? = null,
    val todayAgriContextDay: String? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
    val remoteStartedAtMs: Long = 0L,
    val recoverableFailureCount: Int = 0
)

internal object PendingChatSendRuntime {
    private val activeMessageIds = Collections.synchronizedSet(mutableSetOf<String>())

    fun markActive(userMessageId: String) {
        if (userMessageId.isNotBlank()) activeMessageIds.add(userMessageId)
    }

    fun markInactive(userMessageId: String) {
        if (userMessageId.isNotBlank()) activeMessageIds.remove(userMessageId)
    }

    fun isActive(userMessageId: String): Boolean =
        userMessageId.isNotBlank() && activeMessageIds.contains(userMessageId)
}

internal object PendingChatSendStore {
    private const val PREFS_NAME = "pending_chat_sends"
    private const val KEY_PREFIX = "pending_"
    private const val TERMINAL_FAILURE_KEY_PREFIX = "terminal_failure_"
    const val REMOTE_STARTED_GRACE_MS = 10 * 60 * 1000L
    private val gson = Gson()

    fun upsert(context: Context, pending: PendingChatSend) {
        val scoped = if (pending.authUserId.isNullOrBlank()) {
            pending.copy(authUserId = IdManager.getAuthenticatedUserId())
        } else {
            pending
        }
        val next = scoped.copy(updatedAtMs = System.currentTimeMillis())
        prefs(context).edit()
            .putString(key(next.chatScopeId, next.userMessageId), gson.toJson(next))
            .remove(terminalFailureKey(next.chatScopeId, next.userMessageId))
            .commit()
    }

    fun get(context: Context, chatScopeId: String, userMessageId: String): PendingChatSend? {
        val raw = prefs(context).getString(key(chatScopeId, userMessageId), null).orEmpty()
        if (raw.isBlank()) return null
        return try {
            gson.fromJson(raw, PendingChatSend::class.java)
        } catch (_: JsonSyntaxException) {
            null
        }
    }

    fun has(context: Context, chatScopeId: String, userMessageId: String): Boolean =
        get(context, chatScopeId, userMessageId) != null

    fun isStaleForCurrentSession(pending: PendingChatSend): Boolean {
        val pendingAuthUserId = pending.authUserId?.trim().orEmpty()
        val currentAuthUserId = IdManager.getAuthenticatedUserId()?.trim().orEmpty()
        if (pendingAuthUserId.isBlank() || currentAuthUserId.isBlank() || pendingAuthUserId != currentAuthUserId) {
            return true
        }
        val currentGeneration = SessionApi.currentSessionGenerationOrNull() ?: return false
        val pendingGeneration = pending.sessionGeneration ?: return false
        return pendingGeneration != currentGeneration
    }

    fun updateImageUrls(
        context: Context,
        chatScopeId: String,
        userMessageId: String,
        imageUrls: List<String>
    ) {
        val pending = get(context, chatScopeId, userMessageId) ?: return
        upsert(context, pending.copy(imageUrls = imageUrls))
    }

    fun updateRegion(
        context: Context,
        chatScopeId: String,
        userMessageId: String,
        region: ClientRegionContext?
    ) {
        val pending = get(context, chatScopeId, userMessageId) ?: return
        upsert(
            context,
            pending.copy(
                region = region?.region,
                regionSource = region?.source,
                regionReliability = region?.reliability
            )
        )
    }

    fun markRemoteStarted(context: Context, chatScopeId: String, userMessageId: String) {
        val pending = get(context, chatScopeId, userMessageId) ?: return
        upsert(context, pending.copy(remoteStartedAtMs = System.currentTimeMillis()))
    }

    fun incrementRecoverableFailureCount(
        context: Context,
        chatScopeId: String,
        userMessageId: String
    ): Int? {
        val pending = get(context, chatScopeId, userMessageId) ?: return null
        val nextCount = pending.recoverableFailureCount + 1
        upsert(context, pending.copy(recoverableFailureCount = nextCount))
        return nextCount
    }

    fun remove(context: Context, chatScopeId: String, userMessageId: String) {
        prefs(context).edit()
            .remove(key(chatScopeId, userMessageId))
            .commit()
    }

    fun clear(context: Context, chatScopeId: String, userMessageId: String) {
        prefs(context).edit()
            .remove(key(chatScopeId, userMessageId))
            .remove(terminalFailureKey(chatScopeId, userMessageId))
            .commit()
    }

    fun markTerminalFailure(
        context: Context,
        chatScopeId: String,
        userMessageId: String,
        reason: String
    ) {
        val safeReason = reason.trim().take(48).ifBlank { "failed" }
        prefs(context).edit()
            .putString(terminalFailureKey(chatScopeId, userMessageId), safeReason)
            .commit()
    }

    fun terminalFailureReason(context: Context, chatScopeId: String, userMessageId: String): String? =
        prefs(context)
            .getString(terminalFailureKey(chatScopeId, userMessageId), null)
            ?.takeIf { it.isNotBlank() }

    fun hasTerminalFailure(context: Context, chatScopeId: String, userMessageId: String): Boolean =
        terminalFailureReason(context, chatScopeId, userMessageId) != null

    fun consumeTerminalFailure(context: Context, chatScopeId: String, userMessageId: String) {
        prefs(context).edit()
            .remove(terminalFailureKey(chatScopeId, userMessageId))
            .commit()
    }

    fun retainedImageUris(context: Context): Set<String> {
        val all = prefs(context).all
        if (all.isEmpty()) return emptySet()
        return buildSet {
            all.values.forEach { value ->
                val raw = value as? String ?: return@forEach
                val pending = try {
                    gson.fromJson(raw, PendingChatSend::class.java)
                } catch (_: JsonSyntaxException) {
                    null
                } ?: return@forEach
                pending.imageUris.forEach(::add)
            }
        }
    }

    fun userMessageIdsForScope(context: Context, chatScopeId: String): Set<String> {
        if (chatScopeId.isBlank()) return emptySet()
        val expectedPrefix = key(chatScopeId, "")
        val expectedTerminalFailurePrefix = terminalFailureKey(chatScopeId, "")
        val all = prefs(context).all
        if (all.isEmpty()) return emptySet()
        return buildSet {
            all.forEach { (storedKey, value) ->
                if (storedKey.startsWith(expectedTerminalFailurePrefix)) {
                    storedKey.removePrefix(expectedTerminalFailurePrefix)
                        .takeIf { it.isNotBlank() }
                        ?.let(::add)
                    return@forEach
                }
                val raw = value as? String ?: return@forEach
                val pending = try {
                    gson.fromJson(raw, PendingChatSend::class.java)
                } catch (_: JsonSyntaxException) {
                    null
                }
                when {
                    pending?.chatScopeId == chatScopeId && pending.userMessageId.isNotBlank() -> {
                        add(pending.userMessageId)
                    }
                    storedKey.startsWith(expectedPrefix) -> {
                        storedKey.removePrefix(expectedPrefix).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }
        }
    }

    fun keysForAuthUserId(context: Context, authUserId: String): List<Pair<String, String>> {
        val expectedAuthUserId = authUserId.trim()
        if (expectedAuthUserId.isBlank()) return emptyList()
        val all = prefs(context).all
        if (all.isEmpty()) return emptyList()
        return buildList {
            all.values.forEach { value ->
                val raw = value as? String ?: return@forEach
                val pending = try {
                    gson.fromJson(raw, PendingChatSend::class.java)
                } catch (_: JsonSyntaxException) {
                    null
                } ?: return@forEach
                if (
                    pending.authUserId?.trim() == expectedAuthUserId &&
                    pending.chatScopeId.isNotBlank() &&
                    pending.userMessageId.isNotBlank()
                ) {
                    add(pending.chatScopeId to pending.userMessageId)
                }
            }
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(chatScopeId: String, userMessageId: String): String =
        "$KEY_PREFIX$chatScopeId:$userMessageId"

    private fun terminalFailureKey(chatScopeId: String, userMessageId: String): String =
        "$TERMINAL_FAILURE_KEY_PREFIX$chatScopeId:$userMessageId"
}
