package com.nongjiqianwen

import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.drawToBitmap
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

private data class StartAnchorSnapshotHandle(
    val hostView: View,
    val drawable: BitmapDrawable
)

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

private fun resolvePendingStartAnchorTargetTopPx(
    recyclerView: RecyclerView,
    layoutManager: LinearLayoutManager,
    pendingStartAnchorPosition: Int,
    pendingStartAnchorTargetBottomPx: Int,
    pendingStartAnchorEstimatedHeightPx: Int
): Int {
    val viewportTopPx = recyclerView.paddingTop
    val anchorHeightPx =
        layoutManager.findViewByPosition(pendingStartAnchorPosition)
            ?.height
            ?.takeIf { it > 0 }
            ?: pendingStartAnchorEstimatedHeightPx.coerceAtLeast(0)
    val targetBottomPx =
        pendingStartAnchorTargetBottomPx.takeIf { it > 0 }
            ?: (recyclerView.height - recyclerView.paddingBottom)
    return (targetBottomPx - anchorHeightPx)
        .coerceAtLeast(viewportTopPx)
}

@Composable
internal fun ChatRecyclerViewHost(
    modifier: Modifier = Modifier,
    itemIds: List<String>,
    topPaddingPx: Int,
    bottomPaddingPx: Int,
    pendingStartAnchorTargetBottomPx: Int,
    pendingStartAnchorEstimatedHeightPx: Int,
    pendingStartAnchorMessageId: String?,
    pendingStartAnchorRequestId: Int,
    onPendingStartAnchorHandled: () -> Unit,
    onRecyclerReady: (RecyclerView, LinearLayoutManager) -> Unit,
    onScrollStateChanged: (RecyclerView, Int) -> Unit,
    onScrolled: (RecyclerView, Int, Int) -> Unit,
    itemContent: @Composable (String) -> Unit
) {
    val adapter = remember(itemContent) { ChatRecyclerComposeAdapter(itemContent) }
    val lastAppliedStartAnchorRequestId = remember { mutableIntStateOf(0) }
    val activeStartAnchorRequestId = remember { mutableIntStateOf(0) }
    val activeStartAnchorSnapshot = remember { mutableStateOf<StartAnchorSnapshotHandle?>(null) }
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
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return@AndroidView
            fun clearStartAnchorSnapshot() {
                val snapshot = activeStartAnchorSnapshot.value ?: return
                snapshot.hostView.overlay.remove(snapshot.drawable)
                activeStartAnchorSnapshot.value = null
            }

            fun freezeRecyclerVisualSnapshot() {
                clearStartAnchorSnapshot()
                if (
                    recyclerView.width <= 0 ||
                    recyclerView.height <= 0 ||
                    recyclerView.childCount == 0
                ) {
                    return
                }
                val snapshotBitmap = runCatching { recyclerView.drawToBitmap() }.getOrNull() ?: return
                val overlayHost = recyclerView.rootView ?: recyclerView
                val recyclerLocation = IntArray(2)
                val hostLocation = IntArray(2)
                recyclerView.getLocationOnScreen(recyclerLocation)
                overlayHost.getLocationOnScreen(hostLocation)
                val left = recyclerLocation[0] - hostLocation[0]
                val top = recyclerLocation[1] - hostLocation[1]
                val snapshotDrawable = BitmapDrawable(recyclerView.resources, snapshotBitmap).apply {
                    setBounds(left, top, left + recyclerView.width, top + recyclerView.height)
                }
                overlayHost.overlay.add(snapshotDrawable)
                activeStartAnchorSnapshot.value = StartAnchorSnapshotHandle(
                    hostView = overlayHost,
                    drawable = snapshotDrawable
                )
            }

            if (
                recyclerView.paddingTop != topPaddingPx ||
                recyclerView.paddingBottom != bottomPaddingPx
            ) {
                recyclerView.setPadding(0, topPaddingPx, 0, bottomPaddingPx)
            }
            val pendingStartAnchorPosition =
                pendingStartAnchorMessageId?.let(itemIds::indexOf)?.takeIf { it >= 0 } ?: -1
            if (pendingStartAnchorRequestId <= 0 || pendingStartAnchorPosition < 0) {
                activeStartAnchorRequestId.intValue = 0
                clearStartAnchorSnapshot()
            }
            val shouldApplyPendingStartAnchor =
                pendingStartAnchorRequestId > 0 &&
                    pendingStartAnchorRequestId != lastAppliedStartAnchorRequestId.intValue &&
                    pendingStartAnchorRequestId != activeStartAnchorRequestId.intValue &&
                    pendingStartAnchorPosition >= 0
            if (shouldApplyPendingStartAnchor) {
                val requestId = pendingStartAnchorRequestId
                activeStartAnchorRequestId.intValue = requestId
                var observerConsumed = false
                var remainingAlignmentRetries = 2
                var remainingRevealValidationFrames = 3
                var lastObservedAnchorTop = Int.MIN_VALUE
                var lastObservedPrecedingBottom = Int.MIN_VALUE
                var stableGeometryFrames = 0

                fun finishStartAnchorHandling() {
                    clearStartAnchorSnapshot()
                    activeStartAnchorRequestId.intValue = 0
                    lastAppliedStartAnchorRequestId.intValue = requestId
                    onPendingStartAnchorHandled()
                }

                fun isRevealStable(targetTopOffset: Int): Boolean {
                    val anchorView = layoutManager.findViewByPosition(pendingStartAnchorPosition)
                    val precedingView =
                        (pendingStartAnchorPosition - 1)
                            .takeIf { it >= 0 }
                            ?.let(layoutManager::findViewByPosition)
                    val anchorSettled =
                        anchorView != null &&
                            abs(anchorView.top - targetTopOffset) <= 5
                    val precedingItemReady =
                        pendingStartAnchorPosition <= 0 ||
                            (precedingView != null && precedingView.height > 0)
                    if (!anchorSettled || !precedingItemReady || anchorView == null) {
                        stableGeometryFrames = 0
                        lastObservedAnchorTop = Int.MIN_VALUE
                        lastObservedPrecedingBottom = Int.MIN_VALUE
                        return false
                    }
                    val currentAnchorTop = anchorView.top
                    val currentPrecedingBottom =
                        precedingView?.bottom ?: Int.MIN_VALUE
                    stableGeometryFrames =
                        if (
                            currentAnchorTop == lastObservedAnchorTop &&
                            currentPrecedingBottom == lastObservedPrecedingBottom
                        ) {
                            stableGeometryFrames + 1
                        } else {
                            1
                        }
                    lastObservedAnchorTop = currentAnchorTop
                    lastObservedPrecedingBottom = currentPrecedingBottom
                    return stableGeometryFrames >= 2
                }

                fun scheduleRevealValidation() {
                    if (activeStartAnchorRequestId.intValue != requestId) return
                    val viewTreeObserver = recyclerView.viewTreeObserver
                    if (!viewTreeObserver.isAlive) {
                        finishStartAnchorHandling()
                        return
                    }
                    val listener = object : ViewTreeObserver.OnPreDrawListener {
                        override fun onPreDraw(): Boolean {
                            if (viewTreeObserver.isAlive) {
                                recyclerView.viewTreeObserver.removeOnPreDrawListener(this)
                            }
                            if (activeStartAnchorRequestId.intValue != requestId) {
                                return true
                            }
                            val targetTopOffset = resolvePendingStartAnchorTargetTopPx(
                                recyclerView = recyclerView,
                                layoutManager = layoutManager,
                                pendingStartAnchorPosition = pendingStartAnchorPosition,
                                pendingStartAnchorTargetBottomPx = pendingStartAnchorTargetBottomPx,
                                pendingStartAnchorEstimatedHeightPx = pendingStartAnchorEstimatedHeightPx
                            )
                            if (!isRevealStable(targetTopOffset) && remainingRevealValidationFrames > 0) {
                                remainingRevealValidationFrames -= 1
                                scheduleRevealValidation()
                                return true
                            }
                            finishStartAnchorHandling()
                            return true
                        }
                    }
                    viewTreeObserver.addOnPreDrawListener(listener)
                }

                fun scheduleStartAnchorAlignment() {
                    if (activeStartAnchorRequestId.intValue != requestId) return
                    val targetTopOffset = resolvePendingStartAnchorTargetTopPx(
                        recyclerView = recyclerView,
                        layoutManager = layoutManager,
                        pendingStartAnchorPosition = pendingStartAnchorPosition,
                        pendingStartAnchorTargetBottomPx = pendingStartAnchorTargetBottomPx,
                        pendingStartAnchorEstimatedHeightPx = pendingStartAnchorEstimatedHeightPx
                    )
                    layoutManager.scrollToPositionWithOffset(
                        pendingStartAnchorPosition,
                        targetTopOffset
                    )
                    val viewTreeObserver = recyclerView.viewTreeObserver
                    if (!viewTreeObserver.isAlive) {
                        clearStartAnchorSnapshot()
                        activeStartAnchorRequestId.intValue = 0
                        return
                    }
                    val listener = object : ViewTreeObserver.OnPreDrawListener {
                        override fun onPreDraw(): Boolean {
                            if (viewTreeObserver.isAlive) {
                                recyclerView.viewTreeObserver.removeOnPreDrawListener(this)
                            }
                            if (activeStartAnchorRequestId.intValue != requestId) {
                                return true
                            }
                            val anchorView = layoutManager.findViewByPosition(pendingStartAnchorPosition)
                            if (anchorView == null) {
                                if (remainingAlignmentRetries > 0) {
                                    remainingAlignmentRetries -= 1
                                    scheduleStartAnchorAlignment()
                                    return false
                                }
                                clearStartAnchorSnapshot()
                                activeStartAnchorRequestId.intValue = 0
                                return true
                            }
                            if (anchorView.top != targetTopOffset && remainingAlignmentRetries > 0) {
                                remainingAlignmentRetries -= 1
                                scheduleStartAnchorAlignment()
                                return false
                            }
                            if (isRevealStable(targetTopOffset)) {
                                finishStartAnchorHandling()
                            } else {
                                scheduleRevealValidation()
                            }
                            return true
                        }
                    }
                    viewTreeObserver.addOnPreDrawListener(listener)
                }

                freezeRecyclerVisualSnapshot()
                val dataObserver = object : RecyclerView.AdapterDataObserver() {
                    private fun consume() {
                        if (observerConsumed) return
                        observerConsumed = true
                        runCatching { adapter.unregisterAdapterDataObserver(this) }
                        scheduleStartAnchorAlignment()
                    }

                    override fun onChanged() = consume()

                    override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = consume()

                    override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = consume()

                    override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) = consume()

                        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) = consume()
                }
                val initialTargetTopOffset = resolvePendingStartAnchorTargetTopPx(
                    recyclerView = recyclerView,
                    layoutManager = layoutManager,
                    pendingStartAnchorPosition = pendingStartAnchorPosition,
                    pendingStartAnchorTargetBottomPx = pendingStartAnchorTargetBottomPx,
                    pendingStartAnchorEstimatedHeightPx = pendingStartAnchorEstimatedHeightPx
                )
                layoutManager.scrollToPositionWithOffset(
                    pendingStartAnchorPosition,
                    initialTargetTopOffset
                )
                adapter.registerAdapterDataObserver(dataObserver)
                adapter.submitIds(itemIds)
                if (!observerConsumed) {
                    runCatching { adapter.unregisterAdapterDataObserver(dataObserver) }
                    scheduleStartAnchorAlignment()
                }
            } else {
                if (activeStartAnchorRequestId.intValue == 0) {
                    clearStartAnchorSnapshot()
                }
                adapter.submitIds(itemIds)
            }
            onRecyclerReady(recyclerView, layoutManager)
        }
    )
}
