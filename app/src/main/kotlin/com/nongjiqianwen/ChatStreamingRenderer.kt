package com.nongjiqianwen

import android.os.Handler
import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal enum class StreamingRenderMode {
    Waiting,
    Streaming,
    Settled
}

internal data class ChatStreamingRuntimeState(
    val isStreaming: MutableState<Boolean>,
    val streamingMessageId: MutableState<String?>,
    val streamingMessageContent: MutableState<String>,
    val streamingRevealBuffer: MutableState<String>,
    val streamRevealJob: MutableState<kotlinx.coroutines.Job?>,
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
    onAdvance: suspend (StreamingRevealAdvance) -> Unit
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
                delay(advance.delayMs)
            }
        }
    )
}

internal fun appendAssistantChunk(
    piece: String,
    mainHandler: Handler,
    isStreaming: () -> Boolean,
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
        if (!isStreaming()) return@post
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

private fun splitRendererMarkdownTableCells(line: String): List<String> {
    val trimmed = line.trim().removePrefix("|").removeSuffix("|")
    if (trimmed.isBlank()) return emptyList()
    return trimmed.split('|').map { it.trim() }
}

private fun isRendererMarkdownTableSeparatorLine(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.contains('|')) return false
    val normalized = trimmed.replace("|", "").replace(" ", "")
    return normalized.isNotEmpty() && normalized.all { it == '-' || it == ':' }
}

private fun looksLikeRendererMarkdownTableRow(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isBlank() || !trimmed.contains('|')) return false
    if (isRendererMarkdownTableSeparatorLine(trimmed)) return false
    return splitRendererMarkdownTableCells(trimmed).size >= 2
}

private fun convertRendererMarkdownTableBlock(headerLine: String, rowLines: List<String>): List<String> {
    val headers = splitRendererMarkdownTableCells(headerLine)
        .mapIndexed { index, cell -> cell.ifBlank { "列${index + 1}" } }
    if (headers.isEmpty()) return emptyList()
    return rowLines.mapNotNull { rowLine ->
        val values = splitRendererMarkdownTableCells(rowLine)
        if (values.isEmpty()) {
            null
        } else {
            val pairs = headers.mapIndexedNotNull { index, header ->
                val value = values.getOrNull(index)?.trim().orEmpty()
                if (value.isBlank()) null else "$header：$value"
            }
            when {
                pairs.isNotEmpty() -> "- ${pairs.joinToString("；")}"
                else -> "- ${values.joinToString(" | ").trim()}"
            }
        }
    }
}

private fun normalizeRendererMarkdownTables(content: String): String {
    val normalized = content.replace("\r\n", "\n")
    if (!normalized.contains('|')) return normalized
    val lines = normalized.lines()
    if (lines.isEmpty()) return normalized
    val result = mutableListOf<String>()
    var index = 0
    var inCodeFence = false
    while (index < lines.size) {
        val current = lines[index]
        val trimmed = current.trimStart()
        if (trimmed.startsWith("```")) {
            inCodeFence = !inCodeFence
            result += current
            index++
            continue
        }
        if (inCodeFence) {
            result += current
            index++
            continue
        }
        if (
            index + 1 < lines.size &&
            looksLikeRendererMarkdownTableRow(current) &&
            isRendererMarkdownTableSeparatorLine(lines[index + 1])
        ) {
            val rowLines = mutableListOf<String>()
            var cursor = index + 2
            while (cursor < lines.size && looksLikeRendererMarkdownTableRow(lines[cursor])) {
                rowLines += lines[cursor]
                cursor++
            }
            if (rowLines.isNotEmpty()) {
                result += convertRendererMarkdownTableBlock(current, rowLines)
                index = cursor
                continue
            }
        }
        result += current
        index++
    }
    return result.joinToString("\n")
}

internal data class StreamingTypewriterStep(
    val text: String,
    val delayMs: Long
)

