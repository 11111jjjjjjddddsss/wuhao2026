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
import kotlin.math.roundToInt

internal enum class AutoScrollMode {
    Idle,
    AnchorUser,
    StreamAnchorFollow
}

internal enum class ScrollMode {
    Idle,
    AutoFollow,
    UserBrowsing
}

internal data class ChatScrollRuntimeState(
    val scrollMode: MutableState<ScrollMode>,
    val autoScrollMode: MutableState<AutoScrollMode>,
    val userInteracting: MutableState<Boolean>,
    val streamTick: MutableIntState,
    val sendTick: MutableIntState,
    val programmaticScroll: MutableState<Boolean>,
    val lastProgrammaticScrollMs: MutableState<Long>,
    val streamingContentBottomPx: MutableIntState,
    val streamBottomFollowActive: MutableState<Boolean>,
    val initialBottomSnapDone: MutableState<Boolean>,
    val jumpButtonPulseVisible: MutableState<Boolean>,
    val userDetachedFromBottom: MutableState<Boolean>,
    val pendingResumeAutoFollow: MutableState<Boolean>,
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
    val autoScrollMode = remember { mutableStateOf(AutoScrollMode.Idle) }
    val userInteracting = remember { mutableStateOf(false) }
    val streamTick = remember { mutableIntStateOf(0) }
    val sendTick = remember { mutableIntStateOf(0) }
    val programmaticScroll = remember { mutableStateOf(false) }
    val lastProgrammaticScrollMs = remember { mutableStateOf(0L) }
    val streamingContentBottomPx = remember { mutableIntStateOf(-1) }
    val streamBottomFollowActive = remember { mutableStateOf(false) }
    val initialBottomSnapDone = remember(chatScopeId) { mutableStateOf(false) }
    val jumpButtonPulseVisible = remember { mutableStateOf(false) }
    val userDetachedFromBottom = remember { mutableStateOf(false) }
    val pendingResumeAutoFollow = remember { mutableStateOf(false) }
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
            autoScrollMode = autoScrollMode,
            userInteracting = userInteracting,
            streamTick = streamTick,
            sendTick = sendTick,
            programmaticScroll = programmaticScroll,
            lastProgrammaticScrollMs = lastProgrammaticScrollMs,
            streamingContentBottomPx = streamingContentBottomPx,
            streamBottomFollowActive = streamBottomFollowActive,
            initialBottomSnapDone = initialBottomSnapDone,
            jumpButtonPulseVisible = jumpButtonPulseVisible,
            userDetachedFromBottom = userDetachedFromBottom,
            pendingResumeAutoFollow = pendingResumeAutoFollow,
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

internal fun resolveBottomDragOverflowPx(
    tailBottomPx: Int,
    legalBottomPx: Int,
    deltaY: Float
): Float {
    if (deltaY <= 0f || tailBottomPx <= 0 || legalBottomPx <= 0) return 0f
    val projectedBottom = tailBottomPx + deltaY
    return (projectedBottom - legalBottomPx).coerceAtLeast(0f)
}

internal fun shouldConsumeBottomFling(
    snapshot: StreamingGuardSnapshot,
    velocityY: Float
): Boolean {
    if (velocityY <= 0f) return false
    if (snapshot.userBrowsing) return false
    if (snapshot.tailBottomPx <= 0 || snapshot.legalBottomPx <= 0) return false
    return snapshot.tailBottomPx >= snapshot.legalBottomPx
}

internal fun currentStreamingOverflowDelta(
    contentBottom: Int,
    visibleBottom: Int
): Int {
    if (contentBottom <= 0 || visibleBottom <= 0) return 0
    return (contentBottom - visibleBottom).coerceAtLeast(0)
}

internal fun resolveStreamingFollowStepPx(
    overflow: Int,
    assistantLineStepPx: Int
): Int {
    if (overflow <= 0) return 0
    val steadyStepPx = (assistantLineStepPx * 0.12f).roundToInt().coerceAtLeast(5)
    val triggerThresholdPx = (steadyStepPx * 0.2f).roundToInt().coerceAtLeast(2)
    if (overflow < triggerThresholdPx) return 0
    return overflow.coerceAtMost(steadyStepPx)
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
    messageViewportHeightPx: Int,
    stickyScrollStepPx: Int,
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
        val stickyStep = messageViewportHeightPx
            .takeIf { it > 0 }
            ?.let { (it * 0.56f).roundToInt() }
            ?.coerceAtLeast(stickyScrollStepPx)
            ?: stickyScrollStepPx
        if (animated) {
            repeat(72) {
                withFrameNanos { }
                if (!listState.canScrollForward) return
                val consumed = listState.scrollBy(stickyStep.toFloat())
                if (consumed <= 0f) return
            }
        } else {
            repeat(72) {
                if (!listState.canScrollForward) return
                val consumed = listState.scrollBy(stickyStep.toFloat())
                if (consumed <= 0f) return
            }
        }
        withFrameNanos { }
    } finally {
        onProgrammaticScrollEnd()
    }
}

