package com.nongjiqianwen

import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.math.roundToInt
import kotlin.coroutines.resume

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
    val sendStartAnchorActive: MutableState<Boolean>,
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
    val scrollMode = remember { mutableStateOf(ScrollMode.Idle) }
    val userInteracting = remember { mutableStateOf(false) }
    val programmaticScroll = remember { mutableStateOf(false) }
    val streamingContentBottomPx = remember { mutableIntStateOf(-1) }
    val sendStartAnchorActive = remember(chatScopeId) { mutableStateOf(false) }
    val streamBottomFollowActive = remember { mutableStateOf(false) }
    val initialBottomSnapDone = remember(chatScopeId) { mutableStateOf(false) }
    val jumpButtonPulseVisible = remember { mutableStateOf(false) }
    val pendingFinalBottomSnap = remember { mutableStateOf(false) }
    val suppressJumpButtonForImeTransition = remember { mutableStateOf(false) }
    val suppressJumpButtonForLifecycleResume = remember { mutableStateOf(false) }
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
            sendStartAnchorActive = sendStartAnchorActive,
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

internal data class ChatRecyclerMetrics(
    val scrollInProgress: Boolean,
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int
)

internal fun readRecyclerMetrics(recyclerView: RecyclerView): ChatRecyclerMetrics {
    val layoutManager = recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
    val firstVisible = layoutManager?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION
    return ChatRecyclerMetrics(
        scrollInProgress = recyclerView.scrollState != RecyclerView.SCROLL_STATE_IDLE,
        firstVisibleItemIndex = firstVisible.coerceAtLeast(0),
        firstVisibleItemScrollOffset = if (firstVisible != RecyclerView.NO_POSITION) {
            -(layoutManager?.findViewByPosition(firstVisible)?.top ?: 0)
        } else {
            0
        }
    )
}

internal fun beginProgrammaticRecyclerScroll(
    programmaticScrollState: MutableState<Boolean>
) {
    programmaticScrollState.value = true
}

internal fun endProgrammaticRecyclerScroll(
    programmaticScrollState: MutableState<Boolean>,
    recyclerView: RecyclerView?,
    refreshRecyclerMetrics: (RecyclerView) -> Unit
) {
    programmaticScrollState.value = false
    recyclerView?.let(refreshRecyclerMetrics)
}

private suspend fun awaitRecyclerPreDrawAlignment(
    recyclerView: RecyclerView,
    align: () -> Boolean
) {
    suspendCancellableCoroutine<Unit> { continuation ->
        val viewTreeObserver = recyclerView.viewTreeObserver
        if (!viewTreeObserver.isAlive) {
            continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (viewTreeObserver.isAlive) {
                    viewTreeObserver.removeOnPreDrawListener(this)
                }
                val shouldDraw = align()
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
                return shouldDraw
            }
        }
        continuation.invokeOnCancellation {
            if (viewTreeObserver.isAlive) {
                viewTreeObserver.removeOnPreDrawListener(listener)
            }
        }
        viewTreeObserver.addOnPreDrawListener(listener)
    }
}

internal suspend fun scrollRecyclerToBottom(
    recyclerView: RecyclerView?,
    layoutManager: androidx.recyclerview.widget.LinearLayoutManager?,
    lastIndex: Int,
    animated: Boolean,
    currentBottomAlignDeltaPx: () -> Int,
    beginProgrammaticScroll: () -> Unit,
    endProgrammaticScroll: () -> Unit
) {
    val activeRecyclerView = recyclerView ?: return
    val activeLayoutManager = layoutManager ?: return
    if (lastIndex < 0) return
    beginProgrammaticScroll()
    try {
        if (animated) {
            activeRecyclerView.smoothScrollToPosition(lastIndex)
        } else {
            val laidOutLastItem = activeLayoutManager.findViewByPosition(lastIndex)
            if (laidOutLastItem != null) {
                val alignDeltaPx = currentBottomAlignDeltaPx()
                if (alignDeltaPx != 0) {
                    activeRecyclerView.scrollBy(0, -alignDeltaPx)
                }
                endProgrammaticScroll()
            } else {
                activeLayoutManager.scrollToPosition(lastIndex)
                awaitRecyclerPreDrawAlignment(activeRecyclerView) {
                    val alignDeltaPx = currentBottomAlignDeltaPx()
                    if (alignDeltaPx != 0) {
                        activeRecyclerView.scrollBy(0, -alignDeltaPx)
                        false
                    } else {
                        true
                    }
                }
                endProgrammaticScroll()
            }
        }
    } catch (_: Throwable) {
        endProgrammaticScroll()
    }
}

