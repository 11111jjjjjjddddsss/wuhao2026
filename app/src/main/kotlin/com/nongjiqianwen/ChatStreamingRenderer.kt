package com.nongjiqianwen

import android.os.Handler
import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.TextMeasurer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.random.Random

internal enum class StreamingRenderMode {
    Waiting,
    Streaming,
    Settled
}

internal enum class StreamingRevealMode {
    Free,
    Conservative
}

internal val ASSISTANT_WAITING_STABLE_SHELL_EXTRA_HEIGHT = 16.dp

internal data class ChatStreamingRuntimeState(
    val isStreaming: MutableState<Boolean>,
    val streamingMessageId: MutableState<String?>,
    val streamingMessageContent: MutableState<String>,
    val streamingRevealBuffer: MutableState<String>,
    val streamRevealJob: MutableState<kotlinx.coroutines.Job?>,
    val streamingLineAdvanceTick: MutableIntState,
    val streamingFreshStart: MutableIntState,
    val streamingFreshEnd: MutableIntState,
    val streamingFreshTick: MutableIntState,
    val lastStreamingFreshRevealMs: MutableState<Long>
)

@Composable
internal fun rememberChatStreamingRuntimeState(chatScopeId: String): ChatStreamingRuntimeState {
    val isStreaming = rememberSaveable(chatScopeId) { mutableStateOf(false) }
    val streamingMessageId = rememberSaveable(chatScopeId) { mutableStateOf<String?>(null) }
    val streamingMessageContent = rememberSaveable(chatScopeId) { mutableStateOf("") }
    val streamingRevealBuffer = rememberSaveable(chatScopeId) { mutableStateOf("") }
    val streamRevealJob = remember(chatScopeId) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val streamingLineAdvanceTick = remember(chatScopeId) { mutableIntStateOf(0) }
    val streamingFreshStart = remember(chatScopeId) { mutableIntStateOf(-1) }
    val streamingFreshEnd = remember(chatScopeId) { mutableIntStateOf(-1) }
    val streamingFreshTick = remember(chatScopeId) { mutableIntStateOf(0) }
    val lastStreamingFreshRevealMs = remember(chatScopeId) { mutableStateOf(0L) }
    return remember(chatScopeId) {
        ChatStreamingRuntimeState(
            isStreaming = isStreaming,
            streamingMessageId = streamingMessageId,
            streamingMessageContent = streamingMessageContent,
            streamingRevealBuffer = streamingRevealBuffer,
            streamRevealJob = streamRevealJob,
            streamingLineAdvanceTick = streamingLineAdvanceTick,
            streamingFreshStart = streamingFreshStart,
            streamingFreshEnd = streamingFreshEnd,
            streamingFreshTick = streamingFreshTick,
            lastStreamingFreshRevealMs = lastStreamingFreshRevealMs
        )
    }
}

internal fun ensureStreamingRevealJob(
    currentJob: Job?,
    setJob: (Job?) -> Unit,
    scope: CoroutineScope,
    isStreaming: () -> Boolean,
    currentMessageId: () -> String?,
    currentContent: () -> String,
    currentRevealBuffer: () -> String,
    currentFreshTick: () -> Int,
    currentLastFreshRevealMs: () -> Long,
    anchoredUserMessageId: () -> String?,
    assistantIdProvider: (String) -> String,
    fallbackIdProvider: () -> String,
    onAdvance: (StreamingRevealAdvance) -> Unit,
    onTick: () -> Unit
) {
    if (currentJob?.isActive == true) return
    setJob(
        scope.launch {
            while (isActive) {
                val advance = consumeStreamingRevealBatch(
                    currentMessageId = currentMessageId(),
                    currentContent = currentContent(),
                    currentRevealBuffer = currentRevealBuffer(),
                    currentFreshTick = currentFreshTick(),
                    lastFreshRevealMs = currentLastFreshRevealMs(),
                    anchoredUserMessageId = anchoredUserMessageId(),
                    assistantIdProvider = assistantIdProvider,
                    fallbackIdProvider = fallbackIdProvider,
                    nowMs = SystemClock.uptimeMillis()
                )
                if (advance == null) {
                    if (!isStreaming()) break
                    delay(STREAM_TYPEWRITER_IDLE_POLL_MS)
                    continue
                }
                if (
                    advance.content == currentContent() &&
                    advance.revealBuffer == currentRevealBuffer()
                ) {
                    delay(advance.delayMs)
                    continue
                }
                onAdvance(advance)
                onTick()
                delay(advance.delayMs)
            }
        }
    )
}

internal fun appendAssistantChunk(
    piece: String,
    mainHandler: Handler,
    currentMessageId: () -> String?,
    currentRevealBuffer: () -> String,
    anchoredUserMessageId: () -> String?,
    assistantIdProvider: (String) -> String,
    fallbackIdProvider: () -> String,
    onQueued: (QueuedStreamingChunk) -> Unit,
    ensureRevealJob: () -> Unit
) {
    if (piece.isEmpty()) return
    mainHandler.post {
        val queued = queueStreamingChunk(
            currentMessageId = currentMessageId(),
            currentRevealBuffer = currentRevealBuffer(),
            piece = piece,
            anchoredUserMessageId = anchoredUserMessageId(),
            assistantIdProvider = assistantIdProvider,
            fallbackIdProvider = fallbackIdProvider
        ) ?: return@post
        onQueued(queued)
        ensureRevealJob()
    }
}

private val rendererHeadingRegex = Regex("^#{1,6}\\s+.*$")
private val rendererBulletRegex = Regex("^[*-]\\s+.*$")
private val rendererNumberedRegex = Regex("^\\d+\\.\\s+.*$")
private val rendererQuoteRegex = Regex("^>\\s+.*$")

internal data class StreamingTypewriterStep(
    val text: String,
    val delayMs: Long
)

internal data class StreamingRevealBatch(
    val text: String,
    val delayMs: Long
)

internal data class StreamingRenderedLines(
    val stableLines: List<AnnotatedString>,
    val activeLine: AnnotatedString?
)

private data class StreamingLogicalLines(
    val completedLines: List<String>,
    val activeLine: String?
)

internal data class StreamingBlockState(
    val completedBlocks: List<String>,
    val activeBlock: String?
)

