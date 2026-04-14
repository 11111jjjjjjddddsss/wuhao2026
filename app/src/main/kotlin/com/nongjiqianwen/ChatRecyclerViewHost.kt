package com.nongjiqianwen

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
    pendingStartAnchorScrollOffsetPx: Int,
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
        pendingStartAnchorScrollOffsetPx
    ) {
        if (pendingStartAnchorRequestId <= 0) return@LaunchedEffect
        if (pendingStartAnchorRequestId == lastAppliedStartAnchorRequestId.intValue) {
            return@LaunchedEffect
        }
        val pendingStartAnchorPosition =
            pendingStartAnchorMessageId?.let(itemIds::indexOf)?.takeIf { it >= 0 }
                ?: return@LaunchedEffect
        if (pendingStartAnchorScrollOffsetPx == Int.MIN_VALUE) {
            return@LaunchedEffect
        }

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
            snapshotFlow { listState.layoutInfo.viewportSize.height > 0 }.first { it }

            beginStartAnchorScrollIfNeeded()
            // Single-shot send-start alignment. The workline and waiting-host height are both
            // frozen before this point, so we can jump straight to the final offset without a
            // second-frame measured scrollBy correction.
            listState.scrollToItem(
                index = pendingStartAnchorPosition,
                scrollOffset = pendingStartAnchorScrollOffsetPx
            )

            lastAppliedStartAnchorRequestId.intValue = pendingStartAnchorRequestId
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
