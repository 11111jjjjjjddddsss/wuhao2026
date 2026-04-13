package com.nongjiqianwen

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

internal enum class ScrollMode {
    Idle,
    AutoFollow,
    UserBrowsing
}

internal data class ChatScrollRuntimeState(
    val scrollMode: MutableState<ScrollMode>,
    val userInteracting: MutableState<Boolean>,
    val programmaticScroll: MutableState<Boolean>,
    val streamingContentBottomPx: MutableIntState,
    val streamBottomFollowActive: MutableState<Boolean>,
    val initialBottomSnapDone: MutableState<Boolean>,
    val jumpButtonPulseVisible: MutableState<Boolean>,
    val pendingFinalBottomSnap: MutableState<Boolean>,
    val suppressJumpButtonForImeTransition: MutableState<Boolean>,
    val suppressJumpButtonForLifecycleResume: MutableState<Boolean>,
    val bottomBarHeightPx: MutableIntState,
    val inputChromeRowHeightPx: MutableIntState
)

@Composable
internal fun rememberChatScrollRuntimeState(
    chatScopeId: String,
    startupBottomBarHeightEstimatePx: Int,
    startupInputChromeRowHeightEstimatePx: Int
): ChatScrollRuntimeState {
    val scrollMode = remember(chatScopeId) { mutableStateOf(ScrollMode.Idle) }
    val userInteracting = remember(chatScopeId) { mutableStateOf(false) }
    val programmaticScroll = remember(chatScopeId) { mutableStateOf(false) }
    val streamingContentBottomPx = remember(chatScopeId) { mutableIntStateOf(-1) }
    val streamBottomFollowActive = remember(chatScopeId) { mutableStateOf(false) }
    val initialBottomSnapDone = remember(chatScopeId) { mutableStateOf(false) }
    val jumpButtonPulseVisible = remember(chatScopeId) { mutableStateOf(false) }
    val pendingFinalBottomSnap = remember(chatScopeId) { mutableStateOf(false) }
    val suppressJumpButtonForImeTransition = remember(chatScopeId) { mutableStateOf(false) }
    val suppressJumpButtonForLifecycleResume = remember(chatScopeId) { mutableStateOf(false) }
    val bottomBarHeightPx = remember(chatScopeId, startupBottomBarHeightEstimatePx) {
        mutableIntStateOf(startupBottomBarHeightEstimatePx)
    }
    val inputChromeRowHeightPx = remember(chatScopeId, startupInputChromeRowHeightEstimatePx) {
        mutableIntStateOf(startupInputChromeRowHeightEstimatePx)
    }
    return remember(
        chatScopeId,
        startupBottomBarHeightEstimatePx,
        startupInputChromeRowHeightEstimatePx
    ) {
        ChatScrollRuntimeState(
            scrollMode = scrollMode,
            userInteracting = userInteracting,
            programmaticScroll = programmaticScroll,
            streamingContentBottomPx = streamingContentBottomPx,
            streamBottomFollowActive = streamBottomFollowActive,
            initialBottomSnapDone = initialBottomSnapDone,
            jumpButtonPulseVisible = jumpButtonPulseVisible,
            pendingFinalBottomSnap = pendingFinalBottomSnap,
            suppressJumpButtonForImeTransition = suppressJumpButtonForImeTransition,
            suppressJumpButtonForLifecycleResume = suppressJumpButtonForLifecycleResume,
            bottomBarHeightPx = bottomBarHeightPx,
            inputChromeRowHeightPx = inputChromeRowHeightPx
        )
    }
}

internal data class ChatListMetrics(
    val scrollInProgress: Boolean,
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int
)

internal fun readChatListMetrics(listState: LazyListState): ChatListMetrics {
    return ChatListMetrics(
        scrollInProgress = listState.isScrollInProgress,
        firstVisibleItemIndex = listState.firstVisibleItemIndex.coerceAtLeast(0),
        firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset.coerceAtLeast(0)
    )
}