internal sealed interface StreamingLineModel {
    data object Blank : StreamingLineModel
    data class Heading(val level: Int, val text: String) : StreamingLineModel
    data class Bullet(val text: String) : StreamingLineModel
    data class Numbered(val number: String, val text: String) : StreamingLineModel
    data class Quote(val text: String) : StreamingLineModel
    data class Paragraph(val text: String) : StreamingLineModel
}

internal data class QueuedStreamingChunk(
    val messageId: String,
    val revealBuffer: String
)

internal data class StreamingRevealAdvance(
    val messageId: String,
    val content: String,
    val revealBuffer: String,
    val freshStart: Int,
    val freshEnd: Int,
    val freshTick: Int,
    val lastFreshRevealMs: Long,
    val delayMs: Long
)

internal data class FlushedStreamingRenderBuffer(
    val messageId: String,
    val content: String
)

internal fun queueStreamingChunk(
    currentMessageId: String?,
    currentRevealBuffer: String,
    piece: String,
    anchoredUserMessageId: String?,
    assistantIdProvider: (String) -> String,
    fallbackIdProvider: () -> String
): QueuedStreamingChunk? {
    if (piece.isEmpty()) return null
    val messageId = ensureStreamingMessageId(
        currentMessageId = currentMessageId,
        anchoredUserMessageId = anchoredUserMessageId,
        assistantIdProvider = assistantIdProvider,
        fallbackIdProvider = fallbackIdProvider
    )
    return QueuedStreamingChunk(
        messageId = messageId,
        revealBuffer = currentRevealBuffer + piece
    )
}

internal fun consumeStreamingRevealBatch(
    currentMessageId: String?,
    currentContent: String,
    currentRevealBuffer: String,
    currentFreshTick: Int,
    lastFreshRevealMs: Long,
    anchoredUserMessageId: String?,
    assistantIdProvider: (String) -> String,
    fallbackIdProvider: () -> String,
    nowMs: Long
): StreamingRevealAdvance? {
    if (currentRevealBuffer.isEmpty()) return null
    val batch = buildStreamingRevealBatch(currentRevealBuffer)
    if (batch.text.isEmpty()) {
        return StreamingRevealAdvance(
            messageId = ensureStreamingMessageId(
                currentMessageId = currentMessageId,
                anchoredUserMessageId = anchoredUserMessageId,
                assistantIdProvider = assistantIdProvider,
                fallbackIdProvider = fallbackIdProvider
            ),
            content = currentContent,
            revealBuffer = currentRevealBuffer,
            freshStart = currentContent.length,
            freshEnd = currentContent.length,
            freshTick = currentFreshTick,
            lastFreshRevealMs = lastFreshRevealMs,
            delayMs = batch.delayMs
        )
    }
    val messageId = ensureStreamingMessageId(
        currentMessageId = currentMessageId,
        anchoredUserMessageId = anchoredUserMessageId,
        assistantIdProvider = assistantIdProvider,
        fallbackIdProvider = fallbackIdProvider
    )
    val nextContent = currentContent + batch.text
    val nextFreshTick = if (
        currentFreshTick <= 0 ||
        nowMs - lastFreshRevealMs >= STREAM_FRESH_SUFFIX_TRIGGER_INTERVAL_MS ||
        batch.text.indexOf('\n') >= 0
    ) {
        currentFreshTick + 1
    } else {
        currentFreshTick
    }
    val nextFreshRevealMs = if (nextFreshTick != currentFreshTick) nowMs else lastFreshRevealMs
    return StreamingRevealAdvance(
        messageId = messageId,
        content = nextContent,
        revealBuffer = currentRevealBuffer.drop(batch.text.length),
        freshStart = currentContent.length,
        freshEnd = nextContent.length,
        freshTick = nextFreshTick,
        lastFreshRevealMs = nextFreshRevealMs,
        delayMs = batch.delayMs
    )
}

internal fun flushStreamingRevealBuffer(
    currentMessageId: String?,
    currentContent: String,
    currentRevealBuffer: String,
    anchoredUserMessageId: String?,
    assistantIdProvider: (String) -> String,
    fallbackIdProvider: () -> String
): FlushedStreamingRenderBuffer? {
    if (currentRevealBuffer.isEmpty()) return null
    return FlushedStreamingRenderBuffer(
        messageId = ensureStreamingMessageId(
            currentMessageId = currentMessageId,
            anchoredUserMessageId = anchoredUserMessageId,
            assistantIdProvider = assistantIdProvider,
            fallbackIdProvider = fallbackIdProvider
        ),
        content = currentContent + currentRevealBuffer
    )
}

internal fun buildLockedStreamingActivePreview(
    activeLine: AnnotatedString,
    maxVisibleChars: Int
): AnnotatedString {
    if (maxVisibleChars <= 0 || activeLine.text.isEmpty()) {
        return AnnotatedString("")
    }
    val visibleCharCount = activeLine.text.count { !it.isWhitespace() }
    if (visibleCharCount <= maxVisibleChars) return activeLine

    val preview = StringBuilder()
    var remaining = activeLine.text
    var revealedVisibleChars = 0
    while (remaining.isNotEmpty() && revealedVisibleChars < maxVisibleChars) {
        val token = takeRendererTypewriterToken(remaining).ifEmpty { remaining.first().toString() }
        preview.append(token)
        revealedVisibleChars += token.count { !it.isWhitespace() }
        remaining = remaining.drop(token.length.coerceAtLeast(1))
    }
    return AnnotatedString(preview.toString())
}

internal fun splitStreamingBlockState(content: String): StreamingBlockState {
    val logicalLines = splitRendererStreamingLogicalLines(content)
    val completedBlocks = mutableListOf<String>()
    val paragraph = StringBuilder()

    fun flushParagraphBlock() {
        val text = paragraph.toString().trim()
        if (text.isNotEmpty()) {
            completedBlocks += text
        }
        paragraph.clear()
    }

    logicalLines.completedLines.forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.isBlank() -> flushParagraphBlock()
            isStructuralRendererStreamingLine(trimmed) -> {
                flushParagraphBlock()
                completedBlocks += trimmed
            }
            else -> paragraph.appendRendererParagraphLine(trimmed)
        }
    }

    val activeBlock = logicalLines.activeLine?.let { line ->
        val trimmed = line.trim()
        val activeLooksStructural = trimmed.isNotBlank() && isStructuralRendererActiveStreamingLine(line)
        when {
            paragraph.isNotEmpty() && activeLooksStructural -> {
                flushParagraphBlock()
                trimmed.ifEmpty { null }
            }
            paragraph.isNotEmpty() -> buildString {
                append(paragraph.toString())
                if (trimmed.isNotBlank()) {
                    if (isNotEmpty()) {
                        appendRendererParagraphLine(trimmed)
                    } else {
                        append(trimmed)
                    }
                }
            }.trim().ifEmpty { null }
            trimmed.isBlank() -> null
            else -> trimmed
        }
    } ?: paragraph.toString().trim().ifEmpty { null }

    return StreamingBlockState(
        completedBlocks = completedBlocks,
        activeBlock = activeBlock
    )
}

