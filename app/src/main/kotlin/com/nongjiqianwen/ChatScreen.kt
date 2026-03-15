package com.nongjiqianwen

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.isImeVisible
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import java.util.LinkedHashMap
import kotlin.random.Random
import kotlin.math.roundToInt
import java.util.UUID

private enum class ChatRole { USER, ASSISTANT }
private enum class AutoScrollMode { Idle, AnchorUser, StreamAnchorFollow }
@Immutable
private data class ChatMessage(val id: String, val role: ChatRole, val content: String)
@Immutable
private data class LocalStreamingDraft(
    val messageId: String,
    val content: String,
    val revealBuffer: String,
    val anchoredUserMessageId: String?,
    val savedAtMs: Long
)
@Immutable
private data class LocalBottomViewport(
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int
)
private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Bullet(val text: String) : MarkdownBlock
    data class Numbered(val number: String, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
}

private const val LOCAL_RENDER_ROUND_LIMIT = 30
private const val CHAT_CACHE_PREFS = "chat_ui_cache"
private const val CHAT_CACHE_KEY_PREFIX = "render_window_"
private const val CHAT_STREAM_DRAFT_KEY_PREFIX = "stream_draft_"
private const val CHAT_BOTTOM_VIEWPORT_KEY_PREFIX = "bottom_viewport_"
private const val INLINE_MARKDOWN_CACHE_LIMIT = 120
private const val BLOCK_MARKDOWN_CACHE_LIMIT = 80
private const val JUMP_BUTTON_AUTO_HIDE_MS = 1200L
private const val BOTTOM_VIEWPORT_SAVE_DEBOUNCE_MS = 180L
private const val STREAM_TYPEWRITER_IDLE_POLL_MS = 8L
private const val STREAM_REVEAL_FRAME_BUDGET_MS = 56L
private const val STREAM_REVEAL_MAX_TOKENS_PER_BATCH = 7
private const val LOCAL_STREAM_FIRST_TOKEN_MIN_MS = 520L
private const val LOCAL_STREAM_FIRST_TOKEN_MAX_MS = 860L
private const val LOCAL_STREAM_MIN_BALL_MS = 2200L
private const val STREAM_STICKY_SCROLL_STEP_PX = 96
private const val STREAM_ANCHOR_FOLLOW_STEP_PX = 22
private const val STREAM_BOTTOM_FOLLOW_STEP_PX = 16
private const val SEND_ANCHOR_EXTRA_BOTTOM_SPACE_RATIO = 0.72f
private const val STREAM_ANCHOR_COMPENSATE_THRESHOLD_PX = 12
private const val STREAM_FOLLOW_ANIMATE_THRESHOLD_PX = 120
private const val STREAM_AUTO_FOLLOW_DEBOUNCE_MS = 16L
private const val PROGRAMMATIC_SCROLL_SETTLE_MS = 180L
private const val INPUT_MAX_CHARS = 6000
private const val INPUT_LIMIT_HINT_MS = 1600L
private const val GPT_BALL_PULSE_MS = 720
private const val GPT_BALL_EXIT_MS = 180
private const val GPT_STREAM_TEXT_ENTRY_MS = 220
private val STREAMING_MESSAGE_MIN_HEIGHT = 76.dp
private val STREAM_AUTO_FOLLOW_SLOP = 28.dp
private val MIN_SEND_ANCHOR_EXTRA_BOTTOM_SPACE = 160.dp
private val ASSISTANT_START_ANCHOR_TOP = 236.dp
private val STREAM_VISIBLE_BOTTOM_GAP = 18.dp
private val BOTTOM_OVERLAY_CONTENT_CLEARANCE = 52.dp
private val INITIAL_BOTTOM_SNAP_THRESHOLD = 22.dp
private val GPT_BALL_SIZE = 14.dp
private val GPT_BALL_CONTAINER_SIZE = 24.dp
private val GPT_BALL_START_PADDING = 0.dp
private val MARKDOWN_BLOCK_SPACING = 12.dp
private val SECTION_DIVIDER_GAP = 28.dp
private val SECTION_DIVIDER_TOP_EXTRA_GAP = 16.dp
private const val AI_DISCLAIMER_TEXT = "本回答由AI生成，内容仅供参考。"
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

private fun takeMarkdownPrefixToken(buffer: String): String? {
    if (buffer.isEmpty()) return null
    return when {
        buffer.startsWith("> ") -> "> "
        buffer.startsWith("- ") -> "- "
        buffer.startsWith("* ") -> "* "
        buffer.first() == '#' -> {
            var end = 1
            while (end < buffer.length && end < 6 && buffer[end] == '#') end++
            while (end < buffer.length && buffer[end].isWhitespace()) end++
            buffer.substring(0, end)
        }
        buffer.first().isDigit() -> {
            var end = 1
            while (end < buffer.length && end < 4 && buffer[end].isDigit()) end++
            if (end < buffer.length && buffer[end] == '.') {
                end++
                while (end < buffer.length && buffer[end].isWhitespace()) end++
                buffer.substring(0, end)
            } else {
                null
            }
        }
        else -> null
    }
}

