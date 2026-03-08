package com.nongjiqianwen

import android.content.Context
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
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
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
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.LinkedHashMap
import kotlin.random.Random
import kotlin.math.roundToInt
import java.util.UUID

private enum class ChatRole { USER, ASSISTANT }
private enum class AutoScrollMode { Idle, AnchorUser, StreamAnchorFollow }
@Immutable
private data class ChatMessage(val id: String, val role: ChatRole, val content: String)
private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Bullet(val text: String) : MarkdownBlock
    data class Numbered(val number: String, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
}

private const val LOCAL_RENDER_ROUND_LIMIT = 30
private const val CHAT_CACHE_PREFS = "chat_ui_cache"
private const val CHAT_CACHE_KEY_PREFIX = "render_window_"
private const val INLINE_MARKDOWN_CACHE_LIMIT = 120
private const val BLOCK_MARKDOWN_CACHE_LIMIT = 80
private const val JUMP_BUTTON_AUTO_HIDE_MS = 1200L
private const val STREAM_AUTO_SCROLL_THROTTLE_MS = 36L
private const val STREAM_TYPEWRITER_IDLE_POLL_MS = 12L
private const val STREAM_REVEAL_FRAME_BUDGET_MS = 42L
private const val STREAM_REVEAL_MAX_TOKENS_PER_BATCH = 8
private const val LOCAL_STREAM_FIRST_TOKEN_MIN_MS = 860L
private const val LOCAL_STREAM_FIRST_TOKEN_MAX_MS = 1420L
private const val LOCAL_STREAM_MIN_BALL_MS = 1480L
private const val STREAM_ANIMATED_SCROLL_MAX_DELTA_PX = 220
private const val STREAM_STICKY_SCROLL_STEP_PX = 96
private const val SEND_ANCHOR_USER_BOTTOM_RATIO = 0.46f
private const val SEND_ANCHOR_EXTRA_BOTTOM_SPACE_RATIO = 0.44f
private const val STREAM_ANCHOR_COMPENSATE_THRESHOLD_PX = 12
private const val STREAM_FOLLOW_ANIMATE_THRESHOLD_PX = 120
private val STREAMING_MESSAGE_MIN_HEIGHT = 92.dp
private val STREAM_AUTO_FOLLOW_SLOP = 28.dp
private val MIN_SEND_ANCHOR_EXTRA_BOTTOM_SPACE = 220.dp
private val chatCacheGson = Gson()
private val chatCacheListType = object : TypeToken<List<ChatMessage>>() {}.type
private val headingRegex = Regex("^#{1,6}\\s+.*$")
private val bulletRegex = Regex("^[*-]\\s+.*$")
private val numberedRegex = Regex("^\\d+\\.\\s+.*$")
private val quoteRegex = Regex("^>\\s+.*$")
private val linkRegex = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")
private val inlineMarkdownCache = object : LinkedHashMap<String, AnnotatedString>(INLINE_MARKDOWN_CACHE_LIMIT, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AnnotatedString>?): Boolean {
        return size > INLINE_MARKDOWN_CACHE_LIMIT
    }
}
private val blockMarkdownCache = object : LinkedHashMap<String, List<MarkdownUiBlock>>(BLOCK_MARKDOWN_CACHE_LIMIT, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<MarkdownUiBlock>>?): Boolean {
        return size > BLOCK_MARKDOWN_CACHE_LIMIT
    }
}

private fun normalizeAssistantText(content: String): String {
    return content
        .replace("\r\n", "\n")
        .trim()
}

private data class StreamingMarkdownParts(
    val completedContent: String,
    val tailContent: String
)

private fun splitStreamingMarkdownParts(content: String): StreamingMarkdownParts {
    val normalized = content
        .replace("\r\n", "\n")
    if (normalized.isBlank()) {
        return StreamingMarkdownParts(completedContent = "", tailContent = "")
    }

    if (!normalized.contains('\n')) {
        return StreamingMarkdownParts(completedContent = "", tailContent = normalized)
    }

    if (normalized.last() == '\n') {
        val completed = normalized.dropLast(1)
        return StreamingMarkdownParts(
            completedContent = normalizeAssistantText(completed),
            tailContent = ""
        )
    }

    val splitIndex = normalized.lastIndexOf('\n')
    val completed = normalized.substring(0, splitIndex)
    val tail = normalized.substring(splitIndex + 1)
    return StreamingMarkdownParts(
        completedContent = normalizeAssistantText(completed),
        tailContent = tail
    )
}

private fun Char.isCjkUnifiedIdeograph(): Boolean {
    val block = Character.UnicodeBlock.of(this)
    return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
        block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
}

private fun Char.isStrongPausePunctuation(): Boolean {
    return this == '\n' || this == '。' || this == '！' || this == '？' || this == '!' || this == '?'
}

private fun Char.isWeakPausePunctuation(): Boolean {
    return this == '，' || this == '；' || this == '：' || this == ',' || this == ';' || this == ':'
}

private fun Char.isStructuralMarkdownChar(): Boolean {
    return this == '#' || this == '-' || this == '*' || this == '`' || this == '>'
}

