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
        listState.scrollBy(alignDeltaPx.toFloat())
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

internal fun handleChatListScrollStateChanged(
    scrollInProgress: Boolean,
    userDragging: Boolean,
    programmaticScroll: Boolean,
    isStreaming: Boolean,
    hasStreamingItem: Boolean,
    scrollModeState: MutableState<ScrollMode>,
    userInteractingState: MutableState<Boolean>,
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
            }
        }

        scrollInProgress -> {
            val userOwnedScrollInProgress =
                userInteractingState.value || scrollModeState.value == ScrollMode.UserBrowsing
            if (userOwnedScrollInProgress) {
                userInteractingState.value = true
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
    runtime.scrollMode.value = ScrollMode.Idle
    runtime.userInteracting.value = false
}

internal fun resetScrollRuntimeAfterStreamingStop(
    runtime: ChatScrollRuntimeState
) {
    runtime.streamingContentBottomPx.intValue = -1
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
    isComposerSettling: Boolean,
    sendStartAnchorActiveState: MutableState<Boolean>,
    scrollModeState: MutableState<ScrollMode>,
    userInteractingState: MutableState<Boolean>,
    currentStreamingContentBottomPx: () -> Int,
    currentStreamingLegalBottomPx: () -> Int,
    isNearStreamingWorkline: () -> Boolean,
    isAtStreamingWorklineStrict: () -> Boolean
) {
    val scrollMode = scrollModeState.value
    val userInteracting = userInteractingState.value
    val sendStartAnchorReleaseArmedState = remember(sendStartAnchorActiveState) {
        mutableStateOf(false)
    }

    LaunchedEffect(
        isStreaming,
        hasStreamingItem,
        streamingMessageContent,
        sendStartAnchorActiveState.value,
        scrollMode,
        userInteracting,
        listScrollInProgress,
        isComposerSettling,
        currentStreamingContentBottomPx(),
        currentStreamingLegalBottomPx(),
        isNearStreamingWorkline(),
        isAtStreamingWorklineStrict()
    ) {
        if (!isStreaming || !hasStreamingItem) {
            sendStartAnchorActiveState.value = false
            sendStartAnchorReleaseArmedState.value = false
            return@LaunchedEffect
        }
        while (isActive && isStreaming && hasStreamingItem) {
            withFrameNanos { }
            val activeScrollMode = scrollModeState.value
            val contentBottom = currentStreamingContentBottomPx()
            val legalBottom = currentStreamingLegalBottomPx()
            if (sendStartAnchorActiveState.value) {
                if (activeScrollMode == ScrollMode.UserBrowsing || userInteractingState.value) {
                    sendStartAnchorActiveState.value = false
                    sendStartAnchorReleaseArmedState.value = false
                    continue
                }
                val shouldReleaseStartAnchorProtection =
                    contentBottom > 0 &&
                        legalBottom > 0 &&
                        isNearStreamingWorkline() &&
                        !listScrollInProgress &&
                        !isComposerSettling
                if (shouldReleaseStartAnchorProtection) {
                    if (sendStartAnchorReleaseArmedState.value) {
                        sendStartAnchorActiveState.value = false
                        sendStartAnchorReleaseArmedState.value = false
                    } else {
                        sendStartAnchorReleaseArmedState.value = true
                    }
                } else {
                    sendStartAnchorReleaseArmedState.value = false
                }
                continue
            }
            sendStartAnchorReleaseArmedState.value = false
            if (activeScrollMode == ScrollMode.UserBrowsing) {
                if (
                    !listScrollInProgress &&
                    !userInteractingState.value &&
                    isAtStreamingWorklineStrict()
                ) {
                    scrollModeState.value = ScrollMode.AutoFollow
                    continue
                }
                continue
            }
            if (listScrollInProgress || userInteractingState.value) {
                continue
            }
            if (contentBottom <= 0 || legalBottom <= 0 || streamingMessageContent.isBlank()) {
                continue
            }
            if (activeScrollMode != ScrollMode.AutoFollow) {
                scrollModeState.value = ScrollMode.AutoFollow
            }
            continue
        }
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
