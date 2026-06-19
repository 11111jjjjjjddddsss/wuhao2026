package com.nongjiqianwen

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.exifinterface.media.ExifInterface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.coroutines.resume

internal enum class ChatRole { USER, ASSISTANT }

private enum class InitialWorklinePhase {
    WaitingForFirstSend,
    TopUnreached,
    TopAnchoring,
    WorklineOwned
}

@Immutable
internal data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val content: String,
    val imageUris: List<String>? = null,
    val imageUrls: List<String>? = null,
    val todayAgriContextDay: String? = null
)
@Immutable
internal sealed interface ChatTimelineItem {
    val stableKey: String

    @Immutable
    data class HistoryNotice(
        val hiddenRoundCount: Int
    ) : ChatTimelineItem {
        override val stableKey: String = "history-notice"
    }

    @Immutable
    data class Message(val message: ChatMessage) : ChatTimelineItem {
        override val stableKey: String = message.id
    }

    @Immutable
    data class TodayAgriCard(
        val card: SessionApi.TodayAgriCard
    ) : ChatTimelineItem {
        override val stableKey: String = "today-agri-card-${normalizeTodayAgriCardDayKey(card.dateCn.orEmpty())}"
    }
}
@Immutable
internal data class FailedAssistantMessageState(
    val sourceUserMessageId: String,
    val reason: String? = null
)
@Immutable
internal data class LocalChatWindowSnapshot(
    val messages: List<ChatMessage> = emptyList(),
    val failedUserMessageStates: Map<String, String> = emptyMap(),
    val failedAssistantMessageStates: Map<String, FailedAssistantMessageState> = emptyMap(),
    val initialWorklineOwned: Boolean? = null
)

private data class PendingHydratedSnapshot(
    val snapshot: LocalChatWindowSnapshot,
    val replaceMessages: Boolean,
    val updateFailedUserStates: Boolean,
    val updateFailedAssistantStates: Boolean,
    val updateHiddenRemoteRoundCount: Boolean,
    val hiddenRemoteRoundCount: Int,
    val startupRecoverableUserMessageId: String?
)

@Immutable
private data class LocalChatWindowSnapshotPayload(
    val messages: List<ChatMessage>? = null,
    val failedUserMessageStates: Map<String, String>? = null,
    val failedAssistantMessageStates: Map<String, FailedAssistantMessageState>? = null,
    val initialWorklineOwned: Boolean? = null,
    val sessionGeneration: Int? = null
)

private fun restoredInitialWorklinePhase(
    hasStaticVisualContent: Boolean,
    hasStreamingDraft: Boolean,
    initialWorklineOwned: Boolean?
): InitialWorklinePhase =
    when {
        !hasStaticVisualContent && !hasStreamingDraft -> InitialWorklinePhase.WaitingForFirstSend
        initialWorklineOwned == false -> InitialWorklinePhase.TopUnreached
        else -> InitialWorklinePhase.WorklineOwned
    }

private fun restoredStartupWorklinePhase(
    messageCount: Int,
    hasTodayAgriVisualContent: Boolean,
    persistedInitialWorklineOwned: Boolean?
): InitialWorklinePhase =
    restoredInitialWorklinePhase(
        hasStaticVisualContent = messageCount > 0 || hasTodayAgriVisualContent,
        hasStreamingDraft = false,
        initialWorklineOwned = when {
            messageCount > 0 -> persistedInitialWorklineOwned
            hasTodayAgriVisualContent -> false
            else -> null
        }
    )

private fun ChatMessage.isLocalImageUploadPendingUserMessage(): Boolean =
    role == ChatRole.USER &&
        imageUris.orEmpty().isNotEmpty() &&
        imageUrls.orEmpty().isEmpty()

internal fun shouldShowPendingImageSendFooter(
    message: ChatMessage,
    pendingExists: Boolean,
    failedUserStateExists: Boolean
): Boolean =
    message.isLocalImageUploadPendingUserMessage() &&
        pendingExists &&
        !failedUserStateExists

private fun ChatMessage.isCompletedAssistantAnswer(failedAssistantMessageIds: Set<String> = emptySet()): Boolean =
    role == ChatRole.ASSISTANT && content.isNotBlank() && id !in failedAssistantMessageIds

internal fun hasCompletedAssistantAnswerTail(
    messages: List<ChatMessage>,
    failedAssistantMessageIds: Set<String> = emptySet()
): Boolean =
    messages.lastOrNull()?.isCompletedAssistantAnswer(failedAssistantMessageIds) == true

private fun latestCompletedAssistantAnswerId(
    messages: List<ChatMessage>,
    failedAssistantMessageIds: Set<String> = emptySet()
): String? =
    messages.lastOrNull()?.takeIf { it.isCompletedAssistantAnswer(failedAssistantMessageIds) }?.id

internal fun buildChatTimelineItems(
    messages: List<ChatMessage>,
    todayAgriCard: SessionApi.TodayAgriCard?,
    todayAgriAfterMessageId: String?,
    hiddenRoundCount: Int = 0,
    failedAssistantMessageIds: Set<String> = emptySet()
): List<ChatTimelineItem> {
    val items = ArrayList<ChatTimelineItem>(messages.size + 2)
    if (hiddenRoundCount > 0) {
        items += ChatTimelineItem.HistoryNotice(hiddenRoundCount)
    }
    val validTodayAgriCard = todayAgriCard?.takeIf {
        it.isRenderableTodayAgriCard() &&
            messages.any { message -> message.isCompletedAssistantAnswer(failedAssistantMessageIds) }
    }
    val requestedAfterMessageId = todayAgriAfterMessageId?.takeIf { it.isNotBlank() }
    val requestedAfterAssistantMessageIds = requestedAfterMessageId
        ?.let { id -> setOf(id, assistantMessageIdForSourceUser(id)) }
        .orEmpty()
    var todayAgriCardInserted = false
    var latestAssistantAnswerInsertIndex = -1
    messages.forEach { message ->
        items += ChatTimelineItem.Message(message)
        if (message.isCompletedAssistantAnswer(failedAssistantMessageIds)) {
            latestAssistantAnswerInsertIndex = items.size
        }
        if (
            validTodayAgriCard != null &&
            requestedAfterAssistantMessageIds.isNotEmpty() &&
            message.id in requestedAfterAssistantMessageIds &&
            message.isCompletedAssistantAnswer(failedAssistantMessageIds)
        ) {
            items += ChatTimelineItem.TodayAgriCard(validTodayAgriCard)
            todayAgriCardInserted = true
        }
    }
    if (validTodayAgriCard != null && !todayAgriCardInserted) {
        val insertIndex = if (requestedAfterMessageId != null && hiddenRoundCount > 0) {
            items.indexOfFirst { item -> item is ChatTimelineItem.HistoryNotice }
                .takeIf { it >= 0 }
                ?.plus(1)
                ?: 0
        } else {
            latestAssistantAnswerInsertIndex.takeIf { it >= 0 } ?: items.size
        }
        items.add(insertIndex, ChatTimelineItem.TodayAgriCard(validTodayAgriCard))
    }
    return items
}

internal fun resolveTodayAgriContextDayForTimeline(
    chatListItems: List<ChatTimelineItem>,
    currentTodayAgriCardDay: String,
    currentDayKey: String,
    remoteConfirmedDay: String?,
    existingUserMessageId: String? = null,
    failedUserMessageIds: Set<String> = emptySet()
): String? {
    val day = currentTodayAgriCardDay.takeIf { it.isNotBlank() && it == currentDayKey } ?: return null
    if (remoteConfirmedDay != day) return null
    val todayAgriVisualIndex = chatListItems.indexOfFirst { item ->
        item is ChatTimelineItem.TodayAgriCard
    }
    if (todayAgriVisualIndex < 0) return null
    val userMessagesAfterAnchor = chatListItems
        .drop(todayAgriVisualIndex + 1)
        .count { message ->
            message is ChatTimelineItem.Message &&
                message.message.role == ChatRole.USER &&
                message.message.id != existingUserMessageId &&
                message.message.id !in failedUserMessageIds
    }
    return day.takeIf { userMessagesAfterAnchor < 3 }
}

internal fun isTodayAgriCardVisibleInViewport(
    chatListItems: List<ChatTimelineItem>,
    visibleItems: List<VisibleChatListItem>,
    viewportStartOffset: Int,
    viewportEndOffset: Int,
    minVisiblePx: Int,
    coveredBottomPx: Int = 0
): Boolean {
    val todayAgriVisualIndex = chatListItems.indexOfFirst { item ->
        item is ChatTimelineItem.TodayAgriCard
    }
    val effectiveViewportEndOffset = (viewportEndOffset - coveredBottomPx.coerceAtLeast(0))
        .coerceAtLeast(viewportStartOffset)
    if (todayAgriVisualIndex < 0 || effectiveViewportEndOffset <= viewportStartOffset || minVisiblePx <= 0) {
        return false
    }
    val visibleItem = visibleItems.firstOrNull { it.index == todayAgriVisualIndex } ?: return false
    val itemStart = visibleItem.offset
    val itemEnd = visibleItem.offset + visibleItem.size
    val visibleStart = maxOf(itemStart, viewportStartOffset)
    val visibleEnd = minOf(itemEnd, effectiveViewportEndOffset)
    return visibleEnd > visibleStart && visibleEnd - visibleStart >= minVisiblePx
}

internal fun resolveTodayAgriVisualAnchorMessageId(
    savedAnchorMessageId: String?,
    localAnchorMessageId: String?,
    latestCompletedAssistantTailId: String?,
    existingMessageIds: Set<String>
): String? {
    savedAnchorMessageId
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }
    localAnchorMessageId
        ?.takeIf { it.isNotBlank() && it in existingMessageIds }
        ?.let { return it }
    return latestCompletedAssistantTailId?.takeIf { it.isNotBlank() }
}

internal data class VisibleChatListItem(
    val index: Int,
    val offset: Int,
    val size: Int
)

internal fun shouldShowTodayAgriMainCard(
    card: SessionApi.TodayAgriCard?,
    currentDayKey: String,
    shownDayKey: String,
    shownThisRuntime: Boolean,
    hasAssistantAnswerTail: Boolean,
    hasSavedItem: Boolean = false,
    suppressedThisRuntime: Boolean = false,
    insertedThisRuntime: Boolean = false
): Boolean {
    val normalizedCurrentDay = normalizeTodayAgriCardDayKey(currentDayKey)
    val cardDay = normalizeTodayAgriCardDayKey(card?.dateCn.orEmpty())
    return card?.isRenderableTodayAgriCard() == true &&
        (shownThisRuntime || hasSavedItem || insertedThisRuntime || hasAssistantAnswerTail) &&
        cardDay.isNotBlank() &&
        cardDay == normalizedCurrentDay &&
        (!suppressedThisRuntime || shownThisRuntime || hasSavedItem || insertedThisRuntime) &&
        (shownThisRuntime || hasSavedItem || insertedThisRuntime || shownDayKey != cardDay)
}

internal fun shouldWaitForNextTodayAgriAssistantAfterDayChange(
    hasStartedConversation: Boolean,
    latestCompletedAssistantTailId: String?
): Boolean =
    hasStartedConversation || !latestCompletedAssistantTailId.isNullOrBlank()

internal fun shouldReleaseTodayAgriAutoInsertAfterDayChange(
    waitingForNextAssistant: Boolean,
    baselineCompletedAssistantTailId: String?,
    latestCompletedAssistantTailId: String?
): Boolean =
    waitingForNextAssistant &&
        !latestCompletedAssistantTailId.isNullOrBlank() &&
        latestCompletedAssistantTailId != baselineCompletedAssistantTailId

internal fun shouldRenderTodayAgriMainCardInTimeline(
    shouldShowTodayAgriCard: Boolean,
    shouldHydrateRemoteHistory: Boolean,
    remoteSnapshotHydrationComplete: Boolean,
    shownThisRuntime: Boolean
): Boolean =
    shouldShowTodayAgriCard &&
        (!shouldHydrateRemoteHistory || remoteSnapshotHydrationComplete || shownThisRuntime)

internal fun shouldClearTodayAgriMainItemAfterSnapshot(
    restoredItemFound: Boolean,
    todayAgriItemsUnavailable: Boolean
): Boolean = !restoredItemFound && !todayAgriItemsUnavailable

internal fun shouldSkipTodayAgriCardFetch(
    hasRemoteHistorySource: Boolean,
    todayAgriShownThisRuntime: Boolean,
    shownDayKey: String,
    refreshDayKey: String,
    hasRefreshDayItem: Boolean
): Boolean =
    !hasRemoteHistorySource &&
        !todayAgriShownThisRuntime &&
        shownDayKey == refreshDayKey &&
        !hasRefreshDayItem

internal fun shouldRevealChatMessageList(
    startupHydrationBarrierSatisfied: Boolean,
    historyHydrationComplete: Boolean,
    shouldHydrateRemoteHistory: Boolean,
    hasStartedConversation: Boolean,
    isStreaming: Boolean,
    hasStreamingItem: Boolean,
    hasTodayAgriCard: Boolean,
    messageCount: Int
): Boolean {
    val waitingForRemoteStartupHydration =
        shouldHydrateRemoteHistory &&
            !historyHydrationComplete &&
            !hasStartedConversation &&
            !isStreaming &&
            !hasStreamingItem &&
            messageCount <= 0
    return when {
        waitingForRemoteStartupHydration -> false
        messageCount > 0 -> true
        hasStreamingItem -> true
        !hasStartedConversation -> true
        startupHydrationBarrierSatisfied -> true
        else -> false
    }
}

internal fun shouldShowChatWelcomePlaceholder(
    startupHydrationBarrierSatisfied: Boolean,
    hasStartedConversation: Boolean,
    hasStreamingItem: Boolean,
    hasTodayAgriCard: Boolean,
    messageCount: Int
): Boolean =
    messageCount == 0 &&
        !hasStreamingItem &&
        (startupHydrationBarrierSatisfied || !hasStartedConversation)

@Composable
private fun ChatHistoryWindowNotice(
    hiddenRoundCount: Int,
    horizontalPadding: Dp,
    maxCardWidth: Dp
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.widthIn(max = maxCardWidth),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFF7F8FA),
            border = BorderStroke(1.dp, Color(0xFFE1E4E8))
        ) {
            Text(
                text = "仅显示最近 30 轮；更早 ${hiddenRoundCount} 轮已收起，后续对话会尽量接上",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                ),
                color = Color(0xFF686C74),
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun markLocalImageUploadPendingAsFailed(
    snapshot: LocalChatWindowSnapshot,
    shouldKeepPending: (String) -> Boolean = { false }
): LocalChatWindowSnapshot {
    val sanitizedSnapshot = sanitizeLocalChatWindowSnapshot(snapshot)
    val nextFailedStates = sanitizedSnapshot.failedUserMessageStates.toMutableMap()
    sanitizedSnapshot.messages.forEach { message ->
        if (!message.isLocalImageUploadPendingUserMessage()) return@forEach
        if (shouldKeepPending(message.id)) {
            nextFailedStates.remove(message.id)
            return@forEach
        }
        val hasSettledAssistant = sanitizedSnapshot.messages.any { candidate ->
            candidate.id == assistantMessageIdForSourceUser(message.id) &&
                candidate.role == ChatRole.ASSISTANT &&
                candidate.content.isNotBlank()
        }
        if (
            shouldMarkLocalImageUploadPendingAsFailed(
                message = message,
                shouldKeepPending = false,
                hasSettledAssistant = hasSettledAssistant
            )
        ) {
            nextFailedStates.putIfAbsent(message.id, "network")
        }
    }
    if (nextFailedStates == sanitizedSnapshot.failedUserMessageStates) return sanitizedSnapshot
    return sanitizedSnapshot.copy(failedUserMessageStates = nextFailedStates)
}

internal fun shouldMarkLocalImageUploadPendingAsFailed(
    message: ChatMessage,
    shouldKeepPending: Boolean,
    hasSettledAssistant: Boolean
): Boolean =
    message.isLocalImageUploadPendingUserMessage() &&
        !shouldKeepPending &&
        !hasSettledAssistant

internal fun shouldTrackPendingImageAssistantRecovery(
    message: ChatMessage,
    pendingExists: Boolean,
    terminalFailureExists: Boolean,
    remoteCompletionExists: Boolean,
    hasSettledAssistant: Boolean
): Boolean =
    message.isLocalImageUploadPendingUserMessage() &&
        !hasSettledAssistant &&
        (pendingExists || terminalFailureExists || remoteCompletionExists)

internal fun shouldApplyPendingImageTerminalFailure(
    terminalFailureReason: String?,
    snapshotAvailable: Boolean,
    isLastAttempt: Boolean
): Boolean {
    val reason = terminalFailureReason?.trim().orEmpty()
    if (reason.isBlank()) return false
    if (snapshotAvailable || isLastAttempt) return true
    return reason in setOf(
        "auth",
        "bad_request",
        "backend_not_configured",
        "image_read_failed",
        "quota",
        "stale_session"
    )
}

@Immutable
internal data class LocalStreamingDraft(
    val messageId: String,
    val content: String,
    val revealBuffer: String,
    val anchoredUserMessageId: String?,
    val savedAtMs: Long,
    val sessionGeneration: Int? = null
)

@Suppress("UNUSED_PARAMETER")
internal fun visibleContentForInterruptedStreamingDraft(content: String, revealBuffer: String): String {
    // The buffer has not been rendered yet; interrupted recovery must not expose it at once.
    return normalizeAssistantText(content)
}

private data class MessageSelectionToolbarState(
    val messageId: String,
    val anchorX: Int,
    val anchorY: Int,
    val selectionBottomY: Int,
    val anchorXRatio: Float,
    val selectionTopRatio: Float,
    val selectionBottomRatio: Float,
    val onCopyRequested: (() -> Unit)?,
    val onCopyFullRequested: (() -> Unit)?
)

private data class PendingMessageSelectionToolbarState(
    val messageId: String,
    val rect: Rect,
    val onCopyRequested: (() -> Unit)?,
    val onCopyFullRequested: (() -> Unit)?
)

private data class PendingInputSelectionToolbarState(
    val rect: Rect,
    val onCopyRequested: (() -> Unit)?,
    val onPasteRequested: (() -> Unit)?,
    val onCutRequested: (() -> Unit)?,
    val onSelectAllRequested: (() -> Unit)?
)

private enum class MessageActionMenuSide { Above, Below }

private object StaticMessageSelectionBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

private const val LOCAL_RENDER_ROUND_LIMIT = 30
private const val CHAT_CACHE_PREFS = "chat_ui_cache"
private const val CHAT_CACHE_KEY_PREFIX = "render_window_"
private const val CHAT_STREAM_DRAFT_KEY_PREFIX = "stream_draft_"
private const val CHAT_COMPOSER_DRAFT_KEY_PREFIX = "composer_draft_"
private const val CHAT_COMPOSER_DRAFT_GENERATION_KEY_PREFIX = "composer_draft_gen_"
private const val TODAY_AGRI_CARD_CACHE_DAY_KEY_PREFIX = "today_agri_card_cache_day_"
private const val TODAY_AGRI_CARD_CACHE_KEY_PREFIX = "today_agri_card_cache_"
private const val TODAY_AGRI_MAIN_SHOWN_DAY_KEY_PREFIX = "today_agri_main_shown_day_"
private const val TODAY_AGRI_CARD_FETCH_RETRY_DELAY_MS = 5_000L
private const val TODAY_AGRI_ITEM_SAVE_MAX_ATTEMPTS = 3
private const val TODAY_AGRI_ITEM_SAVE_RETRY_DELAY_MS = 1_500L
private const val TODAY_AGRI_CARD_DAY_REFRESH_POLL_MS = 15 * 60 * 1000L
private const val UNKNOWN_SESSION_GENERATION = Int.MIN_VALUE
private const val CHAT_STARTUP_DIAG_TAG = "ChatStartup"
private const val JUMP_BUTTON_AUTO_HIDE_MS = 2200L
private const val STREAM_DRAFT_SAVE_DEBOUNCE_MS = 180L
internal const val STREAM_TYPEWRITER_IDLE_POLL_MS = 20L
private const val STREAM_TYPEWRITER_FINISH_DRAIN_POLL_MS = 40L
internal const val STREAM_REVEAL_FRAME_BUDGET_MS = 40L
internal const val STREAM_REVEAL_MAX_TOKENS_PER_BATCH = 1
private const val REMOTE_STREAM_MIN_BALL_MS = 1800L
// Positive scrollOffset pushes a top-to-bottom LazyColumn item upward; the
// large value intentionally relies on LazyList's end clamp to land at bottom.
private const val FORWARD_LIST_BOTTOM_SCROLL_OFFSET = Int.MAX_VALUE / 4
private const val INPUT_MAX_CHARS = 6000
private const val COMPOSER_MAX_IMAGE_COUNT = 4
private const val COMPOSER_IMAGE_COUNT_HINT = "最多4张图片"
private const val COMPOSER_MAX_IMAGE_SIZE_BYTES = 1024 * 1024
private const val COMPOSER_ORIGINAL_IMAGE_MAX_BYTES = 32 * 1024 * 1024
private const val COMPOSER_DIRECT_JPEG_MAX_LONG_EDGE = 1024
private const val INPUT_LIMIT_HINT_MS = 1600L
private const val COMPOSER_STATUS_HINT_MS = 1800L
private const val SCROLL_OFFSET_METRIC_BUCKET_PX = 24
internal const val GPT_BALL_PULSE_MS = 700
private const val GPT_BALL_EXIT_MS = 180
private const val GPT_STREAM_TEXT_ENTRY_MS = 180
private val STREAM_VISIBLE_BOTTOM_GAP = 96.dp
private val BOTTOM_POSITION_TOLERANCE = 16.dp
private val STATIC_BOTTOM_POSITION_TOLERANCE = 0.dp
private val INITIAL_WORKLINE_BOTTOM_SWITCH_OVERFLOW = 56.dp
private val CHAT_MESSAGE_ITEM_VERTICAL_PADDING = 8.dp
private const val BOTTOM_BAR_HEIGHT_JITTER_TOLERANCE_PX = 10
private const val REMOTE_STREAM_RECOVERY_MAX_ATTEMPTS = 10
private const val REMOTE_STREAM_RECOVERY_DELAY_MS = 700L
private const val REMOTE_BACKGROUND_STREAM_RECOVERY_MAX_ATTEMPTS = 240
private const val REMOTE_BACKGROUND_STREAM_RECOVERY_DELAY_MS = 2500L
private const val STREAMING_FINALIZE_BOUNDS_TIMEOUT_MS = 1500L
private val MESSAGE_ACTION_MENU_MARGIN = 8.dp
private val MESSAGE_ACTION_MENU_VERTICAL_SPACING = 16.dp
private val MESSAGE_ACTION_MENU_ESTIMATED_HEIGHT = 44.dp
private val MESSAGE_ACTION_MENU_SWITCH_THRESHOLD = 28.dp
private val JUMP_BUTTON_EXTRA_BOTTOM_CLEARANCE = 32.dp
private val JUMP_BUTTON_BOTTOM_SAFETY_ZONE = 56.dp
private val MESSAGE_SELECTION_HANDLE_MASK_GUARD = 20.dp
private val TOP_CHROME_MASK_EXTRA = 8.dp
private val CHAT_SELECTION_HANDLE_COLOR = Color(0xFF111111)
private val CHAT_SELECTION_BACKGROUND_COLOR = Color(0xFF858B94).copy(alpha = 0.52f)
private val STATIC_SELECTION_CACHE_WINDOW = 6000.dp
private val STARTUP_INPUT_CHROME_ROW_HEIGHT_ESTIMATE = 64.dp
private val STARTUP_BOTTOM_BAR_HEIGHT_ESTIMATE = 72.dp
internal val GPT_BALL_SIZE = 14.dp
internal val GPT_BALL_CONTAINER_SIZE = 24.dp
internal val GPT_BALL_START_PADDING = 0.dp
internal val MARKDOWN_BLOCK_SPACING = 12.dp
internal val SECTION_DIVIDER_GAP = 28.dp
internal val SECTION_DIVIDER_TOP_EXTRA_GAP = 16.dp
private const val APP_TITLE_TEXT = "农技千查"
private const val WELCOME_EMPTY_STATE_TEXT = "欢迎咨询种植\n病虫害防治、施肥等问题\n必要时可上传图片"
internal const val AI_DISCLAIMER_TEXT = "本回答由AI生成，内容仅供参考。"
private const val QUOTA_EXHAUSTED_HINT_TEXT = "今日额度已用完，请明天再试"
private const val NETWORK_UNAVAILABLE_HINT_TEXT = "当前网络不可用"
private const val RATE_LIMIT_HINT_TEXT = "当前请求较多，请稍后重试"
private const val SERVICE_UNAVAILABLE_HINT_TEXT = "服务暂不可用，请稍后再试"
private const val INTERRUPTED_NETWORK_HINT_TEXT = "网络波动，回复未完成"
private const val ACTIVE_STREAM_HINT_TEXT = "上一条还在处理，请稍后重试"
private const val STALE_SESSION_HINT_TEXT = "会话已更新，本次回复未完成"
private const val INTERRUPTED_FALLBACK_HINT_TEXT = "本次回复未完成，请重试"
internal const val CAMERA_OPEN_FAILED_HINT_TEXT = "相机打开失败，请重试"
private const val ASSISTANT_RETRY_STATUS_TEXT = "回复未完成"
private const val ASSISTANT_RETRY_ACTION_TEXT = "重试"
private const val ASSISTANT_RETRYING_STATUS_TEXT = "正在重试..."
private const val ASSISTANT_RETRY_PREVIEW_TEXT = "回复未完成 · 点击重试"
private const val USER_RETRY_STATUS_TEXT = "发送失败"
private const val USER_RETRY_ACTION_TEXT = "重发"
private const val USER_RETRYING_STATUS_TEXT = "正在重发..."
private const val USER_RETRY_PREVIEW_TEXT = "发送失败 · 点击重发"
private const val USER_PENDING_IMAGE_SEND_STATUS_TEXT = "后台发送中 · 稍后自动重试"
private const val USER_PENDING_IMAGE_SEND_PREVIEW_TEXT = "后台发送中 · 稍后自动重试"
private val chatCacheGson = Gson()
private val chatCacheWriteLock = Any()
private val chatCacheListType = object : TypeToken<List<ChatMessage>>() {}.type
private val bareUrlRegex = Regex("(?i)\\b((?:https?://|www\\.)[^\\s<>()]+)")

private fun currentQuotaDayKey(): String {
    val calendar = java.util.Calendar.getInstance(
        java.util.TimeZone.getTimeZone("Asia/Shanghai")
    )
    return "${calendar.get(java.util.Calendar.YEAR)}-${calendar.get(java.util.Calendar.DAY_OF_YEAR)}"
}

private fun currentChinaDateKey(): String {
    val calendar = java.util.Calendar.getInstance(
        java.util.TimeZone.getTimeZone("Asia/Shanghai")
    )
    val year = calendar.get(java.util.Calendar.YEAR)
    val month = calendar.get(java.util.Calendar.MONTH) + 1
    val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
    return "${year.toString().padStart(4, '0')}${month.toString().padStart(2, '0')}${day.toString().padStart(2, '0')}"
}

private fun normalizeTodayAgriCardDayKey(raw: String): String {
    val trimmed = raw.trim()
    val digits = trimmed.filter { it.isDigit() }
    return digits.takeIf { it.length == 8 } ?: trimmed
}

private fun todayAgriCardDayKey(card: SessionApi.TodayAgriCard?): String =
    normalizeTodayAgriCardDayKey(card?.dateCn.orEmpty()).ifBlank { currentChinaDateKey() }

internal fun validTodayAgriMainItemForCurrentDay(
    item: TodayAgriMainItem?,
    currentDayKey: String
): TodayAgriMainItem? {
    val normalizedCurrentDay = normalizeTodayAgriCardDayKey(currentDayKey)
    return item
        ?.takeIf { normalizedCurrentDay.isNotBlank() }
        ?.takeIf { normalizeTodayAgriCardDayKey(it.day_cn) == normalizedCurrentDay }
        ?.takeIf { it.anchor_client_msg_id.isNotBlank() }
        ?.takeIf { it.card.isRenderableTodayAgriCard() }
        ?.takeIf { normalizeTodayAgriCardDayKey(it.card.dateCn.orEmpty()) == normalizedCurrentDay }
}

private fun normalizeAssistantText(content: String): String {
    return content
        .replace("\r\n", "\n")
        .trim()
}

private fun markdownInlineSpanStyle(
    isBold: Boolean,
    isItalic: Boolean,
    isCode: Boolean
): SpanStyle {
    return SpanStyle(
        fontWeight = if (isBold) FontWeight.SemiBold else null,
        fontStyle = if (isItalic) FontStyle.Italic else null,
        fontFamily = if (isCode) FontFamily.Monospace else null,
        background = if (isCode) Color(0xFFF2F3F5) else Color.Unspecified
    )
}

private fun chatLinkSpanStyle(
    isBold: Boolean = false,
    isItalic: Boolean = false,
    isCode: Boolean = false
): SpanStyle {
    return markdownInlineSpanStyle(
        isBold = isBold,
        isItalic = isItalic,
        isCode = isCode
    ).copy(
        color = Color(0xFF111111),
        textDecoration = TextDecoration.Underline
    )
}

private fun normalizeLinkTarget(raw: String): String {
    val trimmed = raw.trim().removePrefix("<").removeSuffix(">")
    if (trimmed.isBlank()) return raw.trim()
    return if (
        trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        trimmed
    } else {
        "https://$trimmed"
    }
}

private fun trimBareUrlDisplayText(raw: String): String {
    val trailingPunctuation = ".,;:!?，。；：！？)]}）】》」』”\"'"
    return raw.trimEnd { it in trailingPunctuation }
}

private fun buildPlainLinkedAnnotatedString(
    text: String,
    linkColor: Color = Color(0xFF111111)
): AnnotatedString {
    return buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            val bareUrl = bareUrlRegex.find(text, index)
            if (bareUrl == null) {
                append(text.substring(index))
                break
            }
            if (bareUrl.range.first > index) {
                append(text.substring(index, bareUrl.range.first))
            }
            val displayText = trimBareUrlDisplayText(bareUrl.value)
            if (displayText.isEmpty()) {
                append(bareUrl.value)
                index = bareUrl.range.last + 1
                continue
            }
            withLink(LinkAnnotation.Url(normalizeLinkTarget(displayText))) {
                withStyle(chatLinkSpanStyle().copy(color = linkColor)) {
                    append(displayText)
                }
            }
            index = bareUrl.range.first + displayText.length
        }
    }
}

private fun buildRenderedMessageCopyText(role: ChatRole, content: String): String {
    return when (role) {
        ChatRole.USER -> content.trim()
        ChatRole.ASSISTANT -> buildRendererPlainCopyText(content)
    }
}

private fun Rect.containsPoint(offset: Offset): Boolean {
    return offset.x >= left && offset.x <= right && offset.y >= top && offset.y <= bottom
}

private suspend fun AwaitPointerEventScope.waitForUpIgnoringConsumption(
    pass: PointerEventPass
): PointerInputChange? {
    while (true) {
        val event = awaitPointerEvent(pass)
        event.changes.firstOrNull { it.changedToUpIgnoreConsumed() }?.let { return it }
        if (event.changes.none { it.pressed }) {
            return null
        }
    }
}

internal fun assistantParagraphTextStyle(): TextStyle = TextStyle(
    fontSize = 17.sp,
    lineHeight = 28.sp,
    letterSpacing = 0.05.sp,
    color = Color(0xFF171717),
    textMotion = TextMotion.Static,
    lineBreak = LineBreak.Paragraph
)

internal fun assistantStreamingParagraphTextStyle(): TextStyle =
    assistantParagraphTextStyle().copy(
        lineHeight = 30.sp,
        lineBreak = LineBreak.Simple
    )

internal fun assistantDisclaimerTextStyle(): TextStyle = TextStyle(
    fontSize = 14.sp,
    lineHeight = 20.sp,
    color = Color(0xFF8D929A),
    letterSpacing = 0.sp,
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    textMotion = TextMotion.Static
)

private fun shouldShowAiDisclaimer(content: String): Boolean {
    if (content.isBlank()) return false
    val normalized = content.lowercase()
    val guidanceKeywords = listOf(
        "用药", "施药", "农药", "杀菌剂", "杀虫剂", "杀螨", "施肥", "追肥", "叶面肥",
        "冲施", "滴灌", "灌根", "喷施", "喷雾", "喷药", "剂量", "用量", "倍数", "浓度", "稀释",
        "稀释倍数", "配比", "配方", "混配", "复配", "安全期", "间隔期", "残留", "采收期", "停药期",
        "防治", "防控", "处理方案"
    )
    if (guidanceKeywords.any { normalized.contains(it) }) return true
    val dosageKeywords = listOf(
        "每亩", "亩用", "兑水", "稀释", "二次稀释", "倍液", "ppm"
    )
    if (dosageKeywords.any { normalized.contains(it) }) return true
    val dosageRegexes = listOf(
        Regex("\\d+(\\.\\d+)?\\s*(克|g|公斤|kg)\\s*/\\s*(亩|升|l)"),
        Regex("\\d+(\\.\\d+)?\\s*(毫升|ml|升|l)\\s*/\\s*(亩|升|l)"),
        Regex("\\d+(\\.\\d+)?\\s*(克|g|毫升|ml|升|l|公斤|kg)\\s*(每亩|/亩)"),
        Regex("\\d+(\\.\\d+)?\\s*(克|g|毫升|ml|升|l)\\s*兑\\s*\\d+(\\.\\d+)?\\s*(升|l)?\\s*水"),
        Regex("\\d+(\\.\\d+)?\\s*ppm"),
        Regex("\\d+(\\.\\d+)?\\s*倍液")
    )
    return dosageRegexes.any { it.containsMatchIn(normalized) }
}

private fun containsDisclaimerSensitiveAssistant(messages: List<ChatMessage>): Boolean {
    return messages.any { it.role == ChatRole.ASSISTANT && shouldShowAiDisclaimerRefined(it.content) }
}

internal fun shouldShowAiDisclaimerRefined(content: String): Boolean {
    if (content.isBlank()) return false
    val normalized = content.lowercase()
    val actionKeywords = listOf(
        "用药", "施药", "喷药", "喷施", "喷雾",
        "灌根", "滴灌", "冲施", "施肥", "追肥"
    )
    val treatmentMaterialKeywords = listOf(
        "农药", "药剂", "杀菌剂", "杀虫剂", "杀螨剂",
        "除草剂", "叶面肥", "水溶肥", "冲施肥"
    )
    val dosageKeywords = listOf(
        "剂量", "用量", "浓度", "稀释", "倍数", "倍液",
        "配比", "配方", "ppm", "兑水", "每亩",
        "间隔期", "安全期", "停药期", "采收期"
    )
    val dosageRegexes = listOf(
        Regex("\\d+(\\.\\d+)?\\s*(克|kg|公斤)\\s*/\\s*(亩|升|l)"),
        Regex("\\d+(\\.\\d+)?\\s*(毫升|ml|升|l)\\s*/\\s*(亩|升|l)"),
        Regex("\\d+(\\.\\d+)?\\s*(克|毫升|ml|升|l|公斤|kg)\\s*(每亩|/亩)"),
        Regex("\\d+(\\.\\d+)?\\s*(克|毫升|ml|升|l)\\s*兑\\s*\\d+(\\.\\d+)?\\s*(升|l)?\\s*水"),
        Regex("\\d+(\\.\\d+)?\\s*ppm"),
        Regex("\\d+(\\.\\d+)?\\s*倍液")
    )
    if (dosageRegexes.any { it.containsMatchIn(normalized) }) return true
    val hasAction = actionKeywords.any { normalized.contains(it) }
    val hasTreatmentMaterial = treatmentMaterialKeywords.any { normalized.contains(it) }
    val hasDosage = dosageKeywords.any { normalized.contains(it) }
    return (hasAction && hasDosage) || (hasAction && hasTreatmentMaterial)
}

internal fun assistantHeadingTextStyle(level: Int): TextStyle = TextStyle(
    fontSize = if (level <= 2) 20.sp else 18.sp,
    lineHeight = if (level <= 2) 31.sp else 28.sp,
    fontWeight = FontWeight.Bold,
    color = Color(0xFF111111),
    textMotion = TextMotion.Static,
    lineBreak = LineBreak.Heading
)

internal fun assistantStreamingHeadingTextStyle(level: Int): TextStyle =
    assistantHeadingTextStyle(level).copy(lineBreak = LineBreak.Simple)

private fun trimWindowStartIndex(source: List<ChatMessage>): Int {
    var userCount = 0
    source.forEach { if (it.role == ChatRole.USER) userCount++ }
    if (userCount <= LOCAL_RENDER_ROUND_LIMIT) return 0
    var keepFromUser = userCount - LOCAL_RENDER_ROUND_LIMIT
    source.forEachIndexed { index, message ->
        if (message.role == ChatRole.USER) {
            if (keepFromUser == 0) return index
            keepFromUser--
        }
    }
    return 0
}

private fun trimMessageWindow(source: List<ChatMessage>): List<ChatMessage> {
    val startIndex = trimWindowStartIndex(source)
    if (startIndex <= 0) return source
    return source.drop(startIndex)
}

private fun dedupeAdjacentMessages(source: List<ChatMessage>): List<ChatMessage> {
    if (source.size < 2) return source
    val deduped = ArrayList<ChatMessage>(source.size)
    source.forEach { message ->
        val previous = deduped.lastOrNull()
        val sameContent = when (message.role) {
            ChatRole.USER ->
                previous?.content == message.content &&
                    previous.imageUris.orEmpty() == message.imageUris.orEmpty() &&
                    previous.imageUrls.orEmpty() == message.imageUrls.orEmpty() &&
                    previous.todayAgriContextDay == message.todayAgriContextDay
            ChatRole.ASSISTANT -> previous?.content?.let(::normalizeAssistantText) == normalizeAssistantText(message.content)
        }
        if (previous?.role == message.role && sameContent) return@forEach
        deduped.add(message)
    }
    return deduped
}

private fun sanitizeMessageWindow(source: List<ChatMessage>): List<ChatMessage> {
    return dedupeAdjacentMessages(trimMessageWindow(source))
}

private fun sanitizeLocalChatWindowSnapshot(snapshot: LocalChatWindowSnapshot): LocalChatWindowSnapshot {
    val trimmedMessages = sanitizeMessageWindow(snapshot.messages)
    val persistedMessageIds = trimmedMessages
        .mapTo(mutableSetOf()) { it.id }
        .filter(String::isNotBlank)
        .toSet()
    val failedUserStates = snapshot.failedUserMessageStates
        .filter { (messageId, reason) ->
            messageId.isNotBlank() && reason.isNotBlank() && messageId in persistedMessageIds
        }
    val failedAssistantStates = snapshot.failedAssistantMessageStates
        .filter { (messageId, state) ->
            messageId.isNotBlank() &&
                state.sourceUserMessageId.isNotBlank() &&
                messageId in persistedMessageIds &&
                state.sourceUserMessageId in persistedMessageIds
        }
    return LocalChatWindowSnapshot(
        messages = trimmedMessages,
        failedUserMessageStates = failedUserStates,
        failedAssistantMessageStates = failedAssistantStates,
        initialWorklineOwned = snapshot.initialWorklineOwned.takeIf {
            trimmedMessages.isNotEmpty()
        }
    )
}

private fun normalizeLocalChatWindowSnapshot(
    payload: LocalChatWindowSnapshotPayload?
): LocalChatWindowSnapshot =
    sanitizeLocalChatWindowSnapshot(
        LocalChatWindowSnapshot(
            messages = payload?.messages.orEmpty(),
            failedUserMessageStates = payload?.failedUserMessageStates.orEmpty(),
            failedAssistantMessageStates = payload?.failedAssistantMessageStates.orEmpty(),
            initialWorklineOwned = payload?.initialWorklineOwned
        )
    )

private fun isStoredSessionGenerationCurrent(storedGeneration: Int?): Boolean {
    val currentGeneration = SessionApi.currentSessionGenerationOrNull() ?: return true
    return storedGeneration == currentGeneration
}

private fun mergeHydratedMessagesWithLocalPendingState(
    remoteMessages: List<ChatMessage>,
    localSnapshot: LocalChatWindowSnapshot,
    shouldKeepPendingUserMessage: (String) -> Boolean = { false }
): LocalChatWindowSnapshot {
    val mergedMessages = sanitizeMessageWindow(remoteMessages).toMutableList()
    val localFailedSnapshot = markLocalImageUploadPendingAsFailed(
        snapshot = localSnapshot,
        shouldKeepPending = shouldKeepPendingUserMessage
    )
    val existingIds = mergedMessages.mapTo(mutableSetOf()) { it.id }
    val retainedFailedUserStates = mutableMapOf<String, String>()
    val retainedFailedAssistantStates = mutableMapOf<String, FailedAssistantMessageState>()
    localFailedSnapshot.messages.forEach { localMessage ->
        if (
            localMessage.isLocalImageUploadPendingUserMessage() &&
            shouldKeepPendingUserMessage(localMessage.id)
        ) {
            if (existingIds.add(localMessage.id)) {
                mergedMessages.add(localMessage)
            }
            return@forEach
        }
        val failedUserReason = localFailedSnapshot.failedUserMessageStates[localMessage.id]
        if (failedUserReason != null) {
            if (existingIds.add(localMessage.id)) {
                mergedMessages.add(localMessage)
                retainedFailedUserStates[localMessage.id] = failedUserReason
            }
            return@forEach
        }
        val failedAssistantState = localFailedSnapshot.failedAssistantMessageStates[localMessage.id] ?: return@forEach
        if (!existingIds.add(localMessage.id)) return@forEach
        val sourceUserIndex = mergedMessages.indexOfFirst { it.id == failedAssistantState.sourceUserMessageId }
        if (sourceUserIndex >= 0) {
            mergedMessages.add(sourceUserIndex + 1, localMessage)
        } else {
            mergedMessages.add(localMessage)
        }
        retainedFailedAssistantStates[localMessage.id] = failedAssistantState
    }
    val recoverableUserMessageId = trailingRecoverableUserMessageId(
        source = localFailedSnapshot.messages,
        ignoredUserMessageIds = localFailedSnapshot.failedUserMessageStates.keys
    )
    val recoverableUserMessage = recoverableUserMessageId
        ?.let { messageId ->
            localFailedSnapshot.messages.firstOrNull { message ->
                message.id == messageId && message.role == ChatRole.USER
            }
        }
    val hasRecoveredAssistant = recoverableUserMessageId
        ?.let { sourceUserMessageId ->
            mergedMessages.any { message ->
                message.id == assistantMessageIdForSourceUser(sourceUserMessageId) &&
                    message.role == ChatRole.ASSISTANT &&
                    message.content.isNotBlank()
            }
        } == true
    if (recoverableUserMessage != null && !hasRecoveredAssistant && existingIds.add(recoverableUserMessage.id)) {
        mergedMessages.add(recoverableUserMessage)
    }
    return sanitizeLocalChatWindowSnapshot(
        LocalChatWindowSnapshot(
            messages = mergedMessages,
            failedUserMessageStates = retainedFailedUserStates,
            failedAssistantMessageStates = retainedFailedAssistantStates,
            initialWorklineOwned = localSnapshot.initialWorklineOwned
        )
    )
}

private fun retainLocalRecoverySnapshotForRemoteFallback(
    localSnapshot: LocalChatWindowSnapshot,
    shouldKeepPendingUserMessage: (String) -> Boolean
): LocalChatWindowSnapshot {
    val localFailedSnapshot = markLocalImageUploadPendingAsFailed(
        snapshot = localSnapshot,
        shouldKeepPending = shouldKeepPendingUserMessage
    )
    val retainedMessageIds = linkedSetOf<String>()
    localFailedSnapshot.messages.forEach { message ->
        if (
            message.isLocalImageUploadPendingUserMessage() &&
            shouldKeepPendingUserMessage(message.id)
        ) {
            retainedMessageIds.add(message.id)
            return@forEach
        }
        if (message.id in localFailedSnapshot.failedUserMessageStates) {
            retainedMessageIds.add(message.id)
        }
    }
    localFailedSnapshot.failedAssistantMessageStates.forEach { (assistantMessageId, state) ->
        retainedMessageIds.add(state.sourceUserMessageId)
        retainedMessageIds.add(assistantMessageId)
    }
    val retainedMessages = localFailedSnapshot.messages.filter { message ->
        message.id in retainedMessageIds
    }
    val retainedFailedUserStates = localFailedSnapshot.failedUserMessageStates.filterKeys { messageId ->
        messageId in retainedMessageIds
    }
    val retainedFailedAssistantStates = localFailedSnapshot.failedAssistantMessageStates.filter { (messageId, state) ->
        messageId in retainedMessageIds && state.sourceUserMessageId in retainedMessageIds
    }
    return sanitizeLocalChatWindowSnapshot(
        LocalChatWindowSnapshot(
            messages = retainedMessages,
            failedUserMessageStates = retainedFailedUserStates,
            failedAssistantMessageStates = retainedFailedAssistantStates,
            initialWorklineOwned = localSnapshot.initialWorklineOwned
        )
    )
}

internal fun recoverStreamingDraftAsInterruptedSnapshot(
    localSnapshot: LocalChatWindowSnapshot,
    draft: LocalStreamingDraft?
): LocalChatWindowSnapshot {
    val sanitizedSnapshot = sanitizeLocalChatWindowSnapshot(localSnapshot)
    val safeDraft = draft ?: return sanitizedSnapshot
    val sourceUserMessageId = safeDraft.anchoredUserMessageId
        ?.takeIf { it.isNotBlank() }
        ?: return sanitizedSnapshot
    if (
        sanitizedSnapshot.messages.none { message ->
            message.id == sourceUserMessageId && message.role == ChatRole.USER
        }
    ) {
        return sanitizedSnapshot
    }
    val partialContent = visibleContentForInterruptedStreamingDraft(
        content = safeDraft.content,
        revealBuffer = safeDraft.revealBuffer
    )
    val assistantMessageId = safeDraft.messageId
        .takeIf { it.isNotBlank() }
        ?: assistantMessageIdForSourceUser(sourceUserMessageId)
    val recoveredMessages = ArrayList<ChatMessage>(sanitizedSnapshot.messages.size + 1)
    var inserted = false
    sanitizedSnapshot.messages.forEach { message ->
        if (message.id == assistantMessageId) {
            if (!inserted) {
                recoveredMessages.add(
                    ChatMessage(
                        id = assistantMessageId,
                        role = ChatRole.ASSISTANT,
                        content = partialContent
                    )
                )
                inserted = true
            }
            return@forEach
        }
        recoveredMessages.add(message)
        if (
            !inserted &&
            message.id == sourceUserMessageId &&
            message.role == ChatRole.USER
        ) {
            recoveredMessages.add(
                ChatMessage(
                    id = assistantMessageId,
                    role = ChatRole.ASSISTANT,
                    content = partialContent
                )
            )
            inserted = true
        }
    }
    if (!inserted) return sanitizedSnapshot
    val recoveredFailedAssistantStates = sanitizedSnapshot.failedAssistantMessageStates.toMutableMap()
    recoveredFailedAssistantStates[assistantMessageId] = FailedAssistantMessageState(
        sourceUserMessageId = sourceUserMessageId
    )
    return sanitizeLocalChatWindowSnapshot(
        sanitizedSnapshot.copy(
            messages = recoveredMessages,
            failedAssistantMessageStates = recoveredFailedAssistantStates
        )
    )
}

private fun appendCompletedAssistantMessage(
    source: List<ChatMessage>,
    messageId: String,
    content: String
): List<ChatMessage> {
    val normalizedContent = normalizeAssistantText(content)
    if (normalizedContent.isBlank()) return sanitizeMessageWindow(source)
    val finalId = messageId.ifBlank { "assistant_${UUID.randomUUID()}" }
    val merged = ArrayList<ChatMessage>(source.size + 1)
    var inserted = false
    source.forEach { message ->
        if (message.id == finalId) {
            if (!inserted) {
                merged.add(ChatMessage(finalId, ChatRole.ASSISTANT, normalizedContent))
                inserted = true
            }
            return@forEach
        }
        merged.add(message)
    }
    if (!inserted) {
        merged.add(ChatMessage(finalId, ChatRole.ASSISTANT, normalizedContent))
    }
    return sanitizeMessageWindow(merged)
}

private fun applyCompletedAssistantMessageInPlace(
    target: MutableList<ChatMessage>,
    messageId: String,
    content: String
) {
    val normalizedContent = normalizeAssistantText(content)
    if (normalizedContent.isBlank()) return
    val finalId = messageId.ifBlank { "assistant_${UUID.randomUUID()}" }
    val finalMessage = ChatMessage(finalId, ChatRole.ASSISTANT, normalizedContent)
    val existingIndex = target.indexOfFirst { it.id == finalId }
    if (existingIndex >= 0) {
        if (target[existingIndex] != finalMessage) {
            target[existingIndex] = finalMessage
        }
    } else {
        target.add(finalMessage)
    }
    val trimCount = trimWindowStartIndex(target)
    if (trimCount > 0) {
        repeat(trimCount) { target.removeAt(0) }
    }
}

private suspend fun Context.loadLocalChatWindow(chatScopeId: String): LocalChatWindowSnapshot = withContext(Dispatchers.IO) {
    val appContext = applicationContext
    val raw = getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE)
        .getString("$CHAT_CACHE_KEY_PREFIX$chatScopeId", null)
        .orEmpty()
    if (raw.isBlank()) return@withContext LocalChatWindowSnapshot()
    runCatching {
        if (raw.trimStart().startsWith("[")) {
            if (!isStoredSessionGenerationCurrent(null)) return@runCatching LocalChatWindowSnapshot()
            @Suppress("UNCHECKED_CAST")
            markLocalImageUploadPendingAsFailed(
                snapshot = LocalChatWindowSnapshot(
                    messages = chatCacheGson.fromJson<List<ChatMessage>>(raw, chatCacheListType) ?: emptyList()
                ),
                shouldKeepPending = { messageId ->
                    PendingChatSendStore.has(appContext, chatScopeId, messageId) ||
                        PendingChatSendStore.hasRemoteCompletionAwaitingSnapshot(
                            appContext,
                            chatScopeId,
                            messageId
                        )
                }
            )
        } else {
            val payload = chatCacheGson.fromJson(raw, LocalChatWindowSnapshotPayload::class.java)
            if (!isStoredSessionGenerationCurrent(payload?.sessionGeneration)) {
                return@runCatching LocalChatWindowSnapshot()
            }
            markLocalImageUploadPendingAsFailed(
                snapshot = normalizeLocalChatWindowSnapshot(payload),
                shouldKeepPending = { messageId ->
                    PendingChatSendStore.has(appContext, chatScopeId, messageId) ||
                        PendingChatSendStore.hasRemoteCompletionAwaitingSnapshot(
                            appContext,
                            chatScopeId,
                            messageId
                        )
                }
            )
        }
    }.getOrDefault(LocalChatWindowSnapshot())
}

private fun Context.loadLocalChatWindowSync(chatScopeId: String): LocalChatWindowSnapshot {
    val appContext = applicationContext
    val raw = getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE)
        .getString("$CHAT_CACHE_KEY_PREFIX$chatScopeId", null)
        .orEmpty()
    if (raw.isBlank()) return LocalChatWindowSnapshot()
    return runCatching {
        if (raw.trimStart().startsWith("[")) {
            if (!isStoredSessionGenerationCurrent(null)) return@runCatching LocalChatWindowSnapshot()
            @Suppress("UNCHECKED_CAST")
            markLocalImageUploadPendingAsFailed(
                snapshot = LocalChatWindowSnapshot(
                    messages = chatCacheGson.fromJson<List<ChatMessage>>(raw, chatCacheListType) ?: emptyList()
                ),
                shouldKeepPending = { messageId ->
                    PendingChatSendStore.has(appContext, chatScopeId, messageId) ||
                        PendingChatSendStore.hasRemoteCompletionAwaitingSnapshot(
                            appContext,
                            chatScopeId,
                            messageId
                        )
                }
            )
        } else {
            val payload = chatCacheGson.fromJson(raw, LocalChatWindowSnapshotPayload::class.java)
            if (!isStoredSessionGenerationCurrent(payload?.sessionGeneration)) {
                return@runCatching LocalChatWindowSnapshot()
            }
            markLocalImageUploadPendingAsFailed(
                snapshot = normalizeLocalChatWindowSnapshot(payload),
                shouldKeepPending = { messageId ->
                    PendingChatSendStore.has(appContext, chatScopeId, messageId) ||
                        PendingChatSendStore.hasRemoteCompletionAwaitingSnapshot(
                            appContext,
                            chatScopeId,
                            messageId
                        )
                }
            )
        }
    }.getOrDefault(LocalChatWindowSnapshot())
}

private suspend fun Context.saveLocalChatWindowIfEpoch(
    chatScopeId: String,
    snapshot: LocalChatWindowSnapshot,
    expectedEpoch: Int,
    epochRef: AtomicInteger
) = withContext(Dispatchers.IO) {
    val sanitizedSnapshot = sanitizeLocalChatWindowSnapshot(snapshot)
    val payload = LocalChatWindowSnapshotPayload(
        messages = sanitizedSnapshot.messages,
        failedUserMessageStates = sanitizedSnapshot.failedUserMessageStates,
        failedAssistantMessageStates = sanitizedSnapshot.failedAssistantMessageStates,
        initialWorklineOwned = sanitizedSnapshot.initialWorklineOwned,
        sessionGeneration = SessionApi.currentSessionGenerationOrNull()
    )
    synchronized(chatCacheWriteLock) {
        if (epochRef.get() != expectedEpoch) return@synchronized
        getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("$CHAT_CACHE_KEY_PREFIX$chatScopeId", chatCacheGson.toJson(payload))
            .commit()
    }
}

private fun Context.saveLocalChatWindowSync(chatScopeId: String, snapshot: LocalChatWindowSnapshot) {
    val sanitizedSnapshot = sanitizeLocalChatWindowSnapshot(snapshot)
    val payload = LocalChatWindowSnapshotPayload(
        messages = sanitizedSnapshot.messages,
        failedUserMessageStates = sanitizedSnapshot.failedUserMessageStates,
        failedAssistantMessageStates = sanitizedSnapshot.failedAssistantMessageStates,
        initialWorklineOwned = sanitizedSnapshot.initialWorklineOwned,
        sessionGeneration = SessionApi.currentSessionGenerationOrNull()
    )
    synchronized(chatCacheWriteLock) {
        getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("$CHAT_CACHE_KEY_PREFIX$chatScopeId", chatCacheGson.toJson(payload))
            .commit()
    }
}

private suspend fun Context.clearLocalChatHistoryState(chatScopeId: String) = withContext(Dispatchers.IO) {
    clearLocalChatHistoryStateSync(chatScopeId)
}

private fun Context.clearLocalChatHistoryStateSync(chatScopeId: String) {
    synchronized(chatCacheWriteLock) {
        getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove("$CHAT_CACHE_KEY_PREFIX$chatScopeId")
            .remove("$CHAT_STREAM_DRAFT_KEY_PREFIX$chatScopeId")
            .remove("$CHAT_COMPOSER_DRAFT_KEY_PREFIX$chatScopeId")
            .remove("$CHAT_COMPOSER_DRAFT_GENERATION_KEY_PREFIX$chatScopeId")
            .remove("$TODAY_AGRI_CARD_CACHE_DAY_KEY_PREFIX$chatScopeId")
            .remove("$TODAY_AGRI_CARD_CACHE_KEY_PREFIX$chatScopeId")
            .remove("$TODAY_AGRI_MAIN_SHOWN_DAY_KEY_PREFIX$chatScopeId")
            .commit()
    }
}

private fun Context.loadTodayAgriMainShownDaySync(chatScopeId: String): String =
    normalizeTodayAgriCardDayKey(
        getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE)
            .getString("$TODAY_AGRI_MAIN_SHOWN_DAY_KEY_PREFIX$chatScopeId", null)
            .orEmpty()
    )

private fun Context.saveTodayAgriMainShownDaySync(
    chatScopeId: String,
    dayKey: String
) {
    val normalizedDay = normalizeTodayAgriCardDayKey(dayKey)
    if (normalizedDay.isBlank()) return
    synchronized(chatCacheWriteLock) {
        getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("$TODAY_AGRI_MAIN_SHOWN_DAY_KEY_PREFIX$chatScopeId", normalizedDay)
            .commit()
    }
}

internal fun todayAgriMainShownDayAfterHistoryClear(): String = ""

private fun Context.loadTodayAgriCardCacheSync(chatScopeId: String): SessionApi.TodayAgriCard? {
    val prefs = getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE)
    val currentDay = currentChinaDateKey()
    val cachedDay = normalizeTodayAgriCardDayKey(
        prefs.getString("$TODAY_AGRI_CARD_CACHE_DAY_KEY_PREFIX$chatScopeId", null).orEmpty()
    )
    if (cachedDay != currentDay) return null
    val raw = prefs.getString("$TODAY_AGRI_CARD_CACHE_KEY_PREFIX$chatScopeId", null).orEmpty()
    if (raw.isBlank()) return null
    return runCatching {
        chatCacheGson.fromJson(raw, SessionApi.TodayAgriCard::class.java)
    }.getOrNull()
        ?.takeIf { card -> card.isRenderableTodayAgriCard() }
        ?.takeIf { card ->
            normalizeTodayAgriCardDayKey(card.dateCn.orEmpty()).ifBlank { cachedDay } == currentDay
        }
}

private fun Context.saveTodayAgriCardCacheSync(
    chatScopeId: String,
    card: SessionApi.TodayAgriCard
) {
    if (!card.isRenderableTodayAgriCard()) return
    val dayKey = todayAgriCardDayKey(card)
    if (dayKey.isBlank()) return
    synchronized(chatCacheWriteLock) {
        getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("$TODAY_AGRI_CARD_CACHE_DAY_KEY_PREFIX$chatScopeId", dayKey)
            .putString("$TODAY_AGRI_CARD_CACHE_KEY_PREFIX$chatScopeId", chatCacheGson.toJson(card))
            .commit()
    }
}

private fun Context.loadLocalStreamingDraftSync(chatScopeId: String): LocalStreamingDraft? {
    val raw = getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE)
        .getString("$CHAT_STREAM_DRAFT_KEY_PREFIX$chatScopeId", null)
        .orEmpty()
    if (raw.isBlank()) return null
    return runCatching {
        chatCacheGson.fromJson(raw, LocalStreamingDraft::class.java)
    }.getOrNull()?.takeIf { draft ->
        isStoredSessionGenerationCurrent(draft.sessionGeneration)
    }
}

private suspend fun Context.loadLocalStreamingDraft(chatScopeId: String): LocalStreamingDraft? =
    withContext(Dispatchers.IO) {
        loadLocalStreamingDraftSync(chatScopeId)
    }

private suspend fun Context.saveLocalStreamingDraftIfEpoch(
    chatScopeId: String,
    draft: LocalStreamingDraft,
    expectedEpoch: Int,
    epochRef: AtomicInteger
) = withContext(Dispatchers.IO) {
    synchronized(chatCacheWriteLock) {
        if (epochRef.get() != expectedEpoch) return@synchronized
        getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(
                "$CHAT_STREAM_DRAFT_KEY_PREFIX$chatScopeId",
                chatCacheGson.toJson(draft.copy(sessionGeneration = SessionApi.currentSessionGenerationOrNull()))
            )
            .commit()
    }
}

private fun Context.saveLocalStreamingDraftSync(chatScopeId: String, draft: LocalStreamingDraft) {
    synchronized(chatCacheWriteLock) {
        getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(
                "$CHAT_STREAM_DRAFT_KEY_PREFIX$chatScopeId",
                chatCacheGson.toJson(draft.copy(sessionGeneration = SessionApi.currentSessionGenerationOrNull()))
            )
            .commit()
    }
}

private fun Context.clearLocalStreamingDraftSync(chatScopeId: String) {
    synchronized(chatCacheWriteLock) {
        getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove("$CHAT_STREAM_DRAFT_KEY_PREFIX$chatScopeId")
            .commit()
    }
}

private suspend fun Context.clearLocalStreamingDraft(chatScopeId: String) = withContext(Dispatchers.IO) {
    synchronized(chatCacheWriteLock) {
        getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove("$CHAT_STREAM_DRAFT_KEY_PREFIX$chatScopeId")
            .commit()
    }
}

private fun Context.loadLocalComposerDraftSync(chatScopeId: String): String {
    val prefs = getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE)
    val storedGeneration = prefs.getInt(
        "$CHAT_COMPOSER_DRAFT_GENERATION_KEY_PREFIX$chatScopeId",
        UNKNOWN_SESSION_GENERATION
    ).takeIf { it != UNKNOWN_SESSION_GENERATION }
    if (!isStoredSessionGenerationCurrent(storedGeneration)) return ""
    return prefs.getString("$CHAT_COMPOSER_DRAFT_KEY_PREFIX$chatScopeId", null).orEmpty()
}

private suspend fun Context.saveLocalComposerDraftIfEpoch(
    chatScopeId: String,
    text: String,
    expectedEpoch: Int,
    epochRef: AtomicInteger
) = withContext(Dispatchers.IO) {
    synchronized(chatCacheWriteLock) {
        if (epochRef.get() != expectedEpoch) return@synchronized
        val editor = getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE).edit()
        if (text.isBlank()) {
            editor.remove("$CHAT_COMPOSER_DRAFT_KEY_PREFIX$chatScopeId")
            editor.remove("$CHAT_COMPOSER_DRAFT_GENERATION_KEY_PREFIX$chatScopeId")
        } else {
            editor.putString("$CHAT_COMPOSER_DRAFT_KEY_PREFIX$chatScopeId", text)
            val generation = SessionApi.currentSessionGenerationOrNull()
            if (generation == null) {
                editor.remove("$CHAT_COMPOSER_DRAFT_GENERATION_KEY_PREFIX$chatScopeId")
            } else {
                editor.putInt("$CHAT_COMPOSER_DRAFT_GENERATION_KEY_PREFIX$chatScopeId", generation)
            }
        }
        editor.commit()
    }
}

private fun Context.saveLocalComposerDraftSync(chatScopeId: String, text: String) {
    synchronized(chatCacheWriteLock) {
        val editor = getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE).edit()
        if (text.isBlank()) {
            editor.remove("$CHAT_COMPOSER_DRAFT_KEY_PREFIX$chatScopeId")
            editor.remove("$CHAT_COMPOSER_DRAFT_GENERATION_KEY_PREFIX$chatScopeId")
        } else {
            editor.putString("$CHAT_COMPOSER_DRAFT_KEY_PREFIX$chatScopeId", text)
            val generation = SessionApi.currentSessionGenerationOrNull()
            if (generation == null) {
                editor.remove("$CHAT_COMPOSER_DRAFT_GENERATION_KEY_PREFIX$chatScopeId")
            } else {
                editor.putInt("$CHAT_COMPOSER_DRAFT_GENERATION_KEY_PREFIX$chatScopeId", generation)
            }
        }
        editor.commit()
    }
}

private fun Context.clearLocalComposerDraftSync(chatScopeId: String) {
    synchronized(chatCacheWriteLock) {
        getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove("$CHAT_COMPOSER_DRAFT_KEY_PREFIX$chatScopeId")
            .remove("$CHAT_COMPOSER_DRAFT_GENERATION_KEY_PREFIX$chatScopeId")
            .commit()
    }
}

internal fun Context.hasActiveNetworkConnection(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    val hasInternetCapability = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    val hasValidatedConnection = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    val isCaptivePortal = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
    return hasInternetCapability && hasValidatedConnection && !isCaptivePortal
}

internal fun Context.readImageBytes(uri: Uri): ByteArray? {
    return runCatching {
        if (uri.scheme == "file") {
            val path = uri.path ?: return@runCatching null
            val file = File(path).takeIf { it.isFile } ?: return@runCatching null
            if (file.length() > COMPOSER_ORIGINAL_IMAGE_MAX_BYTES) return@runCatching null
            file.inputStream().use { it.readPreviewBytes(COMPOSER_ORIGINAL_IMAGE_MAX_BYTES) }
        } else {
            contentResolver.openInputStream(uri)?.use {
                it.readPreviewBytes(COMPOSER_ORIGINAL_IMAGE_MAX_BYTES)
            }
        }
    }.getOrNull()
}

internal fun Context.importComposerImageToPrivateStorage(uri: Uri): ComposerImageAttachment? {
    return runCatching {
        val originalBytes = readImageBytes(uri) ?: return@runCatching null
        val uploadBytes = if (originalBytes.canUseOriginalJpegForComposerUpload()) {
            originalBytes
        } else {
            ImageUploader.compressImage(originalBytes)?.bytes ?: return@runCatching null
        }
        val imageDir = File(filesDir, "composer_images").apply { mkdirs() }
        val imageFile = File(imageDir, "composer_${UUID.randomUUID()}.jpg")
        imageFile.writeBytes(uploadBytes)
        ComposerImageAttachment(imageFile.toURI().toString())
    }.getOrNull()
}

internal fun ByteArray.hasJpegStartMarker(): Boolean =
    size >= 2 && this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte()

private fun ByteArray.canUseOriginalJpegForComposerUpload(): Boolean {
    if (!hasJpegStartMarker() || size > COMPOSER_MAX_IMAGE_SIZE_BYTES) return false
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(this, 0, size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
    if (maxOf(bounds.outWidth, bounds.outHeight) > COMPOSER_DIRECT_JPEG_MAX_LONG_EDGE) return false
    val orientation = runCatching {
        ExifInterface(inputStream()).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    }.getOrNull() ?: return false
    return orientation == ExifInterface.ORIENTATION_NORMAL ||
        orientation == ExifInterface.ORIENTATION_UNDEFINED
}

internal fun Context.isPrivateComposerImage(uri: Uri): Boolean {
    return privateComposerImageFile(uri) != null
}

internal fun Context.privateComposerImageFile(uri: Uri): File? {
    return runCatching {
        if (uri.scheme != "file") return@runCatching null
        val path = uri.path ?: return@runCatching null
        val imageDir = File(filesDir, "composer_images").canonicalFile
        val imageFile = File(path).canonicalFile
        imageFile.takeIf {
            it.isFile && it.parentFile?.canonicalFile == imageDir
        }
    }.getOrNull()
}

internal fun Context.deleteComposerImageAttachment(attachment: ComposerImageAttachment) {
    runCatching {
        privateComposerImageFile(Uri.parse(attachment.uri))?.delete()
    }
}

private fun Context.deleteUnreferencedComposerImages(retainedUris: Collection<String>) {
    runCatching {
        val imageDir = File(filesDir, "composer_images").canonicalFile
        if (!imageDir.isDirectory) return@runCatching
        val retainedFiles = retainedUris
            .mapNotNull { uriString ->
                runCatching { privateComposerImageFile(Uri.parse(uriString))?.canonicalFile }.getOrNull()
            }
            .toSet()
        imageDir.listFiles()
            ?.filter { file -> file.isFile && file.canonicalFile !in retainedFiles }
            ?.forEach { file -> file.delete() }
    }
}

private fun assistantMessageIdForSourceUser(sourceUserMessageId: String): String =
    "assistant_$sourceUserMessageId"

private suspend fun awaitRemoteSnapshot(): SessionSnapshot? =
    suspendCancellableCoroutine { continuation ->
        SessionApi.getSnapshot { snapshot ->
            if (continuation.isActive) {
                continuation.resume(snapshot)
            }
        }
    }

private suspend fun awaitMembershipEntitlement(): SessionApi.EntitlementSnapshot? =
    suspendCancellableCoroutine { continuation ->
        SessionApi.getEntitlement { entitlement ->
            if (continuation.isActive) {
                continuation.resume(entitlement)
            }
        }
    }

private suspend fun awaitTodayAgriCard(): SessionApi.TodayAgriCard? =
    suspendCancellableCoroutine { continuation ->
        SessionApi.getTodayAgriCard { card ->
            if (continuation.isActive) {
                continuation.resume(card)
            }
        }
    }

private fun SessionSnapshot.findRoundByClientMessageId(clientMessageId: String): ARound? {
    if (clientMessageId.isBlank()) return null
    for (index in a_rounds_full.lastIndex downTo 0) {
        val round = a_rounds_full[index]
        if (round.client_msg_id == clientMessageId) {
            return round
        }
    }
    for (index in a_rounds_for_ui.lastIndex downTo 0) {
        val round = a_rounds_for_ui[index]
        if (round.client_msg_id == clientMessageId) {
            return round
        }
    }
    return null
}

private fun trailingRecoverableUserMessageId(
    source: List<ChatMessage>,
    ignoredUserMessageIds: Set<String> = emptySet()
): String? =
    source.lastOrNull()
        ?.takeIf {
            it.role == ChatRole.USER &&
                it.id.isNotBlank() &&
                it.id !in ignoredUserMessageIds &&
                !it.isLocalImageUploadPendingUserMessage()
        }
        ?.id

private fun trailingRecoverableSourceUserMessageId(
    source: List<ChatMessage>,
    ignoredUserMessageIds: Set<String> = emptySet(),
    failedAssistantMessageStates: Map<String, FailedAssistantMessageState> = emptyMap()
): String? {
    trailingRecoverableUserMessageId(
        source = source,
        ignoredUserMessageIds = ignoredUserMessageIds
    )?.let { return it }
    val lastMessage = source.lastOrNull() ?: return null
    val failedAssistantState = failedAssistantMessageStates[lastMessage.id] ?: return null
    val sourceUserMessageId = failedAssistantState.sourceUserMessageId
    if (sourceUserMessageId.isBlank() || sourceUserMessageId in ignoredUserMessageIds) return null
    val hasSourceUser = source.any { message ->
        message.id == sourceUserMessageId &&
            message.role == ChatRole.USER &&
            !message.isLocalImageUploadPendingUserMessage()
    }
    return sourceUserMessageId.takeIf { hasSourceUser }
}

private suspend fun prewarmAssistantMarkdown(messages: List<ChatMessage>) = withContext(Dispatchers.Default) {
    messages
        .filter { it.role == ChatRole.ASSISTANT && it.content.isNotBlank() }
        .takeLast(12)
        .forEach { buildRendererStructureStats(it.content) }
}

private fun snapshotRoundsToMessages(rounds: List<ARound>): List<ChatMessage> {
    return rounds.flatMapIndexed { index, round ->
        buildList {
            val sourceUserMessageId = round.client_msg_id?.takeIf { it.isNotBlank() } ?: "remote_user_$index"
            if (round.user.isNotBlank() || round.user_images.isNotEmpty()) {
                add(
                    ChatMessage(
                        id = sourceUserMessageId,
                        role = ChatRole.USER,
                        content = round.user,
                        imageUrls = round.user_images.takeIf { it.isNotEmpty() }
                    )
                )
            }
            if (round.assistant.isNotBlank()) {
                add(ChatMessage(assistantMessageIdForSourceUser(sourceUserMessageId), ChatRole.ASSISTANT, round.assistant))
            }
        }
    }
}

internal fun shouldReplaceHydratedMessages(
    currentMessages: List<ChatMessage>,
    remoteMessages: List<ChatMessage>
): Boolean {
    val trimmedCurrent = sanitizeMessageWindow(currentMessages)
    val trimmedRemote = sanitizeMessageWindow(remoteMessages)
    if (trimmedCurrent.size != trimmedRemote.size) return true
    return trimmedRemote.indices.any { index ->
        val current = trimmedCurrent[index]
        val remote = trimmedRemote[index]
        current.id != remote.id ||
            current.role != remote.role ||
            current.content != remote.content ||
            current.imageUrls.orEmpty() != remote.imageUrls.orEmpty() ||
            current.todayAgriContextDay != remote.todayAgriContextDay
    }
}

internal fun canApplyHydratedVisualMutation(
    hasStartedConversation: Boolean,
    isStreaming: Boolean,
    hasStreamingItem: Boolean,
    userBlocksHydratedVisualMutation: Boolean
): Boolean =
    !hasStartedConversation &&
        !isStreaming &&
        !hasStreamingItem &&
        !userBlocksHydratedVisualMutation

internal fun shouldHoldHydratedVisualMutationForBrowsing(
    hasStartedConversation: Boolean,
    isStreaming: Boolean,
    hasStreamingItem: Boolean,
    userBlocksHydratedVisualMutation: Boolean
): Boolean =
    !hasStartedConversation &&
        !isStreaming &&
        !hasStreamingItem &&
        userBlocksHydratedVisualMutation

@Composable
private fun LongArrowIcon(
    tint: Color,
    directionUp: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.12f
        val centerX = size.width / 2f
        val headY = if (directionUp) size.height * 0.14f else size.height * 0.86f
        val tailY = if (directionUp) size.height * 0.9f else size.height * 0.1f
        val wingY = if (directionUp) size.height * 0.48f else size.height * 0.52f
        drawLine(
            color = tint,
            start = Offset(centerX, tailY),
            end = Offset(centerX, headY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(centerX, headY),
            end = Offset(size.width * 0.16f, wingY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(centerX, headY),
            end = Offset(size.width * 0.84f, wingY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun MenuBarsIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.085f
        val y1 = size.height * 0.34f
        val y2 = size.height * 0.66f
        drawLine(
            color = tint,
            start = Offset(size.width * 0.18f, y1),
            end = Offset(size.width * 0.82f, y1),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.18f, y2),
            end = Offset(size.width * 0.58f, y2),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun MembershipCenterLeafIcon(
    size: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_membership_leaf_outline),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun PlusCrossIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.14f
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        drawLine(
            color = tint,
            start = Offset(centerX, size.height * 0.14f),
            end = Offset(centerX, size.height * 0.86f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.14f, centerY),
            end = Offset(size.width * 0.86f, centerY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun FrostedCircleButton(
    size: Dp,
    surfaceColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: @Composable BoxScope.() -> Unit
) {
    Surface(
        shape = CircleShape,
        color = surfaceColor,
        border = BorderStroke(1.28.dp, borderColor.copy(alpha = 0.96f)),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 1.6.dp,
                shape = CircleShape,
                ambientColor = Color(0x16000000),
                spotColor = Color(0x16000000)
            )
    ) {
        IconButton(
            onClick = onClick,
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFF1A1A1A))
        ) {
            Box(contentAlignment = Alignment.Center, content = icon)
        }
    }
}

@Composable
@OptIn(
    ExperimentalComposeUiApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
    FlowPreview::class
)
fun ChatScreen() {
    val chatScopeId = IdManager.getUserId()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val inputSelectionColors = remember {
        TextSelectionColors(
            handleColor = CHAT_SELECTION_HANDLE_COLOR,
            backgroundColor = CHAT_SELECTION_BACKGROUND_COLOR
        )
    }
    val view = LocalView.current
    val hasRemoteHistorySource = BuildConfig.USE_BACKEND_AB && SessionApi.hasBackendConfigured()
    val initialLocalSnapshot = remember(chatScopeId, hasRemoteHistorySource) {
        context.loadLocalChatWindowSync(chatScopeId)
    }
    val initialLocalMessages = remember(initialLocalSnapshot) { initialLocalSnapshot.messages }
    val initialComposerDraftText = remember(chatScopeId) {
        context.loadLocalComposerDraftSync(chatScopeId)
    }
    val initialTodayAgriMainShownDay = remember(chatScopeId) {
        context.loadTodayAgriMainShownDaySync(chatScopeId)
    }
    val initialTodayAgriCard = remember(chatScopeId) {
        context.loadTodayAgriCardCacheSync(chatScopeId)
    }
    val initialTodayAgriCardIsRenderable = remember(
        initialTodayAgriCard,
        initialTodayAgriMainShownDay,
        initialLocalMessages,
        hasRemoteHistorySource
    ) {
        !hasRemoteHistorySource &&
            shouldShowTodayAgriMainCard(
                card = initialTodayAgriCard,
                currentDayKey = currentChinaDateKey(),
                shownDayKey = initialTodayAgriMainShownDay,
                shownThisRuntime = false,
                hasAssistantAnswerTail = hasCompletedAssistantAnswerTail(
                    initialLocalMessages,
                    initialLocalSnapshot.failedAssistantMessageStates.keys
                )
            )
    }
    val uiRuntimeResetKey = remember(chatScopeId, initialLocalSnapshot, hasRemoteHistorySource) {
        buildString {
            append(chatScopeId)
            append("|local=").append(initialLocalSnapshot.hashCode())
            append("|backend=").append(if (hasRemoteHistorySource) 1 else 0)
        }
    }
    val input = remember(uiRuntimeResetKey, initialComposerDraftText) {
        mutableStateOf(TextFieldValue(initialComposerDraftText))
    }
    val selectedComposerImages = remember(uiRuntimeResetKey) {
        mutableStateListOf<ComposerImageAttachment>()
    }
    var attachmentMenuVisible by remember(uiRuntimeResetKey) { mutableStateOf(false) }
    var pendingCameraImageUriString by rememberSaveable(uiRuntimeResetKey) { mutableStateOf<String?>(null) }
    var pendingCameraImageGalleryBacked by rememberSaveable(uiRuntimeResetKey) { mutableStateOf(false) }
    var pendingCameraImageTemporaryFilePath by rememberSaveable(uiRuntimeResetKey) { mutableStateOf<String?>(null) }
    var imageSendInProgress by remember(uiRuntimeResetKey) { mutableStateOf(false) }
    val shouldHydrateRemoteHistory = remember(chatScopeId, hasRemoteHistorySource) {
        hasRemoteHistorySource
    }
    var historyHydrationComplete by remember(uiRuntimeResetKey) {
        mutableStateOf(initialLocalMessages.isNotEmpty() || !shouldHydrateRemoteHistory)
    }
    var remoteSnapshotHydrationComplete by remember(uiRuntimeResetKey) {
        mutableStateOf(!shouldHydrateRemoteHistory)
    }
    var startupRecoverableUserMessageId by remember(uiRuntimeResetKey) {
        mutableStateOf(
            if (hasRemoteHistorySource) {
                trailingRecoverableSourceUserMessageId(
                    source = initialLocalMessages,
                    ignoredUserMessageIds = initialLocalSnapshot.failedUserMessageStates.keys,
                    failedAssistantMessageStates = initialLocalSnapshot.failedAssistantMessageStates
                )
            } else {
                null
            }
        )
    }
    val messages = remember(uiRuntimeResetKey) {
        mutableStateListOf<ChatMessage>().apply { addAll(initialLocalMessages) }
    }
    LaunchedEffect(uiRuntimeResetKey) {
        if (BuildConfig.DEBUG) {
            Log.d(
                CHAT_STARTUP_DIAG_TAG,
                buildString {
                    append("scopeSuffix=").append(chatScopeId.takeLast(8))
                    append(", localMessages=").append(initialLocalSnapshot.messages.size)
                    append(", localFailedUsers=").append(initialLocalSnapshot.failedUserMessageStates.size)
                    append(", localFailedAssistants=").append(initialLocalSnapshot.failedAssistantMessageStates.size)
                    append(", hasRemoteSource=").append(hasRemoteHistorySource)
                }
            )
        }
    }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    var latestClientRegion by remember(uiRuntimeResetKey) {
        mutableStateOf(ClientRegionProvider.cachedRegion(context))
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        ClientRegionProvider.markLocationPermissionPrompted(context)
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            snackbarScope.launch {
                latestClientRegion = ClientRegionProvider.refreshRegion(context)
            }
        }
    }
    LaunchedEffect(uiRuntimeResetKey, hasRemoteHistorySource) {
        if (!hasRemoteHistorySource) return@LaunchedEffect
        if (ClientRegionProvider.hasLocationPermission(context)) {
            latestClientRegion = ClientRegionProvider.refreshRegion(context)
        }
    }
    val density = LocalDensity.current
    val startupBottomBarHeightEstimatePx = with(density) { STARTUP_BOTTOM_BAR_HEIGHT_ESTIMATE.roundToPx() }
    val startupInputChromeRowHeightEstimatePx = with(density) { STARTUP_INPUT_CHROME_ROW_HEIGHT_ESTIMATE.roundToPx() }
    val startupInputContentHeightEstimatePx = with(density) { 22.sp.roundToPx() }
    val topInset = WindowInsets.safeDrawing
        .only(WindowInsetsSides.Top)
        .asPaddingValues()
        .calculateTopPadding()
    val streamVisibleBottomGapPx = with(density) { STREAM_VISIBLE_BOTTOM_GAP.toPx().roundToInt() }
    val bottomPositionTolerancePx = with(density) { BOTTOM_POSITION_TOLERANCE.roundToPx() }
    val staticBottomPositionTolerancePx = with(density) { STATIC_BOTTOM_POSITION_TOLERANCE.roundToPx() }
    val initialWorklineBottomSwitchOverflowPx =
        with(density) { INITIAL_WORKLINE_BOTTOM_SWITCH_OVERFLOW.roundToPx() }
    val chatMessageItemVerticalPaddingPx = with(density) { CHAT_MESSAGE_ITEM_VERTICAL_PADDING.roundToPx() }
    val todayAgriMinVisiblePx = with(density) { 24.dp.roundToPx() }
    val streamingRuntime = rememberChatStreamingRuntimeState(uiRuntimeResetKey)
    var isStreaming by streamingRuntime.isStreaming
    var streamingMessageId by streamingRuntime.streamingMessageId
    var streamingMessageContent by streamingRuntime.streamingMessageContent
    var streamingRevealBuffer by streamingRuntime.streamingRevealBuffer
    var streamRevealJob by streamingRuntime.streamRevealJob
    val initialChatListIndex = remember(uiRuntimeResetKey) {
        (initialLocalMessages.size - 1).coerceAtLeast(0)
    }
    val initialChatListScrollOffset = remember(uiRuntimeResetKey) {
        if (initialLocalMessages.isNotEmpty()) FORWARD_LIST_BOTTOM_SCROLL_OFFSET else 0
    }
    val chatListState = remember(uiRuntimeResetKey) {
        LazyListState(initialChatListIndex, initialChatListScrollOffset)
    }
    var recyclerScrollInProgress by remember(uiRuntimeResetKey) { mutableStateOf(false) }
    var recyclerFirstVisibleItemIndex by remember(uiRuntimeResetKey) { mutableIntStateOf(0) }
    var recyclerFirstVisibleItemScrollOffset by remember(uiRuntimeResetKey) { mutableIntStateOf(0) }
    val scrollRuntime = rememberChatScrollRuntimeState(
        chatScopeId = uiRuntimeResetKey,
        startupBottomBarHeightEstimatePx = startupBottomBarHeightEstimatePx,
        startupInputChromeRowHeightEstimatePx = startupInputChromeRowHeightEstimatePx
    )
    var scrollMode by scrollRuntime.scrollMode
    var programmaticScroll by scrollRuntime.programmaticScroll
    var streamingContentBottomPx by scrollRuntime.streamingContentBottomPx
    var programmaticBottomAnchorGeneration by remember(uiRuntimeResetKey) { mutableIntStateOf(0) }
    val hasStartupLocalMessages = initialLocalMessages.isNotEmpty()
    var initialBottomSnapDone by remember(uiRuntimeResetKey) { mutableStateOf(false) }
    var postInitialSnapCorrectionDone by remember(uiRuntimeResetKey) { mutableStateOf(false) }
    var startupUiStateLogged by remember(uiRuntimeResetKey) { mutableStateOf(false) }
    var startupBottomSnapDoneLogged by remember(uiRuntimeResetKey) { mutableStateOf(false) }
    var startupBottomSnapPendingLogged by remember(uiRuntimeResetKey) { mutableStateOf(false) }
    var chatRenderStructureLoggedSignature by rememberSaveable(uiRuntimeResetKey) { mutableStateOf("") }
    var todayAgriMainCardLoadedLogged by remember(uiRuntimeResetKey) { mutableStateOf(false) }
    var todayAgriMainCardVisibleLogged by remember(uiRuntimeResetKey) { mutableStateOf(false) }
    var jumpButtonPulseVisible by scrollRuntime.jumpButtonPulseVisible
    var suppressJumpButtonForLifecycleResume by scrollRuntime.suppressJumpButtonForLifecycleResume
    var bottomBarHeightPx by scrollRuntime.bottomBarHeightPx
    var inputChromeRowHeightPx by scrollRuntime.inputChromeRowHeightPx
    val chatListUserDragging by chatListState.interactionSource.collectIsDraggedAsState()

    val composerRuntime = rememberChatComposerRuntimeState(
        chatScopeId = uiRuntimeResetKey,
        startupInputContentHeightEstimatePx = startupInputContentHeightEstimatePx
    )
    var inputLimitHintVisible by composerRuntime.inputLimitHintVisible
    var inputLimitHintTick by composerRuntime.inputLimitHintTick
    var composerStatusHintVisible by composerRuntime.composerStatusHintVisible
    var composerStatusHintTick by composerRuntime.composerStatusHintTick
    var composerStatusHintText by composerRuntime.composerStatusHintText
    var inputFieldFocused by composerRuntime.inputFieldFocused
    var suppressInputCursor by composerRuntime.suppressInputCursor
    var inputContentHeightPx by composerRuntime.inputContentHeightPx
    var composerSettlingMinHeightPx by composerRuntime.composerSettlingMinHeightPx
    var composerSettlingChromeHeightPx by composerRuntime.composerSettlingChromeHeightPx
    var sendUiSettling by composerRuntime.sendUiSettling
    var persistTick by remember(uiRuntimeResetKey) { mutableIntStateOf(0) }
    var messageViewportWidthPx by remember(uiRuntimeResetKey) { mutableIntStateOf(0) }
    var messageViewportHeightPx by remember(uiRuntimeResetKey) { mutableIntStateOf(0) }
    var chatRootLeftPx by remember(uiRuntimeResetKey) { mutableStateOf(0f) }
    var chatRootTopPx by remember(uiRuntimeResetKey) { mutableStateOf(0f) }
    var messageViewportLeftPx by remember(uiRuntimeResetKey) { mutableStateOf(0f) }
    var messageViewportTopPx by remember(uiRuntimeResetKey) { mutableStateOf(0f) }
    var composerTopInViewportPx by remember(uiRuntimeResetKey) { mutableIntStateOf(-1) }
    var topChromeMaskBottomPx by remember(uiRuntimeResetKey) { mutableIntStateOf(-1) }
    var pendingStreamingFinalizeMessageId by remember(uiRuntimeResetKey) {
        mutableStateOf<String?>(null)
    }
    var pendingStreamingFinalizeShouldRestoreBottomAnchor by remember(uiRuntimeResetKey) {
        mutableStateOf(false)
    }
    var pendingStreamingFinalizeStartedAtMs by remember(uiRuntimeResetKey) {
        mutableLongStateOf(0L)
    }
    var anchoredUserMessageId by rememberSaveable(uiRuntimeResetKey) { mutableStateOf<String?>(null) }
    var hasStartedConversation by remember(uiRuntimeResetKey) { mutableStateOf(false) }
    var initialWorklinePhase by rememberSaveable(uiRuntimeResetKey) {
        mutableStateOf(
            restoredStartupWorklinePhase(
                messageCount = initialLocalMessages.size,
                hasTodayAgriVisualContent = initialTodayAgriCardIsRenderable,
                persistedInitialWorklineOwned = initialLocalSnapshot.initialWorklineOwned
            )
        )
    }
    var sendStartViewportHeightPx by remember(uiRuntimeResetKey) { mutableIntStateOf(0) }
    val sendStartAnchorActiveState = remember(uiRuntimeResetKey) { mutableStateOf(false) }
    var sendStartAnchorActive by sendStartAnchorActiveState
    var latestConversationBottomPaddingPx by remember(uiRuntimeResetKey) { mutableIntStateOf(-1) }
    var lockedConversationBottomPaddingPx by remember(uiRuntimeResetKey) { mutableIntStateOf(-1) }
    var observedCollapsedBottomReservePx by remember(uiRuntimeResetKey) { mutableIntStateOf(-1) }
    var remoteRecoveryJob by remember(uiRuntimeResetKey) { mutableStateOf<Job?>(null) }
    var remoteRecoverySourceUserMessageId by rememberSaveable(uiRuntimeResetKey) { mutableStateOf<String?>(null) }
    var imageSendJob by remember(uiRuntimeResetKey) { mutableStateOf<Job?>(null) }
    var imageSendGeneration by remember(uiRuntimeResetKey) { mutableIntStateOf(0) }
    var streamingBackgrounded by rememberSaveable(uiRuntimeResetKey) { mutableStateOf(false) }
    val failedUserMessageStates = remember(uiRuntimeResetKey) {
        mutableStateMapOf<String, String>().apply {
            putAll(initialLocalSnapshot.failedUserMessageStates)
        }
    }
    val failedAssistantMessageStates = remember(uiRuntimeResetKey) {
        mutableStateMapOf<String, FailedAssistantMessageState>().apply {
            putAll(initialLocalSnapshot.failedAssistantMessageStates)
        }
    }
    var todayAgriCard by remember(uiRuntimeResetKey) { mutableStateOf(initialTodayAgriCard) }
    var todayAgriMainItem by remember(uiRuntimeResetKey) { mutableStateOf<TodayAgriMainItem?>(null) }
    var todayAgriLocalAnchorMessageId by rememberSaveable(uiRuntimeResetKey) { mutableStateOf<String?>(null) }
    var pendingHydratedTodayAgriMainItem by remember(uiRuntimeResetKey) { mutableStateOf<TodayAgriMainItem?>(null) }
    var pendingHydratedSnapshot by remember(uiRuntimeResetKey) { mutableStateOf<PendingHydratedSnapshot?>(null) }
    var todayAgriRemoteConfirmedDay by remember(uiRuntimeResetKey) { mutableStateOf<String?>(null) }
    var todayAgriMainShownDay by rememberSaveable(uiRuntimeResetKey) {
        mutableStateOf(initialTodayAgriMainShownDay)
    }
    var todayAgriShownThisRuntime by rememberSaveable(uiRuntimeResetKey) { mutableStateOf(false) }
    var todayAgriInsertedThisRuntime by rememberSaveable(uiRuntimeResetKey) { mutableStateOf(false) }
    var todayAgriAutoInsertSuppressedThisRuntime by rememberSaveable(uiRuntimeResetKey) {
        mutableStateOf(false)
    }
    var todayAgriAwaitingNewCompletedAssistantAfterDayChange by rememberSaveable(uiRuntimeResetKey) {
        mutableStateOf(false)
    }
    var todayAgriDayChangeCompletedAssistantBaselineId by rememberSaveable(uiRuntimeResetKey) {
        mutableStateOf<String?>(null)
    }
    var todayAgriUserSendEpoch by remember(uiRuntimeResetKey) { mutableIntStateOf(0) }
    var hiddenRemoteRoundCount by remember(uiRuntimeResetKey) { mutableIntStateOf(0) }
    val currentTodayAgriCardDay = todayAgriCardDayKey(todayAgriCard)
    val currentTodayAgriMainItem by remember(todayAgriMainItem) {
        derivedStateOf {
            validTodayAgriMainItemForCurrentDay(todayAgriMainItem, currentChinaDateKey())
        }
    }
    val hasCompletedAssistantTail by remember(messages, failedAssistantMessageStates) {
        derivedStateOf { hasCompletedAssistantAnswerTail(messages, failedAssistantMessageStates.keys) }
    }
    val latestCompletedAssistantTailId by remember(messages, failedAssistantMessageStates) {
        derivedStateOf { latestCompletedAssistantAnswerId(messages, failedAssistantMessageStates.keys) }
    }
    LaunchedEffect(
        latestCompletedAssistantTailId,
        todayAgriAwaitingNewCompletedAssistantAfterDayChange,
        todayAgriDayChangeCompletedAssistantBaselineId
    ) {
        if (
            shouldReleaseTodayAgriAutoInsertAfterDayChange(
                waitingForNextAssistant = todayAgriAwaitingNewCompletedAssistantAfterDayChange,
                baselineCompletedAssistantTailId = todayAgriDayChangeCompletedAssistantBaselineId,
                latestCompletedAssistantTailId = latestCompletedAssistantTailId
            )
        ) {
            todayAgriAwaitingNewCompletedAssistantAfterDayChange = false
            todayAgriDayChangeCompletedAssistantBaselineId = null
            todayAgriAutoInsertSuppressedThisRuntime = false
        }
    }
    val currentTodayAgriCardHasSavedItem by remember(currentTodayAgriMainItem) {
        derivedStateOf { currentTodayAgriMainItem != null }
    }
    val retryingUserMessageIds = remember(uiRuntimeResetKey) { mutableStateMapOf<String, Boolean>() }
    val retryingAssistantMessageIds = remember(uiRuntimeResetKey) { mutableStateMapOf<String, Boolean>() }
    var quotaExhaustedDayKey by rememberSaveable(uiRuntimeResetKey) { mutableStateOf<String?>(null) }
    fun isQuotaExhaustedToday(): Boolean = quotaExhaustedDayKey == currentQuotaDayKey()
    val shouldShowTodayAgriCard by remember(
        todayAgriCard,
        currentTodayAgriCardDay,
        currentTodayAgriMainItem,
        todayAgriMainShownDay,
        todayAgriShownThisRuntime,
        todayAgriInsertedThisRuntime,
        hasCompletedAssistantTail,
        currentTodayAgriCardHasSavedItem,
        todayAgriAutoInsertSuppressedThisRuntime
    ) {
        derivedStateOf {
            shouldShowTodayAgriMainCard(
                card = currentTodayAgriMainItem?.card ?: todayAgriCard,
                currentDayKey = currentChinaDateKey(),
                shownDayKey = todayAgriMainShownDay,
                shownThisRuntime = todayAgriShownThisRuntime,
                hasAssistantAnswerTail = hasCompletedAssistantTail,
                hasSavedItem = currentTodayAgriCardHasSavedItem,
                suppressedThisRuntime = todayAgriAutoInsertSuppressedThisRuntime,
                insertedThisRuntime = todayAgriInsertedThisRuntime
            )
        }
    }
    val shouldRenderTodayAgriCardInTimeline by remember(
        shouldShowTodayAgriCard,
        shouldHydrateRemoteHistory,
        remoteSnapshotHydrationComplete,
        todayAgriShownThisRuntime
    ) {
        derivedStateOf {
            shouldRenderTodayAgriMainCardInTimeline(
                shouldShowTodayAgriCard = shouldShowTodayAgriCard,
                shouldHydrateRemoteHistory = shouldHydrateRemoteHistory,
                remoteSnapshotHydrationComplete = remoteSnapshotHydrationComplete,
                shownThisRuntime = todayAgriShownThisRuntime
            )
        }
    }
    val todayAgriAfterMessageIdForRender by remember(
        currentTodayAgriMainItem,
        todayAgriLocalAnchorMessageId,
        latestCompletedAssistantTailId,
        messages
    ) {
        derivedStateOf {
            resolveTodayAgriVisualAnchorMessageId(
                savedAnchorMessageId = currentTodayAgriMainItem?.anchor_client_msg_id,
                localAnchorMessageId = todayAgriLocalAnchorMessageId,
                latestCompletedAssistantTailId = latestCompletedAssistantTailId,
                existingMessageIds = messages.mapTo(mutableSetOf()) { it.id }
            )
        }
    }
    val hasStreamingItem by remember(isStreaming, streamingMessageId, messages.size) {
        derivedStateOf {
            val messageId = streamingMessageId
            isStreaming &&
                !messageId.isNullOrBlank() &&
                messages.any { it.id == messageId }
        }
    }
    val chatListItems by remember(
        todayAgriCard,
        currentTodayAgriMainItem,
        hasRemoteHistorySource,
        shouldRenderTodayAgriCardInTimeline,
        todayAgriAfterMessageIdForRender,
        hiddenRemoteRoundCount,
        messages
    ) {
        derivedStateOf {
            val visibleTodayAgriCard = if (!shouldRenderTodayAgriCardInTimeline) {
                null
            } else if (hasRemoteHistorySource) {
                currentTodayAgriMainItem?.card
            } else {
                currentTodayAgriMainItem?.card ?: todayAgriCard
            }
            buildChatTimelineItems(
                messages = messages,
                todayAgriCard = visibleTodayAgriCard,
                todayAgriAfterMessageId = todayAgriAfterMessageIdForRender,
                hiddenRoundCount = hiddenRemoteRoundCount,
                failedAssistantMessageIds = failedAssistantMessageStates.keys
            )
        }
    }
    val hasTodayAgriCard by remember(chatListItems) {
        derivedStateOf {
            chatListItems.any { it is ChatTimelineItem.TodayAgriCard }
        }
    }
    LaunchedEffect(hasTodayAgriCard, shouldRenderTodayAgriCardInTimeline, currentTodayAgriCardDay) {
        if (hasTodayAgriCard && shouldRenderTodayAgriCardInTimeline) {
            todayAgriInsertedThisRuntime = true
        }
    }
    LaunchedEffect(
        messages.size,
        streamingMessageContent.length,
        hasTodayAgriCard,
        chatListItems.size
    ) {
        val assistantContents = messages
            .filter { message -> message.role == ChatRole.ASSISTANT && message.content.isNotBlank() }
            .map { message -> message.content }
            .takeLast(3)
            .toMutableList()
        if (streamingMessageContent.isNotBlank()) {
            assistantContents += streamingMessageContent
        }
        if (assistantContents.isEmpty() && !hasTodayAgriCard) return@LaunchedEffect
        val stats = assistantContents
            .map(::buildRendererStructureStats)
            .fold(RendererStructureStats(0, 0, 0, 0, 0, 0)) { acc, item ->
                RendererStructureStats(
                    blockCount = acc.blockCount + item.blockCount,
                    headingCount = acc.headingCount + item.headingCount,
                    tableCount = acc.tableCount + item.tableCount,
                    bulletCount = acc.bulletCount + item.bulletCount,
                    numberedCount = acc.numberedCount + item.numberedCount,
                    dividerHeadingCount = acc.dividerHeadingCount + item.dividerHeadingCount
                )
            }
        val signature = listOf(
            messages.size,
            streamingMessageContent.length,
            hasTodayAgriCard,
            chatListItems.size,
            stats.blockCount,
            stats.headingCount,
            stats.tableCount,
            stats.dividerHeadingCount
        ).joinToString("|")
        if (signature == chatRenderStructureLoggedSignature) return@LaunchedEffect
        chatRenderStructureLoggedSignature = signature
        if (BuildConfig.DEBUG) {
            Log.d(
                CHAT_STARTUP_DIAG_TAG,
                "render_structure visible=${messages.size}, list=${chatListItems.size}, " +
                    "todayAgri=$hasTodayAgriCard, blocks=${stats.blockCount}, " +
                    "headings=${stats.headingCount}, tables=${stats.tableCount}, " +
                    "dividers=${stats.dividerHeadingCount}"
            )
        }
        SessionApi.reportClientLog(
            level = "info",
            event = "ui.chat_render_structure",
            message = "Chat render structure",
            attrs = mapOf(
                "visible_item_count" to messages.size,
                "list_item_count" to chatListItems.size,
                "assistant_sample_count" to assistantContents.size,
                "has_today_agri_card" to hasTodayAgriCard,
                "block_count" to stats.blockCount,
                "heading_count" to stats.headingCount,
                "table_count" to stats.tableCount,
                "bullet_count" to stats.bulletCount,
                "numbered_count" to stats.numberedCount,
                "divider_heading_count" to stats.dividerHeadingCount
            )
        )
    }
    fun todayAgriContextDayForNextSend(existingUserMessageId: String? = null): String? {
        if (!hasTodayAgriCard || !shouldRenderTodayAgriCardInTimeline) return null
        if (!todayAgriShownThisRuntime && todayAgriMainShownDay != currentTodayAgriCardDay) {
            return null
        }
        return resolveTodayAgriContextDayForTimeline(
            chatListItems = chatListItems,
            currentTodayAgriCardDay = currentTodayAgriCardDay,
            currentDayKey = currentChinaDateKey(),
            remoteConfirmedDay = todayAgriRemoteConfirmedDay,
            existingUserMessageId = existingUserMessageId,
            failedUserMessageIds = failedUserMessageStates.keys
        )
    }
    fun persistedTodayAgriContextDayForUserMessage(userMessageId: String?): String? {
        if (userMessageId.isNullOrBlank()) return null
        val messageDay = messages
            .firstOrNull { message -> message.id == userMessageId && message.role == ChatRole.USER }
            ?.todayAgriContextDay
            ?.let(::normalizeTodayAgriCardDayKey)
            ?.takeIf { it.isNotBlank() }
        if (messageDay != null) return messageDay
        return PendingChatSendStore.get(context, chatScopeId, userMessageId)
            ?.todayAgriContextDay
            ?.let(::normalizeTodayAgriCardDayKey)
            ?.takeIf { it.isNotBlank() }
    }
    fun resolveTodayAgriContextDayForSend(existingUserMessageId: String? = null): String? {
        return persistedTodayAgriContextDayForUserMessage(existingUserMessageId)
            ?: todayAgriContextDayForNextSend(existingUserMessageId)
    }
    fun suppressPendingTodayAgriAutoInsertForUserSend() {
        if (!todayAgriShownThisRuntime && !hasTodayAgriCard) {
            todayAgriUserSendEpoch++
            todayAgriAutoInsertSuppressedThisRuntime = true
        }
    }

    fun userBlocksHydratedVisualMutation(): Boolean =
        !programmaticScroll &&
            (
                chatListUserDragging ||
                    recyclerScrollInProgress ||
                    scrollRuntime.userInteracting.value ||
                    scrollMode == ScrollMode.UserBrowsing
                )

    fun applyHydratedTodayAgriMainItem(item: TodayAgriMainItem) {
        todayAgriMainItem = item
        todayAgriLocalAnchorMessageId = item.anchor_client_msg_id.takeIf { it.isNotBlank() }
        todayAgriCard = item.card
        context.saveTodayAgriCardCacheSync(chatScopeId, item.card)
        todayAgriRemoteConfirmedDay = item.day_cn
        SessionApi.reportClientLog(
            level = "info",
            event = "today_agri.item_restored",
            message = "Today agri main item restored",
            attrs = mapOf(
                "card_day" to item.day_cn,
                "item_count" to (item.card.items?.size ?: 0),
                "from_snapshot" to true
            )
        )
    }

    fun resetTodayAgriRuntimeAfterHistoryClear() {
        todayAgriMainShownDay = todayAgriMainShownDayAfterHistoryClear()
        todayAgriShownThisRuntime = false
        todayAgriInsertedThisRuntime = false
        todayAgriAutoInsertSuppressedThisRuntime = false
        todayAgriAwaitingNewCompletedAssistantAfterDayChange = false
        todayAgriDayChangeCompletedAssistantBaselineId = null
        todayAgriUserSendEpoch = 0
        todayAgriMainItem = null
        todayAgriLocalAnchorMessageId = null
        pendingHydratedTodayAgriMainItem = null
        pendingHydratedSnapshot = null
        todayAgriCard = null
        todayAgriRemoteConfirmedDay = null
    }

    val messageSelectionBoundsCacheById = remember(uiRuntimeResetKey) { mutableMapOf<String, Rect>() }
    val messageSelectionBoundsById = remember(uiRuntimeResetKey) { mutableStateMapOf<String, Rect>() }
    val messageContentBoundsById = remember(uiRuntimeResetKey) { mutableStateMapOf<String, Rect>() }
    val imeVisible = WindowInsets.isImeVisible
    val isComposerSettling by remember(
        sendUiSettling,
        composerSettlingMinHeightPx,
        composerSettlingChromeHeightPx
    ) {
        derivedStateOf {
            sendUiSettling ||
                composerSettlingMinHeightPx > 0 ||
                composerSettlingChromeHeightPx > 0
        }
    }
    val startupShouldTrackRealtimeComposerGeometry by remember(
        initialBottomSnapDone,
        hasStartedConversation,
        messages.size,
        isStreaming,
        hasStreamingItem
    ) {
        derivedStateOf {
            !initialBottomSnapDone &&
                !hasStartedConversation &&
                messages.isNotEmpty() &&
                !isStreaming &&
                !hasStreamingItem
        }
    }
    val listShouldTrackRealtimeComposerGeometry by remember(
        isStreaming,
        hasStreamingItem,
        startupShouldTrackRealtimeComposerGeometry
    ) {
        derivedStateOf {
            isStreaming || hasStreamingItem || startupShouldTrackRealtimeComposerGeometry
        }
    }
    val shouldUseRealtimeComposerGeometry by remember(
        startupShouldTrackRealtimeComposerGeometry
    ) {
        derivedStateOf {
            startupShouldTrackRealtimeComposerGeometry
        }
    }
    val sendStartBottomPaddingLockActive by remember(
        sendUiSettling,
        sendStartAnchorActive
    ) {
        derivedStateOf {
            sendUiSettling || sendStartAnchorActive
        }
    }
    val lockedMessageViewportHeightPx by remember(
        messageViewportHeightPx,
        sendStartViewportHeightPx,
        sendStartBottomPaddingLockActive
    ) {
        derivedStateOf {
            if (sendStartBottomPaddingLockActive && sendStartViewportHeightPx > 0) {
                sendStartViewportHeightPx
            } else {
                messageViewportHeightPx
            }
        }
    }
    val safeBottomInsetPx = with(density) {
        WindowInsets.navigationBars
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding()
            .roundToPx()
    }
    val stableComposerBottomBarHeightPx by remember(
        inputChromeRowHeightPx,
        safeBottomInsetPx,
        startupBottomBarHeightEstimatePx
    ) {
        derivedStateOf {
            deriveComposerStableBottomBarHeightPx(
                inputChromeRowHeightPx = inputChromeRowHeightPx,
                safeBottomInsetPx = safeBottomInsetPx,
                startupBottomBarHeightEstimatePx = startupBottomBarHeightEstimatePx
            )
        }
    }
    LaunchedEffect(
        latestConversationBottomPaddingPx,
        sendStartBottomPaddingLockActive,
        isComposerSettling,
        inputFieldFocused,
        imeVisible,
        input.value.text,
        selectedComposerImages.size,
        attachmentMenuVisible
    ) {
        val collapsedStable =
            !sendStartBottomPaddingLockActive &&
                !isComposerSettling &&
                !isStreaming &&
                !hasStreamingItem &&
                pendingStreamingFinalizeMessageId.isNullOrBlank() &&
                !inputFieldFocused &&
                !imeVisible &&
                selectedComposerImages.isEmpty() &&
                !attachmentMenuVisible &&
                input.value.text.isEmpty()
        if (!collapsedStable) return@LaunchedEffect
        val prewarmedCollapsedBottomReservePx =
            (latestConversationBottomPaddingPx - streamVisibleBottomGapPx).coerceAtLeast(0)
        if (
            prewarmedCollapsedBottomReservePx > 0 &&
            prewarmedCollapsedBottomReservePx != observedCollapsedBottomReservePx
        ) {
            observedCollapsedBottomReservePx = prewarmedCollapsedBottomReservePx
        }
    }
    LaunchedEffect(
        composerTopInViewportPx,
        messageViewportHeightPx,
        latestConversationBottomPaddingPx,
        sendStartBottomPaddingLockActive,
        isComposerSettling,
        inputFieldFocused,
        imeVisible,
        input.value.text,
        selectedComposerImages.size,
        attachmentMenuVisible
    ) {
        val collapsedStable =
            !sendStartBottomPaddingLockActive &&
            !isComposerSettling &&
                !isStreaming &&
                !hasStreamingItem &&
                pendingStreamingFinalizeMessageId.isNullOrBlank() &&
                !inputFieldFocused &&
                !imeVisible &&
                selectedComposerImages.isEmpty() &&
                !attachmentMenuVisible &&
                input.value.text.isEmpty() &&
                composerTopInViewportPx > 0 &&
                messageViewportHeightPx > 0
        if (!collapsedStable) return@LaunchedEffect
        if (latestConversationBottomPaddingPx > 0) return@LaunchedEffect
        val currentCollapsedBottomReservePx =
            (messageViewportHeightPx - composerTopInViewportPx).coerceAtLeast(0)
        if (
            currentCollapsedBottomReservePx > 0 &&
            currentCollapsedBottomReservePx != observedCollapsedBottomReservePx
        ) {
            observedCollapsedBottomReservePx = currentCollapsedBottomReservePx
        }
    }
    val streamingWorklineBottomPx by remember(
        lockedMessageViewportHeightPx,
        lockedConversationBottomPaddingPx,
        sendStartBottomPaddingLockActive,
        stableComposerBottomBarHeightPx,
        bottomBarHeightPx,
        composerTopInViewportPx,
        latestConversationBottomPaddingPx,
        streamVisibleBottomGapPx,
        shouldUseRealtimeComposerGeometry
    ) {
        derivedStateOf {
            val effectiveViewportHeightPx = lockedMessageViewportHeightPx
            if (
                sendStartBottomPaddingLockActive &&
                lockedConversationBottomPaddingPx > 0 &&
                effectiveViewportHeightPx > 0
            ) {
                return@derivedStateOf (
                    effectiveViewportHeightPx -
                        lockedConversationBottomPaddingPx
                    ).coerceAtLeast(0)
            }
            val stableBottomBarHeightPx = when {
                stableComposerBottomBarHeightPx > 0 -> stableComposerBottomBarHeightPx
                bottomBarHeightPx > 0 -> bottomBarHeightPx
                else -> 0
            }
            val collapsedStableWorklineBottomPx =
                (
                    effectiveViewportHeightPx -
                        stableBottomBarHeightPx -
                        streamVisibleBottomGapPx
                    ).coerceAtLeast(0)
            if (
                shouldUseRealtimeComposerGeometry &&
                effectiveViewportHeightPx > 0 &&
                latestConversationBottomPaddingPx > 0
            ) {
                (effectiveViewportHeightPx - latestConversationBottomPaddingPx).coerceAtLeast(0)
            } else {
                collapsedStableWorklineBottomPx
            }
        }
    }
    fun currentStreamingContentBottomPx(): Int {
        return streamingContentBottomPx.takeIf { it > 0 } ?: -1
    }
    fun latestMessageIndex(): Int {
        return chatListItems.indexOfLast { item ->
            item is ChatTimelineItem.Message
        }
    }
    fun latestMessageIndexOrMinusOne(): Int {
        return latestMessageIndex()
    }
    fun latestVisualTailIndexOrMinusOne(): Int =
        chatListItems.lastIndex

    fun bottomAnchorIndexOrMinusOne(): Int =
        if (isStreaming || hasStreamingItem) {
            latestMessageIndexOrMinusOne()
        } else {
            latestVisualTailIndexOrMinusOne()
        }
    fun isInitialWorklineTopUnreached(): Boolean =
        initialWorklinePhase == InitialWorklinePhase.TopUnreached

    fun isInitialWorklineTopFlow(): Boolean =
        initialWorklinePhase == InitialWorklinePhase.TopUnreached ||
            initialWorklinePhase == InitialWorklinePhase.TopAnchoring

    fun hasStaticVisualTimeline(): Boolean =
        messages.isNotEmpty()

    fun shouldUseTopArrangementForConversation(): Boolean =
        isInitialWorklineTopFlow()

    fun shouldSuppressAutomaticBottomAnchor(): Boolean =
        isInitialWorklineTopFlow()

    fun shouldStartInitialWorklineTopFlow(): Boolean =
        initialWorklinePhase == InitialWorklinePhase.WaitingForFirstSend &&
            !hasStaticVisualTimeline() &&
            !isStreaming &&
            !hasStreamingItem

    fun keepOrStartInitialWorklineTopFlow(shouldUseTopFlow: Boolean) {
        if (shouldUseTopFlow) {
            initialWorklinePhase = InitialWorklinePhase.TopUnreached
        } else if (isInitialWorklineTopFlow()) {
            initialWorklinePhase = InitialWorklinePhase.WorklineOwned
        }
    }

    fun releaseInitialWorklineToBottom() {
        if (initialWorklinePhase != InitialWorklinePhase.WorklineOwned) {
            initialWorklinePhase = InitialWorklinePhase.WorklineOwned
        }
    }

    fun requestForwardListBottomAnchor(force: Boolean = false) {
        if (!force && shouldSuppressAutomaticBottomAnchor()) return
        val targetIndex = bottomAnchorIndexOrMinusOne()
        if (targetIndex >= 0) {
            chatListState.requestScrollToItem(
                index = targetIndex,
                scrollOffset = FORWARD_LIST_BOTTOM_SCROLL_OFFSET
            )
        }
    }
    fun currentLastMessageContentBottomPx(): Int {
        val lastMessage = messages.lastOrNull() ?: return -1
        if (lastMessage.role == ChatRole.ASSISTANT && hasStreamingItem && currentStreamingContentBottomPx() > 0) {
            return currentStreamingContentBottomPx()
        }
        val lastMessageId = lastMessage.id
        val bounds = if (lastMessage.role == ChatRole.ASSISTANT) {
            messageContentBoundsById[lastMessageId]
        } else {
            messageContentBoundsById[lastMessageId] ?: messageSelectionBoundsById[lastMessageId]
        }
        if (bounds != null) {
            return (bounds.bottom - messageViewportTopPx).roundToInt()
        }
        val newestDisplayIndex = latestMessageIndexOrMinusOne()
        if (newestDisplayIndex < 0) return -1
        val fallbackItem =
            chatListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == newestDisplayIndex }
                ?: return -1
        return fallbackItem.offset + fallbackItem.size
    }
    fun currentVisualTailContentBottomPx(): Int {
        if (!hasTodayAgriCard) return currentLastMessageContentBottomPx()
        val lastIndex = chatListItems.lastIndex
        if (lastIndex < 0 || chatListItems.getOrNull(lastIndex) !is ChatTimelineItem.TodayAgriCard) {
            return currentLastMessageContentBottomPx()
        }
        val todayAgriItem = chatListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == lastIndex }
        return todayAgriItem?.let { it.offset + it.size } ?: currentLastMessageContentBottomPx()
    }

    fun currentInitialDocumentFlowBottomPx(): Int {
        if (chatListItems.isEmpty()) return -1
        val currentMessageIds = messages.mapTo(mutableSetOf()) { message -> message.id }
        var bottomPx = currentLastMessageContentBottomPx()
        fun includeBounds(bounds: Rect?) {
            if (bounds == null || bounds.bottom <= bounds.top) return
            bottomPx = bottomPx.coerceAtLeast((bounds.bottom - messageViewportTopPx).roundToInt())
        }
        messageContentBoundsById.forEach { (messageId, bounds) ->
            if (messageId in currentMessageIds) includeBounds(bounds)
        }
        messageSelectionBoundsById.forEach { (messageId, bounds) ->
            if (messageId in currentMessageIds) includeBounds(bounds)
        }
        bottomPx = bottomPx.coerceAtLeast(currentStreamingContentBottomPx())
        chatListState.layoutInfo.visibleItemsInfo.forEach { item ->
            val timelineItem = chatListItems.getOrNull(item.index)
            if (
                timelineItem is ChatTimelineItem.Message ||
                timelineItem is ChatTimelineItem.TodayAgriCard
            ) {
                bottomPx = bottomPx.coerceAtLeast(item.offset + item.size)
            }
        }
        return bottomPx
    }

    fun currentStreamingLegalBottomPx(): Int {
        return streamingWorklineBottomPx.takeIf { it > 0 } ?: -1
    }
    fun currentStaticBottomTargetPx(): Int {
        return streamingWorklineBottomPx.takeIf { it > 0 } ?: 0
    }
    fun currentUnifiedBottomTargetPx(): Int {
        return if (isStreaming || hasStreamingItem) {
            currentStreamingLegalBottomPx().coerceAtLeast(0)
        } else {
            currentStaticBottomTargetPx()
        }
    }

    fun hasInitialDocumentFlowReachedWorkline(): Boolean {
        val documentContentBottomPx = currentInitialDocumentFlowBottomPx()
        val worklineBottomPx = currentUnifiedBottomTargetPx()
        val measuredReach =
            documentContentBottomPx > 0 &&
                worklineBottomPx > 0 &&
                documentContentBottomPx >= worklineBottomPx
        return measuredReach || chatListState.canScrollForward
    }

    fun hasInitialDocumentFlowSafelyPassedWorkline(): Boolean {
        val documentContentBottomPx = currentInitialDocumentFlowBottomPx()
        val worklineBottomPx = currentUnifiedBottomTargetPx()
        return documentContentBottomPx > 0 &&
            worklineBottomPx > 0 &&
            documentContentBottomPx >= worklineBottomPx + initialWorklineBottomSwitchOverflowPx
    }

    fun shouldUseInitialTopFlowForSend(): Boolean {
        if (shouldStartInitialWorklineTopFlow()) return true
        if (!isInitialWorklineTopFlow()) return false
        return !hasInitialDocumentFlowReachedWorkline()
    }
    fun currentBottomOverflowPx(): Int {
        val lastContentBottom = if (!isStreaming && !hasStreamingItem && hasTodayAgriCard) {
            currentVisualTailContentBottomPx()
        } else {
            currentLastMessageContentBottomPx()
        }
        val desiredBottomPx = currentUnifiedBottomTargetPx()
        if (lastContentBottom <= 0) return Int.MAX_VALUE
        val deltaPx = desiredBottomPx - lastContentBottom
        return abs(deltaPx)
    }
    fun isWithinBottomTolerance(tolerancePx: Int): Boolean {
        val overflowPx = currentBottomOverflowPx()
        return overflowPx != Int.MAX_VALUE && overflowPx <= tolerancePx
    }
    fun isWithinStaticBottomTolerance(): Boolean {
        return !chatListState.canScrollForward &&
            isWithinBottomTolerance(staticBottomPositionTolerancePx)
    }
    val atBottom by remember(staticBottomPositionTolerancePx) {
        derivedStateOf { isWithinStaticBottomTolerance() }
    }
    fun isNearStreamingWorkline(): Boolean {
        if (!isStreaming || !hasStreamingItem) return atBottom
        val worklineBottom = streamingWorklineBottomPx
        if (worklineBottom <= 0) return atBottom
        val contentBottom = currentStreamingContentBottomPx()
        if (contentBottom <= 0) return false
        val deltaPx = contentBottom - worklineBottom
        val lowerTolerancePx = bottomPositionTolerancePx
        val upperTolerancePx = bottomPositionTolerancePx
        return deltaPx in -lowerTolerancePx..upperTolerancePx
    }
    fun isForwardListAtExactBottom(): Boolean {
        if (messages.isEmpty()) return true
        return !chatListState.canScrollForward &&
            isWithinBottomTolerance(bottomPositionTolerancePx)
    }
    fun isAtStreamingWorklineStrict(): Boolean {
        if (!isStreaming || !hasStreamingItem) return atBottom
        return isForwardListAtExactBottom()
    }
    val chatPageSurface = Color(0xFFF8F9FA)
    val appCenterTint = chatPageSurface
    val chromeSurface = Color.White
    val chromeBorder = Color(0xFFD8DADF).copy(alpha = 0.18f)
    val userBubbleColor = Color(0xFF050505)
    val userBubbleBorderColor = Color(0xFF050505)
    var inputChromeMeasured by remember(uiRuntimeResetKey) { mutableStateOf(false) }
    var messageViewportMeasured by remember(uiRuntimeResetKey) { mutableStateOf(false) }
    var composerMeasured by remember(uiRuntimeResetKey) { mutableStateOf(false) }
    var measuredComposerHostHeightPx by remember(uiRuntimeResetKey) { mutableIntStateOf(0) }
    var currentComposerHostHeightPx by remember(uiRuntimeResetKey) { mutableIntStateOf(0) }
    val startupBottomReserveReady by remember {
        derivedStateOf { measuredComposerHostHeightPx > 0 }
    }
    var chatHistoryClearEpoch by remember(uiRuntimeResetKey) {
        mutableIntStateOf(0)
    }
    val chatHistoryClearEpochRef = remember(uiRuntimeResetKey) { AtomicInteger(0) }
    fun advanceChatHistoryClearEpoch() {
        chatHistoryClearEpoch++
        chatHistoryClearEpochRef.incrementAndGet()
    }
    var todayAgriItemSaveInFlightKey by remember(uiRuntimeResetKey) { mutableStateOf("") }
    var todayAgriItemSaveRetryKey by remember(uiRuntimeResetKey) { mutableStateOf("") }
    var todayAgriItemSaveRetryAttempt by remember(uiRuntimeResetKey) { mutableIntStateOf(0) }
    LaunchedEffect(
        shouldShowTodayAgriCard,
        currentTodayAgriCardDay,
        currentTodayAgriCardHasSavedItem,
        remoteSnapshotHydrationComplete,
        shouldHydrateRemoteHistory,
        isStreaming,
        hasStreamingItem,
        hasStartedConversation,
        latestCompletedAssistantTailId,
        todayAgriLocalAnchorMessageId,
        messages,
        todayAgriItemSaveRetryAttempt
    ) {
        if (!shouldShowTodayAgriCard || isStreaming || hasStreamingItem) return@LaunchedEffect
        if (shouldHydrateRemoteHistory && !remoteSnapshotHydrationComplete) return@LaunchedEffect
        val existingMessageIds = messages.mapTo(mutableSetOf()) { it.id }
        val anchorMessageId = resolveTodayAgriVisualAnchorMessageId(
            savedAnchorMessageId = null,
            localAnchorMessageId = todayAgriLocalAnchorMessageId,
            latestCompletedAssistantTailId = latestCompletedAssistantTailId,
            existingMessageIds = existingMessageIds
        ) ?: return@LaunchedEffect
        if (todayAgriLocalAnchorMessageId != anchorMessageId) {
            todayAgriLocalAnchorMessageId = anchorMessageId
        }
        val cardToSave = todayAgriCard?.takeIf { card ->
            todayAgriCardDayKey(card) == currentTodayAgriCardDay &&
                card.isRenderableTodayAgriCard()
        } ?: return@LaunchedEffect
        if (currentTodayAgriCardHasSavedItem) {
            return@LaunchedEffect
        }
        val saveKey = "$currentTodayAgriCardDay|$anchorMessageId"
        if (todayAgriItemSaveInFlightKey == saveKey) return@LaunchedEffect
        val retryAttempt = if (todayAgriItemSaveRetryKey == saveKey) todayAgriItemSaveRetryAttempt else 0
        val saveUserSendEpoch = todayAgriUserSendEpoch
        if (retryAttempt > 0) {
            delay(TODAY_AGRI_ITEM_SAVE_RETRY_DELAY_MS * retryAttempt)
        }
        if (saveUserSendEpoch != todayAgriUserSendEpoch) {
            return@LaunchedEffect
        }
        todayAgriItemSaveInFlightKey = saveKey
        val saveClearEpoch = chatHistoryClearEpoch
        if (hasRemoteHistorySource) {
            val anchorSessionGeneration = SessionApi.currentSessionGenerationOrNull()
            SessionApi.saveTodayAgriItem(
                dayCn = currentTodayAgriCardDay,
                anchorClientMsgId = anchorMessageId,
                sessionGeneration = anchorSessionGeneration
            ) { saved ->
                if (todayAgriItemSaveInFlightKey == saveKey) {
                    todayAgriItemSaveInFlightKey = ""
                }
                if (saved &&
                    saveClearEpoch == chatHistoryClearEpoch &&
                    saveUserSendEpoch == todayAgriUserSendEpoch
                ) {
                    todayAgriItemSaveRetryKey = ""
                    todayAgriItemSaveRetryAttempt = 0
                    todayAgriMainItem = TodayAgriMainItem(
                        day_cn = currentTodayAgriCardDay,
                        anchor_client_msg_id = anchorMessageId,
                        card = cardToSave,
                        updated_at = System.currentTimeMillis()
                    )
                    todayAgriLocalAnchorMessageId = anchorMessageId
                    todayAgriRemoteConfirmedDay = currentTodayAgriCardDay
                } else if (!saved &&
                    saveClearEpoch == chatHistoryClearEpoch &&
                    saveUserSendEpoch == todayAgriUserSendEpoch &&
                    !currentTodayAgriCardHasSavedItem &&
                    retryAttempt < TODAY_AGRI_ITEM_SAVE_MAX_ATTEMPTS
                ) {
                    val nextRetryAttempt =
                        if (todayAgriItemSaveRetryKey == saveKey) {
                            todayAgriItemSaveRetryAttempt + 1
                        } else {
                            1
                        }
                    todayAgriItemSaveRetryKey = saveKey
                    todayAgriItemSaveRetryAttempt = nextRetryAttempt
                }
            }
        } else {
            todayAgriItemSaveInFlightKey = ""
            todayAgriItemSaveRetryKey = ""
            todayAgriItemSaveRetryAttempt = 0
            if (saveUserSendEpoch == todayAgriUserSendEpoch) {
                todayAgriMainItem = TodayAgriMainItem(
                    day_cn = currentTodayAgriCardDay,
                    anchor_client_msg_id = anchorMessageId,
                    card = cardToSave,
                    updated_at = System.currentTimeMillis()
                )
                todayAgriLocalAnchorMessageId = anchorMessageId
            }
        }
    }
    val startupHydrationBarrierSatisfied by remember(
        historyHydrationComplete,
        shouldHydrateRemoteHistory,
        initialLocalMessages.size,
        startupRecoverableUserMessageId,
        hasStartedConversation,
        hasStreamingItem
    ) {
        derivedStateOf {
            historyHydrationComplete ||
                (
                    shouldHydrateRemoteHistory &&
                        initialLocalMessages.isEmpty() &&
                        startupRecoverableUserMessageId == null &&
                        !hasStartedConversation &&
                        !hasStreamingItem
                    )
        }
    }
    val startupLayoutReady by remember(
        startupHydrationBarrierSatisfied,
        messageViewportMeasured,
        inputChromeMeasured,
        composerMeasured,
        bottomBarHeightPx
    ) {
        derivedStateOf {
            startupHydrationBarrierSatisfied &&
                messageViewportMeasured &&
                inputChromeMeasured &&
                composerMeasured &&
                bottomBarHeightPx > 0
        }
    }
    val effectiveBottomBarHeightPx by remember(
        startupLayoutReady,
        bottomBarHeightPx,
        startupBottomBarHeightEstimatePx
    ) {
        derivedStateOf {
            if (startupLayoutReady) {
                bottomBarHeightPx
            } else {
                startupBottomBarHeightEstimatePx
            }
        }
    }
    val shouldRevealMessageList by remember(
        startupHydrationBarrierSatisfied,
        historyHydrationComplete,
        shouldHydrateRemoteHistory,
        messages.size,
        hasTodayAgriCard,
        isStreaming,
        hasStreamingItem,
        hasStartedConversation
    ) {
        derivedStateOf {
            shouldRevealChatMessageList(
                startupHydrationBarrierSatisfied = startupHydrationBarrierSatisfied,
                historyHydrationComplete = historyHydrationComplete,
                shouldHydrateRemoteHistory = shouldHydrateRemoteHistory,
                hasStartedConversation = hasStartedConversation,
                isStreaming = isStreaming,
                hasStreamingItem = hasStreamingItem,
                hasTodayAgriCard = hasTodayAgriCard,
                messageCount = messages.size
            )
        }
    }
    val showWelcomePlaceholder by remember(
        startupHydrationBarrierSatisfied,
        messages.size,
        hasTodayAgriCard,
        hasStreamingItem,
        hasStartedConversation
    ) {
        derivedStateOf {
            shouldShowChatWelcomePlaceholder(
                startupHydrationBarrierSatisfied = startupHydrationBarrierSatisfied,
                hasStartedConversation = hasStartedConversation,
                hasStreamingItem = hasStreamingItem,
                hasTodayAgriCard = hasTodayAgriCard,
                messageCount = messages.size
            )
        }
    }
    LaunchedEffect(
        startupHydrationBarrierSatisfied,
        startupLayoutReady,
        startupBottomReserveReady,
        shouldRevealMessageList,
        showWelcomePlaceholder,
        historyHydrationComplete,
        remoteSnapshotHydrationComplete,
        messages.size,
        hasTodayAgriCard,
        initialBottomSnapDone,
        chatListState.canScrollForward
    ) {
        if (startupUiStateLogged) return@LaunchedEffect
        if (!startupHydrationBarrierSatisfied) return@LaunchedEffect
        if (!startupLayoutReady && (messages.isNotEmpty() || hasTodayAgriCard)) {
            return@LaunchedEffect
        }
        startupUiStateLogged = true
        SessionApi.reportClientLog(
            level = "info",
            event = "ui.chat_startup_state",
            message = "Chat startup state",
            attrs = mapOf(
                "has_local_items" to hasStartupLocalMessages,
                "local_item_count" to initialLocalMessages.size,
                "visible_item_count" to messages.size,
                "has_today_agri_card" to hasTodayAgriCard,
                "remote_history_enabled" to shouldHydrateRemoteHistory,
                "history_hydrated" to historyHydrationComplete,
                "remote_snapshot_hydrated" to remoteSnapshotHydrationComplete,
                "layout_ready" to startupLayoutReady,
                "reserve_ready" to startupBottomReserveReady,
                "list_revealed" to shouldRevealMessageList,
                "show_welcome" to showWelcomePlaceholder,
                "bottom_snap_done" to initialBottomSnapDone,
                "workline_phase" to initialWorklinePhase.name.lowercase(),
                "can_scroll_forward" to chatListState.canScrollForward
            )
        )
    }
    LaunchedEffect(
        initialBottomSnapDone,
        startupBottomReserveReady,
        messages.size,
        hasTodayAgriCard,
        chatListState.canScrollForward
    ) {
        if (!initialBottomSnapDone || startupBottomSnapDoneLogged) return@LaunchedEffect
        startupBottomSnapDoneLogged = true
        SessionApi.reportClientLog(
            level = "info",
            event = "ui.chat_startup_bottom_snap_done",
            message = "Chat startup bottom snap done",
            attrs = mapOf(
                "has_local_items" to hasStartupLocalMessages,
                "visible_item_count" to messages.size,
                "has_today_agri_card" to hasTodayAgriCard,
                "reserve_ready" to startupBottomReserveReady,
                "can_scroll_forward" to chatListState.canScrollForward,
                "bottom_overflow_px" to currentBottomOverflowPx(),
                "workline_phase" to initialWorklinePhase.name.lowercase()
            )
        )
    }
    LaunchedEffect(
        hasTodayAgriCard,
        shouldShowTodayAgriCard,
        currentTodayAgriMainItem?.day_cn ?: currentTodayAgriCardDay,
        todayAgriAfterMessageIdForRender,
        currentTodayAgriCardHasSavedItem,
        shouldRevealMessageList,
        remoteSnapshotHydrationComplete,
        shouldHydrateRemoteHistory,
        chatListItems.size,
        messages.size
    ) {
        if (
            !hasTodayAgriCard ||
            !shouldShowTodayAgriCard ||
            !shouldRevealMessageList
        ) {
            return@LaunchedEffect
        }
        snapshotFlow {
            val layoutInfo = chatListState.layoutInfo
            isTodayAgriCardVisibleInViewport(
                chatListItems = chatListItems,
                visibleItems = layoutInfo.visibleItemsInfo.map { item ->
                    VisibleChatListItem(
                        index = item.index,
                        offset = item.offset,
                        size = item.size
                    )
                },
                viewportStartOffset = layoutInfo.viewportStartOffset,
                viewportEndOffset = layoutInfo.viewportEndOffset,
                minVisiblePx = todayAgriMinVisiblePx,
                coveredBottomPx = currentComposerHostHeightPx
                    .coerceAtLeast(measuredComposerHostHeightPx)
                    .coerceAtLeast(stableComposerBottomBarHeightPx)
            )
        }
            .distinctUntilChanged()
            .filter { it }
            .first()
        val canPersistTodayAgriShownDay =
            !shouldHydrateRemoteHistory ||
                (remoteSnapshotHydrationComplete && currentTodayAgriCardHasSavedItem)
        val shouldPersistTodayAgriShownDay =
            canPersistTodayAgriShownDay && todayAgriMainShownDay != currentTodayAgriCardDay
        val shouldLogTodayAgriVisible = !todayAgriMainCardVisibleLogged
        if (!shouldLogTodayAgriVisible && !shouldPersistTodayAgriShownDay) {
            return@LaunchedEffect
        }
        if (shouldLogTodayAgriVisible) {
            todayAgriMainCardVisibleLogged = true
        }
        todayAgriShownThisRuntime = true
        if (shouldPersistTodayAgriShownDay) {
            todayAgriMainShownDay = currentTodayAgriCardDay
            context.saveTodayAgriMainShownDaySync(chatScopeId, currentTodayAgriCardDay)
        }
        if (!shouldLogTodayAgriVisible) {
            return@LaunchedEffect
        }
        val anchorState = if (todayAgriAfterMessageIdForRender.isNullOrBlank()) "none" else "message"
        SessionApi.reportClientLog(
            level = "info",
            event = "today_agri.main_card_visible",
            message = "Today agri main card visible",
            attrs = mapOf(
                "card_day" to (currentTodayAgriMainItem?.day_cn ?: currentTodayAgriCardDay),
                "visible_item_count" to messages.size,
                "list_item_count" to chatListItems.size,
                "list_revealed" to shouldRevealMessageList,
                "anchor_state" to anchorState,
                "bottom_snap_done" to initialBottomSnapDone,
                "shown_once_day" to currentTodayAgriCardDay,
                "shown_day_persisted" to canPersistTodayAgriShownDay
            )
        )
    }
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var inputSelectionToolbarState by remember(uiRuntimeResetKey) {
        mutableStateOf<InputSelectionToolbarState?>(null)
    }
    var pendingInputSelectionToolbarState by remember(uiRuntimeResetKey) {
        mutableStateOf<PendingInputSelectionToolbarState?>(null)
    }
    var inputSelectionMenuBoundsInRoot by remember(uiRuntimeResetKey) { mutableStateOf<Rect?>(null) }
    var inputFieldBoundsInWindow by composerRuntime.inputFieldBoundsInWindow
    var uiCopyPreviewVisible by remember(uiRuntimeResetKey) { mutableStateOf(false) }
    var hamburgerMenuVisible by remember(uiRuntimeResetKey) { mutableStateOf(false) }
    var membershipCenterVisible by remember(uiRuntimeResetKey) { mutableStateOf(false) }
    var membershipLoadState by remember(uiRuntimeResetKey) { mutableStateOf(MembershipLoadState.Idle) }
    var membershipEntitlement by remember(uiRuntimeResetKey) { mutableStateOf<SessionApi.EntitlementSnapshot?>(null) }
    var membershipPurchaseSuccessVisible by remember(uiRuntimeResetKey) { mutableStateOf(false) }
    var membershipRefreshNonce by remember(uiRuntimeResetKey) { mutableIntStateOf(0) }
    var membershipRefreshEpoch by remember(uiRuntimeResetKey) { mutableIntStateOf(0) }
    var todayAgriRefreshDayKey by rememberSaveable(uiRuntimeResetKey) { mutableStateOf(currentChinaDateKey()) }
    LaunchedEffect(uiRuntimeResetKey) {
        while (isActive) {
            delay(TODAY_AGRI_CARD_DAY_REFRESH_POLL_MS)
            val currentDay = currentChinaDateKey()
            if (todayAgriRefreshDayKey != currentDay) {
                todayAgriRefreshDayKey = currentDay
                todayAgriCard = null
                todayAgriMainItem = null
                todayAgriLocalAnchorMessageId = null
                pendingHydratedTodayAgriMainItem = null
                pendingHydratedSnapshot = null
                todayAgriRemoteConfirmedDay = null
                todayAgriItemSaveInFlightKey = ""
                todayAgriShownThisRuntime = false
                todayAgriInsertedThisRuntime = false
                val shouldWaitForNewAssistant = shouldWaitForNextTodayAgriAssistantAfterDayChange(
                    hasStartedConversation = hasStartedConversation,
                    latestCompletedAssistantTailId = latestCompletedAssistantTailId
                )
                todayAgriAwaitingNewCompletedAssistantAfterDayChange = shouldWaitForNewAssistant
                todayAgriDayChangeCompletedAssistantBaselineId = latestCompletedAssistantTailId
                todayAgriAutoInsertSuppressedThisRuntime = shouldWaitForNewAssistant
                todayAgriUserSendEpoch = 0
                todayAgriMainCardLoadedLogged = false
                todayAgriMainCardVisibleLogged = false
            }
        }
    }
    LaunchedEffect(uiRuntimeResetKey) {
        if (!SessionApi.hasBackendConfigured()) return@LaunchedEffect
        if (todayAgriRefreshDayKey != currentChinaDateKey()) {
            todayAgriRefreshDayKey = currentChinaDateKey()
            todayAgriCard = null
            todayAgriMainItem = null
            todayAgriLocalAnchorMessageId = null
            pendingHydratedTodayAgriMainItem = null
            pendingHydratedSnapshot = null
            todayAgriRemoteConfirmedDay = null
            todayAgriItemSaveInFlightKey = ""
            todayAgriShownThisRuntime = false
            todayAgriInsertedThisRuntime = false
            val shouldWaitForNewAssistant = shouldWaitForNextTodayAgriAssistantAfterDayChange(
                hasStartedConversation = hasStartedConversation,
                latestCompletedAssistantTailId = latestCompletedAssistantTailId
            )
            todayAgriAwaitingNewCompletedAssistantAfterDayChange = shouldWaitForNewAssistant
            todayAgriDayChangeCompletedAssistantBaselineId = latestCompletedAssistantTailId
            todayAgriAutoInsertSuppressedThisRuntime = shouldWaitForNewAssistant
            todayAgriUserSendEpoch = 0
            todayAgriMainCardLoadedLogged = false
            todayAgriMainCardVisibleLogged = false
        }
    }
    LaunchedEffect(uiRuntimeResetKey, todayAgriRefreshDayKey) {
        if (!SessionApi.hasBackendConfigured()) return@LaunchedEffect
        val refreshDayKey = todayAgriRefreshDayKey
        val hasRefreshDayItem = currentTodayAgriMainItem?.day_cn == refreshDayKey
        if (
            shouldSkipTodayAgriCardFetch(
                hasRemoteHistorySource = hasRemoteHistorySource,
                todayAgriShownThisRuntime = todayAgriShownThisRuntime,
                shownDayKey = todayAgriMainShownDay,
                refreshDayKey = refreshDayKey,
                hasRefreshDayItem = hasRefreshDayItem
            )
        ) {
            return@LaunchedEffect
        }
        var attempt = 0
        while (isActive && refreshDayKey == currentChinaDateKey()) {
            val card = awaitTodayAgriCard()
            if (
                card != null &&
                normalizeTodayAgriCardDayKey(card.dateCn.orEmpty()) == refreshDayKey
            ) {
                if (!todayAgriMainCardLoadedLogged) {
                    todayAgriMainCardLoadedLogged = true
                    SessionApi.reportClientLog(
                        level = "info",
                        event = "today_agri.main_card_loaded",
                        message = "Today agri main card loaded",
                        attrs = mapOf(
                            "card_day" to normalizeTodayAgriCardDayKey(card.dateCn.orEmpty()),
                            "item_count" to (card.items?.size ?: 0),
                            "attempt" to (attempt + 1),
                            "history_hydrated" to historyHydrationComplete
                        )
                    )
                }
                context.saveTodayAgriCardCacheSync(chatScopeId, card)
                todayAgriCard = card
                todayAgriRemoteConfirmedDay = refreshDayKey
                return@LaunchedEffect
            }
            attempt++
            delay(
                if (attempt == 1) {
                    TODAY_AGRI_CARD_FETCH_RETRY_DELAY_MS
                } else {
                    TODAY_AGRI_CARD_DAY_REFRESH_POLL_MS
                }
            )
        }
    }
    BindComposerRuntimeEffects(
        inputChromeMeasured = inputChromeMeasured,
        inputText = input.value.text,
        inputFieldFocused = inputFieldFocused,
        composerSettlingMinHeightPxState = composerRuntime.composerSettlingMinHeightPx,
        composerSettlingChromeHeightPxState = composerRuntime.composerSettlingChromeHeightPx,
        sendUiSettling = sendUiSettling,
        imeVisible = imeVisible,
        bottomBarHeightPxState = scrollRuntime.bottomBarHeightPx,
        inputChromeRowHeightPx = inputChromeRowHeightPx,
        stableBottomBarHeightPx = stableComposerBottomBarHeightPx,
        jitterTolerancePx = BOTTOM_BAR_HEIGHT_JITTER_TOLERANCE_PX
    )
    val streamingExtraReservedHeightPx = 0
    val jumpButtonBottomPadding = with(density) {
        effectiveBottomBarHeightPx.toDp() + JUMP_BUTTON_EXTRA_BOTTOM_CLEARANCE
    }
    val jumpButtonBottomSafetyZonePx = with(density) {
        JUMP_BUTTON_BOTTOM_SAFETY_ZONE.roundToPx()
    }
    val keyboardVisibleForJumpButton = WindowInsets.isImeVisible
    val forwardListAwayFromJumpButtonBottom by remember(
        chatListState,
        jumpButtonBottomSafetyZonePx,
        chatListItems.size,
        hasStreamingItem,
        isStreaming
    ) {
        derivedStateOf {
            if (!chatListState.canScrollForward) {
                false
            } else {
                val targetBottomPx = currentUnifiedBottomTargetPx()
                val targetIndex = bottomAnchorIndexOrMinusOne()
                val latestItem = chatListState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == targetIndex }
                val distanceFromBottomPx = latestItem
                    ?.let { (it.offset + it.size - targetBottomPx).coerceAtLeast(0) }
                    ?: Int.MAX_VALUE
                distanceFromBottomPx > jumpButtonBottomSafetyZonePx
            }
        }
    }
    var jumpButtonUserScrollSignal by remember(uiRuntimeResetKey) { mutableIntStateOf(0) }
    var jumpButtonSawUserListMotion by remember(uiRuntimeResetKey) { mutableStateOf(false) }
    val userDrivenListMotionForJumpButton =
        !programmaticScroll && (chatListUserDragging || recyclerScrollInProgress)
    val userAwayFromBottomForJumpButton by remember(
        forwardListAwayFromJumpButtonBottom,
        chatListItems.size
    ) {
        derivedStateOf {
            chatListItems.isNotEmpty() && forwardListAwayFromJumpButtonBottom
        }
    }
    LaunchedEffect(
        startupLayoutReady,
        shouldRevealMessageList,
        userDrivenListMotionForJumpButton,
        userAwayFromBottomForJumpButton,
        keyboardVisibleForJumpButton,
        suppressJumpButtonForLifecycleResume,
        recyclerFirstVisibleItemIndex,
        recyclerFirstVisibleItemScrollOffset
    ) {
        if (userDrivenListMotionForJumpButton) {
            jumpButtonSawUserListMotion = true
            jumpButtonPulseVisible = false
            return@LaunchedEffect
        }
        if (!jumpButtonSawUserListMotion) return@LaunchedEffect
        jumpButtonSawUserListMotion = false
        withFrameNanos { }
        if (
            startupLayoutReady &&
            shouldRevealMessageList &&
            !keyboardVisibleForJumpButton &&
            !suppressJumpButtonForLifecycleResume &&
            userAwayFromBottomForJumpButton
        ) {
            jumpButtonUserScrollSignal++
        }
    }
    val showJumpButton by remember(
        startupLayoutReady,
        forwardListAwayFromJumpButtonBottom,
        userDrivenListMotionForJumpButton,
        keyboardVisibleForJumpButton,
        suppressJumpButtonForLifecycleResume,
        messages.size,
        initialWorklinePhase
    ) {
        derivedStateOf {
            startupLayoutReady &&
                !shouldSuppressAutomaticBottomAnchor() &&
                !keyboardVisibleForJumpButton &&
                !userDrivenListMotionForJumpButton &&
                !suppressJumpButtonForLifecycleResume &&
                userAwayFromBottomForJumpButton
        }
    }
    val effectiveJumpButtonVisible by remember(
        showJumpButton,
        jumpButtonPulseVisible
    ) {
        derivedStateOf {
            showJumpButton && jumpButtonPulseVisible
        }
    }
    BindJumpButtonPulseEffect(
        showJumpButton = showJumpButton,
        userScrollSignal = jumpButtonUserScrollSignal,
        jumpButtonPulseVisibleState = scrollRuntime.jumpButtonPulseVisible,
        autoHideMs = JUMP_BUTTON_AUTO_HIDE_MS
    )
    LaunchedEffect(
        startupHydrationBarrierSatisfied,
        shouldRevealMessageList,
        showWelcomePlaceholder,
        hasStartedConversation,
        messages.size,
        hasStreamingItem
    ) {
        LaunchUiGate.chatReady =
            startupHydrationBarrierSatisfied &&
                (shouldRevealMessageList || showWelcomePlaceholder)
    }
    var messageSelectionToolbarState by remember(uiRuntimeResetKey) {
        mutableStateOf<MessageSelectionToolbarState?>(null)
    }
    var pendingMessageSelectionToolbarState by remember(uiRuntimeResetKey) {
        mutableStateOf<PendingMessageSelectionToolbarState?>(null)
    }
    var messageSelectionResetEpoch by remember(uiRuntimeResetKey) { mutableIntStateOf(0) }
    fun currentSelectionMessageBounds(state: MessageSelectionToolbarState): Rect? =
        messageSelectionBoundsById[state.messageId] ?: messageSelectionBoundsCacheById[state.messageId]

    fun buildResolvedMessageSelectionToolbarState(
        messageId: String,
        rect: Rect,
        bounds: Rect,
        onCopyRequested: (() -> Unit)?,
        onCopyFullRequested: (() -> Unit)?
    ): MessageSelectionToolbarState {
        val width = bounds.width.coerceAtLeast(1f)
        val height = bounds.height.coerceAtLeast(1f)
        return MessageSelectionToolbarState(
            messageId = messageId,
            anchorX = rect.center.x.roundToInt(),
            anchorY = rect.top.roundToInt(),
            selectionBottomY = rect.bottom.roundToInt(),
            anchorXRatio = ((rect.center.x - bounds.left) / width).coerceIn(0f, 1f),
            selectionTopRatio = ((rect.top - bounds.top) / height).coerceIn(0f, 1f),
            selectionBottomRatio = ((rect.bottom - bounds.top) / height).coerceIn(0f, 1f),
            onCopyRequested = onCopyRequested,
            onCopyFullRequested = onCopyFullRequested
        )
    }

    fun resolveMessageSelectionToolbarState(state: MessageSelectionToolbarState): MessageSelectionToolbarState? {
        val bounds = currentSelectionMessageBounds(state) ?: return null
        val width = bounds.width.coerceAtLeast(1f)
        val height = bounds.height.coerceAtLeast(1f)
        return state.copy(
            anchorX = (bounds.left + width * state.anchorXRatio).roundToInt(),
            anchorY = (bounds.top + height * state.selectionTopRatio).roundToInt(),
            selectionBottomY = (bounds.top + height * state.selectionBottomRatio).roundToInt()
        )
    }

    fun clearMessageSelection() {
        messageSelectionToolbarState = null
        pendingMessageSelectionToolbarState = null
        messageSelectionResetEpoch++
    }

    fun applyPendingMessageSelectionToolbarIfReady(messageId: String, bounds: Rect) {
        val pending = pendingMessageSelectionToolbarState ?: return
        if (pending.messageId != messageId) return
        pendingMessageSelectionToolbarState = null
        messageSelectionToolbarState = buildResolvedMessageSelectionToolbarState(
            messageId = pending.messageId,
            rect = pending.rect,
            bounds = bounds,
            onCopyRequested = pending.onCopyRequested,
            onCopyFullRequested = pending.onCopyFullRequested
        )
    }

    fun shouldTrackMessageSelectionBounds(messageId: String): Boolean {
        return messageSelectionToolbarState?.messageId == messageId ||
            pendingMessageSelectionToolbarState?.messageId == messageId
    }

    fun updateMessageSelectionBoundsIfNeeded(messageId: String, bounds: Rect) {
        messageSelectionBoundsCacheById[messageId] = bounds
        if (!shouldTrackMessageSelectionBounds(messageId)) {
            if (messageSelectionBoundsById.containsKey(messageId)) {
                messageSelectionBoundsById.remove(messageId)
            }
            return
        }
        if (messageSelectionBoundsById[messageId] != bounds) {
            messageSelectionBoundsById[messageId] = bounds
        }
    }

    fun shouldTrackMessageContentBounds(messageId: String): Boolean {
        return isInitialWorklineTopFlow() ||
            messageId == messages.lastOrNull()?.id ||
            messageId == streamingMessageId ||
            messageId == pendingStreamingFinalizeMessageId
    }

    fun updateMessageContentBounds(messageId: String, bounds: Rect?) {
        if (!shouldTrackMessageContentBounds(messageId)) {
            if (messageContentBoundsById.containsKey(messageId)) {
                messageContentBoundsById.remove(messageId)
            }
            return
        }
        if (bounds != null) {
            if (messageContentBoundsById[messageId] != bounds) {
                messageContentBoundsById[messageId] = bounds
            }
        } else if (messageContentBoundsById.containsKey(messageId)) {
            messageContentBoundsById.remove(messageId)
        }
    }

    fun selectionDismissTapModifier(vararg keys: Any): Modifier =
        Modifier.pointerInput(*keys) {
            detectTapGestures(onTap = { clearMessageSelection() })
        }

    val activeMessageSelectionState =
        messageSelectionToolbarState?.let(::resolveMessageSelectionToolbarState)
    val hasActiveMessageSelection = activeMessageSelectionState != null
    val activeMessageSelectionMessageId = activeMessageSelectionState?.messageId
    val messageSelectionColors = remember {
        TextSelectionColors(
            handleColor = CHAT_SELECTION_HANDLE_COLOR,
            backgroundColor = CHAT_SELECTION_BACKGROUND_COLOR
        )
    }
    fun clearInputSelectionToolbar() {
        inputSelectionToolbarState = null
        pendingInputSelectionToolbarState = null
        inputSelectionMenuBoundsInRoot = null
    }

    fun buildResolvedInputSelectionToolbarState(
        rect: Rect,
        bounds: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ): InputSelectionToolbarState {
        val width = bounds.width.coerceAtLeast(1f)
        return InputSelectionToolbarState(
            anchorXRatio = ((rect.center.x - bounds.left) / width).coerceIn(0f, 1f),
            onCopyRequested = onCopyRequested,
            onPasteRequested = onPasteRequested,
            onCutRequested = onCutRequested,
            onSelectAllRequested = onSelectAllRequested
        )
    }

    fun applyPendingInputSelectionToolbarIfReady(bounds: Rect) {
        val pending = pendingInputSelectionToolbarState ?: return
        pendingInputSelectionToolbarState = null
        inputSelectionToolbarState = buildResolvedInputSelectionToolbarState(
            rect = pending.rect,
            bounds = bounds,
            onCopyRequested = pending.onCopyRequested,
            onPasteRequested = pending.onPasteRequested,
            onCutRequested = pending.onCutRequested,
            onSelectAllRequested = pending.onSelectAllRequested
        )
    }
    fun copyTextToClipboard(label: String, text: String) {
        val normalized = text.trim()
        if (normalized.isEmpty()) return
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(label, normalized))
    }

    fun buildInputSelectionTextToolbar(): TextToolbar {
        return object : TextToolbar {
            override val status: TextToolbarStatus
                get() =
                    if (inputSelectionToolbarState != null) {
                        TextToolbarStatus.Shown
                    } else {
                        TextToolbarStatus.Hidden
                    }

            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {
                val bounds = inputFieldBoundsInWindow
                if (bounds == null) {
                    pendingInputSelectionToolbarState = PendingInputSelectionToolbarState(
                        rect = rect,
                        onCopyRequested = onCopyRequested,
                        onPasteRequested = onPasteRequested,
                        onCutRequested = onCutRequested,
                        onSelectAllRequested = onSelectAllRequested
                    )
                    return
                }
                pendingInputSelectionToolbarState = null
                inputSelectionToolbarState = buildResolvedInputSelectionToolbarState(
                    rect = rect,
                    bounds = bounds,
                    onCopyRequested = onCopyRequested,
                    onPasteRequested = onPasteRequested,
                    onCutRequested = onCutRequested,
                    onSelectAllRequested = onSelectAllRequested
                )
            }

            override fun hide() {
                clearInputSelectionToolbar()
            }
        }
    }

    fun buildMessageSelectionTextToolbar(
        messageId: String,
        role: ChatRole,
        fullCopyText: String
    ): TextToolbar {
        return object : TextToolbar {
            override val status: TextToolbarStatus
                get() =
                    if (messageSelectionToolbarState?.messageId == messageId) {
                        TextToolbarStatus.Shown
                    } else {
                        TextToolbarStatus.Hidden
                    }

            @Suppress("UNUSED_PARAMETER")
            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {
                val onCopyFull = {
                    copyTextToClipboard(
                        label = if (role == ChatRole.USER) "user_message_full_copy" else "assistant_message_full_copy",
                        text = fullCopyText
                    )
                }
                val bounds = messageSelectionBoundsById[messageId] ?: messageSelectionBoundsCacheById[messageId]
                if (bounds == null) {
                    pendingMessageSelectionToolbarState = PendingMessageSelectionToolbarState(
                        messageId = messageId,
                        rect = rect,
                        onCopyRequested = onCopyRequested,
                        onCopyFullRequested = onCopyFull
                    )
                    return
                }
                val nextState = buildResolvedMessageSelectionToolbarState(
                    messageId = messageId,
                    rect = rect,
                    bounds = bounds,
                    onCopyRequested = onCopyRequested,
                    onCopyFullRequested = onCopyFull
                )
                val resolvedState = resolveMessageSelectionToolbarState(nextState) ?: nextState
                pendingMessageSelectionToolbarState = null
                messageSelectionToolbarState = resolvedState
            }

            override fun hide() {
                if (pendingMessageSelectionToolbarState?.messageId == messageId) {
                    pendingMessageSelectionToolbarState = null
                }
                if (messageSelectionToolbarState?.messageId == messageId) {
                    messageSelectionToolbarState = null
                }
            }
        }
    }
    BackHandler(
        enabled = attachmentMenuVisible ||
            hamburgerMenuVisible ||
            membershipCenterVisible ||
            membershipPurchaseSuccessVisible ||
            messageSelectionToolbarState != null ||
            inputSelectionToolbarState != null
    ) {
        when {
            membershipPurchaseSuccessVisible -> membershipPurchaseSuccessVisible = false
            membershipCenterVisible -> membershipCenterVisible = false
            hamburgerMenuVisible -> hamburgerMenuVisible = false
            attachmentMenuVisible -> attachmentMenuVisible = false
            inputSelectionToolbarState != null -> {
                clearInputSelectionToolbar()
                focusManager.clearFocus(force = true)
            }
            messageSelectionToolbarState != null -> clearMessageSelection()
        }
    }
    fun performButtonHaptic() {
        val handled = view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (!handled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    suspend fun refreshMembershipEntitlement() {
        val requestEpoch = membershipRefreshEpoch + 1
        membershipRefreshEpoch = requestEpoch
        if (membershipEntitlement == null) {
            membershipLoadState = MembershipLoadState.Loading
        }
        val entitlement = awaitMembershipEntitlement()
        if (requestEpoch != membershipRefreshEpoch) {
            return
        }
        membershipLoadState = if (entitlement != null) {
            membershipEntitlement = entitlement
            if (
                (entitlement.dailyRemaining ?: 0) > 0 ||
                (entitlement.upgradeRemaining ?: 0) > 0 ||
                (entitlement.topupRemaining ?: 0) > 0
            ) {
                quotaExhaustedDayKey = null
            }
            MembershipLoadState.Loaded
        } else {
            MembershipLoadState.Failed
        }
    }

    LaunchedEffect(membershipCenterVisible, uiRuntimeResetKey) {
        if (!membershipCenterVisible) return@LaunchedEffect
        refreshMembershipEntitlement()
    }

    LaunchedEffect(uiRuntimeResetKey, historyHydrationComplete) {
        if (!historyHydrationComplete) return@LaunchedEffect
        if (!SessionApi.hasBackendConfigured()) return@LaunchedEffect
        refreshMembershipEntitlement()
    }

    LaunchedEffect(membershipRefreshNonce, uiRuntimeResetKey) {
        if (membershipRefreshNonce <= 0) return@LaunchedEffect
        refreshMembershipEntitlement()
    }

    LaunchedEffect(uiRuntimeResetKey) {
        mainHandler.removeCallbacksAndMessages(null)
        streamRevealJob?.cancel()
        streamRevealJob = null
        remoteRecoveryJob?.cancel()
        remoteRecoveryJob = null
        remoteRecoverySourceUserMessageId = null
        SessionApi.resetUiRuntimeForCleanState()
        isStreaming = false
        streamingMessageId = null
        streamingMessageContent = ""
        streamingRevealBuffer = ""
        streamingBackgrounded = false
        pendingStreamingFinalizeMessageId = null
        pendingStreamingFinalizeShouldRestoreBottomAnchor = false
        anchoredUserMessageId = null
        initialWorklinePhase = restoredStartupWorklinePhase(
            messageCount = initialLocalMessages.size,
            hasTodayAgriVisualContent = initialTodayAgriCardIsRenderable,
            persistedInitialWorklineOwned = initialLocalSnapshot.initialWorklineOwned
        )
        inputLimitHintVisible = false
        composerStatusHintVisible = false
        composerStatusHintText = ""
        inputFieldFocused = false
        suppressInputCursor = false
        selectedComposerImages.clear()
        attachmentMenuVisible = false
        imageSendInProgress = false
        inputContentHeightPx = startupInputContentHeightEstimatePx
        composerSettlingMinHeightPx = 0
        composerSettlingChromeHeightPx = 0
        inputFieldBoundsInWindow = null
        sendUiSettling = false
        sendStartViewportHeightPx = 0
        sendStartAnchorActive = false
        initialBottomSnapDone = false
        suppressJumpButtonForLifecycleResume = false
        clearInputSelectionToolbar()
        focusManager.clearFocus(force = true)
        LaunchUiGate.chatReady = false
    }

    DisposableEffect(uiRuntimeResetKey) {
        onDispose {
            PendingChatSendStore.userMessageIdsForScope(context, chatScopeId)
                .forEach(PendingChatSendRuntime::markInactive)
            mainHandler.removeCallbacksAndMessages(null)
            streamRevealJob?.cancel()
            remoteRecoveryJob?.cancel()
            SessionApi.resetUiRuntimeForCleanState()
        }
    }

    fun showComposerStatusHint(text: String) {
        composerStatusHintText = text
        composerStatusHintTick++
    }

    fun addComposerImageUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val remainingSlots = COMPOSER_MAX_IMAGE_COUNT - selectedComposerImages.size
        if (remainingSlots <= 0) {
            showComposerStatusHint(COMPOSER_IMAGE_COUNT_HINT)
            return
        }
        attachmentMenuVisible = false
        suppressInputCursor = false
        val selectedUris = uris.take(remainingSlots)
        val importClearEpoch = chatHistoryClearEpoch
        snackbarScope.launch {
            val importedImages = withContext(Dispatchers.IO) {
                selectedUris.mapNotNull { uri ->
                    context.importComposerImageToPrivateStorage(uri)
                }
            }
            if (importClearEpoch != chatHistoryClearEpoch) {
                withContext(Dispatchers.IO) {
                    importedImages.forEach(context::deleteComposerImageAttachment)
                }
                return@launch
            }
            if (importedImages.isEmpty()) {
                showComposerStatusHint(ImageUploader.DECODE_FAIL_MESSAGE)
                return@launch
            }
            val latestRemainingSlots = COMPOSER_MAX_IMAGE_COUNT - selectedComposerImages.size
            if (latestRemainingSlots <= 0) {
                withContext(Dispatchers.IO) {
                    importedImages.forEach(context::deleteComposerImageAttachment)
                }
                showComposerStatusHint(COMPOSER_IMAGE_COUNT_HINT)
                return@launch
            }
            val imagesToAdd = importedImages.take(latestRemainingSlots)
            val overflowImages = importedImages.drop(latestRemainingSlots)
            if (overflowImages.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    overflowImages.forEach(context::deleteComposerImageAttachment)
                }
            }
            selectedComposerImages.addAll(imagesToAdd)
            if (overflowImages.isNotEmpty() || uris.size > remainingSlots) {
                showComposerStatusHint(COMPOSER_IMAGE_COUNT_HINT)
            }
        }
    }

    val singlePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            addComposerImageUris(listOf(uri))
        }
    }
    val photoPickerTwoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(2)
    ) { uris ->
        addComposerImageUris(uris)
    }
    val photoPickerThreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(3)
    ) { uris ->
        addComposerImageUris(uris)
    }
    val photoPickerFourLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(COMPOSER_MAX_IMAGE_COUNT)
    ) { uris ->
        addComposerImageUris(uris)
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = pendingCameraImageUriString?.let(Uri::parse)
        val galleryBacked = pendingCameraImageGalleryBacked
        val temporaryFilePath = pendingCameraImageTemporaryFilePath
        pendingCameraImageUriString = null
        pendingCameraImageGalleryBacked = false
        pendingCameraImageTemporaryFilePath = null
        if (uri != null) {
            context.revokeComposerCameraUri(uri)
        }
        val success = result.resultCode == Activity.RESULT_OK
        if (success && uri != null) {
            val importClearEpoch = chatHistoryClearEpoch
            snackbarScope.launch {
                val importedImage = withContext(Dispatchers.IO) {
                    val imported = context.importComposerImageToPrivateStorage(uri)
                    if (imported != null) {
                        if (galleryBacked) {
                            if (!context.publishGalleryComposerCameraImage(uri)) {
                                context.deleteGalleryComposerCameraImage(uri)
                            }
                        } else {
                            context.saveComposerCameraImageToGallery(uri)
                        }
                    } else if (galleryBacked) {
                        context.deleteGalleryComposerCameraImage(uri)
                    }
                    if (!galleryBacked) {
                        deleteTemporaryComposerCameraImage(temporaryFilePath)
                    }
                    imported
                }
                if (importClearEpoch != chatHistoryClearEpoch) {
                    if (importedImage != null) {
                        withContext(Dispatchers.IO) {
                            context.deleteComposerImageAttachment(importedImage)
                        }
                    }
                    return@launch
                }
                if (importedImage == null) {
                    showComposerStatusHint(ImageUploader.DECODE_FAIL_MESSAGE)
                    return@launch
                }
                val latestRemainingSlots = COMPOSER_MAX_IMAGE_COUNT - selectedComposerImages.size
                if (latestRemainingSlots <= 0) {
                    withContext(Dispatchers.IO) {
                        context.deleteComposerImageAttachment(importedImage)
                    }
                    showComposerStatusHint(COMPOSER_IMAGE_COUNT_HINT)
                    return@launch
                }
                selectedComposerImages.add(importedImage)
            }
        } else if (uri != null) {
            snackbarScope.launch {
                withContext(Dispatchers.IO) {
                    if (galleryBacked) {
                        context.deleteGalleryComposerCameraImage(uri)
                    } else {
                        deleteTemporaryComposerCameraImage(temporaryFilePath)
                    }
                }
            }
        }
    }

    fun launchComposerCamera() {
        if (selectedComposerImages.size >= COMPOSER_MAX_IMAGE_COUNT) {
            showComposerStatusHint(COMPOSER_IMAGE_COUNT_HINT)
            return
        }
        val target = context.createComposerCameraImageTarget()
        if (target == null) {
            showComposerStatusHint(CAMERA_OPEN_FAILED_HINT_TEXT)
            return
        }
        pendingCameraImageUriString = target.uri.toString()
        pendingCameraImageGalleryBacked = target.galleryBacked
        pendingCameraImageTemporaryFilePath = target.temporaryFilePath
        attachmentMenuVisible = false
        val cameraIntent = buildComposerCameraIntent(target.uri)
        context.grantComposerCameraUri(target.uri, cameraIntent)
        runCatching {
            cameraLauncher.launch(cameraIntent)
        }.onFailure {
            context.revokeComposerCameraUri(target.uri)
            pendingCameraImageUriString = null
            pendingCameraImageGalleryBacked = false
            pendingCameraImageTemporaryFilePath = null
            if (target.galleryBacked) {
                snackbarScope.launch {
                    withContext(Dispatchers.IO) {
                        context.deleteGalleryComposerCameraImage(target.uri)
                    }
                }
            } else {
                snackbarScope.launch {
                    withContext(Dispatchers.IO) {
                        deleteTemporaryComposerCameraImage(target.temporaryFilePath)
                    }
                }
            }
            showComposerStatusHint(CAMERA_OPEN_FAILED_HINT_TEXT)
        }
    }

    fun launchComposerPhotoPicker() {
        val remainingSlots = COMPOSER_MAX_IMAGE_COUNT - selectedComposerImages.size
        if (remainingSlots <= 0) {
            showComposerStatusHint(COMPOSER_IMAGE_COUNT_HINT)
            return
        }
        attachmentMenuVisible = false
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        when (remainingSlots) {
            1 -> singlePhotoPickerLauncher.launch(request)
            2 -> photoPickerTwoLauncher.launch(request)
            3 -> photoPickerThreeLauncher.launch(request)
            else -> photoPickerFourLauncher.launch(request)
        }
    }

    fun showQuotaExhaustedHint() {
        quotaExhaustedDayKey = currentQuotaDayKey()
        showComposerStatusHint(QUOTA_EXHAUSTED_HINT_TEXT)
    }

    fun pruneMessageRuntimeState() {
        val currentMessageIds = messages.mapTo(mutableSetOf()) { it.id }
        failedUserMessageStates.keys
            .toList()
            .filterNot(currentMessageIds::contains)
            .forEach(failedUserMessageStates::remove)
        failedAssistantMessageStates.keys
            .toList()
            .filterNot(currentMessageIds::contains)
            .forEach(failedAssistantMessageStates::remove)
        messageSelectionBoundsCacheById.keys
            .toList()
            .filterNot(currentMessageIds::contains)
            .forEach(messageSelectionBoundsCacheById::remove)
        messageSelectionBoundsById.keys
            .toList()
            .filterNot(currentMessageIds::contains)
            .forEach(messageSelectionBoundsById::remove)
        messageContentBoundsById.keys
            .toList()
            .filterNot(currentMessageIds::contains)
            .forEach(messageContentBoundsById::remove)
    }

    fun persistableMessagesSnapshot(): List<ChatMessage> {
        val transientAssistantId = streamingMessageId
        val finalizedAssistantId = pendingStreamingFinalizeMessageId
        return messages.filterNot { message ->
            val isTransientStreamingAssistant =
                transientAssistantId != null &&
                    message.id == transientAssistantId &&
                    isStreaming &&
                    finalizedAssistantId != message.id
            val isFailedAssistantPlaceholder =
                message.role == ChatRole.ASSISTANT &&
                    message.content.isBlank() &&
                    failedAssistantMessageStates.containsKey(message.id)
            message.role == ChatRole.ASSISTANT &&
                (
                    (message.content.isBlank() && !isFailedAssistantPlaceholder) ||
                        isTransientStreamingAssistant
                    )
        }
    }

    fun persistableLocalChatWindowSnapshot(): LocalChatWindowSnapshot {
        val persistedMessages = persistableMessagesSnapshot()
        val persistedMessageIds = persistedMessages.mapTo(mutableSetOf()) { it.id }
        return sanitizeLocalChatWindowSnapshot(
            LocalChatWindowSnapshot(
                messages = persistedMessages,
                failedUserMessageStates = failedUserMessageStates
                    .filterKeys(persistedMessageIds::contains),
                failedAssistantMessageStates = failedAssistantMessageStates
                    .filter { (messageId, state) ->
                        messageId in persistedMessageIds &&
                            state.sourceUserMessageId in persistedMessageIds
                    },
                initialWorklineOwned = when {
                    persistedMessages.isEmpty() -> null
                    initialWorklinePhase == InitialWorklinePhase.WorklineOwned -> true
                    else -> false
                }
            )
        )
    }

    fun currentRetainedComposerImageUris(): Set<String> = buildSet {
        messages.forEach { message ->
            message.imageUris.orEmpty().forEach(::add)
        }
        selectedComposerImages.forEach { image ->
            add(image.uri)
        }
        PendingChatSendStore.retainedImageUris(context).forEach(::add)
    }

    fun upsertUserMessage(
        messageId: String,
        content: String,
        imageUris: List<String> = emptyList(),
        imageUrls: List<String> = emptyList(),
        todayAgriContextDay: String? = null
    ) {
        val existingTodayAgriContextDay = messages
            .firstOrNull { it.id == messageId && it.role == ChatRole.USER }
            ?.todayAgriContextDay
        val finalMessage = ChatMessage(
            id = messageId,
            role = ChatRole.USER,
            content = content,
            imageUris = imageUris.takeIf { it.isNotEmpty() },
            imageUrls = imageUrls.takeIf { it.isNotEmpty() },
            todayAgriContextDay = todayAgriContextDay ?: existingTodayAgriContextDay
        )
        val existingIndex = messages.indexOfFirst { it.id == messageId }
        if (existingIndex >= 0) {
            if (messages[existingIndex] != finalMessage) {
                messages[existingIndex] = finalMessage
            }
        } else {
            messages.add(finalMessage)
        }
    }

    fun upsertAssistantMessagePlaceholder(messageId: String, sourceUserMessageId: String) {
        val placeholder = ChatMessage(messageId, ChatRole.ASSISTANT, "")
        val existingIndex = messages.indexOfFirst { it.id == messageId }
        if (existingIndex >= 0) {
            if (messages[existingIndex] != placeholder) {
                messages[existingIndex] = placeholder
            }
            return
        }
        val userIndex = messages.indexOfFirst { it.id == sourceUserMessageId && it.role == ChatRole.USER }
        if (userIndex >= 0) {
            messages.add(userIndex + 1, placeholder)
        } else {
            messages.add(placeholder)
        }
    }

    fun removeMessageById(messageId: String) {
        val index = messages.indexOfFirst { it.id == messageId }
        if (index >= 0) {
            messages.removeAt(index)
        }
    }

    fun clearFailedAssistantStateForUser(messageId: String) {
        failedAssistantMessageStates.entries
            .toList()
            .filter { it.value.sourceUserMessageId == messageId }
            .forEach { (assistantMessageId, _) ->
                failedAssistantMessageStates.remove(assistantMessageId)
                retryingAssistantMessageIds.remove(assistantMessageId)
            }
    }

    fun hasSettledAssistantMessageForUser(sourceUserMessageId: String): Boolean {
        val assistantMessageId = assistantMessageIdForSourceUser(sourceUserMessageId)
        return failedAssistantMessageStates[assistantMessageId] == null &&
            messages.any { it.id == assistantMessageId && it.role == ChatRole.ASSISTANT && it.content.isNotBlank() }
    }

    fun clearStaleFailureAffordancesForNewSend(activeUserMessageId: String) {
        // A new send must not erase older failed bubbles; users may still need their retry entries.
        retryingUserMessageIds.remove(activeUserMessageId)
        retryingAssistantMessageIds.remove(assistantMessageIdForSourceUser(activeUserMessageId))
    }

    fun findFailedUserMessageIdByText(text: String): String? =
        messages.lastOrNull()
            ?.takeIf { message ->
                message.role == ChatRole.USER &&
                    message.content == text &&
                    message.imageUris.orEmpty().isEmpty() &&
                    message.imageUrls.orEmpty().isEmpty() &&
                    failedUserMessageStates.containsKey(message.id)
            }
            ?.id

    fun interruptedHintText(reason: String): String =
        when (reason) {
            "network" -> INTERRUPTED_NETWORK_HINT_TEXT
            "quota" -> QUOTA_EXHAUSTED_HINT_TEXT
            "rate_limit" -> RATE_LIMIT_HINT_TEXT
            "backend_not_configured", "model_unavailable" -> SERVICE_UNAVAILABLE_HINT_TEXT
            "stream_in_progress" -> ACTIVE_STREAM_HINT_TEXT
            "stale_session" -> STALE_SESSION_HINT_TEXT
            else -> INTERRUPTED_FALLBACK_HINT_TEXT
        }

    fun canAttemptRemoteAssistantRecovery(reason: String): Boolean =
        hasRemoteHistorySource &&
            reason !in setOf(
                "quota",
                "rate_limit",
                "auth",
                "bad_request",
                "model_unavailable",
                "stale_session"
            )

    fun shouldShowInterruptedAssistantRetry(reason: String): Boolean =
        reason.isNotBlank()

    fun finalizeInterruptedAssistant(
        sourceUserMessageId: String,
        assistantMessageId: String,
        finalContent: String,
        reason: String,
        showHint: Boolean = true
    ) {
        retryingAssistantMessageIds.remove(assistantMessageId)
        if (reason == "quota") {
            quotaExhaustedDayKey = currentQuotaDayKey()
        }
        val showAssistantRetry = shouldShowInterruptedAssistantRetry(reason)
        if (finalContent.isNotBlank()) {
            applyCompletedAssistantMessageInPlace(
                target = messages,
                messageId = assistantMessageId,
                content = finalContent
            )
        } else if (showAssistantRetry) {
            upsertAssistantMessagePlaceholder(
                messageId = assistantMessageId,
                sourceUserMessageId = sourceUserMessageId
            )
            if (showHint) {
                showComposerStatusHint(interruptedHintText(reason))
            }
        } else {
            removeMessageById(assistantMessageId)
            if (showHint) {
                showComposerStatusHint(interruptedHintText(reason))
            }
        }
        if (showAssistantRetry) {
            failedAssistantMessageStates[assistantMessageId] = FailedAssistantMessageState(
                sourceUserMessageId = sourceUserMessageId,
                reason = reason
            )
        } else {
            failedAssistantMessageStates.remove(assistantMessageId)
        }
        persistTick++
    }

    var stopStreamingForRemoteRecovery: () -> Unit = {}

    fun applyRecoveredAssistantRound(
        sourceUserMessageId: String,
        recoveredRound: ARound,
        resetRecoveryJob: Boolean = true,
        interruptActiveStream: Boolean = true,
        expectedClearEpoch: Int = chatHistoryClearEpoch
    ) {
        if (expectedClearEpoch != chatHistoryClearEpoch) return
        if (resetRecoveryJob) {
            remoteRecoveryJob?.cancel()
            remoteRecoveryJob = null
            remoteRecoverySourceUserMessageId = null
        }
        if (interruptActiveStream) {
            SessionApi.cancelCurrentStream()
            streamRevealJob?.cancel()
            streamRevealJob = null
            if (isStreaming) {
                stopStreamingForRemoteRecovery()
            }
        }
        failedUserMessageStates.remove(sourceUserMessageId)
        clearFailedAssistantStateForUser(sourceUserMessageId)
        if (recoveredRound.user.isNotBlank() || recoveredRound.user_images.isNotEmpty()) {
            upsertUserMessage(
                messageId = sourceUserMessageId,
                content = recoveredRound.user,
                imageUrls = recoveredRound.user_images
            )
        }
        applyCompletedAssistantMessageInPlace(
            target = messages,
            messageId = assistantMessageIdForSourceUser(sourceUserMessageId),
            content = recoveredRound.assistant
        )
        val startIndex = trimWindowStartIndex(messages)
        if (startIndex > 0) {
            repeat(startIndex) { messages.removeAt(0) }
        }
        PendingChatSendWorkScheduler.complete(context, chatScopeId, sourceUserMessageId)
        pruneMessageRuntimeState()
        persistTick++
        val persistedSnapshot = persistableLocalChatWindowSnapshot()
        val persistedMessages = persistedSnapshot.messages
        val prewarmMessages = persistedMessages.takeLast(2)
        val persistClearEpoch = expectedClearEpoch
        snackbarScope.launch {
            context.saveLocalChatWindowIfEpoch(
                chatScopeId = chatScopeId,
                snapshot = persistedSnapshot,
                expectedEpoch = persistClearEpoch,
                epochRef = chatHistoryClearEpochRef
            )
            if (persistClearEpoch != chatHistoryClearEpoch) return@launch
            context.clearLocalStreamingDraft(chatScopeId)
            if (persistClearEpoch != chatHistoryClearEpoch) return@launch
            prewarmAssistantMarkdown(prewarmMessages)
        }
    }

    fun scheduleRemoteAssistantRecovery(
        sourceUserMessageId: String,
        assistantMessageId: String,
        fallbackContent: String,
        reason: String,
        maxAttempts: Int = REMOTE_STREAM_RECOVERY_MAX_ATTEMPTS,
        delayMs: Long = REMOTE_STREAM_RECOVERY_DELAY_MS
    ) {
        if (
            remoteRecoverySourceUserMessageId == sourceUserMessageId &&
            remoteRecoveryJob?.isActive == true
        ) {
            return
        }
        remoteRecoveryJob?.cancel()
        remoteRecoverySourceUserMessageId = sourceUserMessageId
        val recoveryClearEpoch = chatHistoryClearEpoch
        remoteRecoveryJob = snackbarScope.launch {
            var recoveredRound: ARound? = null
            for (attempt in 0 until maxAttempts.coerceAtLeast(1)) {
                if (recoveryClearEpoch != chatHistoryClearEpoch) return@launch
                val snapshot = awaitRemoteSnapshot()
                recoveredRound = snapshot
                    ?.findRoundByClientMessageId(sourceUserMessageId)
                    ?.takeIf { it.assistant.isNotBlank() }
                if (recoveredRound != null) break
                if (attempt < maxAttempts.coerceAtLeast(1) - 1) {
                    delay(delayMs)
                }
            }
            if (recoveryClearEpoch != chatHistoryClearEpoch) return@launch
            val matchedRound = recoveredRound
            if (matchedRound != null) {
                applyRecoveredAssistantRound(
                    sourceUserMessageId = sourceUserMessageId,
                    recoveredRound = matchedRound,
                    expectedClearEpoch = recoveryClearEpoch
                )
            } else {
                remoteRecoverySourceUserMessageId = null
                finalizeInterruptedAssistant(
                    sourceUserMessageId = sourceUserMessageId,
                    assistantMessageId = assistantMessageId,
                    finalContent = fallbackContent,
                    reason = reason
                )
                PendingChatSendWorkScheduler.cancelAndRemove(context, chatScopeId, sourceUserMessageId)
            }
        }
    }

    fun scheduleBackgroundRemoteAssistantRecoveryIfNeeded() {
        if (!hasRemoteHistorySource || !isStreaming) return
        if (!pendingStreamingFinalizeMessageId.isNullOrBlank()) return
        val sourceUserMessageId = anchoredUserMessageId ?: return
        val assistantMessageId = streamingMessageId ?: assistantMessageIdForSourceUser(sourceUserMessageId)
        val fallbackContent = normalizeAssistantText(streamingMessageContent)
        scheduleRemoteAssistantRecovery(
            sourceUserMessageId = sourceUserMessageId,
            assistantMessageId = assistantMessageId,
            fallbackContent = fallbackContent,
            reason = "network",
            maxAttempts = REMOTE_BACKGROUND_STREAM_RECOVERY_MAX_ATTEMPTS,
            delayMs = REMOTE_BACKGROUND_STREAM_RECOVERY_DELAY_MS
        )
    }

    fun schedulePendingImageAssistantRecovery(sourceUserMessageIds: List<String>) {
        val pendingSourceUserMessageIds = sourceUserMessageIds
            .filter { it.isNotBlank() }
            .distinct()
        if (pendingSourceUserMessageIds.isEmpty()) return
        val recoveryKey = pendingSourceUserMessageIds.joinToString(separator = "\u001F")
        if (
            remoteRecoverySourceUserMessageId == recoveryKey &&
            remoteRecoveryJob?.isActive == true
        ) {
            return
        }
        remoteRecoveryJob?.cancel()
        remoteRecoverySourceUserMessageId = recoveryKey
        val recoveryClearEpoch = chatHistoryClearEpoch
        remoteRecoveryJob = snackbarScope.launch {
            val remainingSourceUserMessageIds = pendingSourceUserMessageIds.toMutableSet()
            for (attempt in 0 until REMOTE_BACKGROUND_STREAM_RECOVERY_MAX_ATTEMPTS) {
                if (recoveryClearEpoch != chatHistoryClearEpoch) return@launch
                val snapshot = awaitRemoteSnapshot()
                val snapshotAvailable = snapshot != null
                val recoveredRounds = remainingSourceUserMessageIds
                    .mapNotNull { sourceUserMessageId ->
                        snapshot
                            ?.findRoundByClientMessageId(sourceUserMessageId)
                            ?.takeIf { it.assistant.isNotBlank() }
                            ?.let { recoveredRound -> sourceUserMessageId to recoveredRound }
                    }
                recoveredRounds.forEach { (sourceUserMessageId, recoveredRound) ->
                    applyRecoveredAssistantRound(
                        sourceUserMessageId = sourceUserMessageId,
                        recoveredRound = recoveredRound,
                        resetRecoveryJob = false,
                        interruptActiveStream = false,
                        expectedClearEpoch = recoveryClearEpoch
                    )
                    remainingSourceUserMessageIds.remove(sourceUserMessageId)
                }
                val isLastAttempt = attempt >= REMOTE_BACKGROUND_STREAM_RECOVERY_MAX_ATTEMPTS - 1
                val terminalFailedSourceUserMessageIds = remainingSourceUserMessageIds
                    .mapNotNull { sourceUserMessageId ->
                        val reason = PendingChatSendStore.terminalFailureReason(
                            context,
                            chatScopeId,
                            sourceUserMessageId
                        )
                        if (
                            !PendingChatSendStore.has(context, chatScopeId, sourceUserMessageId) &&
                            shouldApplyPendingImageTerminalFailure(
                                terminalFailureReason = reason,
                                snapshotAvailable = snapshotAvailable,
                                isLastAttempt = isLastAttempt
                            )
                        ) {
                            sourceUserMessageId to reason.orEmpty()
                        } else {
                            null
                        }
                }
                terminalFailedSourceUserMessageIds.forEach { (sourceUserMessageId, reason) ->
                    val terminalImageUrls = PendingChatSendStore.terminalFailureImageUrls(
                        context,
                        chatScopeId,
                        sourceUserMessageId
                    )
                    if (terminalImageUrls.isNotEmpty()) {
                        val messageIndex = messages.indexOfFirst { message ->
                            message.id == sourceUserMessageId && message.role == ChatRole.USER
                        }
                        if (messageIndex >= 0) {
                            val message = messages[messageIndex]
                            if (message.imageUrls.orEmpty() != terminalImageUrls) {
                                messages[messageIndex] = message.copy(imageUrls = terminalImageUrls)
                            }
                        }
                    }
                    failedUserMessageStates.putIfAbsent(
                        sourceUserMessageId,
                        reason.ifBlank { "network" }
                    )
                    retryingUserMessageIds.remove(sourceUserMessageId)
                    clearFailedAssistantStateForUser(sourceUserMessageId)
                    PendingChatSendRuntime.markInactive(sourceUserMessageId)
                    PendingChatSendStore.consumeTerminalFailure(context, chatScopeId, sourceUserMessageId)
                    remainingSourceUserMessageIds.remove(sourceUserMessageId)
                }
                if (terminalFailedSourceUserMessageIds.isNotEmpty()) {
                    initialBottomSnapDone = false
                    persistTick++
                }
                if (remainingSourceUserMessageIds.isEmpty()) {
                    initialBottomSnapDone = false
                    return@launch
                }
                if (attempt < REMOTE_BACKGROUND_STREAM_RECOVERY_MAX_ATTEMPTS - 1) {
                    delay(REMOTE_BACKGROUND_STREAM_RECOVERY_DELAY_MS)
                }
            }
            if (recoveryClearEpoch != chatHistoryClearEpoch) return@launch
            if (remoteRecoverySourceUserMessageId == recoveryKey) {
                remoteRecoverySourceUserMessageId = null
                remoteRecoveryJob = null
            }
        }
    }

    fun replaceMessages(newMessages: List<ChatMessage>) {
        val trimmed = sanitizeMessageWindow(newMessages)
        var targetIndex = 0
        while (targetIndex < trimmed.size) {
            val desiredMessage = trimmed[targetIndex]
            if (targetIndex < messages.size && messages[targetIndex].id == desiredMessage.id) {
                if (messages[targetIndex] != desiredMessage) {
                    messages[targetIndex] = desiredMessage
                }
            } else {
                val existingIndex = messages.indexOfFirst { it.id == desiredMessage.id }
                if (existingIndex >= 0) {
                    val movedMessage = messages.removeAt(existingIndex)
                    messages.add(targetIndex, movedMessage)
                    if (messages[targetIndex] != desiredMessage) {
                        messages[targetIndex] = desiredMessage
                    }
                } else {
                    messages.add(targetIndex, desiredMessage)
                }
            }
            targetIndex++
        }
        while (messages.size > trimmed.size) {
            messages.removeAt(messages.lastIndex)
        }
        pruneMessageRuntimeState()
    }

    fun applyHydratedSnapshotToUi(
        pendingSnapshot: PendingHydratedSnapshot,
        messageListWasVisible: Boolean
    ) {
        if (pendingSnapshot.updateHiddenRemoteRoundCount) {
            hiddenRemoteRoundCount = pendingSnapshot.hiddenRemoteRoundCount
        }
        startupRecoverableUserMessageId = pendingSnapshot.startupRecoverableUserMessageId
        if (pendingSnapshot.replaceMessages) {
            replaceMessages(pendingSnapshot.snapshot.messages)
            val hydratedTodayAgriVisualContent = shouldShowTodayAgriMainCard(
                card = todayAgriMainItem?.card,
                currentDayKey = currentChinaDateKey(),
                shownDayKey = todayAgriMainShownDay,
                shownThisRuntime = todayAgriShownThisRuntime,
                hasAssistantAnswerTail = hasCompletedAssistantAnswerTail(
                    pendingSnapshot.snapshot.messages,
                    pendingSnapshot.snapshot.failedAssistantMessageStates.keys
                ),
                hasSavedItem = todayAgriMainItem != null,
                suppressedThisRuntime = todayAgriAutoInsertSuppressedThisRuntime,
                insertedThisRuntime = todayAgriInsertedThisRuntime
            )
            if (!hydratedTodayAgriVisualContent) {
                todayAgriShownThisRuntime = false
                todayAgriInsertedThisRuntime = false
                todayAgriMainCardVisibleLogged = false
            }
            initialWorklinePhase = restoredStartupWorklinePhase(
                messageCount = pendingSnapshot.snapshot.messages.size,
                hasTodayAgriVisualContent = hydratedTodayAgriVisualContent,
                persistedInitialWorklineOwned = pendingSnapshot.snapshot.initialWorklineOwned
            )
        }
        if (pendingSnapshot.updateFailedUserStates) {
            failedUserMessageStates.clear()
            failedUserMessageStates.putAll(pendingSnapshot.snapshot.failedUserMessageStates)
        }
        if (pendingSnapshot.updateFailedAssistantStates) {
            failedAssistantMessageStates.clear()
            failedAssistantMessageStates.putAll(pendingSnapshot.snapshot.failedAssistantMessageStates)
        }
        if ((pendingSnapshot.replaceMessages || pendingSnapshot.updateHiddenRemoteRoundCount) && !messageListWasVisible) {
            initialBottomSnapDone = false
        }
        persistTick++
    }

    fun trimMessagesInPlace() {
        val startIndex = trimWindowStartIndex(messages)
        if (startIndex > 0) {
            repeat(startIndex) { messages.removeAt(0) }
        }
        pruneMessageRuntimeState()
    }

    fun resetStreamingUiState(clearVisibleContent: Boolean) {
        mainHandler.post {
            pendingStreamingFinalizeMessageId = null
            pendingStreamingFinalizeShouldRestoreBottomAnchor = false
            remoteRecoveryJob?.cancel()
            remoteRecoveryJob = null
            remoteRecoverySourceUserMessageId = null
            SessionApi.cancelCurrentStream()
            streamRevealJob?.cancel()
            streamRevealJob = null
            isStreaming = false
            anchoredUserMessageId = null
            sendUiSettling = false
            sendStartViewportHeightPx = 0
            sendStartAnchorActive = false
            streamingMessageId = null
            streamingRevealBuffer = ""
            resetScrollRuntimeAfterStreamingStop(runtime = scrollRuntime)
            composerSettlingMinHeightPx = 0
            composerSettlingChromeHeightPx = 0
            if (clearVisibleContent) {
                streamingMessageContent = ""
            }
            snackbarScope.launch {
                context.clearLocalStreamingDraft(chatScopeId)
            }
        }
    }

    fun applyChatHistoryCleared() {
        SessionApi.resetUiRuntimeForCleanState()
        advanceChatHistoryClearEpoch()
        imageSendGeneration++
        imageSendJob?.cancel()
        imageSendJob = null
        PendingChatSendWorkScheduler.cancelAllForScope(context, chatScopeId)
        mainHandler.removeCallbacksAndMessages(null)
        resetStreamingUiState(clearVisibleContent = true)
        context.clearLocalChatHistoryStateSync(chatScopeId)
        resetTodayAgriRuntimeAfterHistoryClear()
        hiddenRemoteRoundCount = 0
        messages.clear()
        chatListState.requestScrollToItem(0, 0)
        recyclerFirstVisibleItemIndex = 0
        recyclerFirstVisibleItemScrollOffset = 0
        recyclerScrollInProgress = false
        hasStartedConversation = false
        initialWorklinePhase = InitialWorklinePhase.WaitingForFirstSend
        initialBottomSnapDone = false
        postInitialSnapCorrectionDone = false
        scrollMode = ScrollMode.AutoFollow
        imageSendInProgress = false
        failedUserMessageStates.clear()
        failedAssistantMessageStates.clear()
        retryingUserMessageIds.clear()
        retryingAssistantMessageIds.clear()
        val pendingCameraUri = pendingCameraImageUriString?.let(Uri::parse)
        val pendingCameraGalleryBacked = pendingCameraImageGalleryBacked
        val pendingCameraTemporaryFilePath = pendingCameraImageTemporaryFilePath
        pendingCameraImageUriString = null
        pendingCameraImageGalleryBacked = false
        pendingCameraImageTemporaryFilePath = null
        if (pendingCameraUri != null) {
            context.revokeComposerCameraUri(pendingCameraUri)
            snackbarScope.launch {
                withContext(Dispatchers.IO) {
                    if (pendingCameraGalleryBacked) {
                        context.deleteGalleryComposerCameraImage(pendingCameraUri)
                    } else {
                        deleteTemporaryComposerCameraImage(pendingCameraTemporaryFilePath)
                    }
                }
            }
        }
        selectedComposerImages.clear()
        input.value = TextFieldValue("")
        startupRecoverableUserMessageId = null
        remoteRecoverySourceUserMessageId = null
        clearMessageSelection()
        clearInputSelectionToolbar()
        focusManager.clearFocus(force = true)
        messageSelectionBoundsCacheById.clear()
        messageSelectionBoundsById.clear()
        messageContentBoundsById.clear()
        snackbarScope.launch {
            context.deleteUnreferencedComposerImages(emptySet())
        }
    }

    LaunchedEffect(uiRuntimeResetKey) {
        if (initialLocalMessages.isNotEmpty()) {
            snackbarScope.launch {
                prewarmAssistantMarkdown(initialLocalMessages)
            }
        }
        if (shouldHydrateRemoteHistory) {
            val hydrateClearEpoch = chatHistoryClearEpoch
            val localSnapshotForMergeDeferred = async {
                context.loadLocalChatWindow(chatScopeId)
            }
            val localStreamingDraftForMergeDeferred = async {
                context.loadLocalStreamingDraft(chatScopeId)
            }
            val snapshot = awaitRemoteSnapshot()
            val remoteSnapshotLoaded = snapshot != null
            if (hydrateClearEpoch != chatHistoryClearEpoch) {
                historyHydrationComplete = true
                remoteSnapshotHydrationComplete = remoteSnapshotLoaded
                localSnapshotForMergeDeferred.cancel()
                localStreamingDraftForMergeDeferred.cancel()
                todayAgriMainItem = null
                todayAgriLocalAnchorMessageId = null
                todayAgriCard = null
                pendingHydratedSnapshot = null
                pendingHydratedTodayAgriMainItem = null
                todayAgriRemoteConfirmedDay = null
                return@LaunchedEffect
            }
            val hydratedTodayAgriDayKey = currentChinaDateKey()
            val hydratedTodayAgriMainItem = snapshot
                ?.today_agri_items
                ?.firstNotNullOfOrNull { item ->
                    validTodayAgriMainItemForCurrentDay(item, hydratedTodayAgriDayKey)
                }
            val localStreamingDraftForMerge = localStreamingDraftForMergeDeferred.await()
            val localSnapshotForMerge = recoverStreamingDraftAsInterruptedSnapshot(
                localSnapshot = localSnapshotForMergeDeferred.await(),
                draft = localStreamingDraftForMerge
            )
            val shouldKeepPendingUserMessage: (String) -> Boolean = { messageId ->
                PendingChatSendStore.has(context, chatScopeId, messageId) ||
                    PendingChatSendStore.hasTerminalFailure(context, chatScopeId, messageId) ||
                    PendingChatSendStore.hasRemoteCompletionAwaitingSnapshot(
                        context,
                        chatScopeId,
                        messageId
                    )
            }
            val remoteMessages = snapshot?.a_rounds_for_ui?.let(::snapshotRoundsToMessages).orEmpty()
            val hydratedHiddenRemoteRoundCount =
                if (snapshot == null) {
                    0
                } else {
                    (snapshot.round_total - snapshot.a_rounds_for_ui.size).coerceAtLeast(0)
                }
            val hydratedSnapshot = if (snapshot == null) {
                retainLocalRecoverySnapshotForRemoteFallback(
                    localSnapshot = localSnapshotForMerge,
                    shouldKeepPendingUserMessage = shouldKeepPendingUserMessage
                )
            } else {
                mergeHydratedMessagesWithLocalPendingState(
                    remoteMessages = remoteMessages,
                    localSnapshot = localSnapshotForMerge,
                    shouldKeepPendingUserMessage = shouldKeepPendingUserMessage
                )
            }
            val hydratedStartupRecoverableUserMessageId = trailingRecoverableSourceUserMessageId(
                source = hydratedSnapshot.messages,
                ignoredUserMessageIds = hydratedSnapshot.failedUserMessageStates.keys,
                failedAssistantMessageStates = hydratedSnapshot.failedAssistantMessageStates
            )
            val prewarmMessages = if (snapshot == null) hydratedSnapshot.messages else remoteMessages
            if (prewarmMessages.isNotEmpty()) {
                snackbarScope.launch {
                    prewarmAssistantMarkdown(prewarmMessages)
                }
            }
            val shouldReplaceHydratedMessageList =
                if (snapshot == null && hydratedSnapshot.messages.isEmpty()) {
                    false
                } else {
                    shouldReplaceHydratedMessages(
                        currentMessages = messages,
                        remoteMessages = hydratedSnapshot.messages
                    )
                }
            val shouldUpdateFailedUserStates =
                failedUserMessageStates != hydratedSnapshot.failedUserMessageStates
            val shouldUpdateFailedAssistantStates =
                failedAssistantMessageStates != hydratedSnapshot.failedAssistantMessageStates
            val shouldUpdateHiddenRemoteRoundCount =
                hiddenRemoteRoundCount != hydratedHiddenRemoteRoundCount
            val shouldApplyHydratedSnapshot =
                shouldReplaceHydratedMessageList ||
                    shouldUpdateFailedUserStates ||
                    shouldUpdateFailedAssistantStates ||
                    shouldUpdateHiddenRemoteRoundCount
            val hydratedSnapshotPending = PendingHydratedSnapshot(
                snapshot = hydratedSnapshot,
                replaceMessages = shouldReplaceHydratedMessageList,
                updateFailedUserStates = shouldUpdateFailedUserStates,
                updateFailedAssistantStates = shouldUpdateFailedAssistantStates,
                updateHiddenRemoteRoundCount = shouldUpdateHiddenRemoteRoundCount,
                hiddenRemoteRoundCount = hydratedHiddenRemoteRoundCount,
                startupRecoverableUserMessageId = hydratedStartupRecoverableUserMessageId
            )
            val canApplyHydratedVisuals = canApplyHydratedVisualMutation(
                hasStartedConversation = hasStartedConversation,
                isStreaming = isStreaming,
                hasStreamingItem = hasStreamingItem,
                userBlocksHydratedVisualMutation = userBlocksHydratedVisualMutation()
            )
            val shouldHoldHydratedVisuals = shouldHoldHydratedVisualMutationForBrowsing(
                hasStartedConversation = hasStartedConversation,
                isStreaming = isStreaming,
                hasStreamingItem = hasStreamingItem,
                userBlocksHydratedVisualMutation = userBlocksHydratedVisualMutation()
            )
            if (BuildConfig.DEBUG) {
                Log.d(
                    CHAT_STARTUP_DIAG_TAG,
                    buildString {
                        append("remoteMessages=").append(remoteMessages.size)
                        append(", hydratedMessages=").append(hydratedSnapshot.messages.size)
                        append(", apply=").append(
                            canApplyHydratedVisuals &&
                                shouldApplyHydratedSnapshot
                        )
                        append(", replaceMessages=").append(shouldReplaceHydratedMessageList)
                        append(", updateUserTail=").append(shouldUpdateFailedUserStates)
                        append(", updateAssistantTail=").append(shouldUpdateFailedAssistantStates)
                        append(", updateHiddenRemoteRoundCount=").append(shouldUpdateHiddenRemoteRoundCount)
                        append(", hasStarted=").append(hasStartedConversation)
                        append(", isStreaming=").append(isStreaming)
                        append(", hasStreamingItem=").append(hasStreamingItem)
                        append(", holdForBrowsing=").append(shouldHoldHydratedVisuals)
                    }
                )
            }
            if (hydrateClearEpoch != chatHistoryClearEpoch) {
                historyHydrationComplete = true
                remoteSnapshotHydrationComplete = remoteSnapshotLoaded
                return@LaunchedEffect
            }
            if (shouldApplyHydratedSnapshot) {
                when {
                    canApplyHydratedVisuals -> {
                        pendingHydratedSnapshot = null
                        applyHydratedSnapshotToUi(
                            pendingSnapshot = hydratedSnapshotPending,
                            messageListWasVisible = shouldRevealMessageList
                        )
                    }
                    shouldHoldHydratedVisuals -> {
                        pendingHydratedSnapshot = hydratedSnapshotPending
                    }
                    else -> {
                        pendingHydratedSnapshot = null
                    }
                }
            } else if (!shouldHoldHydratedVisuals) {
                pendingHydratedSnapshot = null
            }
            if (hydratedTodayAgriMainItem != null) {
                when {
                    canApplyHydratedVisuals -> {
                        pendingHydratedTodayAgriMainItem = null
                        applyHydratedTodayAgriMainItem(hydratedTodayAgriMainItem)
                    }
                    shouldHoldHydratedVisuals -> {
                        pendingHydratedTodayAgriMainItem = hydratedTodayAgriMainItem
                    }
                    else -> {
                        pendingHydratedTodayAgriMainItem = null
                    }
                }
            } else if (
                snapshot != null &&
                shouldClearTodayAgriMainItemAfterSnapshot(
                    restoredItemFound = false,
                    todayAgriItemsUnavailable = snapshot.today_agri_items_unavailable
                ) &&
                canApplyHydratedVisuals
            ) {
                todayAgriMainItem = null
                todayAgriLocalAnchorMessageId = null
                pendingHydratedTodayAgriMainItem = null
                pendingHydratedSnapshot = null
            }
            historyHydrationComplete = true
            remoteSnapshotHydrationComplete = remoteSnapshotLoaded
        } else {
            historyHydrationComplete = true
            remoteSnapshotHydrationComplete = true
        }
    }

    LaunchedEffect(
        pendingHydratedSnapshot,
        pendingHydratedTodayAgriMainItem,
        chatListUserDragging,
        recyclerScrollInProgress,
        scrollRuntime.userInteracting.value,
        scrollMode,
        hasStartedConversation,
        isStreaming,
        hasStreamingItem,
        shouldRevealMessageList
    ) {
        val pendingSnapshot = pendingHydratedSnapshot
        val pendingItem = pendingHydratedTodayAgriMainItem
        if (pendingSnapshot == null && pendingItem == null) return@LaunchedEffect
        if (hasStartedConversation || isStreaming || hasStreamingItem) {
            pendingHydratedSnapshot = null
            pendingHydratedTodayAgriMainItem = null
            return@LaunchedEffect
        }
        if (userBlocksHydratedVisualMutation()) {
            return@LaunchedEffect
        }
        if (pendingSnapshot != null) {
            pendingHydratedSnapshot = null
            applyHydratedSnapshotToUi(
                pendingSnapshot = pendingSnapshot,
                messageListWasVisible = shouldRevealMessageList
            )
        }
        val itemAfterSnapshot = pendingHydratedTodayAgriMainItem
        if (itemAfterSnapshot != null) {
            pendingHydratedTodayAgriMainItem = null
            if (itemAfterSnapshot.day_cn == currentChinaDateKey()) {
                applyHydratedTodayAgriMainItem(itemAfterSnapshot)
            }
        }
    }

    LaunchedEffect(uiRuntimeResetKey) {
        if (!hasRemoteHistorySource) {
            context.clearLocalStreamingDraft(chatScopeId)
        }
    }

    LaunchedEffect(uiRuntimeResetKey, historyHydrationComplete, startupRecoverableUserMessageId) {
        val sourceUserMessageId = startupRecoverableUserMessageId ?: return@LaunchedEffect
        if (!hasRemoteHistorySource || !historyHydrationComplete) return@LaunchedEffect
        if (hasStartedConversation || isStreaming) return@LaunchedEffect
        if (hasSettledAssistantMessageForUser(sourceUserMessageId)) return@LaunchedEffect
        val recoveryClearEpoch = chatHistoryClearEpoch
        repeat(REMOTE_BACKGROUND_STREAM_RECOVERY_MAX_ATTEMPTS) { attempt ->
            if (recoveryClearEpoch != chatHistoryClearEpoch) return@LaunchedEffect
            val snapshot = awaitRemoteSnapshot()
            val recoveredRound = snapshot
                ?.findRoundByClientMessageId(sourceUserMessageId)
                ?.takeIf { it.assistant.isNotBlank() }
            if (recoveredRound != null) {
                applyRecoveredAssistantRound(
                    sourceUserMessageId = sourceUserMessageId,
                    recoveredRound = recoveredRound,
                    expectedClearEpoch = recoveryClearEpoch
                )
                initialBottomSnapDone = false
                return@LaunchedEffect
            }
            if (attempt < REMOTE_BACKGROUND_STREAM_RECOVERY_MAX_ATTEMPTS - 1) {
                delay(REMOTE_BACKGROUND_STREAM_RECOVERY_DELAY_MS)
            }
        }
        if (!hasSettledAssistantMessageForUser(sourceUserMessageId)) {
            if (recoveryClearEpoch != chatHistoryClearEpoch) return@LaunchedEffect
            val assistantMessageId = assistantMessageIdForSourceUser(sourceUserMessageId)
            val existingPartialContent = messages
                .firstOrNull { message ->
                    message.id == assistantMessageId &&
                        message.role == ChatRole.ASSISTANT
                }
                ?.content
                .orEmpty()
            finalizeInterruptedAssistant(
                sourceUserMessageId = sourceUserMessageId,
                assistantMessageId = assistantMessageId,
                finalContent = existingPartialContent,
                reason = "network",
                showHint = false
            )
            initialBottomSnapDone = false
        }
    }

    LaunchedEffect(uiRuntimeResetKey, historyHydrationComplete, messages.size, isStreaming) {
        if (!hasRemoteHistorySource || !historyHydrationComplete || isStreaming) return@LaunchedEffect
        val pendingUserMessageIds = messages
            .filter { message ->
                shouldTrackPendingImageAssistantRecovery(
                    message = message,
                    pendingExists = PendingChatSendStore.has(context, chatScopeId, message.id),
                    terminalFailureExists = PendingChatSendStore.hasTerminalFailure(
                        context,
                        chatScopeId,
                        message.id
                    ),
                    remoteCompletionExists = PendingChatSendStore.hasRemoteCompletionAwaitingSnapshot(
                        context,
                        chatScopeId,
                        message.id
                    ),
                    hasSettledAssistant = hasSettledAssistantMessageForUser(message.id)
                )
            }
            .map { it.id }
        if (pendingUserMessageIds.isEmpty()) return@LaunchedEffect
        schedulePendingImageAssistantRecovery(pendingUserMessageIds)
    }

    LaunchedEffect(persistTick) {
        if (persistTick == 0) return@LaunchedEffect
        val persistClearEpoch = chatHistoryClearEpoch
        delay(if (isStreaming) 220 else 80)
        if (persistClearEpoch != chatHistoryClearEpoch) return@LaunchedEffect
        val snapshot = persistableLocalChatWindowSnapshot()
        val retainedImageUris = currentRetainedComposerImageUris()
        if (persistClearEpoch != chatHistoryClearEpoch) return@LaunchedEffect
        context.saveLocalChatWindowIfEpoch(
            chatScopeId = chatScopeId,
            snapshot = snapshot,
            expectedEpoch = persistClearEpoch,
            epochRef = chatHistoryClearEpochRef
        )
        withContext(Dispatchers.IO) {
            context.deleteUnreferencedComposerImages(retainedImageUris)
        }
    }

    LaunchedEffect(uiRuntimeResetKey) {
        snapshotFlow { input.value.text }
            .debounce(250)
            .distinctUntilChanged()
            .collect { draftText ->
                val draftClearEpoch = chatHistoryClearEpoch
                context.saveLocalComposerDraftIfEpoch(
                    chatScopeId = chatScopeId,
                    text = draftText,
                    expectedEpoch = draftClearEpoch,
                    epochRef = chatHistoryClearEpochRef
                )
            }
    }

    LaunchedEffect(inputLimitHintTick) {
        if (inputLimitHintTick == 0) return@LaunchedEffect
        val currentTick = inputLimitHintTick
        inputLimitHintVisible = true
        delay(INPUT_LIMIT_HINT_MS)
        if (inputLimitHintTick == currentTick) {
            inputLimitHintVisible = false
        }
    }

    LaunchedEffect(composerStatusHintTick) {
        if (composerStatusHintTick == 0 || composerStatusHintText.isBlank()) return@LaunchedEffect
        val currentTick = composerStatusHintTick
        composerStatusHintVisible = true
        delay(COMPOSER_STATUS_HINT_MS)
        if (composerStatusHintTick == currentTick) {
            composerStatusHintVisible = false
            composerStatusHintText = ""
        }
    }

    LaunchedEffect(
        chatScopeId,
        isStreaming,
        streamingMessageId,
        streamingMessageContent.length,
        streamingRevealBuffer.length,
        anchoredUserMessageId
    ) {
        val draftClearEpoch = chatHistoryClearEpoch
        if (!isStreaming || streamingMessageId.isNullOrBlank()) {
            if (streamingMessageContent.isBlank() && streamingRevealBuffer.isBlank()) {
                context.clearLocalStreamingDraft(chatScopeId)
            }
            return@LaunchedEffect
        }
        delay(STREAM_DRAFT_SAVE_DEBOUNCE_MS)
        if (draftClearEpoch != chatHistoryClearEpoch) return@LaunchedEffect
        context.saveLocalStreamingDraftIfEpoch(
            chatScopeId = chatScopeId,
            draft = LocalStreamingDraft(
                messageId = streamingMessageId.orEmpty(),
                content = streamingMessageContent,
                revealBuffer = streamingRevealBuffer,
                anchoredUserMessageId = anchoredUserMessageId,
                savedAtMs = SystemClock.uptimeMillis()
            ),
            expectedEpoch = draftClearEpoch,
            epochRef = chatHistoryClearEpochRef
        )
    }

    fun updateChatListMetrics(metrics: ChatListMetrics) {
        if (recyclerScrollInProgress != metrics.scrollInProgress) {
            recyclerScrollInProgress = metrics.scrollInProgress
        }
        if (recyclerFirstVisibleItemIndex != metrics.firstVisibleItemIndex) {
            recyclerFirstVisibleItemIndex = metrics.firstVisibleItemIndex
        }
        val scrollOffsetBucket =
            metrics.firstVisibleItemScrollOffset / SCROLL_OFFSET_METRIC_BUCKET_PX
        val currentScrollOffsetBucket =
            recyclerFirstVisibleItemScrollOffset / SCROLL_OFFSET_METRIC_BUCKET_PX
        if (currentScrollOffsetBucket != scrollOffsetBucket) {
            recyclerFirstVisibleItemScrollOffset =
                scrollOffsetBucket * SCROLL_OFFSET_METRIC_BUCKET_PX
        }
    }

    fun requestProgrammaticForwardListBottomAnchor(force: Boolean = false) {
        if (bottomAnchorIndexOrMinusOne() < 0) return
        if (!force && shouldSuppressAutomaticBottomAnchor()) return
        programmaticBottomAnchorGeneration += 1
        val requestGeneration = programmaticBottomAnchorGeneration
        scrollRuntime.programmaticScroll.value = true
        requestForwardListBottomAnchor(force = force)
        snackbarScope.launch {
            withFrameNanos { }
            if (programmaticBottomAnchorGeneration == requestGeneration) {
                scrollRuntime.programmaticScroll.value = false
                updateChatListMetrics(readChatListMetrics(chatListState))
            }
        }
    }

    fun userIsActivelyBrowsingInitialWorkline(): Boolean {
        return chatListUserDragging ||
            recyclerScrollInProgress ||
            (scrollMode == ScrollMode.UserBrowsing && chatListState.canScrollBackward)
    }

    fun userBlocksInitialWorklineAutoSwitch(): Boolean =
        userIsActivelyBrowsingInitialWorkline() || scrollRuntime.userInteracting.value

    var todayAgriAutoAnchorHadCard by remember(uiRuntimeResetKey) { mutableStateOf(hasTodayAgriCard) }
    var todayAgriAutoAnchorEligibleBeforeInsert by remember(uiRuntimeResetKey) { mutableStateOf(false) }

    fun userBlocksTodayAgriInsertedAnchor(): Boolean =
        chatListUserDragging ||
            recyclerScrollInProgress ||
            scrollRuntime.userInteracting.value ||
            scrollMode == ScrollMode.UserBrowsing

    LaunchedEffect(
        hasTodayAgriCard,
        atBottom,
        chatListState.canScrollForward,
        chatListUserDragging,
        recyclerScrollInProgress,
        scrollRuntime.userInteracting.value,
        scrollMode,
        isStreaming,
        hasStreamingItem,
        messages.size,
        chatListItems.size
    ) {
        if (!hasTodayAgriCard) {
            todayAgriAutoAnchorEligibleBeforeInsert =
                !isStreaming &&
                    !hasStreamingItem &&
                    !userBlocksTodayAgriInsertedAnchor() &&
                    isForwardListAtExactBottom()
        }
    }

    LaunchedEffect(
        hasTodayAgriCard,
        shouldShowTodayAgriCard,
        todayAgriAfterMessageIdForRender,
        chatListItems.size,
        currentTodayAgriCardDay
    ) {
        val insertedNow = hasTodayAgriCard && !todayAgriAutoAnchorHadCard
        todayAgriAutoAnchorHadCard = hasTodayAgriCard
        if (!insertedNow || !shouldShowTodayAgriCard) return@LaunchedEffect
        if (!todayAgriAutoAnchorEligibleBeforeInsert) return@LaunchedEffect
        if (isStreaming || hasStreamingItem || userBlocksTodayAgriInsertedAnchor()) return@LaunchedEffect
        withFrameNanos { }
        if (hasTodayAgriCard && !userBlocksTodayAgriInsertedAnchor()) {
            requestProgrammaticForwardListBottomAnchor()
        }
        todayAgriAutoAnchorEligibleBeforeInsert = false
    }

    LaunchedEffect(
        initialWorklinePhase,
        messages.size,
        currentInitialDocumentFlowBottomPx(),
        currentUnifiedBottomTargetPx(),
        hasInitialDocumentFlowSafelyPassedWorkline(),
        startupLayoutReady,
        chatListUserDragging,
        recyclerScrollInProgress,
        scrollRuntime.userInteracting.value,
        scrollMode,
        chatListState.canScrollBackward,
        chatListState.canScrollForward
    ) {
        if (initialWorklinePhase != InitialWorklinePhase.TopUnreached) return@LaunchedEffect
        if (!startupLayoutReady || !hasStaticVisualTimeline()) return@LaunchedEffect
        if (!hasInitialDocumentFlowReachedWorkline()) return@LaunchedEffect
        if (userIsActivelyBrowsingInitialWorkline()) {
            initialWorklinePhase = InitialWorklinePhase.WorklineOwned
            return@LaunchedEffect
        }
        if (userBlocksInitialWorklineAutoSwitch()) return@LaunchedEffect
        initialWorklinePhase = InitialWorklinePhase.TopAnchoring
    }

    LaunchedEffect(
        initialWorklinePhase,
        messages.size,
        currentInitialDocumentFlowBottomPx(),
        currentUnifiedBottomTargetPx(),
        hasInitialDocumentFlowSafelyPassedWorkline(),
        startupLayoutReady,
        chatListState.canScrollForward,
        chatListUserDragging,
        recyclerScrollInProgress,
        scrollRuntime.userInteracting.value,
        scrollMode
    ) {
        if (initialWorklinePhase != InitialWorklinePhase.TopAnchoring) return@LaunchedEffect
        if (!startupLayoutReady || !hasStaticVisualTimeline()) return@LaunchedEffect
        if (userIsActivelyBrowsingInitialWorkline()) {
            initialWorklinePhase = InitialWorklinePhase.WorklineOwned
            return@LaunchedEffect
        }
        if (userBlocksInitialWorklineAutoSwitch()) return@LaunchedEffect
        if (!chatListState.canScrollForward) return@LaunchedEffect
        if (!hasInitialDocumentFlowSafelyPassedWorkline()) return@LaunchedEffect
        initialWorklinePhase = InitialWorklinePhase.WorklineOwned
        scrollMode = ScrollMode.AutoFollow
        scrollRuntime.userInteracting.value = false
        requestProgrammaticForwardListBottomAnchor(force = true)
    }

    val shouldAnchorStreamingBottomThisFrame =
        (isStreaming || hasStreamingItem) &&
            !shouldSuppressAutomaticBottomAnchor() &&
            scrollMode == ScrollMode.AutoFollow &&
            !scrollRuntime.userInteracting.value &&
            !chatListUserDragging &&
            latestMessageIndexOrMinusOne() >= 0
    if (shouldAnchorStreamingBottomThisFrame) {
        // Run after this composition commits but before layout, so a streaming wrap and bottom
        // Anchor in the same frame so the tail does not appear below the workline for one draw.
        SideEffect {
            requestForwardListBottomAnchor()
        }
    }

    LaunchedEffect(chatListState) {
        snapshotFlow { readChatListMetrics(chatListState) }
            .collect { metrics ->
                updateChatListMetrics(metrics)
            }
    }

    LaunchedEffect(chatListUserDragging) {
        if (chatListUserDragging) {
            initialBottomSnapDone = true
            postInitialSnapCorrectionDone = true
        }
    }

    LaunchedEffect(
        recyclerScrollInProgress,
        chatListUserDragging,
        programmaticScroll,
        isStreaming,
        hasStreamingItem
    ) {
        handleChatListScrollStateChanged(
            scrollInProgress = recyclerScrollInProgress,
            userDragging = chatListUserDragging,
            programmaticScroll = programmaticScroll,
            isStreaming = isStreaming,
            hasStreamingItem = hasStreamingItem,
            scrollModeState = scrollRuntime.scrollMode,
            userInteractingState = scrollRuntime.userInteracting,
            endProgrammaticScroll = {
                com.nongjiqianwen.endProgrammaticChatListScroll(
                    programmaticScrollState = scrollRuntime.programmaticScroll,
                    listState = chatListState,
                    refreshChatListMetrics = { listState ->
                        updateChatListMetrics(readChatListMetrics(listState))
                    }
                )
            }
        )
    }

    LaunchedEffect(chatListUserDragging, programmaticScroll, imeVisible) {
        if (!programmaticScroll && chatListUserDragging && imeVisible) {
            focusManager.clearFocus(force = true)
        }
    }

    val ensureStreamingRevealJob = {
        com.nongjiqianwen.ensureStreamingRevealJob(
            currentJob = streamRevealJob,
            setJob = { streamRevealJob = it },
            scope = snackbarScope,
            isStreaming = { isStreaming },
            currentMessageId = { streamingMessageId },
            currentContent = { streamingMessageContent },
            currentRevealBuffer = { streamingRevealBuffer },
            anchoredUserMessageId = { anchoredUserMessageId },
            assistantIdProvider = ::assistantMessageIdForSourceUser,
            fallbackIdProvider = { "assistant_${UUID.randomUUID()}" },
            onAdvance = { advance ->
                streamingMessageId = advance.messageId
                streamingRevealBuffer = advance.revealBuffer
                streamingMessageContent = advance.content
            }
        )
    }

    val appendAssistantChunk: (String) -> Unit = { piece ->
        com.nongjiqianwen.appendAssistantChunk(
            piece = piece,
            mainHandler = mainHandler,
            isStreaming = { isStreaming },
            currentMessageId = { streamingMessageId },
            currentRevealBuffer = { streamingRevealBuffer },
            anchoredUserMessageId = { anchoredUserMessageId },
            assistantIdProvider = ::assistantMessageIdForSourceUser,
            fallbackIdProvider = { "assistant_${UUID.randomUUID()}" },
            onQueued = { queued ->
                streamingMessageId = queued.messageId
                streamingRevealBuffer = queued.revealBuffer
            },
            ensureRevealJob = ensureStreamingRevealJob
        )
    }

    fun clearPendingStreamingFinalize() {
        pendingStreamingFinalizeMessageId = null
        pendingStreamingFinalizeShouldRestoreBottomAnchor = false
        pendingStreamingFinalizeStartedAtMs = 0L
    }

    fun beginPendingStreamingFinalize(
        anchorMessageId: String,
        shouldRestoreBottomAnchor: Boolean
    ) {
        // Drop stale streaming bounds so the finalized settled host must report
        // a fresh bottom before we hand geometry ownership away from streaming.
        messageContentBoundsById.remove(anchorMessageId)
        messageSelectionBoundsCacheById.remove(anchorMessageId)
        messageSelectionBoundsById.remove(anchorMessageId)
        pendingStreamingFinalizeMessageId = anchorMessageId
        pendingStreamingFinalizeShouldRestoreBottomAnchor = shouldRestoreBottomAnchor
        pendingStreamingFinalizeStartedAtMs = SystemClock.uptimeMillis()
    }
    var restoreBottomAnchorIfNeededAfterStreamingStop: (Boolean) -> Unit = {}

    fun finalizeStreamingStop(
        shouldRestoreBottomAnchor: Boolean
    ) {
        isStreaming = false
        anchoredUserMessageId = null
        sendUiSettling = false
        sendStartViewportHeightPx = 0
        sendStartAnchorActive = false
        streamingMessageId = null
        streamingMessageContent = ""
        streamingRevealBuffer = ""
        streamingBackgrounded = false
        streamRevealJob = null
        resetScrollRuntimeAfterStreamingStop(runtime = scrollRuntime)
        restoreBottomAnchorIfNeededAfterStreamingStop(shouldRestoreBottomAnchor)
        clearPendingStreamingFinalize()
    }
    stopStreamingForRemoteRecovery = {
        clearPendingStreamingFinalize()
        finalizeStreamingStop(shouldRestoreBottomAnchor = false)
    }

    fun finishStreaming() {
        val finishClearEpoch = chatHistoryClearEpoch
        mainHandler.post {
            if (finishClearEpoch != chatHistoryClearEpoch) return@post
            if (!pendingStreamingFinalizeMessageId.isNullOrBlank()) return@post
            val completedSourceUserMessageId = anchoredUserMessageId
            if (
                !completedSourceUserMessageId.isNullOrBlank() &&
                messages.none { it.id == completedSourceUserMessageId }
            ) {
                return@post
            }
            remoteRecoveryJob?.cancel()
            remoteRecoveryJob = null
            remoteRecoverySourceUserMessageId = null
            val shouldRestoreBottomAnchor = scrollMode != ScrollMode.UserBrowsing
            if (streamingRevealBuffer.isNotEmpty()) {
                if (shouldForceFlushStreamingRevealBufferForFinish(streamingRevealBuffer)) {
                    flushStreamingRevealBuffer(
                        currentMessageId = streamingMessageId,
                        currentContent = streamingMessageContent,
                        currentRevealBuffer = streamingRevealBuffer,
                        anchoredUserMessageId = anchoredUserMessageId,
                        assistantIdProvider = ::assistantMessageIdForSourceUser,
                        fallbackIdProvider = { "assistant_${UUID.randomUUID()}" }
                    )?.let { flushed ->
                        streamingMessageId = flushed.messageId
                        streamingMessageContent = flushed.content
                        streamingRevealBuffer = ""
                    }
                }
            }
            if (streamingRevealBuffer.isNotEmpty()) {
                ensureStreamingRevealJob()
                mainHandler.postDelayed({ finishStreaming() }, STREAM_TYPEWRITER_FINISH_DRAIN_POLL_MS)
                return@post
            }
            flushStreamingRevealBuffer(
                currentMessageId = streamingMessageId,
                currentContent = streamingMessageContent,
                currentRevealBuffer = streamingRevealBuffer,
                anchoredUserMessageId = anchoredUserMessageId,
                assistantIdProvider = ::assistantMessageIdForSourceUser,
                fallbackIdProvider = { "assistant_${UUID.randomUUID()}" }
            )?.let { flushed ->
                streamingMessageId = flushed.messageId
                streamingMessageContent = flushed.content
                streamingRevealBuffer = ""
            }
            val finalContent = normalizeAssistantText(streamingMessageContent)
            if (streamingMessageContent != finalContent) {
                streamingMessageContent = finalContent
            }
            val finalId = streamingMessageId
            streamingRevealBuffer = ""
            var shouldCompleteSourceUserMessage = false
            if (finalContent.isNotBlank()) {
                shouldCompleteSourceUserMessage = true
                if (shouldRestoreBottomAnchor) {
                    requestProgrammaticForwardListBottomAnchor()
                }
                applyCompletedAssistantMessageInPlace(
                    target = messages,
                    messageId = finalId.orEmpty(),
                    content = finalContent
                )
                beginPendingStreamingFinalize(
                    anchorMessageId = finalId.orEmpty(),
                    shouldRestoreBottomAnchor = shouldRestoreBottomAnchor
                )
                persistTick++
                val persistedSnapshot = persistableLocalChatWindowSnapshot()
                val persistedMessages = persistedSnapshot.messages
                val prewarmMessages = persistedMessages.takeLast(2)
                val persistClearEpoch = finishClearEpoch
                snackbarScope.launch {
                    context.saveLocalChatWindowIfEpoch(
                        chatScopeId = chatScopeId,
                        snapshot = persistedSnapshot,
                        expectedEpoch = persistClearEpoch,
                        epochRef = chatHistoryClearEpochRef
                    )
                    if (persistClearEpoch != chatHistoryClearEpoch) return@launch
                    context.clearLocalStreamingDraft(chatScopeId)
                    if (persistClearEpoch != chatHistoryClearEpoch) return@launch
                    prewarmAssistantMarkdown(prewarmMessages)
                }
            } else {
                val sourceUserMessageId = completedSourceUserMessageId
                if (!sourceUserMessageId.isNullOrBlank() && !finalId.isNullOrBlank()) {
                    finalizeInterruptedAssistant(
                        sourceUserMessageId = sourceUserMessageId,
                        assistantMessageId = finalId,
                        finalContent = "",
                        reason = "empty"
                    )
                } else {
                    finalId?.let(::removeMessageById)
                    persistTick++
                }
                finalizeStreamingStop(shouldRestoreBottomAnchor = shouldRestoreBottomAnchor)
            }
            completedSourceUserMessageId?.takeIf { shouldCompleteSourceUserMessage }?.let { userMessageId ->
                PendingChatSendWorkScheduler.complete(context, chatScopeId, userMessageId)
            }
        }
    }

    fun handleAssistantInterrupted(sourceUserMessageId: String, reason: String) {
        val interruptedClearEpoch = chatHistoryClearEpoch
        mainHandler.post {
            if (interruptedClearEpoch != chatHistoryClearEpoch) return@post
            if (sourceUserMessageId.isNotBlank() && messages.none { it.id == sourceUserMessageId }) {
                return@post
            }
            if (reason == "quota") {
                quotaExhaustedDayKey = currentQuotaDayKey()
            }
            val finalId = streamingMessageId ?: assistantMessageIdForSourceUser(sourceUserMessageId)
            val finalContent = normalizeAssistantText(streamingMessageContent)
            streamingRevealBuffer = ""
            clearPendingStreamingFinalize()
            streamRevealJob?.cancel()
            streamRevealJob = null
            finalizeStreamingStop(shouldRestoreBottomAnchor = false)
            context.clearLocalStreamingDraftSync(chatScopeId)
            if (canAttemptRemoteAssistantRecovery(reason)) {
                PendingChatSendRuntime.markInactive(sourceUserMessageId)
                if (finalContent.isNotBlank()) {
                    applyCompletedAssistantMessageInPlace(
                        target = messages,
                        messageId = finalId,
                        content = finalContent
                    )
                } else {
                    upsertAssistantMessagePlaceholder(
                        messageId = finalId,
                        sourceUserMessageId = sourceUserMessageId
                    )
                }
                failedAssistantMessageStates[finalId] = FailedAssistantMessageState(
                    sourceUserMessageId = sourceUserMessageId,
                    reason = reason
                )
                retryingAssistantMessageIds[finalId] = true
                persistTick++
                val recoveryMaxAttempts =
                    if (reason == "stream_in_progress" || reason == "replay") {
                        REMOTE_BACKGROUND_STREAM_RECOVERY_MAX_ATTEMPTS
                    } else {
                        REMOTE_STREAM_RECOVERY_MAX_ATTEMPTS
                    }
                val recoveryDelayMs =
                    if (reason == "stream_in_progress" || reason == "replay") {
                        REMOTE_BACKGROUND_STREAM_RECOVERY_DELAY_MS
                    } else {
                        REMOTE_STREAM_RECOVERY_DELAY_MS
                    }
                scheduleRemoteAssistantRecovery(
                    sourceUserMessageId = sourceUserMessageId,
                    assistantMessageId = finalId,
                    fallbackContent = finalContent,
                    reason = reason,
                    maxAttempts = recoveryMaxAttempts,
                    delayMs = recoveryDelayMs
                )
                return@post
            }
            if (reason == "replay") {
                PendingChatSendWorkScheduler.complete(context, chatScopeId, sourceUserMessageId)
            } else {
                PendingChatSendWorkScheduler.cancelAndRemove(context, chatScopeId, sourceUserMessageId)
            }
            val showAssistantRetry = shouldShowInterruptedAssistantRetry(reason)
            if (finalContent.isNotBlank()) {
                applyCompletedAssistantMessageInPlace(
                    target = messages,
                    messageId = finalId,
                    content = finalContent
                )
            } else if (showAssistantRetry) {
                upsertAssistantMessagePlaceholder(
                    messageId = finalId,
                    sourceUserMessageId = sourceUserMessageId
                )
                showComposerStatusHint(interruptedHintText(reason))
            } else {
                removeMessageById(finalId)
                showComposerStatusHint(interruptedHintText(reason))
            }
            if (showAssistantRetry) {
                failedAssistantMessageStates[finalId] = FailedAssistantMessageState(
                    sourceUserMessageId = sourceUserMessageId,
                    reason = reason
                )
            } else {
                failedAssistantMessageStates.remove(finalId)
            }
            persistTick++
        }
    }

    fun markUserMessageSendFailed(
        text: String,
        imageUris: List<String> = emptyList(),
        imageUrls: List<String> = emptyList(),
        existingUserMessageId: String? = null
    ) {
        if ((text.isEmpty() && imageUris.isEmpty() && imageUrls.isEmpty()) || isStreaming || sendUiSettling) return
        val shouldUseInitialTopFlow = shouldUseInitialTopFlowForSend()
        sendUiSettling = true
        snackbarScope.launch {
            try {
                suppressPendingTodayAgriAutoInsertForUserSend()
                hasStartedConversation = true
                keepOrStartInitialWorklineTopFlow(shouldUseInitialTopFlow)
                initialBottomSnapDone = true
                LaunchUiGate.chatReady = true
                val userId = existingUserMessageId
                    ?: findFailedUserMessageIdByText(text)
                    ?: "user_${UUID.randomUUID()}"
                val existingUserAlreadyInTimeline = messages.any { message ->
                    message.id == userId && message.role == ChatRole.USER
                }
                val failedTodayAgriContextDay =
                    resolveTodayAgriContextDayForSend(userId).takeIf { existingUserAlreadyInTimeline }
                        ?: todayAgriContextDayForNextSend()
                if (existingUserMessageId == null) {
                    input.value = TextFieldValue("")
                    context.clearLocalComposerDraftSync(chatScopeId)
                    selectedComposerImages.clear()
                    attachmentMenuVisible = false
                    clearInputSelectionToolbar()
                }
                upsertUserMessage(
                    messageId = userId,
                    content = text,
                    imageUris = imageUris,
                    imageUrls = imageUrls,
                    todayAgriContextDay = failedTodayAgriContextDay
                )
                failedUserMessageStates[userId] = "network"
                clearFailedAssistantStateForUser(userId)
                anchoredUserMessageId = userId
                trimMessagesInPlace()
                if (latestMessageIndexOrMinusOne() >= 0) {
                    requestProgrammaticForwardListBottomAnchor()
                }
                sendUiSettling = false
                persistTick++
                context.saveLocalChatWindowSync(
                    chatScopeId,
                    persistableLocalChatWindowSnapshot()
                )
                showComposerStatusHint(NETWORK_UNAVAILABLE_HINT_TEXT)
            } finally {
                sendUiSettling = false
            }
        }
    }

    fun currentClientRegionForSend(): ClientRegionContext? {
        if (!ClientRegionProvider.hasLocationPermission(context)) return null
        return latestClientRegion ?: ClientRegionProvider.cachedRegion(context)
    }

    suspend fun refreshClientRegionForSend(): ClientRegionContext? {
        if (!hasRemoteHistorySource) return null
        if (!ClientRegionProvider.hasLocationPermission(context)) {
            latestClientRegion = null
            if (!ClientRegionProvider.wasLocationPermissionPrompted(context)) {
                ClientRegionProvider.markLocationPermissionPrompted(context)
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            return null
        }
        val refreshed = ClientRegionProvider.refreshRegion(context)
        if (refreshed != null) {
            latestClientRegion = refreshed
            return refreshed
        }
        return currentClientRegionForSend()
    }

    fun stageUserMessageForImageUpload(
        text: String,
        previewImageUris: List<String>,
        sessionGeneration: Int?,
        todayAgriContextDay: String?
    ): String? {
        if ((text.isEmpty() && previewImageUris.isEmpty()) || isStreaming || sendUiSettling) return null
        val shouldUseInitialTopFlow = shouldUseInitialTopFlowForSend()
        val preSendStableCollapsedReservePx =
            if (!listShouldTrackRealtimeComposerGeometry) {
                (latestConversationBottomPaddingPx - streamVisibleBottomGapPx)
                    .takeIf { it > 0 }
            } else {
                null
            }
        sendStartViewportHeightPx = messageViewportHeightPx
        sendUiSettling = true
        suppressPendingTodayAgriAutoInsertForUserSend()
        hasStartedConversation = true
        keepOrStartInitialWorklineTopFlow(shouldUseInitialTopFlow)
        initialBottomSnapDone = true
        LaunchUiGate.chatReady = true

        val collapsePreparation = prepareComposerCollapse(
            inputContentHeightPx = inputContentHeightPx,
            startupInputContentHeightEstimatePx = startupInputContentHeightEstimatePx,
            inputChromeRowHeightPx = inputChromeRowHeightPx
        )
        composerSettlingMinHeightPx = collapsePreparation.settlingMinHeightPx
        composerSettlingChromeHeightPx = collapsePreparation.settlingChromeHeightPx
        suppressInputCursor = collapsePreparation.shouldSuppressCursor
        inputFieldFocused = false
        clearInputSelectionToolbar()
        input.value = TextFieldValue("")
        context.clearLocalComposerDraftSync(chatScopeId)
        selectedComposerImages.clear()
        attachmentMenuVisible = false
        if (collapsePreparation.shouldClearFocus) {
            focusManager.clearFocus(force = true)
        }

        val userId = "user_${UUID.randomUUID()}"
        clearStaleFailureAffordancesForNewSend(userId)
        failedUserMessageStates.remove(userId)
        clearFailedAssistantStateForUser(userId)
        upsertUserMessage(
            messageId = userId,
            content = text,
            imageUris = previewImageUris,
            todayAgriContextDay = todayAgriContextDay
        )
        trimMessagesInPlace()
        anchoredUserMessageId = userId
        lockedConversationBottomPaddingPx =
            (
                observedCollapsedBottomReservePx
                    .takeIf { it > 0 }
                    ?: preSendStableCollapsedReservePx
                    ?: startupBottomBarHeightEstimatePx
                ) + streamVisibleBottomGapPx
        if (latestMessageIndexOrMinusOne() >= 0) {
            requestProgrammaticForwardListBottomAnchor()
        }
        sendUiSettling = false
        persistTick++
        val stagedSnapshot = persistableLocalChatWindowSnapshot()
        context.saveLocalChatWindowSync(chatScopeId, stagedSnapshot)
        val region = currentClientRegionForSend()
        if (hasRemoteHistorySource) {
            PendingChatSendRuntime.markActive(userId)
            PendingChatSendWorkScheduler.enqueue(
                context = context,
                pending = PendingChatSend(
                    chatScopeId = chatScopeId,
                    userMessageId = userId,
                    text = text,
                    imageUris = previewImageUris,
                    sessionGeneration = sessionGeneration,
                    region = region?.region,
                    regionSource = region?.source,
                    regionReliability = region?.reliability,
                    todayAgriContextDay = todayAgriContextDay
                )
            )
        }
        return userId
    }

    fun shouldPreserveUserBrowsingForStreamingStart(): Boolean {
        return false
    }

    fun commitSendMessage(
        text: String,
        uploadedImageUrls: List<String> = emptyList(),
        previewImageUris: List<String> = emptyList(),
        existingUserMessageId: String? = null,
        collapseComposer: Boolean = true,
        sessionGeneration: Int? = SessionApi.currentSessionGenerationOrNull(),
        todayAgriContextDay: String? = null
    ) {
        if ((text.isEmpty() && uploadedImageUrls.isEmpty()) || isStreaming || sendUiSettling) return
        val resolvedTodayAgriContextDay =
            todayAgriContextDay ?: resolveTodayAgriContextDayForSend(existingUserMessageId)
        val shouldUseInitialTopFlow = shouldUseInitialTopFlowForSend()
        val preSendStableCollapsedReservePx =
            if (!listShouldTrackRealtimeComposerGeometry) {
                (latestConversationBottomPaddingPx - streamVisibleBottomGapPx)
                    .takeIf { it > 0 }
            } else {
                null
            }
        sendStartViewportHeightPx = messageViewportHeightPx
        sendUiSettling = true
        suppressPendingTodayAgriAutoInsertForUserSend()
        hasStartedConversation = true
        keepOrStartInitialWorklineTopFlow(shouldUseInitialTopFlow)
        initialBottomSnapDone = true
        LaunchUiGate.chatReady = true
        if (collapseComposer) {
            val collapsePreparation = prepareComposerCollapse(
                inputContentHeightPx = inputContentHeightPx,
                startupInputContentHeightEstimatePx = startupInputContentHeightEstimatePx,
                inputChromeRowHeightPx = inputChromeRowHeightPx
            )
            composerSettlingMinHeightPx = collapsePreparation.settlingMinHeightPx
            composerSettlingChromeHeightPx = collapsePreparation.settlingChromeHeightPx
            suppressInputCursor = collapsePreparation.shouldSuppressCursor
            inputFieldFocused = false
            clearInputSelectionToolbar()
            input.value = TextFieldValue("")
            context.clearLocalComposerDraftSync(chatScopeId)
            selectedComposerImages.clear()
            attachmentMenuVisible = false
            if (collapsePreparation.shouldClearFocus) {
                focusManager.clearFocus(force = true)
            }
        }
        val userId = existingUserMessageId ?: "user_${UUID.randomUUID()}"
        val streamSessionGeneration = sessionGeneration
        clearStaleFailureAffordancesForNewSend(userId)
        failedUserMessageStates.remove(userId)
        clearFailedAssistantStateForUser(userId)
        val assistantId = assistantMessageIdForSourceUser(userId)
        upsertUserMessage(
            messageId = userId,
            content = text,
            imageUris = previewImageUris,
            imageUrls = uploadedImageUrls,
            todayAgriContextDay = resolvedTodayAgriContextDay
        )
        upsertAssistantMessagePlaceholder(
            messageId = assistantId,
            sourceUserMessageId = userId
        )
        trimMessagesInPlace()
        anchoredUserMessageId = userId
        val collapsedSendBottomPaddingPx =
            (
                observedCollapsedBottomReservePx
                    .takeIf { it > 0 }
                    ?: preSendStableCollapsedReservePx
                    ?: startupBottomBarHeightEstimatePx
                ) + streamVisibleBottomGapPx
        lockedConversationBottomPaddingPx =
            when {
                !collapseComposer && latestConversationBottomPaddingPx > 0 -> {
                    latestConversationBottomPaddingPx
                }
                collapseComposer -> {
                    collapsedSendBottomPaddingPx
                }
                else -> -1
            }
        isStreaming = true
        streamingMessageId = assistantId
        remoteRecoveryJob?.cancel()
        remoteRecoveryJob = null
        remoteRecoverySourceUserMessageId = null
        streamingMessageContent = ""
        streamingRevealBuffer = ""
        context.saveLocalStreamingDraftSync(
            chatScopeId = chatScopeId,
            draft = LocalStreamingDraft(
                messageId = streamingMessageId.orEmpty(),
                content = "",
                revealBuffer = "",
                anchoredUserMessageId = anchoredUserMessageId,
                savedAtMs = SystemClock.uptimeMillis()
            )
        )
        streamingBackgrounded = false
        prepareScrollRuntimeForStreamingStart(
            runtime = scrollRuntime,
            preserveUserBrowsing = shouldPreserveUserBrowsingForStreamingStart()
        )
        streamRevealJob?.cancel()
        streamRevealJob = null
        sendStartAnchorActive = !shouldSuppressAutomaticBottomAnchor()
        if (sendStartAnchorActive && latestMessageIndexOrMinusOne() >= 0) {
            requestProgrammaticForwardListBottomAnchor()
        }
        sendUiSettling = false
        persistTick++
        val hasPendingBackgroundSend = hasRemoteHistorySource &&
            PendingChatSendStore.has(context, chatScopeId, userId)
        if (hasPendingBackgroundSend) {
            PendingChatSendRuntime.markActive(userId)
            if (uploadedImageUrls.isNotEmpty()) {
                PendingChatSendStore.updateImageUrls(
                    context = context,
                    chatScopeId = chatScopeId,
                    userMessageId = userId,
                    imageUrls = uploadedImageUrls
                )
            }
        }
        val sendStartSnapshot = persistableLocalChatWindowSnapshot()
        context.saveLocalChatWindowSync(chatScopeId, sendStartSnapshot)
        snackbarScope.launch {
            try {
                if (hasRemoteHistorySource) {
                    SessionApi.cancelCurrentStream()
                    val clientRegion = refreshClientRegionForSend()
                    if (hasPendingBackgroundSend) {
                        PendingChatSendStore.updateRegion(
                            context = context,
                            chatScopeId = chatScopeId,
                            userMessageId = userId,
                            region = clientRegion
                        )
                        PendingChatSendWorkScheduler.markRemoteStarted(context, chatScopeId, userId)
                    }
                    val remoteStreamStartMs = SystemClock.uptimeMillis()
                    val remoteFirstChunkLock = Any()
                    val remoteFirstChunkQueue = mutableListOf<String>()
                    var remoteFirstChunkFlushScheduled = false
                    var remoteFirstChunkReleased = false
                    var remoteFirstChunkTerminalAction: (() -> Unit)? = null
                    fun flushRemoteFirstChunkGate() {
                        val chunksToFlush: List<String>
                        val terminalAction: (() -> Unit)?
                        synchronized(remoteFirstChunkLock) {
                            if (remoteFirstChunkReleased) return
                            remoteFirstChunkReleased = true
                            chunksToFlush = remoteFirstChunkQueue.toList()
                            remoteFirstChunkQueue.clear()
                            terminalAction = remoteFirstChunkTerminalAction
                            remoteFirstChunkTerminalAction = null
                        }
                        chunksToFlush.forEach { queuedPiece -> appendAssistantChunk(queuedPiece) }
                        terminalAction?.invoke()
                    }
                    fun enqueueRemoteChunk(piece: String) {
                        var shouldAppendNow = false
                        var delayMs: Long? = null
                        synchronized(remoteFirstChunkLock) {
                            if (remoteFirstChunkReleased) {
                                shouldAppendNow = true
                            } else {
                                remoteFirstChunkQueue.add(piece)
                                if (!remoteFirstChunkFlushScheduled) {
                                    remoteFirstChunkFlushScheduled = true
                                    val elapsedMs = SystemClock.uptimeMillis() - remoteStreamStartMs
                                    delayMs = (REMOTE_STREAM_MIN_BALL_MS - elapsedMs).coerceAtLeast(0L)
                                }
                            }
                        }
                        if (shouldAppendNow) {
                            appendAssistantChunk(piece)
                        } else {
                            delayMs?.let { waitMs ->
                                mainHandler.postDelayed({ flushRemoteFirstChunkGate() }, waitMs)
                            }
                        }
                    }
                    fun runAfterRemoteFirstChunkGate(action: () -> Unit) {
                        var shouldRunNow = false
                        synchronized(remoteFirstChunkLock) {
                            if (remoteFirstChunkReleased || remoteFirstChunkQueue.isEmpty()) {
                                shouldRunNow = true
                            } else {
                                remoteFirstChunkTerminalAction = action
                            }
                        }
                        if (shouldRunNow) action()
                    }
                    SessionApi.streamChat(
                        options = SessionApi.StreamOptions(
                            clientMsgId = userId,
                            text = text,
                            images = uploadedImageUrls,
                            sessionGeneration = streamSessionGeneration,
                            region = clientRegion?.region,
                            regionSource = clientRegion?.source,
                            regionReliability = clientRegion?.reliability,
                            todayAgriContextDay = resolvedTodayAgriContextDay
                        ),
                        onChunk = { piece -> enqueueRemoteChunk(piece) },
                        onComplete = { runAfterRemoteFirstChunkGate { finishStreaming() } },
                        onInterrupted = { reason ->
                            runAfterRemoteFirstChunkGate {
                                handleAssistantInterrupted(userId, reason)
                            }
                        }
                    )
                } else {
                    handleAssistantInterrupted(userId, "backend_not_configured")
                }
            } finally {
                sendUiSettling = false
            }
        }
    }

    fun refreshChatListMetrics(listState: LazyListState) {
        updateChatListMetrics(readChatListMetrics(listState))
    }

    fun beginProgrammaticChatListScroll() {
        programmaticBottomAnchorGeneration += 1
        com.nongjiqianwen.beginProgrammaticChatListScroll(scrollRuntime.programmaticScroll)
    }

    fun endProgrammaticChatListScroll() {
        programmaticBottomAnchorGeneration += 1
        com.nongjiqianwen.endProgrammaticChatListScroll(
            programmaticScrollState = scrollRuntime.programmaticScroll,
            listState = chatListState,
            refreshChatListMetrics = ::refreshChatListMetrics
        )
    }
    suspend fun scrollForwardListToBottom(force: Boolean = false) {
        if (!force && shouldSuppressAutomaticBottomAnchor()) return
        val targetIndex = bottomAnchorIndexOrMinusOne()
        if (targetIndex < 0) return
        beginProgrammaticChatListScroll()
        try {
            chatListState.scrollToItem(
                index = targetIndex,
                scrollOffset = FORWARD_LIST_BOTTOM_SCROLL_OFFSET
            )
            withFrameNanos { }
        } finally {
            endProgrammaticChatListScroll()
        }
    }
    fun shouldTrackChatListBrowsingFromPointer(): Boolean {
        return isStreaming || hasStreamingItem || imageSendInProgress || isInitialWorklineTopFlow()
    }
    fun markChatListUserBrowsingFromPointer() {
        if (!shouldTrackChatListBrowsingFromPointer()) return
        endProgrammaticChatListScroll()
        sendStartAnchorActive = false
        if (isInitialWorklineTopFlow()) {
            initialWorklinePhase = InitialWorklinePhase.WorklineOwned
        }
        scrollRuntime.userInteracting.value = true
        if (scrollMode != ScrollMode.UserBrowsing) {
            scrollMode = ScrollMode.UserBrowsing
        }
    }
    fun shouldContinueProgrammaticChatListScroll(): Boolean {
        if (!isStreaming && !hasStreamingItem) return true
        return scrollMode != ScrollMode.UserBrowsing &&
            !scrollRuntime.userInteracting.value &&
            !chatListUserDragging
    }
    val scrollToBottom: suspend (Boolean) -> Unit = scrollToBottom@{ animated ->
        if (shouldSuppressAutomaticBottomAnchor()) return@scrollToBottom
        com.nongjiqianwen.scrollChatListToBottom(
            listState = chatListState,
            targetBottomIndex = bottomAnchorIndexOrMinusOne(),
            targetBottomScrollOffset = FORWARD_LIST_BOTTOM_SCROLL_OFFSET,
            animated = animated,
            beginProgrammaticScroll = ::beginProgrammaticChatListScroll,
            endProgrammaticScroll = ::endProgrammaticChatListScroll,
            shouldContinue = ::shouldContinueProgrammaticChatListScroll
        )
    }

    restoreBottomAnchorIfNeededAfterStreamingStop =
        restoreBottomAnchorIfNeededAfterStreamingStop@{ _ ->
            // Pending finalize already waits for a fresh settled bound. Running
            // another bottom restore in the same handoff window can read cleared
            // bounds and fall back to transient LazyList item geometry, causing
            // the completed message to jump upward with a large blank below.
            return@restoreBottomAnchorIfNeededAfterStreamingStop
        }
    LaunchedEffect(
        startupHydrationBarrierSatisfied,
        startupLayoutReady,
        startupBottomReserveReady,
        messages.size,
        hasTodayAgriCard,
        isStreaming,
        hasStreamingItem,
        initialBottomSnapDone,
        currentBottomOverflowPx(),
        chatListState.canScrollForward,
        chatListUserDragging,
        recyclerScrollInProgress,
        scrollRuntime.userInteracting.value,
        scrollMode
    ) {
        if (initialBottomSnapDone) return@LaunchedEffect
        if (!startupHydrationBarrierSatisfied || !startupLayoutReady) {
            return@LaunchedEffect
        }
        val hasStaticVisualTimeline = hasStaticVisualTimeline()
        if (!hasStaticVisualTimeline && !hasStreamingItem) {
            initialBottomSnapDone = true
            return@LaunchedEffect
        }
        if (isInitialWorklineTopFlow() && !hasInitialDocumentFlowReachedWorkline()) {
            initialBottomSnapDone = true
            return@LaunchedEffect
        }
        if (!hasStaticVisualTimeline || isStreaming || hasStreamingItem) return@LaunchedEffect
        if (!startupBottomReserveReady) return@LaunchedEffect
        if (userBlocksHydratedVisualMutation()) return@LaunchedEffect
        if (isWithinStaticBottomTolerance()) {
            initialBottomSnapDone = true
            return@LaunchedEffect
        }
        repeat(6) {
            if (userBlocksHydratedVisualMutation()) return@LaunchedEffect
            scrollToBottom(false)
            withFrameNanos { }
            if (userBlocksHydratedVisualMutation()) return@LaunchedEffect
            if (isWithinStaticBottomTolerance()) {
                initialBottomSnapDone = true
                return@LaunchedEffect
            }
        }
        if (!startupBottomSnapPendingLogged) {
            startupBottomSnapPendingLogged = true
            SessionApi.reportClientLog(
                level = "warn",
                event = "ui.chat_startup_bottom_snap_pending",
                message = "Chat startup bottom snap still pending",
                attrs = mapOf(
                    "has_local_items" to hasStartupLocalMessages,
                    "visible_item_count" to messages.size,
                    "has_today_agri_card" to hasTodayAgriCard,
                    "reserve_ready" to startupBottomReserveReady,
                    "can_scroll_forward" to chatListState.canScrollForward,
                    "bottom_overflow_px" to currentBottomOverflowPx(),
                    "workline_phase" to initialWorklinePhase.name.lowercase()
                )
            )
        }
    }

    LaunchedEffect(initialBottomSnapDone) {
        if (!initialBottomSnapDone) {
            postInitialSnapCorrectionDone = false
        }
    }

    LaunchedEffect(
        initialBottomSnapDone,
        postInitialSnapCorrectionDone,
        startupBottomReserveReady,
        latestConversationBottomPaddingPx,
        messages.size,
        hasTodayAgriCard,
        hasStartedConversation,
        isStreaming,
        hasStreamingItem,
        chatListUserDragging,
        recyclerScrollInProgress
    ) {
        if (!initialBottomSnapDone) return@LaunchedEffect
        if (postInitialSnapCorrectionDone) return@LaunchedEffect
        if (!startupBottomReserveReady) return@LaunchedEffect
        if (hasStartedConversation) return@LaunchedEffect
        if (!hasStaticVisualTimeline() || isStreaming || hasStreamingItem) {
            return@LaunchedEffect
        }
        if (chatListUserDragging || recyclerScrollInProgress || scrollRuntime.userInteracting.value) {
            return@LaunchedEffect
        }
        withFrameNanos { }
        if (chatListUserDragging || recyclerScrollInProgress || scrollRuntime.userInteracting.value) {
            return@LaunchedEffect
        }
        scrollToBottom(false)
        postInitialSnapCorrectionDone = true
    }

    LaunchedEffect(
        sendStartAnchorActive,
        sendUiSettling
    ) {
        if (!sendStartAnchorActive && !sendUiSettling) {
            lockedConversationBottomPaddingPx = -1
        }
    }
    LaunchedEffect(
        isStreaming,
        hasStreamingItem,
        inputFieldFocused,
        imeVisible
    ) {
        if ((isStreaming || hasStreamingItem) && (inputFieldFocused || imeVisible)) {
            sendStartAnchorActive = false
            sendUiSettling = false
            lockedConversationBottomPaddingPx = -1
        }
    }
    LaunchedEffect(
        pendingStreamingFinalizeMessageId,
        isStreaming,
        messages.size,
        startupBottomReserveReady,
        streamingBackgrounded,
        pendingStreamingFinalizeStartedAtMs
    ) {
        val pendingMessageId = pendingStreamingFinalizeMessageId
        if (pendingMessageId.isNullOrBlank()) return@LaunchedEffect
        if (!isStreaming) {
            clearPendingStreamingFinalize()
            return@LaunchedEffect
        }
        if (messages.none { it.id == pendingMessageId }) {
            clearPendingStreamingFinalize()
            return@LaunchedEffect
        }
        if (streamingBackgrounded) return@LaunchedEffect
        if (!pendingStreamingFinalizeShouldRestoreBottomAnchor) {
            finalizeStreamingStop(shouldRestoreBottomAnchor = false)
            return@LaunchedEffect
        }
        val elapsedMs = (SystemClock.uptimeMillis() - pendingStreamingFinalizeStartedAtMs)
            .coerceAtLeast(0L)
        val remainingBoundsWaitMs = (STREAMING_FINALIZE_BOUNDS_TIMEOUT_MS - elapsedMs)
            .coerceAtLeast(0L)
        val receivedFreshBounds = if (startupBottomReserveReady && remainingBoundsWaitMs > 0L) {
            withTimeoutOrNull(remainingBoundsWaitMs) {
                snapshotFlow {
                    messageContentBoundsById[pendingMessageId]?.takeIf { bounds ->
                        bounds.bottom > bounds.top && bounds.bottom > 0f
                    }
                }
                    .filterNotNull()
                    .first()
            } != null
        } else {
            if (remainingBoundsWaitMs > 0L) {
                delay(remainingBoundsWaitMs)
            }
            false
        }
        if (pendingStreamingFinalizeMessageId == pendingMessageId && isStreaming) {
            if (
                receivedFreshBounds &&
                scrollMode != ScrollMode.UserBrowsing &&
                !scrollRuntime.userInteracting.value &&
                !chatListUserDragging
            ) {
                requestProgrammaticForwardListBottomAnchor()
            }
            finalizeStreamingStop(
                shouldRestoreBottomAnchor = false
            )
        }
    }

    DisposableEffect(lifecycleOwner, focusManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                context.saveLocalComposerDraftSync(chatScopeId, input.value.text)
                suppressJumpButtonForLifecycleResume = false
                if (isStreaming) {
                    streamingBackgrounded = true
                }
                clearMessageSelection()
                clearInputSelectionToolbar()
                attachmentMenuVisible = false
                focusManager.clearFocus(force = true)
            } else if (event == Lifecycle.Event.ON_RESUME) {
                streamingBackgrounded = false
                scheduleBackgroundRemoteAssistantRecoveryIfNeeded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun jumpToBottom() {
        snackbarScope.launch {
            releaseInitialWorklineToBottom()
            performJumpToBottom(
                messagesCount = messages.size,
                hasVisualTailItem = bottomAnchorIndexOrMinusOne() >= 0,
                hasStreamingItem = hasStreamingItem,
                isStreaming = isStreaming,
                scrollModeState = scrollRuntime.scrollMode,
                userInteractingState = scrollRuntime.userInteracting,
                jumpButtonPulseVisibleState = scrollRuntime.jumpButtonPulseVisible,
                scrollToBottom = {
                    scrollForwardListToBottom(force = true)
                }
            )
        }
    }

    BindChatListScrollEffects(
        isStreaming = isStreaming,
        hasStreamingItem = hasStreamingItem,
        streamingMessageContent = streamingMessageContent,
        listScrollInProgress = recyclerScrollInProgress,
        isComposerSettling = isComposerSettling,
        sendStartAnchorActiveState = sendStartAnchorActiveState,
        scrollModeState = scrollRuntime.scrollMode,
        userInteractingState = scrollRuntime.userInteracting,
        currentStreamingContentBottomPx = ::currentStreamingContentBottomPx,
        currentStreamingLegalBottomPx = ::currentStreamingLegalBottomPx,
        isNearStreamingWorkline = ::isNearStreamingWorkline,
        isAtStreamingWorklineStrict = ::isAtStreamingWorklineStrict,
        isAtPhysicalBottom = { !chatListState.canScrollForward },
        requestBottomAnchor = { requestProgrammaticForwardListBottomAnchor() }
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(appCenterTint)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                if (chatRootLeftPx != bounds.left) {
                    chatRootLeftPx = bounds.left
                }
                if (chatRootTopPx != bounds.top) {
                    chatRootTopPx = bounds.top
                }
            }
    ) {
        val chromeMaxWidth: Dp = when {
            maxWidth >= 900.dp -> 900.dp
            maxWidth >= 700.dp -> 760.dp
            else -> maxWidth
        }
        val inputBarHeight = if (maxWidth < 360.dp) 100.dp else 104.dp
        val inputBarMaxHeight = if (maxWidth < 360.dp) 232.dp else 248.dp
        val topButtonTouchSize = 48.dp
        val topMenuIconSize = if (maxWidth < 360.dp) 31.dp else 32.dp
        val topChromeIconVisualInset = (topButtonTouchSize - topMenuIconSize) / 2f
        val listHorizontalPadding = when {
            maxWidth < 360.dp -> 12.dp
            maxWidth < 600.dp -> 16.dp
            else -> 24.dp
        }
        val chromeHorizontalPadding =
            (listHorizontalPadding - topChromeIconVisualInset).coerceAtLeast(4.dp)
        val topTitleFontSize = if (maxWidth < 360.dp) 16.sp else 17.sp
        val topTitleLineHeight = if (maxWidth < 360.dp) 20.sp else 21.sp
        val membershipIconSize = if (maxWidth < 360.dp) 36.dp else 38.dp
        val actionCircleSize = if (maxWidth < 360.dp) 34.dp else 36.dp
        val addButtonSize = actionCircleSize
        val addIconSize = if (maxWidth < 360.dp) 24.dp else 26.dp
        val sendButtonSize = actionCircleSize
        val userBubbleMaxWidth = if (chromeMaxWidth < 440.dp) chromeMaxWidth * 0.84f else 448.dp
        val topBarReservedHeight = topInset + topButtonTouchSize + TOP_CHROME_MASK_EXTRA
        val chatListTopPaddingPx = with(density) { topBarReservedHeight.roundToPx() }
        suspend fun uploadComposerImagesForSend(
            images: List<ComposerImageAttachment>
        ): Pair<List<String>?, String?> = withContext(Dispatchers.IO) {
            val compressedImages = mutableListOf<ByteArray>()
            for (image in images) {
                val imageUri = Uri.parse(image.uri)
                val originalBytes = context.readImageBytes(imageUri)
                    ?: return@withContext null to ImageUploader.DECODE_FAIL_MESSAGE
                val uploadBytes = if (
                    context.isPrivateComposerImage(imageUri) &&
                    originalBytes.hasJpegStartMarker() &&
                    originalBytes.size <= COMPOSER_MAX_IMAGE_SIZE_BYTES
                ) {
                    originalBytes
                } else {
                    val compressed = ImageUploader.compressImage(originalBytes)
                        ?: return@withContext null to ImageUploader.DECODE_FAIL_MESSAGE
                    compressed.bytes
                }
                compressedImages.add(uploadBytes)
            }
            if (compressedImages.isEmpty()) {
                emptyList<String>() to null
            } else {
                val result = ImageUploader.uploadImagesWithResult(compressedImages)
                if (result.urls == null) null to result.errorMessage else result.urls to null
            }
        }
        fun retryFailedUserMessage(messageId: String) {
            if (retryingUserMessageIds[messageId] == true) return
            if (isStreaming || sendUiSettling || imageSendInProgress) return
            val failedMessage = messages.firstOrNull { it.id == messageId } ?: return
            if (isQuotaExhaustedToday()) {
                showQuotaExhaustedHint()
                return
            }
            if (hasRemoteHistorySource && !context.hasActiveNetworkConnection()) {
                showComposerStatusHint(NETWORK_UNAVAILABLE_HINT_TEXT)
                return
            }
            val previewImageUris = failedMessage.imageUris.orEmpty()
            if (previewImageUris.isNotEmpty() && failedMessage.imageUrls.orEmpty().isEmpty()) {
                if (!context.hasActiveNetworkConnection()) {
                    showComposerStatusHint(NETWORK_UNAVAILABLE_HINT_TEXT)
                    return
                }
                val retrySessionGeneration = SessionApi.currentSessionGenerationOrNull()
                val retryTodayAgriContextDay = resolveTodayAgriContextDayForSend(failedMessage.id)
                if (hasRemoteHistorySource) {
                    val region = currentClientRegionForSend()
                    PendingChatSendRuntime.markActive(failedMessage.id)
                    PendingChatSendWorkScheduler.enqueue(
                        context = context,
                        pending = PendingChatSend(
                            chatScopeId = chatScopeId,
                            userMessageId = failedMessage.id,
                            text = failedMessage.content,
                            imageUris = previewImageUris,
                            sessionGeneration = retrySessionGeneration,
                            region = region?.region,
                            regionSource = region?.source,
                            regionReliability = region?.reliability,
                            todayAgriContextDay = retryTodayAgriContextDay
                        )
                    )
                }
                retryingUserMessageIds[failedMessage.id] = true
                imageSendInProgress = true
                val uploadClearEpoch = chatHistoryClearEpoch
                imageSendGeneration++
                val uploadGeneration = imageSendGeneration
                imageSendJob?.cancel()
                imageSendJob = snackbarScope.launch {
                    try {
                        val (uploadedUrls, uploadError) = uploadComposerImagesForSend(
                            previewImageUris.map(::ComposerImageAttachment)
                        )
                        if (uploadClearEpoch != chatHistoryClearEpoch) return@launch
                        if (uploadError != null || uploadedUrls.isNullOrEmpty()) {
                            PendingChatSendWorkScheduler.cancelAndRemove(
                                context,
                                chatScopeId,
                                failedMessage.id
                            )
                            failedUserMessageStates[failedMessage.id] = "network"
                            retryingUserMessageIds.remove(failedMessage.id)
                            persistTick++
                            uploadError?.let(::showComposerStatusHint)
                            return@launch
                        }
                        if (
                            hasRemoteHistorySource &&
                            !PendingChatSendStore.has(context, chatScopeId, failedMessage.id)
                        ) {
                            return@launch
                        }
                        PendingChatSendStore.updateImageUrls(
                            context = context,
                            chatScopeId = chatScopeId,
                            userMessageId = failedMessage.id,
                            imageUrls = uploadedUrls
                        )
                        if (hasRemoteHistorySource) {
                            PendingChatSendStore.updateRegion(
                                context = context,
                                chatScopeId = chatScopeId,
                                userMessageId = failedMessage.id,
                                region = refreshClientRegionForSend()
                            )
                        }
                        commitSendMessage(
                            text = failedMessage.content,
                            uploadedImageUrls = uploadedUrls,
                            previewImageUris = previewImageUris,
                            existingUserMessageId = failedMessage.id,
                            collapseComposer = false,
                            sessionGeneration = retrySessionGeneration,
                            todayAgriContextDay = retryTodayAgriContextDay
                        )
                    } finally {
                        if (imageSendGeneration == uploadGeneration) {
                            retryingUserMessageIds.remove(failedMessage.id)
                            imageSendInProgress = false
                            imageSendJob = null
                        }
                    }
                }
                return
            }
            commitSendMessage(
                text = failedMessage.content,
                uploadedImageUrls = failedMessage.imageUrls.orEmpty(),
                previewImageUris = previewImageUris,
                existingUserMessageId = failedMessage.id,
                collapseComposer = false,
                todayAgriContextDay = resolveTodayAgriContextDayForSend(failedMessage.id)
            )
        }
        fun retryFailedAssistantMessage(assistantMessageId: String) {
            if (retryingAssistantMessageIds[assistantMessageId] == true) return
            if (isStreaming || sendUiSettling || imageSendInProgress) return
            val failedState = failedAssistantMessageStates[assistantMessageId] ?: return
            val sourceUserMessage = messages.firstOrNull { it.id == failedState.sourceUserMessageId } ?: return
            if (isQuotaExhaustedToday()) {
                showQuotaExhaustedHint()
                return
            }
            if (hasRemoteHistorySource && !context.hasActiveNetworkConnection()) {
                showComposerStatusHint(NETWORK_UNAVAILABLE_HINT_TEXT)
                return
            }
            val previewImageUris = sourceUserMessage.imageUris.orEmpty()
            val uploadedImageUrls = sourceUserMessage.imageUrls.orEmpty()
            if (previewImageUris.isNotEmpty() && uploadedImageUrls.isEmpty()) {
                if (!context.hasActiveNetworkConnection()) {
                    showComposerStatusHint(NETWORK_UNAVAILABLE_HINT_TEXT)
                    return
                }
                val retrySessionGeneration = SessionApi.currentSessionGenerationOrNull()
                val retryTodayAgriContextDay = resolveTodayAgriContextDayForSend(sourceUserMessage.id)
                if (hasRemoteHistorySource) {
                    val region = currentClientRegionForSend()
                    PendingChatSendRuntime.markActive(sourceUserMessage.id)
                    PendingChatSendWorkScheduler.enqueue(
                        context = context,
                        pending = PendingChatSend(
                            chatScopeId = chatScopeId,
                            userMessageId = sourceUserMessage.id,
                            text = sourceUserMessage.content,
                            imageUris = previewImageUris,
                            sessionGeneration = retrySessionGeneration,
                            region = region?.region,
                            regionSource = region?.source,
                            regionReliability = region?.reliability,
                            todayAgriContextDay = retryTodayAgriContextDay
                        )
                    )
                }
                retryingAssistantMessageIds[assistantMessageId] = true
                imageSendInProgress = true
                val uploadClearEpoch = chatHistoryClearEpoch
                imageSendGeneration++
                val uploadGeneration = imageSendGeneration
                imageSendJob?.cancel()
                imageSendJob = snackbarScope.launch {
                    try {
                        val (retryUploadedUrls, uploadError) = uploadComposerImagesForSend(
                            previewImageUris.map(::ComposerImageAttachment)
                        )
                        if (uploadClearEpoch != chatHistoryClearEpoch) return@launch
                        if (uploadError != null || retryUploadedUrls.isNullOrEmpty()) {
                            PendingChatSendWorkScheduler.cancelAndRemove(
                                context,
                                chatScopeId,
                                sourceUserMessage.id
                            )
                            failedAssistantMessageStates[assistantMessageId] = failedState
                            retryingAssistantMessageIds.remove(assistantMessageId)
                            persistTick++
                            uploadError?.let(::showComposerStatusHint)
                            return@launch
                        }
                        if (
                            hasRemoteHistorySource &&
                            !PendingChatSendStore.has(context, chatScopeId, sourceUserMessage.id)
                        ) {
                            return@launch
                        }
                        PendingChatSendStore.updateImageUrls(
                            context = context,
                            chatScopeId = chatScopeId,
                            userMessageId = sourceUserMessage.id,
                            imageUrls = retryUploadedUrls
                        )
                        if (hasRemoteHistorySource) {
                            PendingChatSendStore.updateRegion(
                                context = context,
                                chatScopeId = chatScopeId,
                                userMessageId = sourceUserMessage.id,
                                region = refreshClientRegionForSend()
                            )
                        }
                        val existingAssistantIndex = messages.indexOfFirst { it.id == assistantMessageId }
                        if (existingAssistantIndex >= 0) {
                            messages.removeAt(existingAssistantIndex)
                        }
                        failedAssistantMessageStates.remove(assistantMessageId)
                        commitSendMessage(
                            text = sourceUserMessage.content,
                            uploadedImageUrls = retryUploadedUrls,
                            previewImageUris = previewImageUris,
                            existingUserMessageId = sourceUserMessage.id,
                            collapseComposer = false,
                            sessionGeneration = retrySessionGeneration,
                            todayAgriContextDay = retryTodayAgriContextDay
                        )
                    } finally {
                        if (imageSendGeneration == uploadGeneration) {
                            retryingAssistantMessageIds.remove(assistantMessageId)
                            imageSendInProgress = false
                            imageSendJob = null
                        }
                    }
                }
                return
            }
            failedAssistantMessageStates.remove(assistantMessageId)
            val existingAssistantIndex = messages.indexOfFirst { it.id == assistantMessageId }
            if (existingAssistantIndex >= 0) {
                messages.removeAt(existingAssistantIndex)
            }
            val retrySessionGeneration = SessionApi.currentSessionGenerationOrNull()
            val retryTodayAgriContextDay = resolveTodayAgriContextDayForSend(sourceUserMessage.id)
            if (hasRemoteHistorySource && uploadedImageUrls.isNotEmpty()) {
                val region = currentClientRegionForSend()
                PendingChatSendRuntime.markActive(sourceUserMessage.id)
                PendingChatSendWorkScheduler.enqueue(
                    context = context,
                    pending = PendingChatSend(
                        chatScopeId = chatScopeId,
                        userMessageId = sourceUserMessage.id,
                        text = sourceUserMessage.content,
                        imageUris = previewImageUris,
                        imageUrls = uploadedImageUrls,
                        sessionGeneration = retrySessionGeneration,
                        region = region?.region,
                        regionSource = region?.source,
                        regionReliability = region?.reliability,
                        todayAgriContextDay = retryTodayAgriContextDay
                    )
                )
            }
            commitSendMessage(
                text = sourceUserMessage.content,
                uploadedImageUrls = uploadedImageUrls,
                previewImageUris = previewImageUris,
                existingUserMessageId = sourceUserMessage.id,
                collapseComposer = false,
                sessionGeneration = retrySessionGeneration,
                todayAgriContextDay = retryTodayAgriContextDay
            )
        }
        fun performSendMessage(
            rawText: String,
            trimmedText: String,
            imageSnapshot: List<ComposerImageAttachment>
        ) {
            val sendGate = buildSendGateState(
                rawInput = rawText,
                isStreaming = isStreaming || sendUiSettling || imageSendInProgress,
                exceedsInputLimit = rawText.length > INPUT_MAX_CHARS,
                quotaExhausted = isQuotaExhaustedToday(),
                hasImages = imageSnapshot.isNotEmpty()
            )
            if (sendGate.blockReason == SendBlockReason.QuotaExhausted) {
                showQuotaExhaustedHint()
                return
            }
            if (!sendGate.canSubmit) return
            if (hasRemoteHistorySource && !context.hasActiveNetworkConnection()) {
                if (imageSnapshot.isEmpty()) {
                    markUserMessageSendFailed(trimmedText)
                } else {
                    markUserMessageSendFailed(
                        text = trimmedText,
                        imageUris = imageSnapshot.map { it.uri }
                    )
                }
                return
            }
            if (imageSnapshot.isEmpty()) {
                val existingUserMessageId = findFailedUserMessageIdByText(trimmedText)
                commitSendMessage(
                    text = trimmedText,
                    existingUserMessageId = existingUserMessageId,
                    todayAgriContextDay = resolveTodayAgriContextDayForSend(existingUserMessageId)
                )
                return
            }
            val previewImageUris = imageSnapshot.map { it.uri }
            val imageSendSessionGeneration = SessionApi.currentSessionGenerationOrNull()
            val imageSendTodayAgriContextDay = todayAgriContextDayForNextSend()
            val stagedUserMessageId = stageUserMessageForImageUpload(
                text = trimmedText,
                previewImageUris = previewImageUris,
                sessionGeneration = imageSendSessionGeneration,
                todayAgriContextDay = imageSendTodayAgriContextDay
            ) ?: return
            imageSendInProgress = true
            val uploadClearEpoch = chatHistoryClearEpoch
            imageSendGeneration++
            val uploadGeneration = imageSendGeneration
            imageSendJob?.cancel()
            imageSendJob = snackbarScope.launch {
                try {
                    val (uploadedUrls, uploadError) = uploadComposerImagesForSend(imageSnapshot)
                    if (uploadClearEpoch != chatHistoryClearEpoch) return@launch
                    if (uploadError != null || uploadedUrls.isNullOrEmpty()) {
                        PendingChatSendWorkScheduler.cancelAndRemove(
                            context,
                            chatScopeId,
                            stagedUserMessageId
                        )
                        failedUserMessageStates[stagedUserMessageId] = "network"
                        persistTick++
                        context.saveLocalChatWindowIfEpoch(
                            chatScopeId = chatScopeId,
                            snapshot = persistableLocalChatWindowSnapshot(),
                            expectedEpoch = uploadClearEpoch,
                            epochRef = chatHistoryClearEpochRef
                        )
                        uploadError?.let(::showComposerStatusHint)
                    } else {
                        if (
                            hasRemoteHistorySource &&
                            !PendingChatSendStore.has(context, chatScopeId, stagedUserMessageId)
                        ) {
                            return@launch
                        }
                        PendingChatSendStore.updateImageUrls(
                            context = context,
                            chatScopeId = chatScopeId,
                            userMessageId = stagedUserMessageId,
                            imageUrls = uploadedUrls
                        )
                        if (hasRemoteHistorySource) {
                            PendingChatSendStore.updateRegion(
                                context = context,
                                chatScopeId = chatScopeId,
                                userMessageId = stagedUserMessageId,
                                region = refreshClientRegionForSend()
                            )
                        }
                        failedUserMessageStates.remove(stagedUserMessageId)
                        commitSendMessage(
                            text = trimmedText,
                            uploadedImageUrls = uploadedUrls,
                            previewImageUris = previewImageUris,
                            existingUserMessageId = stagedUserMessageId,
                            collapseComposer = false,
                            sessionGeneration = imageSendSessionGeneration,
                            todayAgriContextDay = imageSendTodayAgriContextDay
                        )
                    }
                } finally {
                    if (imageSendGeneration == uploadGeneration) {
                        imageSendInProgress = false
                        imageSendJob = null
                    }
                }
            }
        }
        fun sendMessage() {
            val text = input.value.text
            val trimmedText = text.trim()
            val imageSnapshot = selectedComposerImages.take(COMPOSER_MAX_IMAGE_COUNT)
            val sendGate = buildSendGateState(
                rawInput = text,
                isStreaming = isStreaming || sendUiSettling || imageSendInProgress,
                exceedsInputLimit = text.length > INPUT_MAX_CHARS,
                quotaExhausted = isQuotaExhaustedToday(),
                hasImages = imageSnapshot.isNotEmpty()
            )
            if (sendGate.blockReason == SendBlockReason.QuotaExhausted) {
                showQuotaExhaustedHint()
                return
            }
            if (!sendGate.canSubmit) return
            performSendMessage(text, trimmedText, imageSnapshot)
        }
        val pageSurface = chatPageSurface
        val navigationBottomInset: Dp = WindowInsets.navigationBars
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding()
        val inputChromeHorizontalPadding = when {
            maxWidth < 360.dp -> 12.dp
            maxWidth < 600.dp -> 16.dp
            else -> 24.dp
        }
        val inputChromeBottomPadding = 8.dp
        val inputChromeSurface = Color.White
        val inputChromeBorder = Color(0xFFB9C0C8).copy(alpha = 0.88f)
        val inputFieldSurface = Color.White
        val inputFieldBorder = Color(0xFFD7DCE2).copy(alpha = 0.98f)
        val globalStatusHintText = resolveComposerOverlayHintText(
            composerStatusHintVisible = composerStatusHintVisible,
            composerStatusHintText = composerStatusHintText,
            inputLimitHintVisible = inputLimitHintVisible
        )
        val globalStatusHintVisible = globalStatusHintText != null &&
            !attachmentMenuVisible &&
            !membershipCenterVisible &&
            !hamburgerMenuVisible &&
            !uiCopyPreviewVisible &&
            inputSelectionToolbarState == null &&
            activeMessageSelectionState == null
        val inputTextToolbar = remember(uiRuntimeResetKey) {
            buildInputSelectionTextToolbar()
        }
        val renderConversationMessage: @Composable (ChatMessage) -> Unit = { msg ->
            DisposableEffect(msg.id) {
                onDispose {
                    messageSelectionBoundsById.remove(msg.id)
                    messageContentBoundsById.remove(msg.id)
                    if (pendingMessageSelectionToolbarState?.messageId == msg.id) {
                        pendingMessageSelectionToolbarState = null
                    }
                    if (messageSelectionToolbarState?.messageId == msg.id) {
                        messageSelectionToolbarState = null
                    }
                }
            }
            val isActiveStreamingAssistant =
                msg.role == ChatRole.ASSISTANT &&
                    msg.id == streamingMessageId &&
                    (isStreaming || streamingMessageContent.isNotBlank())
            val isPendingStreamingFinalizeAssistant =
                msg.role == ChatRole.ASSISTANT &&
                    msg.id == pendingStreamingFinalizeMessageId
            val assistantDisplayContent =
                if (isActiveStreamingAssistant && (isStreaming || streamingMessageContent.isNotBlank())) {
                    streamingMessageContent
                } else {
                    msg.content
                }
            val copySourceContent =
                when {
                    msg.role != ChatRole.ASSISTANT -> msg.content
                    isActiveStreamingAssistant &&
                        isStreaming &&
                        !isPendingStreamingFinalizeAssistant -> ""
                    else -> assistantDisplayContent
                }
            val fullCopyText = remember(msg.role, copySourceContent) {
                buildRenderedMessageCopyText(msg.role, copySourceContent)
            }
            val messageTextToolbar = remember(msg.id, msg.role, fullCopyText) {
                buildMessageSelectionTextToolbar(
                    messageId = msg.id,
                    role = msg.role,
                    fullCopyText = fullCopyText
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = listHorizontalPadding,
                        vertical = CHAT_MESSAGE_ITEM_VERTICAL_PADDING
                    )
                    .then(
                        if (
                            hasActiveMessageSelection &&
                            activeMessageSelectionMessageId != msg.id
                        ) {
                            selectionDismissTapModifier(
                                activeMessageSelectionMessageId ?: "selection",
                                msg.id
                            )
                        } else {
                            Modifier
                        }
                    )
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(max = chromeMaxWidth)
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            if (msg.role == ChatRole.ASSISTANT) {
                                val bounds = coordinates.boundsInWindow()
                                updateMessageSelectionBoundsIfNeeded(msg.id, bounds)
                                applyPendingMessageSelectionToolbarIfReady(
                                    messageId = msg.id,
                                    bounds = bounds
                                )
                            }
                        }
                ) {
                    val failedUserState = failedUserMessageStates[msg.id]
                    val failedAssistantState = failedAssistantMessageStates[msg.id]
                    if (msg.role == ChatRole.ASSISTANT) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (failedAssistantState != null) {
                                        Modifier.onGloballyPositioned { coordinates ->
                                            updateMessageContentBounds(
                                                msg.id,
                                                coordinates.boundsInWindow()
                                            )
                                        }
                                    } else {
                                        Modifier
                                    }
                                ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CompositionLocalProvider(
                                LocalTextSelectionColors provides messageSelectionColors,
                                LocalTextToolbar provides messageTextToolbar
                            ) {
                                key(messageSelectionResetEpoch) {
                                    val renderMode = when {
                                        isPendingStreamingFinalizeAssistant -> StreamingRenderMode.Settled
                                        isActiveStreamingAssistant &&
                                            isStreaming &&
                                            assistantDisplayContent.isBlank() -> StreamingRenderMode.Waiting
                                        isActiveStreamingAssistant && isStreaming -> StreamingRenderMode.Streaming
                                        else -> StreamingRenderMode.Settled
                                    }
                                    ChatStreamingRenderer(
                                        content = assistantDisplayContent,
                                        renderMode = renderMode,
                                        showWaitingBall = renderMode == StreamingRenderMode.Waiting,
                                        selectionEnabled =
                                            !isStreaming ||
                                                isPendingStreamingFinalizeAssistant ||
                                                !isActiveStreamingAssistant,
                                        showDisclaimer = true,
                                        tableCopyEnabled = failedAssistantState == null,
                                        onStreamingContentBoundsChanged = { bounds ->
                                            if (failedAssistantState == null) {
                                                if (bounds != null) {
                                                    updateMessageContentBounds(msg.id, bounds)
                                                    if (isActiveStreamingAssistant) {
                                                        val nextStreamingContentBottomPx =
                                                            (bounds.bottom - messageViewportTopPx).roundToInt()
                                                        if (streamingContentBottomPx != nextStreamingContentBottomPx) {
                                                            streamingContentBottomPx = nextStreamingContentBottomPx
                                                        }
                                                    }
                                                } else {
                                                    updateMessageContentBounds(msg.id, null)
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            if (failedAssistantState != null) {
                                val assistantRetrying = retryingAssistantMessageIds[msg.id] == true
                                MessageStatusFooter(
                                    statusText = if (assistantRetrying) {
                                        ASSISTANT_RETRYING_STATUS_TEXT
                                    } else {
                                        ASSISTANT_RETRY_STATUS_TEXT
                                    },
                                    actionText = if (assistantRetrying) {
                                        null
                                    } else {
                                        ASSISTANT_RETRY_ACTION_TEXT
                                    },
                                    alignEnd = false,
                                    enabled = !assistantRetrying &&
                                        !isStreaming &&
                                        !sendUiSettling &&
                                        !imageSendInProgress,
                                    onActionClick = {
                                        performButtonHaptic()
                                        retryFailedAssistantMessage(msg.id)
                                    }
                                )
                            }
                        }
                    } else {
                        val userImageUris = msg.imageUris.orEmpty()
                        val userImageUrls = msg.imageUrls.orEmpty()
                        val userMessageHasImages = userImageUris.isNotEmpty() || userImageUrls.isNotEmpty()
                        val isLocalPendingImageMessage = msg.isLocalImageUploadPendingUserMessage()
                        val showPendingImageSendFooter = shouldShowPendingImageSendFooter(
                            message = msg,
                            pendingExists = isLocalPendingImageMessage &&
                                PendingChatSendStore.has(context, chatScopeId, msg.id),
                            failedUserStateExists = failedUserState != null
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (
                                        failedUserState != null ||
                                        userMessageHasImages
                                    ) {
                                        Modifier.onGloballyPositioned { coordinates ->
                                            updateMessageContentBounds(
                                                msg.id,
                                                coordinates.boundsInWindow()
                                            )
                                        }
                                    } else {
                                        Modifier
                                    }
                                ),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            UserMessageImageStrip(
                                imageUris = userImageUris,
                                imageUrls = userImageUrls,
                                userBubbleMaxWidth = userBubbleMaxWidth
                            )
                            if (msg.content.isNotBlank()) {
                                SelectableRenderedUserMessageBubble(
                                    content = msg.content,
                                    textSelectionColors = messageSelectionColors,
                                    textToolbar = messageTextToolbar,
                                    selectionResetKey = messageSelectionResetEpoch,
                                    userBubbleMaxWidth = userBubbleMaxWidth,
                                    userBubbleColor = userBubbleColor,
                                    userBubbleBorderColor = userBubbleBorderColor,
                                    onBubbleBoundsChanged = { bounds ->
                                        if (bounds != null) {
                                            updateMessageSelectionBoundsIfNeeded(msg.id, bounds)
                                        }
                                        if (failedUserState == null && !userMessageHasImages) {
                                            updateMessageContentBounds(msg.id, bounds)
                                        }
                                        if (bounds != null) {
                                            applyPendingMessageSelectionToolbarIfReady(
                                                messageId = msg.id,
                                                bounds = bounds
                                            )
                                        }
                                    }
                                )
                            } else {
                                if (failedUserState == null && !userMessageHasImages) {
                                    updateMessageContentBounds(msg.id, null)
                                }
                            }
                            if (failedUserState != null) {
                                val userRetrying = retryingUserMessageIds[msg.id] == true
                                MessageStatusFooter(
                                    statusText = if (userRetrying) {
                                        USER_RETRYING_STATUS_TEXT
                                    } else {
                                        USER_RETRY_STATUS_TEXT
                                    },
                                    actionText = USER_RETRY_ACTION_TEXT.takeIf { !userRetrying },
                                    alignEnd = true,
                                    enabled = !userRetrying &&
                                        !isStreaming &&
                                        !sendUiSettling &&
                                        !imageSendInProgress,
                                    onActionClick = {
                                        performButtonHaptic()
                                        retryFailedUserMessage(msg.id)
                                    }
                                )
                            } else if (showPendingImageSendFooter) {
                                MessageStatusFooter(
                                    statusText = USER_PENDING_IMAGE_SEND_STATUS_TEXT,
                                    actionText = null,
                                    alignEnd = true,
                                    enabled = false,
                                    onActionClick = {}
                                )
                            }
                        }
                    }
                }
            }
        }
        val renderChatList: @Composable (Int, Int) -> Unit = { conversationBottomPaddingPx, listBottomPaddingPx ->
            val effectiveBottomPaddingPx =
                lockedConversationBottomPaddingPx
                    .takeIf { sendStartBottomPaddingLockActive && it >= 0 }
                    ?.let { lockedPaddingPx -> lockedPaddingPx }
                    ?: listBottomPaddingPx
            val forwardListBottomPaddingPx =
                (effectiveBottomPaddingPx - chatMessageItemVerticalPaddingPx).coerceAtLeast(0)
            SideEffect {
                latestConversationBottomPaddingPx = conversationBottomPaddingPx
                val collapsedStable =
                    !sendStartBottomPaddingLockActive &&
                        !isComposerSettling &&
                        !isStreaming &&
                        !hasStreamingItem &&
                        pendingStreamingFinalizeMessageId.isNullOrBlank() &&
                        !inputFieldFocused &&
                        !imeVisible &&
                        selectedComposerImages.isEmpty() &&
                        !attachmentMenuVisible &&
                        input.value.text.isEmpty()
                if (collapsedStable && observedCollapsedBottomReservePx <= 0) {
                    val prewarmedCollapsedBottomReservePx =
                        (conversationBottomPaddingPx - streamVisibleBottomGapPx).coerceAtLeast(0)
                    if (prewarmedCollapsedBottomReservePx > 0) {
                        observedCollapsedBottomReservePx = prewarmedCollapsedBottomReservePx
                    }
                }
            }
            CompositionLocalProvider(
                LocalBringIntoViewSpec provides StaticMessageSelectionBringIntoViewSpec
            ) {
                ChatRecyclerViewHost(
                    listState = chatListState,
                    items = chatListItems,
                    itemKey = { it.stableKey },
                    itemContentType = { item ->
                        when (item) {
                            is ChatTimelineItem.HistoryNotice -> "history_notice"
                            is ChatTimelineItem.Message -> item.message.role
                            is ChatTimelineItem.TodayAgriCard -> "today_agri_card"
                        }
                    },
                    topPaddingPx = chatListTopPaddingPx,
                    bottomPaddingPx = forwardListBottomPaddingPx,
                    verticalArrangement = if (shouldUseTopArrangementForConversation()) {
                        Arrangement.Top
                    } else {
                        Arrangement.Bottom
                    },
                    modifier = Modifier
                        .then(
                            if (hasActiveMessageSelection) {
                                selectionDismissTapModifier(activeMessageSelectionMessageId ?: "selection")
                            } else {
                                Modifier
                            }
                        )
                        .then(
                            if (shouldTrackChatListBrowsingFromPointer()) {
                                Modifier.pointerInput(
                                    isStreaming,
                                    hasStreamingItem,
                                    imageSendInProgress
                                ) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(
                                            requireUnconsumed = false,
                                            pass = PointerEventPass.Initial
                                        )
                                        var markedBrowsing = false
                                        while (!markedBrowsing) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            val change = event.changes.firstOrNull { it.id == down.id }
                                                ?: event.changes.firstOrNull()
                                                ?: break
                                            if (!change.pressed) break
                                            val movedPx = (change.position - down.position).getDistance()
                                            if (movedPx > viewConfiguration.touchSlop) {
                                                markChatListUserBrowsingFromPointer()
                                                markedBrowsing = true
                                            }
                                        }
                                    }
                                }
                            } else {
                                Modifier
                            }
                        )
                        .fillMaxSize()
                        .then(
                            if (shouldRevealMessageList) {
                                Modifier
                            } else {
                                Modifier.graphicsLayer(alpha = 0f)
                            }
                        )
                ) { msg ->
                    when (msg) {
                        is ChatTimelineItem.HistoryNotice ->
                            ChatHistoryWindowNotice(
                                hiddenRoundCount = msg.hiddenRoundCount,
                                horizontalPadding = listHorizontalPadding,
                                maxCardWidth = chromeMaxWidth
                            )
                        is ChatTimelineItem.Message -> renderConversationMessage(msg.message)
                        is ChatTimelineItem.TodayAgriCard -> {
                            val todayAgriSelectionId = "today_agri_${todayAgriCardDayKey(msg.card)}"
                            DisposableEffect(todayAgriSelectionId) {
                                onDispose {
                                    messageSelectionBoundsById.remove(todayAgriSelectionId)
                                    messageContentBoundsById.remove(todayAgriSelectionId)
                                    if (pendingMessageSelectionToolbarState?.messageId == todayAgriSelectionId) {
                                        pendingMessageSelectionToolbarState = null
                                    }
                                    if (messageSelectionToolbarState?.messageId == todayAgriSelectionId) {
                                        messageSelectionToolbarState = null
                                    }
                                }
                            }
                            val todayAgriFullCopyText = remember(msg.card) {
                                msg.card.toTodayAgriPlainText()
                            }
                            val todayAgriTextToolbar = remember(todayAgriSelectionId, todayAgriFullCopyText) {
                                buildMessageSelectionTextToolbar(
                                    messageId = todayAgriSelectionId,
                                    role = ChatRole.ASSISTANT,
                                    fullCopyText = todayAgriFullCopyText
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (
                                            hasActiveMessageSelection &&
                                            activeMessageSelectionMessageId != todayAgriSelectionId
                                        ) {
                                            selectionDismissTapModifier(
                                                activeMessageSelectionMessageId ?: "selection",
                                                todayAgriSelectionId
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .onGloballyPositioned { coordinates ->
                                        val bounds = coordinates.boundsInWindow()
                                        updateMessageSelectionBoundsIfNeeded(todayAgriSelectionId, bounds)
                                        applyPendingMessageSelectionToolbarIfReady(
                                            messageId = todayAgriSelectionId,
                                            bounds = bounds
                                        )
                                    }
                            ) {
                                key(messageSelectionResetEpoch) {
                                    TodayAgriNewsText(
                                        card = msg.card,
                                        horizontalPadding = listHorizontalPadding,
                                        maxContentWidth = chromeMaxWidth,
                                        textSelectionColors = messageSelectionColors,
                                        textToolbar = todayAgriTextToolbar
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        val renderWelcomePlaceholder: @Composable (Int) -> Unit = { bottomPaddingPx ->
            if (showWelcomePlaceholder) {
                val welcomeBottomInset =
                    with(density) { bottomPaddingPx.toDp() } + 24.dp
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = listHorizontalPadding,
                            end = listHorizontalPadding,
                            top = topBarReservedHeight,
                            bottom = welcomeBottomInset
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 360.dp)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = WELCOME_EMPTY_STATE_TEXT,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFF141414),
                            lineHeight = 31.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        val renderComposerBar: @Composable (Modifier) -> Unit = { hostModifier ->
            ChatComposerBottomBar(
                inputValue = input.value,
                inputSelectionColors = inputSelectionColors,
                inputTextToolbar = inputTextToolbar,
                inputFieldFocused = inputFieldFocused,
                suppressInputCursor = suppressInputCursor,
                composerSettlingMinHeightPx = composerSettlingMinHeightPx,
                composerSettlingChromeHeightPx = composerSettlingChromeHeightPx,
                startupInputContentHeightEstimatePx = startupInputContentHeightEstimatePx,
                inputChromeRowHeightPx = inputChromeRowHeightPx,
                imeVisible = imeVisible,
                isStreamingOrSettling = isStreaming || sendUiSettling || imageSendInProgress,
                inputMaxChars = INPUT_MAX_CHARS,
                chromeMaxWidth = chromeMaxWidth,
                inputChromeHorizontalPadding = inputChromeHorizontalPadding,
                inputChromeBottomPadding = inputChromeBottomPadding,
                addButtonSize = addButtonSize,
                addIconSize = addIconSize,
                sendButtonSize = sendButtonSize,
                inputBarHeight = inputBarHeight,
                inputBarMaxHeight = inputBarMaxHeight,
                inputChromeSurface = inputChromeSurface,
                inputChromeBorder = inputChromeBorder,
                inputFieldSurface = inputFieldSurface,
                inputFieldBorder = inputFieldBorder,
                overlayHintText = null,
                quotaExhausted = isQuotaExhaustedToday(),
                selectedImages = selectedComposerImages,
                hostModifier = hostModifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .zIndex(55f)
                    .onSizeChanged { size ->
                        if (currentComposerHostHeightPx != size.height) {
                            currentComposerHostHeightPx = size.height
                        }
                        val canRecordCollapsedComposerHeight =
                            !sendStartBottomPaddingLockActive &&
                                !isComposerSettling &&
                                !inputFieldFocused &&
                                !imeVisible &&
                                selectedComposerImages.isEmpty() &&
                                !attachmentMenuVisible &&
                                input.value.text.isEmpty()
                        if (
                            canRecordCollapsedComposerHeight &&
                            measuredComposerHostHeightPx != size.height
                        ) {
                            measuredComposerHostHeightPx = size.height
                        }
                    },
                onChromeMeasured = { height ->
                    val canRecordChromeHeight =
                        !inputFieldFocused &&
                            !imeVisible &&
                            selectedComposerImages.isEmpty() &&
                            !attachmentMenuVisible &&
                            input.value.text.isEmpty()
                    if (canRecordChromeHeight && inputChromeRowHeightPx != height) {
                        inputChromeRowHeightPx = height
                    }
                    if (canRecordChromeHeight && height > 0 && !inputChromeMeasured) {
                        inputChromeMeasured = true
                    }
                },
                onInputBoundsChanged = { bounds ->
                    if (inputFieldBoundsInWindow != bounds) {
                        inputFieldBoundsInWindow = bounds
                    }
                    val nextComposerTopInViewportPx =
                        (bounds.top - messageViewportTopPx).roundToInt()
                    if (composerTopInViewportPx != nextComposerTopInViewportPx) {
                        composerTopInViewportPx = nextComposerTopInViewportPx
                    }
                    if (!composerMeasured) {
                        composerMeasured = true
                    }
                    applyPendingInputSelectionToolbarIfReady(bounds)
                },
                onInputFocused = { focused ->
                    inputFieldFocused = focused
                    if (focused) {
                        suppressInputCursor = false
                        inputContentHeightPx = inputContentHeightPx.coerceAtLeast(
                            startupInputContentHeightEstimatePx
                        )
                    }
                },
                onInputContentHeightChanged = { height ->
                    if (inputContentHeightPx != height) {
                        inputContentHeightPx = height
                    }
                },
                onInputValueChange = {
                    if (sendUiSettling || imageSendInProgress) {
                        return@ChatComposerBottomBar
                    }
                    suppressInputCursor = false
                    input.value = it
                },
                onInputLimitExceeded = {
                    inputLimitHintTick++
                },
                onQuotaExceeded = {
                    showQuotaExhaustedHint()
                },
                onAddClick = {
                    if (sendUiSettling || imageSendInProgress) {
                        return@ChatComposerBottomBar
                    }
                    performButtonHaptic()
                    val shouldShowAttachmentMenu = !attachmentMenuVisible
                    attachmentMenuVisible = shouldShowAttachmentMenu
                    if (shouldShowAttachmentMenu) {
                        focusManager.clearFocus(force = true)
                    }
                    clearInputSelectionToolbar()
                },
                onRemoveImage = { image ->
                    if (sendUiSettling || imageSendInProgress) {
                        return@ChatComposerBottomBar
                    }
                    performButtonHaptic()
                    if (selectedComposerImages.remove(image)) {
                        snackbarScope.launch {
                            withContext(Dispatchers.IO) {
                                context.deleteComposerImageAttachment(image)
                            }
                        }
                    }
                },
                onSendClick = {
                    performButtonHaptic()
                    sendMessage()
                }
            )
        }
        val measuredComposerHeightPx = measuredComposerHostHeightPx
        val canUseMeasuredCollapsedComposerReserve =
            !sendStartBottomPaddingLockActive &&
                !isComposerSettling &&
                !inputFieldFocused &&
                !imeVisible &&
                selectedComposerImages.isEmpty() &&
                !attachmentMenuVisible &&
                input.value.text.isEmpty() &&
                measuredComposerHeightPx > 0
        val collapsedConversationReservePx =
            if (canUseMeasuredCollapsedComposerReserve) {
                measuredComposerHeightPx
            } else {
                observedCollapsedBottomReservePx
                    .takeIf { it > 0 }
                    ?: startupBottomBarHeightEstimatePx
            }
        val stableBottomReservePx =
            resolveBottomContentReservedHeightPx(
                effectiveBottomBarHeightPx = collapsedConversationReservePx,
                extraReservedHeightPx = streamingExtraReservedHeightPx
            ).coerceAtLeast(0)
        val stableStreamingBottomPaddingPx =
            (stableBottomReservePx + streamVisibleBottomGapPx).coerceAtLeast(0)
        val currentExternalBottomInsetPx =
            (measuredComposerHeightPx - inputChromeRowHeightPx).coerceAtLeast(0)
        val realtimeExternalLiftPx =
            (currentExternalBottomInsetPx - safeBottomInsetPx).coerceAtLeast(0)
        val realtimeStaticBottomPaddingPx =
            (collapsedConversationReservePx + realtimeExternalLiftPx).coerceAtLeast(0)
        val realtimeStreamingBottomPaddingPx =
            (realtimeStaticBottomPaddingPx + streamVisibleBottomGapPx).coerceAtLeast(0)
        val conversationBottomPaddingPx =
            if (shouldUseRealtimeComposerGeometry && measuredComposerHeightPx > 0) {
                realtimeStreamingBottomPaddingPx
            } else {
                stableStreamingBottomPaddingPx
            }
        val listBottomPaddingPx = conversationBottomPaddingPx
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = pageSurface,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(pageSurface)
                    .pointerInput(imeVisible) {
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial
                            )
                            val up = waitForUpIgnoringConsumption(pass = PointerEventPass.Initial)
                            if (up == null) return@awaitEachGesture
                            val gestureDistancePx = (up.position - down.position).getDistance()
                            val isTapGesture = gestureDistancePx <= viewConfiguration.touchSlop
                            val downInWindow = Offset(
                                x = down.position.x + messageViewportLeftPx,
                                y = down.position.y + messageViewportTopPx
                            )
                            val tapInWindow = Offset(
                                x = up.position.x + messageViewportLeftPx,
                                y = up.position.y + messageViewportTopPx
                            )
                            if (inputSelectionMenuBoundsInRoot?.containsPoint(tapInWindow) == true) {
                                return@awaitEachGesture
                            }
                            val startedInsideInputField =
                                inputFieldBoundsInWindow?.containsPoint(downInWindow) == true
                            val tappedInsideInputField =
                                inputFieldBoundsInWindow?.containsPoint(tapInWindow) == true
                            if (isTapGesture && inputSelectionToolbarState != null && !tappedInsideInputField) {
                                clearInputSelectionToolbar()
                            }
                            if (imeVisible && isTapGesture && !startedInsideInputField && !tappedInsideInputField) {
                                focusManager.clearFocus(force = true)
                            }
                        }
                    }
                    .onSizeChanged {
                        if (messageViewportWidthPx != it.width) {
                            messageViewportWidthPx = it.width
                        }
                        if (messageViewportHeightPx != it.height) {
                            messageViewportHeightPx = it.height
                        }
                        if (it.width > 0 && it.height > 0) {
                            messageViewportMeasured = true
                        }
                    }
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInWindow()
                        if (messageViewportLeftPx != bounds.left) {
                            messageViewportLeftPx = bounds.left
                        }
                        if (messageViewportTopPx != bounds.top) {
                            messageViewportTopPx = bounds.top
                        }
                    }
            ) {
                renderChatList(conversationBottomPaddingPx, listBottomPaddingPx)
                renderWelcomePlaceholder(listBottomPaddingPx)
                renderComposerBar(Modifier.align(Alignment.BottomCenter))
                GlobalStatusHint(
                    visible = globalStatusHintVisible,
                    text = globalStatusHintText.orEmpty(),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                        .zIndex(56f)
                )

                ComposerAttachmentBottomSheet(
                    visible = attachmentMenuVisible,
                    limitReached = selectedComposerImages.size >= COMPOSER_MAX_IMAGE_COUNT,
                    limitHintText = COMPOSER_IMAGE_COUNT_HINT,
                    modifier = Modifier.fillMaxSize(),
                    onDismiss = {
                        attachmentMenuVisible = false
                    },
                    onCameraClick = cameraClick@{
                        if (imageSendInProgress) {
                            return@cameraClick
                        }
                        performButtonHaptic()
                        launchComposerCamera()
                    },
                    onPhotoClick = photoClick@{
                        if (imageSendInProgress) {
                            return@photoClick
                        }
                        performButtonHaptic()
                        launchComposerPhotoPicker()
                    }
                )

                MembershipCenterBottomSheet(
                    visible = membershipCenterVisible,
                    entitlement = membershipEntitlement,
                    loadState = membershipLoadState,
                    purchaseSuccessVisible = membershipPurchaseSuccessVisible,
                    userId = chatScopeId,
                    modifier = Modifier.fillMaxSize(),
                    onDismiss = {
                        membershipPurchaseSuccessVisible = false
                        membershipCenterVisible = false
                    },
                    onPaymentUnavailable = {
                        performButtonHaptic()
                        SessionApi.reportClientLog(
                            level = "info",
                            event = "payment.unavailable_clicked",
                            message = "Membership payment unavailable clicked",
                            attrs = mapOf("source" to "chat_membership_sheet")
                        )
                    },
                    onRetryLoad = {
                        performButtonHaptic()
                        membershipRefreshNonce += 1
                    },
                    onPurchaseSuccessConfirm = {
                        performButtonHaptic()
                        membershipPurchaseSuccessVisible = false
                        membershipRefreshNonce += 1
                    }
                )

                HamburgerMenuSheet(
                    visible = hamburgerMenuVisible,
                    userId = chatScopeId,
                    membershipEntitlement = membershipEntitlement,
                    membershipLoadState = membershipLoadState,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(78f),
                    onDismiss = {
                        hamburgerMenuVisible = false
                    },
                    onRequestMembershipRefresh = {
                        attachmentMenuVisible = false
                        uiCopyPreviewVisible = false
                        clearInputSelectionToolbar()
                        clearMessageSelection()
                        focusManager.clearFocus(force = true)
                        membershipRefreshNonce += 1
                    },
                    onMembershipPaymentUnavailable = {
                        // The settings page already performs the tap haptic; keep this callback
                        // side-effect free so the inline notice remains local to that page.
                    },
                    onClearChatHistory = {
                        applyChatHistoryCleared()
                    },
                    onPlaceholderClick = {
                        performButtonHaptic()
                    }
                )

            if (navigationBottomInset > 0.dp) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                            .height(navigationBottomInset)
                        .background(pageSurface)
                )
            }

            if (effectiveJumpButtonVisible && shouldRevealMessageList) {
                Surface(
                    onClick = { jumpToBottom() },
                    shape = CircleShape,
                    color = chromeSurface,
                    border = BorderStroke(0.48.dp, chromeBorder),
                    shadowElevation = 0.7.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = jumpButtonBottomPadding)
                        .size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        LongArrowIcon(
                            tint = Color(0xFF111111),
                            directionUp = false,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(topBarReservedHeight)
                    .onGloballyPositioned { coordinates ->
                        val nextTopChromeMaskBottomPx =
                            coordinates.boundsInWindow().bottom.roundToInt()
                        if (topChromeMaskBottomPx != nextTopChromeMaskBottomPx) {
                            topChromeMaskBottomPx = nextTopChromeMaskBottomPx
                        }
                    }
                    .background(pageSurface)
                    .zIndex(45f)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(top = topInset + 4.dp, bottom = 2.dp)
                    .zIndex(46f)
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(max = chromeMaxWidth)
                        .fillMaxWidth()
                        .padding(horizontal = chromeHorizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            performButtonHaptic()
                            attachmentMenuVisible = false
                            uiCopyPreviewVisible = false
                            membershipPurchaseSuccessVisible = false
                            membershipCenterVisible = false
                            clearInputSelectionToolbar()
                            clearMessageSelection()
                            focusManager.clearFocus(force = true)
                            hamburgerMenuVisible = true
                        },
                        modifier = Modifier
                            .size(topButtonTouchSize)
                            .semantics { contentDescription = "设置" }
                    ) {
                        MenuBarsIcon(
                            tint = Color(0xFF1E1E1E),
                            modifier = Modifier.size(topMenuIconSize)
                        )
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        val titleModifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .then(
                                if (BuildConfig.DEBUG) {
                                    Modifier.clickable { uiCopyPreviewVisible = true }
                                } else {
                                    Modifier
                                }
                            )
                        Text(
                            text = APP_TITLE_TEXT,
                            modifier = titleModifier,
                            color = Color(0xFF111111),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = topTitleFontSize,
                                lineHeight = topTitleLineHeight
                            ),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                    IconButton(
                        onClick = {
                            performButtonHaptic()
                            attachmentMenuVisible = false
                            uiCopyPreviewVisible = false
                            clearInputSelectionToolbar()
                            clearMessageSelection()
                            focusManager.clearFocus(force = true)
                            membershipCenterVisible = true
                        },
                        modifier = Modifier
                            .size(topButtonTouchSize)
                            .semantics { contentDescription = "会员中心" }
                    ) {
                        MembershipCenterLeafIcon(
                            size = membershipIconSize
                        )
                    }
                }
            }

            if (BuildConfig.DEBUG && uiCopyPreviewVisible) {
                UiCopyPreviewOverlay(
                    onDismiss = { uiCopyPreviewVisible = false }
                )
            }

            inputSelectionToolbarState?.let { state ->
                val inputBounds = inputFieldBoundsInWindow
                if (inputBounds != null) {
                    InputSelectionMenuPopup(
                        state = state,
                        inputFieldBoundsInWindow = inputBounds,
                        viewportLeftPx = chatRootLeftPx,
                        viewportTopPx = chatRootTopPx,
                        topChromeMaskBottomPx = topChromeMaskBottomPx,
                        onMenuBoundsChanged = { bounds -> inputSelectionMenuBoundsInRoot = bounds },
                        onDismiss = { clearInputSelectionToolbar() }
                    )
                }
            }

            activeMessageSelectionState?.let { state ->
                MessageActionMenuPopup(
                    state = state,
                    viewportLeftPx = chatRootLeftPx,
                    viewportTopPx = chatRootTopPx,
                    contentViewportLeftPx = messageViewportLeftPx,
                    contentViewportTopPx = messageViewportTopPx,
                    contentViewportWidthPx = messageViewportWidthPx,
                    contentViewportHeightPx = messageViewportHeightPx,
                    topChromeMaskBottomPx = topChromeMaskBottomPx,
                    composerTopInViewportPx = composerTopInViewportPx,
                    onCopy = {
                        performButtonHaptic()
                        state.onCopyRequested?.invoke()
                        clearMessageSelection()
                    },
                    onCopyFull = {
                        performButtonHaptic()
                        state.onCopyFullRequested?.invoke()
                        clearMessageSelection()
                    }
                )
            }

            if (hasActiveMessageSelection) {
                val selectionMaskWidth = with(density) {
                    maxOf(view.width, messageViewportWidthPx).toDp()
                }
                if (topInset > 0.dp) {
                    Popup(
                        alignment = Alignment.TopStart,
                        properties = PopupProperties(focusable = false, clippingEnabled = false)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(selectionMaskWidth)
                                .height(topInset)
                                .background(pageSurface)
                        )
                    }
                }
                val bottomMaskHeight = with(density) { safeBottomInsetPx.toDp() }
                if (bottomMaskHeight > 0.dp) {
                    Popup(
                        alignment = Alignment.BottomStart,
                        properties = PopupProperties(focusable = false, clippingEnabled = false)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(selectionMaskWidth)
                                .height(bottomMaskHeight)
                                .background(pageSurface)
                        )
                    }
                }
            }

        }
    }
}
}

@Composable
private fun GlobalStatusHint(
    visible: Boolean,
    text: String,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && text.isNotBlank(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xEE111111),
            contentColor = Color.White,
            border = BorderStroke(0.8.dp, Color.Black),
            shadowElevation = 3.dp,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
            )
        }
    }
}

@Composable
private fun MessageActionMenuButton(
    label: String,
    modifier: Modifier = Modifier,
    minWidth: Dp = 0.dp,
    horizontalPadding: Dp = 12.dp,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .widthIn(min = minWidth)
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Transparent)
            .semantics {
                contentDescription = label
                role = Role.Button
                onClick(label = label) {
                    onClick()
                    true
                }
            }
            .pointerInput(label) {
                detectTapGestures(onTap = { onClick() })
            }
            .padding(horizontal = horizontalPadding, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MessageActionMenuCardContent(
    modifier: Modifier = Modifier,
    onCopy: () -> Unit,
    onCopyFull: () -> Unit
) {
    Surface(
        color = Color(0xFF111111),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 10.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MessageActionMenuButton(
                label = "复制",
                minWidth = 78.dp,
                horizontalPadding = 17.dp,
                onClick = onCopy
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(16.dp)
                    .background(Color.White.copy(alpha = 0.16f))
            )
            MessageActionMenuButton(
                label = "全文复制",
                minWidth = 0.dp,
                horizontalPadding = 12.dp,
                onClick = onCopyFull
            )
        }
    }
}

@Composable
private fun MessageActionMenuPopup(
    state: MessageSelectionToolbarState,
    viewportLeftPx: Float,
    viewportTopPx: Float,
    contentViewportLeftPx: Float,
    contentViewportTopPx: Float,
    contentViewportWidthPx: Int,
    contentViewportHeightPx: Int,
    topChromeMaskBottomPx: Int,
    composerTopInViewportPx: Int,
    onCopy: () -> Unit,
    onCopyFull: () -> Unit
) {
    val density = LocalDensity.current
    val verticalSpacingPx = with(density) { MESSAGE_ACTION_MENU_VERTICAL_SPACING.roundToPx() }
    val marginPx = with(density) { MESSAGE_ACTION_MENU_MARGIN.roundToPx() }
    val switchThresholdPx = with(density) { MESSAGE_ACTION_MENU_SWITCH_THRESHOLD.roundToPx() }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    var lockedSide by remember(state.messageId) { mutableStateOf<MessageActionMenuSide?>(null) }
    val anchorLocalX = (state.anchorX - viewportLeftPx).roundToInt()
    val anchorLocalY = (state.anchorY - viewportTopPx).roundToInt()
    val selectionBottomLocalY = (state.selectionBottomY - viewportTopPx).roundToInt()
    val contentLocalLeft = (contentViewportLeftPx - viewportLeftPx).roundToInt()
    val contentLocalTop = (contentViewportTopPx - viewportTopPx).roundToInt()
    val resolvedWidth = if (cardSize.width > 0) cardSize.width else with(density) { 148.dp.roundToPx() }
    val resolvedHeight =
        if (cardSize.height > 0) cardSize.height else with(density) { MESSAGE_ACTION_MENU_ESTIMATED_HEIGHT.roundToPx() }
    val minX = (contentLocalLeft + marginPx).coerceAtLeast(marginPx)
    val maxX = (
        contentLocalLeft +
            contentViewportWidthPx -
            resolvedWidth -
            marginPx
        ).coerceAtLeast(minX)
    val preferredX = (anchorLocalX - resolvedWidth / 2).coerceIn(minX, maxX)
    val topMaskBottomLocal =
        if (topChromeMaskBottomPx > 0) {
            (topChromeMaskBottomPx - viewportTopPx.roundToInt()).coerceAtLeast(0)
        } else {
            Int.MIN_VALUE
        }
    val bottomMaskTopLocal =
        if (composerTopInViewportPx > 0) {
            composerTopInViewportPx
        } else {
            Int.MAX_VALUE
        }
    val contentTopLimit = (contentLocalTop + marginPx).coerceAtLeast(marginPx)
    val contentBottomLimit = contentLocalTop + contentViewportHeightPx - marginPx
    val protectedTopLimit = maxOf(contentTopLimit, topMaskBottomLocal + marginPx)
    val protectedBottomLimit = minOf(contentBottomLimit, bottomMaskTopLocal - marginPx)
    val topHandleLocalY = minOf(anchorLocalY, selectionBottomLocalY)
    val bottomHandleLocalY = maxOf(anchorLocalY, selectionBottomLocalY)
    val preferredTop = topHandleLocalY - resolvedHeight - verticalSpacingPx
    val belowCandidate = bottomHandleLocalY + verticalSpacingPx
    val aboveMinTop = protectedTopLimit
    val aboveMaxTop = minOf(
        topHandleLocalY - verticalSpacingPx - resolvedHeight,
        protectedBottomLimit - resolvedHeight
    )
    val belowMinTop = maxOf(bottomHandleLocalY + verticalSpacingPx, protectedTopLimit)
    val belowMaxTop = protectedBottomLimit - resolvedHeight
    val topOverflow = (protectedTopLimit - preferredTop).coerceAtLeast(0)
    val bottomOverflow = (belowCandidate + resolvedHeight - protectedBottomLimit).coerceAtLeast(0)
    val canPlaceAbove = aboveMaxTop >= aboveMinTop
    val canPlaceBelow = belowMaxTop >= belowMinTop
    val canPlaceWithoutOverlap = canPlaceAbove || canPlaceBelow
    val resolvedSide =
        when (lockedSide) {
            MessageActionMenuSide.Above -> {
                if ((!canPlaceAbove || topOverflow > switchThresholdPx) && canPlaceBelow) {
                    MessageActionMenuSide.Below
                } else {
                    MessageActionMenuSide.Above
                }
            }

            MessageActionMenuSide.Below -> {
                if ((!canPlaceBelow || bottomOverflow > switchThresholdPx) && canPlaceAbove) {
                    MessageActionMenuSide.Above
                } else {
                    MessageActionMenuSide.Below
                }
            }

            null -> {
                when {
                    canPlaceAbove -> MessageActionMenuSide.Above
                    canPlaceBelow -> MessageActionMenuSide.Below
                    topOverflow <= bottomOverflow -> MessageActionMenuSide.Above
                    else -> MessageActionMenuSide.Below
                }
            }
        }
    LaunchedEffect(
        state.messageId,
        resolvedSide,
        state.anchorY,
        state.selectionBottomY,
        topChromeMaskBottomPx,
        composerTopInViewportPx
    ) {
        if (lockedSide != resolvedSide) {
            lockedSide = resolvedSide
        }
    }
    val rawPreferredY =
        if (resolvedSide == MessageActionMenuSide.Above) {
            preferredTop
        } else {
            belowCandidate
        }
    val fullyVisibleMinTop = protectedTopLimit
    val fullyVisibleMaxTop = (protectedBottomLimit - resolvedHeight).coerceAtLeast(fullyVisibleMinTop)
    val centeredFallbackTop =
        ((protectedTopLimit + protectedBottomLimit - resolvedHeight) / 2)
            .coerceIn(fullyVisibleMinTop, fullyVisibleMaxTop)
    val minAllowedTop =
        when {
            !canPlaceWithoutOverlap -> centeredFallbackTop
            resolvedSide == MessageActionMenuSide.Above && canPlaceAbove -> aboveMinTop
            resolvedSide == MessageActionMenuSide.Below && canPlaceBelow -> belowMinTop
            else -> fullyVisibleMinTop
        }
    val maxAllowedTop =
        when {
            !canPlaceWithoutOverlap -> centeredFallbackTop
            resolvedSide == MessageActionMenuSide.Above && canPlaceAbove -> aboveMaxTop.coerceAtLeast(minAllowedTop)
            resolvedSide == MessageActionMenuSide.Below && canPlaceBelow -> belowMaxTop.coerceAtLeast(minAllowedTop)
            else -> fullyVisibleMaxTop
        }
    val preferredY = rawPreferredY.coerceIn(minAllowedTop, maxAllowedTop)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { clip = true }
            .zIndex(40f)
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(preferredX, preferredY) }
                .onGloballyPositioned { coordinates ->
                    cardSize = coordinates.size
                }
                .pointerInput(state.anchorX, state.anchorY, state.selectionBottomY) {
                    detectTapGestures(onTap = {})
                }
        ) {
            MessageActionMenuCardContent(onCopy = onCopy, onCopyFull = onCopyFull)
        }
    }
}

@Composable
private fun UiCopyPreviewOverlay(
    onDismiss: () -> Unit
) {
    val copyGroups = remember {
        listOf(
            UiCopyPreviewGroup(
                title = "顶部与空态",
                items = listOf(
                    UiCopyPreviewItem(APP_TITLE_TEXT, "顶部标题", UiCopyPreviewKind.AppTitle),
                    UiCopyPreviewItem(
                        WELCOME_EMPTY_STATE_TEXT.replace("\n", " / "),
                        "空会话欢迎态",
                        UiCopyPreviewKind.Welcome
                    )
                )
            ),
            UiCopyPreviewGroup(
                title = "登录与协议",
                items = listOf(
                    UiCopyPreviewItem("首次登录页", "无独立同意页，登录页对号承接隐私同意", UiCopyPreviewKind.LoginInitial),
                    UiCopyPreviewItem("未勾选拦截", "不请求平台、不进入登录校验", UiCopyPreviewKind.LoginAgreementBlocked),
                    UiCopyPreviewItem("短信登录", "手机号 + 6位验证码 + 协议勾选", UiCopyPreviewKind.LoginSmsFallback),
                    UiCopyPreviewItem("换手机号后发送", "倒计时只锁刚发成功的手机号，换号可立即发送", UiCopyPreviewKind.LoginPhoneChanged),
                    UiCopyPreviewItem("登录协议弹窗", "服务协议 / 隐私政策在登录页内打开", UiCopyPreviewKind.LoginLegalDialog)
                )
            ),
            UiCopyPreviewGroup(
                title = "清数据回归",
                items = listOf(
                    UiCopyPreviewItem("清数据首次启动", "空本地状态下的欢迎态和输入框", UiCopyPreviewKind.CleanStateFirstLaunch),
                    UiCopyPreviewItem("清数据首次发送", "用户短文本 + waiting 小球", UiCopyPreviewKind.CleanStateFirstSend),
                    UiCopyPreviewItem("清数据检查点", "不恢复旧聊天 / 旧高度 / 旧滚动位置", UiCopyPreviewKind.CleanStateChecklist)
                )
            ),
            UiCopyPreviewGroup(
                title = "适配与回退",
                items = listOf(
                    UiCopyPreviewItem("远端快照失败兜底", "只保留 pending / 失败态，不回灌普通本地历史", UiCopyPreviewKind.RemoteSnapshotFallback),
                    UiCopyPreviewItem("删除历史 hydrate 拦截", "旧请求晚回来不覆盖 clean-state", UiCopyPreviewKind.ClearHistoryHydrateGuard),
                    UiCopyPreviewItem("图片预览安全区", "页码和关闭按钮避开横向 cutout", UiCopyPreviewKind.ImagePreviewTopChrome),
                    UiCopyPreviewItem("会员面板短屏安全区", "顶部 safe drawing 限制最大高度", UiCopyPreviewKind.MembershipSheetSafeArea)
                )
            ),
            UiCopyPreviewGroup(
                title = "输入区",
                items = listOf(
                    UiCopyPreviewItem(
                        COMPOSER_DEFAULT_PLACEHOLDER_TEXT,
                        "输入框无图 placeholder",
                        UiCopyPreviewKind.ComposerPlaceholder
                    ),
                    UiCopyPreviewItem(
                        COMPOSER_IMAGE_PLACEHOLDER_TEXT,
                        "输入框已有图片 placeholder",
                        UiCopyPreviewKind.ComposerImagePlaceholder
                    )
                )
            ),
            UiCopyPreviewGroup(
                title = "会员中心",
                items = listOf(
                    UiCopyPreviewItem("会员中心（账号ID）", "标题后展示账号短 ID", UiCopyPreviewKind.MembershipHeader),
                    UiCopyPreviewItem("会员首次同步", "无旧权益时才显示读取中", UiCopyPreviewKind.MembershipLoadingSummary),
                    UiCopyPreviewItem("Free 基础额度", "标题同色加粗，信息同色普通", UiCopyPreviewKind.MembershipFreeSummary),
                    UiCopyPreviewItem(
                        "Free 额外次数",
                        "到期后仍可显示升级补偿次数 / 加油包",
                        UiCopyPreviewKind.MembershipFreeExtraSummary
                    ),
                    UiCopyPreviewItem(
                        "礼品卡开通",
                        "显示礼品卡渠道和当前档位每日次数",
                        UiCopyPreviewKind.MembershipPlusExtraSummary
                    ),
                    UiCopyPreviewItem("Pro 到期日", "标题同色加粗，信息同色普通", UiCopyPreviewKind.MembershipProSummary),
                    UiCopyPreviewItem("会员同步失败", "保留旧权益并给重试入口", UiCopyPreviewKind.MembershipFailedSummary),
                    UiCopyPreviewItem("套餐区：待同步", "无权益数据时才置灰", UiCopyPreviewKind.MembershipPlanUnknown),
                    UiCopyPreviewItem("套餐区：Free", "Plus / Pro 都显示“暂未开放”", UiCopyPreviewKind.MembershipPlanFree),
                    UiCopyPreviewItem("套餐区：Plus", "Plus 显示当前套餐，Pro 显示支付升级暂未开放", UiCopyPreviewKind.MembershipPlanPlus),
                    UiCopyPreviewItem("套餐区：Pro", "Plus 显示当前为 Pro", UiCopyPreviewKind.MembershipPlanPro),
                    UiCopyPreviewItem("套餐区：窄屏挤压", "280dp 下标题、胶囊和价格不互撞", UiCopyPreviewKind.MembershipPlanNarrow),
                    UiCopyPreviewItem("加油包：Free不可订购", "Plus / Pro 可订购置灰状态", UiCopyPreviewKind.MembershipTopupUnavailable),
                    UiCopyPreviewItem("加油包：Free剩余", "按钮显示“剩余次数可用”", UiCopyPreviewKind.MembershipTopupFreeActive),
                    UiCopyPreviewItem("加油包：付费档位未开放", "展示“暂未开放”而不是可直接购买", UiCopyPreviewKind.MembershipTopupBuyable),
                    UiCopyPreviewItem("加油包：未用完", "续购暂未开放状态", UiCopyPreviewKind.MembershipTopupActive),
                    UiCopyPreviewItem("加油包：窄屏挤压", "280dp 下名称和价格不互撞", UiCopyPreviewKind.MembershipTopupNarrow),
                    UiCopyPreviewItem("支付入口提示", "支付接入前后的提示条样式", UiCopyPreviewKind.MembershipPaymentNotice),
                    UiCopyPreviewItem("权益生效提示", "支付未开放，仅预览后续权益生效提示", UiCopyPreviewKind.MembershipPurchaseSuccess),
                    UiCopyPreviewItem("规则说明", "Plus升级Pro / 扣次顺序 / 补偿与加油包", UiCopyPreviewKind.MembershipRules)
                )
            ),
            UiCopyPreviewGroup(
                title = "汉堡菜单",
                items = listOf(
                    UiCopyPreviewItem("设置入口", "白卡片设置页，会员、账号、帮助和协议与隐私入口", UiCopyPreviewKind.HamburgerMenu),
                    UiCopyPreviewItem("设置外层", "返回键、标题和设置首页整体位置", UiCopyPreviewKind.HamburgerMenuShell),
                    UiCopyPreviewItem("设置内会员中心", "右进左出整页壳，账号短 ID 和礼品卡渠道跟随最新口径", UiCopyPreviewKind.HamburgerMembershipPage),
                    UiCopyPreviewItem("账号管理", "手机号 / 清理临时缓存 / 历史 / 退出 / 申请注销", UiCopyPreviewKind.HamburgerAccountPage),
                    UiCopyPreviewItem("退出登录确认", "退出当前设备，不删除资产", UiCopyPreviewKind.HamburgerLogoutConfirm),
                    UiCopyPreviewItem("删除历史对话确认", "提示会清除对话记忆，资产不受影响", UiCopyPreviewKind.HamburgerDeleteHistoryConfirm),
                    UiCopyPreviewItem("注销申请确认", "提交申请并退出登录", UiCopyPreviewKind.HamburgerAccountDeletionConfirm),
                    UiCopyPreviewItem("帮助与反馈", "站内消息、历史对话和未读红点", UiCopyPreviewKind.HamburgerSupportPage),
                    UiCopyPreviewItem("检查更新", "物料完整且版本更高才提示更新", UiCopyPreviewKind.HamburgerAppUpdateDialog),
                    UiCopyPreviewItem("更新下载中", "立即更新后的按钮和说明", UiCopyPreviewKind.HamburgerAppUpdateDownloading),
                    UiCopyPreviewItem("更新权限提示", "授权后返回本页继续更新", UiCopyPreviewKind.AppUpdateInstallPermissionHint),
                    UiCopyPreviewItem("更新未完成提示", "系统安装取消后可继续安装", UiCopyPreviewKind.AppUpdateInstallNotCompletedHint),
                    UiCopyPreviewItem("更新权限大字体", "1.6x 字体下真实更新弹窗", UiCopyPreviewKind.AppUpdatePermissionLargeFont),
                    UiCopyPreviewItem("账号管理大字体", "1.6x 字体下账号和危险操作", UiCopyPreviewKind.HamburgerAccountLargeFont),
                    UiCopyPreviewItem("礼品卡", "居中两行输入和兑换按钮", UiCopyPreviewKind.HamburgerGiftCardPage),
                    UiCopyPreviewItem("礼品卡失败提示", "失败原因停留在兑换页内", UiCopyPreviewKind.HamburgerGiftCardFailure),
                    UiCopyPreviewItem("协议与隐私目录", "服务协议、隐私政策和清单入口", UiCopyPreviewKind.HamburgerLegalHubPage),
                    UiCopyPreviewItem("服务协议", "本地内置服务协议正文", UiCopyPreviewKind.HamburgerServiceAgreementPage),
                    UiCopyPreviewItem("隐私政策", "权限和个人信息说明", UiCopyPreviewKind.HamburgerPrivacyPolicyPage),
                    UiCopyPreviewItem("第三方信息共享清单", "第三方和系统能力说明", UiCopyPreviewKind.HamburgerThirdPartyListPage),
                    UiCopyPreviewItem("个人信息收集清单", "按场景列明处理信息", UiCopyPreviewKind.HamburgerPersonalInfoListPage),
                    UiCopyPreviewItem("应用权限", "定位、后台待发送任务和安装更新权限口径", UiCopyPreviewKind.HamburgerPermissionListPage),
                    UiCopyPreviewItem("风险提示", "农业 AI 建议边界", UiCopyPreviewKind.HamburgerRiskNoticePage),
                    UiCopyPreviewItem("礼品卡成功样式", "兑换成功立即发放权益", UiCopyPreviewKind.HamburgerGiftCardSuccess),
                    UiCopyPreviewItem("礼品卡生效规则", "生成即可兑换，不做预约生效", UiCopyPreviewKind.HamburgerGiftCardImmediateRule),
                    UiCopyPreviewItem("礼品卡重复兑换", "同一账号重复提交时提示权益已生效", UiCopyPreviewKind.HamburgerGiftCardReplay)
                )
            ),
            UiCopyPreviewGroup(
                title = "帮助与反馈",
                items = listOf(
                    UiCopyPreviewItem("对话与链接", "旧消息先展示，后台合并新消息", UiCopyPreviewKind.HamburgerSupportPage),
                    UiCopyPreviewItem("空消息", "无历史反馈时的空态", UiCopyPreviewKind.HamburgerSupportEmpty),
                    UiCopyPreviewItem("首次同步中", "无缓存时才显示加载态", UiCopyPreviewKind.HamburgerSupportLoading),
                    UiCopyPreviewItem("首次同步失败", "无缓存时显示失败和重试", UiCopyPreviewKind.HamburgerSupportFailed),
                    UiCopyPreviewItem("图片输入", "图片预览条、输入框抬高和发送中态", UiCopyPreviewKind.HamburgerSupportImageInput),
                    UiCopyPreviewItem("长文本输入", "多行输入不收缩，最多显示 6 行", UiCopyPreviewKind.HamburgerSupportLongInput)
                )
            ),
            UiCopyPreviewGroup(
                title = "今日农情",
                items = listOf(
                    UiCopyPreviewItem("今日农情", "主聊天普通文本项，标题加粗、正文可复制", UiCopyPreviewKind.TodayAgriCard),
                    UiCopyPreviewItem("今日农情长摘要", "接近正式提示词的 3-4 行摘要", UiCopyPreviewKind.TodayAgriLongSummaryCard),
                    UiCopyPreviewItem("今日农情窄屏", "280dp 下标题、正文和来源不互挤", UiCopyPreviewKind.TodayAgriNarrow),
                    UiCopyPreviewItem("农情上下文规则", "远端确认后显示，后方三轮临时参考", UiCopyPreviewKind.TodayAgriContextRule),
                    UiCopyPreviewItem("农情历史页", "日期收进卡片内，旧简报先展示后刷新", UiCopyPreviewKind.HamburgerTodayAgriHistoryPage),
                    UiCopyPreviewItem("农情首次失败", "无缓存时显示失败和重试", UiCopyPreviewKind.HamburgerTodayAgriHistoryFailed)
                )
            ),
            UiCopyPreviewGroup(
                title = "文本渲染",
                items = listOf(
                    UiCopyPreviewItem("AI Markdown", "标题、列表、编号、引用、粗体、代码和链接", UiCopyPreviewKind.AssistantMarkdownSample),
                    UiCopyPreviewItem("AI 表格", "Markdown 表格按移动端分组展示，并保留复制表格按钮", UiCopyPreviewKind.AssistantTableSample),
                    UiCopyPreviewItem("用户链接气泡", "用户输入的网址可点击并可复制", UiCopyPreviewKind.UserLinkBubbleSample)
                )
            ),
            UiCopyPreviewGroup(
                title = "附件面板",
                items = listOf(
                    UiCopyPreviewItem(
                        "$COMPOSER_ATTACHMENT_CAMERA_TEXT / $COMPOSER_ATTACHMENT_PHOTO_TEXT",
                        "+ 面板未满状态：紧凑入口和拍摄建议",
                        UiCopyPreviewKind.AttachmentSheet
                    ),
                    UiCopyPreviewItem("帮助与反馈图片面板", "复用附件结构但不显示农业拍摄提示", UiCopyPreviewKind.SupportAttachmentSheet),
                    UiCopyPreviewItem(COMPOSER_IMAGE_COUNT_HINT, "已满附件面板", UiCopyPreviewKind.ImageCountSheet)
                )
            ),
            UiCopyPreviewGroup(
                title = "图片与预览",
                items = listOf(
                    UiCopyPreviewItem("1", "输入框缩略图角标", UiCopyPreviewKind.ComposerImageBadge),
                    UiCopyPreviewItem("1/4", "图片全屏预览页码", UiCopyPreviewKind.ImagePageIndicator),
                    UiCopyPreviewItem(IMAGE_EXPIRED_THUMB_TEXT, "远端历史图过期后的占位", UiCopyPreviewKind.ImageExpiredPlaceholder)
                )
            ),
            UiCopyPreviewGroup(
                title = "消息尾部",
                items = listOf(
                    UiCopyPreviewItem(AI_DISCLAIMER_TEXT, "AI 回复尾部免责声明", UiCopyPreviewKind.Disclaimer),
                    UiCopyPreviewItem(ASSISTANT_RETRY_PREVIEW_TEXT, "AI 回复中断后尾部", UiCopyPreviewKind.AssistantRetry),
                    UiCopyPreviewItem(ASSISTANT_RETRYING_STATUS_TEXT, "AI 尾部补上传图片时", UiCopyPreviewKind.AssistantRetrying),
                    UiCopyPreviewItem(USER_RETRY_PREVIEW_TEXT, "用户消息发送失败后尾部", UiCopyPreviewKind.UserRetry),
                    UiCopyPreviewItem(USER_PENDING_IMAGE_SEND_PREVIEW_TEXT, "用户带图后台发送中尾部", UiCopyPreviewKind.UserRetrying),
                    UiCopyPreviewItem(USER_RETRYING_STATUS_TEXT, "用户尾部补上传图片时", UiCopyPreviewKind.UserRetrying)
                )
            ),
            UiCopyPreviewGroup(
                title = "主界面中部浮层",
                items = listOf(
                    UiCopyPreviewItem(QUOTA_EXHAUSTED_HINT_TEXT, "日额度耗尽中部短提示", UiCopyPreviewKind.Quota),
                    UiCopyPreviewItem(NETWORK_UNAVAILABLE_HINT_TEXT, "未验证联网 / 门户 Wi-Fi / 无网络", UiCopyPreviewKind.Network),
                    UiCopyPreviewItem(RATE_LIMIT_HINT_TEXT, "限流 / 服务忙浮层", UiCopyPreviewKind.RateLimit),
                    UiCopyPreviewItem(SERVICE_UNAVAILABLE_HINT_TEXT, "服务临时不可用浮层", UiCopyPreviewKind.ServiceUnavailable),
                    UiCopyPreviewItem(ACTIVE_STREAM_HINT_TEXT, "上一条仍在处理浮层", UiCopyPreviewKind.ActiveStream),
                    UiCopyPreviewItem(INTERRUPTED_NETWORK_HINT_TEXT, "streaming 网络中断浮层", UiCopyPreviewKind.Interrupted),
                    UiCopyPreviewItem(INTERRUPTED_FALLBACK_HINT_TEXT, "其他中断浮层", UiCopyPreviewKind.InterruptedFallback),
                    UiCopyPreviewItem(INPUT_TOO_LONG_HINT_TEXT, "输入超过 6000 字浮层", UiCopyPreviewKind.InputTooLong),
                    UiCopyPreviewItem(COMPOSER_IMAGE_COUNT_HINT, "图片数量中部短提示", UiCopyPreviewKind.ImageCountHint),
                    UiCopyPreviewItem(ImageUploader.DECODE_FAIL_MESSAGE, "图片读取失败中部短提示", UiCopyPreviewKind.ImageReadFailure),
                    UiCopyPreviewItem("登录已失效，请重新登录后再上传图片", "图片上传登录失效中部短提示", UiCopyPreviewKind.ImageUploadAuthExpired),
                    UiCopyPreviewItem("图片上传失败，请稍后重试", "图片上传失败中部短提示", UiCopyPreviewKind.ImageUploadFailed),
                    UiCopyPreviewItem(CAMERA_OPEN_FAILED_HINT_TEXT, "相机打开失败中部短提示", UiCopyPreviewKind.CameraOpenFailed),
                    UiCopyPreviewItem(SUPPORT_SEND_FAILED_HINT, "帮助与反馈发送失败中部短提示", UiCopyPreviewKind.SupportSendFailed)
                )
            ),
            UiCopyPreviewGroup(
                title = "选择菜单",
                items = listOf(
                    UiCopyPreviewItem("复制 / 全文复制", "消息选择菜单", UiCopyPreviewKind.MessageMenu),
                    UiCopyPreviewItem("复制 / 粘贴 / 剪切 / 全选", "输入框完整选择菜单", UiCopyPreviewKind.InputMenu),
                    UiCopyPreviewItem("复制", "输入框仅复制菜单", UiCopyPreviewKind.InputMenuCopyOnly),
                    UiCopyPreviewItem("粘贴 / 全选", "输入框无选区菜单", UiCopyPreviewKind.InputMenuPasteSelect)
                )
            ),
            UiCopyPreviewGroup(
                title = "预览面板",
                items = listOf(
                    UiCopyPreviewItem("UI文案样式预览", "debug 面板标题和说明", UiCopyPreviewKind.DebugPanel),
                    UiCopyPreviewItem("右上关闭 / 展开 / 查看", "debug 面板关闭和条目控件", UiCopyPreviewKind.DebugPanelControls)
                )
            )
        )
    }
    val copyItems = remember(copyGroups) { copyGroups.flatMap { it.items } }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val expandedGroups = remember(copyGroups) {
        mutableStateMapOf<String, Boolean>().apply {
            copyGroups.firstOrNull()?.let { group -> put(group.title, true) }
        }
    }
    val selectedItem = copyItems[selectedIndex.coerceIn(0, copyItems.lastIndex)]
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.18f))
            .zIndex(80f)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 12.dp,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 28.dp)
                .widthIn(max = 480.dp)
                .heightIn(max = 760.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {})
                }
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "UI文案样式预览",
                        color = Color(0xFF111111),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F3F6))
                            .semantics { contentDescription = "关闭预览面板" }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismiss
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        UserMessagePreviewCloseIcon(
                            tint = Color(0xFF111111),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Text(
                    text = "点一级标题展开或收起，点二级条目查看正式组件或调试近似样式。点空白关闭，仅 debug 包显示。",
                    color = Color(0xFF6D7178),
                    style = MaterialTheme.typography.bodySmall
                )
                copyGroups.forEach { group ->
                    val expanded = expandedGroups[group.title] == true
                    UiCopyPreviewGroupHeader(
                        title = group.title,
                        count = group.items.size,
                        expanded = expanded,
                        onClick = {
                            expandedGroups[group.title] = !expanded
                        }
                    )
                    AnimatedVisibility(visible = expanded) {
                        Column(
                            modifier = Modifier.animateContentSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            group.items.forEach { item ->
                                val rowIndex = copyItems.indexOf(item)
                                UiCopyPreviewListRow(
                                    item = item,
                                    selected = rowIndex == selectedIndex,
                                    onClick = { selectedIndex = rowIndex }
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(
                    thickness = 0.7.dp,
                    color = Color(0xFFE4E6EA),
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "样式预览",
                    color = Color(0xFF111111),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                UiCopyPreviewSample(selectedItem)
            }
        }
    }
}

private data class UiCopyPreviewGroup(
    val title: String,
    val items: List<UiCopyPreviewItem>
)

private data class UiCopyPreviewItem(
    val title: String,
    val subtitle: String,
    val kind: UiCopyPreviewKind
)

private const val UI_COPY_PREVIEW_ASSISTANT_MARKDOWN_SAMPLE =
    "# 叶片发黄排查\n" +
        "建议先看 **叶背虫体**、`pH` 和近期浇水。\n" +
        "**处理建议**\n" +
        "先停用高浓度叶面肥，补拍根系和土壤湿度。\n" +
        "**注意事项：**\n" +
        "不要把正文里的 **重点词** 当成标题分割。\n" +
        "- 拍叶片正反面\n" +
        "- 拍根系和土壤湿度\n" +
        "1. **三大病害：**\n" +
        "- 疫病：高温高湿时注意控水排湿。\n" +
        "- 炭疽病：果面黑斑要及时摘除病果。\n" +
        "2. **三大虫害：**\n" +
        "- 蚜虫、蓟马、烟青虫：轮换用药，避免抗性。\n" +
        "公式里的 亩数*亩用量*浓度 会保持原样。\n" +
        "特殊符号：EC≤2.0、25~30°C、0.2%、1:800、20kg/亩、±10%、N-P-K、2**3**4。\n" +
        "通用符号：@#$%^&+=~、()[]{}<>、《》“”'\"、→←↑↓、√×÷≈≠≤≥、😀。\n" +
        "> AI 只能提供参考，现场仍要复核。\n" +
        "官方查询可看 https://www.moa.gov.cn/，或 [植保中心](https://www.natesc.org.cn/)。"

private const val UI_COPY_PREVIEW_ASSISTANT_TABLE_SAMPLE =
    "| 维度 | 成品含腐植酸尿素 | 普通尿素 + 矿源黄腐酸钾 |\n" +
        "| --- | --- | --- |\n" +
        "| 便利性 | 开袋即用，省工省力。 | 需要按场景混配，适合做方案。 |\n" +
        "| 含量透明度 | 具体添加量不一定公开。 | 用量自己掌握，客户更容易算账。 |\n" +
        "| 灵活性 | 配比固定，难按地块调整。 | 可按高温、弱根、盐碱等情况增减。 |"

private enum class UiCopyPreviewKind {
    AppTitle,
    Welcome,
    LoginInitial,
    LoginAgreementBlocked,
    LoginSmsFallback,
    LoginPhoneChanged,
    LoginLegalDialog,
    CleanStateFirstLaunch,
    CleanStateFirstSend,
    CleanStateChecklist,
    RemoteSnapshotFallback,
    ClearHistoryHydrateGuard,
    ImagePreviewTopChrome,
    MembershipSheetSafeArea,
    ComposerPlaceholder,
    ComposerImagePlaceholder,
    MembershipHeader,
    MembershipLoadingSummary,
    MembershipFreeSummary,
    MembershipFreeExtraSummary,
    MembershipPlusExtraSummary,
    MembershipProSummary,
    MembershipFailedSummary,
    MembershipPlanUnknown,
    MembershipPlanFree,
    MembershipPlanPlus,
    MembershipPlanPro,
    MembershipPlanNarrow,
    MembershipTopupUnavailable,
    MembershipTopupFreeActive,
    MembershipTopupBuyable,
    MembershipTopupActive,
    MembershipTopupNarrow,
    MembershipPaymentNotice,
    MembershipPurchaseSuccess,
    MembershipRules,
    HamburgerMenu,
    HamburgerMenuShell,
    HamburgerMembershipPage,
    HamburgerAccountPage,
    HamburgerLogoutConfirm,
    HamburgerDeleteHistoryConfirm,
    HamburgerAccountDeletionConfirm,
    HamburgerSupportPage,
    HamburgerSupportEmpty,
    HamburgerSupportLoading,
    HamburgerSupportFailed,
    HamburgerSupportImageInput,
    HamburgerSupportLongInput,
    HamburgerAppUpdateDialog,
    HamburgerAppUpdateDownloading,
    AppUpdateInstallPermissionHint,
    AppUpdateInstallNotCompletedHint,
    AppUpdatePermissionLargeFont,
    HamburgerAccountLargeFont,
    HamburgerGiftCardPage,
    HamburgerGiftCardFailure,
    HamburgerLegalHubPage,
    HamburgerServiceAgreementPage,
    HamburgerPrivacyPolicyPage,
    HamburgerThirdPartyListPage,
    HamburgerPersonalInfoListPage,
    HamburgerPermissionListPage,
    HamburgerRiskNoticePage,
    HamburgerGiftCardSuccess,
    HamburgerGiftCardImmediateRule,
    HamburgerGiftCardReplay,
    TodayAgriCard,
    TodayAgriLongSummaryCard,
    TodayAgriNarrow,
    TodayAgriContextRule,
    HamburgerTodayAgriHistoryPage,
    HamburgerTodayAgriHistoryFailed,
    AssistantMarkdownSample,
    AssistantTableSample,
    UserLinkBubbleSample,
    AttachmentSheet,
    SupportAttachmentSheet,
    Disclaimer,
    AssistantRetry,
    AssistantRetrying,
    UserRetry,
    UserRetrying,
    Network,
    SupportSendFailed,
    Quota,
    RateLimit,
    ServiceUnavailable,
    ActiveStream,
    Interrupted,
    InterruptedFallback,
    InputTooLong,
    MessageMenu,
    InputMenu,
    ImageReadFailure,
    ImageUploadAuthExpired,
    ImageUploadFailed,
    ImageCountHint,
    ImageCountSheet,
    CameraOpenFailed,
    ComposerImageBadge,
    ImagePageIndicator,
    ImageExpiredPlaceholder,
    InputMenuCopyOnly,
    InputMenuPasteSelect,
    DebugPanel,
    DebugPanelControls
}

@Composable
private fun UiCopyPreviewGroupHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (expanded) Color(0xFFF5F6F8) else Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 0.8.dp,
            color = if (expanded) Color(0xFFDCE0E6) else Color(0xFFE8EAEE)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color(0xFF111111),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${count}项",
                color = Color(0xFF8A8E96),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(end = 10.dp)
            )
            Text(
                text = if (expanded) "收起" else "展开",
                color = Color(0xFF111111),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun UiCopyPreviewListRow(
    item: UiCopyPreviewItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) Color(0xFFF1F3F6) else Color.White,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = 0.8.dp,
            color = if (selected) Color(0xFFBFC5CE) else Color(0xFFE8EAEE)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.title,
                    color = Color(0xFF17191C),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
                Text(
                    text = item.subtitle,
                    color = Color(0xFF70747B),
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 17.sp
                )
            }
            Text(
                text = if (selected) "预览中" else "查看",
                color = if (selected) Color(0xFF111111) else Color(0xFF8A8E96),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun UiCopyPreviewSample(item: UiCopyPreviewItem) {
    val previewSelectionColors = remember {
        TextSelectionColors(
            handleColor = CHAT_SELECTION_HANDLE_COLOR,
            backgroundColor = CHAT_SELECTION_BACKGROUND_COLOR
        )
    }
    Surface(
        color = Color(0xFFF7F8FA),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.8.dp, Color(0xFFE4E6EA)),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = item.title,
                color = Color(0xFF70747B),
                style = MaterialTheme.typography.labelMedium
            )
            when (item.kind) {
                UiCopyPreviewKind.AppTitle -> {
                    Text(
                        text = APP_TITLE_TEXT,
                        color = Color(0xFF111111),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                UiCopyPreviewKind.Welcome -> {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(0.8.dp, Color(0xFFE4E6EA)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = WELCOME_EMPTY_STATE_TEXT,
                            color = Color(0xFF202124),
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 23.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp)
                        )
                    }
                }
                UiCopyPreviewKind.LoginInitial -> {
                    UiCopyPreviewLoginPage(
                        agreed = false,
                        message = "首次打开直接是登录页；勾选后才记录隐私同意。"
                    )
                }
                UiCopyPreviewKind.LoginAgreementBlocked -> {
                    UiCopyPreviewLoginPage(
                        agreed = false,
                        message = "请先同意服务协议和隐私政策"
                    )
                }
                UiCopyPreviewKind.LoginSmsFallback -> {
                    UiCopyPreviewLoginPage(
                        agreed = true,
                        message = "验证码已发送",
                        sendButtonText = "60s"
                    )
                }
                UiCopyPreviewKind.LoginPhoneChanged -> {
                    UiCopyPreviewLoginPage(
                        agreed = true,
                        message = "已更换手机号，可重新发送验证码",
                        sendButtonText = "发送"
                    )
                }
                UiCopyPreviewKind.LoginLegalDialog -> UiCopyPreviewLoginLegalDialog()
                UiCopyPreviewKind.CleanStateFirstLaunch -> {
                    UiCopyPreviewCleanStateFirstLaunch()
                }
                UiCopyPreviewKind.CleanStateFirstSend -> {
                    UiCopyPreviewCleanStateFirstSend()
                }
                UiCopyPreviewKind.CleanStateChecklist -> {
                    UiCopyPreviewPlainText(
                        listOf(
                            "清除 App 数据后应视为 clean-state。",
                            "不从本地恢复旧聊天、旧输入框高度或旧滚动位置。",
                            "未登录阶段 user_id 可重置；以后账号恢复历史属于平台业务恢复。"
                        )
                    )
                }
                UiCopyPreviewKind.RemoteSnapshotFallback -> {
                    UiCopyPreviewRemoteSnapshotFallback()
                }
                UiCopyPreviewKind.ClearHistoryHydrateGuard -> {
                    UiCopyPreviewClearHistoryHydrateGuard()
                }
                UiCopyPreviewKind.ImagePreviewTopChrome -> {
                    UiCopyPreviewImageTopChrome()
                }
                UiCopyPreviewKind.MembershipSheetSafeArea -> {
                    UiCopyPreviewMembershipSheetSafeArea()
                }
                UiCopyPreviewKind.ComposerPlaceholder -> {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(30.dp),
                        shadowElevation = 6.dp,
                        border = BorderStroke(0.6.dp, Color(0xFFE6E8EC)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = COMPOSER_DEFAULT_PLACEHOLDER_TEXT,
                            color = Color(0xFFB7BAC1),
                            fontSize = 18.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp)
                        )
                    }
                }
                UiCopyPreviewKind.ComposerImagePlaceholder -> {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(30.dp),
                        shadowElevation = 6.dp,
                        border = BorderStroke(0.6.dp, Color(0xFFE6E8EC)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = COMPOSER_IMAGE_PLACEHOLDER_TEXT,
                            color = Color(0xFFB7BAC1),
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp)
                        )
                    }
                }
                UiCopyPreviewKind.MembershipHeader -> {
                    MembershipCenterHeaderPreview(userId = IdManager.getUserId())
                }
                UiCopyPreviewKind.MembershipLoadingSummary -> {
                    UiCopyPreviewMembershipSummary(
                        entitlement = null,
                        loadState = MembershipLoadState.Loading
                    )
                }
                UiCopyPreviewKind.MembershipFreeSummary -> {
                    UiCopyPreviewMembershipSummary(
                        entitlement = SessionApi.EntitlementSnapshot(
                            tier = "free",
                            dailyRemaining = 6
                        ),
                        loadState = MembershipLoadState.Loaded
                    )
                }
                UiCopyPreviewKind.MembershipFreeExtraSummary -> {
                    UiCopyPreviewMembershipSummaryWithPlans(
                        entitlement = SessionApi.EntitlementSnapshot(
                            tier = "free",
                            dailyRemaining = 2,
                            topupRemaining = 15,
                            upgradeRemaining = 3
                        ),
                        loadState = MembershipLoadState.Loaded,
                        activeTier = "free"
                    )
                }
                UiCopyPreviewKind.MembershipPlusExtraSummary -> {
                    UiCopyPreviewMembershipSummaryWithPlans(
                        entitlement = SessionApi.EntitlementSnapshot(
                            tier = "plus",
                            tierExpireAt = uiCopyPreviewExpireAtMs(daysFromNow = 24),
                            dailyRemaining = 18,
                            topupRemaining = 0,
                            upgradeRemaining = 0,
                            membershipSource = "gift_card",
                            giftCardRedeemedAt = uiCopyPreviewExpireAtMs(daysFromNow = -6)
                        ),
                        loadState = MembershipLoadState.Loaded,
                        activeTier = "plus"
                    )
                }
                UiCopyPreviewKind.MembershipProSummary -> {
                    UiCopyPreviewMembershipSummary(
                        entitlement = SessionApi.EntitlementSnapshot(
                            tier = "pro",
                            tierExpireAt = uiCopyPreviewExpireAtMs(daysFromNow = 30),
                            dailyRemaining = 32
                        ),
                        loadState = MembershipLoadState.Loaded
                    )
                }
                UiCopyPreviewKind.MembershipFailedSummary -> {
                    MembershipCenterBody(
                        entitlement = SessionApi.EntitlementSnapshot(
                            tier = "plus",
                            tierExpireAt = uiCopyPreviewExpireAtMs(daysFromNow = 12),
                            dailyRemaining = 18,
                            topupRemaining = 20
                        ),
                        loadState = MembershipLoadState.Failed,
                        paymentNoticeResetKey = UiCopyPreviewKind.MembershipFailedSummary,
                        onPaymentUnavailable = {},
                        onRetryLoad = {}
                    )
                }
                UiCopyPreviewKind.MembershipPlanUnknown -> {
                    MembershipPlanSectionPreview(activeTier = "unknown")
                }
                UiCopyPreviewKind.MembershipPlanFree -> {
                    MembershipPlanSectionPreview(activeTier = "free")
                }
                UiCopyPreviewKind.MembershipPlanPlus -> {
                    MembershipPlanSectionPreview(activeTier = "plus")
                }
                UiCopyPreviewKind.MembershipPlanPro -> {
                    MembershipPlanSectionPreview(activeTier = "pro")
                }
                UiCopyPreviewKind.MembershipPlanNarrow -> {
                    UiCopyPreviewNarrowFrame {
                        MembershipPlanSectionPreview(
                            activeTier = "plus",
                            upgradeRemaining = 88,
                            topupRemaining = 80
                        )
                    }
                }
                UiCopyPreviewKind.MembershipTopupUnavailable -> {
                    MembershipTopupCardPreview(
                        activeTier = "free",
                        topupRemaining = 0
                    )
                }
                UiCopyPreviewKind.MembershipTopupFreeActive -> {
                    MembershipTopupCardPreview(
                        activeTier = "free",
                        topupRemaining = 15
                    )
                }
                UiCopyPreviewKind.MembershipTopupBuyable -> {
                    MembershipTopupCardPreview(
                        activeTier = "plus",
                        topupRemaining = 0
                    )
                }
                UiCopyPreviewKind.MembershipTopupActive -> {
                    MembershipTopupCardPreview(
                        activeTier = "pro",
                        topupRemaining = 73
                    )
                }
                UiCopyPreviewKind.MembershipTopupNarrow -> {
                    UiCopyPreviewNarrowFrame {
                        MembershipTopupCardPreview(
                            activeTier = "pro",
                            topupRemaining = 73
                        )
                    }
                }
                UiCopyPreviewKind.MembershipPaymentNotice -> {
                    MembershipPaymentNoticePreview()
                }
                UiCopyPreviewKind.MembershipPurchaseSuccess -> {
                    MembershipPurchaseSuccessPreview()
                }
                UiCopyPreviewKind.MembershipRules -> {
                    MembershipRulesPreview()
                }
                UiCopyPreviewKind.HamburgerMenu -> {
                    HamburgerMenuSheetPreview(userId = IdManager.getUserId())
                }
                UiCopyPreviewKind.HamburgerMenuShell -> {
                    HamburgerMenuShellPreview(userId = IdManager.getUserId())
                }
                UiCopyPreviewKind.HamburgerMembershipPage -> {
                    HamburgerMembershipCenterPagePreview(userId = IdManager.getUserId())
                }
                UiCopyPreviewKind.HamburgerAccountPage -> {
                    HamburgerAccountManagementPagePreview()
                }
                UiCopyPreviewKind.HamburgerLogoutConfirm -> {
                    HamburgerLogoutConfirmPreview()
                }
                UiCopyPreviewKind.HamburgerDeleteHistoryConfirm -> {
                    HamburgerDeleteHistoryConfirmPreview()
                }
                UiCopyPreviewKind.HamburgerAccountDeletionConfirm -> {
                    HamburgerAccountDeletionConfirmPreview()
                }
                UiCopyPreviewKind.HamburgerSupportPage -> {
                    HamburgerSupportFeedbackPagePreview()
                }
                UiCopyPreviewKind.HamburgerSupportEmpty -> {
                    HamburgerSupportFeedbackPagePreview(HamburgerSupportFeedbackPreviewVariant.Empty)
                }
                UiCopyPreviewKind.HamburgerSupportLoading -> {
                    HamburgerSupportFeedbackPagePreview(HamburgerSupportFeedbackPreviewVariant.Loading)
                }
                UiCopyPreviewKind.HamburgerSupportFailed -> {
                    HamburgerSupportFeedbackPagePreview(HamburgerSupportFeedbackPreviewVariant.Failed)
                }
                UiCopyPreviewKind.HamburgerSupportImageInput -> {
                    HamburgerSupportFeedbackPagePreview(HamburgerSupportFeedbackPreviewVariant.ImageInput)
                }
                UiCopyPreviewKind.HamburgerSupportLongInput -> {
                    HamburgerSupportFeedbackPagePreview(HamburgerSupportFeedbackPreviewVariant.LongInput)
                }
                UiCopyPreviewKind.HamburgerAppUpdateDialog -> {
                    HamburgerAppUpdateDialogPreview()
                }
                UiCopyPreviewKind.HamburgerAppUpdateDownloading -> {
                    HamburgerAppUpdateDialogPreview(downloading = true)
                }
                UiCopyPreviewKind.AppUpdateInstallPermissionHint -> {
                    HamburgerAppUpdateDialogPreview(installPermissionPending = true)
                }
                UiCopyPreviewKind.AppUpdateInstallNotCompletedHint -> {
                    UiCopyPreviewHint("更新未完成，可稍后继续安装")
                }
                UiCopyPreviewKind.AppUpdatePermissionLargeFont -> {
                    UiCopyPreviewLargeFont {
                        HamburgerAppUpdateDialogPreview(installPermissionPending = true)
                    }
                }
                UiCopyPreviewKind.HamburgerAccountLargeFont -> {
                    UiCopyPreviewLargeFont {
                        HamburgerAccountManagementPagePreview()
                    }
                }
                UiCopyPreviewKind.HamburgerGiftCardPage -> {
                    HamburgerRedeemCodePagePreview()
                }
                UiCopyPreviewKind.HamburgerGiftCardFailure -> {
                    HamburgerRedeemFailurePagePreview()
                }
                UiCopyPreviewKind.HamburgerLegalHubPage -> {
                    HamburgerLegalHubPagePreview()
                }
                UiCopyPreviewKind.HamburgerServiceAgreementPage -> {
                    HamburgerServiceAgreementPagePreview()
                }
                UiCopyPreviewKind.HamburgerPrivacyPolicyPage -> {
                    HamburgerPrivacyPolicyPagePreview()
                }
                UiCopyPreviewKind.HamburgerThirdPartyListPage -> {
                    HamburgerThirdPartyListPagePreview()
                }
                UiCopyPreviewKind.HamburgerPersonalInfoListPage -> {
                    HamburgerPersonalInfoListPagePreview()
                }
                UiCopyPreviewKind.HamburgerPermissionListPage -> {
                    HamburgerPermissionListPagePreview()
                }
                UiCopyPreviewKind.HamburgerRiskNoticePage -> {
                    HamburgerRiskNoticePagePreview()
                }
                UiCopyPreviewKind.HamburgerGiftCardSuccess -> {
                    HamburgerRedeemSuccessCardPreview()
                }
                UiCopyPreviewKind.HamburgerGiftCardImmediateRule -> {
                    UiCopyPreviewPlainText(
                        listOf(
                            "礼品卡生成后即可兑换",
                            "兑换成功后会员权益立即发放",
                            "valid_from 只作创建追溯，不作为预约生效门槛",
                            "兑换失败原因会停留在礼品卡页内"
                        )
                    )
                }
                UiCopyPreviewKind.HamburgerGiftCardReplay -> {
                    HamburgerRedeemReplayCardPreview()
                }
                UiCopyPreviewKind.TodayAgriCard -> {
                    TodayAgriNewsText(
                        card = uiCopyPreviewTodayAgriCard(),
                        textSelectionColors = previewSelectionColors
                    )
                }
                UiCopyPreviewKind.TodayAgriLongSummaryCard -> {
                    TodayAgriNewsText(
                        card = uiCopyPreviewTodayAgriLongSummaryCard(),
                        textSelectionColors = previewSelectionColors
                    )
                }
                UiCopyPreviewKind.TodayAgriNarrow -> {
                    Box(modifier = Modifier.width(280.dp)) {
                        TodayAgriNewsText(
                            card = uiCopyPreviewTodayAgriLongSummaryCard(),
                            horizontalPadding = 0.dp,
                            maxContentWidth = 280.dp,
                            textSelectionColors = previewSelectionColors
                        )
                    }
                }
                UiCopyPreviewKind.TodayAgriContextRule -> {
                    UiCopyPreviewPlainText(
                        listOf(
                            "无真实聊天时只显示欢迎语，今日农情不占空态",
                            "已有完整 AI 回答历史时，才跟在最后一条回答后",
                            "如果用户本次开始问了而农情还没显示，本次运行不突然插入",
                            "一旦主界面展示过，保存当天一条主界面记录，重开后稳定恢复",
                            "远端确认当天 ready 后，用户在它后面发送的后三轮会临时带当天农情标记",
                            "后端只在日期等于服务器当天时读取农情正文作为临时背景",
                            "第四轮起自动不带；记忆整理、聊天归档和扣次都不写入农情正文"
                        )
                    )
                }
                UiCopyPreviewKind.HamburgerTodayAgriHistoryPage -> {
                    HamburgerTodayAgriHistoryPagePreview()
                }
                UiCopyPreviewKind.HamburgerTodayAgriHistoryFailed -> {
                    HamburgerTodayAgriHistoryPagePreview(loadFailed = true)
                }
                UiCopyPreviewKind.AssistantMarkdownSample -> {
                    ChatStreamingRenderer(
                        content = UI_COPY_PREVIEW_ASSISTANT_MARKDOWN_SAMPLE,
                        renderMode = StreamingRenderMode.Settled,
                        showWaitingBall = false,
                        selectionEnabled = true,
                        showDisclaimer = true,
                        onStreamingContentBoundsChanged = null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                UiCopyPreviewKind.AssistantTableSample -> {
                    ChatStreamingRenderer(
                        content = UI_COPY_PREVIEW_ASSISTANT_TABLE_SAMPLE,
                        renderMode = StreamingRenderMode.Settled,
                        showWaitingBall = false,
                        selectionEnabled = true,
                        showDisclaimer = true,
                        onStreamingContentBoundsChanged = null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                UiCopyPreviewKind.UserLinkBubbleSample -> {
                    SelectableRenderedUserMessageBubble(
                        content = "看看这个链接 www.moa.gov.cn，还有 https://www.natesc.org.cn/ 可以点吗",
                        textSelectionColors = TextSelectionColors(
                            handleColor = CHAT_SELECTION_HANDLE_COLOR,
                            backgroundColor = CHAT_SELECTION_BACKGROUND_COLOR
                        ),
                        textToolbar = LocalTextToolbar.current,
                        selectionResetKey = 0,
                        userBubbleMaxWidth = 280.dp,
                        userBubbleColor = Color(0xFF050505),
                        userBubbleBorderColor = Color(0xFF050505)
                    )
                }
                UiCopyPreviewKind.AttachmentSheet -> {
                    UiCopyPreviewAttachmentSheet(limitReached = false)
                }
                UiCopyPreviewKind.SupportAttachmentSheet -> {
                    UiCopyPreviewAttachmentSheet(limitReached = false, supportingHintText = null)
                }
                UiCopyPreviewKind.Disclaimer -> {
                    UiCopyPreviewDisclaimer()
                }
                UiCopyPreviewKind.AssistantRetry -> {
                    MessageStatusFooter(
                        statusText = ASSISTANT_RETRY_STATUS_TEXT,
                        actionText = ASSISTANT_RETRY_ACTION_TEXT,
                        alignEnd = false,
                        onActionClick = {}
                    )
                }
                UiCopyPreviewKind.AssistantRetrying -> {
                    MessageStatusFooter(
                        statusText = ASSISTANT_RETRYING_STATUS_TEXT,
                        actionText = null,
                        alignEnd = false,
                        enabled = false,
                        onActionClick = {}
                    )
                }
                UiCopyPreviewKind.UserRetry -> {
                    MessageStatusFooter(
                        statusText = USER_RETRY_STATUS_TEXT,
                        actionText = USER_RETRY_ACTION_TEXT,
                        alignEnd = true,
                        onActionClick = {}
                    )
                }
                UiCopyPreviewKind.UserRetrying -> {
                    MessageStatusFooter(
                        statusText = USER_RETRYING_STATUS_TEXT,
                        actionText = null,
                        alignEnd = true,
                        enabled = false,
                        onActionClick = {}
                    )
                }
                UiCopyPreviewKind.Network -> UiCopyPreviewHint(NETWORK_UNAVAILABLE_HINT_TEXT)
                UiCopyPreviewKind.SupportSendFailed -> UiCopyPreviewHint(SUPPORT_SEND_FAILED_HINT)
                UiCopyPreviewKind.Quota -> UiCopyPreviewHint(QUOTA_EXHAUSTED_HINT_TEXT)
                UiCopyPreviewKind.RateLimit -> UiCopyPreviewHint(RATE_LIMIT_HINT_TEXT)
                UiCopyPreviewKind.ServiceUnavailable -> UiCopyPreviewHint(SERVICE_UNAVAILABLE_HINT_TEXT)
                UiCopyPreviewKind.ActiveStream -> UiCopyPreviewHint(ACTIVE_STREAM_HINT_TEXT)
                UiCopyPreviewKind.Interrupted -> UiCopyPreviewHint(INTERRUPTED_NETWORK_HINT_TEXT)
                UiCopyPreviewKind.InterruptedFallback -> UiCopyPreviewHint(INTERRUPTED_FALLBACK_HINT_TEXT)
                UiCopyPreviewKind.InputTooLong -> UiCopyPreviewHint(INPUT_TOO_LONG_HINT_TEXT)
                UiCopyPreviewKind.MessageMenu -> {
                    MessageActionMenuCardContent(onCopy = {}, onCopyFull = {})
                }
                UiCopyPreviewKind.InputMenu -> {
                    UiCopyPreviewInputActionMenu(listOf("复制", "粘贴", "剪切", "全选"))
                }
                UiCopyPreviewKind.ImageReadFailure -> UiCopyPreviewHint(ImageUploader.DECODE_FAIL_MESSAGE)
                UiCopyPreviewKind.ImageUploadAuthExpired -> UiCopyPreviewHint("登录已失效，请重新登录后再上传图片")
                UiCopyPreviewKind.ImageUploadFailed -> UiCopyPreviewHint("图片上传失败，请稍后重试")
                UiCopyPreviewKind.ImageCountHint -> UiCopyPreviewHint(COMPOSER_IMAGE_COUNT_HINT)
                UiCopyPreviewKind.ImageCountSheet -> UiCopyPreviewAttachmentSheet(limitReached = true)
                UiCopyPreviewKind.CameraOpenFailed -> UiCopyPreviewHint(CAMERA_OPEN_FAILED_HINT_TEXT)
                UiCopyPreviewKind.ComposerImageBadge -> UiCopyPreviewComposerImageBadge()
                UiCopyPreviewKind.ImagePageIndicator -> UiCopyPreviewImagePageIndicator()
                UiCopyPreviewKind.ImageExpiredPlaceholder -> UiCopyPreviewExpiredImagePlaceholder()
                UiCopyPreviewKind.InputMenuCopyOnly -> UiCopyPreviewInputActionMenu(listOf("复制"))
                UiCopyPreviewKind.InputMenuPasteSelect -> UiCopyPreviewInputActionMenu(listOf("粘贴", "全选"))
                UiCopyPreviewKind.DebugPanel -> UiCopyPreviewPlainText(
                    listOf("UI文案样式预览", "点一级标题展开或收起，点二级条目查看正式组件或调试近似样式。点空白关闭，仅 debug 包显示。")
                )
                UiCopyPreviewKind.DebugPanelControls -> UiCopyPreviewPlainText(
                    listOf(
                        "右上角 X 关闭",
                        "一级标题可展开 / 收起",
                        "登录与协议包含首次登录、未勾选拦截和验证码兜底",
                        "检查更新按普通更新展示，含下载中和权限提示",
                        "今日农情含主卡、长摘要、历史页和失败态",
                        "查看 / 预览中 / 样式预览"
                    )
                )
            }
        }
    }
}

@Composable
private fun UiCopyPreviewNarrowFrame(
    width: Dp = 280.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.width(width)) {
            content()
        }
    }
}

@Composable
private fun UiCopyPreviewLoginPage(
    agreed: Boolean,
    message: String?,
    sendButtonText: String = "发送"
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(0.8.dp, Color(0xFFE2E4E8)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = APP_TITLE_TEXT,
                color = Color(0xFF111111),
                fontSize = 30.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = 0.sp
            )
            UiCopyPreviewLoginField("手机号")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                UiCopyPreviewLoginField(
                    text = "验证码",
                    modifier = Modifier.weight(1f)
                )
                UiCopyPreviewLoginButton(
                    text = sendButtonText,
                    primary = false,
                    modifier = Modifier.width(88.dp)
                )
            }
            UiCopyPreviewLoginButton(
                text = "登录",
                primary = true
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                UiCopyPreviewAgreementCheckbox(checked = agreed)
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "我已阅读并同意《服务协议》《隐私政策》",
                    color = Color(0xFF575D66),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 0.sp,
                    modifier = Modifier.weight(1f)
                )
            }
            message?.let {
                val positive = it.contains("已发送") || it.startsWith("正在")
                Text(
                    text = it,
                    color = Color(0xFF4E5661),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Color(0xFFF1F3F5),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                )
            }
            UiCopyPreviewPlainText(
                listOf(
                    "同意前不初始化身份、不补报崩溃日志。",
                    "验证码由平台短信服务发送和校验。",
                    "登录成功后继续使用同一个账号ID。"
                )
            )
        }
    }
}

@Composable
private fun UiCopyPreviewLoginLegalDialog() {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.8.dp, Color(0xFFE2E4E8)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 8.dp)
            ) {
                Text(
                    text = "服务协议",
                    color = Color(0xFF111111),
                    fontSize = 17.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "关闭",
                    color = Color(0xFF111111),
                    fontSize = 14.sp,
                    letterSpacing = 0.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            HorizontalDivider(color = Color(0xFFE8EAEE))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "服务协议",
                    color = Color(0xFF111111),
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp
                )
                Text(
                    text = "登录页内打开协议时，正文区域有固定高度；服务协议本身滚动，隐私政策外层滚动，避免嵌套滚动导致闪退。",
                    color = Color(0xFF4E5661),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    letterSpacing = 0.sp
                )
                UiCopyPreviewGuardRow("服务协议", "有限高度 LazyColumn")
                UiCopyPreviewGuardRow("隐私政策", "有限高度滚动正文")
            }
        }
    }
}

@Composable
private fun UiCopyPreviewLoginButton(
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Surface(
        color = if (primary) Color(0xFF111111) else Color.White,
        shape = RoundedCornerShape(12.dp),
        border = if (primary) null else BorderStroke(0.8.dp, Color(0xFFD3D7DE)),
        modifier = modifier.height(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = if (primary) Color.White else Color(0xFF111111),
                fontSize = if (primary) 16.sp else 15.sp,
                fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                letterSpacing = 0.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

@Composable
private fun UiCopyPreviewLoginField(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.8.dp, Color(0xFF777B82)),
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            Text(
                text = text,
                color = Color(0xFF8D929B),
                fontSize = 14.sp,
                letterSpacing = 0.sp
            )
        }
    }
}

@Composable
private fun UiCopyPreviewAgreementCheckbox(checked: Boolean) {
    val borderColor = if (checked) Color(0xFF111111) else Color(0xFF747682)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(48.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(23.dp)
                .clip(RoundedCornerShape(4.dp))
                .border(BorderStroke(1.6.dp, borderColor), RoundedCornerShape(4.dp))
        ) {
            if (checked) {
                Canvas(modifier = Modifier.size(14.dp)) {
                    val strokeWidth = 2.4.dp.toPx()
                    drawLine(
                        color = Color(0xFF111111),
                        start = Offset(size.width * 0.16f, size.height * 0.52f),
                        end = Offset(size.width * 0.40f, size.height * 0.76f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0xFF111111),
                        start = Offset(size.width * 0.40f, size.height * 0.76f),
                        end = Offset(size.width * 0.86f, size.height * 0.24f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
private fun UiCopyPreviewRemoteSnapshotFallback() {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.8.dp, Color(0xFFE4E6EA)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            UiCopyPreviewGuardRow("远端 snapshot 成功", "以平台最近 30 轮为真")
            UiCopyPreviewGuardRow("远端 snapshot 失败", "普通本地历史不回灌")
            UiCopyPreviewGuardRow("pending / 失败态", "保留恢复入口")
        }
    }
}

@Composable
private fun UiCopyPreviewClearHistoryHydrateGuard() {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(0.8.dp, Color(0xFFE4E6EA)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            UiCopyPreviewGuardRow("用户确认删除", "清 UI / 草稿 / 本地快照")
            UiCopyPreviewGuardRow("clear epoch +1", "标记当前 clean-state")
            UiCopyPreviewGuardRow("旧 hydrate 晚回来", "epoch 不匹配，丢弃")
        }
    }
}

@Composable
private fun UiCopyPreviewGuardRow(
    title: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color(0xFF17191C),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = Color(0xFF666A72),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.2f)
        )
    }
}

@Composable
private fun UiCopyPreviewImageTopChrome() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(138.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xE6000000))
    ) {
        Text(
            text = "1/4",
            color = Color.White,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(top = 24.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0x66111111))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(top = 18.dp, end = 20.dp)
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0x99111111)),
            contentAlignment = Alignment.Center
        ) {
            UserMessagePreviewCloseIcon(tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun UiCopyPreviewMembershipSheetSafeArea() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFE7E9ED))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(Color(0xFFD2D6DD))
        )
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(184.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MembershipCenterHeaderPreview(userId = IdManager.getUserId())
                MembershipQuotaSummary(
                    entitlement = SessionApi.EntitlementSnapshot(
                        tier = "plus",
                        tierExpireAt = uiCopyPreviewExpireAtMs(daysFromNow = 18),
                        dailyRemaining = 16
                    ),
                    loadState = MembershipLoadState.Loaded
                )
            }
        }
    }
}

@Composable
private fun UiCopyPreviewCleanStateFirstLaunch() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(0.8.dp, Color(0xFFE4E6EA)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = WELCOME_EMPTY_STATE_TEXT,
                color = Color(0xFF202124),
                fontSize = 22.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 22.dp)
            )
        }
        UiCopyPreviewComposerShell(placeholder = COMPOSER_DEFAULT_PLACEHOLDER_TEXT)
    }
}

@Composable
private fun UiCopyPreviewCleanStateFirstSend() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SelectableRenderedUserMessageBubble(
            content = "刚清数据后，第一次发送一条短问题",
            textSelectionColors = TextSelectionColors(
                handleColor = CHAT_SELECTION_HANDLE_COLOR,
                backgroundColor = CHAT_SELECTION_BACKGROUND_COLOR
            ),
            textToolbar = LocalTextToolbar.current,
            selectionResetKey = 0,
            userBubbleMaxWidth = 260.dp,
            userBubbleColor = Color(0xFF050505),
            userBubbleBorderColor = Color(0xFF050505)
        )
        ChatStreamingRenderer(
            content = "",
            renderMode = StreamingRenderMode.Waiting,
            showWaitingBall = true,
            selectionEnabled = false,
            showDisclaimer = false,
            onStreamingContentBoundsChanged = null,
            modifier = Modifier.fillMaxWidth()
        )
        UiCopyPreviewComposerShell(placeholder = COMPOSER_DEFAULT_PLACEHOLDER_TEXT)
    }
}

@Composable
private fun UiCopyPreviewComposerShell(placeholder: String) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(30.dp),
        shadowElevation = 6.dp,
        border = BorderStroke(0.6.dp, Color(0xFFE6E8EC)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = placeholder,
            color = Color(0xFFB7BAC1),
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
        )
    }
}

@Composable
private fun UiCopyPreviewMembershipSummary(
    entitlement: SessionApi.EntitlementSnapshot?,
    loadState: MembershipLoadState
) {
    MembershipQuotaSummary(
        entitlement = entitlement,
        loadState = loadState
    )
}

@Composable
private fun UiCopyPreviewMembershipSummaryWithPlans(
    entitlement: SessionApi.EntitlementSnapshot,
    loadState: MembershipLoadState,
    activeTier: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MembershipQuotaSummary(
            entitlement = entitlement,
            loadState = loadState
        )
        MembershipPlanSectionPreview(
            activeTier = activeTier,
            upgradeRemaining = entitlement.upgradeRemaining ?: 0,
            topupRemaining = entitlement.topupRemaining ?: 0
        )
    }
}

private fun uiCopyPreviewExpireAtMs(daysFromNow: Long): Long =
    System.currentTimeMillis() + daysFromNow * 24L * 60L * 60L * 1000L

@Composable
private fun UiCopyPreviewDisclaimer() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = AI_DISCLAIMER_TEXT,
            style = assistantDisclaimerTextStyle(),
            textAlign = TextAlign.Start
        )
    }
}

@Composable
private fun UiCopyPreviewHint(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xEE111111),
            contentColor = Color.White,
            border = BorderStroke(0.8.dp, Color.Black),
            shadowElevation = 1.2.dp,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun UiCopyPreviewAttachmentSheet(
    limitReached: Boolean,
    supportingHintText: String? = COMPOSER_ATTACHMENT_SHOOTING_HINT_TEXT
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFE7E9ED))
    ) {
        ComposerAttachmentBottomSheet(
            visible = true,
            limitReached = limitReached,
            limitHintText = COMPOSER_IMAGE_COUNT_HINT,
            supportingHintText = supportingHintText,
            modifier = Modifier.fillMaxSize(),
            onDismiss = {},
            onCameraClick = {},
            onPhotoClick = {}
        )
    }
}

@Composable
private fun UiCopyPreviewInputActionMenu(labels: List<String>) {
    Surface(
        color = Color(0xFF111111),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            labels.forEachIndexed { index, label ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(16.dp)
                            .background(Color.White.copy(alpha = 0.16f))
                    )
                }
                MessageActionMenuButton(
                    label = label,
                    minWidth = 64.dp,
                    horizontalPadding = 14.dp,
                    onClick = {}
                )
            }
        }
    }
}

@Composable
private fun UiCopyPreviewComposerImageBadge() {
    Box(
        modifier = Modifier
            .size(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF0F1F3))
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(5.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xAA111111))
                .padding(horizontal = 5.dp, vertical = 1.dp)
        ) {
            Text(
                text = "1",
                color = Color.White,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun UiCopyPreviewImagePageIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xE6000000)),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            text = "1/4",
            color = Color.White,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .padding(top = 44.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0x66111111))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun UiCopyPreviewExpiredImagePlaceholder() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(86.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFF0F1F3)),
            contentAlignment = Alignment.Center
        ) {
            UserMessageExpiredImagePlaceholder()
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(96.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF0F1F3))
                .border(1.dp, Color(0xFFE1E3E7), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = IMAGE_EXPIRED_PREVIEW_TEXT,
                color = Color(0xFF5F646D),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 18.dp)
            )
        }
    }
}

@Composable
private fun UiCopyPreviewPlainText(lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.forEach { line ->
            Text(
                text = line,
                color = Color(0xFF17191C),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun UiCopyPreviewLargeFont(content: @Composable () -> Unit) {
    val currentDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = currentDensity.density,
            fontScale = 1.6f
        )
    ) {
        content()
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SelectableRenderedUserMessageBubble(
    content: String,
    textSelectionColors: TextSelectionColors,
    textToolbar: TextToolbar,
    selectionResetKey: Int,
    userBubbleMaxWidth: Dp,
    userBubbleColor: Color,
    userBubbleBorderColor: Color,
    onBubbleBoundsChanged: (Rect?) -> Unit = {}
) {
    val bubbleShape = RoundedCornerShape(20.dp)
    val renderedContent = remember(content) { buildPlainLinkedAnnotatedString(content, linkColor = Color.White) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .wrapContentWidth(Alignment.End)
                .widthIn(max = userBubbleMaxWidth)
                .shadow(
                    elevation = 1.dp,
                    shape = bubbleShape,
                    ambientColor = Color(0x12000000),
                    spotColor = Color(0x12000000)
                )
                .clip(bubbleShape)
                .background(userBubbleColor)
                .border(BorderStroke(1.dp, userBubbleBorderColor), bubbleShape)
                .onGloballyPositioned { coordinates ->
                    onBubbleBoundsChanged(coordinates.boundsInWindow())
                }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            CompositionLocalProvider(
                LocalTextSelectionColors provides textSelectionColors,
                LocalTextToolbar provides textToolbar
            ) {
                key(selectionResetKey) {
                    SelectionContainer(
                        modifier = Modifier.wrapContentWidth(Alignment.Start)
                    ) {
                        Text(
                            text = renderedContent,
                            modifier = Modifier.wrapContentWidth(Alignment.Start),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageStatusFooter(
    statusText: String,
    actionText: String?,
    alignEnd: Boolean,
    onActionClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = Color(0xEE111111),
            contentColor = Color.White,
            border = BorderStroke(0.8.dp, Color.Black),
            shadowElevation = 1.2.dp,
            modifier = Modifier
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onActionClick
                )
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = 34.dp)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statusText,
                    fontSize = 13.sp,
                    color = Color.White
                )
                if (actionText != null) {
                    Text(
                        text = " · ",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.42f)
                    )
                    Text(
                        text = "点击$actionText",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