private fun takeTypewriterToken(buffer: String): String {
    if (buffer.isEmpty()) return ""
    val first = buffer.first()
    if (first == '\n' || first.isWhitespace() || first.isStructuralMarkdownChar()) {
        return first.toString()
    }
    if (first.isCjkUnifiedIdeograph()) {
        var end = 1
        while (end < buffer.length && end < 3 && buffer[end].isCjkUnifiedIdeograph()) end++
        return buffer.substring(0, end)
    }
    if (first.isDigit()) {
        var end = 1
        while (end < buffer.length && end < 3 && buffer[end].isDigit()) end++
        if (end < buffer.length && buffer[end] == '.') end++
        return buffer.substring(0, end)
    }
    var end = 1
    while (end < buffer.length && end < 4) {
        val ch = buffer[end]
        if (ch.isWhitespace() || ch.isCjkUnifiedIdeograph() || ch.isStructuralMarkdownChar() || ch.isWeakPausePunctuation() || ch.isStrongPausePunctuation()) {
            break
        }
        end++
    }
    return buffer.substring(0, end)
}

private data class LocalStreamFeedStep(
    val text: String,
    val delayMs: Long
)

private fun nextLocalStreamFeedStep(remaining: String): LocalStreamFeedStep {
    if (remaining.isEmpty()) return LocalStreamFeedStep("", 0L)
    val first = remaining.first()
    val takeCount = when {
        first == '\n' -> 1
        first.isStructuralMarkdownChar() -> 1
        first.isCjkUnifiedIdeograph() -> Random.nextInt(1, 3)
        first.isWhitespace() -> 1
        else -> Random.nextInt(2, 5)
    }.coerceAtMost(remaining.length)
    val text = remaining.substring(0, takeCount)
    val tail = text.last()
    val delayMs = when {
        tail == '\n' -> Random.nextLong(150, 260)
        tail.isStrongPausePunctuation() -> Random.nextLong(110, 210)
        tail.isWeakPausePunctuation() -> Random.nextLong(72, 140)
        text.any { it.isStructuralMarkdownChar() } -> Random.nextLong(70, 120)
        else -> Random.nextLong(28, 56)
    }
    return LocalStreamFeedStep(text = text, delayMs = delayMs)
}

private fun hasStructuralMarkdownPrefix(text: String): Boolean {
    val trimmed = text.trimStart()
    return trimmed.startsWith("#") ||
        trimmed.startsWith("- ") ||
        trimmed.startsWith("* ") ||
        trimmed.startsWith("> ") ||
        numberedRegex.matches(trimmed)
}

private fun resolveTypewriterDelay(token: String, remainingBuffer: String): Long {
    val lastChar = token.lastOrNull() ?: return STREAM_TYPEWRITER_IDLE_POLL_MS
    return when {
        lastChar == '\n' -> if (hasStructuralMarkdownPrefix(remainingBuffer)) 104L else 78L
        lastChar.isStrongPausePunctuation() -> 44L
        lastChar.isWeakPausePunctuation() -> 24L
        token.length >= 4 -> 8L
        token.length == 3 -> 9L
        token.length == 2 -> 10L
        else -> if (lastChar.isCjkUnifiedIdeograph()) 12L else 10L
    }
}

private data class StreamingTypewriterStep(
    val text: String,
    val delayMs: Long
)

private fun nextStreamingTypewriterStep(buffer: String): StreamingTypewriterStep {
    if (buffer.isEmpty()) {
        return StreamingTypewriterStep("", STREAM_TYPEWRITER_IDLE_POLL_MS)
    }
    val text = takeTypewriterToken(buffer)
    val remaining = buffer.drop(text.length)
    return StreamingTypewriterStep(
        text = text,
        delayMs = resolveTypewriterDelay(text, remaining)
    )
}

private data class StreamingRevealBatch(
    val text: String,
    val delayMs: Long
)

private fun buildStreamingRevealBatch(buffer: String): StreamingRevealBatch {
    if (buffer.isEmpty()) return StreamingRevealBatch("", STREAM_TYPEWRITER_IDLE_POLL_MS)
    val text = StringBuilder()
    var consumedDelay = 0L
    var remaining = buffer
    var tokenCount = 0

    while (remaining.isNotEmpty() && tokenCount < STREAM_REVEAL_MAX_TOKENS_PER_BATCH) {
        val step = nextStreamingTypewriterStep(remaining)
        if (step.text.isEmpty()) break
        text.append(step.text)
        remaining = remaining.drop(step.text.length)
        consumedDelay += step.delayMs
        tokenCount++
        val tail = step.text.lastOrNull()
        val hitPause = tail == '\n' || tail?.isStrongPausePunctuation() == true
        if (hitPause || consumedDelay >= STREAM_REVEAL_FRAME_BUDGET_MS) {
            break
        }
    }

    return StreamingRevealBatch(
        text = text.toString(),
        delayMs = consumedDelay.coerceAtLeast(STREAM_TYPEWRITER_IDLE_POLL_MS)
    )
}

