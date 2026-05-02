package com.nongjiqianwen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import me.saket.telephoto.zoomable.OverzoomEffect
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
    modifier: Modifier = Modifier
) {
    if (models.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, models.lastIndex)
    ) { models.size }

    Box(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val zoomableState = rememberZoomableState(
                zoomSpec = ZoomSpec(
                    maxZoomFactor = 5f,
                    minZoomFactor = 1f,
                    overzoomEffect = OverzoomEffect.RubberBanding
                )
            )
            ZoomableAsyncImage(
                model = models[page],
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                state = rememberZoomableImageState(zoomableState),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(22.dp)
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
