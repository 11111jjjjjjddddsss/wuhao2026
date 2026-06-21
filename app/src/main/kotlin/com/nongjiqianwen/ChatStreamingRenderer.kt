package com.nongjiqianwen

import android.os.Handler
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
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
    val streamRevealJob: MutableState<kotlinx.coroutines.Job?>
)

@Composable
internal fun rememberChatStreamingRuntimeState(chatScopeId: String): ChatStreamingRuntimeState {
    val isStreaming = remember(chatScopeId) { mutableStateOf(false) }
    val streamingMessageId = remember(chatScopeId) { mutableStateOf<String?>(null) }
    val streamingMessageContent = remember(chatScopeId) { mutableStateOf("") }
    val streamingRevealBuffer = remember(chatScopeId) { mutableStateOf("") }
    val streamRevealJob = remember(chatScopeId) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    return remember(chatScopeId) {
        ChatStreamingRuntimeState(
            isStreaming = isStreaming,
            streamingMessageId = streamingMessageId,
            streamingMessageContent = streamingMessageContent,
            streamingRevealBuffer = streamingRevealBuffer,
            streamRevealJob = streamRevealJob
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
                    anchoredUserMessageId = anchoredUserMessageId(),
                    assistantIdProvider = assistantIdProvider,
                    fallbackIdProvider = fallbackIdProvider
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
private val rendererBulletRegex = Regex("^[-+*]\\s+.*$")
private val rendererNumberedRegex = Regex("^\\d+[.)]\\s+.*$")
private val rendererQuoteRegex = Regex("^>\\s+.*$")
private val rendererHorizontalRuleRegex = Regex("""^([-*_])(?:\s*\1){2,}\s*$""")
private val rendererChineseSectionHeadingRegex = Regex("^([一二三四五六七八九十]{1,3})([、.．])\\s*(.+)$")
private val rendererMarkdownImageRegex = Regex("!\\[([^\\]]*)]\\(([^)]+)\\)")
private val rendererMarkdownLinkRegex = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")
private val rendererBareUrlRegex = Regex("(?i)\\b((?:https?://|www\\.)[^\\s<>()]+)")
private const val RENDERER_TABLE_BLOCK_PREFIX = "\uE000NQ_TABLE:"
private const val RENDERER_TABLE_ROW_SEPARATOR = "\u001E"
private const val RENDERER_TABLE_CELL_SEPARATOR = "\u001F"
private const val RENDERER_INLINE_MARKDOWN_CACHE_LIMIT = 160
private const val RENDERER_MAX_INLINE_WORD_TOKEN_CHARS = 8
private const val GPT_THINKING_LABEL_DELAY_MS = 2600L
private const val GPT_THINKING_TRANSITION_MS = 180
private const val GPT_THINKING_SHIMMER_MS = 1600
private const val GPT_THINKING_SHIMMER_BAND_FRACTION = 0.68f

private val rendererSettledInlineMarkdownCache =
    object : LinkedHashMap<String, AnnotatedString>(RENDERER_INLINE_MARKDOWN_CACHE_LIMIT, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AnnotatedString>?): Boolean {
            return size > RENDERER_INLINE_MARKDOWN_CACHE_LIMIT
        }
    }

internal enum class RendererInlineMode {
    Streaming,
    Settled
}

internal data class RendererMarkdownTable(
    val headers: List<String>,
    val rows: List<List<String>>
)

internal fun RendererMarkdownTable.toPlainCopyText(): String {
    val columnCount = headers.size.coerceAtLeast(1)
    return buildString {
        append(headers.take(columnCount).joinToString("\t") { cell -> plainRendererInlineText(cell) })
        rows.forEach { row ->
            append('\n')
            append(
                (0 until columnCount).joinToString("\t") { index ->
                    plainRendererInlineText(row.getOrNull(index).orEmpty())
                }
            )
        }
    }
}

internal fun buildRendererMarkdownTableCopyText(table: RendererMarkdownTable): String =
    table.toReadableCopyText()

internal fun RendererMarkdownTable.toReadableCopyText(): String {
    val cleanHeaders = headers.map { plainRendererInlineText(it) }
    if (cleanHeaders.isEmpty()) return ""
    if (rows.isEmpty()) return cleanHeaders.joinToString(" / ").trim()

    val firstHeader = cleanHeaders.firstOrNull().orEmpty()
    val firstHeaderIsDimension = firstHeader in setOf("维度", "项目", "类别", "指标", "对比项")
    return rows.mapIndexed { rowIndex, row ->
        val cleanCells = cleanHeaders.indices.map { index ->
            plainRendererInlineText(row.getOrNull(index).orEmpty())
        }
        val rowTitle = cleanCells.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "第${rowIndex + 1}行"
        val lines = mutableListOf<String>()
        if (firstHeaderIsDimension) {
            lines += "$rowTitle："
        } else if (firstHeader.isNotBlank()) {
            lines += "$firstHeader：$rowTitle"
        } else {
            lines += rowTitle
        }
        cleanHeaders.drop(1).forEachIndexed { index, header ->
            val value = cleanCells.getOrNull(index + 1).orEmpty()
            if (header.isNotBlank() && value.isNotBlank()) {
                lines += "$header：$value"
            }
        }
        lines.joinToString("\n")
    }
        .joinToString("\n\n")
        .trim()
}

internal fun shouldEnableRendererMarkdownTableCopy(
    messageSettled: Boolean,
    inlineMode: RendererInlineMode
): Boolean = messageSettled && inlineMode == RendererInlineMode.Settled

internal data class RendererStructureStats(
    val blockCount: Int,
    val headingCount: Int,
    val tableCount: Int,
    val bulletCount: Int,
    val numberedCount: Int,
    val dividerHeadingCount: Int
)

private fun splitRendererMarkdownTableCells(line: String): List<String> {
    val trimmed = line.trim().removePrefix("|").removeSuffix("|")
    if (trimmed.isBlank()) return emptyList()
    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var escaped = false
    var inlineCodeTickCount = 0
    var index = 0
    while (index < trimmed.length) {
        val ch = trimmed[index]
        when {
            escaped -> {
                if (ch != '|') current.append('\\')
                current.append(ch)
                escaped = false
            }
            ch == '\\' -> escaped = true
            ch == '`' -> {
                val start = index
                while (index < trimmed.length && trimmed[index] == '`') {
                    index++
                }
                val tickCount = index - start
                if (inlineCodeTickCount == 0) {
                    inlineCodeTickCount = tickCount
                } else if (tickCount == inlineCodeTickCount) {
                    inlineCodeTickCount = 0
                }
                repeat(tickCount) { current.append('`') }
                continue
            }
            ch == '|' && inlineCodeTickCount > 0 -> current.append(ch)
            ch == '|' -> {
                cells += current.toString().trim()
                current.clear()
            }
            else -> current.append(ch)
        }
        index++
    }
    if (escaped) current.append('\\')
    cells += current.toString().trim()
    return cells
}

private fun isRendererMarkdownTableSeparatorLine(line: String): Boolean {
    val cells = splitRendererMarkdownTableCells(line)
    if (cells.size < 2) return false
    return cells.all { cell -> cell.matches(Regex(":?-{3,}:?")) }
}

private fun looksLikeRendererMarkdownTableRow(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isBlank() || !trimmed.contains('|')) return false
    if (isRendererMarkdownTableSeparatorLine(trimmed)) return false
    return splitRendererMarkdownTableCells(trimmed).size >= 2
}

private fun hasRendererMarkdownTableRowEdge(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed.startsWith("|") || trimmed.endsWith("|")
}

private fun looksLikeRendererMarkdownTableBodyRow(
    line: String,
    expectedColumnCount: Int,
    allowRowsWithoutEdge: Boolean
): Boolean {
    if (!looksLikeRendererMarkdownTableRow(line)) return false
    val cells = splitRendererMarkdownTableCells(line)
    if (hasRendererMarkdownTableRowEdge(line)) return true
    return allowRowsWithoutEdge && expectedColumnCount >= 2 && cells.size >= expectedColumnCount
}

private fun isRendererMarkdownTableBodyBlockBoundary(line: String): Boolean {
    if (isRendererIndentedCodeLine(line)) return true
    if (rendererMarkdownCodeFenceMarker(line) != null) return true
    if (hasRendererMarkdownTableRowEdge(line)) return false
    val trimmed = line.trimStart()
    return isRendererHorizontalRuleLine(trimmed) ||
        trimmed.startsWith(">") ||
        trimmed.matches(Regex("""#{1,6}\s+.+""")) ||
        trimmed.matches(Regex("""[-+*]\s+.+""")) ||
        trimmed.matches(Regex("""\d{1,9}[.)]\s+.+"""))
}

private fun rendererMarkdownCodeFenceMarker(line: String): String? {
    val trimmed = line.trimStart()
    return when {
        trimmed.startsWith("```") -> "```"
        trimmed.startsWith("~~~") -> "~~~"
        else -> null
    }
}

private fun isRendererIndentedCodeLine(line: String): Boolean =
    line.startsWith("    ") || line.startsWith("\t")

private fun normalizeRendererMarkdownTables(
    content: String,
    treatTrailingLineAsComplete: Boolean = true
): String {
    val normalized = content.replace("\r\n", "\n")
    if (!normalized.contains('|')) return normalized
    val lines = normalized.lines()
    if (lines.isEmpty()) return normalized
    val result = mutableListOf<String>()
    var index = 0
    var codeFenceMarker: String? = null
    while (index < lines.size) {
        val current = lines[index]
        val currentFenceMarker = rendererMarkdownCodeFenceMarker(current)
        if (codeFenceMarker == null && currentFenceMarker != null) {
            codeFenceMarker = currentFenceMarker
            result += current
            index++
            continue
        }
        if (codeFenceMarker != null) {
            if (currentFenceMarker == codeFenceMarker) {
                codeFenceMarker = null
            }
            result += current
            index++
            continue
        }
        if (isRendererIndentedCodeLine(current)) {
            result += current
            index++
            continue
        }
        if (
            index + 1 < lines.size &&
            looksLikeRendererMarkdownTableRow(current) &&
            isRendererMarkdownTableSeparatorLine(lines[index + 1])
        ) {
            val headerColumnCount = splitRendererMarkdownTableCells(current).size
            val separatorColumnCount = splitRendererMarkdownTableCells(lines[index + 1]).size
            if (headerColumnCount != separatorColumnCount || headerColumnCount < 2) {
                result += current
                index++
                continue
            }
            val rowLines = mutableListOf<String>()
            val expectedColumnCount = headerColumnCount
            val headerAndSeparatorAllowRowsWithoutEdge =
                !hasRendererMarkdownTableRowEdge(current) &&
                    !hasRendererMarkdownTableRowEdge(lines[index + 1])
            var bodyRowsWithoutEdgeMode = false
            var cursor = index + 2
            while (cursor < lines.size) {
                val isTrailingActiveLine =
                    !treatTrailingLineAsComplete && cursor == lines.lastIndex && !normalized.endsWith("\n")
                val lineLooksLikeBodyRow = !isRendererMarkdownTableBodyBlockBoundary(lines[cursor]) &&
                    looksLikeRendererMarkdownTableBodyRow(
                        line = lines[cursor],
                        expectedColumnCount = expectedColumnCount,
                        allowRowsWithoutEdge = headerAndSeparatorAllowRowsWithoutEdge ||
                            bodyRowsWithoutEdgeMode ||
                            (rowLines.isEmpty() && !hasRendererMarkdownTableRowEdge(lines[cursor]))
                    )
                if (!lineLooksLikeBodyRow) break
                if (isTrailingActiveLine && splitRendererMarkdownTableCells(lines[cursor]).size < 2) break
                if (!hasRendererMarkdownTableRowEdge(lines[cursor])) {
                    bodyRowsWithoutEdgeMode = true
                }
                rowLines += lines[cursor]
                cursor++
            }
            val tableBlock = encodeRendererMarkdownTableBlock(
                headerLine = current,
                separatorLine = lines[index + 1],
                rowLines = rowLines
            )
            if (tableBlock != null) {
                result += tableBlock
                index = cursor
                continue
            }
        }
        result += current
        index++
    }
    return result.joinToString("\n")
}

private fun normalizeRendererMarkdownTableCell(raw: String, fallback: String = ""): String {
    return raw
        .trim()
        .replace(RENDERER_TABLE_BLOCK_PREFIX, " ")
        .replace(RENDERER_TABLE_ROW_SEPARATOR, " ")
        .replace(RENDERER_TABLE_CELL_SEPARATOR, " ")
        .ifBlank { fallback }
}

private fun encodeRendererMarkdownTableBlock(
    headerLine: String,
    separatorLine: String,
    rowLines: List<String>
): String? {
    val rawHeaders = splitRendererMarkdownTableCells(headerLine)
        .map { cell -> normalizeRendererMarkdownTableCell(cell) }
    val separatorColumnCount = splitRendererMarkdownTableCells(separatorLine).size
    if (rawHeaders.size < 2 || rawHeaders.size != separatorColumnCount) return null
    val rawRows = rowLines.mapNotNull { rowLine ->
        val cells = splitRendererMarkdownTableCells(rowLine)
            .map { cell -> normalizeRendererMarkdownTableCell(cell) }
        cells.takeIf { it.any { cell -> cell.isNotBlank() } }
    }
    val columnCount = rawHeaders.size
    if (columnCount < 2) return null
    val headers = List(columnCount) { index ->
        rawHeaders.getOrNull(index)
            ?.takeIf { it.isNotBlank() }
            ?: "列${index + 1}"
    }
    val rowCells = rawRows.map { row ->
        List(columnCount) { index ->
            when {
                index < columnCount - 1 -> row.getOrNull(index).orEmpty()
                else -> row.drop(index).joinToString(" | ")
            }
        }
    }
    return buildString {
        append(RENDERER_TABLE_BLOCK_PREFIX)
        append(headers.joinToString(RENDERER_TABLE_CELL_SEPARATOR))
        rowCells.forEach { row ->
            append(RENDERER_TABLE_ROW_SEPARATOR)
            append(row.joinToString(RENDERER_TABLE_CELL_SEPARATOR))
        }
    }
}

internal fun decodeRendererMarkdownTableBlock(raw: String): RendererMarkdownTable? {
    if (!raw.startsWith(RENDERER_TABLE_BLOCK_PREFIX)) return null
    val payload = raw.removePrefix(RENDERER_TABLE_BLOCK_PREFIX)
    if (payload.isBlank()) return null
    val rows = payload.split(RENDERER_TABLE_ROW_SEPARATOR)
    val rawHeaders = rows.firstOrNull()
        ?.split(RENDERER_TABLE_CELL_SEPARATOR)
        ?.map { it.trim() }
        .orEmpty()
    val rawBodyRows = rows.drop(1).mapNotNull { row ->
        val cells = row.split(RENDERER_TABLE_CELL_SEPARATOR)
            .map { it.trim() }
        cells.takeIf { it.any { cell -> cell.isNotBlank() } }
    }
    val columnCount = maxOf(
        rawHeaders.size,
        rawBodyRows.maxOfOrNull { it.size } ?: 0
    )
    if (columnCount < 2) return null
    val headers = List(columnCount) { index ->
        rawHeaders.getOrNull(index)
            ?.takeIf { it.isNotBlank() }
            ?: "列${index + 1}"
    }
    val bodyRows = rawBodyRows.map { row ->
        List(columnCount) { index -> row.getOrNull(index).orEmpty() }
    }
    return RendererMarkdownTable(headers = headers, rows = bodyRows)
}

internal data class StreamingTypewriterStep(
    val text: String,
    val consumedChars: Int,
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
    data class Bullet(val text: String, val indentLevel: Int = 0) : StreamingLineModel
    data class Numbered(val number: String, val text: String, val indentLevel: Int = 0) : StreamingLineModel
    data class Quote(val text: String) : StreamingLineModel
    data class Table(val table: RendererMarkdownTable) : StreamingLineModel
    data class Paragraph(val text: String) : StreamingLineModel
}

private fun isRendererHorizontalRuleLine(line: String): Boolean =
    line.trim().matches(rendererHorizontalRuleRegex)

internal fun stripRendererStandaloneHorizontalRules(text: String): String {
    if (text.isEmpty()) return text
    val normalized = text.replace("\r\n", "\n")
    if (!normalized.lineSequence().any(::isRendererHorizontalRuleLine)) return text
    return normalized
        .split('\n')
        .filterNot(::isRendererHorizontalRuleLine)
        .joinToString("\n")
}

private fun rendererMarkdownIndentLevel(line: String): Int {
    var columns = 0
    for (ch in line) {
        when (ch) {
            ' ' -> columns += 1
            '\t' -> columns += 4
            else -> break
        }
    }
    return (columns / 4).coerceIn(0, 2)
}

private fun rendererNumberedMarkerEnd(line: String): Int =
    line.indexOfFirst { it == '.' || it == ')' }

private fun normalizeRendererTaskListText(text: String): String {
    val trimmed = text.trimStart()
    if (trimmed.length < 4) return text
    val marker = trimmed.take(3)
    if (trimmed.getOrNull(3)?.isWhitespace() != true) return text
    return when {
        marker == "[ ]" -> "\u2610 ${trimmed.drop(4).trimStart()}"
        marker.equals("[x]", ignoreCase = true) -> "\u2611 ${trimmed.drop(4).trimStart()}"
        else -> text
    }
}

internal data class QueuedStreamingChunk(
    val messageId: String,
    val revealBuffer: String
)

internal data class StreamingRevealAdvance(
    val messageId: String,
    val content: String,
    val revealBuffer: String,
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
    anchoredUserMessageId: String?,
    assistantIdProvider: (String) -> String,
    fallbackIdProvider: () -> String
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
    return StreamingRevealAdvance(
        messageId = messageId,
        content = nextContent,
        revealBuffer = currentRevealBuffer.drop(batch.text.length),
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

internal fun shouldForceFlushStreamingRevealBufferForFinish(buffer: String): Boolean {
    if (buffer.isEmpty()) return false
    return buildStreamingRevealBatch(buffer).text.isEmpty()
}

internal fun splitStreamingBlockState(
    content: String,
    treatTrailingLineAsComplete: Boolean = false
): StreamingBlockState {
    val displayContent = stripRendererStandaloneHorizontalRules(stripRendererDecorativeEmoji(content))
    val logicalLines = splitRendererStreamingLogicalLines(
        normalizeRendererMarkdownTables(
            content = displayContent,
            treatTrailingLineAsComplete = treatTrailingLineAsComplete
        )
    )
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
                completedBlocks += line.trimEnd()
            }
            else -> paragraph.appendRendererActiveParagraphLine(trimmed)
        }
    }

    val activeBlock = logicalLines.activeLine?.let { line ->
        val trimmed = line.trim()
        val activeLooksStructural = trimmed.isNotBlank() && isStructuralRendererActiveStreamingLine(line)
        when {
            paragraph.isNotEmpty() && activeLooksStructural -> {
                flushParagraphBlock()
                line.trimEnd().ifEmpty { null }
            }
            paragraph.isNotEmpty() -> buildString {
                append(paragraph.toString())
                if (trimmed.isNotBlank()) {
                    if (isNotEmpty()) {
                        appendRendererActiveParagraphLine(trimmed)
                    } else {
                        append(trimmed)
                    }
                }
            }.trim().ifEmpty { null }
            trimmed.isBlank() -> null
            activeLooksStructural -> line.trimEnd()
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
    val indentLevel = rendererMarkdownIndentLevel(line)
    decodeRendererMarkdownTableBlock(trimmed)?.let { table ->
        return StreamingLineModel.Table(table)
    }
    if (isRendererHorizontalRuleLine(trimmed)) {
        return StreamingLineModel.Blank
    }
    parseRendererStandaloneBoldHeading(trimmed)?.let { headingText ->
        return StreamingLineModel.Heading(2, headingText)
    }
    parseRendererActiveStandaloneBoldHeading(trimmed)?.let { headingText ->
        return StreamingLineModel.Heading(2, headingText)
    }
    parseRendererChineseSectionHeading(trimmed)?.let { headingText ->
        return StreamingLineModel.Heading(3, headingText)
    }
    return when {
        trimmed.matches(rendererHeadingRegex) -> {
            val marker = trimmed.takeWhile { it == '#' }
            StreamingLineModel.Heading(marker.length, trimmed.drop(marker.length).trimStart())
        }
        trimmed.matches(rendererBulletRegex) -> StreamingLineModel.Bullet(
            text = normalizeRendererTaskListText(trimmed.drop(1).trimStart()),
            indentLevel = indentLevel
        )
        trimmed.matches(rendererNumberedRegex) -> {
            val markerEnd = rendererNumberedMarkerEnd(trimmed)
            StreamingLineModel.Numbered(
                number = trimmed.take(markerEnd),
                text = normalizeRendererTaskListText(trimmed.drop(markerEnd + 1).trimStart()),
                indentLevel = indentLevel
            )
        }
        trimmed.matches(rendererQuoteRegex) -> StreamingLineModel.Quote(trimmed.drop(1).trimStart())
        else -> StreamingLineModel.Paragraph(line)
    }
}

internal fun classifyActiveStreamingLine(line: String): StreamingLineModel {
    if (line.isBlank()) return StreamingLineModel.Blank
    val trimmed = line.trimStart()
    val indentLevel = rendererMarkdownIndentLevel(line)
    decodeRendererMarkdownTableBlock(trimmed)?.let { table ->
        return StreamingLineModel.Table(table)
    }
    if (isRendererHorizontalRuleLine(trimmed)) {
        return StreamingLineModel.Blank
    }
    parseRendererChineseSectionHeading(trimmed)?.let { headingText ->
        return StreamingLineModel.Heading(3, headingText)
    }
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
    if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
        val remainder = trimmed.drop(2)
        if (remainder.trim().isNotEmpty()) {
            return StreamingLineModel.Bullet(
                text = normalizeRendererTaskListText(remainder.trimStart()),
                indentLevel = indentLevel
            )
        }
    }
    val numberedPrefix = trimmed.takeWhile { it.isDigit() }
    if (
        numberedPrefix.isNotEmpty() &&
        (trimmed.drop(numberedPrefix.length).startsWith(". ") ||
            trimmed.drop(numberedPrefix.length).startsWith(") "))
    ) {
        val remainder = trimmed.drop(numberedPrefix.length + 2)
        if (remainder.trim().isNotEmpty()) {
            return StreamingLineModel.Numbered(
                number = numberedPrefix,
                text = normalizeRendererTaskListText(remainder.trimStart()),
                indentLevel = indentLevel
            )
        }
    }
    return StreamingLineModel.Paragraph(line)
}

internal fun shouldShowStreamingSectionDivider(
    previous: StreamingLineModel?,
    current: StreamingLineModel
): Boolean {
    if (
        previous == null ||
        previous is StreamingLineModel.Heading
    ) {
        return false
    }
    val heading = current as? StreamingLineModel.Heading
    if (heading != null) return heading.level <= 3
    val paragraph = current as? StreamingLineModel.Paragraph ?: return false
    return parseRendererLeadingBoldSectionTitle(paragraph.text) != null
}

internal fun buildRendererPlainCopyText(content: String): String {
    val blockState = splitStreamingBlockState(
        content = content,
        treatTrailingLineAsComplete = true
    )
    val models = buildList {
        addAll(blockState.completedBlocks.map(::classifyStreamingLine))
        blockState.activeBlock?.let { add(classifyStreamingLine(it)) }
    }
    if (models.isEmpty()) return stripRendererStandaloneHorizontalRules(stripRendererDecorativeEmoji(content)).trim()
    return models.joinToString(separator = "\n\n") { model ->
        when (model) {
            StreamingLineModel.Blank -> ""
            is StreamingLineModel.Heading -> plainRendererInlineText(model.text)
            is StreamingLineModel.Bullet -> {
                "${"  ".repeat(model.indentLevel)}\u2022 ${plainRendererInlineText(model.text)}".trimEnd()
            }
            is StreamingLineModel.Numbered -> {
                "${"  ".repeat(model.indentLevel)}${model.number}. ${plainRendererInlineText(model.text)}".trimEnd()
            }
            is StreamingLineModel.Quote -> plainRendererInlineText(model.text)
            is StreamingLineModel.Table -> model.table.toReadableCopyText()
            is StreamingLineModel.Paragraph -> plainRendererInlineText(model.text)
        }
    }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim('\n', '\r', ' ')
}

internal fun buildRendererStructureStats(content: String): RendererStructureStats {
    val blockState = splitStreamingBlockState(
        content = content,
        treatTrailingLineAsComplete = true
    )
    val models = buildList {
        addAll(blockState.completedBlocks.map(::classifyStreamingLine))
        blockState.activeBlock?.let { add(classifyActiveStreamingLine(it)) }
    }.filterNot { it is StreamingLineModel.Blank }
    var previous: StreamingLineModel? = null
    var dividerHeadingCount = 0
    models.forEach { model ->
        if (shouldShowStreamingSectionDivider(previous, model)) {
            dividerHeadingCount += 1
        }
        previous = model
    }
    return RendererStructureStats(
        blockCount = models.size,
        headingCount = models.count { it is StreamingLineModel.Heading },
        tableCount = models.count { it is StreamingLineModel.Table },
        bulletCount = models.count { it is StreamingLineModel.Bullet },
        numberedCount = models.count { it is StreamingLineModel.Numbered },
        dividerHeadingCount = dividerHeadingCount
    )
}

private fun plainRendererInlineText(text: String): String =
    buildRendererInlineAnnotatedString(
        text = text,
        mode = RendererInlineMode.Settled,
        linksEnabled = false
    ).text.trim()

internal fun isRendererCompactNumberedSection(model: StreamingLineModel.Numbered): Boolean =
    isRendererCompactNumberedSectionText(model.text)

internal fun shouldUseRendererCompactNumberedSection(
    model: StreamingLineModel.Numbered,
    inlineMode: RendererInlineMode
): Boolean =
    isRendererCompactNumberedSection(model)

private fun isRendererCompactNumberedSectionText(text: String): Boolean {
    val plain = plainRendererInlineText(text)
        .replace(Regex("\\s+"), "")
    if (plain.length !in 2..28) return false
    if (!plain.endsWith("：") && !plain.endsWith(":")) return false
    if (plain.any { it in "。！？!?；;" }) return false
    return true
}

private fun parseRendererStandaloneBoldHeading(line: String): String? {
    val trimmed = line.trim()
    if (!trimmed.startsWith("**")) return null
    val closing = trimmed.indexOf("**", startIndex = 2)
    if (closing <= 1) return null
    val suffix = trimmed.drop(closing + 2).trim()
    if (suffix.isNotEmpty() && suffix !in setOf(":", "：")) return null
    val title = trimmed.substring(2, closing).trim()
    if (title.contains("**")) return null
    if (!isRendererStandaloneBoldHeadingTitle(title)) return null
    return title + suffix
}

private fun parseRendererActiveStandaloneBoldHeading(line: String): String? {
    val trimmed = line.trim()
    if (!trimmed.startsWith("**")) return null
    if (trimmed.indexOf("**", startIndex = 2) >= 0) return null
    val title = trimmed.drop(2).trimStart()
    if (title.length < 3) return null
    if (title.any { it.isWhitespace() }) return null
    if (title.any { it in "，,；;" }) return null
    if (!isRendererLikelyCompleteActiveBoldHeadingTitle(title)) return null
    if (!isRendererStandaloneBoldHeadingTitle(title)) return null
    return title
}

private fun parseRendererLeadingBoldSectionTitle(line: String): String? {
    val trimmed = line.trim()
    if (!trimmed.startsWith("**")) return null
    val closing = trimmed.indexOf("**", startIndex = 2)
    if (closing <= 1) return null
    val title = trimmed.substring(2, closing).trim()
    if (title.contains("**")) return null
    if (!isRendererStandaloneBoldHeadingTitle(title)) return null
    if (!isRendererLikelyCompleteActiveBoldHeadingTitle(title)) return null
    return title
}

private fun parseRendererChineseSectionHeading(line: String): String? {
    val trimmed = line.trim()
    val match = rendererChineseSectionHeadingRegex.matchEntire(trimmed) ?: return null
    val title = match.groupValues[3].trim()
    if (!isRendererStandaloneSectionHeadingTitle(title)) return null
    return trimmed
}

private fun isRendererStandaloneBoldHeadingTitle(title: String): Boolean {
    if (title.isBlank()) return false
    if (title.length > 40) return false
    if (title.any { it in "。！？!?" }) return false
    return true
}

private fun isRendererLikelyCompleteActiveBoldHeadingTitle(title: String): Boolean {
    if (title.endsWith("：") || title.endsWith(":")) return true
    if (title.length > 12) return false
    return listOf(
        "建议",
        "措施",
        "方案",
        "原因",
        "判断",
        "结论",
        "要点",
        "事项",
        "重点",
        "提醒",
        "步骤",
        "策略",
        "问题",
        "病害",
        "虫害",
        "管理",
        "用药",
        "施肥",
        "浇水",
        "灌溉",
        "防治",
        "预防",
        "补救"
    ).any { suffix -> title.endsWith(suffix) }
}

private fun isRendererStandaloneSectionHeadingTitle(title: String): Boolean {
    if (title.isBlank()) return false
    if (title.length > 56) return false
    if (title.any { it in "。；;" }) return false
    if (title.count { it == '，' || it == ',' } > 1) return false
    return true
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

private fun Int.isRendererCjkUnifiedIdeographCodePoint(): Boolean {
    return this in 0x4E00..0x9FFF ||
        this in 0x3400..0x4DBF ||
        this in 0x20000..0x2A6DF ||
        this in 0x2A700..0x2B73F ||
        this in 0x2B740..0x2B81F ||
        this in 0x2B820..0x2CEAF
}

private fun Char.isRendererCjkUnifiedIdeograph(): Boolean = code.isRendererCjkUnifiedIdeographCodePoint()

private fun Char.isRendererStrongPausePunctuation(): Boolean =
    this in setOf('。', '！', '？', '!', '?', ';', '；', ':', '：', '…', '—')

private fun Char.isRendererWeakPausePunctuation(): Boolean = this in setOf('，', ',', '、', '·')

private fun Char.isRendererStructuralMarkdownChar(): Boolean =
    this == '#' || this == '-' || this == '+' || this == '*' || this == '>' || this == '`'

private fun String.rendererCodePointAtOrNull(index: Int): Int? {
    if (index !in indices) return null
    val first = this[index]
    return if (
        Character.isHighSurrogate(first) &&
        index + 1 < length &&
        Character.isLowSurrogate(this[index + 1])
    ) {
        Character.toCodePoint(first, this[index + 1])
    } else {
        first.code
    }
}

private fun String.rendererCodePointCharCountAt(index: Int): Int {
    val codePoint = rendererCodePointAtOrNull(index) ?: return 0
    return Character.charCount(codePoint)
}

private fun Int.isRendererVariationSelectorCodePoint(): Boolean =
    this == 0xFE0E ||
        this == 0xFE0F ||
        this in 0xE0100..0xE01EF

private fun Int.isRendererEmojiModifierCodePoint(): Boolean = this in 0x1F3FB..0x1F3FF

private fun Int.isRendererRegionalIndicatorCodePoint(): Boolean = this in 0x1F1E6..0x1F1FF

private fun Int.isRendererCombiningMarkCodePoint(): Boolean {
    val type = Character.getType(this)
    return type == Character.NON_SPACING_MARK.toInt() ||
        type == Character.COMBINING_SPACING_MARK.toInt() ||
        type == Character.ENCLOSING_MARK.toInt()
}

private fun String.takeRendererCodePointCluster(startIndex: Int): String {
    if (startIndex !in indices) return ""
    var cursor = startIndex + rendererCodePointCharCountAt(startIndex).coerceAtLeast(1)

    fun consumeMarksAndVariants() {
        while (cursor < length) {
            val codePoint = rendererCodePointAtOrNull(cursor) ?: break
            if (
                codePoint.isRendererVariationSelectorCodePoint() ||
                codePoint.isRendererEmojiModifierCodePoint() ||
                codePoint.isRendererCombiningMarkCodePoint()
            ) {
                cursor += rendererCodePointCharCountAt(cursor).coerceAtLeast(1)
            } else {
                break
            }
        }
    }

    val firstCodePoint = rendererCodePointAtOrNull(startIndex)
    consumeMarksAndVariants()
    if (
        firstCodePoint?.isRendererRegionalIndicatorCodePoint() == true &&
        cursor < length &&
        rendererCodePointAtOrNull(cursor)?.isRendererRegionalIndicatorCodePoint() == true
    ) {
        cursor += rendererCodePointCharCountAt(cursor).coerceAtLeast(1)
    }
    while (cursor < length && rendererCodePointAtOrNull(cursor) == 0x200D) {
        val joinerLength = rendererCodePointCharCountAt(cursor).coerceAtLeast(1)
        val nextStart = cursor + joinerLength
        if (nextStart >= length) break
        cursor = nextStart + rendererCodePointCharCountAt(nextStart).coerceAtLeast(1)
        consumeMarksAndVariants()
    }
    return substring(startIndex, cursor)
}

private fun Int.isRendererPreservedTaskCheckboxCodePoint(): Boolean =
    this == 0x2610 || this == 0x2611

private fun Int.isRendererDecorativeEmojiCodePoint(): Boolean {
    if (isRendererPreservedTaskCheckboxCodePoint()) return false
    return this == 0x200D ||
        this == 0x20E3 ||
        isRendererVariationSelectorCodePoint() ||
        isRendererEmojiModifierCodePoint() ||
        isRendererRegionalIndicatorCodePoint() ||
        this in 0x1F000..0x1FAFF ||
        this in 0x2600..0x27BF ||
        this == 0x2B50 ||
        this == 0x2B55
}

internal fun stripRendererDecorativeEmoji(text: String): String {
    if (text.isEmpty()) return text
    val output = StringBuilder(text.length)
    var index = 0
    var skipSingleWhitespaceAfterDecorative = false
    while (index < text.length) {
        val codePoint = text.rendererCodePointAtOrNull(index) ?: break
        val charCount = text.rendererCodePointCharCountAt(index).coerceAtLeast(1)
        if (codePoint.isRendererDecorativeEmojiCodePoint()) {
            skipSingleWhitespaceAfterDecorative = true
            index += charCount
            continue
        }
        val chunk = text.substring(index, index + charCount)
        if (skipSingleWhitespaceAfterDecorative && codePoint != '\n'.code && chunk.isBlank()) {
            skipSingleWhitespaceAfterDecorative = false
            index += charCount
            continue
        }
        output.append(chunk)
        if (codePoint == '\n'.code) {
            skipSingleWhitespaceAfterDecorative = false
        } else if (!chunk.isBlank()) {
            skipSingleWhitespaceAfterDecorative = false
        }
        index += charCount
    }
    return output.toString()
}

private fun takeRendererMarkdownPrefixToken(buffer: String, startIndex: Int = 0): String? {
    if (startIndex !in 0 until buffer.length) return null
    return when {
        buffer.startsWith("```", startIndex = startIndex) -> "```"
        buffer.startsWith("## ", startIndex = startIndex) -> "## "
        buffer.startsWith("# ", startIndex = startIndex) -> "# "
        buffer.startsWith("- ", startIndex = startIndex) -> "- "
        buffer.startsWith("* ", startIndex = startIndex) -> "* "
        buffer.startsWith("+ ", startIndex = startIndex) -> "+ "
        buffer.startsWith("> ", startIndex = startIndex) -> "> "
        else -> {
            var cursor = startIndex
            while (cursor < buffer.length && buffer[cursor].isDigit()) {
                cursor++
            }
            if (
                cursor > startIndex &&
                cursor + 1 < buffer.length &&
                (buffer[cursor] == '.' || buffer[cursor] == ')') &&
                buffer[cursor + 1] == ' '
            ) {
                buffer.substring(startIndex, cursor + 2)
            } else {
                null
            }
        }
    }
}

private fun takeRendererTypewriterToken(buffer: String, startIndex: Int = 0): String {
    if (startIndex !in 0 until buffer.length) return ""
    val first = buffer[startIndex]
    val firstCodePoint = buffer.rendererCodePointAtOrNull(startIndex)
    val firstCluster = buffer.takeRendererCodePointCluster(startIndex)
    val markdownPrefix = takeRendererMarkdownPrefixToken(buffer, startIndex)
    if (markdownPrefix != null) return markdownPrefix
    if (first == '\n') return "\n"
    if (first.isRendererStructuralMarkdownChar()) {
        var cursor = startIndex
        while (cursor < buffer.length && buffer[cursor].isRendererStructuralMarkdownChar()) {
            cursor++
        }
        return if (cursor > startIndex) buffer.substring(startIndex, cursor) else first.toString()
    }
    if (firstCodePoint?.isRendererCjkUnifiedIdeographCodePoint() == true) {
        return firstCluster
    }
    if (first.isWhitespace()) {
        var cursor = startIndex
        while (cursor < buffer.length && buffer[cursor].isWhitespace() && buffer[cursor] != '\n') {
            cursor++
        }
        return if (cursor > startIndex) buffer.substring(startIndex, cursor) else first.toString()
    }

    val token = StringBuilder()
    var index = startIndex
    while (index < buffer.length) {
        val ch = buffer[index]
        val codePoint = buffer.rendererCodePointAtOrNull(index) ?: break
        val cluster = buffer.takeRendererCodePointCluster(index)
        if (token.isEmpty()) {
            token.append(cluster)
            if (ch.isRendererWeakPausePunctuation() || ch.isRendererStrongPausePunctuation()) {
                break
            }
            if (Character.charCount(codePoint) > 1 || cluster.length > Character.charCount(codePoint)) {
                break
            }
            index += cluster.length
            continue
        }
        if (
            ch.isWhitespace() ||
            codePoint.isRendererCjkUnifiedIdeographCodePoint() ||
            ch.isRendererStructuralMarkdownChar() ||
            ch.isRendererWeakPausePunctuation() ||
            ch.isRendererStrongPausePunctuation()
        ) {
            break
        }
        if (Character.charCount(codePoint) > 1 || cluster.length > Character.charCount(codePoint)) {
            break
        }
        token.append(cluster)
        index += cluster.length
        if (token.length >= RENDERER_MAX_INLINE_WORD_TOKEN_CHARS) {
            break
        }
    }
    return token.toString()
}

private fun scaleRendererStreamingDelay(delayMs: Long): Long {
    return max(6L, delayMs)
}

private fun hasRendererStructuralMarkdownPrefix(text: String, startIndex: Int = 0): Boolean {
    var cursor = startIndex.coerceIn(0, text.length)
    while (cursor < text.length && text[cursor].isWhitespace()) {
        cursor++
    }
    return cursor < text.length && (
        text[cursor] == '#' ||
            text.startsWith("**", startIndex = cursor) ||
            text.startsWith("- ", startIndex = cursor) ||
            text.startsWith("* ", startIndex = cursor) ||
            text.startsWith("+ ", startIndex = cursor) ||
            text.startsWith("> ", startIndex = cursor) ||
            run {
                var digitCursor = cursor
                while (digitCursor < text.length && text[digitCursor].isDigit()) {
                    digitCursor++
                }
                digitCursor > cursor &&
                    digitCursor + 1 < text.length &&
                    (text[digitCursor] == '.' || text[digitCursor] == ')') &&
                    text[digitCursor + 1] == ' '
            }
        )
}

private fun resolveRendererTypewriterDelay(token: String, nextHasStructuralMarkdownPrefix: Boolean): Long {
    val lastChar = token.lastOrNull() ?: return STREAM_TYPEWRITER_IDLE_POLL_MS
    val lastCodePoint = token.codePointBefore(token.length)
    val baseDelay = when {
        lastChar == '\n' -> if (nextHasStructuralMarkdownPrefix) 86L else 66L
        lastChar.isRendererStrongPausePunctuation() -> 54L
        lastChar.isRendererWeakPausePunctuation() -> 30L
        lastCodePoint.isRendererCjkUnifiedIdeographCodePoint() -> 19L
        token.length >= 7 -> 23L
        token.length >= 5 -> 21L
        token.length >= 3 -> 19L
        token.length == 2 -> 19L
        else -> 18L
    }
    return scaleRendererStreamingDelay(baseDelay)
}

private fun nextStreamingTypewriterStep(buffer: String, startIndex: Int = 0): StreamingTypewriterStep {
    if (startIndex !in 0 until buffer.length) {
        return StreamingTypewriterStep("", 0, STREAM_TYPEWRITER_IDLE_POLL_MS)
    }
    val text = takeRendererTypewriterToken(buffer, startIndex)
    val nextIndex = startIndex + text.length
    return StreamingTypewriterStep(
        text = text,
        consumedChars = text.length,
        delayMs = resolveRendererTypewriterDelay(
            token = text,
            nextHasStructuralMarkdownPrefix = text.lastOrNull() == '\n' &&
                hasRendererStructuralMarkdownPrefix(buffer, nextIndex)
        )
    )
}

private fun isRendererInvisibleStreamingMarkerToken(token: String): Boolean {
    return token == "**" || token == "*" || token == "`"
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
    return decodeRendererMarkdownTableBlock(trimmed) != null ||
        isRendererHorizontalRuleLine(trimmed) ||
        trimmed.matches(rendererHeadingRegex) ||
        parseRendererStandaloneBoldHeading(trimmed) != null ||
        parseRendererActiveStandaloneBoldHeading(trimmed) != null ||
        parseRendererChineseSectionHeading(trimmed) != null ||
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
        is StreamingLineModel.Quote,
        is StreamingLineModel.Table -> true
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
    var cursor = 0
    var tokenCount = 0

    while (cursor < buffer.length && tokenCount < STREAM_REVEAL_MAX_TOKENS_PER_BATCH) {
        val step = nextStreamingTypewriterStep(buffer, cursor)
        if (step.text.isEmpty() || step.consumedChars <= 0) break
        if (
            text.isEmpty() &&
            isRendererInvisibleStreamingMarkerToken(step.text) &&
            cursor + step.consumedChars >= buffer.length
        ) {
            return StreamingRevealBatch(
                text = "",
                delayMs = step.delayMs.coerceAtLeast(STREAM_TYPEWRITER_IDLE_POLL_MS)
            )
        }
        text.append(step.text)
        cursor += step.consumedChars
        consumedDelay += step.delayMs
        if (!isRendererInvisibleStreamingMarkerToken(step.text)) {
            tokenCount++
        }
        val tail = step.text.lastOrNull()
        val hitPause = tail == '\n' || tail?.isRendererStrongPausePunctuation() == true
        if (tokenCount > 0 && (hitPause || consumedDelay >= STREAM_REVEAL_FRAME_BUDGET_MS)) {
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

private fun StringBuilder.appendRendererActiveParagraphLine(line: String) {
    if (
        line.isNotBlank() &&
        isRendererMarkdownTableSeparatorLine(line.trim()) &&
        toString().lineSequence().lastOrNull()?.let(::looksLikeRendererMarkdownTableRow) == true
    ) {
        if (isNotEmpty()) append('\n')
        append(line)
        return
    }
    appendRendererParagraphLine(line)
}

@Composable
internal fun ChatStreamingRenderer(
    content: String,
    renderMode: StreamingRenderMode,
    showWaitingBall: Boolean,
    showThinkingLabel: Boolean = false,
    selectionEnabled: Boolean,
    showDisclaimer: Boolean,
    showLeadingSectionDivider: Boolean = false,
    onStreamingContentBoundsChanged: ((Rect?) -> Unit)?,
    expandToFullWidth: Boolean = true,
    tableCopyEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    RendererAssistantMessageContentImpl(
        content = content,
        isStreaming = renderMode != StreamingRenderMode.Settled,
        showWaitingBall = showWaitingBall,
        showThinkingLabel = showThinkingLabel,
        selectionEnabled = selectionEnabled,
        showDisclaimer = showDisclaimer,
        showLeadingSectionDivider = showLeadingSectionDivider,
        onStreamingContentBoundsChanged = onStreamingContentBoundsChanged,
        expandToFullWidth = expandToFullWidth,
        tableCopyEnabled = tableCopyEnabled,
        modifier = modifier
    )
}

@Composable
private fun RendererAssistantMessageContentImpl(
    content: String,
    isStreaming: Boolean,
    showWaitingBall: Boolean = false,
    showThinkingLabel: Boolean = false,
    selectionEnabled: Boolean = false,
    showDisclaimer: Boolean = true,
    showLeadingSectionDivider: Boolean = false,
    onStreamingContentBoundsChanged: ((Rect?) -> Unit)? = null,
    expandToFullWidth: Boolean = true,
    tableCopyEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val displayContent = remember(content) {
        stripRendererStandaloneHorizontalRules(stripRendererDecorativeEmoji(content))
    }
    val shouldRenderDisclaimer = remember(displayContent, showDisclaimer) {
        showDisclaimer && shouldShowAiDisclaimerRefined(displayContent)
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
                    content = displayContent,
                    showWaitingBall = showWaitingBall,
                    showThinkingLabel = showThinkingLabel,
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
                        content = displayContent,
                        showLeadingSectionDivider = showLeadingSectionDivider,
                        tableCopyEnabled = tableCopyEnabled
                    )
                }
            } else {
                RendererAssistantMarkdownContentImpl(
                    content = displayContent,
                    showLeadingSectionDivider = showLeadingSectionDivider,
                    tableCopyEnabled = tableCopyEnabled
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
	showThinkingLabel: Boolean,
	modifier: Modifier = Modifier
) {
	val density = LocalDensity.current
	val paragraphStyle = remember { assistantStreamingParagraphTextStyle() }
	var showThinkingText by remember { mutableStateOf(false) }
	LaunchedEffect(showThinkingLabel) {
		showThinkingText = false
		if (showThinkingLabel) {
			delay(GPT_THINKING_LABEL_DELAY_MS)
			showThinkingText = true
		}
	}
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
			AnimatedContent(
				targetState = showThinkingLabel && showThinkingText,
				transitionSpec = {
					fadeIn(animationSpec = tween(durationMillis = GPT_THINKING_TRANSITION_MS)) togetherWith
						fadeOut(animationSpec = tween(durationMillis = GPT_THINKING_TRANSITION_MS))
				},
				label = "assistantThinkingIndicator"
			) { thinking ->
				if (thinking) {
					RendererAssistantThinkingIndicatorImpl()
				} else {
					RendererGPTBreathingBallImpl()
				}
			}
		}
	}
}

@Composable
private fun RendererAssistantThinkingIndicatorImpl(
	modifier: Modifier = Modifier
) {
	val density = LocalDensity.current
	val textStyle = remember {
		assistantStreamingParagraphTextStyle().copy(
			fontSize = 16.sp,
			lineHeight = 24.sp,
			fontWeight = FontWeight.Medium,
			color = Color(0xFF181A1D),
			letterSpacing = 0.sp
		)
	}
	var textWidthPx by remember { mutableStateOf(0) }
	val transition = rememberInfiniteTransition(label = "assistantThinkingShimmer")
	val shimmerProgress by transition.animateFloat(
		initialValue = -1f,
		targetValue = 1f,
		animationSpec = infiniteRepeatable(
			animation = tween(
				durationMillis = GPT_THINKING_SHIMMER_MS,
				easing = LinearEasing
			),
			repeatMode = RepeatMode.Restart
		),
		label = "assistantThinkingShimmerProgress"
	)
	val lineHeight = with(density) { textStyle.lineHeight.toDp() }
	val measuredWidthPx = textWidthPx.takeIf { it > 0 }?.toFloat()
		?: with(density) { 92.dp.toPx() }
	val bandWidthPx = measuredWidthPx * GPT_THINKING_SHIMMER_BAND_FRACTION
	val shimmerStartPx = shimmerProgress * (measuredWidthPx + bandWidthPx)
	val shimmerBrush = Brush.linearGradient(
		colors = listOf(
			Color(0xFF181A1D),
			Color(0xFF9EA4AA),
			Color(0xFFF7F8FA),
			Color(0xFF9EA4AA),
			Color(0xFF181A1D)
		),
		start = Offset(shimmerStartPx, 0f),
		end = Offset(shimmerStartPx + bandWidthPx, 0f)
	)
	val shimmerText = remember(shimmerBrush) {
		buildAnnotatedString {
			withStyle(SpanStyle(brush = shimmerBrush)) {
				append("正在思考")
			}
		}
	}
	Box(
		modifier = modifier
			.heightIn(min = lineHeight)
			.clearAndSetSemantics { contentDescription = "正在思考" },
		contentAlignment = Alignment.BottomStart
	) {
		Text(
			text = shimmerText,
			onTextLayout = { result ->
				textWidthPx = result.size.width
			},
			style = textStyle,
			textAlign = TextAlign.Start
		)
	}
}

private fun markdownBlockSpacingModifier(
    previousBlock: StreamingLineModel?,
    currentBlock: StreamingLineModel,
    modifier: Modifier = Modifier
): Modifier {
    return if (previousBlock != null) {
        modifier.padding(top = rendererMarkdownBlockSpacingAfter(previousBlock, currentBlock))
    } else {
        modifier
    }
}

private fun rendererMarkdownBlockSpacingAfter(
    previousBlock: StreamingLineModel,
    currentBlock: StreamingLineModel
): Dp {
    return if (
        previousBlock is StreamingLineModel.Numbered &&
        isRendererCompactNumberedSection(previousBlock) &&
        currentBlock !is StreamingLineModel.Blank
    ) {
        6.dp
    } else {
        MARKDOWN_BLOCK_SPACING
    }
}

@Composable
private fun RendererAssistantStreamingContentImpl(
    content: String,
    showWaitingBall: Boolean,
    showThinkingLabel: Boolean,
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
                showThinkingLabel = showThinkingLabel,
                modifier = Modifier.fillMaxWidth()
            )
        }
        unifiedModels.forEachIndexed { index, model ->
            if (index > 0) {
                Spacer(
                    modifier = Modifier.height(
                        rendererMarkdownBlockSpacingAfter(
                            previousBlock = unifiedModels[index - 1],
                            currentBlock = model
                        )
                    )
                )
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
                    inlineMode = rendererInlineModeForStreamingBlock(
                        index = index,
                        lastIndex = unifiedModels.lastIndex,
                        model = model
                    ),
                    tableCopyEnabled = false,
                    showLeadingSectionDivider = blockLeadingDivider,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

internal fun rendererInlineModeForStreamingBlock(
    index: Int,
    lastIndex: Int,
    model: StreamingLineModel? = null
): RendererInlineMode {
    return if (index == lastIndex) {
        RendererInlineMode.Streaming
    } else if (model?.streamingInlineText()?.hasRendererUnclosedStreamingInlineDelimiter() == true) {
        RendererInlineMode.Streaming
    } else {
        RendererInlineMode.Settled
    }
}

private fun StreamingLineModel.streamingInlineText(): String? {
    return when (this) {
        StreamingLineModel.Blank -> null
        is StreamingLineModel.Heading -> text
        is StreamingLineModel.Bullet -> text
        is StreamingLineModel.Numbered -> text
        is StreamingLineModel.Quote -> text
        is StreamingLineModel.Table -> buildString {
            append(table.headers.joinToString("\n"))
            table.rows.forEach { row ->
                row.forEach { cell ->
                    append('\n')
                    append(cell)
                }
            }
        }
        is StreamingLineModel.Paragraph -> text
    }
}

private fun String.hasRendererUnclosedStreamingInlineDelimiter(): Boolean {
    return hasRendererUnclosedBoldDelimiter() ||
        hasRendererUnclosedStrikeDelimiter() ||
        hasRendererUnclosedItalicDelimiter() ||
        hasRendererUnclosedCodeDelimiter()
}

private fun String.hasRendererUnclosedBoldDelimiter(): Boolean {
    var cursor = 0
    while (cursor < length) {
        val index = indexOf("**", cursor)
        if (index < 0) return false
        if (isRendererStreamingPendingBoldOpeningDelimiter(this, index)) {
            val closing = findRendererBoldClosingDelimiter(this, index + 2)
            if (closing == null) return true
            cursor = closing + 2
        } else {
            cursor = index + 2
        }
    }
    return false
}

private fun String.hasRendererUnclosedItalicDelimiter(): Boolean {
    var cursor = 0
    while (cursor < length) {
        val index = indexOf('*', cursor)
        if (index < 0) return false
        if (startsWith("**", startIndex = index)) {
            cursor = index + 2
            continue
        }
        if (isRendererStreamingPendingItalicOpeningDelimiter(this, index)) {
            val closing = findRendererItalicClosingDelimiter(this, index + 1)
            if (closing == null) return true
            cursor = closing + 1
        } else {
            cursor = index + 1
        }
    }
    return false
}

private fun String.hasRendererUnclosedStrikeDelimiter(): Boolean {
    var cursor = 0
    while (cursor < length) {
        val index = indexOf("~~", cursor)
        if (index < 0) return false
        if (isRendererStreamingPendingStrikeOpeningDelimiter(this, index)) {
            val closing = findRendererStrikeClosingDelimiter(this, index + 2)
            if (closing == null) return true
            cursor = closing + 2
        } else {
            cursor = index + 2
        }
    }
    return false
}

private fun String.hasRendererUnclosedCodeDelimiter(): Boolean {
    var cursor = 0
    while (cursor < length) {
        val index = indexOf('`', cursor)
        if (index < 0) return false
        if (isRendererCodeDelimiter(this, index, isCode = false, mode = RendererInlineMode.Streaming)) {
            val closing = indexOf('`', index + 1)
            if (closing < 0) return true
            cursor = closing + 1
        } else {
            cursor = index + 1
        }
    }
    return false
}

@Composable
private fun RendererAssistantStreamingUnifiedBlockHost(
    model: StreamingLineModel,
    inlineMode: RendererInlineMode,
    tableCopyEnabled: Boolean,
    showLeadingSectionDivider: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(0.dp)) {
        if (showLeadingSectionDivider) {
            RendererMarkdownSectionDividerImpl()
        }
        RendererAssistantStreamingActiveBlockImpl(
            model = model,
            inlineMode = inlineMode,
            linksEnabled = false,
            tableCopyEnabled = tableCopyEnabled,
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
    inlineMode: RendererInlineMode,
    linksEnabled: Boolean,
    emphasisEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (text.isEmpty()) {
        Spacer(modifier = modifier.height(minLineHeight))
        return
    }
    val rememberedLinkInteractionListener = rememberRendererLinkInteractionListener()
    val linkInteractionListener = rememberedLinkInteractionListener.takeIf { linksEnabled }
    val renderedText = remember(text, inlineMode, linkInteractionListener, emphasisEnabled) {
        buildRendererInlineAnnotatedString(
            text = text,
            mode = inlineMode,
            linkInteractionListener = linkInteractionListener,
            linksEnabled = linksEnabled,
            emphasisEnabled = emphasisEnabled
        )
    }
    Text(
        text = renderedText,
        modifier = modifier.heightIn(min = minLineHeight),
        style = style,
        textAlign = TextAlign.Start,
        softWrap = true
    )
}

@Composable
private fun rememberRendererLinkInteractionListener(): LinkInteractionListener {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    return remember(context, uriHandler) {
        LinkInteractionListener { link ->
            val url = (link as? LinkAnnotation.Url)?.url ?: return@LinkInteractionListener
            runCatching { uriHandler.openUri(url) }
                .onFailure { error ->
                    Toast.makeText(context, "链接打开失败，请复制后打开", Toast.LENGTH_SHORT).show()
                    SessionApi.reportClientLog(
                        level = "warn",
                        event = "ui.link_open_failed",
                        message = "Assistant link open failed",
                        attrs = mapOf(
                            "scheme" to url.substringBefore(":", missingDelimiterValue = "")
                                .lowercase()
                                .take(12),
                            "exception" to error.javaClass.simpleName
                        )
                    )
                }
        }
    }
}

internal fun buildRendererInlineAnnotatedString(
    text: String,
    mode: RendererInlineMode,
    linkInteractionListener: LinkInteractionListener? = null,
    linksEnabled: Boolean = true,
    emphasisEnabled: Boolean = true
): AnnotatedString {
    val displayText = normalizeRendererLooseBoldDelimiterSpacing(
        stripRendererStandaloneHorizontalRules(stripRendererDecorativeEmoji(text))
    )
    val canUseCache =
        mode == RendererInlineMode.Settled && linkInteractionListener == null && linksEnabled && emphasisEnabled
    if (canUseCache) {
        synchronized(rendererSettledInlineMarkdownCache) {
            rendererSettledInlineMarkdownCache[displayText]?.let { return it }
        }
    }
    return buildAnnotatedString {
        var index = 0
        var bold = false
        var italic = false
        var strike = false
        var code = false

        fun currentTextStyle(): SpanStyle {
            return SpanStyle(
                fontWeight = if (bold && emphasisEnabled) FontWeight.Medium else null,
                fontStyle = if (italic) FontStyle.Italic else null,
                fontFamily = if (code) FontFamily.Monospace else null,
                background = if (code) Color(0xFFF2F3F5) else Color.Unspecified,
                textDecoration = if (strike) TextDecoration.LineThrough else null
            )
        }

        fun currentLinkStyle(): SpanStyle {
            return currentTextStyle().copy(
                color = Color(0xFF2563EB),
                textDecoration = TextDecoration.Underline
            )
        }

        fun appendStyled(chunk: String) {
            if (chunk.isEmpty()) return
            withStyle(currentTextStyle()) {
                append(chunk)
            }
        }

        fun appendLinked(displayText: String, url: String) {
            if (displayText.isEmpty()) return
            if (!linksEnabled) {
                appendStyled(displayText)
                return
            }
            withLink(
                LinkAnnotation.Url(
                    url = normalizeRendererLinkTarget(url),
                    linkInteractionListener = linkInteractionListener
                )
            ) {
                withStyle(currentLinkStyle()) {
                    append(displayText)
                }
            }
        }

        while (index < displayText.length) {
            if (!code) {
                val markdownLink = rendererMarkdownLinkRegex.find(displayText, index)
                    ?.takeIf { it.range.first == index }
                if (markdownLink != null) {
                    appendLinked(
                        displayText = markdownLink.groupValues[1],
                        url = markdownLink.groupValues[2]
                    )
                    index = markdownLink.range.last + 1
                    continue
                }
                val markdownImage = rendererMarkdownImageRegex.find(displayText, index)
                    ?.takeIf { it.range.first == index }
                if (markdownImage != null) {
                    appendStyled(markdownImage.groupValues[1].ifBlank { markdownImage.groupValues[2] })
                    index = markdownImage.range.last + 1
                    continue
                }
                val bareUrl = rendererBareUrlRegex.find(displayText, index)
                    ?.takeIf { it.range.first == index }
                if (bareUrl != null) {
                    val displayText = trimRendererBareUrlDisplayText(bareUrl.value)
                    if (displayText.isNotEmpty()) {
                        appendLinked(displayText = displayText, url = displayText)
                        index += displayText.length
                        continue
                    }
                }
            }
            when {
                !code && isRendererBoldDelimiter(displayText, index, bold, mode) -> {
                    bold = !bold
                    index += 2
                }
                isRendererCodeDelimiter(displayText, index, code, mode) -> {
                    code = !code
                    index += 1
                }
                !code && isRendererStrikeDelimiter(displayText, index, strike, mode) -> {
                    strike = !strike
                    index += 2
                }
                !code && isRendererItalicDelimiter(displayText, index, italic, mode) -> {
                    italic = !italic
                    index += 1
                }
                else -> {
                    val nextSpecial = findNextStreamingInlineDelimiterIndex(
                        text = displayText,
                        startIndex = index,
                        bold = bold,
                        italic = italic,
                        strike = strike,
                        code = code,
                        mode = mode
                    )
                    appendStyled(displayText.substring(index, nextSpecial))
                    index = nextSpecial
                }
            }
        }
    }.also { built ->
        if (canUseCache) {
            synchronized(rendererSettledInlineMarkdownCache) {
                rendererSettledInlineMarkdownCache[displayText] = built
            }
        }
    }
}

private fun normalizeRendererLooseBoldDelimiterSpacing(text: String): String {
    if (!text.contains("**")) return text
    val result = StringBuilder(text.length)
    var index = 0
    var code = false
    while (index < text.length) {
        val current = text[index]
        if (current == '`') {
            code = !code
            result.append(current)
            index += 1
            continue
        }
        if (
            !code &&
            text.startsWith("**", startIndex = index) &&
            !isRendererAsciiInlineOperatorRun(text, index, length = 2)
        ) {
            val closing = findRendererLooseBoldSpacingClosingDelimiter(text, index + 2)
            if (closing != null) {
                val inner = text.substring(index + 2, closing)
                val trimmed = inner.trim()
                if (
                    trimmed.isNotEmpty() &&
                    trimmed != inner &&
                    !trimmed.contains("**")
                ) {
                    result.append("**")
                    result.append(trimmed)
                    result.append("**")
                    index = closing + 2
                    continue
                }
            }
        }
        result.append(current)
        index += 1
    }
    return result.toString()
}

private fun findRendererLooseBoldSpacingClosingDelimiter(text: String, startIndex: Int): Int? {
    var cursor = text.indexOf("**", startIndex)
    while (cursor >= 0) {
        if (!isRendererAsciiInlineOperatorRun(text, cursor, length = 2)) return cursor
        cursor = text.indexOf("**", cursor + 2)
    }
    return null
}

private fun normalizeRendererLinkTarget(raw: String): String {
    val trimmed = raw.trim().removePrefix("<").removeSuffix(">")
    if (trimmed.isBlank()) return raw.trim()
    return if (
        trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        trimmed
    } else {
        "https://$trimmed"
    }
}

private fun trimRendererBareUrlDisplayText(raw: String): String {
    val trailingPunctuation = ".,;:!?，。；：！？)]}）】》」』”\"'"
    return raw.trimEnd { it in trailingPunctuation }
}

private fun Char.isRendererAsciiLetterOrDigit(): Boolean {
    return this in '0'..'9' ||
        this in 'a'..'z' ||
        this in 'A'..'Z'
}

private fun Char.isRendererMarkdownDelimiterBoundary(): Boolean {
    return isWhitespace() ||
        this in ".,;:!?，。；：！？、（）()[]{}<>《》“”\"'"
}

private fun isRendererAsciiInlineOperatorRun(text: String, index: Int, length: Int): Boolean {
    val previous = text.getOrNull(index - 1)
    val next = text.getOrNull(index + length)
    return previous?.isRendererAsciiLetterOrDigit() == true &&
        next?.isRendererAsciiLetterOrDigit() == true
}

private fun isRendererSingleAsterisk(text: String, index: Int): Boolean {
    return text.getOrNull(index) == '*' &&
        text.getOrNull(index - 1) != '*' &&
        text.getOrNull(index + 1) != '*'
}

private fun isRendererBoldOpeningDelimiter(text: String, index: Int): Boolean {
    if (!text.startsWith("**", index)) return false
    if (isRendererAsciiInlineOperatorRun(text, index, length = 2)) return false
    val next = text.getOrNull(index + 2)
    return next != null && !next.isWhitespace()
}

private fun isRendererStreamingPendingBoldOpeningDelimiter(text: String, index: Int): Boolean {
    if (!text.startsWith("**", index)) return false
    if (isRendererAsciiInlineOperatorRun(text, index, length = 2)) return false
    val next = text.getOrNull(index + 2)
    return next == null || !next.isWhitespace()
}

private fun isRendererBoldClosingDelimiter(text: String, index: Int): Boolean {
    if (!text.startsWith("**", index)) return false
    if (isRendererAsciiInlineOperatorRun(text, index, length = 2)) return false
    val previous = text.getOrNull(index - 1)
    return previous != null && !previous.isWhitespace()
}

private fun findRendererBoldClosingDelimiter(text: String, startIndex: Int): Int? {
    var cursor = text.indexOf("**", startIndex)
    while (cursor >= 0) {
        if (isRendererBoldClosingDelimiter(text, cursor)) return cursor
        cursor = text.indexOf("**", cursor + 2)
    }
    return null
}

private fun isRendererItalicOpeningDelimiter(text: String, index: Int): Boolean {
    if (!isRendererSingleAsterisk(text, index)) return false
    val previous = text.getOrNull(index - 1)
    val next = text.getOrNull(index + 1)
    if (next == null || next.isWhitespace()) return false
    return previous == null || previous.isRendererMarkdownDelimiterBoundary()
}

private fun isRendererStreamingPendingItalicOpeningDelimiter(text: String, index: Int): Boolean {
    if (!isRendererSingleAsterisk(text, index)) return false
    val previous = text.getOrNull(index - 1)
    val next = text.getOrNull(index + 1)
    if (next?.isWhitespace() == true) return false
    return previous == null || previous.isRendererMarkdownDelimiterBoundary()
}

private fun isRendererItalicClosingDelimiter(text: String, index: Int): Boolean {
    if (!isRendererSingleAsterisk(text, index)) return false
    val previous = text.getOrNull(index - 1)
    val next = text.getOrNull(index + 1)
    if (previous == null || previous.isWhitespace()) return false
    return next == null || next.isRendererMarkdownDelimiterBoundary()
}

private fun findRendererItalicClosingDelimiter(text: String, startIndex: Int): Int? {
    var cursor = text.indexOf('*', startIndex)
    while (cursor >= 0) {
        if (isRendererItalicClosingDelimiter(text, cursor)) return cursor
        cursor = text.indexOf('*', cursor + 1)
    }
    return null
}

private fun isRendererStrikeOpeningDelimiter(text: String, index: Int): Boolean {
    if (!text.startsWith("~~", index)) return false
    if (isRendererAsciiInlineOperatorRun(text, index, length = 2)) return false
    val next = text.getOrNull(index + 2)
    return next != null && !next.isWhitespace()
}

private fun isRendererStreamingPendingStrikeOpeningDelimiter(text: String, index: Int): Boolean {
    if (!text.startsWith("~~", index)) return false
    if (isRendererAsciiInlineOperatorRun(text, index, length = 2)) return false
    val next = text.getOrNull(index + 2)
    return next == null || !next.isWhitespace()
}

private fun isRendererStrikeClosingDelimiter(text: String, index: Int): Boolean {
    if (!text.startsWith("~~", index)) return false
    if (isRendererAsciiInlineOperatorRun(text, index, length = 2)) return false
    val previous = text.getOrNull(index - 1)
    return previous != null && !previous.isWhitespace()
}

private fun findRendererStrikeClosingDelimiter(text: String, startIndex: Int): Int? {
    var cursor = text.indexOf("~~", startIndex)
    while (cursor >= 0) {
        if (isRendererStrikeClosingDelimiter(text, cursor)) return cursor
        cursor = text.indexOf("~~", cursor + 2)
    }
    return null
}

private fun isRendererBoldDelimiter(
    text: String,
    index: Int,
    isBold: Boolean,
    mode: RendererInlineMode
): Boolean {
    return if (isBold) {
        isRendererBoldClosingDelimiter(text, index)
    } else {
        if (mode == RendererInlineMode.Streaming) {
            isRendererStreamingPendingBoldOpeningDelimiter(text, index)
        } else {
            isRendererBoldOpeningDelimiter(text, index) &&
                findRendererBoldClosingDelimiter(text, index + 2) != null
        }
    }
}

private fun isRendererItalicDelimiter(
    text: String,
    index: Int,
    isItalic: Boolean,
    mode: RendererInlineMode
): Boolean {
    return if (isItalic) {
        isRendererItalicClosingDelimiter(text, index)
    } else {
        if (mode == RendererInlineMode.Streaming) {
            isRendererStreamingPendingItalicOpeningDelimiter(text, index)
        } else {
            isRendererItalicOpeningDelimiter(text, index) &&
                findRendererItalicClosingDelimiter(text, index + 1) != null
        }
    }
}

private fun isRendererStrikeDelimiter(
    text: String,
    index: Int,
    isStrike: Boolean,
    mode: RendererInlineMode
): Boolean {
    return if (isStrike) {
        isRendererStrikeClosingDelimiter(text, index)
    } else {
        if (mode == RendererInlineMode.Streaming) {
            isRendererStreamingPendingStrikeOpeningDelimiter(text, index)
        } else {
            isRendererStrikeOpeningDelimiter(text, index) &&
                findRendererStrikeClosingDelimiter(text, index + 2) != null
        }
    }
}

private fun isRendererCodeDelimiter(
    text: String,
    index: Int,
    isCode: Boolean,
    mode: RendererInlineMode
): Boolean {
    if (text.getOrNull(index) != '`') return false
    return if (isCode) {
        true
    } else {
        val next = text.getOrNull(index + 1)
        if (mode == RendererInlineMode.Streaming) {
            next == null || !next.isWhitespace()
        } else {
            next != null &&
                !next.isWhitespace() &&
                text.indexOf('`', index + 1) >= 0
        }
    }
}

private fun findNextStreamingInlineDelimiterIndex(
    text: String,
    startIndex: Int,
    bold: Boolean,
    italic: Boolean,
    strike: Boolean,
    code: Boolean,
    mode: RendererInlineMode
): Int {
    var next = text.length
    if (!code) {
        val markdownImageIndex = rendererMarkdownImageRegex.find(text, startIndex)
            ?.range
            ?.first
            ?.takeIf { it >= startIndex }
        if (markdownImageIndex != null) next = minOf(next, markdownImageIndex)
        val markdownLinkIndex = rendererMarkdownLinkRegex.find(text, startIndex)
            ?.range
            ?.first
            ?.takeIf { it >= startIndex }
        if (markdownLinkIndex != null) next = minOf(next, markdownLinkIndex)
        val bareUrlIndex = rendererBareUrlRegex.find(text, startIndex)
            ?.range
            ?.first
            ?.takeIf { it >= startIndex }
        if (bareUrlIndex != null) next = minOf(next, bareUrlIndex)
    }
    var codeIndex = text.indexOf('`', startIndex)
    while (codeIndex >= 0 && !isRendererCodeDelimiter(text, codeIndex, isCode = code, mode = mode)) {
        codeIndex = text.indexOf('`', codeIndex + 1)
    }
    if (codeIndex >= 0) next = minOf(next, codeIndex)
    if (!code) {
        var boldIndex = text.indexOf("**", startIndex)
        while (boldIndex >= 0 && !isRendererBoldDelimiter(text, boldIndex, isBold = bold, mode = mode)) {
            boldIndex = text.indexOf("**", boldIndex + 2)
        }
        if (boldIndex >= 0) next = minOf(next, boldIndex)
        var strikeIndex = text.indexOf("~~", startIndex)
        while (strikeIndex >= 0 && !isRendererStrikeDelimiter(text, strikeIndex, isStrike = strike, mode = mode)) {
            strikeIndex = text.indexOf("~~", strikeIndex + 2)
        }
        if (strikeIndex >= 0) next = minOf(next, strikeIndex)
        var italicIndex = text.indexOf('*', startIndex)
        while (italicIndex >= 0 && !isRendererItalicDelimiter(text, italicIndex, isItalic = italic, mode = mode)) {
            italicIndex = text.indexOf('*', italicIndex + 1)
        }
        if (italicIndex >= 0) next = minOf(next, italicIndex)
    }
    return next
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
    inlineMode: RendererInlineMode,
    linksEnabled: Boolean,
    tableCopyEnabled: Boolean,
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
                    if (showLeadingSectionDivider) RendererMarkdownSectionDividerImpl()
                    RendererStreamingActiveTextImpl(
                        text = model.text,
                        modifier = Modifier.fillMaxWidth(),
                        style = headingStyle,
                        minLineHeight = with(density) { headingStyle.lineHeight.toDp() },
                        inlineMode = inlineMode,
                        linksEnabled = linksEnabled
                    )
                }
            }
            is StreamingLineModel.Bullet -> {
                val bulletStyle = remember(paragraphStyle) { paragraphStyle.copy(fontSize = 17.5.sp) }
                val bodyStyle = paragraphStyle
                val listIndent = rendererListIndentDp(model.indentLevel)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = listIndent),
                    horizontalArrangement = Arrangement.spacedBy(if (model.indentLevel > 0) 7.dp else 8.dp)
                ) {
                    Text(
                        text = "\u2022",
                        modifier = Modifier.alignBy(FirstBaseline),
                        style = bulletStyle
                    )
                    RendererStreamingActiveTextImpl(
                        text = model.text,
                        modifier = Modifier
                            .alignBy(FirstBaseline)
                            .weight(1f)
                            .heightIn(min = paragraphLineHeight),
                        style = bodyStyle,
                        minLineHeight = paragraphLineHeight,
                        inlineMode = inlineMode,
                        linksEnabled = linksEnabled
                    )
                }
            }
            is StreamingLineModel.Numbered -> {
                val compactNumberedSection = remember(model.text, inlineMode) {
                    shouldUseRendererCompactNumberedSection(model, inlineMode)
                }
                val numberStyle = remember(paragraphStyle, compactNumberedSection) {
                    if (compactNumberedSection) {
                        paragraphStyle.copy(
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        paragraphStyle.copy(fontWeight = FontWeight.SemiBold)
                    }
                }
                val bodyStyle = remember(paragraphStyle, compactNumberedSection) {
                    if (compactNumberedSection) {
                        paragraphStyle.copy(
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        paragraphStyle
                    }
                }
                val bodyLineHeight = with(density) { bodyStyle.lineHeight.toDp() }
                val listIndent = rendererListIndentDp(model.indentLevel)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = listIndent),
                    horizontalArrangement = Arrangement.spacedBy(
                        if (compactNumberedSection) 6.dp else 8.dp
                    )
                ) {
                    Text(
                        text = "${model.number}.",
                        modifier = Modifier.alignBy(FirstBaseline),
                        style = numberStyle
                    )
                    RendererStreamingActiveTextImpl(
                        text = model.text,
                        modifier = Modifier
                            .alignBy(FirstBaseline)
                            .weight(1f)
                            .heightIn(min = bodyLineHeight),
                        style = bodyStyle,
                        minLineHeight = bodyLineHeight,
                        inlineMode = inlineMode,
                        linksEnabled = linksEnabled
                    )
                }
            }
            is StreamingLineModel.Quote -> {
                val quoteStyle = paragraphStyle
                RendererStreamingActiveTextImpl(
                    text = model.text,
                    modifier = Modifier.fillMaxWidth(),
                    style = quoteStyle,
                    minLineHeight = paragraphLineHeight,
                    inlineMode = inlineMode,
                    linksEnabled = linksEnabled
                )
            }
            is StreamingLineModel.Table -> {
                RendererMarkdownTableImpl(
                    table = model.table,
                    inlineMode = inlineMode,
                    linksEnabled = linksEnabled,
                    copyEnabled = shouldEnableRendererMarkdownTableCopy(
                        messageSettled = tableCopyEnabled,
                        inlineMode = inlineMode
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            is StreamingLineModel.Paragraph -> {
                RendererStreamingActiveTextImpl(
                    text = model.text,
                    modifier = Modifier.fillMaxWidth(),
                    style = paragraphStyle,
                    minLineHeight = paragraphLineHeight,
                    inlineMode = inlineMode,
                    linksEnabled = linksEnabled
                )
            }
        }
    }
}

@Composable
private fun RendererMarkdownTableImpl(
    table: RendererMarkdownTable,
    inlineMode: RendererInlineMode,
    linksEnabled: Boolean,
    copyEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    if (table.rows.isEmpty()) return
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val copyTable = {
        clipboardManager.setText(AnnotatedString(buildRendererMarkdownTableCopyText(table)))
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(width = 0.8.dp, color = Color(0xFFDDE2E8), shape = RoundedCornerShape(8.dp))
    ) {
        table.rows.forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) {
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 0.8.dp,
                    color = Color(0xFFE2E6EA)
                )
            }
            RendererMarkdownTableRowImpl(
                headers = table.headers,
                cells = row,
                rowIndex = rowIndex,
                inlineMode = inlineMode,
                linksEnabled = linksEnabled,
                copyEnabled = copyEnabled && rowIndex == 0,
                onCopy = copyTable
            )
        }
    }
}