internal fun classifyStreamingLine(line: String): StreamingLineModel {
    if (line.isBlank()) return StreamingLineModel.Blank
    val trimmed = line.trimStart()
    return when {
        trimmed.matches(rendererHeadingRegex) -> {
            val marker = trimmed.takeWhile { it == '#' }
            StreamingLineModel.Heading(marker.length, trimmed.drop(marker.length).trimStart())
        }
        trimmed.matches(rendererBulletRegex) -> StreamingLineModel.Bullet(trimmed.drop(1).trimStart())
        trimmed.matches(rendererNumberedRegex) -> StreamingLineModel.Numbered(
            number = trimmed.substringBefore('.'),
            text = trimmed.substringAfter('.').trimStart()
        )
        trimmed.matches(rendererQuoteRegex) -> StreamingLineModel.Quote(trimmed.drop(1).trimStart())
        else -> StreamingLineModel.Paragraph(line)
    }
}

internal fun classifyActiveStreamingLine(line: String): StreamingLineModel {
    if (line.isBlank()) return StreamingLineModel.Blank
    val trimmed = line.trimStart()
    val headingMarker = trimmed.takeWhile { it == '#' }
    if (headingMarker.isNotEmpty() && headingMarker.length <= 6) {
        val remainder = trimmed.drop(headingMarker.length)
        if (remainder.isEmpty() || remainder.first().isWhitespace()) {
            return StreamingLineModel.Heading(
                level = headingMarker.length,
                text = remainder.trimStart()
            )
        }
    }
    if (trimmed.startsWith(">")) {
        val remainder = trimmed.drop(1)
        if (remainder.isEmpty() || remainder.first().isWhitespace()) {
            return StreamingLineModel.Quote(remainder.trimStart())
        }
    }
    if (trimmed.startsWith("-") || trimmed.startsWith("*")) {
        val remainder = trimmed.drop(1)
        if (remainder.isEmpty() || remainder.first().isWhitespace()) {
            return StreamingLineModel.Bullet(remainder.trimStart())
        }
    }
    val numberedPrefix = trimmed.takeWhile { it.isDigit() }
    if (numberedPrefix.isNotEmpty() && trimmed.drop(numberedPrefix.length).startsWith(".")) {
        val remainder = trimmed.drop(numberedPrefix.length + 1)
        if (remainder.isEmpty() || remainder.first().isWhitespace()) {
            return StreamingLineModel.Numbered(
                number = numberedPrefix,
                text = remainder.trimStart()
            )
        }
    }
    return StreamingLineModel.Paragraph(line)
}

internal fun shouldShowStreamingSectionDivider(
    previous: StreamingLineModel?,
    current: StreamingLineModel
): Boolean {
    val heading = current as? StreamingLineModel.Heading ?: return false
    return previous != null && heading.level <= 2
}

internal fun buildStableStreamingLineBuffer(
    text: AnnotatedString,
    style: TextStyle,
    availableWidthPx: Int,
    textMeasurer: TextMeasurer
): StreamingRenderedLines {
    if (text.isEmpty()) return StreamingRenderedLines(emptyList(), null)
    if (availableWidthPx <= 0) return StreamingRenderedLines(emptyList(), text)
    val raw = text.text
    val layout = textMeasurer.measure(
        text = text,
        style = style,
        constraints = Constraints(maxWidth = availableWidthPx)
    )
    if (layout.lineCount <= 0) {
        return StreamingRenderedLines(
            stableLines = emptyList(),
            activeLine = if (raw.endsWith('\n')) AnnotatedString("") else text
        )
    }

    val stableLines = buildList {
        for (lineIndex in 0 until (layout.lineCount - 1)) {
            val lineStart = layout.getLineStart(lineIndex).coerceIn(0, raw.length)
            val lineEnd = layout.getLineEnd(lineIndex, visibleEnd = true).coerceIn(lineStart, raw.length)
            add(text.subSequence(lineStart, lineEnd))
        }
    }
    val activeLine = when {
        raw.endsWith('\n') -> AnnotatedString("")
        else -> {
            val activeIndex = layout.lineCount - 1
            val lineStart = layout.getLineStart(activeIndex).coerceIn(0, raw.length)
            val lineEnd = layout.getLineEnd(activeIndex, visibleEnd = true).coerceIn(lineStart, raw.length)
            if (lineStart >= lineEnd) null else text.subSequence(lineStart, lineEnd)
        }
    }

    return StreamingRenderedLines(stableLines = stableLines, activeLine = activeLine)
}

private fun ensureStreamingMessageId(
    currentMessageId: String?,
    anchoredUserMessageId: String?,
    assistantIdProvider: (String) -> String,
    fallbackIdProvider: () -> String
): String {
    return currentMessageId
        ?: anchoredUserMessageId?.let(assistantIdProvider)
        ?: fallbackIdProvider()
}

private fun Char.isRendererCjkUnifiedIdeograph(): Boolean {
    val code = code
    return code in 0x4E00..0x9FFF ||
        code in 0x3400..0x4DBF ||
        code in 0x20000..0x2A6DF ||
        code in 0x2A700..0x2B73F ||
        code in 0x2B740..0x2B81F ||
        code in 0x2B820..0x2CEAF
}

private fun Char.isRendererStrongPausePunctuation(): Boolean = this in setOf('。', '！', '？', '!', '?', ';', '；', ':', '：')

private fun Char.isRendererWeakPausePunctuation(): Boolean = this in setOf('，', ',', '、', '·')

private fun Char.isRendererStructuralMarkdownChar(): Boolean = this == '#' || this == '-' || this == '*' || this == '>' || this == '`'