internal data class StreamingRevealBatch(
    val text: String,
    val delayMs: Long
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

internal fun splitStreamingBlockState(content: String): StreamingBlockState {
    val logicalLines = splitRendererStreamingLogicalLines(normalizeRendererMarkdownTables(content))
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
        if (remainder.startsWith(" ") && remainder.trim().isNotEmpty()) {
            return StreamingLineModel.Heading(
                level = headingMarker.length,
                text = remainder.trimStart()
            )
        }
    }
    if (trimmed.startsWith("> ")) {
        val remainder = trimmed.drop(2)
        if (remainder.trim().isNotEmpty()) {
            return StreamingLineModel.Quote(remainder.trimStart())
        }
    }
    if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
        val remainder = trimmed.drop(2)
        if (remainder.trim().isNotEmpty()) {
            return StreamingLineModel.Bullet(remainder.trimStart())
        }
    }
    val numberedPrefix = trimmed.takeWhile { it.isDigit() }
    if (
        numberedPrefix.isNotEmpty() &&
        trimmed.drop(numberedPrefix.length).startsWith(". ")
    ) {
        val remainder = trimmed.drop(numberedPrefix.length + 2)
        if (remainder.trim().isNotEmpty()) {
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
    freshSuffixEnabled: Boolean,
    showWaitingBall: Boolean,
    streamingFreshStart: Int,
    streamingFreshEnd: Int,
    streamingFreshTick: Int,
    selectionEnabled: Boolean,
    showDisclaimer: Boolean,
    showLeadingSectionDivider: Boolean = false,
    onStreamingContentBoundsChanged: ((Rect?) -> Unit)?,
    expandToFullWidth: Boolean = true,
    modifier: Modifier = Modifier
) {
    RendererAssistantMessageContentImpl(
        content = content,
        isStreaming = renderMode != StreamingRenderMode.Settled,
        streamingFreshStart = streamingFreshStart,
        streamingFreshEnd = streamingFreshEnd,
        streamingFreshTick = if (freshSuffixEnabled) streamingFreshTick else 0,
        showWaitingBall = showWaitingBall,
        selectionEnabled = selectionEnabled,
        showDisclaimer = showDisclaimer,
        showLeadingSectionDivider = showLeadingSectionDivider,
        onStreamingContentBoundsChanged = onStreamingContentBoundsChanged,
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
    showWaitingBall: Boolean = false,
    selectionEnabled: Boolean = false,
    showDisclaimer: Boolean = true,
    showLeadingSectionDivider: Boolean = false,
    onStreamingContentBoundsChanged: ((Rect?) -> Unit)? = null,
    expandToFullWidth: Boolean = true,
    modifier: Modifier = Modifier
) {
    val shouldRenderDisclaimer = remember(content, showDisclaimer) {
        showDisclaimer && shouldShowAiDisclaimerRefined(content)
    }
    val disclaimerStyle = remember { assistantDisclaimerTextStyle() }
    val boundsReportingModifier = if (onStreamingContentBoundsChanged != null) {
        Modifier.onGloballyPositioned { coordinates ->
            onStreamingContentBoundsChanged.invoke(coordinates.boundsInWindow())
        }
    } else {
        Modifier
    }
    val hostModifier = if (expandToFullWidth) {
        modifier
            .then(boundsReportingModifier)
            .fillMaxWidth()
    } else {
        modifier.then(boundsReportingModifier)
    }
    Column(
        modifier = hostModifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isStreaming) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.BottomStart
            ) {
                RendererAssistantStreamingContentImpl(
                    content = content,
                    streamingFreshStart = streamingFreshStart,
                    streamingFreshEnd = streamingFreshEnd,
                    streamingFreshTick = streamingFreshTick,
                    showWaitingBall = showWaitingBall,
                    showLeadingSectionDivider = showLeadingSectionDivider,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (shouldRenderDisclaimer) {
                AssistantDisclaimerFooter(
                    style = disclaimerStyle,
                    visible = false
                )
            }
        } else {
            if (selectionEnabled) {
                SelectionContainer {
                    RendererAssistantMarkdownContentImpl(
                        content = content,
                        showLeadingSectionDivider = showLeadingSectionDivider
                    )
                }
            } else {
                RendererAssistantMarkdownContentImpl(
                    content = content,
                    showLeadingSectionDivider = showLeadingSectionDivider
                )
            }
            if (shouldRenderDisclaimer) {
                AssistantDisclaimerFooter(
                    style = disclaimerStyle,
                    visible = true
                )
            }
        }
    }
}

@Composable
private fun AssistantDisclaimerFooter(
    style: TextStyle,
    visible: Boolean
) {
    val visibilityModifier = if (visible) {
        Modifier
    } else {
        Modifier
            .alpha(0f)
            .clearAndSetSemantics { }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(visibilityModifier),
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = AI_DISCLAIMER_TEXT,
            style = style,
            textAlign = TextAlign.Start
        )
    }
}

