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
import androidx.compose.ui.text.font.FontFamily
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
                            contentDescription = "娣诲姞",
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
                            placeholder = { Text("鎻忚堪浣滅墿/鍦板尯/闂", color = Color(0xFF9A9A9A)) },
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
                                .padding(vertical = 6.dp),
                            contentAlignment = align
                        ) {
                            if (msg.role == ChatRole.ASSISTANT) {
                                val showBreathingBall = isStreaming && msg.id == assistantMessageId && msg.content.isBlank()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
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
                            contentDescription = "鍥炲埌搴曢儴",
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
                        Icon(Icons.Default.Menu, contentDescription = "鑿滃崟", tint = Color(0xFF222222))
                    }
                }
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    tonalElevation = 1.dp,
                    shadowElevation = 1.dp
                ) {
                    Text(
                        text = "鍐滄妧鍗冮棶",
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
鍒濇鍒ゆ柇锛氫綘鎻忚堪鐨勫彾鐗囧け缁裤€佸崍鍚庤悗钄拰灞€閮ㄩ暱鍔夸笉榻愶紝鏇村儚鐜娉㈠姩鍙犲姞绠＄悊鑺傚涓嶇ǔ瀵艰嚧鐨勭患鍚堟€ч棶棰橈紝鏆傛椂涓嶈兘鐩存帴褰掑洜涓哄崟涓€鐥呰櫕瀹炽€傚厛鎸変笅闈㈢殑闂瘖璺緞鏀舵暃淇℃伅锛岀洰鏍囨槸鍏堟帶椋庨櫓锛屽啀鍦ㄤ袱鍒颁笁澶╁唴鎶婄粨璁哄仛瀹炪€?

## 涓€銆佽ˉ鍏呬俊鎭?
1. 浣滅墿涓庨樁娈碉細鍝佺銆佸畾妞嶆椂闂淬€佸綋鍓嶅湪钀ュ吇鐢熼暱杩樻槸寮€鑺卞潗鏋滄湡锛屼笉鍚岄樁娈靛娓╂箍鍜屾按鍒嗘尝鍔ㄧ殑鑰愬彈宸紓鏄庢樉銆?
2. 寮傚父璧风偣锛氭渶鏃╁嚭鐜板湪鏂板彾銆佽€佸彾銆佸彾缂樸€佸彾鑴夎繕鏄寧鍩洪儴锛岃捣鐐逛綅缃€氬父姣旀渶缁堢棁鐘舵洿鏈夎瘖鏂环鍊笺€?
3. 鍙樺寲閫熷害锛氫竴澶╁唴绐佺劧鍔犻噸锛岃繕鏄笁鍒颁簲澶╃紦鎱㈡墿灞曪紝閫熷害鐩存帴褰卞搷鎺掓煡浼樺厛绾с€?
4. 绌洪棿鍒嗗竷锛氶浂鏄熺偣鐘躲€佽闂存垚鐗囥€佽繕鏄暣妫氬悓姝ワ紝鍒嗗竷鐗瑰緛鍙尯鍒嗙鐞嗘壈鍔ㄤ笌鎵╁睍鎬ч棶棰樸€?
5. 杩戞湡鎿嶄綔锛氳繎涓冨ぉ鏄惁鏈夋祰姘磋妭濂忔敼鍙樸€侀€氶璋冩暣銆侀槾闆ㄨ浆鏅存垨鏈烘鎵板姩锛岃繖浜涢兘鍙兘瑙﹀彂杩為攣鍙嶅簲銆?

## 浜屻€佸彲鑳藉師鍥?Top3
1. 鏍归檯渚涢渶鐭椂澶辫　锛氬惈姘村拰閫氭皵鍦ㄧ煭鍛ㄦ湡鍙嶅娉㈠姩锛屽父瑙佽〃鐜版槸鐧藉ぉ钀庤敨銆佸闂寸紦瑙ｃ€佽竟缂樿交澶辩豢銆?
2. 灏忔皵鍊欒儊杩彔鍔狅細鍚屾娓╂箍宸亸澶э紝杈硅浣嶅拰椋庡彛浣嶈捣浼忔槑鏄撅紝鐥囩姸闅忔椂娈靛彉鍖栬€屽彉鍖栥€?
3. 绠＄悊鑺傚涓庣敓鑲叉湡閿欎綅锛氫笉鍚岄樁娈垫部鐢ㄥ悓涓€寮哄害绠＄悊锛岄€犳垚閮ㄥ垎鏍綋璐熸媴杩囬珮锛屽嚭鐜伴暱鍔垮垎鍖栥€?

## 涓夈€佽瀵熶笌澶嶆煡
褰撳ぉ鍏堝仛鍙洖閫€鍔ㄤ綔锛氭祰姘淬€侀€氶銆侀伄闃撮兘閲囩敤灏忓箙銆佽繛缁€佸彲杩借釜鐨勮皟鏁达紝涓嶅仛涓€娆℃€уぇ骞呮敼鍙樸€傝缃笁鍒颁簲涓浐瀹氳娴嬬偣锛岃鐩栭噸銆佷腑銆佽交涓夌被鏍綋锛屽悓涓€鏃堕棿鎷嶈繎鏅笌涓櫙锛岃褰曞彾鑹层€佹尯搴︺€佽竟缂樺拰鏂板彾鐘舵€併€?

娆℃棩澶嶆煡鍙湅鍥涗欢浜嬶細鐥囩姸杈圭晫鏄惁缁х画澶栨墿锛屽紓甯告牚涓庡仴搴锋牚宸窛鏄惁鎷夊ぇ锛屾柊鍙舵槸鍚︽寔缁彉宸紝鍗堝悗鍜屽倣鏅氬樊寮傛槸鍚︾缉灏忋€傝嫢鍑虹幇鑼冨洿瓒嬬ǔ銆佹仮澶嶅彉蹇€佸樊璺濇敹鏁涳紝鍙户缁ǔ鎬佽瀵燂紱鑻ユ墿灞曞姞蹇€佽悗钄彁鍓嶃€佸樊寮傛斁澶э紝鍒欒繘鍏ュ崌绾у鏍搞€?

## 鍥涖€侀闄╂彁绀?
閬垮厤涓変釜璇尯锛氫竴鏄湅鍒拌〃璞″氨绔嬪埢瀹氬洜锛屽拷鐣ュ鍥犵礌鍙犲姞锛涗簩鏄彧鐪嬪崟娆＄収鐗囷紝涓嶇湅杩炵画鏃跺簭锛涗笁鏄湪璇佹嵁涓嶈冻鏃堕绻佸ぇ鍔ㄤ綔璋冩暣锛岄€犳垚浜屾鎵板姩銆傚缓璁墽琛岄『搴忎负鍏堟鎹熴€佸啀楠岃瘉銆佸悗鍗囩骇锛屾瘡涓垽鏂兘闄勫甫鏉′欢鍜屽鏌ヨ妭鐐广€?

## 浜斻€佹墽琛岀粨璁?
浠婂ぉ鍏堢ǔ瀹氱鐞嗗苟瀹屾垚瀹氱偣璁板綍锛屾槑澶╁悓涓€鏃舵澶嶆煡鍏抽敭鎸囨爣锛屽啀鍐冲畾鏄惁鍗囩骇澶勭悊銆傝繖鏍峰仛涓嶆槸涓€娆℃€х粓鍒わ紝鑰屾槸寤虹珛鍙惤鍦般€佸彲澶嶆牳銆佸彲鍥為€€鐨勯棶璇婇棴鐜紝鑳藉湪绉诲姩绔寔缁窡杩涘苟闄嶄綆璇垽鎴愭湰銆?
""".trimIndent()

