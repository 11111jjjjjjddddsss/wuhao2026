package com.nongjiqianwen

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal data class InputSelectionToolbarState(
    val anchorXRatio: Float,
    val onCopyRequested: (() -> Unit)?,
    val onPasteRequested: (() -> Unit)?,
    val onCutRequested: (() -> Unit)?,
    val onSelectAllRequested: (() -> Unit)?
)

@Immutable
internal data class ComposerImageAttachment(
    val uri: String
)

private fun composerPreviewInSampleSize(width: Int, height: Int, targetSize: Int): Int {
    var sampleSize = 1
    if (height > targetSize || width > targetSize) {
        var halfHeight = height / 2
        var halfWidth = width / 2
        while (halfHeight / sampleSize >= targetSize && halfWidth / sampleSize >= targetSize) {
            sampleSize *= 2
        }
    }
    return sampleSize.coerceAtLeast(1)
}

private fun decodeComposerPreviewBitmap(context: Context, uriString: String): ImageBitmap? {
    return runCatching {
        val uri = Uri.parse(uriString)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = composerPreviewInSampleSize(bounds.outWidth, bounds.outHeight, targetSize = 256)
        }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOptions)?.asImageBitmap()
        }
    }.getOrNull()
}

internal enum class SendBlockReason {
    None,
    EmptyInput,
    Streaming,
    InputTooLong,
    QuotaExhausted
}

internal data class SendGateState(
    val canPress: Boolean,
    val canSubmit: Boolean,
    val blockReason: SendBlockReason,
    val activeAppearance: Boolean = canPress
)

internal fun buildSendGateState(
    rawInput: String,
    isStreaming: Boolean,
    exceedsInputLimit: Boolean,
    quotaExhausted: Boolean = false,
    hasImages: Boolean = false
): SendGateState {
    val hasText = rawInput.trim().isNotEmpty()
    val hasContent = hasText || hasImages
    return when {
        !hasContent -> SendGateState(
            canPress = false,
            canSubmit = false,
            blockReason = SendBlockReason.EmptyInput,
            activeAppearance = false
        )
        isStreaming -> SendGateState(
            canPress = false,
            canSubmit = false,
            blockReason = SendBlockReason.Streaming,
            activeAppearance = false
        )
        quotaExhausted -> SendGateState(
            canPress = true,
            canSubmit = false,
            blockReason = SendBlockReason.QuotaExhausted,
            activeAppearance = false
        )
        exceedsInputLimit -> SendGateState(
            canPress = true,
            canSubmit = false,
            blockReason = SendBlockReason.InputTooLong
        )
        else -> SendGateState(
            canPress = true,
            canSubmit = true,
            blockReason = SendBlockReason.None
        )
    }
}