@Composable
private fun RendererMarkdownTableRowImpl(
    headers: List<String>,
    cells: List<String>,
    rowIndex: Int,
    inlineMode: RendererInlineMode,
    linksEnabled: Boolean,
    copyEnabled: Boolean,
    onCopy: () -> Unit
) {
    val titleStyle = remember {
        assistantStreamingParagraphTextStyle().copy(
            fontSize = 16.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF151515),
            letterSpacing = 0.sp
        )
    }
    val labelStyle = remember {
        TextStyle(
            fontSize = 15.sp,
            lineHeight = 23.sp,
            color = Color(0xFF666D76),
            letterSpacing = 0.sp,
            fontWeight = FontWeight.Normal
        )
    }
    val valueStyle = remember {
        assistantStreamingParagraphTextStyle().copy(
            fontSize = 15.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Normal,
            color = Color(0xFF222222),
            letterSpacing = 0.sp
        )
    }
    val title = rendererMarkdownTableDisplayTitle(headers, cells, rowIndex)
    val visibleEntries = headers.drop(1).mapIndexedNotNull { index, header ->
        val value = cells.getOrNull(index + 1).orEmpty().trim()
        if (value.isBlank()) {
            null
        } else {
            header to value
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFAFBFC))
                .heightIn(min = 44.dp)
                .padding(start = 12.dp, end = if (copyEnabled) 0.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RendererStreamingActiveTextImpl(
                text = title,
                style = titleStyle,
                minLineHeight = 24.dp,
                inlineMode = inlineMode,
                linksEnabled = linksEnabled,
                modifier = Modifier.weight(1f)
            )
            if (copyEnabled) {
                RendererCopyTableIconButton(onClick = onCopy)
            }
        }
        visibleEntries.forEachIndexed { index, (header, value) ->
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 0.8.dp,
                color = Color(0xFFE7EAEE)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = plainRendererInlineText(header),
                    style = labelStyle
                )
                RendererStreamingActiveTextImpl(
                    text = value,
                    style = valueStyle,
                    minLineHeight = 23.dp,
                    inlineMode = inlineMode,
                    linksEnabled = linksEnabled,
                    emphasisEnabled = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun RendererCopyTableIconButton(
    onClick: () -> Unit
) {
    DisableSelection {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .clearAndSetSemantics { contentDescription = "复制表格" },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(28.dp)) {
                val iconColor = Color(0xFF111111)
                val coverColor = Color(0xFFFAFBFC)
                val stroke = Stroke(width = 2.35.dp.toPx())
                val radius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx())
                val squareSize = size.width * 0.48f
                val backTopLeft = Offset(x = size.width * 0.38f, y = size.height * 0.15f)
                val frontTopLeft = Offset(x = size.width * 0.14f, y = size.height * 0.37f)
                drawRoundRect(
                    color = iconColor,
                    topLeft = backTopLeft,
                    size = Size(width = squareSize, height = squareSize),
                    cornerRadius = radius,
                    style = stroke
                )
                drawRoundRect(
                    color = coverColor,
                    topLeft = frontTopLeft,
                    size = Size(width = squareSize, height = squareSize),
                    cornerRadius = radius
                )
                drawRoundRect(
                    color = iconColor,
                    topLeft = frontTopLeft,
                    size = Size(width = squareSize, height = squareSize),
                    cornerRadius = radius,
                    style = stroke
                )
            }
        }
    }
}