private fun takeRendererMarkdownPrefixToken(buffer: String): String? {
    if (buffer.isEmpty()) return null
    return when {
        buffer.startsWith("```") -> "```"
        buffer.startsWith("## ") -> "## "
        buffer.startsWith("# ") -> "# "
        buffer.startsWith("- ") -> "- "
        buffer.startsWith("* ") -> "* "
        buffer.startsWith("> ") -> "> "
        else -> {
            val digits = buffer.takeWhile { it.isDigit() }
            if (digits.isNotEmpty() && buffer.drop(digits.length).startsWith(". ")) {
                "$digits. "
            } else {
                null
            }
        }
    }
}

private fun takeRendererTypewriterToken(buffer: String): String {
    if (buffer.isEmpty()) return ""
    val first = buffer.first()
    val markdownPrefix = takeRendererMarkdownPrefixToken(buffer)
    if (markdownPrefix != null) return markdownPrefix
    if (first == '\n') return "\n"
    if (first.isRendererStructuralMarkdownChar()) {
        return buffer.takeWhile { it.isRendererStructuralMarkdownChar() }.ifEmpty { first.toString() }
    }
    if (first.isRendererCjkUnifiedIdeograph()) {
        return first.toString()
    }
    if (first.isWhitespace()) {
        return buffer.takeWhile { it.isWhitespace() && it != '\n' }.ifEmpty { first.toString() }
    }

    val token = StringBuilder()
    for (ch in buffer) {
        if (token.isEmpty()) {
            token.append(ch)
            if (ch.isRendererWeakPausePunctuation() || ch.isRendererStrongPausePunctuation()) {
                break
            }
            continue
        }
        if (
            ch.isWhitespace() ||
            ch.isRendererCjkUnifiedIdeograph() ||
            ch.isRendererStructuralMarkdownChar() ||
            ch.isRendererWeakPausePunctuation() ||
            ch.isRendererStrongPausePunctuation()
        ) {
            break
        }
        token.append(ch)
    }
    return token.toString()
}

private fun scaleRendererStreamingDelay(delayMs: Long): Long {
    val scale = if (BuildConfig.DEBUG) 0.72f else 1f
    return max(6L, (delayMs * scale).toLong())
}

private fun hasRendererStructuralMarkdownPrefix(text: String): Boolean {
    val trimmed = text.trimStart()
    return trimmed.startsWith("#") ||
        trimmed.startsWith("- ") ||
        trimmed.startsWith("* ") ||
        trimmed.startsWith("> ") ||
        rendererNumberedRegex.matches(trimmed)
}

private fun resolveRendererTypewriterDelay(token: String, remainingBuffer: String): Long {
    val lastChar = token.lastOrNull() ?: return STREAM_TYPEWRITER_IDLE_POLL_MS
    val baseDelay = when {
        lastChar == '\n' -> if (hasRendererStructuralMarkdownPrefix(remainingBuffer)) 64L else 48L
        lastChar.isRendererStrongPausePunctuation() -> 28L
        lastChar.isRendererWeakPausePunctuation() -> 16L
        token.length >= 7 -> 8L
        token.length >= 5 -> 10L
        token.length >= 3 -> 12L
        token.length == 2 -> 13L
        else -> if (lastChar.isRendererCjkUnifiedIdeograph()) 14L else 12L
    }
    return scaleRendererStreamingDelay(baseDelay)
}

private fun nextStreamingTypewriterStep(buffer: String): StreamingTypewriterStep {
    if (buffer.isEmpty()) {
        return StreamingTypewriterStep("", STREAM_TYPEWRITER_IDLE_POLL_MS)
    }
    val text = takeRendererTypewriterToken(buffer)
    val remaining = buffer.drop(text.length)
    return StreamingTypewriterStep(
        text = text,
        delayMs = resolveRendererTypewriterDelay(text, remaining)
    )
}

private fun splitRendererStreamingLogicalLines(content: String): StreamingLogicalLines {
    val normalized = content.replace("\r\n", "\n")
    if (normalized.isEmpty()) return StreamingLogicalLines(emptyList(), null)

    val lines = mutableListOf<String>()
    var start = 0
    normalized.forEachIndexed { index, ch ->
        if (ch == '\n') {
            lines += normalized.substring(start, index)
            start = index + 1
        }
    }

    return if (normalized.last() == '\n') {
        lines += ""
        StreamingLogicalLines(completedLines = lines, activeLine = null)
    } else {
        StreamingLogicalLines(
            completedLines = lines,
            activeLine = normalized.substring(start)
        )
    }
}

private fun isStructuralRendererStreamingLine(trimmed: String): Boolean {
    return trimmed.matches(rendererHeadingRegex) ||
        trimmed.matches(rendererBulletRegex) ||
        trimmed.matches(rendererNumberedRegex) ||
        trimmed.matches(rendererQuoteRegex)
}

private fun isStructuralRendererActiveStreamingLine(line: String): Boolean {
    return when (classifyActiveStreamingLine(line)) {
        StreamingLineModel.Blank,
        is StreamingLineModel.Paragraph -> false
        is StreamingLineModel.Heading,
        is StreamingLineModel.Bullet,
        is StreamingLineModel.Numbered,
        is StreamingLineModel.Quote -> true
    }
}

private fun needsRendererParagraphJoinSpace(previous: Char, next: Char): Boolean {
    if (previous.isWhitespace() || next.isWhitespace()) return false
    if (previous.isRendererCjkUnifiedIdeograph() || next.isRendererCjkUnifiedIdeograph()) return false
    if (previous.isRendererWeakPausePunctuation() || previous.isRendererStrongPausePunctuation()) return true
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
        val hitPause = tail == '\n' || tail?.isRendererStrongPausePunctuation() == true
        if (hitPause || consumedDelay >= STREAM_REVEAL_FRAME_BUDGET_MS) {
            break
        }
    }

    return StreamingRevealBatch(
        text = text.toString(),
        delayMs = consumedDelay.coerceAtLeast(STREAM_TYPEWRITER_IDLE_POLL_MS)
    )
}

private fun StringBuilder.appendRendererParagraphLine(line: String) {
    if (line.isBlank()) return
    if (isNotEmpty()) {
        val previous = this[lastIndex]
        val next = line.first()
        if (needsRendererParagraphJoinSpace(previous, next)) {
            append(' ')
        }
    }
    append(line)
}

