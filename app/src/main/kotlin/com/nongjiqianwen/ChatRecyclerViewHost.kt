package com.nongjiqianwen

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

internal class ChatRecyclerComposeAdapter(
    private val itemContent: @Composable (String) -> Unit
) : RecyclerView.Adapter<ChatRecyclerComposeAdapter.ComposeMessageViewHolder>() {

    private var itemIds: List<String> = emptyList()

    init {
        setHasStableIds(true)
    }

    fun submitIds(newIds: List<String>) {
        if (itemIds == newIds) return
        val previousIds = itemIds
        val nextIds = newIds.toList()
        val diffResult = DiffUtil.calculateDiff(
            object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = previousIds.size

                override fun getNewListSize(): Int = nextIds.size

                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    return previousIds[oldItemPosition] == nextIds[newItemPosition]
                }

                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    return true
                }
            }
        )
        itemIds = nextIds
        diffResult.dispatchUpdatesTo(this)
    }

    override fun getItemId(position: Int): Long = itemIds[position].hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ComposeMessageViewHolder {
        val composeView = ComposeView(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
            )
        }
        return ComposeMessageViewHolder(composeView)
    }

    override fun onBindViewHolder(holder: ComposeMessageViewHolder, position: Int) {
        val itemId = itemIds[position]
        holder.composeView.setContent {
            itemContent(itemId)
        }
    }

    override fun getItemCount(): Int = itemIds.size

    class ComposeMessageViewHolder(
        val composeView: ComposeView
    ) : RecyclerView.ViewHolder(composeView)
}

@Composable
internal fun ChatRecyclerViewHost(
    modifier: Modifier = Modifier,
    itemIds: List<String>,
    topPaddingPx: Int,
    bottomPaddingPx: Int,
    pendingStartAnchorMessageId: String?,
    pendingStartAnchorRequestId: Int,
    pendingStartAnchorFallbackHeightPx: Int,
    onPendingStartAnchorHandled: () -> Unit,
    onRecyclerReady: (RecyclerView, LinearLayoutManager) -> Unit,
    onScrollStateChanged: (RecyclerView, Int) -> Unit,
    onScrolled: (RecyclerView, Int, Int) -> Unit,
    itemContent: @Composable (String) -> Unit
) {
    val adapter = remember(itemContent) { ChatRecyclerComposeAdapter(itemContent) }
    val lastAppliedStartAnchorRequestId = remember { mutableIntStateOf(0) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = false
            }
            RecyclerView(context).apply {
                this.layoutManager = layoutManager
                itemAnimator = null
                clipToPadding = false
                overScrollMode = RecyclerView.OVER_SCROLL_NEVER
                setHasFixedSize(false)
                this.adapter = adapter
                setPadding(0, topPaddingPx, 0, bottomPaddingPx)
                addOnScrollListener(
                    object : RecyclerView.OnScrollListener() {
                        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                            onScrollStateChanged(recyclerView, newState)
                        }

                        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                            onScrolled(recyclerView, dx, dy)
                        }
                    }
                )
                onRecyclerReady(this, layoutManager)
            }
        },
        update = { recyclerView ->
            if (
                recyclerView.paddingTop != topPaddingPx ||
                recyclerView.paddingBottom != bottomPaddingPx
            ) {
                recyclerView.setPadding(0, topPaddingPx, 0, bottomPaddingPx)
            }
            adapter.submitIds(itemIds)
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return@AndroidView
            val shouldApplyPendingStartAnchor =
                pendingStartAnchorRequestId > 0 &&
                    pendingStartAnchorRequestId != lastAppliedStartAnchorRequestId.intValue &&
                    pendingStartAnchorMessageId != null &&
                    itemIds.lastOrNull() == pendingStartAnchorMessageId
            if (shouldApplyPendingStartAnchor) {
                lastAppliedStartAnchorRequestId.intValue = pendingStartAnchorRequestId
                val lastIndex = itemIds.lastIndex
                recyclerView.post {
                    val lastViewHeight = layoutManager.findViewByPosition(lastIndex)?.height
                        ?: pendingStartAnchorFallbackHeightPx
                    val targetBottom = (recyclerView.height - recyclerView.paddingBottom).coerceAtLeast(0)
                    val targetTopOffset = targetBottom - lastViewHeight
                    layoutManager.scrollToPositionWithOffset(lastIndex, targetTopOffset)
                    onPendingStartAnchorHandled()
                }
            }
            onRecyclerReady(recyclerView, layoutManager)
        }
    )
}
