package com.nongjiqianwen

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private fun resolvePendingStartAnchorTargetTopPx(
    layoutInfo: LazyListLayoutInfo,
    pendingStartAnchorPosition: Int,
    pendingStartAnchorTargetBottomPx: Int,
    pendingStartAnchorEstimatedHeightPx: Int,
    pendingStartAnchorVisibleBottomInsetPx: Int,
    topPaddingPx: Int,
    bottomPaddingPx: Int
): Int {
    val anchorHeightPx =
        layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == pendingStartAnchorPosition }
            ?.size
            ?.takeIf { it > 0 }
            ?: pendingStartAnchorEstimatedHeightPx.coerceAtLeast(0)
    val visibleAnchorHeightPx =
        (anchorHeightPx - pendingStartAnchorVisibleBottomInsetPx.coerceAtLeast(0))
            .coerceAtLeast(0)
    val targetBottomPx =
        pendingStartAnchorTargetBottomPx.takeIf { it > 0 }
            ?: (layoutInfo.viewportSize.height - bottomPaddingPx).coerceAtLeast(topPaddingPx)
    return (targetBottomPx - visibleAnchorHeightPx)
        .coerceAtLeast(topPaddingPx)
}

@Composable
internal fun ChatRecyclerViewHost(
    modifier: Modifier = Modifier,
    stateResetKey: String,
    listState: LazyListState,
    itemIds: List<String>,
    topPaddingPx: Int,
    bottomPaddingPx: Int,
    pendingStartAnchorTargetBottomPx: Int,
    pendingStartAnchorEstimatedHeightPx: Int,
    pendingStartAnchorVisibleBottomInsetPx: Int,
    pendingStartAnchorMessageId: String?,
    pendingStartAnchorRequestId: Int,
    onPendingStartAnchorHandled: () -> Unit,
    onStartAnchorScrollStarted: () -> Unit,
    onStartAnchorScrollFinished: () -> Unit,
    itemContent: @Composable (String) -> Unit
) {
    val density = LocalDensity.current
    val lastAppliedStartAnchorRequestId = remember(stateResetKey) { mutableIntStateOf(0) }

    LaunchedEffect(
        stateResetKey,
        itemIds,
        pendingStartAnchorMessageId,
        pendingStartAnchorRequestId,
        pendingStartAnchorTargetBottomPx,
        pendingStartAnchorEstimatedHeightPx,
        pendingStartAnchorVisibleBottomInsetPx,
        topPaddingPx,
        bottomPaddingPx
    ) {
        if (pendingStartAnchorRequestId <= 0) return@LaunchedEffect
        if (pendingStartAnchorRequestId == lastAppliedStartAnchorRequestId.intValue) {
            return@LaunchedEffect
        }
        val pendingStartAnchorPosition =
            pendingStartAnchorMessageId?.let(itemIds::indexOf)?.takeIf { it >= 0 }
                ?: return@LaunchedEffect
        val requestId = pendingStartAnchorRequestId
        var stableGeometryFrames = 0
        var lastObservedAnchorTop = Int.MIN_VALUE
        var lastObservedPrecedingBottom = Int.MIN_VALUE
        var startAnchorScrollOwned = false

        fun beginStartAnchorScrollIfNeeded() {
            if (startAnchorScrollOwned) return
            onStartAnchorScrollStarted()
            startAnchorScrollOwned = true
        }

        fun isRevealStable(targetTopOffset: Int): Boolean {
            val anchorItem =
                listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == pendingStartAnchorPosition }
            val precedingItem =
                (pendingStartAnchorPosition - 1)
                    .takeIf { it >= 0 }
                    ?.let { precedingIndex ->
                        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == precedingIndex }
                    }
            val anchorSettled =
                anchorItem != null &&
                    abs(anchorItem.offset - targetTopOffset) <= 2
            val precedingItemReady =
                pendingStartAnchorPosition <= 0 || precedingItem != null
            if (!anchorSettled || !precedingItemReady || anchorItem == null) {
                stableGeometryFrames = 0
                lastObservedAnchorTop = Int.MIN_VALUE
                lastObservedPrecedingBottom = Int.MIN_VALUE
                return false
            }
            val currentAnchorTop = anchorItem.offset
            val currentPrecedingBottom =
                precedingItem?.let { it.offset + it.size } ?: Int.MIN_VALUE
            stableGeometryFrames =
                if (
                    currentAnchorTop == lastObservedAnchorTop &&
                    currentPrecedingBottom == lastObservedPrecedingBottom
                ) {
                    stableGeometryFrames + 1
                } else {
                    1
                }
            lastObservedAnchorTop = currentAnchorTop
            lastObservedPrecedingBottom = currentPrecedingBottom
            return stableGeometryFrames >= 2
        }

        try {
            repeat(8) {
                beginStartAnchorScrollIfNeeded()
                val targetTopOffset = resolvePendingStartAnchorTargetTopPx(
                    layoutInfo = listState.layoutInfo,
                    pendingStartAnchorPosition = pendingStartAnchorPosition,
                    pendingStartAnchorTargetBottomPx = pendingStartAnchorTargetBottomPx,
                    pendingStartAnchorEstimatedHeightPx = pendingStartAnchorEstimatedHeightPx,
                    pendingStartAnchorVisibleBottomInsetPx = pendingStartAnchorVisibleBottomInsetPx,
                    topPaddingPx = topPaddingPx,
                    bottomPaddingPx = bottomPaddingPx
                )
                listState.requestScrollToItem(
                    pendingStartAnchorPosition,
                    -targetTopOffset
                )
                withFrameNanos { }
                val anchorItem =
                    listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == pendingStartAnchorPosition }
                        ?: run {
                            listState.scrollToItem(
                                pendingStartAnchorPosition,
                                -targetTopOffset
                            )
                            withFrameNanos { }
                            listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == pendingStartAnchorPosition }
                        }
                        ?: return@repeat
                val deltaPx = anchorItem.offset - targetTopOffset
                if (deltaPx != 0) {
                    listState.scrollBy(deltaPx.toFloat())
                    withFrameNanos { }
                }
                if (isRevealStable(targetTopOffset)) {
                    lastAppliedStartAnchorRequestId.intValue = requestId
                    onPendingStartAnchorHandled()
                    return@LaunchedEffect
                }
            }
            lastAppliedStartAnchorRequestId.intValue = requestId
            onPendingStartAnchorHandled()
        } finally {
            if (startAnchorScrollOwned) {
                onStartAnchorScrollFinished()
            }
        }
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = with(density) {
            PaddingValues(
                top = topPaddingPx.toDp(),
                bottom = bottomPaddingPx.toDp()
            )
        },
        userScrollEnabled = true
    ) {
        items(
            items = itemIds,
            key = { it }
        ) { itemId ->
            itemContent(itemId)
        }
        item(key = "bottom_footer") {
            Spacer(modifier = Modifier.height(1.dp))
        }
    }
}
