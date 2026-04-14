package com.nongjiqianwen

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
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
    pendingStartAnchorMessageId: String?,
    pendingStartAnchorRequestId: Int,
    currentPendingStartAnchorMeasuredBottomPx: () -> Int,
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
        pendingStartAnchorTargetBottomPx
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
            snapshotFlow {
                pendingStartAnchorTargetBottomPx > 0 &&
                    listState.layoutInfo.viewportSize.height > 0
            }.first { it }
            beginStartAnchorScrollIfNeeded()
            val anchorAlreadyVisible =
                listState.layoutInfo.visibleItemsInfo.any {
                    it.index == pendingStartAnchorPosition && it.size > 0
                }
            // Only do the coarse jump when the new assistant placeholder is still outside
            // the viewport. If it is already visible, skipping this avoids an extra
            // whole-list jump before the precise workline alignment.
            if (!anchorAlreadyVisible) {
                listState.scrollToItem(pendingStartAnchorPosition)
            }
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == pendingStartAnchorPosition }
                    ?.size
                    ?.let { it > 0 }
                    ?: false
            }.first { it }
            withFrameNanos { }
            snapshotFlow { currentPendingStartAnchorMeasuredBottomPx() }
                .first { it > 0 }
            withFrameNanos { }
            val measuredBottomPx = currentPendingStartAnchorMeasuredBottomPx()
            val scrollDeltaPx = measuredBottomPx - pendingStartAnchorTargetBottomPx
            // Use the real waiting host bottom instead of an estimated inset. This makes the
            // send-start anchor deterministic for both first-send and history-send cases.
            if (scrollDeltaPx != 0) {
                listState.scrollBy(scrollDeltaPx.toFloat())
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
            Spacer(
                modifier = with(density) {
                    Modifier.height(bottomFooterHeightPx.toDp())
                }
            )
        }
    }
}