@Composable
private fun RendererAssistantStreamingWaitingIndicatorImpl(
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val paragraphStyle = remember { assistantStreamingParagraphTextStyle() }
    val lineHeight = with(density) {
        paragraphStyle.lineHeight.toDp()
    }
    Box(
        modifier = modifier.heightIn(min = lineHeight),
        contentAlignment = Alignment.BottomStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = lineHeight)
                .padding(start = GPT_BALL_START_PADDING),
            contentAlignment = Alignment.BottomStart
        ) {
            RendererGPTBreathingBallImpl(
                modifier = Modifier
            )
        }
    }
}

private fun markdownBlockSpacingModifier(
    hasPreviousBlock: Boolean,
    modifier: Modifier = Modifier
): Modifier {
    return if (hasPreviousBlock) {
        modifier.padding(top = MARKDOWN_BLOCK_SPACING)
    } else {
        modifier
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun RendererAssistantStreamingContentImpl(
    content: String,
    streamingFreshStart: Int,
    streamingFreshEnd: Int,
    streamingFreshTick: Int,
    showWaitingBall: Boolean,
    showLeadingSectionDivider: Boolean,
    modifier: Modifier = Modifier
) {
    val blockState = remember(content) { splitStreamingBlockState(content) }
    val completedModels = remember(blockState.completedBlocks) {
        blockState.completedBlocks.map(::classifyStreamingLine)
    }
    val activeModel = remember(blockState.activeBlock) {
        blockState.activeBlock?.let(::classifyActiveStreamingLine)
    }
    val unifiedModels = remember(completedModels, activeModel) {
        buildList<StreamingLineModel> {
            addAll(completedModels)
            activeModel?.let { add(it) }
        }
    }
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        if (showLeadingSectionDivider) {
            RendererMarkdownSectionDividerImpl()
        }
        if (showWaitingBall && unifiedModels.isEmpty()) {
            RendererAssistantStreamingWaitingIndicatorImpl(
                modifier = Modifier.fillMaxWidth()
            )
        }
        unifiedModels.forEachIndexed { index, model ->
            if (index > 0) {
                Spacer(modifier = Modifier.height(MARKDOWN_BLOCK_SPACING))
            }
            // Streaming blocks are append-only in render order, so the absolute
            // block index is the stable shell key we want to preserve across
            // active -> committed transitions.
            key("streaming_unified_block_$index") {
                val blockLeadingDivider = shouldShowStreamingSectionDivider(
                    previous = unifiedModels.getOrNull(index - 1),
                    current = model
                )
                RendererAssistantStreamingUnifiedBlockHost(
                    model = model,
                    showLeadingSectionDivider = blockLeadingDivider,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun RendererAssistantStreamingUnifiedBlockHost(
    model: StreamingLineModel,
    showLeadingSectionDivider: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(0.dp)) {
        if (showLeadingSectionDivider) {
            RendererMarkdownSectionDividerImpl()
        }
        RendererAssistantStreamingActiveBlockImpl(
            model = model,
            showLeadingSectionDivider = false,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RendererStreamingActiveTextImpl(
    text: String,
    style: TextStyle,
    minLineHeight: Dp,
    modifier: Modifier = Modifier
) {
    if (text.isEmpty()) {
        Spacer(modifier = modifier.height(minLineHeight))
        return
    }
    val renderedText = remember(text) { getCachedAnnotatedString(text) }
    Text(
        text = renderedText,
        modifier = modifier.heightIn(min = minLineHeight),
        style = style,
        textAlign = TextAlign.Start,
        softWrap = true
    )
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
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val paragraphStyle = remember { assistantStreamingParagraphTextStyle() }
    val paragraphLineHeight = with(density) { paragraphStyle.lineHeight.toDp() }
    Box(modifier = modifier.fillMaxWidth()) {
        when (model) {
            StreamingLineModel.Blank -> Unit
            is StreamingLineModel.Heading -> {
                val headingStyle = remember(model.level) { assistantStreamingHeadingTextStyle(model.level) }
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    if (showLeadingSectionDivider && model.level <= 2) RendererMarkdownSectionDividerImpl()
                    RendererStreamingActiveTextImpl(
                        text = model.text,
                        modifier = Modifier.fillMaxWidth(),
                        style = headingStyle,
                        minLineHeight = with(density) { headingStyle.lineHeight.toDp() }
                    )
                }
            }
            is StreamingLineModel.Bullet -> {
                val bulletStyle = remember(paragraphStyle) { paragraphStyle.copy(fontSize = 18.sp) }
                val bodyStyle = paragraphStyle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "\u2022", style = bulletStyle)
                    RendererStreamingActiveTextImpl(
                        text = model.text,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = paragraphLineHeight),
                        style = bodyStyle,
                        minLineHeight = paragraphLineHeight
                    )
                }
            }
            is StreamingLineModel.Numbered -> {
                val numberStyle = remember(paragraphStyle) { paragraphStyle.copy(fontWeight = FontWeight.SemiBold) }
                val bodyStyle = paragraphStyle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "${model.number}.", style = numberStyle)
                    RendererStreamingActiveTextImpl(
                        text = model.text,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = paragraphLineHeight),
                        style = bodyStyle,
                        minLineHeight = paragraphLineHeight
                    )
                }
            }
            is StreamingLineModel.Quote -> {
                val quoteStyle = paragraphStyle
                RendererStreamingActiveTextImpl(
                    text = model.text,
                    modifier = Modifier.fillMaxWidth(),
                    style = quoteStyle,
                    minLineHeight = paragraphLineHeight
                )
            }
            is StreamingLineModel.Paragraph -> {
                RendererStreamingActiveTextImpl(
                    text = model.text,
                    modifier = Modifier.fillMaxWidth(),
                    style = paragraphStyle,
                    minLineHeight = paragraphLineHeight
                )
            }
        }
    }
}

@Composable
private fun RendererAssistantMarkdownContentImpl(
    content: String,
    modifier: Modifier = Modifier,
    showLeadingSectionDivider: Boolean = false
) {
    val blockState = remember(content) { splitStreamingBlockState(content) }
    val completedModels = remember(blockState) {
        buildList {
            addAll(blockState.completedBlocks.map(::classifyStreamingLine))
            blockState.activeBlock?.let { add(classifyStreamingLine(it)) }
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        if (showLeadingSectionDivider) {
            RendererMarkdownSectionDividerImpl()
        }
        completedModels.forEachIndexed { index, model ->
            key("markdown_completed_$index") {
                val blockLeadingDivider = shouldShowStreamingSectionDivider(
                    previous = completedModels.getOrNull(index - 1),
                    current = model
                )
                val blockModifier = markdownBlockSpacingModifier(
                    hasPreviousBlock = index > 0,
                    modifier = Modifier.fillMaxWidth()
                )
                RendererAssistantStreamingActiveBlockImpl(
                    model = model,
                    showLeadingSectionDivider = blockLeadingDivider,
                    modifier = blockModifier
                )
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