private fun takeTypewriterToken(buffer: String): String {
    if (buffer.isEmpty()) return ""
    val first = buffer.first()
    val markdownPrefix = takeMarkdownPrefixToken(buffer)
    if (markdownPrefix != null) {
        return markdownPrefix
    }
    if (first == '\n') {
        return first.toString()
    }
    if (first.isWhitespace()) {
        var end = 1
        while (end < buffer.length && end < 3 && buffer[end].isWhitespace() && buffer[end] != '\n') end++
        return buffer.substring(0, end)
    }
    if (first.isStructuralMarkdownChar()) {
        return first.toString()
    }
    if (first.isCjkUnifiedIdeograph()) {
        var end = 1
        while (end < buffer.length && end < 5 && buffer[end].isCjkUnifiedIdeograph()) end++
        return buffer.substring(0, end)
    }
    if (first.isDigit()) {
        var end = 1
        while (end < buffer.length && end < 4 && buffer[end].isDigit()) end++
        if (end < buffer.length && buffer[end] == '.') end++
        return buffer.substring(0, end)
    }
    var end = 1
    while (end < buffer.length && end < 8) {
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
    val markdownPrefix = takeMarkdownPrefixToken(remaining)
    val takeCount = when {
        markdownPrefix != null -> markdownPrefix.length
        first == '\n' -> 1
        first.isStructuralMarkdownChar() -> 1
        first.isCjkUnifiedIdeograph() -> Random.nextInt(2, 5)
        first.isWhitespace() -> remaining.takeWhile { it.isWhitespace() && it != '\n' }.length.coerceIn(1, 2)
        else -> Random.nextInt(4, 8)
    }.coerceAtMost(remaining.length)
    val text = remaining.substring(0, takeCount)
    val tail = text.last()
    val delayMs = when {
        tail == '\n' -> Random.nextLong(110, 180)
        tail.isStrongPausePunctuation() -> Random.nextLong(72, 118)
        tail.isWeakPausePunctuation() -> Random.nextLong(34, 58)
        markdownPrefix != null || text.any { it.isStructuralMarkdownChar() } -> Random.nextLong(36, 60)
        else -> Random.nextLong(18, 30)
    }
    return LocalStreamFeedStep(text = text, delayMs = delayMs)
}

private fun nextLocalStreamFeedBatch(remaining: String): LocalStreamFeedStep {
    if (remaining.isEmpty()) return LocalStreamFeedStep("", 0L)
    val text = StringBuilder()
    var totalDelayMs = 0L
    var cursor = remaining
    var stepCount = 0

    while (cursor.isNotEmpty() && stepCount < 4) {
        val step = nextLocalStreamFeedStep(cursor)
        if (step.text.isEmpty()) break
        text.append(step.text)
        cursor = cursor.drop(step.text.length)
        totalDelayMs += step.delayMs
        stepCount++
        val tail = step.text.lastOrNull()
        val hitBoundary = tail == '\n' || tail?.isStrongPausePunctuation() == true
        if (hitBoundary || totalDelayMs >= 72L) {
            break
        }
    }

    return LocalStreamFeedStep(
        text = text.toString(),
        delayMs = totalDelayMs.coerceAtLeast(42L)
    )
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
        lastChar == '\n' -> if (hasStructuralMarkdownPrefix(remainingBuffer)) 64L else 48L
        lastChar.isStrongPausePunctuation() -> 28L
        lastChar.isWeakPausePunctuation() -> 16L
        token.length >= 7 -> 8L
        token.length >= 5 -> 10L
        token.length >= 3 -> 12L
        token.length == 2 -> 13L
        else -> if (lastChar.isCjkUnifiedIdeograph()) 14L else 12L
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

private fun needsParagraphJoinSpace(previous: Char, next: Char): Boolean {
    if (previous.isWhitespace() || next.isWhitespace()) return false
    if (previous.isCjkUnifiedIdeograph() || next.isCjkUnifiedIdeograph()) return false
    if (previous.isWeakPausePunctuation() || previous.isStrongPausePunctuation()) return true
    return previous.isLetterOrDigit() && next.isLetterOrDigit()
}

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

private fun StringBuilder.appendParagraphLine(line: String) {
    if (line.isBlank()) return
    if (isNotEmpty()) {
        val previous = this[lastIndex]
        val next = line.first()
        if (needsParagraphJoinSpace(previous, next)) {
            append(' ')
        }
    }
    append(line)
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
                paragraph.appendParagraphLine(trimmed)
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
    lineHeight = 28.sp,
    letterSpacing = 0.05.sp,
    color = Color(0xFF171717),
    textMotion = TextMotion.Static,
    lineBreak = LineBreak.Paragraph
)

private fun assistantDisclaimerTextStyle(): TextStyle = TextStyle(
    fontSize = 14.sp,
    lineHeight = 20.sp,
    color = Color(0xFF94979D),
    letterSpacing = 0.1.sp,
    fontStyle = FontStyle.Italic,
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Normal,
    textMotion = TextMotion.Static
)

private fun shouldShowAiDisclaimer(content: String): Boolean {
    if (content.isBlank()) return false
    val normalized = content.lowercase()
    val guidanceKeywords = listOf(
        "用药", "施药", "农药", "杀菌剂", "杀虫剂", "杀螨", "施肥", "肥料", "追肥", "叶面肥",
        "冲施", "滴灌", "灌根", "喷施", "喷雾", "喷药", "剂量", "用量", "倍数", "浓度", "稀释",
        "稀释倍数", "配比", "配方", "混配", "复配", "安全期", "间隔期", "残留", "采收期", "停药期",
        "诊断", "病害", "虫害", "真菌", "细菌", "病毒", "药害", "肥害", "缺素",
        "防治", "防控", "处理方案", "治疗", "处方"
    )
    if (guidanceKeywords.any { normalized.contains(it) }) return true
    val dosageKeywords = listOf(
        "每亩", "亩用", "兑水", "稀释", "二次稀释", "倍液", "ppm"
    )
    if (dosageKeywords.any { normalized.contains(it) }) return true
    val dosageRegexes = listOf(
        Regex("\\d+(\\.\\d+)?\\s*(克|g|公斤|kg)\\s*/\\s*(亩|升|l)"),
        Regex("\\d+(\\.\\d+)?\\s*(毫升|ml|升|l)\\s*/\\s*(亩|升|l)"),
        Regex("\\d+(\\.\\d+)?\\s*(克|g|毫升|ml|升|l|公斤|kg)\\s*(每亩|/亩)"),
        Regex("\\d+(\\.\\d+)?\\s*(克|g|毫升|ml|升|l)\\s*兑\\s*\\d+(\\.\\d+)?\\s*(升|l)?\\s*水"),
        Regex("\\d+(\\.\\d+)?\\s*ppm"),
        Regex("\\d+(\\.\\d+)?\\s*倍液")
    )
    return dosageRegexes.any { it.containsMatchIn(normalized) }
}

private fun containsDisclaimerSensitiveAssistant(messages: List<ChatMessage>): Boolean {
    return messages.any { it.role == ChatRole.ASSISTANT && shouldShowAiDisclaimer(it.content) }
}

private fun assistantHeadingTextStyle(level: Int): TextStyle = TextStyle(
    fontSize = if (level <= 2) 20.sp else 18.sp,
    lineHeight = if (level <= 2) 31.sp else 28.sp,
    fontWeight = FontWeight.Bold,
    color = Color(0xFF111111),
    textMotion = TextMotion.Static,
    lineBreak = LineBreak.Heading
)

private fun consumeStreamingBottomSpacer(currentSpacerPx: Int, consumedScrollPx: Float): Int {
    if (currentSpacerPx <= 0 || consumedScrollPx <= 0f) return currentSpacerPx
    return (currentSpacerPx - consumedScrollPx.toInt()).coerceAtLeast(0)
}

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

private fun dedupeAdjacentMessages(source: List<ChatMessage>): List<ChatMessage> {
    if (source.size < 2) return source
    val deduped = ArrayList<ChatMessage>(source.size)
    source.forEach { message ->
        val previous = deduped.lastOrNull()
        val sameContent = when (message.role) {
            ChatRole.USER -> previous?.content == message.content
            ChatRole.ASSISTANT -> previous?.content?.let(::normalizeAssistantText) == normalizeAssistantText(message.content)
        }
        if (previous?.role == message.role && sameContent) return@forEach
        deduped.add(message)
    }
    return deduped
}

private fun sanitizeMessageWindow(source: List<ChatMessage>): List<ChatMessage> {
    return dedupeAdjacentMessages(trimMessageWindow(source))
}

private fun appendCompletedAssistantMessage(
    source: List<ChatMessage>,
    messageId: String,
    content: String
): List<ChatMessage> {
    val normalizedContent = normalizeAssistantText(content)
    if (normalizedContent.isBlank()) return sanitizeMessageWindow(source)
    val finalId = messageId.ifBlank { "assistant_${UUID.randomUUID()}" }
    val merged = ArrayList<ChatMessage>(source.size + 1)
    var inserted = false
    source.forEach { message ->
        if (message.id == finalId) {
            if (!inserted) {
                merged.add(ChatMessage(finalId, ChatRole.ASSISTANT, normalizedContent))
                inserted = true
            }
            return@forEach
        }
        merged.add(message)
    }
    if (!inserted) {
        merged.add(ChatMessage(finalId, ChatRole.ASSISTANT, normalizedContent))
    }
    return sanitizeMessageWindow(merged)
}

private suspend fun Context.loadLocalChatWindow(chatScopeId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
    val raw = getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE)
        .getString("$CHAT_CACHE_KEY_PREFIX$chatScopeId", null)
        .orEmpty()
    if (raw.isBlank()) return@withContext emptyList()
    runCatching {
        @Suppress("UNCHECKED_CAST")
        (chatCacheGson.fromJson<List<ChatMessage>>(raw, chatCacheListType) ?: emptyList())
    }.getOrDefault(emptyList())
}

private fun Context.loadLocalChatWindowSync(chatScopeId: String): List<ChatMessage> {
    val raw = getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE)
        .getString("$CHAT_CACHE_KEY_PREFIX$chatScopeId", null)
        .orEmpty()
    if (raw.isBlank()) return emptyList()
    return runCatching {
        @Suppress("UNCHECKED_CAST")
        (chatCacheGson.fromJson<List<ChatMessage>>(raw, chatCacheListType) ?: emptyList())
    }.getOrDefault(emptyList())
}

private suspend fun Context.saveLocalChatWindow(chatScopeId: String, messages: List<ChatMessage>) = withContext(Dispatchers.IO) {
    val trimmed = sanitizeMessageWindow(messages)
    getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString("$CHAT_CACHE_KEY_PREFIX$chatScopeId", chatCacheGson.toJson(trimmed))
        .commit()
}

private fun Context.saveLocalChatWindowSync(chatScopeId: String, messages: List<ChatMessage>) {
    val trimmed = sanitizeMessageWindow(messages)
    getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString("$CHAT_CACHE_KEY_PREFIX$chatScopeId", chatCacheGson.toJson(trimmed))
        .commit()
}

private fun Context.loadLocalStreamingDraftSync(chatScopeId: String): LocalStreamingDraft? {
    val raw = getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE)
        .getString("$CHAT_STREAM_DRAFT_KEY_PREFIX$chatScopeId", null)
        .orEmpty()
    if (raw.isBlank()) return null
    return runCatching {
        chatCacheGson.fromJson(raw, LocalStreamingDraft::class.java)
    }.getOrNull()
}

private suspend fun Context.saveLocalStreamingDraft(chatScopeId: String, draft: LocalStreamingDraft) = withContext(Dispatchers.IO) {
    getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString("$CHAT_STREAM_DRAFT_KEY_PREFIX$chatScopeId", chatCacheGson.toJson(draft))
        .commit()
}

private fun Context.saveLocalStreamingDraftSync(chatScopeId: String, draft: LocalStreamingDraft) {
    getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString("$CHAT_STREAM_DRAFT_KEY_PREFIX$chatScopeId", chatCacheGson.toJson(draft))
        .commit()
}

