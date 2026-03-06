package com.nongjiqianwen

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val ballColor by transition.animateColor(
        initialValue = Color(0xFF111111),
        targetValue = Color(0xFF5A5A5A),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "assistantBreathingDotColor"
    )
    val scale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "assistantBreathingDotScale"
    )
    Box(
        modifier = modifier
            .size(16.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(ballColor)
    )
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
    val atBottom by remember { derivedStateOf { !listState.canScrollForward } }
    val topInset = WindowInsets.safeDrawing
        .only(WindowInsetsSides.Top)
        .asPaddingValues()
        .calculateTopPadding()
    val topBarReservedHeight = topInset + 68.dp

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
            val initialDelayMs = Random.nextLong(600, 901)
            val minBallMs = 2200L
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

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5)),
        containerColor = Color(0xFFF5F5F5),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .navigationBarsPadding()
                    .imePadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    tonalElevation = 1.dp,
                    shadowElevation = 1.dp,
                    modifier = Modifier.size(42.dp)
                ) {
                    IconButton(
                        onClick = {},
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFF252525))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "添加",
                            modifier = Modifier.size(30.dp),
                            tint = Color(0xFF252525)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White,
                    tonalElevation = 1.dp,
                    shadowElevation = 1.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        TextField(
                            value = input.value,
                            onValueChange = {
                                if (it.length <= 3000) input.value = it
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.CenterStart)
                                .padding(end = 52.dp),
                            placeholder = { Text("描述作物/地区/问题", color = Color(0xFF9A9A9A)) },
                            singleLine = true,
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
                        val actionBg = if (canSend) Color(0xFF101010) else Color(0xFFD9D9D9)
                        val actionTint = if (canSend) Color.White else Color(0xFF7A7A7A)

                        IconButton(
                            onClick = {
                                if (canSend) {
                                    sendMessage()
                                }
                            },
                            enabled = canSend,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 6.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(actionBg)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Send",
                                tint = actionTint,
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer { rotationZ = 90f }
                            )
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
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 10.dp)
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
                        val align = if (msg.role == ChatRole.USER) Alignment.CenterEnd else Alignment.CenterStart
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = align
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
                                                .width(20.dp)
                                                .padding(top = 8.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            GPTBreathingBall()
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    if (msg.content.isBlank() && showBreathingBall) {
                                        Spacer(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(56.dp)
                                        )
                                    } else {
                                        AssistantMarkdownContent(
                                            content = msg.content,
                                            modifier = Modifier
                                                .weight(1f)
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = msg.content,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFFECECEF))
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color(0xFF161616)
                                )
                            }
                        }
                    }
            }

            if (messages.isEmpty()) {
                Text(
                    text = "欢迎咨询种植、病虫害防治、施肥等问题。\n描述作物/地区/现象，必要时可上传图片。",
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 104.dp, start = 24.dp, end = 24.dp),
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
                    color = Color.White,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp)
                        .navigationBarsPadding()
                        .size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "回到底部",
                            tint = Color(0xFF111111),
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer { rotationZ = -90f }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    tonalElevation = 1.dp,
                    shadowElevation = 1.dp,
                    modifier = Modifier.size(46.dp)
                ) {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Menu, contentDescription = "菜单", tint = Color(0xFF222222))
                    }
                }
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    tonalElevation = 1.dp,
                    shadowElevation = 1.dp
                ) {
                    Text(
                        text = "农技千问",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        color = Color(0xFF111111),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
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