@Composable
internal fun ChatStreamingRenderer(
    content: String,
    renderMode: StreamingRenderMode,
    revealMode: StreamingRevealMode,
    freshSuffixEnabled: Boolean,
    showWaitingBall: Boolean,
    streamingFreshStart: Int,
    streamingFreshEnd: Int,
    streamingFreshTick: Int,
    streamingLineAdvanceTick: Int,
    selectionEnabled: Boolean,
    showDisclaimer: Boolean,
    onStreamingContentBoundsChanged: ((Rect?) -> Unit)?,
    onWaitingAnchorBoundsChanged: ((Rect?) -> Unit)? = null,
    expandToFullWidth: Boolean = true,
    modifier: Modifier = Modifier
) {
    RendererAssistantMessageContentImpl(
        content = content,
        isStreaming = renderMode != StreamingRenderMode.Settled,
        streamingFreshStart = streamingFreshStart,
        streamingFreshEnd = streamingFreshEnd,
        streamingFreshTick = if (freshSuffixEnabled) streamingFreshTick else 0,
        streamingLineAdvanceTick = streamingLineAdvanceTick,
        showWaitingBall = showWaitingBall,
        strictLineReveal = renderMode != StreamingRenderMode.Settled &&
            revealMode != StreamingRevealMode.Free,
        lineRevealLocked = revealMode == StreamingRevealMode.Conservative,
        selectionEnabled = selectionEnabled,
        showDisclaimer = showDisclaimer,
        onStreamingContentBoundsChanged = onStreamingContentBoundsChanged,
        onWaitingAnchorBoundsChanged = onWaitingAnchorBoundsChanged,
        expandToFullWidth = expandToFullWidth,
        modifier = modifier
    )
}