private fun parseMarkdownBlocks(content: String): List<MarkdownBlock> {
    val normalized = normalizeAssistantText(content)
    if (normalized.isBlank()) return emptyList()
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = StringBuilder()

    fun flushParagraph() {
        val text = paragraph.toString().trim()
        if (text.isNotEmpty()) blocks += MarkdownBlock.Paragraph(text)
        paragraph.clear()
    }

    normalized.lines().forEach { rawLine ->
        val trimmed = rawLine.trim()
        when {
            trimmed.isBlank() -> flushParagraph()
            trimmed.startsWith("```") -> Unit
            trimmed.matches(headingRegex) -> {
                flushParagraph()
                val marker = trimmed.takeWhile { it == '#' }
                blocks += MarkdownBlock.Heading(marker.length, trimmed.drop(marker.length).trim())
            }
            trimmed.matches(bulletRegex) -> {
                flushParagraph()
                blocks += MarkdownBlock.Bullet(trimmed.drop(1).trim())
            }
            trimmed.matches(numberedRegex) -> {
                flushParagraph()
                blocks += MarkdownBlock.Numbered(trimmed.substringBefore('.'), trimmed.substringAfter('.').trim())
            }
            trimmed.matches(quoteRegex) -> {
                flushParagraph()
                blocks += MarkdownBlock.Paragraph(trimmed.drop(1).trim())
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(trimmed)
            }
        }
    }

    flushParagraph()
    return blocks
}

private fun markdownInlineSpanStyle(
    isBold: Boolean,
    isItalic: Boolean,
    isCode: Boolean
): SpanStyle {
    return SpanStyle(
        fontWeight = if (isBold) FontWeight.SemiBold else null,
        fontStyle = if (isItalic) FontStyle.Italic else null,
        fontFamily = if (isCode) FontFamily.Monospace else null,
        background = if (isCode) Color(0xFFF2F3F5) else Color.Unspecified
    )
}

private fun buildMarkdownAnnotatedStringInternal(
    text: String
): AnnotatedString {
    val normalized = text.replace(linkRegex, "$1")
    return buildAnnotatedString {
        var index = 0
        var bold = false
        var italic = false
        var code = false

        fun appendStyled(chunk: String) {
            if (chunk.isEmpty()) return
            withStyle(markdownInlineSpanStyle(bold, italic, code)) {
                append(chunk)
            }
        }

        while (index < normalized.length) {
            when {
                !code && normalized.startsWith("**", index) -> {
                    bold = !bold
                    index += 2
                }
                normalized[index] == '`' -> {
                    code = !code
                    index += 1
                }
                !code && normalized[index] == '*' -> {
                    italic = !italic
                    index += 1
                }
                else -> {
                    val nextSpecial = buildList {
                        val boldIndex = if (!code) normalized.indexOf("**", index).takeIf { it >= 0 } else null
                        val codeIndex = normalized.indexOf('`', index).takeIf { it >= 0 }
                        val italicIndex = if (!code) normalized.indexOf('*', index).takeIf { it >= 0 } else null
                        if (boldIndex != null) add(boldIndex)
                        if (codeIndex != null) add(codeIndex)
                        if (italicIndex != null) add(italicIndex)
                    }.minOrNull() ?: normalized.length
                    appendStyled(normalized.substring(index, nextSpecial))
                    index = nextSpecial
                }
            }
        }
    }
}

private fun buildMarkdownAnnotatedString(text: String): AnnotatedString {
    return buildMarkdownAnnotatedStringInternal(text = text)
}

private fun buildStreamingAnnotatedString(text: String): AnnotatedString {
    return buildMarkdownAnnotatedStringInternal(text = text)
}

private fun getCachedAnnotatedString(text: String): AnnotatedString {
    synchronized(inlineMarkdownCache) {
        inlineMarkdownCache[text]?.let { return it }
    }
    val built = buildMarkdownAnnotatedString(text)
    synchronized(inlineMarkdownCache) {
        inlineMarkdownCache[text] = built
    }
    return built
}

private fun getCachedMarkdownUiBlocks(content: String): List<MarkdownUiBlock> {
    synchronized(blockMarkdownCache) {
        blockMarkdownCache[content]?.let { return it }
    }
    val built = parseMarkdownBlocks(content).map { block ->
        when (block) {
            is MarkdownBlock.Heading -> MarkdownUiBlock.Heading(block.level, getCachedAnnotatedString(block.text))
            is MarkdownBlock.Bullet -> MarkdownUiBlock.Bullet(getCachedAnnotatedString(block.text))
            is MarkdownBlock.Numbered -> MarkdownUiBlock.Numbered(block.number, getCachedAnnotatedString(block.text))
            is MarkdownBlock.Paragraph -> MarkdownUiBlock.Paragraph(getCachedAnnotatedString(block.text))
        }
    }
    synchronized(blockMarkdownCache) {
        blockMarkdownCache[content] = built
    }
    return built
}

private fun assistantParagraphTextStyle(): TextStyle = TextStyle(
    fontSize = 17.sp,
    lineHeight = 32.sp,
    color = Color(0xFF171717)
)

private fun assistantHeadingTextStyle(level: Int): TextStyle = TextStyle(
    fontSize = if (level <= 2) 22.sp else 19.sp,
    lineHeight = if (level <= 2) 38.sp else 33.sp,
    fontWeight = FontWeight.Bold,
    color = Color(0xFF111111)
)

private fun trimWindowStartIndex(source: List<ChatMessage>): Int {
    var userCount = 0
    source.forEach { if (it.role == ChatRole.USER) userCount++ }
    if (userCount <= LOCAL_RENDER_ROUND_LIMIT) return 0
    var keepFromUser = userCount - LOCAL_RENDER_ROUND_LIMIT
    source.forEachIndexed { index, message ->
        if (message.role == ChatRole.USER) {
            if (keepFromUser == 0) return index
            keepFromUser--
        }
    }
    return 0
}