@Composable
internal fun ChatComposerBottomBar(
    inputValue: TextFieldValue,
    inputSelectionColors: TextSelectionColors,
    inputTextToolbar: TextToolbar,
    inputFieldFocused: Boolean,
    suppressInputCursor: Boolean,
    composerSettlingMinHeightPx: Int,
    composerSettlingChromeHeightPx: Int,
    startupInputContentHeightEstimatePx: Int,
    inputChromeRowHeightPx: Int,
    imeVisible: Boolean,
    isStreamingOrSettling: Boolean,
    inputMaxChars: Int,
    chromeMaxWidth: Dp,
    inputChromeHorizontalPadding: Dp,
    inputChromeBottomPadding: Dp,
    addButtonSize: Dp,
    addIconSize: Dp,
    sendButtonSize: Dp,
    inputBarHeight: Dp,
    inputBarMaxHeight: Dp,
    inputChromeSurface: Color,
    inputChromeBorder: Color,
    inputFieldSurface: Color,
    inputFieldBorder: Color,
    overlayHintText: String?,
    quotaExhausted: Boolean,
    selectedImages: List<ComposerImageAttachment>,
    hostModifier: Modifier = Modifier,
    onChromeMeasured: (Int) -> Unit,
    onChromeBoundsChanged: (Rect) -> Unit,
    onInputBoundsChanged: (Rect) -> Unit,
    onInputFocused: (Boolean) -> Unit,
    onInputContentHeightChanged: (Int) -> Unit,
    onInputValueChange: (TextFieldValue) -> Unit,
    onInputLimitExceeded: () -> Unit,
    onQuotaExceeded: () -> Unit,
    onAddClick: () -> Unit,
    onRemoveImage: (ComposerImageAttachment) -> Unit,
    onSendClick: () -> Unit
) {
    val density = LocalDensity.current
    val inputLimitHintOffsetPx = with(density) { 14.dp.roundToPx() } + inputChromeRowHeightPx
    val composerSettlingChromeMinHeight = with(density) {
        composerSettlingChromeHeightPx.coerceAtLeast(0).toDp()
    }
    val sendGate = buildSendGateState(
        rawInput = inputValue.text,
        isStreaming = isStreamingOrSettling,
        exceedsInputLimit = inputValue.text.length > inputMaxChars,
        quotaExhausted = quotaExhausted,
        hasImages = selectedImages.isNotEmpty()
    )
    val canPressSend = sendGate.canPress
    val canSend = sendGate.canSubmit
    val actionBg = if (sendGate.activeAppearance) Color(0xFF111111) else Color(0xFFD3D4D6)
    val actionTint = if (sendGate.activeAppearance) Color.White else Color(0xFF7F8083)
    val inputSelectionBottomClearance = if (inputValue.selection.collapsed) 0.dp else 34.dp

    Box(modifier = hostModifier.background(Color.Transparent)) {
        if (overlayHintText != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xEE111111),
                border = BorderStroke(0.8.dp, Color.Black),
                shadowElevation = 1.2.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = chromeMaxWidth)
                    .padding(
                        start = inputChromeHorizontalPadding,
                        end = inputChromeHorizontalPadding
                    )
                    .offset { IntOffset(0, -inputLimitHintOffsetPx) }
            ) {
                Text(
                    text = overlayHintText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = chromeMaxWidth)
                .fillMaxWidth()
        ) {
            ComposerChromeRow(
                modifier = Modifier
                    .fillMaxWidth()
                .heightIn(min = composerSettlingChromeMinHeight)
                .onSizeChanged { onChromeMeasured(it.height) }
                .onGloballyPositioned { coordinates ->
                    onChromeBoundsChanged(coordinates.boundsInWindow())
                }
                .padding(
                    start = inputChromeHorizontalPadding,
                    end = inputChromeHorizontalPadding,
                    top = 0.dp,
                    bottom = inputChromeBottomPadding
                ),
            addButtonSize = addButtonSize,
            addIconSize = addIconSize,
            sendButtonSize = sendButtonSize,
            inputChromeSurface = inputChromeSurface,
            inputChromeBorder = inputChromeBorder,
            inputFieldSurface = inputFieldSurface,
            inputFieldBorder = inputFieldBorder,
            inputBarHeight = inputBarHeight,
            inputBarMaxHeight = inputBarMaxHeight,
            onAddClick = onAddClick,
            attachmentsContent = if (selectedImages.isNotEmpty()) {
                {
                    ComposerImagePreviewStrip(
                        images = selectedImages,
                        onRemoveImage = onRemoveImage
                    )
                }
            } else {
                null
            },
            inputShellModifier = Modifier.onGloballyPositioned { coordinates ->
                onInputBoundsChanged(coordinates.boundsInWindow())
            },
            inputContent = {
                CompositionLocalProvider(
                    LocalTextSelectionColors provides inputSelectionColors,
                    LocalTextToolbar provides inputTextToolbar
                ) {
                    ChatInputField(
                        value = inputValue,
                        focused = inputFieldFocused,
                        suppressCursor = suppressInputCursor,
                        settlingMinHeightPx = composerSettlingMinHeightPx,
                        suppressPlaceholder =
                            (imeVisible && composerSettlingMinHeightPx > 0) ||
                                composerSettlingChromeHeightPx > 0,
                        onFocusChanged = { focused ->
                            onInputFocused(focused)
                            if (focused) {
                                onInputContentHeightChanged(
                                    startupInputContentHeightEstimatePx.coerceAtLeast(composerSettlingMinHeightPx)
                                )
                            }
                        },
                        onContentHeightChanged = onInputContentHeightChanged,
                        placeholderText = if (selectedImages.isNotEmpty()) {
                            "补充作物、部位或症状会更准"
                        } else {
                            "描述种植问题"
                        },
                        onValueChange = {
                            if (it.text.length > inputMaxChars && inputValue.text.length <= inputMaxChars) {
                                onInputLimitExceeded()
                            }
                            onInputValueChange(it)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 2.dp),
                        singleLine = false,
                        minLines = 1,
                        maxLines = 8,
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            color = Color(0xFF111111)
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            cursorColor = if (inputFieldFocused && !suppressInputCursor) {
                                Color(0xFF111111)
                            } else {
                                Color.Transparent
                            },
                            errorCursorColor = if (inputFieldFocused && !suppressInputCursor) {
                                Color(0xFF111111)
                            } else {
                                Color.Transparent
                            }
                        )
                    )
                }
            },
            sendButtonEnabled = canPressSend,
            sendButtonBackgroundColor = actionBg,
            sendButtonTint = actionTint,
            inputSelectionBottomClearance = inputSelectionBottomClearance,
            onSendClick = {
                when (sendGate.blockReason) {
                    SendBlockReason.InputTooLong -> onInputLimitExceeded()
                    SendBlockReason.QuotaExhausted -> onQuotaExceeded()
                    else -> if (canSend) {
                        onSendClick()
                    }
                }
            }
        )
        }
    }
}

