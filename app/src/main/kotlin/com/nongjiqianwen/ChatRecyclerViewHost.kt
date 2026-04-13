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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first

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
    // The send-start anchor is the assistant placeholder's visible bottom edge:
    // the waiting ball should land on the workline, while the user bubble stays above it.
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
    bottomFooterHeightPx: Int,
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
        var startAnchorScrollOwned = false

        fun beginStartAnchorScrollIfNeeded() {
            if (startAnchorScrollOwned) return
            onStartAnchorScrollStarted()
            startAnchorScrollOwned = true
        }

        try {
            snapshotFlow {
                listState.layoutInfo.totalItemsCount >= itemIds.size &&
                    itemIds.getOrNull(pendingStartAnchorPosition) == pendingStartAnchorMessageId
            }.first { it }
            withFrameNanos { }
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
            // Keep only one direct send-start positioning pass. If the waiting ball still
            // lands low after this, the remaining issue is elsewhere in the chain.
            listState.scrollToItem(
                pendingStartAnchorPosition,
                -targetTopOffset
            )
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
            Spacer(
                modifier = with(density) {
                    Modifier.height(bottomFooterHeightPx.toDp())
                }
            )
        }
    }
}
