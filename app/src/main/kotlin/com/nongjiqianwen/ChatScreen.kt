package com.nongjiqianwen

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.random.Random
import java.util.UUID

private enum class ChatRole { USER, ASSISTANT }
private data class ChatMessage(val id: String, val role: ChatRole, val content: String)

private fun normalizeAssistantText(content: String): String {
    return content
        .replace("\r\n", "\n")
        .replace(Regex("(?m)^#{1,6}\\s+"), "")
        .replace(Regex("(?m)^```.*$"), "")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

@Composable
private fun AssistantMarkdownContent(content: String, modifier: Modifier = Modifier) {
    val displayText = remember(content) { normalizeAssistantText(content) }
    Text(
        text = displayText,
        modifier = modifier.fillMaxWidth(),
        style = TextStyle(
            fontSize = 17.sp,
            lineHeight = 31.sp,
            color = Color(0xFF171717)
        ),
        textAlign = TextAlign.Start
    )
}

@Composable
private fun GPTBreathingBall(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "assistantBreathingDot")
    val alpha by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1180,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "assistantBreathingDotAlpha"
    )
    val scale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1180,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "assistantBreathingDotScale"
    )
    Box(
        modifier = modifier
            .size(12.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(CircleShape)
            .background(Color(0xFF111111))
    )
}