internal fun beginProgrammaticChatListScroll(
    programmaticScrollState: MutableState<Boolean>
) {
    programmaticScrollState.value = true
}

internal fun endProgrammaticChatListScroll(
    programmaticScrollState: MutableState<Boolean>,
    listState: LazyListState?,
    refreshChatListMetrics: (LazyListState) -> Unit
) {
    programmaticScrollState.value = false
    listState?.let(refreshChatListMetrics)
}

private suspend fun alignChatListBottom(
    listState: LazyListState,
    currentLastMessageContentBottomPx: () -> Int,
    currentBottomAlignDeltaPx: () -> Int
) {
    repeat(8) {
        withFrameNanos { }
        if (currentLastMessageContentBottomPx() <= 0) return@repeat
        val alignDeltaPx = currentBottomAlignDeltaPx()
        if (alignDeltaPx == 0) return
        listState.scrollBy((-alignDeltaPx).toFloat())
    }
}

internal suspend fun scrollChatListToBottom(
    listState: LazyListState?,
    lastIndex: Int,
    animated: Boolean,
    currentLastMessageContentBottomPx: () -> Int,
    currentBottomAlignDeltaPx: () -> Int,
    beginProgrammaticScroll: () -> Unit,
    endProgrammaticScroll: () -> Unit
) {
    val activeListState = listState ?: return
    if (lastIndex < 0) return
    beginProgrammaticScroll()
    try {
        if (animated) {
            activeListState.animateScrollToItem(lastIndex)
        } else {
            activeListState.scrollToItem(lastIndex)
        }
        alignChatListBottom(
            listState = activeListState,
            currentLastMessageContentBottomPx = currentLastMessageContentBottomPx,
            currentBottomAlignDeltaPx = currentBottomAlignDeltaPx
        )
    } catch (_: Throwable) {
        endProgrammaticScroll()
        return
    }
    endProgrammaticScroll()
}

internal suspend fun snapChatListStreamingToWorkline(
    listState: LazyListState?,
    currentStreamingAlignDeltaPx: () -> Int,
    beginProgrammaticScroll: () -> Unit,
    endProgrammaticScroll: () -> Unit
) {
    val activeListState = listState ?: return
    val deltaPx = currentStreamingAlignDeltaPx()
    if (deltaPx == 0) return
    beginProgrammaticScroll()
    try {
        activeListState.scrollBy((-deltaPx).toFloat())
    } finally {
        endProgrammaticScroll()
    }
}

internal fun handleChatListScrollStateChanged(
    scrollInProgress: Boolean,
    userDragging: Boolean,
    programmaticScroll: Boolean,
    isStreaming: Boolean,
    hasStreamingItem: Boolean,
    scrollModeState: MutableState<ScrollMode>,
    userInteractingState: MutableState<Boolean>,
    streamBottomFollowActiveState: MutableState<Boolean>,
    endProgrammaticScroll: () -> Unit
) {
    if (programmaticScroll) {
        if (!scrollInProgress) {
            endProgrammaticScroll()
        }
        return
    }
    when {
        userDragging || scrollInProgress -> {
            userInteractingState.value = true
            if (
                isStreaming &&
                hasStreamingItem &&
                scrollModeState.value != ScrollMode.UserBrowsing
            ) {
                scrollModeState.value = ScrollMode.UserBrowsing
                streamBottomFollowActiveState.value = false
            }
        }

        else -> {
            userInteractingState.value = false
        }
    }
}

internal suspend fun performJumpToBottom(
    messagesCount: Int,
    hasStreamingItem: Boolean,
    isStreaming: Boolean,
    scrollModeState: MutableState<ScrollMode>,
    userInteractingState: MutableState<Boolean>,
    jumpButtonPulseVisibleState: MutableState<Boolean>,
    snapStreamingToWorkline: suspend () -> Unit,
    scrollToBottom: suspend (Boolean) -> Unit
) {
    if (messagesCount == 0 && !hasStreamingItem) return
    val jumpingIntoStreaming = isStreaming && hasStreamingItem
    scrollModeState.value = if (jumpingIntoStreaming) {
        ScrollMode.AutoFollow
    } else {
        ScrollMode.Idle
    }
    userInteractingState.value = false
    jumpButtonPulseVisibleState.value = false
    if (jumpingIntoStreaming) {
        snapStreamingToWorkline()
    } else {
        scrollToBottom(false)
    }
}

