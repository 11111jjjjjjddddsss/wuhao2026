package com.nongjiqianwen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity

@Composable
internal fun <T> ChatRecyclerViewHost(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    items: List<T>,
    itemKey: (T) -> Any,
    itemContentType: (T) -> Any? = { null },
    topPaddingPx: Int,
    bottomPaddingPx: Int,
    itemContent: @Composable (T) -> Unit
) {
    val density = LocalDensity.current
    val contentPadding = remember(density, topPaddingPx, bottomPaddingPx) {
        with(density) {
            PaddingValues(
                top = topPaddingPx.toDp(),
                bottom = bottomPaddingPx.toDp()
            )
        }
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.Bottom,
        contentPadding = contentPadding,
        userScrollEnabled = true
    ) {
        items(
            items = items,
            key = itemKey,
            contentType = itemContentType
        ) { item ->
            itemContent(item)
        }
    }
}