@Composable
private fun RendererAssistantMessageContentImpl(
    content: String,
    isStreaming: Boolean,
    streamingFreshStart: Int = -1,
    streamingFreshEnd: Int = -1,
    streamingFreshTick: Int = 0,
    streamingLineAdvanceTick: Int = 0,
    showWaitingBall: Boolean = false,
    strictLineReveal: Boolean = false,
    lineRevealLocked: Boolean = false,
    selectionEnabled: Boolean = false,
    showDisclaimer: Boolean = true,
    onStreamingContentBoundsChanged: ((Rect?) -> Unit)? = null,
    onWaitingAnchorBoundsChanged: ((Rect?) -> Unit)? = null,
    expandToFullWidth: Boolean = true,
    modifier: Modifier = Modifier
) {
    val shouldRenderDisclaimer = remember(content, showDisclaimer) {
        showDisclaimer && shouldShowAiDisclaimerRefined(content)
    }
    val boundsReportingModifier = if (onStreamingContentBoundsChanged != null) {
        Modifier.onGloballyPositioned { coordinates ->
            onStreamingContentBoundsChanged.invoke(coordinates.boundsInWindow())
        }
    } else {
        Modifier
    }
    if (isStreaming) {
        val hostModifier = if (expandToFullWidth) {
            modifier
                .fillMaxWidth()
        } else {
            modifier
        }
        Box(
            modifier = hostModifier,
            contentAlignment = Alignment.TopStart
        ) {
            if (showWaitingBall || content.isBlank()) {
                Box(
                    modifier = Modifier
                        .then(boundsReportingModifier)
                        .fillMaxWidth()
                ) {
                    RendererAssistantStreamingWaitingIndicatorImpl(
                        onWaitingAnchorBoundsChanged = onWaitingAnchorBoundsChanged,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .then(boundsReportingModifier)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RendererAssistantStreamingContentImpl(
                                content = content,
                                streamingFreshStart = streamingFreshStart,
                                streamingFreshEnd = streamingFreshEnd,
                                streamingFreshTick = streamingFreshTick,
                                streamingLineAdvanceTick = streamingLineAdvanceTick,
                                strictLineReveal = strictLineReveal,
                                lineRevealLocked = lineRevealLocked,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    } else {
        Column(
            modifier = if (expandToFullWidth) {
                modifier
                    .then(boundsReportingModifier)
                    .fillMaxWidth()
            } else {
                modifier.then(boundsReportingModifier)
            },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (selectionEnabled) {
                SelectionContainer {
                    RendererAssistantMarkdownContentImpl(content = content)
                }
            } else {
                RendererAssistantMarkdownContentImpl(content = content)
            }
            if (shouldRenderDisclaimer) {
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
private fun RendererAssistantStreamingWaitingIndicatorImpl(
    onWaitingAnchorBoundsChanged: ((Rect?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val lineHeight = with(density) {
        assistantStreamingParagraphTextStyle().lineHeight.toDp()
    }
    val waitingShellMinHeight = lineHeight + ASSISTANT_WAITING_STABLE_SHELL_EXTRA_HEIGHT
    Box(
        modifier = modifier.heightIn(min = waitingShellMinHeight),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(lineHeight)
                .padding(start = GPT_BALL_START_PADDING),
            contentAlignment = Alignment.CenterStart
        ) {
            RendererGPTBreathingBallImpl(
                modifier = if (onWaitingAnchorBoundsChanged != null) {
                    Modifier.onGloballyPositioned { coordinates ->
                        onWaitingAnchorBoundsChanged.invoke(coordinates.boundsInWindow())
                    }
                } else {
                    Modifier
                }
            )
        }
    }
}

@Composable
private fun RendererAssistantStreamingContentImpl(
    content: String,
    streamingFreshStart: Int,
    streamingFreshEnd: Int,
    streamingFreshTick: Int,
    streamingLineAdvanceTick: Int,
    strictLineReveal: Boolean,
    lineRevealLocked: Boolean,
    modifier: Modifier = Modifier
) {
    val blockState = remember(content) { splitStreamingBlockState(content) }
    val completedModels = remember(blockState.completedBlocks) {
        blockState.completedBlocks.map(::classifyStreamingLine)
    }
    val activeModel = remember(blockState.activeBlock) {
        blockState.activeBlock?.let(::classifyActiveStreamingLine)
    }
    val activeFreshTailChars = remember(
        content,
        streamingFreshStart,
        streamingFreshEnd,
        streamingFreshTick,
        blockState.activeBlock
    ) {
        if (streamingFreshTick <= 0 || blockState.activeBlock.isNullOrEmpty()) {
            0
        } else {
            (streamingFreshEnd - streamingFreshStart)
                .coerceAtLeast(0)
                .coerceAtMost(blockState.activeBlock.length)
        }
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MARKDOWN_BLOCK_SPACING)
    ) {
        completedModels.forEachIndexed { index, model ->
            key("streaming_completed_$index:${blockState.completedBlocks[index]}") {
                RendererAssistantStreamingCommittedBlockImpl(
                    model = model,
                    showLeadingSectionDivider = shouldShowStreamingSectionDivider(
                        previous = completedModels.getOrNull(index - 1),
                        current = model
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        activeModel?.let { model ->
            RendererAssistantStreamingActiveBlockImpl(
                model = model,
                showLeadingSectionDivider = shouldShowStreamingSectionDivider(
                    previous = completedModels.lastOrNull(),
                    current = model
                ),
                freshTailChars = activeFreshTailChars,
                freshTick = streamingFreshTick,
                lineAdvanceTick = streamingLineAdvanceTick,
                strictLineReveal = strictLineReveal,
                lineRevealLocked = lineRevealLocked,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun RendererStreamingSingleActiveLineTextImpl(
    lines: StreamingRenderedLines,
    style: TextStyle,
    emptyLineHeight: Dp,
    freshTailChars: Int = 0,
    freshTick: Int = 0,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        lines.stableLines.forEach { line ->
            if (line.text.isEmpty()) {
                Spacer(modifier = Modifier.height(emptyLineHeight))
            } else {
                Text(
                    text = line,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = emptyLineHeight),
                    style = style,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        lines.activeLine?.let { line ->
            if (line.text.isEmpty()) {
                Spacer(modifier = Modifier.height(emptyLineHeight))
            } else {
                RendererStreamingAnimatedLineTextImpl(
                    text = line,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = emptyLineHeight),
                    style = style,
                    freshTailChars = freshTailChars,
                    freshTick = freshTick
                )
            }
        }
    }
}

@Composable
private fun RendererStreamingAnimatedLineTextImpl(
    text: AnnotatedString,
    style: TextStyle,
    freshTailChars: Int = 0,
    freshTick: Int = 0,
    modifier: Modifier = Modifier
) {
    val effectiveFreshTailChars = freshTailChars.coerceIn(0, text.length)
    if (effectiveFreshTailChars <= 0 || freshTick <= 0) {
        Text(
            text = text,
            modifier = modifier,
            style = style,
            textAlign = TextAlign.Start,
            maxLines = 1,
            softWrap = false
        )
        return
    }
    val highlightedTailChars = maxOf(
        effectiveFreshTailChars,
        STREAM_FRESH_SUFFIX_MIN_HIGHLIGHT_CHARS
    ).coerceAtMost(text.length)

    var freshRevealTarget by remember(freshTick) {
        mutableFloatStateOf(0f)
    }
    LaunchedEffect(freshTick) {
        freshRevealTarget = 1f
    }
    val freshRevealProgress by animateFloatAsState(
        targetValue = freshRevealTarget,
        animationSpec = tween(
            durationMillis = STREAM_FRESH_SUFFIX_HIGHLIGHT_MS,
            easing = LinearOutSlowInEasing
        ),
        label = "streamFreshSuffixReveal"
    )
    val renderedText = remember(text, highlightedTailChars, freshRevealProgress, style.color) {
        val stableEnd = (text.length - highlightedTailChars).coerceAtLeast(0)
        val baseColor = style.color
        val settledProgress = FastOutSlowInEasing.transform(freshRevealProgress)
        val freshColor =
            if (baseColor != Color.Unspecified) {
                lerp(STREAM_FRESH_SUFFIX_HIGHLIGHT_COLOR, baseColor, settledProgress)
            } else {
                Color.Unspecified
            }
        buildAnnotatedString {
            append(text.subSequence(0, stableEnd))
            withStyle(SpanStyle(color = freshColor)) {
                append(text.subSequence(stableEnd, text.length))
            }
        }
    }
    Text(
        text = renderedText,
        modifier = modifier,
        style = style,
        textAlign = TextAlign.Start,
        maxLines = 1,
        softWrap = false
    )
}

@Composable
private fun rememberRendererLockedStreamingRenderedLinesImpl(
    lines: StreamingRenderedLines,
    strictLineReveal: Boolean,
    lineRevealLocked: Boolean,
    lineAdvanceTick: Int,
    maxVisibleCharsWhenLocked: Int
): StreamingRenderedLines {
    if (!strictLineReveal || lines.activeLine == null || lines.stableLines.isEmpty()) {
        return lines
    }
    var activeLineUnlockedOnce by remember(lines.stableLines.size) { mutableStateOf(false) }
    var pendingFreshLineRelease by remember(lines.stableLines.size) { mutableStateOf(true) }
    var pendingFreshLineTick by remember(lines.stableLines.size) { mutableIntStateOf(lineAdvanceTick) }

    LaunchedEffect(lines.stableLines.size) {
        pendingFreshLineRelease = true
        pendingFreshLineTick = lineAdvanceTick
        activeLineUnlockedOnce = false
    }

    LaunchedEffect(lines.stableLines.size, lineRevealLocked, lineAdvanceTick) {
        if (activeLineUnlockedOnce) return@LaunchedEffect
        if (!pendingFreshLineRelease) {
            if (!lineRevealLocked) activeLineUnlockedOnce = true
            return@LaunchedEffect
        }
        if (lineRevealLocked) return@LaunchedEffect
        val settleFrames = if (lineAdvanceTick > pendingFreshLineTick) {
            STREAM_FRESH_LINE_AFTER_FOLLOW_SETTLE_FRAMES
        } else {
            STREAM_FRESH_LINE_SETTLE_FRAMES
        }
        repeat(settleFrames) { withFrameNanos { } }
        if (!lineRevealLocked) {
            pendingFreshLineRelease = false
            activeLineUnlockedOnce = true
        }
    }

    return remember(
        lines,
        lineRevealLocked,
        lineAdvanceTick,
        activeLineUnlockedOnce,
        pendingFreshLineRelease,
        maxVisibleCharsWhenLocked
    ) {
        when {
            activeLineUnlockedOnce -> lines
            pendingFreshLineRelease || lineRevealLocked -> lines.copy(
                activeLine = buildLockedStreamingActivePreview(
                    activeLine = lines.activeLine,
                    maxVisibleChars = maxVisibleCharsWhenLocked
                )
            )
            else -> lines
        }
    }
}

@Composable
private fun RendererStreamingCommittedTextBlockImpl(
    text: String,
    style: TextStyle,
    emptyLineHeight: Dp,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        val measured = remember(text, style, maxWidthPx) {
            val lines = buildStableStreamingLineBuffer(
                text = AnnotatedString(text),
                style = style,
                availableWidthPx = maxWidthPx,
                textMeasurer = textMeasurer
            )
            StreamingRenderedLines(
                stableLines = if (lines.activeLine != null) lines.stableLines + lines.activeLine else lines.stableLines,
                activeLine = null
            )
        }
        RendererStreamingSingleActiveLineTextImpl(
            lines = measured,
            style = style,
            emptyLineHeight = emptyLineHeight,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RendererAssistantStreamingCommittedBlockImpl(
    model: StreamingLineModel,
    showLeadingSectionDivider: Boolean = false,
    modifier: Modifier = Modifier
) {
    @Composable
    fun RichText(text: String, modifier: Modifier = Modifier, style: TextStyle) {
        val annotated = remember(text) { getCachedAnnotatedString(text) }
        Text(
            text = annotated,
            modifier = modifier,
            style = style,
            textAlign = TextAlign.Start
        )
    }

    when (model) {
        StreamingLineModel.Blank -> Spacer(modifier = modifier.height(MARKDOWN_BLOCK_SPACING))
        is StreamingLineModel.Heading -> Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            if (showLeadingSectionDivider && model.level <= 2) {
                RendererMarkdownSectionDividerImpl()
            }
            RichText(
                text = model.text,
                modifier = Modifier.fillMaxWidth(),
                style = assistantStreamingHeadingTextStyle(model.level)
            )
        }
        is StreamingLineModel.Bullet -> Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "\u2022",
                style = assistantStreamingParagraphTextStyle().copy(fontSize = 18.sp)
            )
            RichText(
                text = model.text,
                modifier = Modifier.weight(1f),
                style = assistantStreamingParagraphTextStyle()
            )
        }
        is StreamingLineModel.Numbered -> Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${model.number}.",
                style = assistantStreamingParagraphTextStyle().copy(fontWeight = FontWeight.SemiBold)
            )
            RichText(
                text = model.text,
                modifier = Modifier.weight(1f),
                style = assistantStreamingParagraphTextStyle()
            )
        }
        is StreamingLineModel.Quote -> RichText(
            text = model.text,
            modifier = modifier.fillMaxWidth(),
            style = assistantStreamingParagraphTextStyle()
        )
        is StreamingLineModel.Paragraph -> RichText(
            text = model.text,
            modifier = modifier.fillMaxWidth(),
            style = assistantStreamingParagraphTextStyle()
        )
    }
}

@Composable
private fun RendererMarkdownSectionDividerImpl() {
    Spacer(modifier = Modifier.height(SECTION_DIVIDER_TOP_EXTRA_GAP))
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth(),
        thickness = 1.dp,
        color = Color(0xFFE7E9ED)
    )
    Spacer(modifier = Modifier.height(SECTION_DIVIDER_GAP))
}

@Composable
private fun RendererAssistantStreamingActiveBlockImpl(
    model: StreamingLineModel,
    showLeadingSectionDivider: Boolean = false,
    freshTailChars: Int = 0,
    freshTick: Int = 0,
    lineAdvanceTick: Int = 0,
    strictLineReveal: Boolean = true,
    lineRevealLocked: Boolean = false,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val spacingPx = with(density) { 8.dp.roundToPx() }
    val paragraphLineHeight = with(density) { assistantStreamingParagraphTextStyle().lineHeight.toDp() }
    fun resolveRenderedLines(text: String, style: TextStyle, availableWidthPx: Int): StreamingRenderedLines {
        return buildStableStreamingLineBuffer(
            text = AnnotatedString(text),
            style = style,
            availableWidthPx = availableWidthPx,
            textMeasurer = textMeasurer
        )
    }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        when (model) {
            StreamingLineModel.Blank -> Unit
            is StreamingLineModel.Heading -> {
                val headingStyle = assistantStreamingHeadingTextStyle(model.level)
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    if (showLeadingSectionDivider && model.level <= 2) RendererMarkdownSectionDividerImpl()
                    val rawLines = remember(model.text, maxWidthPx) {
                        resolveRenderedLines(model.text, headingStyle, maxWidthPx)
                    }
                    val lines = rememberRendererLockedStreamingRenderedLinesImpl(
                        lines = rawLines,
                        strictLineReveal = strictLineReveal,
                        lineRevealLocked = lineRevealLocked,
                        lineAdvanceTick = lineAdvanceTick,
                        maxVisibleCharsWhenLocked = 0
                    )
                    RendererStreamingSingleActiveLineTextImpl(
                        lines = lines,
                        modifier = Modifier.fillMaxWidth(),
                        style = headingStyle,
                        emptyLineHeight = with(density) { headingStyle.lineHeight.toDp() },
                        freshTailChars = freshTailChars,
                        freshTick = freshTick
                    )
                }
            }
            is StreamingLineModel.Bullet -> {
                val bulletStyle = assistantStreamingParagraphTextStyle().copy(fontSize = 18.sp)
                val bodyStyle = assistantStreamingParagraphTextStyle()
                val bulletWidthPx = remember(textMeasurer, bulletStyle) {
                    textMeasurer.measure(AnnotatedString("\u2022"), style = bulletStyle).size.width
                }
                val bodyWidthPx = (maxWidthPx - bulletWidthPx - spacingPx).coerceAtLeast(0)
                val gutterWidth = with(density) { (bulletWidthPx + spacingPx).toDp() }
                val rawLines = remember(model.text, bodyWidthPx) { resolveRenderedLines(model.text, bodyStyle, bodyWidthPx) }
                val lines = rememberRendererLockedStreamingRenderedLinesImpl(
                    lines = rawLines,
                    strictLineReveal = strictLineReveal,
                    lineRevealLocked = lineRevealLocked,
                    lineAdvanceTick = lineAdvanceTick,
                    maxVisibleCharsWhenLocked = 0
                )
                RendererStreamingBulletOrNumberedBlockImpl(
                    leading = { Text(text = "\u2022", style = bulletStyle) },
                    gutterWidth = gutterWidth,
                    bodyStyle = bodyStyle,
                    paragraphLineHeight = paragraphLineHeight,
                    lines = lines,
                    freshTailChars = freshTailChars,
                    freshTick = freshTick
                )
            }
            is StreamingLineModel.Numbered -> {
                val numberStyle = assistantStreamingParagraphTextStyle().copy(fontWeight = FontWeight.SemiBold)
                val bodyStyle = assistantStreamingParagraphTextStyle()
                val numberWidthPx = remember(textMeasurer, model.number, numberStyle) {
                    textMeasurer.measure(AnnotatedString("${model.number}."), style = numberStyle).size.width
                }
                val bodyWidthPx = (maxWidthPx - numberWidthPx - spacingPx).coerceAtLeast(0)
                val gutterWidth = with(density) { (numberWidthPx + spacingPx).toDp() }
                val rawLines = remember(model.text, bodyWidthPx) { resolveRenderedLines(model.text, bodyStyle, bodyWidthPx) }
                val lines = rememberRendererLockedStreamingRenderedLinesImpl(
                    lines = rawLines,
                    strictLineReveal = strictLineReveal,
                    lineRevealLocked = lineRevealLocked,
                    lineAdvanceTick = lineAdvanceTick,
                    maxVisibleCharsWhenLocked = 0
                )
                RendererStreamingBulletOrNumberedBlockImpl(
                    leading = { Text(text = "${model.number}.", style = numberStyle) },
                    gutterWidth = gutterWidth,
                    bodyStyle = bodyStyle,
                    paragraphLineHeight = paragraphLineHeight,
                    lines = lines,
                    freshTailChars = freshTailChars,
                    freshTick = freshTick
                )
            }
            is StreamingLineModel.Quote -> {
                val quoteStyle = assistantStreamingParagraphTextStyle()
                val rawLines = remember(model.text, maxWidthPx) { resolveRenderedLines(model.text, quoteStyle, maxWidthPx) }
                val lines = rememberRendererLockedStreamingRenderedLinesImpl(
                    lines = rawLines,
                    strictLineReveal = strictLineReveal,
                    lineRevealLocked = lineRevealLocked,
                    lineAdvanceTick = lineAdvanceTick,
                    maxVisibleCharsWhenLocked = 0
                )
                RendererStreamingSingleActiveLineTextImpl(
                    lines = lines,
                    modifier = Modifier.fillMaxWidth(),
                    style = quoteStyle,
                    emptyLineHeight = paragraphLineHeight,
                    freshTailChars = freshTailChars,
                    freshTick = freshTick
                )
            }
            is StreamingLineModel.Paragraph -> {
                val paragraphStyle = assistantStreamingParagraphTextStyle()
                val rawLines = remember(model.text, maxWidthPx) { resolveRenderedLines(model.text, paragraphStyle, maxWidthPx) }
                val lines = rememberRendererLockedStreamingRenderedLinesImpl(
                    lines = rawLines,
                    strictLineReveal = strictLineReveal,
                    lineRevealLocked = lineRevealLocked,
                    lineAdvanceTick = lineAdvanceTick,
                    maxVisibleCharsWhenLocked = 0
                )
                RendererStreamingSingleActiveLineTextImpl(
                    lines = lines,
                    modifier = Modifier.fillMaxWidth(),
                    style = paragraphStyle,
                    emptyLineHeight = paragraphLineHeight,
                    freshTailChars = freshTailChars,
                    freshTick = freshTick
                )
            }
        }
    }
}

@Composable
private fun RendererStreamingBulletOrNumberedBlockImpl(
    leading: @Composable () -> Unit,
    gutterWidth: Dp,
    bodyStyle: TextStyle,
    paragraphLineHeight: Dp,
    lines: StreamingRenderedLines,
    freshTailChars: Int,
    freshTick: Int
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        if (lines.activeLine != null || lines.stableLines.isNotEmpty()) {
            val firstLine = lines.stableLines.firstOrNull() ?: lines.activeLine
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = paragraphLineHeight),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                leading()
                firstLine?.let { line ->
                    RendererStreamingAnimatedLineTextImpl(
                        text = line,
                        modifier = Modifier.weight(1f),
                        style = bodyStyle,
                        freshTailChars = if (lines.stableLines.isEmpty()) freshTailChars else 0,
                        freshTick = if (lines.stableLines.isEmpty()) freshTick else 0
                    )
                }
            }
            lines.stableLines.drop(1).forEach { line ->
                Row(modifier = Modifier.fillMaxWidth().heightIn(min = paragraphLineHeight)) {
                    Spacer(modifier = Modifier.width(gutterWidth))
                    Text(
                        text = line,
                        modifier = Modifier.weight(1f),
                        style = bodyStyle,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            if (lines.stableLines.isNotEmpty()) {
                lines.activeLine?.let { line ->
                    Row(modifier = Modifier.fillMaxWidth().heightIn(min = paragraphLineHeight)) {
                        Spacer(modifier = Modifier.width(gutterWidth))
                        RendererStreamingAnimatedLineTextImpl(
                            text = line,
                            modifier = Modifier.weight(1f),
                            style = bodyStyle,
                            freshTailChars = freshTailChars,
                            freshTick = freshTick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RendererAssistantMarkdownContentImpl(content: String, modifier: Modifier = Modifier) {
    val blockState = remember(content) { splitStreamingBlockState(content) }
    val completedModels = remember(blockState) {
        buildList {
            addAll(blockState.completedBlocks.map(::classifyStreamingLine))
            blockState.activeBlock?.let { add(classifyStreamingLine(it)) }
        }
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MARKDOWN_BLOCK_SPACING)
    ) {
        completedModels.forEachIndexed { index, model ->
            key("markdown_completed_$index:${model.hashCode()}") {
                val showLeadingSectionDivider = shouldShowStreamingSectionDivider(
                    previous = completedModels.getOrNull(index - 1),
                    current = model
                )
                if (index == completedModels.lastIndex) {
                    RendererAssistantStreamingActiveBlockImpl(
                        model = model,
                        showLeadingSectionDivider = showLeadingSectionDivider,
                        lineAdvanceTick = 0,
                        strictLineReveal = false,
                        lineRevealLocked = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    RendererAssistantStreamingCommittedBlockImpl(
                        model = model,
                        showLeadingSectionDivider = showLeadingSectionDivider,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun RendererGPTBreathingBallImpl(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "assistantBreathingDot")
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
        Canvas(modifier = Modifier.size(GPT_BALL_CONTAINER_SIZE)) {
            val baseRadius = GPT_BALL_SIZE.toPx() / 2f
            val radius = baseRadius * scale
            drawCircle(
                color = Color.Black,
                radius = radius,
                center = this.center
            )
        }
    }
}