private fun trimMessageWindow(source: List<ChatMessage>): List<ChatMessage> {
    val startIndex = trimWindowStartIndex(source)
    if (startIndex <= 0) return source
    return source.drop(startIndex)
}

private suspend fun Context.loadLocalChatWindow(sessionId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
    val raw = getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE)
        .getString("$CHAT_CACHE_KEY_PREFIX$sessionId", null)
        .orEmpty()
    if (raw.isBlank()) return@withContext emptyList()
    runCatching {
        @Suppress("UNCHECKED_CAST")
        (chatCacheGson.fromJson<List<ChatMessage>>(raw, chatCacheListType) ?: emptyList())
    }.getOrDefault(emptyList())
}

private fun Context.loadLocalChatWindowSync(sessionId: String): List<ChatMessage> {
    val raw = getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE)
        .getString("$CHAT_CACHE_KEY_PREFIX$sessionId", null)
        .orEmpty()
    if (raw.isBlank()) return emptyList()
    return runCatching {
        @Suppress("UNCHECKED_CAST")
        (chatCacheGson.fromJson<List<ChatMessage>>(raw, chatCacheListType) ?: emptyList())
    }.getOrDefault(emptyList())
}

private suspend fun Context.saveLocalChatWindow(sessionId: String, messages: List<ChatMessage>) = withContext(Dispatchers.IO) {
    val trimmed = trimMessageWindow(messages)
    getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString("$CHAT_CACHE_KEY_PREFIX$sessionId", chatCacheGson.toJson(trimmed))
        .apply()
}

private suspend fun prewarmAssistantMarkdown(messages: List<ChatMessage>) = withContext(Dispatchers.Default) {
    messages
        .filter { it.role == ChatRole.ASSISTANT && it.content.isNotBlank() }
        .takeLast(12)
        .forEach { getCachedMarkdownUiBlocks(it.content) }
}

private fun snapshotRoundsToMessages(rounds: List<ARound>): List<ChatMessage> {
    return rounds.flatMapIndexed { index, round ->
        buildList {
            if (round.user.isNotBlank()) add(ChatMessage("remote_user_$index", ChatRole.USER, round.user))
            if (round.assistant.isNotBlank()) add(ChatMessage("remote_assistant_$index", ChatRole.ASSISTANT, round.assistant))
        }
    }
}

private sealed interface MarkdownUiBlock {
    data class Heading(val level: Int, val text: AnnotatedString) : MarkdownUiBlock
    data class Bullet(val text: AnnotatedString) : MarkdownUiBlock
    data class Numbered(val number: String, val text: AnnotatedString) : MarkdownUiBlock
    data class Paragraph(val text: AnnotatedString) : MarkdownUiBlock
}

