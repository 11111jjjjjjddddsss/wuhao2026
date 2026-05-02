package com.nongjiqianwen

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput

internal fun Modifier.zoomableImagePreviewInput(
    key: Any,
    maxScale: Float = 5f,
    onTransformChanged: (scale: Float, offset: Offset) -> Unit
): Modifier = pointerInput(key) {
    var gestureScale = 1f
    var gestureOffset = Offset.Zero

    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var previousCentroid: Offset? = null
        var previousDistance = 0f

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val pressedChanges = event.changes.filter { it.pressed }
            if (pressedChanges.isEmpty()) break

            if (pressedChanges.size >= 2) {
                val centroid = pressedChanges.centroid()
                val distance = pressedChanges.averageDistanceTo(centroid)
                val lastCentroid = previousCentroid
                if (lastCentroid != null && previousDistance > 0f && distance > 0f) {
                    val zoom = distance / previousDistance
                    val pan = centroid - lastCentroid
                    val nextScale = (gestureScale * zoom).coerceIn(1f, maxScale)
                    gestureScale = nextScale
                    gestureOffset = if (nextScale <= 1.01f) Offset.Zero else gestureOffset + pan
                    onTransformChanged(gestureScale, gestureOffset)
                }
                previousCentroid = centroid
                previousDistance = distance
                pressedChanges.forEach { it.consume() }
            } else {
                previousCentroid = null
                previousDistance = 0f
                if (gestureScale > 1.01f) {
                    val change = pressedChanges.first()
                    val pan = change.position - change.previousPosition
                    if (pan != Offset.Zero) {
                        gestureOffset += pan
                        onTransformChanged(gestureScale, gestureOffset)
                        change.consume()
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
