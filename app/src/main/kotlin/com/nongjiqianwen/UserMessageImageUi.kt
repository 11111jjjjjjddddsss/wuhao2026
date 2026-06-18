package com.nongjiqianwen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val USER_MESSAGE_IMAGE_MAX_COUNT = 4

private data class UserMessageImageSource(
    val local: String?,
    val remote: String?
) {
    val previewFallback: String?
        get() = local ?: remote
}

@Composable
internal fun UserMessageImageStrip(
    imageUris: List<String>,
    imageUrls: List<String>,
    userBubbleMaxWidth: Dp
) {
    val imageSources = remember(imageUris, imageUrls) {
        val localSources = imageUris.filter { it.isNotBlank() }
        val remoteSources = imageUrls.filter { it.isNotBlank() }
        val maxSourceCount = maxOf(localSources.size, remoteSources.size)
        (0 until maxSourceCount)
            .mapNotNull { index ->
                val local = localSources.getOrNull(index)
                val remote = remoteSources.getOrNull(index)
                if (local == null && remote == null) {
                    null
                } else {
                    UserMessageImageSource(local = local, remote = remote)
                }
            }
            .distinctBy { it.local ?: it.remote }
            .take(USER_MESSAGE_IMAGE_MAX_COUNT)
    }
    if (imageSources.isEmpty()) return
    var previewIndex by remember {
        mutableStateOf<Int?>(null)
    }
    val displaySources = remember { mutableStateMapOf<Int, String>() }
    val unavailableIndexes = remember { mutableStateMapOf<Int, Boolean>() }
    LaunchedEffect(imageSources) {
        displaySources.keys
            .filter { it !in imageSources.indices }
            .forEach { displaySources.remove(it) }
        unavailableIndexes.keys
            .filter { it !in imageSources.indices }
            .forEach { unavailableIndexes.remove(it) }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Column(
            modifier = Modifier.widthIn(max = userBubbleMaxWidth),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            imageSources.chunked(2).forEachIndexed { rowIndex, rowSources ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowSources.forEachIndexed { columnIndex, source ->
                        val sourceIndex = rowIndex * 2 + columnIndex
                        UserMessageImageThumb(
                            source = source,
                            onDisplaySourceChanged = { displayedSource, unavailable ->
                                if (displayedSource.isNullOrBlank()) {
                                    displaySources.remove(sourceIndex)
                                } else {
                                    displaySources[sourceIndex] = displayedSource
                                }
                                if (unavailable) {
                                    unavailableIndexes[sourceIndex] = true
                                } else {
                                    unavailableIndexes.remove(sourceIndex)
                                }
                            },
                            onPreviewImage = { previewIndex = sourceIndex }
                        )
                    }
                }
            }
        }
    }
    previewIndex?.let { index ->
        UserMessageImagePreviewDialog(
            sources = imageSources.mapIndexed { sourceIndex, source ->
                displaySources[sourceIndex] ?: source.previewFallback.orEmpty()
            },
            initialPage = index,
            unavailableIndexes = unavailableIndexes.keys.toSet(),
            onDismiss = { previewIndex = null }
        )
    }
}

@Composable
private fun UserMessageImageThumb(
    source: UserMessageImageSource,
    onDisplaySourceChanged: (String?, Boolean) -> Unit,
    onPreviewImage: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(source) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    var loadUnavailable by remember(source) { mutableStateOf(false) }
    LaunchedEffect(source) {
        loadUnavailable = false
        onDisplaySourceChanged(source.previewFallback, false)
        val localSource = source.local
        val remoteSource = source.remote
        val decoded = withContext(Dispatchers.IO) {
            val localDecoded = localSource?.let { context.decodeChatImagePreview(it) }
            if (localDecoded != null) {
                localSource to localDecoded
            } else {
                val remoteDecoded = remoteSource?.let { context.decodeChatImagePreview(it) }
                remoteSource to remoteDecoded
            }
        }
        bitmap = decoded.second
        loadUnavailable = decoded.second == null
        onDisplaySourceChanged(decoded.first ?: source.previewFallback, loadUnavailable)
    }
    Box(
        modifier = Modifier
            .size(112.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF0F1F3))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onPreviewImage() }
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!,
                contentDescription = "用户上传图片",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (loadUnavailable) {
                    UserMessageExpiredImagePlaceholder()
                } else {
                    UserMessageImagePlaceholderIcon(
                        tint = Color(0xFF8B8D93),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun UserMessageImagePreviewDialog(
    sources: List<String>,
    initialPage: Int,
    unavailableIndexes: Set<Int>,
    onDismiss: () -> Unit
) {
    if (sources.isEmpty()) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xE6000000))
        ) {
            ImagePreviewPager(
                models = sources,
                initialPage = initialPage,
                contentDescription = "用户上传图片预览",
                onDismiss = onDismiss,
                unavailablePages = unavailableIndexes
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .statusBarsPadding()
                    .padding(top = 10.dp, end = 22.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0x99111111))
                    .zIndex(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                UserMessagePreviewCloseIcon(tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
internal fun UserMessageExpiredImagePlaceholder() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        UserMessageImagePlaceholderIcon(
            tint = Color(0xFF8B8D93),
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = IMAGE_EXPIRED_THUMB_TEXT,
            color = Color(0xFF777C85),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun UserMessageImagePlaceholderIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.085f
        val left = size.width * 0.16f
        val top = size.height * 0.18f
        val right = size.width * 0.84f
        val bottom = size.height * 0.82f
        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(left, top),
            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                size.minDimension * 0.14f,
                size.minDimension * 0.14f
            ),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawCircle(
            color = tint,
            radius = size.minDimension * 0.065f,
            center = androidx.compose.ui.geometry.Offset(size.width * 0.65f, size.height * 0.36f)
        )
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.24f, size.height * 0.72f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.42f, size.height * 0.53f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.42f, size.height * 0.53f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.55f, size.height * 0.66f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.55f, size.height * 0.66f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.56f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
internal fun UserMessagePreviewCloseIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.13f
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.22f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.78f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.22f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.78f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}
