package com.nongjiqianwen

import android.os.SystemClock
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class ScrollMode {
    Idle,
    AutoFollow,
    UserBrowsing
}

internal data class ChatScrollRuntimeState(
    val scrollMode: MutableState<ScrollMode>,
    val userInteracting: MutableState<Boolean>,
    val streamTick: MutableIntState,
    val sendTick: MutableIntState,
    val programmaticScroll: MutableState<Boolean>,
    val lastProgrammaticScrollMs: MutableState<Long>,
    val streamingContentBottomPx: MutableIntState,
    val streamBottomFollowActive: MutableState<Boolean>,
    val initialBottomSnapDone: MutableState<Boolean>,
    val jumpButtonPulseVisible: MutableState<Boolean>,
    val pendingFinalBottomSnap: MutableState<Boolean>,
    val restoreBottomAfterImeClose: MutableState<Boolean>,
    val suppressJumpButtonForImeTransition: MutableState<Boolean>,
    val restoreBottomAfterLifecycleResume: MutableState<Boolean>,
    val suppressJumpButtonForLifecycleResume: MutableState<Boolean>,
    val lifecycleResumeReady: MutableState<Boolean>,
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
    val streamTick = remember { mutableIntStateOf(0) }
    val sendTick = remember { mutableIntStateOf(0) }
    val programmaticScroll = remember { mutableStateOf(false) }
    val lastProgrammaticScrollMs = remember { mutableStateOf(0L) }
    val streamingContentBottomPx = remember { mutableIntStateOf(-1) }
    val streamBottomFollowActive = remember { mutableStateOf(false) }
    val initialBottomSnapDone = remember(chatScopeId) { mutableStateOf(false) }
    val jumpButtonPulseVisible = remember { mutableStateOf(false) }
    val pendingFinalBottomSnap = remember { mutableStateOf(false) }
    val restoreBottomAfterImeClose = remember { mutableStateOf(false) }
    val suppressJumpButtonForImeTransition = remember { mutableStateOf(false) }
    val restoreBottomAfterLifecycleResume = remember { mutableStateOf(false) }
    val suppressJumpButtonForLifecycleResume = remember { mutableStateOf(false) }
    val lifecycleResumeReady = remember { mutableStateOf(false) }
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
            streamTick = streamTick,
            sendTick = sendTick,
            programmaticScroll = programmaticScroll,
            lastProgrammaticScrollMs = lastProgrammaticScrollMs,
            streamingContentBottomPx = streamingContentBottomPx,
            streamBottomFollowActive = streamBottomFollowActive,
            initialBottomSnapDone = initialBottomSnapDone,
            jumpButtonPulseVisible = jumpButtonPulseVisible,
            pendingFinalBottomSnap = pendingFinalBottomSnap,
            restoreBottomAfterImeClose = restoreBottomAfterImeClose,
            suppressJumpButtonForImeTransition = suppressJumpButtonForImeTransition,
            restoreBottomAfterLifecycleResume = restoreBottomAfterLifecycleResume,
            suppressJumpButtonForLifecycleResume = suppressJumpButtonForLifecycleResume,
            lifecycleResumeReady = lifecycleResumeReady,
            bottomBarHeightPx = bottomBarHeightPx,
            inputChromeRowHeightPx = inputChromeRowHeightPx
        )
    }
}

internal fun resolveStreamingLegalBottomPx(
    worklineBottomPx: Int,
    composerTopInViewportPx: Int,
    streamVisibleBottomGapPx: Int
): Int {
    if (worklineBottomPx > 0) return worklineBottomPx
    if (composerTopInViewportPx <= 0) return -1
    return (composerTopInViewportPx - streamVisibleBottomGapPx).coerceAtLeast(0)
}

internal data class StreamingGuardSnapshot(
    val isStreaming: Boolean,
    val hasStreamingItem: Boolean,
    val scrollModeAutoFollow: Boolean,
    val userBrowsing: Boolean,
    val tailBottomPx: Int,
    val legalBottomPx: Int,
    val viewportHeightPx: Int,
    val assistantLineStepPx: Int
)

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
    if (overflow == 0) return 0
    val steadyStepPx = (assistantLineStepPx * 0.12f).roundToInt().coerceAtLeast(5)
    val triggerThresholdPx = (steadyStepPx * 0.2f).roundToInt().coerceAtLeast(2)
    if (abs(overflow) < triggerThresholdPx) return 0
    return overflow.coerceIn(-steadyStepPx, steadyStepPx)
}

