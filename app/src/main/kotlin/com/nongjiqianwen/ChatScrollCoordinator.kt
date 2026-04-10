package com.nongjiqianwen

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
    val resumeAutoFollowArmed: MutableState<Boolean>,
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
    val streamBottomFollowActive = remember { mutableStateOf(false) }
    val resumeAutoFollowArmed = remember(chatScopeId) { mutableStateOf(false) }
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
            streamBottomFollowActive = streamBottomFollowActive,
            resumeAutoFollowArmed = resumeAutoFollowArmed,
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

internal suspend fun scrollRecyclerToBottom(
    recyclerView: RecyclerView?,
    layoutManager: androidx.recyclerview.widget.LinearLayoutManager?,
    lastIndex: Int,
    animated: Boolean,
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
            activeLayoutManager.scrollToPositionWithOffset(
                lastIndex,
                activeRecyclerView.paddingBottom
            )
            activeRecyclerView.post {
                endProgrammaticScroll()
            }
        }
    } catch (_: Throwable) {
        endProgrammaticScroll()
    }
}

internal suspend fun alignRecyclerLastMessageToBottomTarget(
    recyclerView: RecyclerView?,
    currentBottomAlignDeltaPx: () -> Int,
    beginProgrammaticScroll: () -> Unit,
    endProgrammaticScroll: () -> Unit
) {
    val activeRecyclerView = recyclerView ?: return
    val alignDeltaPx = currentBottomAlignDeltaPx()
    if (alignDeltaPx == 0) return
    beginProgrammaticScroll()
    try {
        activeRecyclerView.scrollBy(0, -alignDeltaPx)
    } finally {
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
    resumeAutoFollowArmedState: MutableState<Boolean>,
    isStreamingReadyForAutoFollow: () -> Boolean,
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
                resumeAutoFollowArmedState.value = false
            }
        }

        RecyclerView.SCROLL_STATE_IDLE -> {
            userInteractingState.value = false
            if (
                isStreaming &&
                hasStreamingItem &&
                scrollModeState.value == ScrollMode.UserBrowsing &&
                resumeAutoFollowArmedState.value &&
                isStreamingReadyForAutoFollow()
            ) {
                scrollModeState.value = ScrollMode.AutoFollow
                resumeAutoFollowArmedState.value = false
            }
        }
    }
}

internal fun handleRecyclerScrolledWhileBrowsing(
    dy: Int,
    programmaticScroll: Boolean,
    isStreaming: Boolean,
    scrollMode: ScrollMode,
    resumeAutoFollowArmedState: MutableState<Boolean>
) {
    if (programmaticScroll || !isStreaming || scrollMode != ScrollMode.UserBrowsing) return
    when {
        dy > 0 -> resumeAutoFollowArmedState.value = true
        dy < 0 -> resumeAutoFollowArmedState.value = false
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
    runtime.resumeAutoFollowArmed.value = false
}

internal fun resetScrollRuntimeAfterStreamingStop(
    runtime: ChatScrollRuntimeState,
    offerFinalBottomSnap: Boolean
) {
    runtime.streamingContentBottomPx.intValue = -1
    runtime.streamBottomFollowActive.value = false
    runtime.scrollMode.value = ScrollMode.Idle
    runtime.userInteracting.value = false
    runtime.resumeAutoFollowArmed.value = false
    runtime.pendingFinalBottomSnap.value = offerFinalBottomSnap
}

internal fun resumeScrollRuntimeForStreamingRecovery(
    runtime: ChatScrollRuntimeState
) {
    runtime.scrollMode.value = ScrollMode.AutoFollow
    runtime.userInteracting.value = false
    runtime.resumeAutoFollowArmed.value = false
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
            val contentBottom = currentStreamingContentBottomPx()
            val activeScrollMode = scrollModeState.value
            if (
                activeScrollMode == ScrollMode.UserBrowsing ||
                recyclerScrollInProgress ||
                userInteractingState.value ||
                contentBottom <= 0
            ) {
                streamBottomFollowActiveState.value = false
                return@LaunchedEffect
            }
            if (activeScrollMode == ScrollMode.Idle) {
                if (currentStreamingOverflowDelta() > 0) {
                    snapStreamingToWorkline()
                }
                if (streamingMessageContent.isNotBlank() && isStreamingReadyForAutoFollow()) {
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
                return@LaunchedEffect
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
        repeat(2) { withFrameNanos { } }
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
        repeat(3) { withFrameNanos { } }
        if (currentLastMessageContentBottomPx() > 0 && !isWithinBottomTolerance()) {
            scrollToBottom(false)
        }
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

internal fun deriveStreamingRevealMode(
    isStreaming: Boolean,
    scrollMode: ScrollMode,
    userInteracting: Boolean,
    streamBottomFollowActive: Boolean,
    streamingTailBottomPx: Int,
    worklineBottomPx: Int,
    assistantLineStepPx: Int,
    currentMode: StreamingRevealMode
): StreamingRevealMode {
    if (!isStreaming) return StreamingRevealMode.Free
    if (scrollMode == ScrollMode.UserBrowsing || userInteracting) {
        return StreamingRevealMode.Free
    }
    if (
        scrollMode == ScrollMode.AutoFollow &&
        streamingTailBottomPx > 0 &&
        worklineBottomPx > 0 &&
        streamingTailBottomPx >= (worklineBottomPx - assistantLineStepPx.coerceAtLeast(8))
    ) {
        return StreamingRevealMode.Conservative
    }
    if (streamBottomFollowActive) {
        return StreamingRevealMode.Conservative
    }
    val overflowPx = if (streamingTailBottomPx > 0 && worklineBottomPx > 0) {
        (streamingTailBottomPx - worklineBottomPx).coerceAtLeast(0)
    } else {
        0
    }
    if (overflowPx <= 0) return StreamingRevealMode.Free
    val lockThresholdPx = (assistantLineStepPx * 0.16f).roundToInt().coerceAtLeast(6)
    val unlockThresholdPx = (assistantLineStepPx * 0.06f).roundToInt().coerceAtLeast(3)
    return when (currentMode) {
        StreamingRevealMode.Conservative -> {
            if (overflowPx > unlockThresholdPx) StreamingRevealMode.Conservative else StreamingRevealMode.Free
        }
        StreamingRevealMode.Free -> {
            if (overflowPx > lockThresholdPx) StreamingRevealMode.Conservative else StreamingRevealMode.Free
        }
    }
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
