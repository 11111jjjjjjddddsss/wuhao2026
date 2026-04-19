package com.nongjiqianwen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

internal data class InputSelectionToolbarState(
    val anchorXRatio: Float,
    val onCopyRequested: (() -> Unit)?,
    val onPasteRequested: (() -> Unit)?,
    val onCutRequested: (() -> Unit)?,
    val onSelectAllRequested: (() -> Unit)?
)

internal enum class SendBlockReason {
    None,
    EmptyInput,
    Streaming,
    InputTooLong
}

internal data class SendGateState(
    val canPress: Boolean,
    val canSubmit: Boolean,
    val blockReason: SendBlockReason
)

internal fun buildSendGateState(
    rawInput: String,
    isStreaming: Boolean,
    exceedsInputLimit: Boolean
): SendGateState {
    val hasText = rawInput.trim().isNotEmpty()
    return when {
        !hasText -> SendGateState(
            canPress = false,
            canSubmit = false,
            blockReason = SendBlockReason.EmptyInput
        )
        isStreaming -> SendGateState(
            canPress = false,
            canSubmit = false,
            blockReason = SendBlockReason.Streaming
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
    hostModifier: Modifier = Modifier,
    onChromeMeasured: (Int) -> Unit,
    onChromeBoundsChanged: (Rect) -> Unit,
    onInputBoundsChanged: (Rect) -> Unit,
    onInputFocused: (Boolean) -> Unit,
    onInputContentHeightChanged: (Int) -> Unit,
    onInputValueChange: (TextFieldValue) -> Unit,
    onInputLimitExceeded: () -> Unit,
    onAddClick: () -> Unit,
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
        exceedsInputLimit = inputValue.text.length > inputMaxChars
    )
    val canPressSend = sendGate.canPress
    val canSend = sendGate.canSubmit
    val actionBg = if (canPressSend) Color(0xFF111111) else Color(0xFFD3D4D6)
    val actionTint = if (canPressSend) Color.White else Color(0xFF7F8083)

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
        ComposerChromeRow(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = chromeMaxWidth)
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
                            isStreamingOrSettling ||
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
                        maxLines = 6,
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
            onSendClick = {
                if (sendGate.blockReason == SendBlockReason.InputTooLong) {
                    onInputLimitExceeded()
                } else if (canSend) {
                    onSendClick()
                }
            }
        )
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
                        text = "描述种植问题",
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
private fun ComposerSendArrowIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.12f
        val centerX = size.width / 2f
        val headY = size.height * 0.14f
        val tailY = size.height * 0.9f
        val wingY = size.height * 0.48f
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
            end = Offset(size.width * 0.16f, wingY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(centerX, headY),
            end = Offset(size.width * 0.84f, wingY),
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
        val stroke = size.minDimension * 0.14f
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
            modifier = Modifier.size(22.dp)
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
            .background(surfaceColor.copy(alpha = 0.7f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        ComposerPlusCrossIcon(
            tint = borderColor.copy(alpha = 0.76f),
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
    content: @Composable RowScope.() -> Unit,
    sendButtonSize: Dp,
    sendButtonEnabled: Boolean,
    sendButtonBackgroundColor: Color,
    sendButtonTint: Color,
    onSendClick: () -> Unit
) {
    val shellShape = RoundedCornerShape(28.dp)
    val actionDockHeight = if (addButtonSize > sendButtonSize) addButtonSize else sendButtonSize
    Surface(
        shape = shellShape,
        color = inputFieldSurface,
        border = BorderStroke(1.16.dp, inputFieldBorder.copy(alpha = 0.98f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = shellShape,
                ambientColor = Color(0x16000000),
                spotColor = Color(0x22000000)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = inputBarHeight, max = inputBarMaxHeight)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = actionDockHeight + 8.dp
                    ),
                verticalAlignment = Alignment.Top
            ) {
                content()
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
                    borderColor = inputChromeBorder,
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
    inputShellModifier: Modifier = Modifier,
    inputContent: @Composable RowScope.() -> Unit,
    sendButtonEnabled: Boolean,
    sendButtonBackgroundColor: Color,
    sendButtonTint: Color,
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
        content = inputContent,
        sendButtonSize = sendButtonSize,
        sendButtonEnabled = sendButtonEnabled,
        sendButtonBackgroundColor = sendButtonBackgroundColor,
        sendButtonTint = sendButtonTint,
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