internal fun shouldShowStreamingScrollToBottomButton(
    isStreaming: Boolean,
    hasStreamingItem: Boolean,
    scrollMode: ScrollMode,
    nearReturnLine: Boolean
): Boolean {
    return isStreaming &&
        hasStreamingItem &&
        scrollMode == ScrollMode.UserBrowsing &&
        !nearReturnLine
}

internal fun shouldOfferFinalBottomSnap(
    scrollMode: ScrollMode
): Boolean {
    return scrollMode != ScrollMode.UserBrowsing
}

internal fun isStreamingReadyForAutoFollow(
    isStreaming: Boolean,
    hasStreamingItem: Boolean,
    streamingFollowArmed: Boolean,
    streamingBottomInViewport: Int,
    legalBottomPx: Int
): Boolean {
    if (!isStreaming || !hasStreamingItem || !streamingFollowArmed) return false
    if (streamingBottomInViewport <= 0 || legalBottomPx <= 0) return false
    return streamingBottomInViewport >= legalBottomPx
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

internal suspend fun performScrollToBottom(
    listState: LazyListState,
    isStreaming: Boolean,
    scrollMode: ScrollMode,
    messagesSize: Int,
    hasStreamingItem: Boolean,
    currentBottomAlignDeltaPx: () -> Int,
    animated: Boolean,
    onProgrammaticScrollStart: () -> Unit,
    onProgrammaticScrollEnd: () -> Unit
) {
    if (isStreaming && scrollMode != ScrollMode.AutoFollow) return
    if (messagesSize <= 0 && !hasStreamingItem) return
    onProgrammaticScrollStart()
    try {
        withFrameNanos { }
        val lastIndex = messagesSize - 1
        if (lastIndex < 0) return
        if (animated) {
            listState.animateScrollToItem(lastIndex)
        } else {
            listState.scrollToItem(lastIndex)
        }
        repeat(3) {
            withFrameNanos { }
            val deltaPx = currentBottomAlignDeltaPx()
            if (abs(deltaPx) <= 1) return@repeat
            val consumed = listState.scrollBy((-deltaPx).toFloat())
            if (consumed == 0f) return@repeat
        }
        withFrameNanos { }
    } finally {
        onProgrammaticScrollEnd()
    }
}

internal suspend fun performSnapStreamingToWorkline(
    scrollToBottom: suspend (Boolean) -> Unit,
    listState: LazyListState,
    currentStreamingAlignDeltaPx: () -> Int,
    onProgrammaticScrollStart: () -> Unit,
    onProgrammaticScrollEnd: () -> Unit,
) {
    scrollToBottom(false)
    onProgrammaticScrollStart()
    try {
        repeat(3) {
            withFrameNanos { }
            val deltaPx = currentStreamingAlignDeltaPx()
            if (abs(deltaPx) <= 1) return@repeat
            val consumed = listState.scrollBy((-deltaPx).toFloat())
            if (consumed == 0f) return@repeat
        }
    } finally {
        onProgrammaticScrollEnd()
    }
}

@Composable
internal fun rememberStreamingDirectionLock(
    snapshotProvider: () -> StreamingGuardSnapshot,
    onInterruptAutoFollow: () -> Unit
): NestedScrollConnection {
    return remember(snapshotProvider) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val snapshot = snapshotProvider()
                if (
                    snapshot.isStreaming &&
                    snapshot.hasStreamingItem &&
                    snapshot.scrollModeAutoFollow &&
                    available.y != 0f
                ) {
                    onInterruptAutoFollow()
                    return Offset.Zero
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val snapshot = snapshotProvider()
                if (
                    snapshot.isStreaming &&
                    snapshot.hasStreamingItem &&
                    snapshot.scrollModeAutoFollow &&
                    available.y != 0f
                ) {
                    onInterruptAutoFollow()
                    return Velocity.Zero
                }
                return Velocity.Zero
            }
        }
    }
}