@Composable
private fun LongArrowIcon(
    tint: Color,
    directionUp: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.1f
        val centerX = size.width / 2f
        val headY = if (directionUp) size.height * 0.16f else size.height * 0.84f
        val tailY = if (directionUp) size.height * 0.88f else size.height * 0.12f
        val wingY = if (directionUp) size.height * 0.36f else size.height * 0.64f
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
            end = Offset(size.width * 0.24f, wingY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(centerX, headY),
            end = Offset(size.width * 0.76f, wingY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun MenuBarsIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.085f
        val y1 = size.height * 0.34f
        val y2 = size.height * 0.66f
        drawLine(
            color = tint,
            start = Offset(size.width * 0.18f, y1),
            end = Offset(size.width * 0.82f, y1),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.22f, y2),
            end = Offset(size.width * 0.58f, y2),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun DiamondOutlineIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.095f
        val top = Offset(size.width * 0.5f, size.height * 0.14f)
        val right = Offset(size.width * 0.82f, size.height * 0.5f)
        val bottom = Offset(size.width * 0.5f, size.height * 0.86f)
        val left = Offset(size.width * 0.18f, size.height * 0.5f)
        drawLine(tint, top, right, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(tint, right, bottom, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(tint, bottom, left, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(tint, left, top, strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
private fun FrostedCircleButton(
    size: Dp,
    surfaceColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: @Composable BoxScope.() -> Unit
) {
    Surface(
        shape = CircleShape,
        color = surfaceColor,
        border = BorderStroke(0.45.dp, borderColor),
        shadowElevation = 0.92.dp,
        tonalElevation = 0.dp,
        modifier = modifier.size(size)
    ) {
        IconButton(
            onClick = onClick,
            colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFF1A1A1A))
        ) {
            Box(contentAlignment = Alignment.Center, content = icon)
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun ChatScreen() {
    val input = remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val listState = rememberLazyListState()
    val isReverse = false
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    var fakeStreamJob by remember { mutableStateOf<Job?>(null) }

    var isStreaming by remember { mutableStateOf(false) }
    var assistantMessageId by remember { mutableStateOf<String?>(null) }
    var autoFollowEnabled by remember { mutableStateOf(true) }
    var userInteracting by remember { mutableStateOf(false) }
    var streamTick by remember { mutableStateOf(0) }
    var sendTick by remember { mutableStateOf(0) }
    var programmaticScroll by remember { mutableStateOf(false) }
    var lastAutoScrollMs by remember { mutableStateOf(0L) }
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    val atBottom by remember { derivedStateOf { !listState.canScrollForward } }
    val density = LocalDensity.current
    val appTopBottomTint = Color(0xFFF4F4F2)
    val appCenterTint = Color(0xFFF7F7F6)
    val chromeSurface = Color.White.copy(alpha = 0.96f)
    val chromeBorder = Color(0xFFEAE7E1).copy(alpha = 0.16f)
    val inputSurface = Color(0xFFFFFFFF)
    val inputBorder = Color(0xFFE4E1DA).copy(alpha = 0.42f)
    val userBubbleColor = Color(0xFFF4F4F7)
    val topInset = WindowInsets.safeDrawing
        .only(WindowInsetsSides.Top)
        .asPaddingValues()
        .calculateTopPadding()
    val measuredTopBarHeight = with(density) { topBarHeightPx.toDp() }
    val measuredBottomBarHeight = with(density) { bottomBarHeightPx.toDp() }
    val topBarReservedHeight = if (measuredTopBarHeight > 0.dp) {
        measuredTopBarHeight + 12.dp
    } else {
        topInset + 72.dp
    }
    val jumpButtonBottomPadding = if (measuredBottomBarHeight > 0.dp) {
        measuredBottomBarHeight + 18.dp
    } else {
        96.dp
    }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, focusManager, keyboardController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun appendAssistantChunk(piece: String) {
        if (piece.isBlank()) return
        mainHandler.post {
            val currentId = assistantMessageId
            if (currentId.isNullOrBlank()) {
                val newId = "assistant_${UUID.randomUUID()}"
                assistantMessageId = newId
                messages.add(ChatMessage(newId, ChatRole.ASSISTANT, piece))
                streamTick++
                return@post
            }
            val index = messages.indexOfLast { it.id == currentId }
            if (index >= 0) {
                val old = messages[index]
                messages[index] = old.copy(content = old.content + piece)
            } else {
                messages.add(ChatMessage(currentId, ChatRole.ASSISTANT, piece))
            }
            streamTick++
        }
    }

    fun finishStreaming() {
        mainHandler.post {
            val currentId = assistantMessageId
            if (!currentId.isNullOrBlank()) {
                val index = messages.indexOfLast { it.id == currentId }
                if (index >= 0 && messages[index].role == ChatRole.ASSISTANT && messages[index].content.isBlank()) {
                    messages.removeAt(index)
                }
            }
            fakeStreamJob = null
            isStreaming = false
            assistantMessageId = null
        }
    }

    fun sendMessage() {
        val text = input.value.trim()
        if (text.isEmpty()) return
        val userId = "user_${UUID.randomUUID()}"
        messages.add(ChatMessage(userId, ChatRole.USER, text))
        input.value = ""
        focusManager.clearFocus(force = true)
        keyboardController?.hide()

        val assistantId = "assistant_${UUID.randomUUID()}"
        messages.add(ChatMessage(assistantId, ChatRole.ASSISTANT, ""))
        isStreaming = true
        assistantMessageId = assistantId
        autoFollowEnabled = true
        userInteracting = false
        sendTick++

        fakeStreamJob?.cancel()
        val fullText = FAKE_STREAM_TEXT
        fakeStreamJob = snackbarScope.launch {
            val ballStartTime = SystemClock.uptimeMillis()
            val initialDelayMs = Random.nextLong(720, 1001)
            val minBallMs = 1680L
            val elapsed = SystemClock.uptimeMillis() - ballStartTime
            val firstTokenWait = maxOf(initialDelayMs, minBallMs - elapsed)
            if (firstTokenWait > 0) {
                delay(firstTokenWait)
            }

            var cursor = 0
            var emittedSincePause = 0
            var pauseThreshold = Random.nextInt(90, 161)
            while (isActive && cursor < fullText.length) {
                val chunkSize = Random.nextInt(2, 6)
                val next = min(cursor + chunkSize, fullText.length)
                val piece = fullText.substring(cursor, next)
                appendAssistantChunk(piece)
                emittedSincePause += piece.length
                cursor = next
                delay(Random.nextLong(48, 96))
                val tail = piece.lastOrNull()
                if (tail == '。' || tail == '，' || tail == '；' || tail == '：' || tail == '！' || tail == '？' || tail == '\n') {
                    delay(Random.nextLong(70, 151))
                }
                if (emittedSincePause >= pauseThreshold && cursor < fullText.length) {
                    emittedSincePause = 0
                    pauseThreshold = Random.nextInt(90, 161)
                    delay(Random.nextLong(130, 281))
                }
            }
            if (isActive) finishStreaming()
        }
    }

    suspend fun scrollToBottom(animated: Boolean) {
        val lastIndex = messages.lastIndex
        if (lastIndex < 0) return
        programmaticScroll = true
        try {
            withFrameNanos { }
            if (animated) listState.animateScrollToItem(lastIndex) else listState.scrollToItem(lastIndex)
            withFrameNanos { }
            if (animated) listState.animateScrollToItem(lastIndex) else listState.scrollToItem(lastIndex)
        } finally {
            programmaticScroll = false
        }
    }

    suspend fun scrollAfterSendAnchor() {
        val userIndex = messages.indexOfLast { it.role == ChatRole.USER }
        if (userIndex < 0) return
        programmaticScroll = true
        try {
            withFrameNanos { }
            val viewportHeight =
                (listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset)
                    .coerceAtLeast(1)
            val anchorOffset = (viewportHeight * 0.42f).toInt().coerceAtLeast(0)
            listState.scrollToItem(userIndex, anchorOffset)
            withFrameNanos { }
            listState.scrollToItem(userIndex, anchorOffset)
        } finally {
            programmaticScroll = false
        }
    }

    LaunchedEffect(streamTick) {
        if (!autoFollowEnabled) return@LaunchedEffect
        if (userInteracting) return@LaunchedEffect
        if (messages.isEmpty()) return@LaunchedEffect
        val now = SystemClock.uptimeMillis()
        if (now - lastAutoScrollMs < 120L) return@LaunchedEffect
        lastAutoScrollMs = now
        scrollToBottom(animated = false)
    }

    LaunchedEffect(sendTick) {
        if (messages.isEmpty()) return@LaunchedEffect
        scrollAfterSendAnchor()
    }

    fun jumpToBottom() {
        snackbarScope.launch {
            if (messages.isEmpty()) return@launch
            autoFollowEnabled = true
            userInteracting = false
            scrollToBottom(animated = true)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to appTopBottomTint,
                        0.14f to Color(0xFFF5F5F3),
                        0.5f to appCenterTint,
                        0.86f to Color(0xFFF5F5F3),
                        1.0f to appTopBottomTint
                    )
                )
            )
    ) {
        val chromeMaxWidth: Dp = when {
            maxWidth >= 900.dp -> 820.dp
            maxWidth >= 700.dp -> 680.dp
            else -> maxWidth
        }
        val chromeHorizontalPadding = when {
            maxWidth < 360.dp -> 10.dp
            maxWidth < 600.dp -> 12.dp
            else -> 20.dp
        }
        val listHorizontalPadding = when {
            maxWidth < 360.dp -> 8.dp
            maxWidth < 600.dp -> 12.dp
            else -> 20.dp
        }
        val inputBarHeight = if (maxWidth < 360.dp) 52.dp else 56.dp
        val chromeButtonSize = if (maxWidth < 360.dp) 40.dp else 42.dp
        val addButtonSize = if (maxWidth < 360.dp) 40.dp else 42.dp
        val addIconSize = if (maxWidth < 360.dp) 24.dp else 26.dp
        val sendButtonSize = if (maxWidth < 360.dp) 40.dp else 42.dp
        val userBubbleMaxWidth = if (chromeMaxWidth < 440.dp) chromeMaxWidth * 0.78f else 420.dp
        val assistantLeadWidth = 18.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { bottomBarHeightPx = it.height }
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(104.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0xFFFAFAF9).copy(alpha = 0.94f),
                                        Color(0xFFEFEEE9).copy(alpha = 0.98f)
                                    )
                                )
                            )
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .widthIn(max = chromeMaxWidth)
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            .padding(horizontal = chromeHorizontalPadding, vertical = 8.dp)
                            .navigationBarsPadding()
                            .imePadding(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FrostedCircleButton(
                            size = addButtonSize,
                            surfaceColor = chromeSurface,
                            borderColor = chromeBorder,
                            onClick = {}
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "添加",
                                modifier = Modifier.size(addIconSize),
                                tint = Color(0xFF252525)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(30.dp),
                            color = inputSurface,
                            border = BorderStroke(0.72.dp, inputBorder),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.86.dp,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(inputBarHeight)
                            ) {
                                TextField(
                                    value = input.value,
                                    onValueChange = {
                                        if (it.length <= 3000) input.value = it
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.CenterStart)
                                        .padding(end = 58.dp),
                                    placeholder = { Text("描述作物/地区/问题", color = Color(0xFFA4A19A)) },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontSize = 16.sp,
                                        lineHeight = 21.sp,
                                        color = Color(0xFF111111)
                                    ),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        disabledIndicatorColor = Color.Transparent
                                    )
                                )

                                val canSend = input.value.trim().isNotEmpty() && !isStreaming
                                val actionBg = if (canSend) Color(0xFF111111) else Color(0xFFD3D4D6)
                                val actionTint = if (canSend) Color.White else Color(0xFF7F8083)

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(end = 7.dp)
                                        .size(sendButtonSize)
                                        .clip(CircleShape)
                                        .background(actionBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (canSend) {
                                                sendMessage()
                                            }
                                        },
                                        enabled = canSend,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        LongArrowIcon(
                                            tint = actionTint,
                                            directionUp = true,
                                            modifier = Modifier.size(21.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            userInteracting = true
                            autoFollowEnabled = false
                            waitForUpOrCancellation()
                            userInteracting = false
                        }
                    },
                state = listState,
                reverseLayout = isReverse,
                contentPadding = PaddingValues(
                    top = topBarReservedHeight,
                    bottom = 12.dp
                )
            ) {
                    items(messages, key = { it.id }) { msg ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = listHorizontalPadding, vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .widthIn(max = chromeMaxWidth)
                                    .fillMaxWidth()
                            ) {
                                if (msg.role == ChatRole.ASSISTANT) {
                                    val showBreathingBall = isStreaming && msg.id == assistantMessageId && msg.content.isBlank()
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(end = 4.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        if (showBreathingBall) {
                                            Box(
                                                modifier = Modifier
                                                    .width(assistantLeadWidth)
                                                    .padding(top = 9.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                GPTBreathingBall()
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        if (msg.content.isBlank() && showBreathingBall) {
                                            Spacer(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                            )
                                        } else {
                                            AssistantMarkdownContent(
                                                content = msg.content,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        text = msg.content,
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .widthIn(max = userBubbleMaxWidth)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(userBubbleColor)
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color(0xFF161616)
                                    )
                                }
                            }
                        }
                    }
            }

            if (messages.isEmpty()) {
                Text(
                    text = "欢迎咨询种植、病虫害防治、施肥等问题。\n描述作物/地区/现象，必要时可上传图片。",
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topBarReservedHeight + 24.dp, start = 24.dp, end = 24.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF141414),
                    lineHeight = MaterialTheme.typography.titleMedium.lineHeight,
                    textAlign = TextAlign.Start
                )
            }

            if (messages.isNotEmpty() && !atBottom) {
                Surface(
                    onClick = { jumpToBottom() },
                    shape = CircleShape,
                    color = chromeSurface,
                    border = BorderStroke(0.55.dp, chromeBorder),
                    shadowElevation = 0.48.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = jumpButtonBottomPadding)
                        .navigationBarsPadding()
                        .size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        LongArrowIcon(
                            tint = Color(0xFF111111),
                            directionUp = false,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(topBarReservedHeight + 40.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFF0F0ED).copy(alpha = 0.98f),
                                Color(0xFFF5F5F2).copy(alpha = 0.94f),
                                appCenterTint.copy(alpha = 0.72f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onSizeChanged { topBarHeightPx = it.height }
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(top = topInset + 8.dp, bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(max = chromeMaxWidth)
                        .fillMaxWidth()
                        .padding(horizontal = chromeHorizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FrostedCircleButton(
                        size = chromeButtonSize,
                        surfaceColor = chromeSurface,
                        borderColor = chromeBorder,
                        onClick = {}
                    ) {
                        MenuBarsIcon(
                            tint = Color(0xFF1E1E1E),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color.White.copy(alpha = 0.96f),
                            border = BorderStroke(0.38.dp, chromeBorder),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.56.dp
                        ) {
                            Text(
                                text = "农技千问",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                color = Color(0xFF111111),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    FrostedCircleButton(
                        size = chromeButtonSize,
                        surfaceColor = chromeSurface,
                        borderColor = chromeBorder,
                        onClick = {}
                    ) {
                        DiamondOutlineIcon(
                            tint = Color(0xFF1E1E1E),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
}

private val FAKE_STREAM_TEXT = """
初步判断：你描述的叶片失绿、午后萎蔫和局部长势不齐，更像环境波动叠加管理节奏不稳导致的综合性问题，暂时还不能直接归因为单一病虫害。先按下面的问诊路径补齐信息，目标是先把风险控制住，再在两到三天内把结论做实。

## 一、补充信息
1. 作物与阶段：品种、定植时间、当前处于营养生长期还是开花坐果期，不同阶段对温湿和供水波动的耐受差异很大。
2. 异常起点：最早出现在新叶、老叶、叶缘、叶脉还是茎基部，起点位置通常比最后表现更有诊断价值。
3. 变化速度：是一天内突然加重，还是三到五天缓慢扩展，速度会直接影响排查优先级。
4. 空间分布：零散点状、行间成片，还是整棚同步，分布特征能帮助区分环境扰动和持续性问题。
5. 近期操作：近七天是否有浇水节奏变化、通风调整、连续阴雨后暴晒，或者整枝打杈等管理动作。

## 二、可能原因 Top3
1. 根际短时失衡：水分和通气在短周期内反复波动，白天蒸腾上来后叶片容易发软，傍晚缓解，边缘轻失绿。
2. 小气候叠加：同棚不同位置温差、湿差、风口位置差异明显，症状会随时段和天气轻重变化。
3. 管理节奏错位：作物实际需求已变化，但灌水、遮阴、通风、追肥节奏没有同步调整，导致部分植株先出现应激反应。

## 三、观察与复查
当天先做可回退动作：浇水、通风、遮阴都采用小幅、连续、可追踪的调整，不要一次性大改。设置三到五个固定观测点，覆盖轻、中、重三类植株，同一时间拍近景和中景，记录叶色、挺度、边缘卷曲程度和新叶状态。

次日复查重点只看四件事：症状边界是否继续外扩，异常株与正常株差距是否拉大，新叶是否持续变差，午后与傍晚差异是否缩小。如果范围趋稳、恢复加快、差距收敛，可以继续稳态观察；如果扩展加快、萎蔫提前、差异放大，就说明要升级排查。

## 四、风险提示
不要急着凭一张图或者一次观察下结论。农业现场最常见的误判，是把环境应激、根系问题、营养失衡和轻度病虫信号混成一个原因处理，结果越调越乱。现阶段先保证操作可回退、记录可对比、判断有依据，比仓促给药或者大改管理更重要。

## 五、执行建议
今天先把管理稳定下来，完成定点记录；明天同一时段回看关键指标，再决定是否需要升级处理。这样做不是拖，而是建立一套可落地、可复核、可回退的移动端问诊闭环，后面即使接入 SAE 和真实模型流式输出，这套消息插入、流式追加、结束收敛的 UI 逻辑也可以直接沿用，只需要把假流式文本源替换成后端流数据。
""".trimIndent()
