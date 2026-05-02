package com.nongjiqianwen

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.min

internal fun Modifier.zoomableImagePreviewInput(
    key: Any,
    imageSize: IntSize,
    viewportSize: IntSize,
    maxScale: Float = 5f,
    onTransformChanged: (scale: Float, offset: Offset) -> Unit
): Modifier = pointerInput(key, imageSize, viewportSize) {
    var gestureScale = 1f
    var gestureOffset = Offset.Zero

    awaitEachGesture {
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
                    val rawOffset = if (nextScale <= 1.01f) {
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
                if (gestureScale > 1.01f) {
                    val change = pressedChanges.first()
                    val pan = change.position - change.previousPosition
                    if (pan != Offset.Zero) {
                        val nextOffset = clampImagePreviewOffset(
                            offset = gestureOffset + pan,
                            scale = gestureScale,
                            imageSize = imageSize,
                            viewportSize = viewportSize
                        )
                        val consumedPan = nextOffset - gestureOffset
                        val horizontalDominant = abs(pan.x) >= abs(pan.y)
                        val horizontalEdgeHandoff =
                            horizontalDominant &&
                                abs(pan.x) > 0.5f &&
                                abs(consumedPan.x) < 0.5f
                        if (!horizontalEdgeHandoff) {
                            gestureOffset = nextOffset
                            onTransformChanged(gestureScale, gestureOffset)
                            change.consume()
                        }
                    }
                }
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

private fun clampImagePreviewOffset(
    offset: Offset,
    scale: Float,
    imageSize: IntSize,
    viewportSize: IntSize
): Offset {
    if (
        scale <= 1.01f ||
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
    return Offset(
        x = offset.x.coerceIn(-maxOffsetX, maxOffsetX),
        y = offset.y.coerceIn(-maxOffsetY, maxOffsetY)
    )
}