@Composable
private fun AssistantMessageContent(
    content: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    val stableModifier = if (isStreaming) {
        modifier.heightIn(min = STREAMING_MESSAGE_MIN_HEIGHT)
    } else {
        modifier
    }
    if (content.isBlank()) {
        if (isStreaming) {
            Box(
                modifier = stableModifier
                    .fillMaxWidth()
                    .height(44.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                GPTBreathingBall()
            }
        }
        return
    }

    if (isStreaming) {
        AssistantStreamingContent(content = content, modifier = stableModifier)
    } else {
        AssistantMarkdownContent(content = content, modifier = modifier)
    }
}

@Composable
private fun AssistantStreamingContent(content: String, modifier: Modifier = Modifier) {
    val parts = remember(content) { splitStreamingMarkdownParts(content) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (parts.completedContent.isNotBlank()) {
            AssistantMarkdownContent(content = parts.completedContent)
        }
        if (parts.tailContent.isNotBlank()) {
            AssistantStreamingTail(content = parts.tailContent)
        }
    }
}

@Composable
private fun AssistantStreamingTail(content: String) {
    val trimmed = content.trimStart()
    fun buildTail(text: String): AnnotatedString = buildStreamingAnnotatedString(text)

    when {
        trimmed.matches(headingRegex) -> {
            val marker = trimmed.takeWhile { it == '#' }
            Text(
                text = remember(trimmed) { buildTail(trimmed.drop(marker.length).trimStart()) },
                modifier = Modifier.fillMaxWidth(),
                style = assistantHeadingTextStyle(marker.length),
                textAlign = TextAlign.Start
            )
        }
        trimmed.matches(bulletRegex) -> {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "\u2022",
                    style = assistantParagraphTextStyle().copy(fontSize = 18.sp)
                )
                Text(
                    text = remember(trimmed) { buildTail(trimmed.drop(1).trimStart()) },
                    modifier = Modifier.weight(1f),
                    style = assistantParagraphTextStyle(),
                    textAlign = TextAlign.Start
                )
            }
        }
        trimmed.matches(numberedRegex) -> {
            val number = trimmed.substringBefore('.')
            val body = trimmed.substringAfter('.', "")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "$number.",
                    style = assistantParagraphTextStyle().copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = remember(body) { buildTail(body.trimStart()) },
                    modifier = Modifier.weight(1f),
                    style = assistantParagraphTextStyle(),
                    textAlign = TextAlign.Start
                )
            }
        }
        trimmed.matches(quoteRegex) -> {
            Text(
                text = remember(trimmed) { buildTail(trimmed.drop(1).trimStart()) },
                modifier = Modifier.fillMaxWidth(),
                style = assistantParagraphTextStyle(),
                textAlign = TextAlign.Start
            )
        }
        else -> {
            Text(
                text = remember(content) { buildTail(content) },
                modifier = Modifier.fillMaxWidth(),
                style = assistantParagraphTextStyle(),
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
private fun AssistantMarkdownContent(content: String, modifier: Modifier = Modifier) {
    val blocks = remember(content) { getCachedMarkdownUiBlocks(content) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownUiBlock.Heading -> Text(
                    text = block.text,
                    style = assistantHeadingTextStyle(block.level)
                )
                is MarkdownUiBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "\u2022",
                        style = assistantParagraphTextStyle().copy(fontSize = 18.sp),
                    )
                    Text(
                        text = block.text,
                        modifier = Modifier.weight(1f),
                        style = assistantParagraphTextStyle()
                    )
                }
                is MarkdownUiBlock.Numbered -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "${block.number}.",
                        style = assistantParagraphTextStyle().copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        text = block.text,
                        modifier = Modifier.weight(1f),
                        style = assistantParagraphTextStyle()
                    )
                }
                is MarkdownUiBlock.Paragraph -> Text(
                    text = block.text,
                    style = assistantParagraphTextStyle(),
                    textAlign = TextAlign.Start
                )
            }
        }
    }
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
        val stroke = size.minDimension * 0.12f
        val centerX = size.width / 2f
        val headY = if (directionUp) size.height * 0.14f else size.height * 0.86f
        val tailY = if (directionUp) size.height * 0.9f else size.height * 0.1f
        val wingY = if (directionUp) size.height * 0.48f else size.height * 0.52f
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
            start = Offset(size.width * 0.18f, y2),
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
private fun PlusCrossIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.12f
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
    val context = LocalContext.current
    val sessionId = remember { IdManager.getSessionId() }
    val initialLocalMessages = remember(sessionId) { context.loadLocalChatWindowSync(sessionId) }
    val messages = remember(sessionId) {
        mutableStateListOf<ChatMessage>().apply { addAll(initialLocalMessages) }
    }
    val scrollState = rememberScrollState(initial = Int.MAX_VALUE)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    var fakeStreamJob by remember { mutableStateOf<Job?>(null) }
    var streamRevealJob by remember { mutableStateOf<Job?>(null) }

    var isStreaming by remember { mutableStateOf(false) }
    var streamingMessageId by remember { mutableStateOf<String?>(null) }
    var streamingMessageContent by remember { mutableStateOf("") }
    var streamingRevealBuffer by remember { mutableStateOf("") }
    var autoScrollMode by remember { mutableStateOf(AutoScrollMode.Idle) }
    var userInteracting by remember { mutableStateOf(false) }
    var streamTick by remember { mutableIntStateOf(0) }
    var sendTick by remember { mutableIntStateOf(0) }
    var programmaticScroll by remember { mutableStateOf(false) }
    var lastAutoScrollMs by remember { mutableStateOf(0L) }
    var persistTick by remember { mutableIntStateOf(0) }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    var messageViewportHeightPx by remember { mutableIntStateOf(0) }
    var streamBottomSpacerPx by remember { mutableIntStateOf(0) }
    var messageViewportTopPx by remember { mutableStateOf(0f) }
    var anchoredUserMessageId by remember { mutableStateOf<String?>(null) }
    var anchoredUserBottomPx by remember { mutableIntStateOf(-1) }
    var anchoredTargetBottomPx by remember { mutableIntStateOf(0) }
    var jumpButtonVisible by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val followSlopPx = with(density) { STREAM_AUTO_FOLLOW_SLOP.toPx().toInt() }
    val minSendAnchorExtraBottomSpacePx = with(density) { MIN_SEND_ANCHOR_EXTRA_BOTTOM_SPACE.toPx().roundToInt() }
    val streamBottomSpacerDp = with(density) { streamBottomSpacerPx.toDp() }
    val hasStreamingItem by remember(isStreaming, streamingMessageContent) {
        derivedStateOf { isStreaming || streamingMessageContent.isNotBlank() }
    }
    val atBottom by remember {
        derivedStateOf {
            val remaining = scrollState.maxValue - scrollState.value
            remaining <= followSlopPx
        }
    }
    val imeVisible = WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp
    val shouldOfferJumpButton by remember(atBottom, imeVisible, messages.size, autoScrollMode, hasStreamingItem) {
        derivedStateOf {
            (messages.isNotEmpty() || hasStreamingItem) &&
                !atBottom &&
                !imeVisible &&
                autoScrollMode == AutoScrollMode.Idle
        }
    }
    val appCenterTint = Color.White
    val chromeSurface = Color.White
    val chromeBorder = Color(0xFFD8DADF).copy(alpha = 0.18f)
    val inputSurface = Color.White
    val inputBorder = Color(0xFFD4D8DE).copy(alpha = 0.22f)
    val userBubbleColor = Color(0xFFF4F4F7)
    val topInset = WindowInsets.safeDrawing
        .only(WindowInsetsSides.Top)
        .asPaddingValues()
        .calculateTopPadding()
    val jumpButtonBottomPadding = 78.dp

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun replaceMessages(newMessages: List<ChatMessage>) {
        val trimmed = trimMessageWindow(newMessages)
        messages.clear()
        messages.addAll(trimmed)
    }

    fun trimMessagesInPlace() {
        val startIndex = trimWindowStartIndex(messages)
        if (startIndex > 0) {
            repeat(startIndex) { messages.removeAt(0) }
        }
    }

    fun resetStreamingUiState(clearVisibleContent: Boolean) {
        mainHandler.post {
            fakeStreamJob?.cancel()
            fakeStreamJob = null
            streamRevealJob?.cancel()
            streamRevealJob = null
            isStreaming = false
            streamingMessageId = null
            streamingRevealBuffer = ""
            streamBottomSpacerPx = 0
            autoScrollMode = AutoScrollMode.Idle
            if (clearVisibleContent) {
                streamingMessageContent = ""
            }
        }
    }

    DisposableEffect(lifecycleOwner, focusManager, keyboardController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            } else if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                if (isStreaming && fakeStreamJob?.isActive != true && streamRevealJob?.isActive != true) {
                    resetStreamingUiState(clearVisibleContent = false)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (isStreaming) {
                resetStreamingUiState(clearVisibleContent = false)
            }
        }
    }

    LaunchedEffect(sessionId) {
        if (initialLocalMessages.isNotEmpty()) {
            snackbarScope.launch {
                prewarmAssistantMarkdown(initialLocalMessages)
            }
        }
        if (BuildConfig.USE_BACKEND_AB && SessionApi.hasBackendConfigured()) {
            SessionApi.getSnapshot(sessionId) { snapshot ->
                val remoteMessages = snapshot?.a_rounds_for_ui?.let(::snapshotRoundsToMessages).orEmpty()
                if (remoteMessages.isNotEmpty()) {
                    snackbarScope.launch {
                        prewarmAssistantMarkdown(remoteMessages)
                    }
                    mainHandler.post {
                        if (!isStreaming && remoteMessages.size > messages.size) {
                            replaceMessages(remoteMessages)
                            persistTick++
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(persistTick) {
        if (persistTick == 0) return@LaunchedEffect
        delay(if (isStreaming) 220 else 80)
        context.saveLocalChatWindow(sessionId, messages)
    }

    LaunchedEffect(scrollState.isScrollInProgress, programmaticScroll, atBottom) {
        if (programmaticScroll) return@LaunchedEffect
        userInteracting = scrollState.isScrollInProgress
        if (scrollState.isScrollInProgress && !atBottom) {
            autoScrollMode = AutoScrollMode.Idle
        }
    }

    LaunchedEffect(shouldOfferJumpButton, scrollState.isScrollInProgress, programmaticScroll) {
        if (!shouldOfferJumpButton) {
            jumpButtonVisible = false
            return@LaunchedEffect
        }
        if (programmaticScroll || scrollState.isScrollInProgress) {
            jumpButtonVisible = false
            return@LaunchedEffect
        }

        jumpButtonVisible = true
        delay(JUMP_BUTTON_AUTO_HIDE_MS)
        if (!scrollState.isScrollInProgress && !programmaticScroll && shouldOfferJumpButton) {
            jumpButtonVisible = false
        }
    }

    fun ensureStreamingRevealJob() {
        if (streamRevealJob?.isActive == true) return
        streamRevealJob = snackbarScope.launch {
            while (isActive) {
                val buffer = streamingRevealBuffer
                if (buffer.isEmpty()) {
                    if (!isStreaming) break
                    delay(STREAM_TYPEWRITER_IDLE_POLL_MS)
                    continue
                }
                val batch = buildStreamingRevealBatch(buffer)
                if (batch.text.isEmpty()) {
                    delay(batch.delayMs)
                    continue
                }
                streamingRevealBuffer = streamingRevealBuffer.drop(batch.text.length)
                if (streamingMessageId.isNullOrBlank()) {
                    streamingMessageId = "assistant_${UUID.randomUUID()}"
                }
                streamingMessageContent += batch.text
                if (streamBottomSpacerPx > 0 && streamingMessageContent.isNotBlank()) {
                    streamBottomSpacerPx = 0
                }
                streamTick++
                delay(batch.delayMs)
            }
        }
    }

    fun appendAssistantChunk(piece: String) {
        if (piece.isEmpty()) return
        mainHandler.post {
            if (streamingMessageId.isNullOrBlank()) {
                streamingMessageId = "assistant_${UUID.randomUUID()}"
            }
            streamingRevealBuffer += piece
            ensureStreamingRevealJob()
        }
    }

    fun finishStreaming() {
        mainHandler.post {
            streamRevealJob?.cancel()
            streamRevealJob = null
            if (streamingRevealBuffer.isNotEmpty()) {
                if (streamingMessageId.isNullOrBlank()) {
                    streamingMessageId = "assistant_${UUID.randomUUID()}"
                }
                streamingMessageContent += streamingRevealBuffer
                streamingRevealBuffer = ""
                streamTick++
            }
            val finalContent = streamingMessageContent
            val finalId = streamingMessageId
            if (finalContent.isNotBlank()) {
                messages.add(ChatMessage(finalId ?: "assistant_${UUID.randomUUID()}", ChatRole.ASSISTANT, finalContent))
                trimMessagesInPlace()
            }
            fakeStreamJob = null
            isStreaming = false
            streamingMessageId = null
            streamingMessageContent = ""
            streamingRevealBuffer = ""
            streamBottomSpacerPx = 0
            autoScrollMode = AutoScrollMode.Idle
            persistTick++
        }
    }

    fun sendMessage() {
        val text = input.value.trim()
        if (text.isEmpty()) return
        val userId = "user_${UUID.randomUUID()}"
        messages.add(ChatMessage(userId, ChatRole.USER, text))
        anchoredUserMessageId = userId
        anchoredUserBottomPx = -1
        input.value = ""
        focusManager.clearFocus(force = true)
        keyboardController?.hide()

        trimMessagesInPlace()
        persistTick++
        isStreaming = true
        streamingMessageId = "assistant_${UUID.randomUUID()}"
        streamingMessageContent = ""
        streamingRevealBuffer = ""
        streamBottomSpacerPx = maxOf(
            (messageViewportHeightPx * SEND_ANCHOR_EXTRA_BOTTOM_SPACE_RATIO).roundToInt(),
            minSendAnchorExtraBottomSpacePx
        )
        autoScrollMode = AutoScrollMode.AnchorUser
        userInteracting = false
        sendTick++

        fakeStreamJob?.cancel()
        streamRevealJob?.cancel()
        streamRevealJob = null
        val fullText = FAKE_STREAM_TEXT
        fakeStreamJob = snackbarScope.launch {
            val ballStartTime = SystemClock.uptimeMillis()
            val initialDelayMs = Random.nextLong(LOCAL_STREAM_FIRST_TOKEN_MIN_MS, LOCAL_STREAM_FIRST_TOKEN_MAX_MS)
            val minBallMs = LOCAL_STREAM_MIN_BALL_MS
            val elapsed = SystemClock.uptimeMillis() - ballStartTime
            val firstTokenWait = maxOf(initialDelayMs, minBallMs - elapsed)
            if (firstTokenWait > 0) {
                delay(firstTokenWait)
            }

            var remaining = fullText
            while (isActive && remaining.isNotEmpty()) {
                val step = nextLocalStreamFeedStep(remaining)
                appendAssistantChunk(step.text)
                remaining = remaining.drop(step.text.length)
                if (step.delayMs > 0) {
                    delay(step.delayMs)
                }
            }
            if (isActive) finishStreaming()
        }
    }

    suspend fun scrollToBottom(animated: Boolean) {
        if (messages.isEmpty() && !hasStreamingItem) return
        programmaticScroll = true
        try {
            withFrameNanos { }
            val target = scrollState.maxValue
            val delta = target - scrollState.value
            if (delta <= 0) return
            when {
                animated -> {
                    scrollState.animateScrollTo(target, animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing))
                }
                delta <= STREAM_ANIMATED_SCROLL_MAX_DELTA_PX -> {
                    scrollState.animateScrollTo(target, animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing))
                }
                else -> {
                    scrollState.scrollTo(target)
                }
            }
            withFrameNanos { }
        } finally {
            programmaticScroll = false
        }
    }

    suspend fun scrollAfterSendAnchor() {
        if (messages.isEmpty()) return
        programmaticScroll = true
        try {
            anchoredTargetBottomPx = (messageViewportHeightPx * SEND_ANCHOR_USER_BOTTOM_RATIO).roundToInt()
            repeat(16) {
                withFrameNanos { }
                delay(24)
                val currentBottom = anchoredUserBottomPx
                if (currentBottom <= 0) return@repeat
                val delta = currentBottom - anchoredTargetBottomPx
                if (kotlin.math.abs(delta) <= 4) return@repeat
                val target = (scrollState.value + delta).coerceIn(0, scrollState.maxValue)
                if (it < 15) scrollState.scrollTo(target)
                else scrollState.animateScrollTo(target, animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing))
            }
            autoScrollMode = if (isStreaming && anchoredUserMessageId != null) {
                AutoScrollMode.StreamAnchorFollow
            } else {
                AutoScrollMode.Idle
            }
        } finally {
            programmaticScroll = false
        }
    }

    LaunchedEffect(streamTick, autoScrollMode, isStreaming, userInteracting, anchoredUserBottomPx) {
        if (!hasStreamingItem || !isStreaming) return@LaunchedEffect
        if (autoScrollMode != AutoScrollMode.StreamAnchorFollow) return@LaunchedEffect
        if (userInteracting || programmaticScroll) return@LaunchedEffect
        if (anchoredUserBottomPx <= 0 || anchoredTargetBottomPx <= 0) return@LaunchedEffect
        val now = SystemClock.uptimeMillis()
        if (now - lastAutoScrollMs < STREAM_AUTO_SCROLL_THROTTLE_MS) return@LaunchedEffect
        val delta = anchoredUserBottomPx - anchoredTargetBottomPx
        if (delta <= STREAM_ANCHOR_COMPENSATE_THRESHOLD_PX) return@LaunchedEffect
        lastAutoScrollMs = now
        programmaticScroll = true
        try {
            if (delta <= STREAM_FOLLOW_ANIMATE_THRESHOLD_PX) {
                scrollState.animateScrollBy(
                    delta.toFloat(),
                    animationSpec = tween(durationMillis = 68, easing = FastOutSlowInEasing)
                )
            } else {
                scrollState.scrollBy(delta.toFloat())
            }
        } finally {
            programmaticScroll = false
        }
    }

    LaunchedEffect(sendTick) {
        if (messages.isEmpty()) return@LaunchedEffect
        autoScrollMode = AutoScrollMode.AnchorUser
        userInteracting = false
        scrollAfterSendAnchor()
        lastAutoScrollMs = 0L
    }

    fun jumpToBottom() {
        snackbarScope.launch {
            if (messages.isEmpty() && !hasStreamingItem) return@launch
            autoScrollMode = AutoScrollMode.Idle
            userInteracting = false
            jumpButtonVisible = false
            scrollToBottom(animated = true)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(appCenterTint)
    ) {
        val chromeMaxWidth: Dp = when {
            maxWidth >= 900.dp -> 820.dp
            maxWidth >= 700.dp -> 680.dp
            else -> maxWidth
        }
        val chromeHorizontalPadding = when {
            maxWidth < 360.dp -> 14.dp
            maxWidth < 600.dp -> 18.dp
            else -> 24.dp
        }
        val listHorizontalPadding = when {
            maxWidth < 360.dp -> 20.dp
            maxWidth < 600.dp -> 26.dp
            else -> 32.dp
        }
        val inputBarHeight = if (maxWidth < 360.dp) 52.dp else 56.dp
        val chromeButtonSize = if (maxWidth < 360.dp) 40.dp else 42.dp
        val actionCircleSize = if (maxWidth < 360.dp) 42.dp else 44.dp
        val addButtonSize = actionCircleSize
        val addIconSize = if (maxWidth < 360.dp) 27.dp else 29.dp
        val sendButtonSize = actionCircleSize
        val userBubbleMaxWidth = if (chromeMaxWidth < 440.dp) chromeMaxWidth * 0.78f else 420.dp
        val topBarReservedHeight = topInset + chromeButtonSize + 6.dp
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
                            .height(116.dp)
                            .background(Color.White)
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
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FrostedCircleButton(
                            size = addButtonSize,
                            surfaceColor = chromeSurface,
                            borderColor = chromeBorder,
                            onClick = {}
                        ) {
                            PlusCrossIcon(
                                tint = Color(0xFF6F7277),
                                modifier = Modifier.size(addIconSize)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(30.dp),
                            color = inputSurface,
                            border = BorderStroke(0.68.dp, inputBorder),
                            tonalElevation = 0.dp,
                            shadowElevation = 1.1.dp,
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
                                        .padding(start = 2.dp, end = 58.dp),
                                    placeholder = { Text("描述作物/地区/问题", color = Color(0xFFAEAFB4)) },
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
                                        .padding(end = 8.dp)
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
                                            modifier = Modifier.size(22.dp)
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
                    .onSizeChanged { messageViewportHeightPx = it.height }
                    .onGloballyPositioned { coordinates ->
                        messageViewportTopPx = coordinates.boundsInWindow().top
                    }
                    .padding(innerPadding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(top = topBarReservedHeight, bottom = 18.dp)
                ) {
                    messages.forEach { msg ->
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
                                    AssistantMessageContent(
                                        content = msg.content,
                                        isStreaming = false,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(end = 4.dp)
                                    )
                                } else {
                                    Text(
                                        text = msg.content,
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .widthIn(max = userBubbleMaxWidth)
                                            .then(
                                                if (msg.id == anchoredUserMessageId) {
                                                    Modifier.onGloballyPositioned { coordinates ->
                                                        anchoredUserBottomPx =
                                                            (coordinates.boundsInWindow().bottom - messageViewportTopPx).roundToInt()
                                                    }
                                                } else {
                                                    Modifier
                                                }
                                            )
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
                    if (hasStreamingItem) {
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
                                AssistantMessageContent(
                                    content = streamingMessageContent,
                                    isStreaming = isStreaming,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(end = 4.dp)
                                )
                            }
                        }
                    }
                    if (streamBottomSpacerPx > 0 && isStreaming && streamingMessageContent.isBlank()) {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(streamBottomSpacerDp)
                        )
                    }
                }

            if (messages.isEmpty() && !hasStreamingItem) {
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

            if (jumpButtonVisible) {
                Surface(
                    onClick = { jumpToBottom() },
                    shape = CircleShape,
                    color = chromeSurface,
                    border = BorderStroke(0.48.dp, chromeBorder),
                    shadowElevation = 0.7.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = jumpButtonBottomPadding)
                        .size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        LongArrowIcon(
                            tint = Color(0xFF111111),
                            directionUp = false,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(topInset + chromeButtonSize + 2.dp)
                    .background(Color.White)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(top = topInset + 4.dp, bottom = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(max = chromeMaxWidth)
                        .fillMaxWidth()
                        .padding(horizontal = chromeHorizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {},
                        modifier = Modifier.size(chromeButtonSize)
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
                        Text(
                            text = "农技千问",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            color = Color(0xFF111111),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                    IconButton(
                        onClick = {},
                        modifier = Modifier.size(chromeButtonSize)
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