@Composable
internal fun ChatComposerCollapseOverlay(
    visible: Boolean,
    chatRootLeftPx: Float,
    chatRootTopPx: Float,
    hostBoundsInWindow: Rect?,
    chromeBoundsInWindow: Rect?,
    pageSurface: Color,
    addButtonSize: Dp,
    addIconSize: Dp,
    sendButtonSize: Dp,
    inputChromeSurface: Color,
    inputChromeBorder: Color,
    inputFieldSurface: Color,
    inputFieldBorder: Color,
    inputBarHeight: Dp,
    inputBarMaxHeight: Dp
) {
    if (!visible || hostBoundsInWindow == null || chromeBoundsInWindow == null) return
    val density = LocalDensity.current
    val composerCollapseOverlayHostTop =
        with(density) { (hostBoundsInWindow.top - chatRootTopPx).toDp() }
    val composerCollapseOverlayHostStart =
        with(density) { (hostBoundsInWindow.left - chatRootLeftPx).toDp() }
    val composerCollapseOverlayHostWidth = with(density) { hostBoundsInWindow.width.toDp() }
    val composerCollapseOverlayHostHeight = with(density) { hostBoundsInWindow.height.toDp() }
    val composerCollapseOverlayWidth = with(density) { chromeBoundsInWindow.width.toDp() }
    val composerCollapseOverlayHeight = with(density) { chromeBoundsInWindow.height.toDp() }
    val composerCollapseOverlayRowTop =
        with(density) { (chromeBoundsInWindow.top - hostBoundsInWindow.top).toDp() }
    val composerCollapseOverlayRowStart =
        with(density) { (chromeBoundsInWindow.left - hostBoundsInWindow.left).toDp() }

    Box(
        modifier = Modifier
            .zIndex(44f)
            .offset(
                x = composerCollapseOverlayHostStart,
                y = composerCollapseOverlayHostTop
            )
            .width(composerCollapseOverlayHostWidth)
            .height(composerCollapseOverlayHostHeight)
            .background(pageSurface.copy(alpha = 0f))
    ) {
        ComposerChromeRow(
            modifier = Modifier
                .offset(
                    x = composerCollapseOverlayRowStart,
                    y = composerCollapseOverlayRowTop
                )
                .width(composerCollapseOverlayWidth)
                .heightIn(min = composerCollapseOverlayHeight),
            addButtonSize = addButtonSize,
            addIconSize = addIconSize,
            sendButtonSize = sendButtonSize,
            inputChromeSurface = inputChromeSurface,
            inputChromeBorder = inputChromeBorder,
            inputFieldSurface = inputFieldSurface,
            inputFieldBorder = inputFieldBorder,
            inputBarHeight = inputBarHeight,
            inputBarMaxHeight = inputBarMaxHeight,
            onAddClick = {},
            inputContent = {
                Text(
                    text = "描述种植问题",
                    color = Color(0xFFAEAFB4),
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 18.dp)
                )
            },
            sendButtonEnabled = false,
            sendButtonBackgroundColor = Color(0xFFD3D4D6),
            sendButtonTint = Color(0xFF7F8083),
            onSendClick = {}
        )
    }
}