@Composable
internal fun BindChatScrollRuntimeEffects(
    listState: LazyListState,
    messagesSize: Int,
    isStreaming: Boolean,
    hasStreamingItem: Boolean,
    hasStreamingContent: Boolean,
    streamingMessageId: String?,
    streamingContentBottomPxState: MutableIntState,
    streamingFollowArmedState: MutableState<Boolean>,
    scrollModeState: MutableState<ScrollMode>,
    userInteractingState: MutableState<Boolean>,
    streamBottomFollowActiveState: MutableState<Boolean>,
    streamTick: Int,
    sendTick: Int,
    programmaticScrollState: MutableState<Boolean>,
    lastProgrammaticScrollMsState: MutableState<Long>,
    streamingLineAdvanceTickState: MutableIntState,
    streamingWorklineBottomPx: Int,
    currentStreamingContentBottomPx: () -> Int,
    currentStreamingOverflowDelta: () -> Int,
    resolveStreamingFollowStepPx: (Int) -> Int,
    isStreamingReadyForAutoFollow: () -> Boolean,
    scrollToBottom: suspend (Boolean) -> Unit,
    snapStreamingToWorkline: suspend () -> Unit
) {
    LaunchedEffect(
        isStreaming,
        hasStreamingItem,
        streamingMessageId,
        listState.firstVisibleItemIndex,
        listState.firstVisibleItemScrollOffset
    ) {
        if (!isStreaming || !hasStreamingItem) return@LaunchedEffect
        val streamingItemIndex = messagesSize - 1
        if (streamingItemIndex < 0) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo.any { it.index == streamingItemIndex }
        if (!visible && streamingContentBottomPxState.intValue != -1) {
            streamingContentBottomPxState.intValue = -1
        }
    }

    LaunchedEffect(
        isStreaming,
        hasStreamingItem,
        hasStreamingContent,
        streamingContentBottomPxState.intValue,
        listState.firstVisibleItemIndex,
        listState.firstVisibleItemScrollOffset
    ) {
        streamingFollowArmedState.value =
            isStreaming &&
                hasStreamingItem &&
                hasStreamingContent &&
                streamingContentBottomPxState.intValue > 0
    }

    LaunchedEffect(listState.isScrollInProgress, programmaticScrollState.value) {
        if (programmaticScrollState.value) {
            userInteractingState.value = false
            return@LaunchedEffect
        }
        userInteractingState.value = listState.isScrollInProgress
    }

    LaunchedEffect(listState, isStreaming, hasStreamingItem) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow {
            listOf(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                if (listState.isScrollInProgress) 1 else 0,
                streamTick,
                streamingWorklineBottomPx,
                currentStreamingContentBottomPx()
            )
        }.collect { state ->
            val currentIndex = state[0]
            val currentOffset = state[1]
            val scrollInProgress = state[2] == 1
            if (programmaticScrollState.value) {
                previousIndex = currentIndex
                previousOffset = currentOffset
                return@collect
            }
            if (isStreaming && hasStreamingItem && scrollModeState.value == ScrollMode.Idle) {
                previousIndex = currentIndex
                previousOffset = currentOffset
                return@collect
            }
            val movedTowardBottom =
                currentIndex > previousIndex ||
                    (currentIndex == previousIndex && currentOffset > previousOffset)
            val movedTowardTop =
                currentIndex < previousIndex ||
                    (currentIndex == previousIndex && currentOffset < previousOffset)
            if (isStreaming && hasStreamingItem) {
                when {
                    scrollInProgress &&
                        (movedTowardTop || movedTowardBottom) &&
                        scrollModeState.value == ScrollMode.AutoFollow -> {
                        scrollModeState.value = ScrollMode.UserBrowsing
                    }

                    scrollModeState.value == ScrollMode.UserBrowsing -> {
                        val canResumeAutoFollow =
                            !scrollInProgress &&
                                currentStreamingContentBottomPx() > 0 &&
                                isStreamingReadyForAutoFollow()
                        if (canResumeAutoFollow) {
                            scrollModeState.value = ScrollMode.AutoFollow
                        }
                    }
                }
            } else {
                when {
                    movedTowardBottom -> {
                        scrollModeState.value = ScrollMode.Idle
                    }
                    movedTowardTop -> {
                        scrollModeState.value = ScrollMode.UserBrowsing
                    }
                    else -> {
                        scrollModeState.value = ScrollMode.Idle
                    }
                }
            }
            previousIndex = currentIndex
            previousOffset = currentOffset
        }
    }

    LaunchedEffect(
        scrollModeState.value,
        isStreaming,
        hasStreamingItem,
        hasStreamingContent,
        userInteractingState.value,
        listState.isScrollInProgress
    ) {
        if (!hasStreamingItem || !isStreaming || !hasStreamingContent) {
            streamBottomFollowActiveState.value = false
            return@LaunchedEffect
        }
        while (isActive && hasStreamingItem && isStreaming && hasStreamingContent) {
            withFrameNanos { }
            if (
                scrollModeState.value != ScrollMode.AutoFollow ||
                listState.isScrollInProgress ||
                userInteractingState.value ||
                !streamingFollowArmedState.value
            ) {
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
            lastProgrammaticScrollMsState.value = SystemClock.uptimeMillis()
            programmaticScrollState.value = true
            try {
                val consumed = listState.scrollBy(stepPx.toFloat())
                if (abs(consumed) > 0f) {
                    streamingLineAdvanceTickState.intValue++
                }
            } finally {
                programmaticScrollState.value = false
                lastProgrammaticScrollMsState.value = SystemClock.uptimeMillis()
                streamBottomFollowActiveState.value = false
            }
        }
    }

    LaunchedEffect(sendTick) {
        if (messagesSize <= 0) return@LaunchedEffect
        userInteractingState.value = false
        scrollModeState.value = ScrollMode.AutoFollow
        if (!hasStreamingContent) {
            scrollToBottom(false)
            return@LaunchedEffect
        }
        for (attempt in 0 until 12) {
            withFrameNanos { }
            if (streamingContentBottomPxState.intValue > 0) break
        }
        if (streamingContentBottomPxState.intValue <= 0) {
            return@LaunchedEffect
        }
        snapStreamingToWorkline()
    }

}

