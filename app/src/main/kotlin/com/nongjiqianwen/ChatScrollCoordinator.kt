package com.nongjiqianwen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt

internal enum class AnchorPhase {
    None,
    FrozenBottom
}

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

internal data class StreamingGuardSnapshot(
    val isStreaming: Boolean,
    val hasStreamingItem: Boolean,
    val scrollModeAutoFollow: Boolean,
    val tailBottomPx: Int,
    val legalBottomPx: Int,
    val viewportHeightPx: Int,
    val assistantLineStepPx: Int
)

internal fun findSendAnchorIndex(
    messages: List<Any>,
    anchoredUserMessageId: String?,
    messageIdProvider: (Any) -> String,
    assistantIdProvider: (String) -> String
): Int {
    val sourceUserId = anchoredUserMessageId
    return sourceUserId
        ?.let(assistantIdProvider)
        ?.let { assistantId -> messages.indexOfFirst { messageIdProvider(it) == assistantId } }
        ?.takeIf { it >= 0 }
        ?: sourceUserId
            ?.let { userId -> messages.indexOfFirst { messageIdProvider(it) == userId } }
            ?.takeIf { it >= 0 }
        ?: messages.lastIndex
}

internal fun isStreamingTailNearGuardBoundary(snapshot: StreamingGuardSnapshot): Boolean {
    if (!snapshot.isStreaming || !snapshot.hasStreamingItem) return false
    if (snapshot.tailBottomPx <= 0 || snapshot.legalBottomPx <= 0) return false
    val activationRangePx = (snapshot.viewportHeightPx * 0.22f).roundToInt()
        .coerceAtLeast(snapshot.assistantLineStepPx * 2)
    return snapshot.tailBottomPx >= (snapshot.legalBottomPx - activationRangePx)
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

internal fun isStreamingReadyForAutoFollow(
    isStreaming: Boolean,
    hasStreamingItem: Boolean,
    streamingFollowArmed: Boolean,
    streamingBottomInViewport: Int,
    legalBottomPx: Int
): Boolean {
    if (!isStreaming || !hasStreamingItem || !streamingFollowArmed) return false
    if (streamingBottomInViewport <= 0 || legalBottomPx <= 0) return false
    return streamingBottomInViewport <= legalBottomPx
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

internal suspend fun snapStreamingToSendAnchor(
    listState: LazyListState,
    anchorIndex: Int,
    anchorTopPx: Int
) {
    if (anchorIndex < 0) return
    listState.scrollToItem(anchorIndex, scrollOffset = -anchorTopPx)
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
                }
                if (
                    snapshot.isStreaming &&
                    snapshot.hasStreamingItem &&
                    available.y < 0f &&
                    isStreamingTailNearGuardBoundary(snapshot)
                ) {
                    val projectedBottom = snapshot.tailBottomPx - available.y
                    val overflowPx = (projectedBottom - snapshot.legalBottomPx).coerceAtLeast(0f)
                    if (overflowPx > 0f) {
                        val consumedPx = overflowPx.coerceAtMost(-available.y)
                        return Offset(x = 0f, y = -consumedPx)
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
                    available.y < 0f &&
                    isStreamingTailNearGuardBoundary(snapshot)
                ) {
                    val remainingToBoundaryPx =
                        if (snapshot.tailBottomPx > 0 && snapshot.legalBottomPx > 0) {
                            (snapshot.legalBottomPx - snapshot.tailBottomPx).coerceAtLeast(0)
                        } else {
                            Int.MAX_VALUE
                        }
                    val shouldClampBottomFling =
                        when {
                            remainingToBoundaryPx == Int.MAX_VALUE -> false
                            remainingToBoundaryPx <= 0 -> true
                            remainingToBoundaryPx < 150 -> true
                            else -> false
                        }
                    if (shouldClampBottomFling) {
                        return Velocity(x = 0f, y = available.y)
                    }
                }
                return Velocity.Zero
            }
        }
    }
}