internal fun prepareScrollRuntimeForStreamingStart(
    runtime: ChatScrollRuntimeState
) {
    runtime.streamingContentBottomPx.intValue = -1
    runtime.streamBottomFollowActive.value = false
    runtime.pendingFinalBottomSnap.value = false
    runtime.scrollMode.value = ScrollMode.Idle
    runtime.userInteracting.value = false
}

internal fun resetScrollRuntimeAfterStreamingStop(
    runtime: ChatScrollRuntimeState,
    offerFinalBottomSnap: Boolean
) {
    runtime.streamingContentBottomPx.intValue = -1
    runtime.streamBottomFollowActive.value = false
    runtime.scrollMode.value = ScrollMode.Idle
    runtime.userInteracting.value = false
    runtime.pendingFinalBottomSnap.value = offerFinalBottomSnap
}

internal fun resumeScrollRuntimeForStreamingRecovery(
    runtime: ChatScrollRuntimeState
) {
    runtime.scrollMode.value = ScrollMode.AutoFollow
    runtime.userInteracting.value = false
}

@Composable
internal fun BindChatListScrollEffects(
    isStreaming: Boolean,
    hasStreamingItem: Boolean,
    streamingMessageContent: String,
    listScrollInProgress: Boolean,
    messagesCount: Int,
    scrollModeState: MutableState<ScrollMode>,
    userInteractingState: MutableState<Boolean>,
    streamBottomFollowActiveState: MutableState<Boolean>,
    pendingFinalBottomSnapState: MutableState<Boolean>,
    currentLastMessageContentBottomPx: () -> Int,
    currentStreamingContentBottomPx: () -> Int,
    currentStreamingLegalBottomPx: () -> Int,
    currentStreamingOverflowDelta: () -> Int,
    isWithinBottomTolerance: () -> Boolean,
    isStreamingReadyForAutoFollow: () -> Boolean,
    resolveStreamingFollowStepPx: (Int) -> Int,
    performStreamingFollowStep: suspend (Int) -> Unit,
    snapStreamingToWorkline: suspend () -> Unit,
    scrollToBottom: suspend (Boolean) -> Unit
) {
    val scrollMode = scrollModeState.value
    val userInteracting = userInteractingState.value
    val pendingFinalBottomSnap = pendingFinalBottomSnapState.value

    LaunchedEffect(
        isStreaming,
        hasStreamingItem,
        streamingMessageContent,
        scrollMode,
        userInteracting,
        listScrollInProgress,
        currentStreamingContentBottomPx(),
        currentStreamingLegalBottomPx()
    ) {
        if (!isStreaming || !hasStreamingItem) {
            streamBottomFollowActiveState.value = false
            return@LaunchedEffect
        }
        while (isActive && isStreaming && hasStreamingItem) {
            withFrameNanos { }
            val activeScrollMode = scrollModeState.value
            val contentBottom = currentStreamingContentBottomPx()
            if (activeScrollMode == ScrollMode.UserBrowsing) {
                if (
                    !listScrollInProgress &&
                    !userInteractingState.value &&
                    isStreamingReadyForAutoFollow()
                ) {
                    scrollModeState.value = ScrollMode.AutoFollow
                    continue
                }
                streamBottomFollowActiveState.value = false
                continue
            }
            if (listScrollInProgress || userInteractingState.value) {
                streamBottomFollowActiveState.value = false
                continue
            }
            if (contentBottom <= 0) {
                streamBottomFollowActiveState.value = false
                continue
            }
            if (activeScrollMode == ScrollMode.Idle) {
                if (streamingMessageContent.isBlank()) {
                    streamBottomFollowActiveState.value = false
                    continue
                }
                if (currentStreamingOverflowDelta() > 0 && isStreamingReadyForAutoFollow()) {
                    snapStreamingToWorkline()
                }
                if (isStreamingReadyForAutoFollow()) {
                    if (
                        scrollModeState.value == ScrollMode.Idle &&
                        !userInteractingState.value &&
                        !listScrollInProgress
                    ) {
                        scrollModeState.value = ScrollMode.AutoFollow
                    }
                }
                streamBottomFollowActiveState.value = false
                continue
            }
            if (streamingMessageContent.isBlank() || activeScrollMode != ScrollMode.AutoFollow) {
                streamBottomFollowActiveState.value = false
                continue
            }
            val overflow = currentStreamingOverflowDelta()
            val stepPx = resolveStreamingFollowStepPx(overflow)
            if (stepPx == 0) {
                streamBottomFollowActiveState.value = false
                continue
            }
            streamBottomFollowActiveState.value = true
            try {
                performStreamingFollowStep(stepPx)
            } finally {
                streamBottomFollowActiveState.value = false
            }
        }
    }

    LaunchedEffect(
        pendingFinalBottomSnap,
        messagesCount,
        isStreaming,
        scrollMode,
        currentLastMessageContentBottomPx(),
        isWithinBottomTolerance()
    ) {
        if (!pendingFinalBottomSnap || isStreaming || scrollMode == ScrollMode.UserBrowsing) {
            return@LaunchedEffect
        }
        if (currentLastMessageContentBottomPx() <= 0) {
            return@LaunchedEffect
        }
        if (isWithinBottomTolerance()) {
            pendingFinalBottomSnapState.value = false
            return@LaunchedEffect
        }
        scrollToBottom(false)
        if (isWithinBottomTolerance()) {
            pendingFinalBottomSnapState.value = false
        }
    }

}