@Composable
internal fun InputSelectionMenuPopup(
    state: InputSelectionToolbarState,
    inputFieldBoundsInWindow: Rect,
    viewportLeftPx: Float,
    viewportTopPx: Float,
    topChromeMaskBottomPx: Int,
    onMenuBoundsChanged: (Rect?) -> Unit,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val actions = remember(state) {
        buildList {
            state.onCopyRequested?.let {
                add(InputActionMenuItem(label = "复制", minWidth = 64.dp, horizontalPadding = 14.dp, onClick = it))
            }
            state.onPasteRequested?.let {
                add(InputActionMenuItem(label = "粘贴", minWidth = 64.dp, horizontalPadding = 14.dp, onClick = it))
            }
            state.onCutRequested?.let {
                add(InputActionMenuItem(label = "剪切", minWidth = 64.dp, horizontalPadding = 14.dp, onClick = it))
            }
            state.onSelectAllRequested?.let {
                add(InputActionMenuItem(label = "全选", minWidth = 64.dp, horizontalPadding = 14.dp, onClick = it))
            }
        }
    }
    if (actions.isEmpty()) {
        SideEffect { onMenuBoundsChanged(null) }
        return
    }

    val verticalSpacingPx = with(density) { 10.dp.roundToPx() }
    val marginPx = with(density) { 8.dp.roundToPx() }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    val boundsLocal = Rect(
        left = inputFieldBoundsInWindow.left - viewportLeftPx,
        top = inputFieldBoundsInWindow.top - viewportTopPx,
        right = inputFieldBoundsInWindow.right - viewportLeftPx,
        bottom = inputFieldBoundsInWindow.bottom - viewportTopPx
    )
    val resolvedWidth =
        if (cardSize.width > 0) cardSize.width else with(density) { 256.dp.roundToPx() }
    val resolvedHeight =
        if (cardSize.height > 0) cardSize.height else with(density) { 44.dp.roundToPx() }
    val preferredCenterX = boundsLocal.left + boundsLocal.width * state.anchorXRatio
    val minX = (boundsLocal.left + marginPx).roundToInt()
    val maxX = (boundsLocal.right - resolvedWidth - marginPx).roundToInt().coerceAtLeast(minX)
    val preferredX = (preferredCenterX - resolvedWidth / 2f).roundToInt().coerceIn(minX, maxX)
    val protectedTop =
        maxOf(
            marginPx,
            if (topChromeMaskBottomPx > 0) {
                (topChromeMaskBottomPx - viewportTopPx.roundToInt()) + marginPx
            } else {
                marginPx
            }
        )
    val preferredY =
        (boundsLocal.top.roundToInt() - resolvedHeight - verticalSpacingPx).coerceAtLeast(protectedTop)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(47f)
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(preferredX, preferredY) }
                .onGloballyPositioned { coordinates ->
                    cardSize = coordinates.size
                    onMenuBoundsChanged(coordinates.boundsInWindow())
                }
                .pointerInput(state.anchorXRatio, actions.size) {
                    detectTapGestures(onTap = {})
                }
        ) {
            InputActionMenuCardContent(
                actions = actions.map { action ->
                    action.copy(
                        onClick = {
                            action.onClick()
                            onDismiss()
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun ChatInputField(
    value: TextFieldValue,
    focused: Boolean,
    suppressCursor: Boolean,
    settlingMinHeightPx: Int,
    suppressPlaceholder: Boolean,
    onValueChange: (TextFieldValue) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onContentHeightChanged: (Int) -> Unit,
    placeholderText: String = "描述种植问题",
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = 6,
    textStyle: TextStyle,
    colors: Any? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val settlingSnapshotMinHeight = with(density) {
        settlingMinHeightPx.coerceAtLeast(0).toDp()
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.onFocusChanged { focusState ->
            onFocusChanged(focusState.isFocused)
        },
        textStyle = textStyle,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        cursorBrush = SolidColor(
            if (focused && !suppressCursor) {
                Color(0xFF111111)
            } else {
                Color.Transparent
            }
        ),
        decorationBox = { innerTextField ->
            if (colors != null) Unit
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = settlingSnapshotMinHeight),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.text.isEmpty() && !suppressPlaceholder) {
                    Text(
                        text = placeholderText,
                        color = Color(0xFFAEAFB4)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { size ->
                            if (size.height > 0) {
                                onContentHeightChanged(size.height)
                            }
                        }
                ) {
                    innerTextField()
                }
            }
        }
    )
}

@Composable
internal fun ComposerAttachmentBottomSheet(
    visible: Boolean,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onCameraClick: () -> Unit,
    onPhotoClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(80f)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(durationMillis = 120)),
            exit = fadeOut(animationSpec = tween(durationMillis = 100))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 220)
            ) + fadeIn(animationSpec = tween(durationMillis = 120)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 160)
            ) + fadeOut(animationSpec = tween(durationMillis = 120)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
                shadowElevation = 14.dp,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 292.dp)
                        .navigationBarsPadding()
                        .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ComposerAttachmentBottomSheetTile(
                            title = "相机",
                            modifier = Modifier.weight(1f),
                            onClick = onCameraClick
                        ) {
                            ComposerCameraIcon(tint = Color(0xFF111111), modifier = Modifier.size(34.dp))
                        }
                        ComposerAttachmentBottomSheetTile(
                            title = "照片",
                            modifier = Modifier.weight(1f),
                            onClick = onPhotoClick
                        ) {
                            ComposerPhotoIcon(tint = Color(0xFF111111), modifier = Modifier.size(34.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0xFFEDEEF1))
                    )
                    Text(
                        text = "建议拍清病斑、整株、叶背或果实，最多4张。",
                        color = Color(0xFF8B8D93),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.ComposerAttachmentBottomSheetTile(
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .height(118.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFF5F6F7))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon()
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = title,
            color = Color(0xFF111111),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ComposerImagePreviewStrip(
    images: List<ComposerImageAttachment>,
    onRemoveImage: (ComposerImageAttachment) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        images.take(4).forEachIndexed { index, image ->
            ComposerImagePreviewThumb(
                image = image,
                index = index,
                onRemoveImage = onRemoveImage
            )
        }
    }
}

