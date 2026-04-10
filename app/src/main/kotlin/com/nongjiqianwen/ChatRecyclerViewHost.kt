package com.nongjiqianwen

import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.AdapterListUpdateCallback
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.atomic.AtomicInteger

private const val CHAT_SCROLL_TRACE_TAG = "ChatScrollTrace"

private fun RecyclerView.traceLastItemBottomPx(): Int {
    val activeLayoutManager = layoutManager as? LinearLayoutManager ?: return -1
    val lastIndex = (adapter?.itemCount ?: 0) - 1
    if (lastIndex < 0) return -1
    return activeLayoutManager.findViewByPosition(lastIndex)?.bottom ?: -1
}

private class TracingLinearLayoutManager(
    context: android.content.Context
) : LinearLayoutManager(context) {

    override fun onLayoutChildren(
        recycler: RecyclerView.Recycler?,
        state: RecyclerView.State?
    ) {
        super.onLayoutChildren(recycler, state)
        if (!BuildConfig.DEBUG) return
        val lastIndex = itemCount - 1
        val lastBottom = if (lastIndex >= 0) {
            findViewByPosition(lastIndex)?.bottom ?: -1
        } else {
            -1
        }
        Log.d(
            CHAT_SCROLL_TRACE_TAG,
            "layout_children t=${SystemClock.uptimeMillis()} itemCount=$itemCount preLayout=${state?.isPreLayout} lastItemBottom=$lastBottom"
        )
    }
}

internal class ChatRecyclerComposeAdapter(
    private val itemContent: @Composable (String) -> Unit
) : RecyclerView.Adapter<ChatRecyclerComposeAdapter.ComposeMessageViewHolder>() {

    companion object {
        private val diffDispatchCounter = AtomicInteger(0)
    }

    private var itemIds: List<String> = emptyList()

    init {
        setHasStableIds(true)
    }

    fun submitIds(newIds: List<String>) {
        if (itemIds == newIds) return
        val previousIds = itemIds
        val nextIds = newIds.toList()
        val dispatchId = diffDispatchCounter.incrementAndGet()
        if (BuildConfig.DEBUG) {
            Log.d(
                CHAT_SCROLL_TRACE_TAG,
                "diff_submit#$dispatchId t=${SystemClock.uptimeMillis()} oldSize=${previousIds.size} newSize=${nextIds.size} oldLast=${previousIds.lastOrNull()} newLast=${nextIds.lastOrNull()}"
            )
        }
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
        val adapterCallback = AdapterListUpdateCallback(this)
        diffResult.dispatchUpdatesTo(
            object : ListUpdateCallback {
                override fun onInserted(position: Int, count: Int) {
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            CHAT_SCROLL_TRACE_TAG,
                            "diff_dispatch#$dispatchId t=${SystemClock.uptimeMillis()} op=insert position=$position count=$count"
                        )
                    }
                    adapterCallback.onInserted(position, count)
                }

                override fun onRemoved(position: Int, count: Int) {
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            CHAT_SCROLL_TRACE_TAG,
                            "diff_dispatch#$dispatchId t=${SystemClock.uptimeMillis()} op=remove position=$position count=$count"
                        )
                    }
                    adapterCallback.onRemoved(position, count)
                }

                override fun onMoved(fromPosition: Int, toPosition: Int) {
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            CHAT_SCROLL_TRACE_TAG,
                            "diff_dispatch#$dispatchId t=${SystemClock.uptimeMillis()} op=move from=$fromPosition to=$toPosition"
                        )
                    }
                    adapterCallback.onMoved(fromPosition, toPosition)
                }

                override fun onChanged(position: Int, count: Int, payload: Any?) {
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            CHAT_SCROLL_TRACE_TAG,
                            "diff_dispatch#$dispatchId t=${SystemClock.uptimeMillis()} op=change position=$position count=$count payload=${payload != null}"
                        )
                    }
                    adapterCallback.onChanged(position, count, payload)
                }
            }
        )
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
    onRecyclerReady: (RecyclerView, LinearLayoutManager) -> Unit,
    onScrollStateChanged: (RecyclerView, Int) -> Unit,
    onScrolled: (RecyclerView, Int, Int) -> Unit,
    itemContent: @Composable (String) -> Unit
) {
    val adapter = remember(itemContent) { ChatRecyclerComposeAdapter(itemContent) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val layoutManager = TracingLinearLayoutManager(context).apply {
                stackFromEnd = true
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
                if (BuildConfig.DEBUG) {
                    addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                        val recyclerView = view as? RecyclerView ?: return@addOnLayoutChangeListener
                        Log.d(
                            CHAT_SCROLL_TRACE_TAG,
                            "recycler_layout_change t=${SystemClock.uptimeMillis()} itemCount=${recyclerView.adapter?.itemCount ?: 0} lastItemBottom=${recyclerView.traceLastItemBottomPx()} paddingBottom=${recyclerView.paddingBottom}"
                        )
                    }
                }
                onRecyclerReady(this, layoutManager)
            }
        },
        update = { recyclerView ->
            adapter.submitIds(itemIds)
            if (
                recyclerView.paddingTop != topPaddingPx ||
                recyclerView.paddingBottom != bottomPaddingPx
            ) {
                recyclerView.setPadding(0, topPaddingPx, 0, bottomPaddingPx)
            }
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return@AndroidView
            onRecyclerReady(recyclerView, layoutManager)
        }
    )
}
