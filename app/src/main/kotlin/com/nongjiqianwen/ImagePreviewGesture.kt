package com.nongjiqianwen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.min

private const val IMAGE_PREVIEW_MIN_ZOOMED_SCALE = 1.01f
private const val IMAGE_PREVIEW_EDGE_HANDOFF_PX = 0.5f
private const val IMAGE_PREVIEW_EDGE_HANDOFF_RUBBER_PX = 18f
private const val IMAGE_PREVIEW_EDGE_RUBBER_BAND_PX = 72f
private const val IMAGE_PREVIEW_EDGE_RUBBER_FACTOR = 0.42f
private const val IMAGE_PREVIEW_ZOOMED_DRAG_GAIN = 1.12f
private const val IMAGE_PREVIEW_SNAP_BACK_MS = 90

internal fun Modifier.zoomableImagePreviewInput(
    key: Any,
    imageSize: IntSize,
    viewportSize: IntSize,
    canPageBefore: Boolean,
    canPageAfter: Boolean,
    maxScale: Float = 5f,
    onTransformChanged: (scale: Float, offset: Offset) -> Unit
): Modifier = pointerInput(key, imageSize, viewportSize) {
    var gestureScale = 1f
    var gestureOffset = Offset.Zero

    while (true) {
        awaitPointerEventScope {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var previousCentroid: Offset? = null
            var previousDistance = 0f
            var previousPointerCount = 0

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val pressedChanges = event.changes.filter { it.pressed }
                if (pressedChanges.isEmpty()) break

                if (pressedChanges.size >= 2) {
                    val centroid = pressedChanges.centroid()
                    val distance = pressedChanges.averageDistanceTo(centroid)
                    val lastCentroid = previousCentroid
                    if (
                        previousPointerCount == pressedChanges.size &&
                        lastCentroid != null &&
                        previousDistance > 0f &&
                        distance > 0f
                    ) {
                        val zoom = distance / previousDistance
                        val pan = centroid - lastCentroid
                        val oldScale = gestureScale
                        val nextScale = (oldScale * zoom).coerceIn(1f, maxScale)
                        val zoomChange = if (oldScale > 0f) nextScale / oldScale else 1f
                        val viewportCenter = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
                        val centroidFromCenter = centroid - viewportCenter
                        val rawOffset = if (nextScale <= IMAGE_PREVIEW_MIN_ZOOMED_SCALE) {
                            Offset.Zero
                        } else {
                            (gestureOffset - centroidFromCenter) * zoomChange + centroidFromCenter + pan
                        }
                        gestureScale = nextScale
                        gestureOffset = clampImagePreviewOffset(
                            offset = rawOffset,
                            scale = gestureScale,
                            imageSize = imageSize,
                            viewportSize = viewportSize
                        )
                        onTransformChanged(gestureScale, gestureOffset)
                    }
                    previousCentroid = centroid
                    previousDistance = distance
                    previousPointerCount = pressedChanges.size
                    pressedChanges.forEach { it.consume() }
                } else {
                    previousCentroid = null
                    previousDistance = 0f
                    previousPointerCount = pressedChanges.size
                    val change = pressedChanges.first()
                    val pan = change.position - change.previousPosition
                    if (pan != Offset.Zero) {
                        val horizontalDominant = abs(pan.x) >= abs(pan.y)
                        val hasPageInPanDirection = if (pan.x > 0f) canPageBefore else canPageAfter
                        if (gestureScale > IMAGE_PREVIEW_MIN_ZOOMED_SCALE) {
                            val imagePan = pan * IMAGE_PREVIEW_ZOOMED_DRAG_GAIN
                            val limit = imagePreviewOffsetLimit(
                                scale = gestureScale,
                                imageSize = imageSize,
                                viewportSize = viewportSize
                            )
                            val movingBeyondHorizontalEdge =
                                horizontalDominant &&
                                    abs(pan.x) > IMAGE_PREVIEW_EDGE_HANDOFF_PX &&
                                    isMovingBeyondHorizontalLimit(
                                        offsetX = gestureOffset.x,
                                        panX = imagePan.x,
                                        limitX = limit.x
                                    )
                            val shouldHandOffToPager =
                                movingBeyondHorizontalEdge &&
                                    hasPageInPanDirection &&
                                    abs(gestureOffset.x) > limit.x + IMAGE_PREVIEW_EDGE_HANDOFF_RUBBER_PX
                            if (!shouldHandOffToPager) {
                                gestureOffset = rubberBandImagePreviewOffset(
                                    offset = gestureOffset + imagePan,
                                    scale = gestureScale,
                                    imageSize = imageSize,
                                    viewportSize = viewportSize
                                )
                                onTransformChanged(gestureScale, gestureOffset)
                                change.consume()
                            }
                        } else if (
                            horizontalDominant &&
                            abs(pan.x) > IMAGE_PREVIEW_EDGE_HANDOFF_PX &&
                            !hasPageInPanDirection
                        ) {
                            gestureOffset = Offset(
                                x = rubberBandAxis(gestureOffset.x + pan.x, limit = 0f),
                                y = 0f
                            )
                            onTransformChanged(gestureScale, gestureOffset)
                            change.consume()
                        }
                    }
                }
            }
        }
        val targetOffset = clampImagePreviewOffset(
            offset = gestureOffset,
            scale = gestureScale,
            imageSize = imageSize,
            viewportSize = viewportSize
        )
        if ((targetOffset - gestureOffset).getDistance() > 0.5f) {
            val startOffset = gestureOffset
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = IMAGE_PREVIEW_SNAP_BACK_MS,
                    easing = FastOutSlowInEasing
                )
            ) { progress, _ ->
                gestureOffset = Offset(
                    x = startOffset.x + (targetOffset.x - startOffset.x) * progress,
                    y = startOffset.y + (targetOffset.y - startOffset.y) * progress
                )
                onTransformChanged(gestureScale, gestureOffset)
            }
        }
    }
}