@Composable
internal fun BindChatScrollAuxiliaryEffects(
    isStreaming: Boolean,
    hasStreamingItem: Boolean,
    messagesSize: Int,
    startupLayoutReady: Boolean,
    startupHydrationBarrierSatisfied: Boolean,
    isWithinFinalBottomSnapTolerance: () -> Boolean,
    isBottomSettled: suspend () -> Boolean,
    scrollToBottom: suspend (Boolean) -> Unit,
    initialBottomSnapDoneState: MutableState<Boolean>,
    pendingFinalBottomSnapState: MutableState<Boolean>,
    suppressJumpButtonForImeTransitionState: MutableState<Boolean>,
    suppressJumpButtonForLifecycleResumeState: MutableState<Boolean>
) {
    LaunchedEffect(pendingFinalBottomSnapState.value, messagesSize, isStreaming) {
        if (!pendingFinalBottomSnapState.value || isStreaming) return@LaunchedEffect
        repeat(2) { withFrameNanos { } }
        for (attempt in 0 until 4) {
            withFrameNanos { }
            if (isWithinFinalBottomSnapTolerance()) {
                break
            }
            scrollToBottom(false)
            if (isWithinFinalBottomSnapTolerance()) {
                break
            }
            if (attempt < 3) {
                kotlinx.coroutines.delay(16)
            }
        }
        if (isWithinFinalBottomSnapTolerance()) {
            repeat(2) { withFrameNanos { } }
        }
        pendingFinalBottomSnapState.value = false
    }

    LaunchedEffect(
        messagesSize,
        isStreaming,
        startupLayoutReady,
        hasStreamingItem,
        initialBottomSnapDoneState.value,
        startupHydrationBarrierSatisfied
    ) {
        if (initialBottomSnapDoneState.value) return@LaunchedEffect
        if (!startupHydrationBarrierSatisfied) return@LaunchedEffect
        if (!startupLayoutReady) return@LaunchedEffect
        if (messagesSize == 0 && !hasStreamingItem) {
            initialBottomSnapDoneState.value = true
            return@LaunchedEffect
        }
        if (messagesSize == 0 || isStreaming || hasStreamingItem) return@LaunchedEffect
        repeat(3) { withFrameNanos { } }
        repeat(4) { attempt ->
            scrollToBottom(false)
            if (isBottomSettled()) {
                initialBottomSnapDoneState.value = true
                return@LaunchedEffect
            }
            if (attempt < 3) {
                kotlinx.coroutines.delay(60)
            }
        }
    }

    LaunchedEffect(Unit) {
        suppressJumpButtonForImeTransitionState.value = false
        suppressJumpButtonForLifecycleResumeState.value = false
    }
}
