package com.nongjiqianwen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
internal fun ChatRecyclerViewHost(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    itemIds: List<String>,
    topPaddingPx: Int,
    bottomPaddingPx: Int,
    bottomFooterHeightPx: Int,
    itemContent: @Composable (String) -> Unit
) {
    val density = LocalDensity.current

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
