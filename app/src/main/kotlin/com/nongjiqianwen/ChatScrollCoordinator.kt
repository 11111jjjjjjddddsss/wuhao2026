package com.nongjiqianwen

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

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
    val jumpButtonPulseVisible: MutableState<Boolean>,
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
    val jumpButtonPulseVisible = remember(chatScopeId) { mutableStateOf(false) }
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
            jumpButtonPulseVisible = jumpButtonPulseVisible,
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

internal suspend fun scrollChatListToBottom(
    listState: LazyListState?,
    lastIndex: Int,
    animated: Boolean,
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
    } catch (_: Throwable) {
        endProgrammaticScroll()
        return
    }
    endProgrammaticScroll()
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
        userDragging -> {
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

        scrollInProgress -> {
            val userOwnedScrollInProgress =
                userInteractingState.value || scrollModeState.value == ScrollMode.UserBrowsing
            if (userOwnedScrollInProgress) {
                userInteractingState.value = true
                streamBottomFollowActiveState.value = false
            } else {
                userInteractingState.value = false
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
    scrollToBottom(false)
}

internal fun prepareScrollRuntimeForStreamingStart(
    runtime: ChatScrollRuntimeState
) {
    runtime.streamingContentBottomPx.intValue = -1
    runtime.streamBottomFollowActive.value = false
    runtime.scrollMode.value = ScrollMode.Idle
    runtime.userInteracting.value = false
}

internal fun resetScrollRuntimeAfterStreamingStop(
    runtime: ChatScrollRuntimeState
) {
    runtime.streamingContentBottomPx.intValue = -1
    runtime.streamBottomFollowActive.value = false
    runtime.scrollMode.value = ScrollMode.Idle
    runtime.userInteracting.value = false
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
    scrollModeState: MutableState<ScrollMode>,
    userInteractingState: MutableState<Boolean>,
    streamBottomFollowActiveState: MutableState<Boolean>,
    currentStreamingContentBottomPx: () -> Int,
    isNearStreamingWorkline: () -> Boolean
) {
    val scrollMode = scrollModeState.value
    val userInteracting = userInteractingState.value

    LaunchedEffect(
        isStreaming,
        hasStreamingItem,
        streamingMessageContent,
        scrollMode,
        userInteracting,
        listScrollInProgress,
        currentStreamingContentBottomPx(),
        isNearStreamingWorkline()
    ) {
        if (!isStreaming || !hasStreamingItem) {
            streamBottomFollowActiveState.value = false
            return@LaunchedEffect
        }
        val activeScrollMode = scrollModeState.value
        val contentBottom = currentStreamingContentBottomPx()
        if (activeScrollMode == ScrollMode.UserBrowsing) {
            if (
                !listScrollInProgress &&
                !userInteractingState.value &&
                isNearStreamingWorkline()
            ) {
                scrollModeState.value = ScrollMode.AutoFollow
            }
            streamBottomFollowActiveState.value = false
            return@LaunchedEffect
        }
        if (listScrollInProgress || userInteractingState.value) {
            streamBottomFollowActiveState.value = false
            return@LaunchedEffect
        }
        if (contentBottom <= 0 || streamingMessageContent.isBlank()) {
            streamBottomFollowActiveState.value = false
            return@LaunchedEffect
        }
        // Reverse layout already keeps the newest assistant host pinned at the
        // visual bottom while the user is not browsing. During streaming we only
        // need to manage ownership, not run the old overflow-driven scrollBy chain.
        if (scrollModeState.value != ScrollMode.AutoFollow) {
            scrollModeState.value = ScrollMode.AutoFollow
        }
        streamBottomFollowActiveState.value = false
    }
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