internal suspend fun performSnapStreamingToWorkline(
    scrollToBottom: suspend (Boolean) -> Unit,
) {
    scrollToBottom(false)
}

@Composable
internal fun rememberStreamingDirectionLock(
    snapshotProvider: () -> StreamingGuardSnapshot,
    onInterruptAutoFollow: () -> Unit,
    onTowardBottomGesture: () -> Unit,
    onTowardTopGesture: () -> Unit
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
                }
                if (
                    snapshot.isStreaming &&
                    snapshot.hasStreamingItem
                ) {
                    when {
                        available.y > 0f -> onTowardBottomGesture()
                        available.y < 0f -> onTowardTopGesture()
                    }
                    if (available.y > 0f) {
                        val overflowPx = resolveBottomDragOverflowPx(
                            tailBottomPx = snapshot.tailBottomPx,
                            legalBottomPx = snapshot.legalBottomPx,
                            deltaY = available.y
                        )
                        if (overflowPx > 0f) {
                            val consumedPx = overflowPx.coerceAtMost(available.y)
                            return Offset(x = 0f, y = consumedPx)
                        }
                    }
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
                }
                if (
                    snapshot.isStreaming &&
                    snapshot.hasStreamingItem &&
                    shouldConsumeBottomFling(snapshot, available.y)
                ) {
                    return Velocity(x = 0f, y = available.y)
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
    streamingMessageId: String?,
    streamingMessageContent: String,
    streamingContentBottomPxState: MutableIntState,
    streamingFollowArmedState: MutableState<Boolean>,
    scrollModeState: MutableState<ScrollMode>,
    autoScrollModeState: MutableState<AutoScrollMode>,
    userInteractingState: MutableState<Boolean>,
    userDetachedFromBottomState: MutableState<Boolean>,
    streamBottomFollowActiveState: MutableState<Boolean>,
    pendingResumeAutoFollowState: MutableState<Boolean>,
    streamTick: Int,
    sendTick: Int,
    programmaticScrollState: MutableState<Boolean>,
    lastProgrammaticScrollMsState: MutableState<Long>,
    streamingLineAdvanceTickState: MutableIntState,
    bottomPositionTolerancePx: Int,
    streamingWorklineBottomPx: Int,
    currentStreamingTailBottomPx: () -> Int,
    currentStreamingVisualBottomPx: () -> Int,
    currentStreamingGuardContentBottomPx: () -> Int,
    currentStreamingLegalBottomPx: () -> Int,
    currentStreamingOverflowDelta: () -> Int,
    resolveStreamingFollowStepPx: (Int) -> Int,
    isStreamingReadyForAutoFollow: () -> Boolean,
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
        streamingMessageContent.length,
        streamingContentBottomPxState.intValue
    ) {
        if (!isStreaming || !hasStreamingItem) {
            streamingFollowArmedState.value = false
            return@LaunchedEffect
        }
        if (streamingMessageContent.isBlank()) {
            streamingFollowArmedState.value = false
            return@LaunchedEffect
        }
        val firstBottom = currentStreamingTailBottomPx()
        if (firstBottom <= 0) {
            streamingFollowArmedState.value = false
            return@LaunchedEffect
        }
        repeat(2) { withFrameNanos { } }
        val secondBottom = currentStreamingTailBottomPx()
        val canArmFollow =
            isStreaming &&
                hasStreamingItem &&
                streamingMessageContent.isNotBlank() &&
                secondBottom > 0 &&
                kotlin.math.abs(secondBottom - firstBottom) <= bottomPositionTolerancePx
        if (canArmFollow) {
            streamingFollowArmedState.value = true
        } else if (!streamingFollowArmedState.value) {
            streamingFollowArmedState.value = false
        }
    }

    LaunchedEffect(scrollModeState.value, isStreaming, hasStreamingItem) {
        val derivedAutoScrollMode = when (scrollModeState.value) {
            ScrollMode.Idle -> if (isStreaming && hasStreamingItem) AutoScrollMode.AnchorUser else AutoScrollMode.Idle
            ScrollMode.AutoFollow -> AutoScrollMode.StreamAnchorFollow
            ScrollMode.UserBrowsing -> AutoScrollMode.AnchorUser
        }
        val derivedDetached = scrollModeState.value == ScrollMode.UserBrowsing
        if (autoScrollModeState.value != derivedAutoScrollMode) {
            autoScrollModeState.value = derivedAutoScrollMode
        }
        if (userDetachedFromBottomState.value != derivedDetached) {
            userDetachedFromBottomState.value = derivedDetached
        }
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
                currentStreamingVisualBottomPx()
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
                        pendingResumeAutoFollowState.value = false
                        scrollModeState.value = ScrollMode.UserBrowsing
                    }

                    scrollModeState.value == ScrollMode.UserBrowsing -> {
                        val canResumeAutoFollow =
                            pendingResumeAutoFollowState.value &&
                            !scrollInProgress &&
                                currentStreamingVisualBottomPx() > 0 &&
                                isStreamingReadyForAutoFollow()
                        if (canResumeAutoFollow) {
                            pendingResumeAutoFollowState.value = false
                            scrollModeState.value = ScrollMode.AutoFollow
                        }
                    }
                }
            } else {
                when {
                    movedTowardBottom -> {
                        pendingResumeAutoFollowState.value = false
                        scrollModeState.value = ScrollMode.Idle
                        autoScrollModeState.value = AutoScrollMode.Idle
                        userDetachedFromBottomState.value = false
                    }
                    movedTowardTop -> {
                        pendingResumeAutoFollowState.value = false
                        scrollModeState.value = ScrollMode.UserBrowsing
                        autoScrollModeState.value = AutoScrollMode.Idle
                        userDetachedFromBottomState.value = true
                    }
                    else -> {
                        scrollModeState.value = ScrollMode.Idle
                        autoScrollModeState.value = AutoScrollMode.Idle
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
        userInteractingState.value,
        userDetachedFromBottomState.value,
        listState.isScrollInProgress
    ) {
        if (!hasStreamingItem || !isStreaming) {
            streamBottomFollowActiveState.value = false
            return@LaunchedEffect
        }
        while (isActive && hasStreamingItem && isStreaming) {
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
            if (streamingMessageContent.isBlank()) {
                streamBottomFollowActiveState.value = false
                continue
            }
            val overflow = currentStreamingOverflowDelta()
            val stepPx = resolveStreamingFollowStepPx(overflow)
            if (stepPx <= 0) {
                streamBottomFollowActiveState.value = false
                continue
            }
            streamBottomFollowActiveState.value = true
            lastProgrammaticScrollMsState.value = SystemClock.uptimeMillis()
            programmaticScrollState.value = true
            try {
                val consumed = listState.scrollBy(stepPx.toFloat())
                if (consumed > 0f) {
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
        pendingResumeAutoFollowState.value = false
        scrollModeState.value = ScrollMode.AutoFollow
        autoScrollModeState.value = AutoScrollMode.StreamAnchorFollow
        repeat(2) { withFrameNanos { } }
        snapStreamingToWorkline()
    }

    LaunchedEffect(
        isStreaming,
        hasStreamingItem,
        listState.firstVisibleItemIndex,
        listState.firstVisibleItemScrollOffset,
        listState.isScrollInProgress,
        streamingContentBottomPxState.intValue
    ) {
        if (!isStreaming || !hasStreamingItem) {
            return@LaunchedEffect
        }
        if (listState.isScrollInProgress || programmaticScrollState.value || userInteractingState.value) {
            return@LaunchedEffect
        }
        val guardBottom = currentStreamingGuardContentBottomPx()
        val legalBottom = currentStreamingLegalBottomPx()
        if (guardBottom <= 0 || legalBottom <= 0) return@LaunchedEffect
        val overflowPx = (guardBottom - legalBottom).coerceAtLeast(0)
        if (overflowPx <= bottomPositionTolerancePx) return@LaunchedEffect
        lastProgrammaticScrollMsState.value = SystemClock.uptimeMillis()
        programmaticScrollState.value = true
        try {
            listState.scrollBy(overflowPx.toFloat())
        } finally {
            programmaticScrollState.value = false
            lastProgrammaticScrollMsState.value = SystemClock.uptimeMillis()
        }
    }

    LaunchedEffect(
        isStreaming,
        hasStreamingItem,
        scrollModeState.value,
        pendingResumeAutoFollowState.value,
        streamTick,
        streamingMessageId,
        streamingMessageContent.length,
        listState.firstVisibleItemIndex,
        listState.firstVisibleItemScrollOffset,
        streamingContentBottomPxState.intValue,
        streamingWorklineBottomPx,
        listState.isScrollInProgress,
        userInteractingState.value
    ) {
        if (!isStreaming || !hasStreamingItem) return@LaunchedEffect
        if (scrollModeState.value != ScrollMode.Idle && scrollModeState.value != ScrollMode.UserBrowsing) {
            return@LaunchedEffect
        }
        if (streamingMessageContent.isBlank()) return@LaunchedEffect
        if (userInteractingState.value || listState.isScrollInProgress) return@LaunchedEffect
        if (isStreamingReadyForAutoFollow()) {
            repeat(2) { withFrameNanos { } }
            if (
                userInteractingState.value ||
                listState.isScrollInProgress ||
                !isStreamingReadyForAutoFollow()
            ) {
                return@LaunchedEffect
            }
            pendingResumeAutoFollowState.value = false
            scrollModeState.value = ScrollMode.AutoFollow
            autoScrollModeState.value = AutoScrollMode.StreamAnchorFollow
        }
    }
}

@Composable
internal fun BindChatScrollAuxiliaryEffects(
    listState: LazyListState,
    isStreaming: Boolean,
    hasStreamingItem: Boolean,
    messagesSize: Int,
    atBottom: Boolean,
    imeVisible: Boolean,
    startupLayoutReady: Boolean,
    startupHydrationBarrierSatisfied: Boolean,
    hasStartedConversation: Boolean,
    currentBottomOverflowPx: () -> Int,
    isWithinFinalBottomSnapTolerance: () -> Boolean,
    isBottomSettled: suspend () -> Boolean,
    scrollToBottom: suspend (Boolean) -> Unit,
    lifecycleResumeBottomSnapThresholdPx: Int,
    userDetachedFromBottomState: MutableState<Boolean>,
    initialBottomSnapDoneState: MutableState<Boolean>,
    pendingFinalBottomSnapState: MutableState<Boolean>,
    restoreBottomAfterImeCloseState: MutableState<Boolean>,
    suppressJumpButtonForImeTransitionState: MutableState<Boolean>,
    restoreBottomAfterLifecycleResumeState: MutableState<Boolean>,
    suppressJumpButtonForLifecycleResumeState: MutableState<Boolean>,
    lifecycleResumeReadyState: MutableState<Boolean>,
    programmaticScrollState: MutableState<Boolean>
) {
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            restoreBottomAfterImeCloseState.value =
                atBottom &&
                    !isStreaming &&
                    !userDetachedFromBottomState.value &&
                    !listState.isScrollInProgress &&
                    !programmaticScrollState.value
            suppressJumpButtonForImeTransitionState.value = true
            return@LaunchedEffect
        }

        if (restoreBottomAfterImeCloseState.value && !isStreaming) {
            withFrameNanos { }
            if (!userDetachedFromBottomState.value && !listState.isScrollInProgress && !programmaticScrollState.value) {
                scrollToBottom(false)
            }
            restoreBottomAfterImeCloseState.value = false
        } else {
            restoreBottomAfterImeCloseState.value = false
        }

        repeat(2) { withFrameNanos { } }
        suppressJumpButtonForImeTransitionState.value = false
    }

    LaunchedEffect(pendingFinalBottomSnapState.value, messagesSize, isStreaming) {
        if (!pendingFinalBottomSnapState.value || isStreaming) return@LaunchedEffect
        repeat(2) { withFrameNanos { } }
        for (attempt in 0 until 4) {
            withFrameNanos { }
            if (isWithinFinalBottomSnapTolerance()) {
                break
            }
            scrollToBottom(false)
            if (!listState.canScrollForward || isWithinFinalBottomSnapTolerance()) {
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
        restoreBottomAfterLifecycleResumeState.value,
        lifecycleResumeReadyState.value,
        isStreaming,
        messagesSize,
        hasStreamingItem
    ) {
        if (!restoreBottomAfterLifecycleResumeState.value || !lifecycleResumeReadyState.value) {
            return@LaunchedEffect
        }
        if (isStreaming || (messagesSize == 0 && !hasStreamingItem)) {
            restoreBottomAfterLifecycleResumeState.value = false
            suppressJumpButtonForLifecycleResumeState.value = false
            return@LaunchedEffect
        }
        var stableResumeOverflowPx = Int.MAX_VALUE
        repeat(4) {
            withFrameNanos { }
            val overflowPx = currentBottomOverflowPx()
            if (overflowPx == Int.MAX_VALUE) return@repeat
            stableResumeOverflowPx = minOf(stableResumeOverflowPx, overflowPx)
        }
        if (
            !listState.isScrollInProgress &&
            !programmaticScrollState.value &&
            stableResumeOverflowPx != Int.MAX_VALUE &&
            stableResumeOverflowPx > lifecycleResumeBottomSnapThresholdPx
        ) {
            scrollToBottom(false)
        }
        repeat(2) { withFrameNanos { } }
        suppressJumpButtonForLifecycleResumeState.value = false
        restoreBottomAfterLifecycleResumeState.value = false
    }

    LaunchedEffect(
        hasStartedConversation,
        messagesSize,
        isStreaming,
        startupLayoutReady,
        hasStreamingItem,
        initialBottomSnapDoneState.value,
        startupHydrationBarrierSatisfied
    ) {
        if (hasStartedConversation) return@LaunchedEffect
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
            if (!listState.canScrollForward || isBottomSettled()) {
                initialBottomSnapDoneState.value = true
                return@LaunchedEffect
            }
            if (attempt < 3) {
                kotlinx.coroutines.delay(60)
            }
        }
    }
}
