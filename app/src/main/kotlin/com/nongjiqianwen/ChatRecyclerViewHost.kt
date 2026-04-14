package com.nongjiqianwen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

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

    SideEffect {
        if (pendingStartAnchorRequestId <= 0) return@SideEffect
        if (pendingStartAnchorRequestId == lastAppliedStartAnchorRequestId.intValue) {
            return@SideEffect
        }
        val pendingStartAnchorPosition =
            pendingStartAnchorMessageId?.let(itemIds::indexOf)?.takeIf { it >= 0 }
                ?: return@SideEffect
        if (pendingStartAnchorScrollOffsetPx == Int.MIN_VALUE) {
            return@SideEffect
        }
        onStartAnchorScrollStarted()
        try {
            // requestScrollToItem writes the target directly into the next lazy-list remeasure,
            // which avoids the extra frame where the list first keeps the old top anchor and
            // only then applies our send-start offset.
            listState.requestScrollToItem(
                index = pendingStartAnchorPosition,
                scrollOffset = pendingStartAnchorScrollOffsetPx
            )
            lastAppliedStartAnchorRequestId.intValue = pendingStartAnchorRequestId
            onPendingStartAnchorHandled()
        } finally {
            onStartAnchorScrollFinished()
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
