package com.nongjiqianwen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    val programmaticScroll: MutableState<Boolean>,
    val lastProgrammaticScrollMs: MutableState<Long>,
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
    val scrollMode = remember { mutableStateOf(ScrollMode.Idle) }
    val userInteracting = remember { mutableStateOf(false) }
    val streamTick = remember { mutableIntStateOf(0) }
    val programmaticScroll = remember { mutableStateOf(false) }
    val lastProgrammaticScrollMs = remember { mutableStateOf(0L) }
    val streamingContentBottomPx = remember { mutableIntStateOf(-1) }
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
            streamTick = streamTick,
            programmaticScroll = programmaticScroll,
            lastProgrammaticScrollMs = lastProgrammaticScrollMs,
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

internal fun resolveStreamingLegalBottomPx(
    worklineBottomPx: Int,
    composerTopInViewportPx: Int,
    streamVisibleBottomGapPx: Int
): Int {
    if (worklineBottomPx > 0) return worklineBottomPx
    if (composerTopInViewportPx <= 0) return -1
    return (composerTopInViewportPx - streamVisibleBottomGapPx).coerceAtLeast(0)
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
    val steadyStepPx = (assistantLineStepPx * 0.12f).roundToInt().coerceAtLeast(5)
    val triggerThresholdPx = (steadyStepPx * 0.2f).roundToInt().coerceAtLeast(2)
    if (overflow < triggerThresholdPx) return 0
    return overflow.coerceAtMost(steadyStepPx)
}

internal fun resolveStreamingFollowScrollDeltaPx(
    alignDelta: Int,
    assistantLineStepPx: Int
): Int {
    if (alignDelta == 0) return 0
    val steadyStepPx = (assistantLineStepPx * 0.14f).roundToInt().coerceAtLeast(6)
    val triggerThresholdPx = (steadyStepPx * 0.2f).roundToInt().coerceAtLeast(2)
    if (kotlin.math.abs(alignDelta) < triggerThresholdPx) return 0
    val rawDelta = -alignDelta
    return rawDelta.coerceIn(-steadyStepPx, steadyStepPx)
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
    streamingBottomInViewport: Int,
    legalBottomPx: Int
): Boolean {
    if (!isStreaming || !hasStreamingItem) return false
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