private fun rendererListIndentDp(indentLevel: Int): Dp =
    (indentLevel.coerceAtLeast(0) * 10).dp

private fun rendererMarkdownTableDisplayTitle(
    headers: List<String>,
    cells: List<String>,
    rowIndex: Int
): String {
    val firstHeader = headers.firstOrNull()?.let(::plainRendererInlineText).orEmpty()
    val firstCell = cells.firstOrNull()?.trim().orEmpty()
    val rowTitle = firstCell.takeIf { it.isNotBlank() } ?: "第${rowIndex + 1}行"
    return if (firstHeader in setOf("维度", "项目", "类别", "指标", "对比项") || firstHeader.isBlank()) {
        rowTitle
    } else {
        "$firstHeader：$rowTitle"
    }
}

@Composable
private fun RendererAssistantMarkdownContentImpl(
    content: String,
    modifier: Modifier = Modifier,
    showLeadingSectionDivider: Boolean = false,
    tableCopyEnabled: Boolean = true
) {
    val blockState = remember(content) {
        splitStreamingBlockState(
            content = content,
            treatTrailingLineAsComplete = true
        )
    }
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
                    previousBlock = completedModels.getOrNull(index - 1),
                    currentBlock = model,
                    modifier = Modifier.fillMaxWidth()
                )
                RendererAssistantStreamingActiveBlockImpl(
                    model = model,
                    inlineMode = RendererInlineMode.Settled,
                    linksEnabled = true,
                    tableCopyEnabled = tableCopyEnabled,
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
