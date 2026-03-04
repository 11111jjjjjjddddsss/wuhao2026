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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.random.Random
import java.util.UUID

private enum class ChatRole { USER, ASSISTANT }
private data class ChatMessage(val id: String, val role: ChatRole, val content: String)

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String) : MdBlock
    data class Numbered(val index: String, val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
}

private val numberedRegex = Regex("""^(\d+)[.)]\s+(.*)$""")

private fun parseMarkdownLikeBlocks(content: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = mutableListOf<String>()
    val lines = content.replace("\r\n", "\n").split('\n')
    var inCode = false
    val code = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MdBlock.Paragraph(paragraph.joinToString("\n").trim())
            paragraph.clear()
        }
    }

    lines.forEach { raw ->
        val trimmed = raw.trim()

        if (trimmed.startsWith("```")) {
            if (inCode) {
                blocks += MdBlock.Code(code.joinToString("\n"))
                code.clear()
                inCode = false
            } else {
                flushParagraph()
                inCode = true
            }
            return@forEach
        }

        if (inCode) {
            code += raw
            return@forEach
        }

        if (trimmed.isBlank()) {
            flushParagraph()
            return@forEach
        }

        val headingLevel = when {
            trimmed.startsWith("### ") -> 3
            trimmed.startsWith("## ") -> 2
            trimmed.startsWith("# ") -> 1
            else -> 0
        }
        if (headingLevel > 0) {
            flushParagraph()
            blocks += MdBlock.Heading(headingLevel, trimmed.substring(headingLevel + 1).trim())
            return@forEach
        }

        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            flushParagraph()
            blocks += MdBlock.Bullet(trimmed.substring(2).trim())
            return@forEach
        }

        val numbered = numberedRegex.matchEntire(trimmed)
        if (numbered != null) {
            flushParagraph()
            blocks += MdBlock.Numbered(numbered.groupValues[1], numbered.groupValues[2].trim())
            return@forEach
        }

        paragraph += trimmed
    }

    flushParagraph()
    if (inCode && code.isNotEmpty()) blocks += MdBlock.Code(code.joinToString("\n"))
    return blocks
}