internal suspend fun snapRecyclerStreamingToWorkline(
    recyclerView: RecyclerView?,
    currentStreamingAlignDeltaPx: () -> Int,
    beginProgrammaticScroll: () -> Unit,
    endProgrammaticScroll: () -> Unit
) {
    val activeRecyclerView = recyclerView ?: return
    val deltaPx = currentStreamingAlignDeltaPx()
    if (deltaPx == 0) return
    beginProgrammaticScroll()
    try {
        activeRecyclerView.scrollBy(0, -deltaPx)
    } finally {
        endProgrammaticScroll()
    }
}

internal fun handleRecyclerScrollStateChanged(
    newState: Int,
    programmaticScroll: Boolean,
    isStreaming: Boolean,
    hasStreamingItem: Boolean,
    scrollModeState: MutableState<ScrollMode>,
    userInteractingState: MutableState<Boolean>,
    streamBottomFollowActiveState: MutableState<Boolean>,
    endProgrammaticScroll: () -> Unit
) {
    if (programmaticScroll) {
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
            endProgrammaticScroll()
        }
        return
    }
    when (newState) {
        RecyclerView.SCROLL_STATE_DRAGGING,
        RecyclerView.SCROLL_STATE_SETTLING -> {
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

        RecyclerView.SCROLL_STATE_IDLE -> {
            userInteractingState.value = false
        }
    }
}

internal fun handleRecyclerScrolledWhileBrowsing(
    dy: Int,
    programmaticScroll: Boolean,
    isStreaming: Boolean,
    scrollMode: ScrollMode
) {
    if (programmaticScroll || !isStreaming || scrollMode != ScrollMode.UserBrowsing) return
    if (dy == 0) return
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
    runtime.sendStartAnchorActive.value = false
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
    runtime.sendStartAnchorActive.value = false
    runtime.streamBottomFollowActive.value = false
    runtime.scrollMode.value = ScrollMode.Idle
    runtime.userInteracting.value = false
    runtime.pendingFinalBottomSnap.value = offerFinalBottomSnap
}

internal fun resumeScrollRuntimeForStreamingRecovery(
    runtime: ChatScrollRuntimeState
) {
    runtime.sendStartAnchorActive.value = false
    runtime.scrollMode.value = ScrollMode.AutoFollow
    runtime.userInteracting.value = false
}

@Composable
internal fun BindRecyclerChatScrollEffects(
    isStreaming: Boolean,
    hasStreamingItem: Boolean,
    streamingMessageContent: String,
    recyclerScrollInProgress: Boolean,
    startupHydrationBarrierSatisfied: Boolean,
    startupLayoutReady: Boolean,
    messagesCount: Int,
    scrollModeState: MutableState<ScrollMode>,
    userInteractingState: MutableState<Boolean>,
    streamBottomFollowActiveState: MutableState<Boolean>,
    sendStartAnchorActiveState: MutableState<Boolean>,
    pendingFinalBottomSnapState: MutableState<Boolean>,
    initialBottomSnapDoneState: MutableState<Boolean>,
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
    val initialBottomSnapDone = initialBottomSnapDoneState.value

    LaunchedEffect(
        isStreaming,
        hasStreamingItem,
        streamingMessageContent,
        scrollMode,
        userInteracting,
        recyclerScrollInProgress,
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
                    !recyclerScrollInProgress &&
                    !userInteractingState.value &&
                    isStreamingReadyForAutoFollow()
                ) {
                    scrollModeState.value = ScrollMode.AutoFollow
                    continue
                }
                streamBottomFollowActiveState.value = false
                continue
            }
            if (recyclerScrollInProgress || userInteractingState.value) {
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
                if (sendStartAnchorActiveState.value) {
                    if (isStreamingReadyForAutoFollow()) {
                        sendStartAnchorActiveState.value = false
                    }
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
                        !recyclerScrollInProgress
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

    LaunchedEffect(
        startupLayoutReady,
        startupHydrationBarrierSatisfied,
        messagesCount,
        hasStreamingItem,
        isStreaming,
        initialBottomSnapDone,
        currentLastMessageContentBottomPx(),
        isWithinBottomTolerance()
    ) {
        if (initialBottomSnapDone) return@LaunchedEffect
        if (!startupHydrationBarrierSatisfied || !startupLayoutReady) return@LaunchedEffect
        if (messagesCount == 0 && !hasStreamingItem) {
            initialBottomSnapDoneState.value = true
            return@LaunchedEffect
        }
        if (messagesCount == 0 || isStreaming || hasStreamingItem) return@LaunchedEffect
        scrollToBottom(false)
        var lastContentBottom = currentLastMessageContentBottomPx()
        repeat(3) {
            if (lastContentBottom > 0) return@repeat
            withFrameNanos { }
            lastContentBottom = currentLastMessageContentBottomPx()
        }
        if (lastContentBottom > 0 && !isWithinBottomTolerance()) {
            scrollToBottom(false)
        }
        repeat(1) { withFrameNanos { } }
        initialBottomSnapDoneState.value = true
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
