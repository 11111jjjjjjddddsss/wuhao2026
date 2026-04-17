package com.nongjiqianwen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
internal fun ChatRecyclerViewHost(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    itemIds: List<String>,
    topPaddingPx: Int,
    bottomPaddingPx: () -> Int,
    itemContent: @Composable (String) -> Unit
) {
    val density = LocalDensity.current

    LazyColumn(
        modifier = modifier,
        state = listState,
        reverseLayout = true,
        contentPadding = with(density) {
            PaddingValues(
                top = topPaddingPx.toDp()
            )
        },
        userScrollEnabled = true
    ) {
        item(key = "__bottom_padding_spacer__") {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.dp)
                    .layout { measurable, constraints ->
                        val dynamicBottomPaddingPx = bottomPaddingPx().coerceAtLeast(0)
                        val placeable = measurable.measure(
                            constraints.copy(
                                minHeight = dynamicBottomPaddingPx,
                                maxHeight = dynamicBottomPaddingPx
                            )
                        )
                        layout(placeable.width, placeable.height) {
                            placeable.placeRelative(0, 0)
                        }
                    }
            )
        }
        items(
            items = itemIds.asReversed(),
            key = { it }
        ) { itemId ->
            itemContent(itemId)
        }
    }
}
