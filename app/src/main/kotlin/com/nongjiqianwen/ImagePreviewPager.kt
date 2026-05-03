package com.nongjiqianwen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import me.saket.telephoto.zoomable.OverzoomEffect
import me.saket.telephoto.zoomable.ZoomLimit
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ImagePreviewPager(
    models: List<Any?>,
    initialPage: Int,
    contentDescription: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (models.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, models.lastIndex)
    ) { models.size }

    Box(
        modifier = modifier
            .fillMaxSize()
            .dismissImagePreviewOnQuickTap(onDismiss)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val zoomableState = rememberZoomableState(
                zoomSpec = ZoomSpec(
                    maximum = ZoomLimit(5f, OverzoomEffect.RubberBanding),
                    minimum = ZoomLimit(1f, OverzoomEffect.Disabled)
                )
            )
            ZoomableAsyncImage(
                model = models[page],
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                state = rememberZoomableImageState(zoomableState),
                contentPadding = PaddingValues(22.dp),
                modifier = Modifier.fillMaxSize()
            )
        }
        if (models.size > 1) {
            Text(
                text = "${pagerState.currentPage + 1}/${models.size}",
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 44.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0x66111111))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .zIndex(1f)
            )
        }
    }
}

private fun Modifier.dismissImagePreviewOnQuickTap(onDismiss: () -> Unit): Modifier =
    pointerInput(onDismiss) {
        awaitPointerEventScope {
            while (true) {
                val firstEvent = awaitPointerEvent(PointerEventPass.Initial)
                val initiallyPressed = firstEvent.changes.filter { it.pressed }
                val down = initiallyPressed.firstOrNull() ?: continue
                val startPosition = down.position
                val startTime = down.uptimeMillis
                var moved = false
                var multiTouch = initiallyPressed.size > 1 || firstEvent.changes.size > 1
                var released = false
                var pressedAtEnd = false

                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    pressedAtEnd = event.changes.any { it.pressed }
                    val relatedPointerCount = event.changes.count { it.pressed || it.previousPressed }
                    if (
                        relatedPointerCount > 1 ||
                        event.changes.any { it.pressed && !it.previousPressed }
                    ) {
                        multiTouch = true
                    }
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null) {
                        if (event.changes.none { it.pressed }) break
                        continue
                    }
                    if ((change.position - startPosition).getDistance() > viewConfiguration.touchSlop) {
                        moved = true
                    }
                    if (!change.pressed) {
                        released = true
                        pressedAtEnd = event.changes.any { it.pressed }
                        val durationMs = change.uptimeMillis - startTime
                        if (!multiTouch && !moved && durationMs <= viewConfiguration.longPressTimeoutMillis) {
                            onDismiss()
                        }
                        break
                    }
                }

                if (!released) {
                    continue
                }
                if (multiTouch && pressedAtEnd) {
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        pressedAtEnd = event.changes.any { it.pressed }
                    } while (pressedAtEnd)
                }
            }
        }
    }