@Composable
private fun ComposerImagePreviewThumb(
    image: ComposerImageAttachment,
    index: Int,
    onRemoveImage: (ComposerImageAttachment) -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(image.uri) {
        mutableStateOf<ImageBitmap?>(null)
    }
    LaunchedEffect(image.uri) {
        bitmap = withContext(Dispatchers.IO) {
            decodeComposerPreviewBitmap(context, image.uri)
        }
    }
    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF0F1F3))
    ) {
        val previewBitmap = bitmap
        if (previewBitmap != null) {
            Image(
                bitmap = previewBitmap,
                contentDescription = "第${index + 1}张图片",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            ComposerPhotoIcon(
                tint = Color(0xFF8B8D93),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(26.dp)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(5.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xAA111111))
                .padding(horizontal = 5.dp, vertical = 1.dp)
        ) {
            Text(
                text = "${index + 1}",
                color = Color.White,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color(0xCC111111))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onRemoveImage(image) },
            contentAlignment = Alignment.Center
        ) {
            ComposerCloseIcon(tint = Color.White, modifier = Modifier.size(10.dp))
        }
    }
}

@Composable
private fun ComposerSendArrowIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.12f
        val centerX = size.width / 2f
        val headY = size.height * 0.1f
        val tailY = size.height * 0.92f
        val wingY = size.height * 0.44f
        drawLine(
            color = tint,
            start = Offset(centerX, tailY),
            end = Offset(centerX, headY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(centerX, headY),
            end = Offset(size.width * 0.2f, wingY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(centerX, headY),
            end = Offset(size.width * 0.8f, wingY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun ComposerCameraIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.085f
        val left = size.width * 0.13f
        val top = size.height * 0.31f
        val right = size.width * 0.87f
        val bottom = size.height * 0.82f
        val corner = size.minDimension * 0.13f
        val bumpLeft = size.width * 0.38f
        val bumpTop = size.height * 0.18f
        val bumpRight = size.width * 0.62f
        val bumpCorner = size.minDimension * 0.055f
        val cameraPath = Path().apply {
            moveTo(left + corner, top)
            lineTo(bumpLeft - bumpCorner, top)
            quadraticTo(bumpLeft, top, bumpLeft, top - bumpCorner)
            lineTo(bumpLeft, bumpTop + bumpCorner)
            quadraticTo(bumpLeft, bumpTop, bumpLeft + bumpCorner, bumpTop)
            lineTo(bumpRight - bumpCorner, bumpTop)
            quadraticTo(bumpRight, bumpTop, bumpRight, bumpTop + bumpCorner)
            lineTo(bumpRight, top - bumpCorner)
            quadraticTo(bumpRight, top, bumpRight + bumpCorner, top)
            lineTo(right - corner, top)
            quadraticTo(right, top, right, top + corner)
            lineTo(right, bottom - corner)
            quadraticTo(right, bottom, right - corner, bottom)
            lineTo(left + corner, bottom)
            quadraticTo(left, bottom, left, bottom - corner)
            lineTo(left, top + corner)
            quadraticTo(left, top, left + corner, top)
            close()
        }
        drawPath(
            path = cameraPath,
            color = tint,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        drawCircle(
            color = tint,
            radius = size.minDimension * 0.15f,
            center = Offset(size.width * 0.5f, size.height * 0.56f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        )
    }
}

@Composable
private fun ComposerPhotoIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.085f
        val corner = size.minDimension * 0.14f
        val left = size.width * 0.16f
        val top = size.height * 0.18f
        val right = size.width * 0.84f
        val bottom = size.height * 0.82f
        drawRoundRect(
            color = tint,
            topLeft = Offset(left, top),
            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawCircle(
            color = tint,
            radius = size.minDimension * 0.065f,
            center = Offset(size.width * 0.65f, size.height * 0.36f)
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.24f, size.height * 0.72f),
            end = Offset(size.width * 0.42f, size.height * 0.53f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.42f, size.height * 0.53f),
            end = Offset(size.width * 0.55f, size.height * 0.66f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.55f, size.height * 0.66f),
            end = Offset(size.width * 0.68f, size.height * 0.56f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.68f, size.height * 0.56f),
            end = Offset(size.width * 0.78f, size.height * 0.72f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun ComposerCloseIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.18f
        drawLine(
            color = tint,
            start = Offset(size.width * 0.16f, size.height * 0.16f),
            end = Offset(size.width * 0.84f, size.height * 0.84f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.84f, size.height * 0.16f),
            end = Offset(size.width * 0.16f, size.height * 0.84f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun ComposerPlusCrossIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.105f
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        drawLine(
            color = tint,
            start = Offset(centerX, size.height * 0.14f),
            end = Offset(centerX, size.height * 0.86f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.14f, centerY),
            end = Offset(size.width * 0.86f, centerY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun ComposerSendActionButton(
    modifier: Modifier = Modifier,
    size: Dp,
    backgroundColor: Color,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 1.8.dp,
                shape = CircleShape,
                ambientColor = Color(0x19000000),
                spotColor = Color(0x19000000)
            )
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null
            ) {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        ComposerSendArrowIcon(
            tint = tint,
            modifier = Modifier.size(21.dp)
        )
    }
}

@Composable
private fun ComposerInlineAddButton(
    size: Dp,
    iconSize: Dp,
    surfaceColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(surfaceColor.copy(alpha = 0f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        ComposerPlusCrossIcon(
            tint = borderColor,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun ComposerInputShell(
    modifier: Modifier = Modifier,
    addButtonSize: Dp,
    addIconSize: Dp,
    inputChromeSurface: Color,
    inputChromeBorder: Color,
    inputFieldSurface: Color,
    inputFieldBorder: Color,
    inputBarHeight: Dp,
    inputBarMaxHeight: Dp,
    onAddClick: () -> Unit,
    attachmentsContent: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
    sendButtonSize: Dp,
    sendButtonEnabled: Boolean,
    sendButtonBackgroundColor: Color,
    sendButtonTint: Color,
    inputSelectionBottomClearance: Dp = 0.dp,
    onSendClick: () -> Unit
) {
    val shellShape = RoundedCornerShape(24.dp)
    val actionDockHeight = if (addButtonSize > sendButtonSize) addButtonSize else sendButtonSize
    Surface(
        shape = shellShape,
        color = inputFieldSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .dropShadow(
                shape = shellShape,
                shadow = Shadow(
                    radius = 28.dp,
                    spread = 2.dp,
                    color = Color.Black.copy(alpha = 0.14f),
                    offset = DpOffset(x = 0.dp, y = 12.dp)
                )
            )
            .dropShadow(
                shape = shellShape,
                shadow = Shadow(
                    radius = 10.dp,
                    spread = 0.dp,
                    color = Color.Black.copy(alpha = 0.18f),
                    offset = DpOffset(x = 0.dp, y = 4.dp)
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = inputBarHeight, max = inputBarMaxHeight)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = actionDockHeight + 8.dp + inputSelectionBottomClearance
                    )
            ) {
                attachmentsContent?.invoke()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    content()
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ComposerInlineAddButton(
                    size = addButtonSize,
                    iconSize = addIconSize,
                    surfaceColor = inputChromeSurface,
                    borderColor = Color(0xFF111111),
                    onClick = onAddClick
                )
                Spacer(modifier = Modifier.weight(1f))
            }

            ComposerSendActionButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 8.dp),
                size = sendButtonSize,
                backgroundColor = sendButtonBackgroundColor,
                tint = sendButtonTint,
                enabled = sendButtonEnabled,
                onClick = onSendClick
            )
        }
    }
}

@Composable
private fun ComposerChromeRow(
    modifier: Modifier = Modifier,
    addButtonSize: Dp,
    addIconSize: Dp,
    sendButtonSize: Dp,
    inputChromeSurface: Color,
    inputChromeBorder: Color,
    inputFieldSurface: Color,
    inputFieldBorder: Color,
    inputBarHeight: Dp,
    inputBarMaxHeight: Dp,
    onAddClick: () -> Unit,
    attachmentsContent: (@Composable () -> Unit)? = null,
    inputShellModifier: Modifier = Modifier,
    inputContent: @Composable RowScope.() -> Unit,
    sendButtonEnabled: Boolean,
    sendButtonBackgroundColor: Color,
    sendButtonTint: Color,
    inputSelectionBottomClearance: Dp = 0.dp,
    onSendClick: () -> Unit
) {
    ComposerInputShell(
        modifier = modifier.then(inputShellModifier),
        addButtonSize = addButtonSize,
        addIconSize = addIconSize,
        inputChromeSurface = inputChromeSurface,
        inputChromeBorder = inputChromeBorder,
        inputFieldSurface = inputFieldSurface,
        inputFieldBorder = inputFieldBorder,
        inputBarHeight = inputBarHeight,
        inputBarMaxHeight = inputBarMaxHeight,
        onAddClick = onAddClick,
        attachmentsContent = attachmentsContent,
        content = inputContent,
        sendButtonSize = sendButtonSize,
        sendButtonEnabled = sendButtonEnabled,
        sendButtonBackgroundColor = sendButtonBackgroundColor,
        sendButtonTint = sendButtonTint,
        inputSelectionBottomClearance = inputSelectionBottomClearance,
        onSendClick = onSendClick
    )
}

private data class InputActionMenuItem(
    val label: String,
    val minWidth: Dp,
    val horizontalPadding: Dp,
    val onClick: () -> Unit
)

@Composable
private fun InputActionMenuButton(
    label: String,
    modifier: Modifier = Modifier,
    minWidth: Dp = 0.dp,
    horizontalPadding: Dp = 12.dp,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .widthIn(min = minWidth)
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Transparent)
            .pointerInput(label) {
                detectTapGestures(onTap = { onClick() })
            }
            .padding(horizontal = horizontalPadding, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun InputActionMenuCardContent(
    actions: List<InputActionMenuItem>,
    modifier: Modifier = Modifier
) {
    if (actions.isEmpty()) return
    Surface(
        color = Color(0xFF111111),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 10.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions.forEachIndexed { index, action ->
                InputActionMenuButton(
                    label = action.label,
                    minWidth = action.minWidth,
                    horizontalPadding = action.horizontalPadding,
                    onClick = action.onClick
                )
                if (index < actions.lastIndex) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(16.dp)
                            .background(Color.White.copy(alpha = 0.16f))
                    )
                }
            }
        }
    }
}
