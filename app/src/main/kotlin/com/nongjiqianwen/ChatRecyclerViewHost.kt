package com.nongjiqianwen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.max

@Composable
internal fun ChatRecyclerViewHost(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    itemIds: List<String>,
    topPaddingPx: Int,
    bottomPaddingState: State<Int>,
    itemContent: @Composable (String) -> Unit
) {
    val density = LocalDensity.current

    Layout(
        modifier = modifier,
        content = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                reverseLayout = true,
                contentPadding = with(density) {
                    PaddingValues(
                        top = topPaddingPx.toDp(),
                        bottom = 0.dp
                    )
                },
                userScrollEnabled = true
            ) {
                items(
                    items = itemIds.asReversed(),
                    key = { it }
                ) { itemId ->
                    itemContent(itemId)
                }
            }
        }
    ) { measurables, constraints ->
        val bottomPaddingPx = bottomPaddingState.value.coerceAtLeast(0)
        val lazyColumnConstraints =
            constraints.copy(
                minHeight = 0,
                maxHeight = (constraints.maxHeight - bottomPaddingPx).coerceAtLeast(0)
            )
        val placeable = measurables.single().measure(lazyColumnConstraints)
        val layoutWidth = placeable.width.coerceIn(constraints.minWidth, constraints.maxWidth)
        val layoutHeight =
            max(placeable.height + bottomPaddingPx, constraints.minHeight)
                .coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(layoutWidth, layoutHeight) {
            placeable.placeRelative(0, 0)
        }
    }
}