private fun Context.clearLocalStreamingDraftSync(chatScopeId: String) {
    getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .remove("$CHAT_STREAM_DRAFT_KEY_PREFIX$chatScopeId")
        .commit()
}

private suspend fun Context.clearLocalStreamingDraft(chatScopeId: String) = withContext(Dispatchers.IO) {
    getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .remove("$CHAT_STREAM_DRAFT_KEY_PREFIX$chatScopeId")
        .commit()
}

private fun Context.loadLocalBottomViewportSync(chatScopeId: String): LocalBottomViewport? {
    val raw = getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE)
        .getString("$CHAT_BOTTOM_VIEWPORT_KEY_PREFIX$chatScopeId", null)
        .orEmpty()
    if (raw.isBlank()) return null
    return runCatching {
        chatCacheGson.fromJson(raw, LocalBottomViewport::class.java)
    }.getOrNull()
}

private suspend fun Context.saveLocalBottomViewport(chatScopeId: String, viewport: LocalBottomViewport) = withContext(Dispatchers.IO) {
    getSharedPreferences(CHAT_CACHE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString("$CHAT_BOTTOM_VIEWPORT_KEY_PREFIX$chatScopeId", chatCacheGson.toJson(viewport))
        .apply()
}

private fun recoverStreamingDraftAsCompletedMessage(
    localMessages: List<ChatMessage>,
    draft: LocalStreamingDraft?
): List<ChatMessage> {
    if (draft == null) return sanitizeMessageWindow(localMessages)
    return appendCompletedAssistantMessage(
        source = localMessages,
        messageId = draft.messageId,
        content = FAKE_STREAM_TEXT
    )
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

private fun shouldReplaceHydratedMessages(
    currentMessages: List<ChatMessage>,
    remoteMessages: List<ChatMessage>
): Boolean {
    if (remoteMessages.isEmpty()) return false
    val trimmedCurrent = sanitizeMessageWindow(currentMessages)
    val trimmedRemote = sanitizeMessageWindow(remoteMessages)
    if (trimmedCurrent.size != trimmedRemote.size) return true
    return trimmedRemote.indices.any { index ->
        val current = trimmedCurrent[index]
        val remote = trimmedRemote[index]
        current.role != remote.role || current.content != remote.content
    }
}

private sealed interface MarkdownUiBlock {
    data class Heading(val level: Int, val text: AnnotatedString) : MarkdownUiBlock
    data class Bullet(val text: AnnotatedString) : MarkdownUiBlock
    data class Numbered(val number: String, val text: AnnotatedString) : MarkdownUiBlock
    data class Paragraph(val text: AnnotatedString) : MarkdownUiBlock
}

private fun shouldShowMarkdownSectionDivider(
    previous: MarkdownUiBlock?,
    current: MarkdownUiBlock
): Boolean {
    if (previous == null) return false
    val heading = current as? MarkdownUiBlock.Heading ?: return false
    return heading.level <= 2
}

@Composable
private fun MarkdownSectionDivider() {
    HorizontalDivider(
        modifier = Modifier
            .fillMaxWidth(),
        thickness = 1.dp,
        color = Color(0xFFE7E9ED)
    )
}

@Composable
private fun AssistantMessageContent(
    content: String,
    isStreaming: Boolean,
    animateLayoutChanges: Boolean = true,
    streamingFreshStart: Int = -1,
    streamingFreshEnd: Int = -1,
    streamingFreshTick: Int = 0,
    modifier: Modifier = Modifier
) {
    val showDisclaimer = remember(content) { shouldShowAiDisclaimer(content) }
    val stableModifier = if (isStreaming) {
        modifier.heightIn(min = STREAMING_MESSAGE_MIN_HEIGHT)
    } else {
        modifier
    }
    val sizeAnimatedModifier = if (animateLayoutChanges) {
        Modifier.animateContentSize(
            animationSpec = tween(
                durationMillis = 120,
                easing = LinearOutSlowInEasing
            )
        )
    } else {
        Modifier
    }
    if (isStreaming) {
        Box(
            modifier = stableModifier
                .fillMaxWidth()
                .then(sizeAnimatedModifier),
            contentAlignment = Alignment.TopStart
        ) {
            AnimatedVisibility(
                visible = content.isBlank(),
                enter = fadeIn(animationSpec = tween(durationMillis = 90)),
                exit = fadeOut(animationSpec = tween(durationMillis = GPT_BALL_EXIT_MS))
            ) {
                AssistantStreamingWaitingIndicator(modifier = Modifier.fillMaxWidth())
            }
            AnimatedVisibility(
                visible = content.isNotBlank(),
                enter = fadeIn(
                    animationSpec = tween(
                        durationMillis = GPT_STREAM_TEXT_ENTRY_MS,
                        delayMillis = 36
                    )
                ),
                exit = fadeOut(animationSpec = tween(durationMillis = 80))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(sizeAnimatedModifier),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistantStreamingContent(
                        content = content,
                        streamingFreshStart = streamingFreshStart,
                        streamingFreshEnd = streamingFreshEnd,
                        streamingFreshTick = streamingFreshTick,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (showDisclaimer) {
                        Text(
                            text = AI_DISCLAIMER_TEXT,
                            modifier = Modifier.fillMaxWidth(),
                            style = assistantDisclaimerTextStyle().copy(color = Color.Transparent),
                            textAlign = TextAlign.Start
                        )
                    }
                }
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .then(sizeAnimatedModifier),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistantMarkdownContent(content = content)
            if (showDisclaimer) {
                Text(
                    text = AI_DISCLAIMER_TEXT,
                    modifier = Modifier.fillMaxWidth(),
                    style = assistantDisclaimerTextStyle(),
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

@Composable
private fun AssistantStreamingWaitingIndicator(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val lineHeight = with(density) {
        assistantParagraphTextStyle().lineHeight.toDp()
    }
    Box(
        modifier = modifier
            .height(lineHeight)
            .padding(start = GPT_BALL_START_PADDING),
        contentAlignment = Alignment.CenterStart
    ) {
        GPTBreathingBall()
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
private fun AssistantStreamingContent(
    content: String,
    streamingFreshStart: Int,
    streamingFreshEnd: Int,
    streamingFreshTick: Int,
    modifier: Modifier = Modifier
) {
    val parts = remember(content) { splitStreamingMarkdownParts(content) }
    val completedBlocks = remember(parts.completedContent) {
        if (parts.completedContent.isBlank()) emptyList() else getCachedMarkdownUiBlocks(parts.completedContent)
    }
    val tailTrimmed = parts.tailContent.trimStart()
    val showLeadingSectionDivider = remember(completedBlocks, tailTrimmed) {
        val lastCompleted = completedBlocks.lastOrNull() ?: return@remember false
        if (!tailTrimmed.matches(headingRegex)) return@remember false
        val markerLength = tailTrimmed.takeWhile { it == '#' }.length
        markerLength <= 2 && shouldShowMarkdownSectionDivider(
            previous = lastCompleted,
            current = MarkdownUiBlock.Heading(markerLength, AnnotatedString(""))
        )
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MARKDOWN_BLOCK_SPACING)
    ) {
        if (parts.completedContent.isNotBlank()) {
            AssistantMarkdownContent(
                content = parts.completedContent,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (parts.tailContent.isNotBlank()) {
            AssistantStreamingTail(
                content = parts.tailContent,
                showLeadingSectionDivider = showLeadingSectionDivider
            )
        }
    }
}

@Composable
private fun AssistantStreamingTail(
    content: String,
    showLeadingSectionDivider: Boolean = false
) {
    val trimmed = content.trimStart()
    fun buildTail(text: String): AnnotatedString = buildStreamingAnnotatedString(text)
    val animatedModifier = Modifier.fillMaxWidth()

    when {
        trimmed.matches(headingRegex) -> {
            val marker = trimmed.takeWhile { it == '#' }
            Column(
                modifier = animatedModifier,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                if (showLeadingSectionDivider && marker.length <= 2) {
                    Spacer(modifier = Modifier.height(SECTION_DIVIDER_TOP_EXTRA_GAP))
                    MarkdownSectionDivider()
                    Spacer(modifier = Modifier.height(SECTION_DIVIDER_GAP))
                }
                Text(
                    text = remember(trimmed) { buildTail(trimmed.drop(marker.length).trimStart()) },
                    modifier = Modifier.fillMaxWidth(),
                    style = assistantHeadingTextStyle(marker.length),
                    textAlign = TextAlign.Start
                )
            }
        }
        trimmed.matches(bulletRegex) -> {
            Row(
                modifier = animatedModifier,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
            Row(
                modifier = animatedModifier,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                modifier = animatedModifier,
                style = assistantParagraphTextStyle(),
                textAlign = TextAlign.Start
            )
        }
        else -> {
            Text(
                text = remember(content) { buildTail(content) },
                modifier = animatedModifier,
                style = assistantParagraphTextStyle(),
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
private fun AssistantMarkdownContent(content: String, modifier: Modifier = Modifier) {
    val blocks = remember(content) { getCachedMarkdownUiBlocks(content) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MARKDOWN_BLOCK_SPACING)) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MarkdownUiBlock.Heading -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    if (shouldShowMarkdownSectionDivider(blocks.getOrNull(index - 1), block)) {
                        Spacer(modifier = Modifier.height(SECTION_DIVIDER_TOP_EXTRA_GAP))
                        MarkdownSectionDivider()
                        Spacer(modifier = Modifier.height(SECTION_DIVIDER_GAP))
                    }
                    Text(
                        text = block.text,
                        style = assistantHeadingTextStyle(block.level)
                    )
                }
                is MarkdownUiBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                is MarkdownUiBlock.Numbered -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        initialValue = 0.48f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = GPT_BALL_PULSE_MS,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "assistantBreathingDotAlpha"
    )
    val scale by transition.animateFloat(
        initialValue = 0.68f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = GPT_BALL_PULSE_MS,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "assistantBreathingDotScale"
    )
    Box(
        modifier = modifier.size(GPT_BALL_CONTAINER_SIZE),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(GPT_BALL_CONTAINER_SIZE)
                .graphicsLayer { this.alpha = alpha }
        ) {
            val baseRadius = GPT_BALL_SIZE.toPx() / 2f
            val radius = baseRadius * scale
            val center = this.center

            drawCircle(
                color = Color.Black.copy(alpha = 0.10f),
                radius = radius * 1.04f,
                center = Offset(
                    x = center.x,
                    y = center.y + radius * 0.04f
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF121212),
                        Color(0xFF080808),
                        Color(0xFF020202)
                    ),
                    center = center,
                    radius = radius * 1.10f
                ),
                radius = radius,
                center = center
            )
        }
    }
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
        border = BorderStroke(0.6.dp, borderColor.copy(alpha = 0.82f)),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 1.6.dp,
                shape = CircleShape,
                ambientColor = Color(0x16000000),
                spotColor = Color(0x16000000)
            )
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
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
fun ChatScreen() {
    val input = rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val chatScopeId = remember { IdManager.getUserId() }
    val hasRemoteHistorySource = BuildConfig.USE_BACKEND_AB && SessionApi.hasBackendConfigured()
    val initialStreamingDraft = remember(chatScopeId, hasRemoteHistorySource) {
        if (hasRemoteHistorySource) {
            null
        } else {
            context.loadLocalStreamingDraftSync(chatScopeId)
        }
    }
    val initialLocalMessages = remember(chatScopeId, initialStreamingDraft) {
        recoverStreamingDraftAsCompletedMessage(
            localMessages = context.loadLocalChatWindowSync(chatScopeId),
            draft = initialStreamingDraft
        )
    }
    val initialBottomViewport = remember(chatScopeId) {
        context.loadLocalBottomViewportSync(chatScopeId)
    }
    val shouldHydrateRemoteHistory = remember(chatScopeId, hasRemoteHistorySource) {
        hasRemoteHistorySource
    }
    val messages = remember(chatScopeId) {
        mutableStateListOf<ChatMessage>().apply { addAll(initialLocalMessages) }
    }
    val initialHasDisclaimerSensitiveAssistant = remember(chatScopeId, initialLocalMessages) {
        containsDisclaimerSensitiveAssistant(initialLocalMessages)
    }
    val initialListIndex = remember(chatScopeId, initialLocalMessages.size, initialBottomViewport) {
        initialBottomViewport?.firstVisibleItemIndex
            ?.coerceIn(0, initialLocalMessages.lastIndex.coerceAtLeast(0))
            ?: (initialLocalMessages.lastIndex).coerceAtLeast(0)
    }
    val initialListScrollOffset = remember(chatScopeId, initialBottomViewport) {
        initialBottomViewport?.firstVisibleItemScrollOffset?.coerceAtLeast(0) ?: 0
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialListIndex,
        initialFirstVisibleItemScrollOffset = initialListScrollOffset
    )
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    var fakeStreamJob by remember { mutableStateOf<Job?>(null) }
    var streamRevealJob by remember { mutableStateOf<Job?>(null) }

    var isStreaming by rememberSaveable(chatScopeId) { mutableStateOf(false) }
    var streamingMessageId by rememberSaveable(chatScopeId) { mutableStateOf<String?>(null) }
    var streamingMessageContent by rememberSaveable(chatScopeId) { mutableStateOf("") }
    var streamingRevealBuffer by rememberSaveable(chatScopeId) { mutableStateOf("") }
    var autoScrollMode by remember { mutableStateOf(AutoScrollMode.Idle) }
    var userInteracting by remember { mutableStateOf(false) }
    var streamTick by remember { mutableIntStateOf(0) }
    var streamingFreshStart by remember { mutableIntStateOf(-1) }
    var streamingFreshEnd by remember { mutableIntStateOf(-1) }
    var streamingFreshTick by remember { mutableIntStateOf(0) }
    var sendTick by remember { mutableIntStateOf(0) }
    var programmaticScroll by remember { mutableStateOf(false) }
    var lastProgrammaticScrollMs by remember { mutableStateOf(0L) }
    var persistTick by remember { mutableIntStateOf(0) }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    var messageViewportHeightPx by remember { mutableIntStateOf(0) }
    var streamBottomSpacerPx by rememberSaveable(chatScopeId) { mutableStateOf(0) }
    var messageViewportTopPx by remember { mutableStateOf(0f) }
    var anchoredUserMessageId by rememberSaveable(chatScopeId) { mutableStateOf<String?>(null) }
    var streamingAnchorTopPx by remember { mutableIntStateOf(-1) }
    var streamingContentBottomPx by remember { mutableIntStateOf(-1) }
    var streamBottomFollowActive by remember { mutableStateOf(false) }
    var initialBottomSnapDone by remember(chatScopeId) { mutableStateOf(false) }
    var initialListRevealConsumed by remember(chatScopeId) { mutableStateOf(false) }
    var hasStartedConversation by rememberSaveable(chatScopeId) { mutableStateOf(false) }
    var jumpButtonVisible by remember { mutableStateOf(false) }
    var userDetachedFromBottom by remember { mutableStateOf(false) }
    var pendingResumeAutoFollow by remember { mutableStateOf(false) }
    var pendingFinalBottomSnap by remember { mutableStateOf(false) }
    var streamingBackgrounded by rememberSaveable(chatScopeId) { mutableStateOf(false) }
    var inputLimitHintVisible by remember { mutableStateOf(false) }
    var inputLimitHintTick by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val followSlopPx = with(density) { STREAM_AUTO_FOLLOW_SLOP.toPx().toInt() }
    val minSendAnchorExtraBottomSpacePx = with(density) { MIN_SEND_ANCHOR_EXTRA_BOTTOM_SPACE.toPx().roundToInt() }
    val assistantStartAnchorTopPx = with(density) { ASSISTANT_START_ANCHOR_TOP.toPx().roundToInt() }
    val streamVisibleBottomGapPx = with(density) { STREAM_VISIBLE_BOTTOM_GAP.toPx().roundToInt() }
    val bottomOverlayClearancePx = with(density) { BOTTOM_OVERLAY_CONTENT_CLEARANCE.toPx().roundToInt() }
    val activeStreamBottomSpacerPx = if (
        isStreaming &&
        anchoredUserMessageId != null &&
        (
            autoScrollMode == AutoScrollMode.AnchorUser ||
                (!userDetachedFromBottom && !userInteracting)
            )
    ) {
        streamBottomSpacerPx
    } else {
        0
    }
    val streamBottomSpacerDp = with(density) { activeStreamBottomSpacerPx.toDp() }
    val hasStreamingItem by remember(isStreaming, streamingMessageContent) {
        derivedStateOf { isStreaming || streamingMessageContent.isNotBlank() }
    }
    val lockUserScrollDuringBall by remember(isStreaming, autoScrollMode, streamingMessageContent) {
        derivedStateOf {
            isStreaming &&
                autoScrollMode == AutoScrollMode.AnchorUser &&
                streamingMessageContent.isBlank()
        }
    }
    val hasStartupBottomViewport by remember(initialBottomViewport, initialLocalMessages.size) {
        derivedStateOf {
            initialBottomViewport != null &&
                initialLocalMessages.isNotEmpty()
        }
    }
    val atBottom by remember {
        derivedStateOf { !listState.canScrollForward }
    }
    val shouldOfferJumpButton by remember(
        atBottom,
        messages.size,
        hasStreamingItem,
        autoScrollMode
    ) {
        derivedStateOf {
            (messages.isNotEmpty() || hasStreamingItem) &&
                autoScrollMode == AutoScrollMode.Idle &&
                !atBottom
        }
    }
    val shouldRevealMessageList = true
    val appCenterTint = Color.White
    val chromeSurface = Color.White
    val chromeBorder = Color(0xFFD8DADF).copy(alpha = 0.18f)
    val inputSurface = Color.White
    val inputBorder = Color(0xFFD4D8DE).copy(alpha = 0.22f)
    val userBubbleColor = Color(0xFFF4F4F7)
    val imeVisible = WindowInsets.isImeVisible
    var historyHydrationComplete by remember(chatScopeId) {
        mutableStateOf(initialLocalMessages.isNotEmpty() || !shouldHydrateRemoteHistory)
    }
    val showWelcomePlaceholder by remember(historyHydrationComplete, messages.size, hasStreamingItem) {
        derivedStateOf {
            historyHydrationComplete && messages.isEmpty() && !hasStreamingItem
        }
    }
    val topInset = WindowInsets.safeDrawing
        .only(WindowInsetsSides.Top)
        .asPaddingValues()
        .calculateTopPadding()
    val jumpButtonBottomPadding = with(density) { bottomBarHeightPx.toDp() + 48.dp }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    fun performButtonHaptic() {
        val handled = view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (!handled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    LaunchedEffect(chatScopeId) {
        initialBottomSnapDone = initialLocalMessages.isEmpty()
        initialListRevealConsumed = true
        jumpButtonVisible = false
        LaunchUiGate.chatReady = true
    }

    fun replaceMessages(newMessages: List<ChatMessage>) {
        val trimmed = sanitizeMessageWindow(newMessages)
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
            streamingFreshStart = -1
            streamingFreshEnd = -1
            streamBottomSpacerPx = 0
            streamingContentBottomPx = -1
            streamBottomFollowActive = false
            pendingResumeAutoFollow = false
            pendingFinalBottomSnap = false
            userDetachedFromBottom = false
            autoScrollMode = AutoScrollMode.Idle
            if (clearVisibleContent) {
                streamingMessageContent = ""
            }
            snackbarScope.launch {
                context.clearLocalStreamingDraft(chatScopeId)
            }
        }
    }

    LaunchedEffect(chatScopeId) {
        if (initialLocalMessages.isNotEmpty()) {
            snackbarScope.launch {
                prewarmAssistantMarkdown(initialLocalMessages)
            }
        }
        if (shouldHydrateRemoteHistory) {
                SessionApi.getSnapshot { snapshot ->
                val remoteMessages = snapshot?.a_rounds_for_ui?.let(::snapshotRoundsToMessages).orEmpty()
                if (remoteMessages.isNotEmpty()) {
                    snackbarScope.launch {
                        prewarmAssistantMarkdown(remoteMessages)
                    }
                }
                mainHandler.post {
                    if (remoteMessages.isNotEmpty()) {
                        if (!hasStartedConversation && !isStreaming && shouldReplaceHydratedMessages(messages, remoteMessages)) {
                            replaceMessages(remoteMessages)
                            initialBottomSnapDone = false
                            persistTick++
                        }
                    }
                    historyHydrationComplete = true
                }
            }
        } else {
            historyHydrationComplete = true
        }
    }

    LaunchedEffect(chatScopeId, initialStreamingDraft) {
        if (initialStreamingDraft == null) return@LaunchedEffect
        context.saveLocalChatWindow(chatScopeId, initialLocalMessages)
        context.clearLocalStreamingDraft(chatScopeId)
        jumpButtonVisible = false
    }

    LaunchedEffect(persistTick) {
        if (persistTick == 0) return@LaunchedEffect
        delay(if (isStreaming) 220 else 80)
        context.saveLocalChatWindow(chatScopeId, messages)
    }

    LaunchedEffect(inputLimitHintTick) {
        if (inputLimitHintTick == 0) return@LaunchedEffect
        val currentTick = inputLimitHintTick
        inputLimitHintVisible = true
        delay(INPUT_LIMIT_HINT_MS)
        if (inputLimitHintTick == currentTick) {
            inputLimitHintVisible = false
        }
    }

    LaunchedEffect(chatScopeId, listState, messages.size, hasStreamingItem) {
        snapshotFlow {
            if (!atBottom || (messages.isEmpty() && !hasStreamingItem)) {
                null
            } else {
                LocalBottomViewport(
                    firstVisibleItemIndex = listState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
                )
            }
        }
            .distinctUntilChanged()
            .debounce(BOTTOM_VIEWPORT_SAVE_DEBOUNCE_MS)
            .filterNotNull()
            .collect { viewport ->
                context.saveLocalBottomViewport(
                    chatScopeId = chatScopeId,
                    viewport = viewport
                )
            }
    }

    LaunchedEffect(
        chatScopeId,
        isStreaming,
        streamingMessageId,
        streamingMessageContent.length,
        streamingRevealBuffer.length,
        anchoredUserMessageId
    ) {
        if (!isStreaming || streamingMessageId.isNullOrBlank()) {
            if (streamingMessageContent.isBlank() && streamingRevealBuffer.isBlank()) {
                context.clearLocalStreamingDraft(chatScopeId)
            }
            return@LaunchedEffect
        }
        delay(90)
        context.saveLocalStreamingDraft(
            chatScopeId = chatScopeId,
            draft = LocalStreamingDraft(
                messageId = streamingMessageId.orEmpty(),
                content = streamingMessageContent,
                revealBuffer = streamingRevealBuffer,
                anchoredUserMessageId = anchoredUserMessageId,
                savedAtMs = SystemClock.uptimeMillis()
            )
        )
    }

    LaunchedEffect(listState.isScrollInProgress, programmaticScroll, atBottom) {
        if (programmaticScroll) {
            userInteracting = false
            return@LaunchedEffect
        }
        if (listState.isScrollInProgress && imeVisible) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
        userInteracting = listState.isScrollInProgress
        if (atBottom) {
            userDetachedFromBottom = false
            pendingResumeAutoFollow = false
            jumpButtonVisible = false
        }
    }

    LaunchedEffect(listState, isStreaming, hasStreamingItem) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousOffset = listState.firstVisibleItemScrollOffset
        while (isActive) {
            withFrameNanos { }
            val currentIndex = listState.firstVisibleItemIndex
            val currentOffset = listState.firstVisibleItemScrollOffset
            if (programmaticScroll) {
                previousIndex = currentIndex
                previousOffset = currentOffset
                continue
            }
            val movedTowardBottom =
                currentIndex > previousIndex ||
                    (currentIndex == previousIndex && currentOffset > previousOffset)
            val movedTowardTop =
                currentIndex < previousIndex ||
                    (currentIndex == previousIndex && currentOffset < previousOffset)
            if (atBottom) {
                userDetachedFromBottom = false
                pendingResumeAutoFollow = false
                jumpButtonVisible = false
            } else if (!isStreaming || !hasStreamingItem) {
                pendingResumeAutoFollow = false
                when {
                    movedTowardBottom -> {
                        userDetachedFromBottom = false
                        jumpButtonVisible = false
                    }

                    movedTowardTop -> {
                        userDetachedFromBottom = true
                    }
                }
            } else if (listState.isScrollInProgress) {
                if (autoScrollMode == AutoScrollMode.AnchorUser) {
                    pendingResumeAutoFollow = false
                    userDetachedFromBottom = false
                    jumpButtonVisible = false
                    previousIndex = currentIndex
                    previousOffset = currentOffset
                    continue
                }
                pendingResumeAutoFollow = false
                userDetachedFromBottom = true
            }
            previousIndex = currentIndex
            previousOffset = currentOffset
        }
    }

    LaunchedEffect(autoScrollMode, streamingMessageContent.length, userDetachedFromBottom, userInteracting) {
        if (streamBottomSpacerPx <= 0) return@LaunchedEffect
        if (!isStreaming || autoScrollMode == AutoScrollMode.Idle) {
            streamBottomSpacerPx = 0
            return@LaunchedEffect
        }
        if (autoScrollMode != AutoScrollMode.AnchorUser && (userDetachedFromBottom || userInteracting)) {
            streamBottomSpacerPx = 0
        }
    }

    LaunchedEffect(shouldOfferJumpButton, listState.isScrollInProgress, programmaticScroll) {
        if (programmaticScroll || listState.isScrollInProgress) {
            jumpButtonVisible = false
            return@LaunchedEffect
        }
        if (shouldOfferJumpButton) {
            jumpButtonVisible = true
            delay(JUMP_BUTTON_AUTO_HIDE_MS)
            if (!listState.isScrollInProgress && !programmaticScroll && shouldOfferJumpButton) {
                jumpButtonVisible = false
            }
            return@LaunchedEffect
        }
        if (!jumpButtonVisible) return@LaunchedEffect
        delay(JUMP_BUTTON_AUTO_HIDE_MS)
        if (!listState.isScrollInProgress && !programmaticScroll && !shouldOfferJumpButton) {
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
                streamingFreshStart = streamingMessageContent.length
                streamingMessageContent += batch.text
                streamingFreshEnd = streamingMessageContent.length
                streamingFreshTick++
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
            if (streamRevealJob?.isActive == true) {
                streamRevealJob?.cancel()
                streamRevealJob = null
            }
            if (streamingRevealBuffer.isNotEmpty()) {
                streamingMessageContent += streamingRevealBuffer
                streamingRevealBuffer = ""
            }
            streamingFreshStart = streamingMessageContent.length
            streamingMessageContent += piece
            streamingFreshEnd = streamingMessageContent.length
            streamingFreshTick++
            streamTick++
        }
    }

    fun currentStreamingOverflowDelta(): Int {
        val info = listState.layoutInfo
        val lastIndex = info.totalItemsCount - 1
        if (lastIndex < 0) return 0
        val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return 0
        if (lastVisible.index < lastIndex) {
            return (info.viewportEndOffset * 0.18f)
                .roundToInt()
                .coerceAtLeast(STREAM_ANCHOR_FOLLOW_STEP_PX)
        }
        val visibleBottom = (info.viewportEndOffset - streamVisibleBottomGapPx).coerceAtLeast(0)
        val itemBottom = lastVisible.offset + lastVisible.size
        return (itemBottom - visibleBottom).coerceAtLeast(0)
    }

    fun resolveStreamingFollowStepPx(overflow: Int): Int {
        val viewportStep = (messageViewportHeightPx * 0.012f)
            .roundToInt()
            .coerceAtLeast(STREAM_BOTTOM_FOLLOW_STEP_PX / 2)
        val smoothStep = maxOf(viewportStep, 6)
        return overflow.coerceAtMost(smoothStep)
    }

    suspend fun smoothCatchUpToBottom(maxFrames: Int = 480) {
        lastProgrammaticScrollMs = SystemClock.uptimeMillis()
        programmaticScroll = true
        try {
            repeat(maxFrames) {
                withFrameNanos { }
                if (!listState.canScrollForward) return
                val consumed = listState.scrollBy(STREAM_BOTTOM_FOLLOW_STEP_PX.toFloat())
                if (consumed <= 0f) return
            }
        } finally {
            programmaticScroll = false
            lastProgrammaticScrollMs = SystemClock.uptimeMillis()
        }
    }

    fun finishStreaming() {
        mainHandler.post {
            val shouldSnapToBottomOnFinish =
                autoScrollMode == AutoScrollMode.StreamAnchorFollow &&
                    !userInteracting &&
                    !userDetachedFromBottom
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
                replaceMessages(
                    appendCompletedAssistantMessage(
                        source = messages,
                        messageId = finalId.orEmpty(),
                        content = finalContent
                    )
                )
            }
            fakeStreamJob = null
            isStreaming = false
            streamingMessageId = null
            streamingMessageContent = ""
            streamingRevealBuffer = ""
            streamingFreshStart = -1
            streamingFreshEnd = -1
            streamingAnchorTopPx = -1
            streamBottomSpacerPx = 0
            streamingContentBottomPx = -1
            streamBottomFollowActive = false
            pendingResumeAutoFollow = false
            streamingBackgrounded = false
            userDetachedFromBottom = false
            autoScrollMode = AutoScrollMode.Idle
            persistTick++
            pendingFinalBottomSnap = shouldSnapToBottomOnFinish
        }
    }

    fun launchLocalFakeStream(
        skipChars: Int = 0,
        applyInitialDelay: Boolean
    ) {
        val fullText = FAKE_STREAM_TEXT
        val safeSkipChars = skipChars.coerceIn(0, fullText.length)
        fakeStreamJob?.cancel()
        fakeStreamJob = snackbarScope.launch {
            if (applyInitialDelay) {
                val ballStartTime = SystemClock.uptimeMillis()
                val initialDelayMs = Random.nextLong(LOCAL_STREAM_FIRST_TOKEN_MIN_MS, LOCAL_STREAM_FIRST_TOKEN_MAX_MS)
                val minBallMs = LOCAL_STREAM_MIN_BALL_MS
                val elapsed = SystemClock.uptimeMillis() - ballStartTime
                val firstTokenWait = maxOf(initialDelayMs, minBallMs - elapsed)
                if (firstTokenWait > 0) {
                    delay(firstTokenWait)
                }
            }

            var remaining = fullText.drop(safeSkipChars)
            if (remaining.isEmpty()) {
                while (isActive && streamingRevealBuffer.isNotEmpty()) {
                    delay(STREAM_TYPEWRITER_IDLE_POLL_MS)
                }
                if (isActive && isStreaming) {
                    finishStreaming()
                }
                return@launch
            }

            while (isActive && remaining.isNotEmpty()) {
                val step = nextLocalStreamFeedBatch(remaining)
                appendAssistantChunk(step.text)
                remaining = remaining.drop(step.text.length)
                if (step.delayMs > 0) {
                    delay(step.delayMs)
                }
            }
            if (isActive) finishStreaming()
        }
    }

    fun recoverStreamingAfterLifecycleLoss() {
        mainHandler.post {
            if (!isStreaming) return@post
            autoScrollMode = AutoScrollMode.StreamAnchorFollow
            if (streamRevealJob?.isActive == true) {
                streamRevealJob?.cancel()
                streamRevealJob = null
            }
            if (streamingRevealBuffer.isNotEmpty()) {
                if (streamingMessageId.isNullOrBlank()) {
                    streamingMessageId = "assistant_${UUID.randomUUID()}"
                }
                streamingMessageContent += streamingRevealBuffer
                streamingRevealBuffer = ""
                streamTick++
            }
            if (fakeStreamJob?.isActive == true) return@post
            val consumedChars = (streamingMessageContent.length + streamingRevealBuffer.length)
                .coerceAtMost(FAKE_STREAM_TEXT.length)
            launchLocalFakeStream(
                skipChars = consumedChars,
                applyInitialDelay = false
            )
        }
    }

    fun commitSendMessage(text: String) {
        if (text.isEmpty() || isStreaming) return
        hasStartedConversation = true
        initialBottomSnapDone = true
        initialListRevealConsumed = true
        LaunchUiGate.chatReady = true
        val userId = "user_${UUID.randomUUID()}"
        messages.add(ChatMessage(userId, ChatRole.USER, text))
        anchoredUserMessageId = userId
        streamingAnchorTopPx = -1
        streamingContentBottomPx = -1
        streamBottomFollowActive = false
        pendingResumeAutoFollow = false
        pendingFinalBottomSnap = false
        streamingFreshStart = -1
        streamingFreshEnd = -1
        userDetachedFromBottom = false
        jumpButtonVisible = false
        input.value = ""
        focusManager.clearFocus(force = true)
        keyboardController?.hide()

        trimMessagesInPlace()
        persistTick++
        snackbarScope.launch {
            context.saveLocalChatWindow(chatScopeId, messages)
        }
        isStreaming = true
        streamingMessageId = "assistant_${UUID.randomUUID()}"
        streamingMessageContent = ""
        streamingRevealBuffer = ""
        streamingFreshStart = -1
        streamingFreshEnd = -1
        context.saveLocalStreamingDraftSync(
            chatScopeId = chatScopeId,
            draft = LocalStreamingDraft(
                messageId = streamingMessageId.orEmpty(),
                content = "",
                revealBuffer = "",
                anchoredUserMessageId = anchoredUserMessageId,
                savedAtMs = SystemClock.uptimeMillis()
            )
        )
        streamBottomSpacerPx = maxOf(
            (messageViewportHeightPx * SEND_ANCHOR_EXTRA_BOTTOM_SPACE_RATIO).roundToInt(),
            minSendAnchorExtraBottomSpacePx
        )
        streamingBackgrounded = false
        autoScrollMode = AutoScrollMode.AnchorUser
        userInteracting = false
        sendTick++

        fakeStreamJob?.cancel()
        streamRevealJob?.cancel()
        streamRevealJob = null
        launchLocalFakeStream(applyInitialDelay = true)
    }

    fun sendMessage() {
        val text = input.value.trim()
        if (text.isEmpty() || isStreaming) return
        commitSendMessage(text)
    }

    suspend fun scrollToBottom(animated: Boolean) {
        if (messages.isEmpty() && !hasStreamingItem) return
        lastProgrammaticScrollMs = SystemClock.uptimeMillis()
        programmaticScroll = true
        try {
            withFrameNanos { }
            val lastIndex = (messages.size + if (hasStreamingItem) 1 else 0) - 1
            if (lastIndex < 0) return
            if (animated) {
                listState.animateScrollToItem(lastIndex)
            } else {
                listState.scrollToItem(lastIndex)
            }
            val stickyStepPx = messageViewportHeightPx
                .takeIf { it > 0 }
                ?.let { (it * 0.56f).roundToInt() }
                ?.coerceAtLeast(STREAM_STICKY_SCROLL_STEP_PX)
                ?: STREAM_STICKY_SCROLL_STEP_PX
            if (animated) {
                repeat(72) {
                    withFrameNanos { }
                    if (!listState.canScrollForward) return
                    val consumed = listState.scrollBy(stickyStepPx.toFloat())
                    if (consumed <= 0f) return
                }
            } else {
                repeat(72) {
                    if (!listState.canScrollForward) return
                    val consumed = listState.scrollBy(stickyStepPx.toFloat())
                    if (consumed <= 0f) return
                }
            }
            withFrameNanos { }
        } finally {
            programmaticScroll = false
            lastProgrammaticScrollMs = SystemClock.uptimeMillis()
        }
    }

    suspend fun scrollStreamingToBottom() {
        if (messages.isEmpty() && !hasStreamingItem) return
        lastProgrammaticScrollMs = SystemClock.uptimeMillis()
        programmaticScroll = true
        try {
            withFrameNanos { }
            val lastIndex = (messages.size + if (hasStreamingItem) 1 else 0) - 1
            if (lastIndex < 0) return
            listState.scrollToItem(lastIndex)
            withFrameNanos { }
        } finally {
            programmaticScroll = false
            lastProgrammaticScrollMs = SystemClock.uptimeMillis()
        }
    }

    LaunchedEffect(pendingFinalBottomSnap, messages.size, isStreaming) {
        if (!pendingFinalBottomSnap || isStreaming) return@LaunchedEffect
        for (attempt in 0 until 4) {
            repeat(2) { withFrameNanos { } }
            scrollToBottom(animated = false)
            if (!listState.canScrollForward) {
                break
            }
            if (attempt < 3) {
                delay(24)
            }
        }
        jumpButtonVisible = false
        pendingFinalBottomSnap = false
    }

    fun completeStreamingImmediatelyFromBackground() {
        mainHandler.post {
            if (!isStreaming) return@post
            val shouldSnapToBottomOnFinish =
                autoScrollMode == AutoScrollMode.StreamAnchorFollow &&
                    !userInteracting &&
                    !userDetachedFromBottom
            val finalId = streamingMessageId ?: "assistant_${UUID.randomUUID()}"
            val finalContent = normalizeAssistantText(FAKE_STREAM_TEXT)
            fakeStreamJob?.cancel()
            fakeStreamJob = null
            streamRevealJob?.cancel()
            streamRevealJob = null
            streamingMessageId = finalId
            streamingMessageContent = finalContent
            streamingRevealBuffer = ""
            if (finalContent.isNotBlank()) {
                replaceMessages(
                    appendCompletedAssistantMessage(
                        source = messages,
                        messageId = finalId,
                        content = finalContent
                    )
                )
            }
            isStreaming = false
            streamingMessageId = null
            streamingMessageContent = ""
            streamingRevealBuffer = ""
            streamingFreshStart = -1
            streamingFreshEnd = -1
            streamBottomSpacerPx = 0
            streamingContentBottomPx = -1
            streamBottomFollowActive = false
            pendingResumeAutoFollow = false
            streamingBackgrounded = false
            userDetachedFromBottom = false
            autoScrollMode = AutoScrollMode.Idle
            jumpButtonVisible = false
            pendingFinalBottomSnap = shouldSnapToBottomOnFinish
            context.saveLocalChatWindowSync(chatScopeId, messages)
            context.clearLocalStreamingDraftSync(chatScopeId)
            snackbarScope.launch {
                prewarmAssistantMarkdown(messages.takeLast(2))
            }
        }
    }

    DisposableEffect(lifecycleOwner, focusManager, keyboardController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                if (isStreaming) {
                    streamingBackgrounded = true
                    completeStreamingImmediatelyFromBackground()
                }
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            } else if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                streamingBackgrounded = false
                if (isStreaming && fakeStreamJob?.isActive != true && streamRevealJob?.isActive != true) {
                    recoverStreamingAfterLifecycleLoss()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    suspend fun isBottomSettled(stableFrames: Int = 4): Boolean {
        repeat(stableFrames) {
            withFrameNanos { }
            if (listState.canScrollForward) return false
        }
        return true
    }

    suspend fun scrollAfterSendAnchor() {
        if (messages.isEmpty() && !hasStreamingItem) return
        lastProgrammaticScrollMs = SystemClock.uptimeMillis()
        programmaticScroll = true
        try {
            val anchorTop = assistantStartAnchorTopPx
            val anchorIndex = (messages.size + if (hasStreamingItem) 1 else 0) - 1
            if (anchorIndex >= 0) {
                listState.scrollToItem(anchorIndex, scrollOffset = -anchorTop)
                repeat(6) { withFrameNanos { } }
            }
        } finally {
            programmaticScroll = false
            lastProgrammaticScrollMs = SystemClock.uptimeMillis()
        }
    }

    LaunchedEffect(
        autoScrollMode,
        isStreaming,
        hasStreamingItem,
        userInteracting,
        userDetachedFromBottom,
        streamTick
    ) {
        if (!hasStreamingItem || !isStreaming) {
            streamBottomFollowActive = false
            return@LaunchedEffect
        }
        if (autoScrollMode != AutoScrollMode.StreamAnchorFollow || userInteracting || userDetachedFromBottom) {
            streamBottomFollowActive = false
            return@LaunchedEffect
        }
        if (streamingMessageContent.isBlank()) {
            streamBottomFollowActive = false
            return@LaunchedEffect
        }
        val overflow = currentStreamingOverflowDelta()
        val stepPx = resolveStreamingFollowStepPx(overflow)
        if (stepPx <= 0) {
            streamBottomFollowActive = false
            return@LaunchedEffect
        }
        streamBottomFollowActive = true
        lastProgrammaticScrollMs = SystemClock.uptimeMillis()
        programmaticScroll = true
        try {
            listState.scrollBy(stepPx.toFloat())
        } finally {
            programmaticScroll = false
            lastProgrammaticScrollMs = SystemClock.uptimeMillis()
            streamBottomFollowActive = false
        }
    }

    LaunchedEffect(sendTick) {
        if (messages.isEmpty()) return@LaunchedEffect
        autoScrollMode = AutoScrollMode.AnchorUser
        userInteracting = false
        scrollAfterSendAnchor()
        autoScrollMode = if (isStreaming && streamingMessageContent.isNotBlank()) {
            AutoScrollMode.StreamAnchorFollow
        } else {
            AutoScrollMode.AnchorUser
        }
    }

    LaunchedEffect(isStreaming, streamingMessageContent.isNotBlank(), autoScrollMode, userDetachedFromBottom) {
        if (!isStreaming) return@LaunchedEffect
        if (autoScrollMode != AutoScrollMode.AnchorUser) return@LaunchedEffect
        if (userDetachedFromBottom) return@LaunchedEffect
        if (streamingMessageContent.isBlank()) return@LaunchedEffect
        autoScrollMode = AutoScrollMode.StreamAnchorFollow
    }

    LaunchedEffect(
        hasStartedConversation,
        messages.size,
        isStreaming,
        bottomBarHeightPx,
        hasStreamingItem,
        initialBottomSnapDone,
        hasStartupBottomViewport
    ) {
        if (hasStartedConversation) return@LaunchedEffect
        if (initialBottomSnapDone) return@LaunchedEffect
        if (messages.isEmpty() || isStreaming || hasStreamingItem) return@LaunchedEffect
        if (bottomBarHeightPx <= 0) return@LaunchedEffect
        repeat(if (hasStartupBottomViewport) 1 else 4) { withFrameNanos { } }
        repeat(if (hasStartupBottomViewport) 5 else 4) { attempt ->
            scrollToBottom(animated = false)
            if (isBottomSettled()) {
                jumpButtonVisible = false
                initialBottomSnapDone = true
                return@LaunchedEffect
            }
            if (attempt < if (hasStartupBottomViewport) 4 else 3) {
                delay(if (hasStartupBottomViewport) 24 else 90)
            }
        }
    }

    fun jumpToBottom() {
        snackbarScope.launch {
            if (messages.isEmpty() && !hasStreamingItem) return@launch
            autoScrollMode = if (isStreaming && hasStreamingItem) {
                AutoScrollMode.StreamAnchorFollow
            } else {
                AutoScrollMode.Idle
            }
            userInteracting = false
            pendingResumeAutoFollow = false
            userDetachedFromBottom = false
            jumpButtonVisible = false
            scrollToBottom(animated = false)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(appCenterTint)
    ) {
        val chromeMaxWidth: Dp = when {
            maxWidth >= 900.dp -> 900.dp
            maxWidth >= 700.dp -> 760.dp
            else -> maxWidth
        }
        val chromeHorizontalPadding = when {
            maxWidth < 360.dp -> 12.dp
            maxWidth < 600.dp -> 16.dp
            else -> 24.dp
        }
        val listHorizontalPadding = when {
            maxWidth < 360.dp -> 12.dp
            maxWidth < 600.dp -> 14.dp
            else -> 22.dp
        }
        val inputBarHeight = if (maxWidth < 360.dp) 52.dp else 56.dp
        val inputBarMaxHeight = if (maxWidth < 360.dp) 132.dp else 156.dp
        val chromeButtonSize = if (maxWidth < 360.dp) 40.dp else 42.dp
        val actionCircleSize = if (maxWidth < 360.dp) 42.dp else 44.dp
        val addButtonSize = actionCircleSize
        val addIconSize = if (maxWidth < 360.dp) 27.dp else 29.dp
        val sendButtonSize = actionCircleSize
        val userBubbleMaxWidth = if (chromeMaxWidth < 440.dp) chromeMaxWidth * 0.8f else 432.dp
        val topBarReservedHeight = topInset + chromeButtonSize + 6.dp
        val pageSurface = Color(0xFFFFFFFF)
        val inputChromeHorizontalPadding = when {
            maxWidth < 360.dp -> 10.dp
            maxWidth < 600.dp -> 12.dp
            else -> chromeHorizontalPadding
        }
        val inputChromeSurface = Color.White
        val inputChromeBorder = Color(0xFFC9CDD4).copy(alpha = 0.42f)
        val inputBarOverlayBrush = Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.White.copy(alpha = 0.10f),
                0.18f to Color.White.copy(alpha = 0.28f),
                0.54f to Color.White.copy(alpha = 0.64f),
                1.0f to Color.White.copy(alpha = 0.90f)
            )
        )
        val inputFieldSurface = Color.White
        val inputFieldBorder = Color(0xFFC9CDD4).copy(alpha = 0.4f)
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = pageSurface,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .onSizeChanged { bottomBarHeightPx = it.height }
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(inputBarOverlayBrush)
                    )
                    if (inputLimitHintVisible) {
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
                                    end = inputChromeHorizontalPadding,
                                    bottom = inputBarMaxHeight + 14.dp
                                )
                        ) {
                            Text(
                                text = "已超过6000字，暂时不能发送",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .widthIn(max = chromeMaxWidth)
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            .padding(horizontal = inputChromeHorizontalPadding, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FrostedCircleButton(
                            size = addButtonSize,
                            surfaceColor = inputChromeSurface,
                            borderColor = inputChromeBorder,
                            onClick = {
                                performButtonHaptic()
                            }
                        ) {
                            PlusCrossIcon(
                                tint = Color(0xFF6F7277),
                                modifier = Modifier.size(addIconSize)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(30.dp),
                            color = inputFieldSurface,
                            border = BorderStroke(0.72.dp, inputFieldBorder.copy(alpha = 0.62f)),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .shadow(
                                    elevation = 1.35.dp,
                                    shape = RoundedCornerShape(30.dp),
                                    ambientColor = Color(0x14000000),
                                    spotColor = Color(0x14000000)
                                )
                        ) {
                            val exceedsInputLimit = input.value.length > INPUT_MAX_CHARS
                            val inputSelectionColors = TextSelectionColors(
                                handleColor = Color(0xFFB8BDC5),
                                backgroundColor = Color(0xFFD9DDE3).copy(alpha = 0.72f)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = inputBarHeight, max = inputBarMaxHeight)
                            ) {
                                CompositionLocalProvider(LocalTextSelectionColors provides inputSelectionColors) {
                                    TextField(
                                        value = input.value,
                                        onValueChange = {
                                            if (it.length > INPUT_MAX_CHARS && input.value.length <= INPUT_MAX_CHARS) {
                                                inputLimitHintTick++
                                            }
                                            input.value = it
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.CenterStart)
                                            .padding(start = 2.dp, end = 58.dp),
                                        placeholder = { Text("描述作物/图片/问题", color = Color(0xFFAEAFB4)) },
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
                                            disabledIndicatorColor = Color.Transparent
                                        )
                                    )
                                }
                                val canPressSend = input.value.trim().isNotEmpty() && !isStreaming
                                val canSend = canPressSend && !exceedsInputLimit
                                val actionBg = if (canPressSend) Color(0xFF111111) else Color(0xFFD3D4D6)
                                val actionTint = if (canPressSend) Color.White else Color(0xFF7F8083)

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
                                            performButtonHaptic()
                                            if (exceedsInputLimit) {
                                                inputLimitHintTick++
                                            } else if (canSend) {
                                                sendMessage()
                                            }
                                        },
                                        enabled = canPressSend,
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
        ) { _ ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(pageSurface)
                    .onSizeChanged { messageViewportHeightPx = it.height }
                    .onGloballyPositioned { coordinates ->
                        messageViewportTopPx = coordinates.boundsInWindow().top
                    }
            ) {
                LazyColumn(
                    state = listState,
                    userScrollEnabled = !lockUserScrollDuringBall,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (shouldRevealMessageList) {
                                Modifier
                            } else {
                                Modifier.graphicsLayer(alpha = 0f)
                            }
                        ),
                    contentPadding = PaddingValues(
                        top = topBarReservedHeight,
                        bottom = with(density) { bottomBarHeightPx.toDp() } +
                            BOTTOM_OVERLAY_CONTENT_CLEARANCE +
                            streamBottomSpacerDp
                    )
                ) {
                    items(
                        items = messages,
                        key = { it.id },
                        contentType = { it.role }
                    ) { msg ->
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
                                        animateLayoutChanges = false,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                    )
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
                    if (hasStreamingItem) {
                        item(
                            key = "streaming_${streamingMessageId ?: "message"}",
                            contentType = ChatRole.ASSISTANT
                        ) {
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
                                        .onGloballyPositioned { coordinates ->
                                            val bounds = coordinates.boundsInWindow()
                                            streamingAnchorTopPx =
                                                (bounds.top - messageViewportTopPx).roundToInt()
                                        }
                                ) {
                                    AssistantMessageContent(
                                        content = streamingMessageContent,
                                        isStreaming = isStreaming,
                                        animateLayoutChanges = false,
                                        streamingFreshStart = streamingFreshStart,
                                        streamingFreshEnd = streamingFreshEnd,
                                        streamingFreshTick = streamingFreshTick,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                if (showWelcomePlaceholder) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(top = topBarReservedHeight + 56.dp)
                            .padding(horizontal = listHorizontalPadding)
                    ) {
                        Text(
                            text = "欢迎咨询种植、病虫害防治、施肥等问题。\n描述作物/地区/现象，必要时可上传图片。",
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .widthIn(max = chromeMaxWidth)
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF141414),
                            lineHeight = MaterialTheme.typography.titleMedium.lineHeight,
                            textAlign = TextAlign.Start
                        )
                    }
                }

            if (jumpButtonVisible && shouldRevealMessageList) {
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
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(topInset + chromeButtonSize + 2.dp)
                    .background(pageSurface)
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