@Composable
private fun AssistantMarkdownContent(content: String, modifier: Modifier = Modifier) {
    val blocks = remember(content) { parseMarkdownLikeBlocks(content) }
    val paragraphStyle = TextStyle(
        fontSize = 17.sp,
        lineHeight = 30.sp,
        color = Color(0xFF181818)
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val size = when (block.level) {
                        1 -> 22.sp
                        2 -> 20.sp
                        else -> 18.sp
                    }
                    Text(
                        text = block.text,
                        fontSize = size,
                        lineHeight = (size.value + 8).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF141414)
                    )
                }

                is MdBlock.Paragraph -> Text(text = block.text, style = paragraphStyle)

                is MdBlock.Bullet -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Text(text = "-", fontSize = 17.sp, lineHeight = 30.sp, color = Color(0xFF181818))
                        Text(text = block.text, style = paragraphStyle, modifier = Modifier.weight(1f))
                    }
                }

                is MdBlock.Numbered -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Text(text = "${block.index}.", fontSize = 17.sp, lineHeight = 30.sp, color = Color(0xFF181818))
                        Text(text = block.text, style = paragraphStyle, modifier = Modifier.weight(1f))
                    }
                }

                is MdBlock.Code -> {
                    Text(
                        text = block.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        lineHeight = 26.sp,
                        color = Color(0xFF1C1C1C)
                    )
                }
            }
        }
    }
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
fun ChatScreen() {
    val input = remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val listState = rememberLazyListState()
    val isReverse = true
    fun bottomIndex(total: Int): Int = if (isReverse) 0 else (total - 1).coerceAtLeast(0)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    var fakeStreamJob by remember { mutableStateOf<Job?>(null) }

    var isStreaming by remember { mutableStateOf(false) }
    var assistantMessageId by remember { mutableStateOf<String?>(null) }
    var autoFollowEnabled by remember { mutableStateOf(true) }
    var streamTick by remember { mutableStateOf(0) }
    var sendTick by remember { mutableStateOf(0) }
    var programmaticScroll by remember { mutableStateOf(false) }
    var pendingJumpToBottom by remember { mutableStateOf(false) }
    var lastUserScrollPos by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val atBottom by remember { derivedStateOf { !listState.canScrollBackward } }
    val shouldStickToBottom = atBottom
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
                if (currentId == assistantMessageId) {
                    streamTick++
                }
            } else {
                messages.add(ChatMessage(currentId, ChatRole.ASSISTANT, piece))
                streamTick++
            }
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

        val assistantId = "assistant_${UUID.randomUUID()}"
        messages.add(ChatMessage(assistantId, ChatRole.ASSISTANT, ""))
        isStreaming = true
        assistantMessageId = assistantId
        autoFollowEnabled = true
        if (!listState.isScrollInProgress) {
            pendingJumpToBottom = false
            sendTick++
        } else {
            pendingJumpToBottom = true
        }

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
            var pauseThreshold = Random.nextInt(120, 221)
            while (isActive && cursor < fullText.length) {
                val chunkSize = Random.nextInt(3, 11)
                val next = min(cursor + chunkSize, fullText.length)
                val piece = fullText.substring(cursor, next)
                appendAssistantChunk(piece)
                emittedSincePause += piece.length
                cursor = next
                delay(Random.nextLong(35, 71))
                if (emittedSincePause >= pauseThreshold && cursor < fullText.length) {
                    emittedSincePause = 0
                    pauseThreshold = Random.nextInt(120, 221)
                    delay(Random.nextLong(180, 421))
                }
            }
            if (isActive) finishStreaming()
        }
    }

    suspend fun scrollToBottom(animated: Boolean) {
        if (messages.isEmpty()) return
        val bottom = bottomIndex(messages.size)
        programmaticScroll = true
        try {
            withFrameNanos { }
            if (animated) listState.animateScrollToItem(bottom) else listState.scrollToItem(bottom)
            withFrameNanos { }
            if (animated) listState.animateScrollToItem(bottom) else listState.scrollToItem(bottom)
        } finally {
            programmaticScroll = false
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            Triple(
                listState.isScrollInProgress,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }.collectLatest { (isScrolling, idx, off) ->
            if (isScrolling && !programmaticScroll) {
                val cur = idx to off
                val last = lastUserScrollPos
                if (last != null && last != cur) {
                    autoFollowEnabled = false
                }
                lastUserScrollPos = cur
            } else {
                lastUserScrollPos = null
                if (!isScrolling && pendingJumpToBottom) {
                    pendingJumpToBottom = false
                    sendTick++
                }
            }
        }
    }

    LaunchedEffect(streamTick) {
        if (autoFollowEnabled && !listState.isScrollInProgress) {
            scrollToBottom(animated = false)
        }
    }

    LaunchedEffect(sendTick) {
        if (autoFollowEnabled) {
            scrollToBottom(animated = false)
        }
    }

    fun jumpToBottom() {
        snackbarScope.launch {
            if (messages.isEmpty()) return@launch
            autoFollowEnabled = true
            scrollToBottom(animated = true)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5)),
        containerColor = Color(0xFFF5F5F5),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
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

                        val canSend = input.value.trim().isNotEmpty()
                        val actionEnabled = isStreaming || canSend
                        val actionBg = if (actionEnabled) Color(0xFF101010) else Color.Transparent
                        val actionTint = if (actionEnabled) Color.White else Color(0xFF1A1A1A)

                        IconButton(
                            onClick = {
                                if (isStreaming) {
                                    fakeStreamJob?.cancel()
                                    finishStreaming()
                                } else if (canSend) {
                                    sendMessage()
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 6.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(actionBg)
                        ) {
                            Icon(
                                imageVector = if (isStreaming) Icons.Default.Close else Icons.Default.Send,
                                contentDescription = if (isStreaming) "停止" else "发送",
                                tint = actionTint
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
                    .padding(horizontal = 20.dp),
                state = listState,
                reverseLayout = isReverse,
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = if (messages.isEmpty()) 80.dp else 8.dp
                )
            ) {
                if (messages.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 112.dp, end = 4.dp)
                        ) {
                            Text(
                                text = "欢迎咨询种植、病虫害防治、施肥等问题。\n描述作物/地区/现象，必要时可上传图片。",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFF141414),
                                lineHeight = MaterialTheme.typography.titleMedium.lineHeight,
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                } else {
                    items(if (isReverse) messages.asReversed() else messages, key = { it.id }) { msg ->
                        val align = if (msg.role == ChatRole.USER) Alignment.CenterEnd else Alignment.CenterStart
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            contentAlignment = align
                        ) {
                            if (msg.role == ChatRole.ASSISTANT) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(20.dp)
                                            .padding(top = 8.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (isStreaming && msg.id == assistantMessageId && msg.content.isBlank()) {
                                            GPTBreathingBall()
                                        }
                                    }
                                    if (msg.content.isBlank() && isStreaming && msg.id == assistantMessageId) {
                                        Spacer(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(24.dp)
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
            }

            if (messages.isNotEmpty() && (!shouldStickToBottom || !autoFollowEnabled)) {
                Surface(
                    onClick = { jumpToBottom() },
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 72.dp)
                        .navigationBarsPadding()
                        .imePadding()
                        .size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "回到底部",
                            tint = Color(0xFF111111),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
初步判断：你描述的叶片失绿、午后萎蔫和局部长势不齐，更像环境波动叠加管理节奏不稳导致的综合性问题，暂时不能直接归因为单一病虫害。先按下面的问诊路径收敛信息，目标是先控风险，再在两到三天内把结论做实。

## 一、补充信息
1. 作物与阶段：品种、定植时间、当前在营养生长还是开花坐果期，不同阶段对温湿和水分波动的耐受差异明显。
2. 异常起点：最早出现在新叶、老叶、叶缘、叶脉还是茎基部，起点位置通常比最终症状更有诊断价值。
3. 变化速度：一天内突然加重，还是三到五天缓慢扩展，速度直接影响排查优先级。
4. 空间分布：零星点状、行间成片、还是整棚同步，分布特征可区分管理扰动与扩展性问题。
5. 近期操作：近七天是否有浇水节奏改变、通风调整、阴雨转晴或机械扰动，这些都可能触发连锁反应。

## 二、可能原因 Top3
1. 根际供需短时失衡：含水和通气在短周期反复波动，常见表现是白天萎蔫、夜间缓解、边缘轻失绿。
2. 小气候胁迫叠加：同棚温湿差偏大，边角位和风口位起伏明显，症状随时段变化而变化。
3. 管理节奏与生育期错位：不同阶段沿用同一强度管理，造成部分株体负担过高，出现长势分化。

## 三、观察与复查
当天先做可回退动作：浇水、通风、遮阴都采用小幅、连续、可追踪的调整，不做一次性大幅改变。设置三到五个固定观测点，覆盖重、中、轻三类株体，同一时间拍近景与中景，记录叶色、挺度、边缘和新叶状态。

次日复查只看四件事：症状边界是否继续外扩，异常株与健康株差距是否拉大，新叶是否持续变差，午后和傍晚差异是否缩小。若出现范围趋稳、恢复变快、差距收敛，可继续稳态观察；若扩展加快、萎蔫提前、差异放大，则进入升级复核。

## 四、风险提示
避免三个误区：一是看到表象就立刻定因，忽略多因素叠加；二是只看单次照片，不看连续时序；三是在证据不足时频繁大动作调整，造成二次扰动。建议执行顺序为先止损、再验证、后升级，每个判断都附带条件和复查节点。

## 五、执行结论
今天先稳定管理并完成定点记录，明天同一时段复查关键指标，再决定是否升级处理。这样做不是一次性终判，而是建立可落地、可复核、可回退的问诊闭环，能在移动端持续跟进并降低误判成本。
""".trimIndent()