internal fun currentStreamingOverflowDelta(
    contentBottom: Int,
    visibleBottom: Int
): Int {
    if (contentBottom <= 0 || visibleBottom <= 0) return 0
    return contentBottom - visibleBottom
}

internal fun resolveStreamingFollowStepPx(
    overflow: Int,
    assistantLineStepPx: Int
): Int {
    if (overflow <= 0) return 0
    val triggerThresholdPx = (assistantLineStepPx * 0.04f).roundToInt().coerceAtLeast(3)
    if (overflow < triggerThresholdPx) return 0
    return overflow
}

internal fun shouldShowStreamingScrollToBottomButton(
    isStreaming: Boolean,
    hasStreamingItem: Boolean,
    scrollMode: ScrollMode,
    nearWorkline: Boolean
): Boolean {
    return isStreaming &&
        hasStreamingItem &&
        scrollMode == ScrollMode.UserBrowsing &&
        !nearWorkline
}

internal fun shouldOfferFinalBottomSnap(
    scrollMode: ScrollMode
): Boolean {
    return scrollMode != ScrollMode.UserBrowsing
}

@Composable
internal fun BindJumpButtonPulseEffect(
    showStreamingJumpButton: Boolean,
    showStaticJumpButton: Boolean,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    jumpButtonPulseVisibleState: MutableState<Boolean>,
    autoHideMs: Long
) {
    LaunchedEffect(
        showStreamingJumpButton,
        showStaticJumpButton,
        firstVisibleItemIndex,
        firstVisibleItemScrollOffset
    ) {
        val shouldOfferJumpButton = showStreamingJumpButton || showStaticJumpButton
        if (!shouldOfferJumpButton) {
            jumpButtonPulseVisibleState.value = false
            return@LaunchedEffect
        }
        jumpButtonPulseVisibleState.value = true
        kotlinx.coroutines.delay(autoHideMs)
        if (showStreamingJumpButton || showStaticJumpButton) {
            jumpButtonPulseVisibleState.value = false
        }
    }
}