private fun List<PointerInputChange>.centroid(): Offset {
    var x = 0f
    var y = 0f
    forEach { change ->
        x += change.position.x
        y += change.position.y
    }
    return Offset(x / size, y / size)
}

private fun List<PointerInputChange>.averageDistanceTo(centroid: Offset): Float {
    var distance = 0f
    forEach { change ->
        distance += (change.position - centroid).getDistance()
    }
    return distance / size
}

private fun isMovingBeyondHorizontalLimit(
    offsetX: Float,
    panX: Float,
    limitX: Float
): Boolean {
    if (limitX <= 0f) return true
    return (panX > 0f && offsetX >= limitX - IMAGE_PREVIEW_EDGE_HANDOFF_PX) ||
        (panX < 0f && offsetX <= -limitX + IMAGE_PREVIEW_EDGE_HANDOFF_PX)
}

private fun rubberBandImagePreviewOffset(
    offset: Offset,
    scale: Float,
    imageSize: IntSize,
    viewportSize: IntSize
): Offset {
    val limit = imagePreviewOffsetLimit(scale, imageSize, viewportSize)
    return Offset(
        x = rubberBandAxis(offset.x, limit.x),
        y = rubberBandAxis(offset.y, limit.y)
    )
}

private fun rubberBandAxis(value: Float, limit: Float): Float {
    return when {
        value > limit -> limit + ((value - limit) * IMAGE_PREVIEW_EDGE_RUBBER_FACTOR)
            .coerceAtMost(IMAGE_PREVIEW_EDGE_RUBBER_BAND_PX)
        value < -limit -> -limit - ((-limit - value) * IMAGE_PREVIEW_EDGE_RUBBER_FACTOR)
            .coerceAtMost(IMAGE_PREVIEW_EDGE_RUBBER_BAND_PX)
        else -> value
    }
}

private fun clampImagePreviewOffset(
    offset: Offset,
    scale: Float,
    imageSize: IntSize,
    viewportSize: IntSize
): Offset {
    val limit = imagePreviewOffsetLimit(scale, imageSize, viewportSize)
    return Offset(
        x = offset.x.coerceIn(-limit.x, limit.x),
        y = offset.y.coerceIn(-limit.y, limit.y)
    )
}

private fun imagePreviewOffsetLimit(
    scale: Float,
    imageSize: IntSize,
    viewportSize: IntSize
): Offset {
    if (
        scale <= IMAGE_PREVIEW_MIN_ZOOMED_SCALE ||
        imageSize.width <= 0 ||
        imageSize.height <= 0 ||
        viewportSize.width <= 0 ||
        viewportSize.height <= 0
    ) {
        return Offset.Zero
    }
    val fitScale = min(
        viewportSize.width.toFloat() / imageSize.width.toFloat(),
        viewportSize.height.toFloat() / imageSize.height.toFloat()
    )
    val fittedWidth = imageSize.width * fitScale
    val fittedHeight = imageSize.height * fitScale
    val maxOffsetX = ((fittedWidth * scale - viewportSize.width) / 2f).coerceAtLeast(0f)
    val maxOffsetY = ((fittedHeight * scale - viewportSize.height) / 2f).coerceAtLeast(0f)
    return Offset(maxOffsetX, maxOffsetY)
}
