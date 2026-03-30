package com.nongjiqianwen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import java.util.LinkedHashMap
import kotlin.random.Random
import kotlin.math.roundToInt
import java.util.UUID
import kotlin.coroutines.resume

private enum class ChatRole { USER, ASSISTANT }
private enum class AutoScrollMode { Idle, AnchorUser, StreamAnchorFollow }
private enum class ScrollMode { Idle, AutoFollow, UserBrowsing, Returning }
@Immutable
private data class ChatMessage(val id: String, val role: ChatRole, val content: String)
@Immutable
private data class FailedAssistantMessageState(val sourceUserMessageId: String)
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
private data class MessageSelectionToolbarState(
    val messageId: String,
    val anchorX: Int,
    val anchorY: Int,
    val selectionBottomY: Int,
    val anchorXRatio: Float,
    val selectionTopRatio: Float,
    val selectionBottomRatio: Float,
    val onCopyRequested: (() -> Unit)?,
    val onCopyFullRequested: (() -> Unit)?
)

private data class PendingMessageSelectionToolbarState(
    val messageId: String,
    val rect: Rect,
    val onCopyRequested: (() -> Unit)?,
    val onCopyFullRequested: (() -> Unit)?
)

private data class InputSelectionToolbarState(
    val anchorXRatio: Float,
    val onCopyRequested: (() -> Unit)?,
    val onPasteRequested: (() -> Unit)?,
    val onCutRequested: (() -> Unit)?,
    val onSelectAllRequested: (() -> Unit)?
)

private data class PendingInputSelectionToolbarState(
    val rect: Rect,
    val onCopyRequested: (() -> Unit)?,
    val onPasteRequested: (() -> Unit)?,
    val onCutRequested: (() -> Unit)?,
    val onSelectAllRequested: (() -> Unit)?
)

private enum class SendBlockReason {
    None,
    EmptyInput,
    Streaming,
    InputTooLong
}

private data class SendGateState(
    val canPress: Boolean,
    val canSubmit: Boolean,
    val blockReason: SendBlockReason
)

private enum class MessageActionMenuSide { Above, Below }

private object StaticMessageSelectionBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

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
private const val INLINE_MARKDOWN_CACHE_LIMIT = 180
private const val BLOCK_MARKDOWN_CACHE_LIMIT = 120
private const val JUMP_BUTTON_AUTO_HIDE_MS = 1200L
private const val BOTTOM_VIEWPORT_SAVE_DEBOUNCE_MS = 320L
private const val STREAM_DRAFT_SAVE_DEBOUNCE_MS = 180L
private const val STREAM_TYPEWRITER_IDLE_POLL_MS = 8L
private const val STREAM_REVEAL_FRAME_BUDGET_MS = 40L
private const val STREAM_REVEAL_MAX_TOKENS_PER_BATCH = 4
private const val STREAM_DELAY_MULTIPLIER = 1.18
private const val STREAM_FRESH_LINE_SETTLE_FRAMES = 4
private const val STREAM_FRESH_LINE_AFTER_FOLLOW_SETTLE_FRAMES = 3
private const val STREAM_FRESH_SUFFIX_MIN_HIGHLIGHT_CHARS = 3
private const val STREAM_FRESH_SUFFIX_HIGHLIGHT_MS = 90
private const val STREAM_FRESH_SUFFIX_TRIGGER_INTERVAL_MS = 760L
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
private const val COMPOSER_STATUS_HINT_MS = 1800L
private const val GPT_BALL_PULSE_MS = 720
private const val GPT_BALL_EXIT_MS = 180
private const val GPT_STREAM_TEXT_ENTRY_MS = 220
private val STREAMING_MESSAGE_MIN_HEIGHT = 76.dp
private val STREAM_AUTO_FOLLOW_SLOP = 28.dp
private val MIN_SEND_ANCHOR_EXTRA_BOTTOM_SPACE = 160.dp
private val ASSISTANT_START_ANCHOR_TOP = 96.dp
private val STREAM_VISIBLE_BOTTOM_GAP = 24.dp
private val BOTTOM_OVERLAY_CONTENT_CLEARANCE = 4.dp
private val BOTTOM_POSITION_TOLERANCE = 16.dp
private val FINAL_BOTTOM_SNAP_TOLERANCE = 24.dp
private val LIFECYCLE_RESUME_BOTTOM_SNAP_THRESHOLD = 32.dp
private const val BOTTOM_BAR_HEIGHT_JITTER_TOLERANCE_PX = 10
private const val REMOTE_STREAM_RECOVERY_MAX_ATTEMPTS = 10
private const val REMOTE_STREAM_RECOVERY_DELAY_MS = 700L
private val MESSAGE_ACTION_MENU_MARGIN = 8.dp
private val MESSAGE_ACTION_MENU_VERTICAL_SPACING = 16.dp
private val MESSAGE_ACTION_MENU_ESTIMATED_HEIGHT = 44.dp
private val MESSAGE_ACTION_MENU_SWITCH_THRESHOLD = 28.dp
private val JUMP_BUTTON_EXTRA_BOTTOM_CLEARANCE = 32.dp
private val MESSAGE_SELECTION_HANDLE_MASK_GUARD = 20.dp
private val TOP_CHROME_MASK_EXTRA = 12.dp
private val STREAM_FRESH_SUFFIX_HIGHLIGHT_COLOR = Color(0xFFDDE1E6)
private val CHAT_SELECTION_HANDLE_COLOR = Color(0xFF111111)
private val CHAT_SELECTION_BACKGROUND_COLOR = Color(0xFF858B94).copy(alpha = 0.52f)
private val STATIC_SELECTION_CACHE_WINDOW = 6000.dp
private val INITIAL_BOTTOM_SNAP_THRESHOLD = 22.dp
private val STARTUP_INPUT_CHROME_ROW_HEIGHT_ESTIMATE = 64.dp
private val STARTUP_BOTTOM_BAR_HEIGHT_ESTIMATE = 72.dp
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
private val bareUrlRegex = Regex("(?i)\\b((?:https?://|www\\.)[^\\s<>()]+)")
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
        return buffer.substring(0, 1)
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

private fun scaleStreamingDelay(delayMs: Long): Long {
    return (delayMs * STREAM_DELAY_MULTIPLIER).roundToInt().toLong().coerceAtLeast(STREAM_TYPEWRITER_IDLE_POLL_MS)
}

private fun nextLocalStreamFeedStep(remaining: String): LocalStreamFeedStep {
    if (remaining.isEmpty()) return LocalStreamFeedStep("", 0L)
    val first = remaining.first()
    val markdownPrefix = takeMarkdownPrefixToken(remaining)
    val takeCount = when {
        markdownPrefix != null -> markdownPrefix.length
        first == '\n' -> 1
        first.isStructuralMarkdownChar() -> 1
        first.isCjkUnifiedIdeograph() -> Random.nextInt(1, 3)
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
    return LocalStreamFeedStep(text = text, delayMs = scaleStreamingDelay(delayMs))
}

private fun nextLocalStreamFeedBatch(remaining: String): LocalStreamFeedStep {
    if (remaining.isEmpty()) return LocalStreamFeedStep("", 0L)
    if (remaining.length <= 24) return nextLocalStreamFeedStep(remaining)
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
        delayMs = scaleStreamingDelay(totalDelayMs.coerceAtLeast(42L))
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
    val baseDelay = when {
        lastChar == '\n' -> if (hasStructuralMarkdownPrefix(remainingBuffer)) 64L else 48L
        lastChar.isStrongPausePunctuation() -> 28L
        lastChar.isWeakPausePunctuation() -> 16L
        token.length >= 7 -> 8L
        token.length >= 5 -> 10L
        token.length >= 3 -> 12L
        token.length == 2 -> 13L
        else -> if (lastChar.isCjkUnifiedIdeograph()) 14L else 12L
    }
    return scaleStreamingDelay(baseDelay)
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

private data class StreamingRenderedLines(
    val stableLines: List<AnnotatedString>,
    val activeLine: AnnotatedString?
)

private data class StreamingLogicalLines(
    val completedLines: List<String>,
    val activeLine: String?
)

private data class StreamingBlockState(
    val completedBlocks: List<String>,
    val activeBlock: String?
)

private sealed interface StreamingLineModel {
    data object Blank : StreamingLineModel
    data class Heading(val level: Int, val text: String) : StreamingLineModel
    data class Bullet(val text: String) : StreamingLineModel
    data class Numbered(val number: String, val text: String) : StreamingLineModel
    data class Quote(val text: String) : StreamingLineModel
    data class Paragraph(val text: String) : StreamingLineModel
}

private fun buildLockedStreamingActivePreview(
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
        val token = takeTypewriterToken(remaining).ifEmpty { remaining.first().toString() }
        preview.append(token)
        revealedVisibleChars += token.count { !it.isWhitespace() }
        remaining = remaining.drop(token.length.coerceAtLeast(1))
    }
    return AnnotatedString(preview.toString())
}

private fun splitStreamingLogicalLines(content: String): StreamingLogicalLines {
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

private fun shouldShowStreamingSectionDivider(previousLine: String?, currentLine: String): Boolean {
    if (previousLine.isNullOrBlank()) return false
    val trimmed = currentLine.trimStart()
    if (!trimmed.matches(headingRegex)) return false
    return trimmed.takeWhile { it == '#' }.length <= 2
}

private fun isStructuralStreamingLine(trimmed: String): Boolean {
    return trimmed.matches(headingRegex) ||
        trimmed.matches(bulletRegex) ||
        trimmed.matches(numberedRegex) ||
        trimmed.matches(quoteRegex)
}

private fun isStructuralActiveStreamingLine(line: String): Boolean {
    return when (classifyActiveStreamingLine(line)) {
        StreamingLineModel.Blank,
        is StreamingLineModel.Paragraph -> false
        is StreamingLineModel.Heading,
        is StreamingLineModel.Bullet,
        is StreamingLineModel.Numbered,
        is StreamingLineModel.Quote -> true
    }
}

private fun splitStreamingBlockState(content: String): StreamingBlockState {
    val logicalLines = splitStreamingLogicalLines(content)
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
            isStructuralStreamingLine(trimmed) -> {
                flushParagraphBlock()
                completedBlocks += trimmed
            }
            else -> paragraph.appendParagraphLine(trimmed)
        }
    }

    val activeBlock = logicalLines.activeLine?.let { line ->
        val trimmed = line.trim()
        val activeLooksStructural = trimmed.isNotBlank() && isStructuralActiveStreamingLine(line)
        when {
            paragraph.isNotEmpty() && activeLooksStructural -> {
                flushParagraphBlock()
                trimmed.ifEmpty { null }
            }
            paragraph.isNotEmpty() -> buildString {
                append(paragraph.toString())
                if (trimmed.isNotBlank()) {
                    if (isNotEmpty()) {
                        appendParagraphLine(trimmed)
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

private fun classifyStreamingLine(line: String): StreamingLineModel {
    if (line.isBlank()) return StreamingLineModel.Blank
    val trimmed = line.trimStart()
    return when {
        trimmed.matches(headingRegex) -> {
            val marker = trimmed.takeWhile { it == '#' }
            StreamingLineModel.Heading(marker.length, trimmed.drop(marker.length).trimStart())
        }
        trimmed.matches(bulletRegex) -> StreamingLineModel.Bullet(trimmed.drop(1).trimStart())
        trimmed.matches(numberedRegex) -> StreamingLineModel.Numbered(
            number = trimmed.substringBefore('.'),
            text = trimmed.substringAfter('.').trimStart()
        )
        trimmed.matches(quoteRegex) -> StreamingLineModel.Quote(trimmed.drop(1).trimStart())
        else -> StreamingLineModel.Paragraph(line)
    }
}

private fun classifyActiveStreamingLine(line: String): StreamingLineModel {
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

private fun shouldShowStreamingSectionDivider(
    previous: StreamingLineModel?,
    current: StreamingLineModel
): Boolean {
    val heading = current as? StreamingLineModel.Heading ?: return false
    return previous != null && heading.level <= 2
}

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

private fun buildStableStreamingLineBuffer(
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

private fun splitMarkdownTableCells(line: String): List<String> {
    val trimmed = line.trim().removePrefix("|").removeSuffix("|")
    if (trimmed.isBlank()) return emptyList()
    return trimmed.split('|').map { it.trim() }
}

private fun isMarkdownTableSeparatorLine(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.contains('|')) return false
    val normalized = trimmed.replace("|", "").replace(" ", "")
    return normalized.isNotEmpty() && normalized.all { it == '-' || it == ':' }
}

private fun looksLikeMarkdownTableRow(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isBlank() || !trimmed.contains('|')) return false
    if (isMarkdownTableSeparatorLine(trimmed)) return false
    return splitMarkdownTableCells(trimmed).size >= 2
}

private fun fallbackMarkdownTableHeaders(headerCells: List<String>): List<String> {
    return headerCells.mapIndexed { index, cell ->
        cell.ifBlank { "\u5217${index + 1}" }
    }
}

private fun convertMarkdownTableBlock(headerLine: String, rowLines: List<String>): List<String> {
    val headers = fallbackMarkdownTableHeaders(splitMarkdownTableCells(headerLine))
    if (headers.isEmpty()) return emptyList()
    return rowLines.mapNotNull { rowLine ->
        val values = splitMarkdownTableCells(rowLine)
        if (values.isEmpty()) {
            null
        } else {
            val pairs = headers.mapIndexedNotNull { index, header ->
                val value = values.getOrNull(index)?.trim().orEmpty()
                if (value.isBlank()) null else "$header\uff1a$value"
            }
            when {
                pairs.isNotEmpty() -> "- ${pairs.joinToString("\uff1b")}"
                else -> "- ${values.joinToString(" | ").trim()}"
            }
        }
    }
}

private fun normalizeMarkdownTables(content: String): String {
    val normalized = content.replace("\r\n", "\n")
    if (!normalized.contains('|')) return normalized
    val lines = normalized.lines()
    if (lines.isEmpty()) return normalized
    val result = mutableListOf<String>()
    var index = 0
    while (index < lines.size) {
        val current = lines[index]
        if (
            index + 1 < lines.size &&
            looksLikeMarkdownTableRow(current) &&
            isMarkdownTableSeparatorLine(lines[index + 1])
        ) {
            val rowLines = mutableListOf<String>()
            var cursor = index + 2
            while (cursor < lines.size && looksLikeMarkdownTableRow(lines[cursor])) {
                rowLines += lines[cursor]
                cursor++
            }
            if (rowLines.isNotEmpty()) {
                result += convertMarkdownTableBlock(current, rowLines)
                index = cursor
                continue
            }
        }
        result += current
        index++
    }
    return result.joinToString("\n")
}

private fun parseMarkdownBlocks(content: String): List<MarkdownBlock> {
    val normalized = normalizeAssistantText(normalizeMarkdownTables(content))
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

private fun normalizeLinkTarget(raw: String): String {
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

private fun trimBareUrlDisplayText(raw: String): String {
    val trailingPunctuation = ".,;:!?，。；：！？"
    return raw.trimEnd { it in trailingPunctuation }
}

private fun buildMarkdownAnnotatedStringInternal(
    text: String
): AnnotatedString {
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

        fun appendLinked(displayText: String, url: String) {
            if (displayText.isEmpty()) return
            withLink(LinkAnnotation.Url(normalizeLinkTarget(url))) {
                appendStyled(displayText)
            }
        }

        while (index < text.length) {
            if (!code) {
                val markdownLink = linkRegex.find(text, index)?.takeIf { it.range.first == index }
                if (markdownLink != null) {
                    appendLinked(
                        displayText = markdownLink.groupValues[1],
                        url = markdownLink.groupValues[2]
                    )
                    index = markdownLink.range.last + 1
                    continue
                }
                val bareUrl = bareUrlRegex.find(text, index)?.takeIf { it.range.first == index }
                if (bareUrl != null) {
                    val displayText = trimBareUrlDisplayText(bareUrl.value)
                    if (displayText.isNotEmpty()) {
                        appendLinked(displayText = displayText, url = displayText)
                        index += displayText.length
                        continue
                    }
                }
            }
            when {
                !code && text.startsWith("**", index) -> {
                    bold = !bold
                    index += 2
                }
                text[index] == '`' -> {
                    code = !code
                    index += 1
                }
                !code && text[index] == '*' -> {
                    italic = !italic
                    index += 1
                }
                else -> {
                    val nextSpecial = buildList {
                        val markdownLinkIndex = if (!code) {
                            linkRegex.find(text, index)?.range?.first?.takeIf { it >= index }
                        } else {
                            null
                        }
                        val bareUrlIndex = if (!code) {
                            bareUrlRegex.find(text, index)?.range?.first?.takeIf { it >= index }
                        } else {
                            null
                        }
                        val boldIndex = if (!code) text.indexOf("**", index).takeIf { it >= 0 } else null
                        val codeIndex = text.indexOf('`', index).takeIf { it >= 0 }
                        val italicIndex = if (!code) text.indexOf('*', index).takeIf { it >= 0 } else null
                        if (markdownLinkIndex != null) add(markdownLinkIndex)
                        if (bareUrlIndex != null) add(bareUrlIndex)
                        if (boldIndex != null) add(boldIndex)
                        if (codeIndex != null) add(codeIndex)
                        if (italicIndex != null) add(italicIndex)
                    }.minOrNull() ?: text.length
                    appendStyled(text.substring(index, nextSpecial))
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

private fun buildRenderedMessageCopyText(role: ChatRole, content: String): String {
    return when (role) {
        ChatRole.USER -> content.trim()
        ChatRole.ASSISTANT -> {
            val blocks = getCachedMarkdownUiBlocks(content)
            if (blocks.isEmpty()) {
                getCachedAnnotatedString(normalizeAssistantText(content)).text.trim()
            } else {
                blocks.joinToString(separator = "\n\n") { block ->
                    when (block) {
                        is MarkdownUiBlock.Heading -> block.text.text.trim()
                        is MarkdownUiBlock.Bullet -> "\u2022 ${block.text.text.trim()}".trim()
                        is MarkdownUiBlock.Numbered -> "${block.number}. ${block.text.text.trim()}".trim()
                        is MarkdownUiBlock.Paragraph -> block.text.text.trim()
                    }
                }.trim()
            }
        }
    }
}

private fun Rect.containsPoint(offset: Offset): Boolean {
    return offset.x >= left && offset.x <= right && offset.y >= top && offset.y <= bottom
}

private suspend fun AwaitPointerEventScope.waitForUpIgnoringConsumption(
    pass: PointerEventPass
): PointerInputChange? {
    while (true) {
        val event = awaitPointerEvent(pass)
        event.changes.firstOrNull { it.changedToUpIgnoreConsumed() }?.let { return it }
        if (event.changes.none { it.pressed }) {
            return null
        }
    }
}

private fun assistantParagraphTextStyle(): TextStyle = TextStyle(
    fontSize = 17.sp,
    lineHeight = 28.sp,
    letterSpacing = 0.05.sp,
    color = Color(0xFF171717),
    textMotion = TextMotion.Static,
    lineBreak = LineBreak.Paragraph
)

private fun assistantStreamingParagraphTextStyle(): TextStyle =
    assistantParagraphTextStyle().copy(
        lineHeight = 30.sp,
        lineBreak = LineBreak.Simple
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
        "用药", "施药", "农药", "杀菌剂", "杀虫剂", "杀螨", "施肥", "追肥", "叶面肥",
        "冲施", "滴灌", "灌根", "喷施", "喷雾", "喷药", "剂量", "用量", "倍数", "浓度", "稀释",
        "稀释倍数", "配比", "配方", "混配", "复配", "安全期", "间隔期", "残留", "采收期", "停药期",
        "防治", "防控", "处理方案"
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
    return messages.any { it.role == ChatRole.ASSISTANT && shouldShowAiDisclaimerRefined(it.content) }
}

private fun buildSendGateState(
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

private fun shouldShowAiDisclaimerRefined(content: String): Boolean {
    if (content.isBlank()) return false
    val normalized = content.lowercase()
    val actionKeywords = listOf(
        "用药", "施药", "喷药", "喷施", "喷雾",
        "灌根", "滴灌", "冲施", "施肥", "追肥"
    )
    val treatmentMaterialKeywords = listOf(
        "农药", "药剂", "杀菌剂", "杀虫剂", "杀螨剂",
        "除草剂", "叶面肥", "水溶肥", "冲施肥"
    )
    val dosageKeywords = listOf(
        "剂量", "用量", "浓度", "稀释", "倍数", "倍液",
        "配比", "配方", "ppm", "兑水", "每亩",
        "间隔期", "安全期", "停药期", "采收期"
    )
    val dosageRegexes = listOf(
        Regex("\\d+(\\.\\d+)?\\s*(克|kg|公斤)\\s*/\\s*(亩|升|l)"),
        Regex("\\d+(\\.\\d+)?\\s*(毫升|ml|升|l)\\s*/\\s*(亩|升|l)"),
        Regex("\\d+(\\.\\d+)?\\s*(克|毫升|ml|升|l|公斤|kg)\\s*(每亩|/亩)"),
        Regex("\\d+(\\.\\d+)?\\s*(克|毫升|ml|升|l)\\s*兑\\s*\\d+(\\.\\d+)?\\s*(升|l)?\\s*水"),
        Regex("\\d+(\\.\\d+)?\\s*ppm"),
        Regex("\\d+(\\.\\d+)?\\s*倍液")
    )
    if (dosageRegexes.any { it.containsMatchIn(normalized) }) return true
    val hasAction = actionKeywords.any { normalized.contains(it) }
    val hasTreatmentMaterial = treatmentMaterialKeywords.any { normalized.contains(it) }
    val hasDosage = dosageKeywords.any { normalized.contains(it) }
    return (hasAction && hasDosage) || (hasAction && hasTreatmentMaterial)
}

private fun assistantHeadingTextStyle(level: Int): TextStyle = TextStyle(
    fontSize = if (level <= 2) 20.sp else 18.sp,
    lineHeight = if (level <= 2) 31.sp else 28.sp,
    fontWeight = FontWeight.Bold,
    color = Color(0xFF111111),
    textMotion = TextMotion.Static,
    lineBreak = LineBreak.Heading
)

private fun assistantStreamingHeadingTextStyle(level: Int): TextStyle =
    assistantHeadingTextStyle(level).copy(lineBreak = LineBreak.Simple)

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

private fun applyCompletedAssistantMessageInPlace(
    target: MutableList<ChatMessage>,
    messageId: String,
    content: String
) {
    val normalizedContent = normalizeAssistantText(content)
    if (normalizedContent.isBlank()) return
    val finalId = messageId.ifBlank { "assistant_${UUID.randomUUID()}" }
    val finalMessage = ChatMessage(finalId, ChatRole.ASSISTANT, normalizedContent)
    val existingIndex = target.indexOfFirst { it.id == finalId }
    if (existingIndex >= 0) {
        if (target[existingIndex] != finalMessage) {
            target[existingIndex] = finalMessage
        }
    } else {
        target.add(finalMessage)
    }
    val trimCount = trimWindowStartIndex(target)
    if (trimCount > 0) {
        repeat(trimCount) { target.removeAt(0) }
    }
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

private fun Context.hasActiveNetworkConnection(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        (
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            )
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

private fun assistantMessageIdForSourceUser(sourceUserMessageId: String): String =
    "assistant_$sourceUserMessageId"

private suspend fun awaitRemoteSnapshot(): SessionSnapshot? =
    suspendCancellableCoroutine { continuation ->
        SessionApi.getSnapshot { snapshot ->
            if (continuation.isActive) {
                continuation.resume(snapshot)
            }
        }
    }

private fun SessionSnapshot.findRoundByClientMessageId(clientMessageId: String): ARound? {
    if (clientMessageId.isBlank()) return null
    return a_rounds_full
        .asReversed()
        .firstOrNull { it.client_msg_id == clientMessageId }
        ?: a_rounds_for_ui
            .asReversed()
            .firstOrNull { it.client_msg_id == clientMessageId }
}

private fun trailingRecoverableUserMessageId(source: List<ChatMessage>): String? =
    source.lastOrNull()
        ?.takeIf { it.role == ChatRole.USER && it.id.isNotBlank() }
        ?.id

private suspend fun prewarmAssistantMarkdown(messages: List<ChatMessage>) = withContext(Dispatchers.Default) {
    messages
        .filter { it.role == ChatRole.ASSISTANT && it.content.isNotBlank() }
        .takeLast(12)
        .forEach { getCachedMarkdownUiBlocks(it.content) }
}

private fun snapshotRoundsToMessages(rounds: List<ARound>): List<ChatMessage> {
    return rounds.flatMapIndexed { index, round ->
        buildList {
            val sourceUserMessageId = round.client_msg_id?.takeIf { it.isNotBlank() } ?: "remote_user_$index"
            if (round.user.isNotBlank()) add(ChatMessage(sourceUserMessageId, ChatRole.USER, round.user))
            if (round.assistant.isNotBlank()) {
                add(ChatMessage(assistantMessageIdForSourceUser(sourceUserMessageId), ChatRole.ASSISTANT, round.assistant))
            }
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
    streamingFreshStart: Int = -1,
    streamingFreshEnd: Int = -1,
    streamingFreshTick: Int = 0,
    streamingLineAdvanceTick: Int = 0,
    strictLineReveal: Boolean = false,
    lineRevealLocked: Boolean = false,
    selectionEnabled: Boolean = false,
    showDisclaimer: Boolean = true,
    onStreamingContentBoundsChanged: ((Rect?) -> Unit)? = null,
    expandToFullWidth: Boolean = true,
    modifier: Modifier = Modifier
) {
    val shouldRenderDisclaimer = remember(content, showDisclaimer) {
        showDisclaimer && shouldShowAiDisclaimerRefined(content)
    }
    val stableModifier = if (isStreaming) {
        modifier.heightIn(min = STREAMING_MESSAGE_MIN_HEIGHT)
    } else {
        modifier
    }
    if (isStreaming) {
        // Streaming: NO animateContentSize, NO AnimatedVisibility crossfade.
        // Simple conditional switch: ball OR text. Eliminates jitter/ghosting.
        Box(
            modifier = if (expandToFullWidth) {
                stableModifier.fillMaxWidth()
            } else {
                stableModifier
            },
            contentAlignment = Alignment.TopStart
        ) {
            if (content.isBlank()) {
                AssistantStreamingWaitingIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            onStreamingContentBoundsChanged?.invoke(coordinates.boundsInWindow())
                        }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coordinates ->
                                onStreamingContentBoundsChanged?.invoke(coordinates.boundsInWindow())
                            }
                    ) {
                        AssistantStreamingContent(
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
                    if (shouldRenderDisclaimer) {
                        Text(
                            text = AI_DISCLAIMER_TEXT,
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(0f),
                            style = assistantDisclaimerTextStyle(),
                            textAlign = TextAlign.Start
                        )
                    }
                }
            }
        }
    } else {
        Column(
            modifier = if (expandToFullWidth) {
                modifier.fillMaxWidth()
            } else {
                modifier
            },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (selectionEnabled) {
                SelectionContainer {
                    AssistantMarkdownContent(content = content)
                }
            } else {
                AssistantMarkdownContent(content = content)
            }
            if (shouldRenderDisclaimer) {
                Text(
                    text = AI_DISCLAIMER_TEXT,
                    modifier = Modifier
                        .fillMaxWidth(),
                    style = assistantDisclaimerTextStyle(),
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
private fun ChatInputField(
    value: TextFieldValue,
    focused: Boolean,
    suppressCursor: Boolean,
    settlingMinHeightPx: Int,
    suppressPlaceholder: Boolean,
    onValueChange: (TextFieldValue) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onContentHeightChanged: (Int) -> Unit,
    placeholder: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = 6,
    textStyle: TextStyle = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = Color(0xFF111111)
    ),
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
                    .padding(start = 14.dp, end = 10.dp, top = 11.dp, bottom = 11.dp)
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
                AssistantStreamingCommittedBlock(
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
            AssistantStreamingActiveBlock(
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
private fun StreamingSingleActiveLineText(
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
                StreamingAnimatedLineText(
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
private fun StreamingAnimatedLineText(
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
                lerp(
                    STREAM_FRESH_SUFFIX_HIGHLIGHT_COLOR,
                    baseColor,
                    settledProgress
                )
            } else {
                Color.Unspecified
            }
        buildAnnotatedString {
            append(text.subSequence(0, stableEnd))
            withStyle(
                SpanStyle(
                    color = freshColor
                )
            ) {
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
private fun rememberLockedStreamingRenderedLines(
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
            if (!lineRevealLocked) {
                activeLineUnlockedOnce = true
            }
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
private fun StreamingCommittedTextBlock(
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
        StreamingSingleActiveLineText(
            lines = measured,
            style = style,
            emptyLineHeight = emptyLineHeight,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AssistantStreamingCommittedBlock(
    model: StreamingLineModel,
    showLeadingSectionDivider: Boolean = false,
    modifier: Modifier = Modifier
) {
    @Composable
    fun RichText(
        text: String,
        modifier: Modifier = Modifier,
        style: TextStyle
    ) {
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
                Spacer(modifier = Modifier.height(SECTION_DIVIDER_TOP_EXTRA_GAP))
                MarkdownSectionDivider()
                Spacer(modifier = Modifier.height(SECTION_DIVIDER_GAP))
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
private fun AssistantStreamingActiveBlock(
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
    fun resolveRenderedLines(
        text: String,
        style: TextStyle,
        availableWidthPx: Int
    ): StreamingRenderedLines {
        return buildStableStreamingLineBuffer(
            text = AnnotatedString(text),
            style = style,
            availableWidthPx = availableWidthPx,
            textMeasurer = textMeasurer
        )
    }
    val animatedModifier = modifier.fillMaxWidth()

    BoxWithConstraints(modifier = animatedModifier) {
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        when (model) {
            StreamingLineModel.Blank -> Unit
            is StreamingLineModel.Heading -> {
                val headingStyle = assistantStreamingHeadingTextStyle(model.level)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    if (showLeadingSectionDivider && model.level <= 2) {
                        Spacer(modifier = Modifier.height(SECTION_DIVIDER_TOP_EXTRA_GAP))
                        MarkdownSectionDivider()
                        Spacer(modifier = Modifier.height(SECTION_DIVIDER_GAP))
                    }
                    val rawLines = remember(model.text, maxWidthPx) {
                        resolveRenderedLines(model.text, headingStyle, maxWidthPx)
                    }
                    val lines = rememberLockedStreamingRenderedLines(
                        lines = rawLines,
                        strictLineReveal = strictLineReveal,
                        lineRevealLocked = lineRevealLocked,
                        lineAdvanceTick = lineAdvanceTick,
                        maxVisibleCharsWhenLocked = 0
                    )
                    StreamingSingleActiveLineText(
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
                val rawLines = remember(model.text, bodyWidthPx) {
                    resolveRenderedLines(model.text, bodyStyle, bodyWidthPx)
                }
                val lines = rememberLockedStreamingRenderedLines(
                    lines = rawLines,
                    strictLineReveal = strictLineReveal,
                    lineRevealLocked = lineRevealLocked,
                    lineAdvanceTick = lineAdvanceTick,
                    maxVisibleCharsWhenLocked = 0
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    if (lines.activeLine != null || lines.stableLines.isNotEmpty()) {
                        val firstLine = lines.stableLines.firstOrNull() ?: lines.activeLine
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = paragraphLineHeight),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "\u2022",
                                style = bulletStyle
                            )
                            firstLine?.let { line ->
                                StreamingAnimatedLineText(
                                    text = line,
                                    modifier = Modifier.weight(1f),
                                    style = bodyStyle,
                                    freshTailChars = if (lines.stableLines.isEmpty()) freshTailChars else 0,
                                    freshTick = if (lines.stableLines.isEmpty()) freshTick else 0
                                )
                            }
                        }
                        lines.stableLines.drop(1).forEach { line ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = paragraphLineHeight)
                            ) {
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
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = paragraphLineHeight)
                                ) {
                                    Spacer(modifier = Modifier.width(gutterWidth))
                                    StreamingAnimatedLineText(
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
            is StreamingLineModel.Numbered -> {
                val numberStyle = assistantStreamingParagraphTextStyle().copy(fontWeight = FontWeight.SemiBold)
                val bodyStyle = assistantStreamingParagraphTextStyle()
                val numberWidthPx = remember(textMeasurer, model.number, numberStyle) {
                    textMeasurer.measure(AnnotatedString("${model.number}."), style = numberStyle).size.width
                }
                val bodyWidthPx = (maxWidthPx - numberWidthPx - spacingPx).coerceAtLeast(0)
                val gutterWidth = with(density) { (numberWidthPx + spacingPx).toDp() }
                val rawLines = remember(model.text, bodyWidthPx) {
                    resolveRenderedLines(model.text, bodyStyle, bodyWidthPx)
                }
                val lines = rememberLockedStreamingRenderedLines(
                    lines = rawLines,
                    strictLineReveal = strictLineReveal,
                    lineRevealLocked = lineRevealLocked,
                    lineAdvanceTick = lineAdvanceTick,
                    maxVisibleCharsWhenLocked = 0
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    if (lines.activeLine != null || lines.stableLines.isNotEmpty()) {
                        val firstLine = lines.stableLines.firstOrNull() ?: lines.activeLine
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = paragraphLineHeight),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${model.number}.",
                                style = numberStyle
                            )
                            firstLine?.let { line ->
                                StreamingAnimatedLineText(
                                    text = line,
                                    modifier = Modifier.weight(1f),
                                    style = bodyStyle,
                                    freshTailChars = if (lines.stableLines.isEmpty()) freshTailChars else 0,
                                    freshTick = if (lines.stableLines.isEmpty()) freshTick else 0
                                )
                            }
                        }
                        lines.stableLines.drop(1).forEach { line ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = paragraphLineHeight)
                            ) {
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
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = paragraphLineHeight)
                                ) {
                                    Spacer(modifier = Modifier.width(gutterWidth))
                                    StreamingAnimatedLineText(
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
            is StreamingLineModel.Quote -> {
                val quoteStyle = assistantStreamingParagraphTextStyle()
                val rawLines = remember(model.text, maxWidthPx) {
                    resolveRenderedLines(model.text, quoteStyle, maxWidthPx)
                }
                val lines = rememberLockedStreamingRenderedLines(
                    lines = rawLines,
                    strictLineReveal = strictLineReveal,
                    lineRevealLocked = lineRevealLocked,
                    lineAdvanceTick = lineAdvanceTick,
                    maxVisibleCharsWhenLocked = 0
                )
                StreamingSingleActiveLineText(
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
                val rawLines = remember(model.text, maxWidthPx) {
                    resolveRenderedLines(model.text, paragraphStyle, maxWidthPx)
                }
                val lines = rememberLockedStreamingRenderedLines(
                    lines = rawLines,
                    strictLineReveal = strictLineReveal,
                    lineRevealLocked = lineRevealLocked,
                    lineAdvanceTick = lineAdvanceTick,
                    maxVisibleCharsWhenLocked = 0
                )
                StreamingSingleActiveLineText(
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
private fun AssistantMarkdownContent(content: String, modifier: Modifier = Modifier) {
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
                    AssistantStreamingActiveBlock(
                        model = model,
                        showLeadingSectionDivider = showLeadingSectionDivider,
                        lineAdvanceTick = 0,
                        strictLineReveal = false,
                        lineRevealLocked = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    AssistantStreamingCommittedBlock(
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
private fun GPTBreathingBall(modifier: Modifier = Modifier) {
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
        Canvas(
            modifier = Modifier
                .size(GPT_BALL_CONTAINER_SIZE)
        ) {
            val baseRadius = GPT_BALL_SIZE.toPx() / 2f
            val radius = baseRadius * scale
            val center = this.center

            drawCircle(
                color = Color.Black,
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
        border = BorderStroke(1.28.dp, borderColor.copy(alpha = 0.96f)),
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
private fun ComposerSendActionButton(
    size: Dp,
    backgroundColor: Color,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .padding(start = 6.dp)
            .size(size)
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
        LongArrowIcon(
            tint = tint,
            directionUp = true,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun ComposerInputShell(
    modifier: Modifier = Modifier,
    inputFieldSurface: Color,
    inputFieldBorder: Color,
    inputBarHeight: Dp,
    inputBarMaxHeight: Dp,
    content: @Composable RowScope.() -> Unit,
    sendButtonSize: Dp,
    sendButtonEnabled: Boolean,
    sendButtonBackgroundColor: Color,
    sendButtonTint: Color,
    onSendClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = inputFieldSurface,
        border = BorderStroke(1.22.dp, inputFieldBorder.copy(alpha = 0.98f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier
            .shadow(
                elevation = 1.35.dp,
                shape = RoundedCornerShape(30.dp),
                ambientColor = Color(0x14000000),
                spotColor = Color(0x14000000)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = inputBarHeight, max = inputBarMaxHeight)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
            ComposerSendActionButton(
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
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        FrostedCircleButton(
            size = addButtonSize,
            surfaceColor = inputChromeSurface,
            borderColor = inputChromeBorder,
            onClick = onAddClick
        ) {
            PlusCrossIcon(
                tint = Color(0xFF6F7277),
                modifier = Modifier.size(addIconSize)
            )
        }

        ComposerInputShell(
            modifier = Modifier.weight(1f).then(inputShellModifier),
            inputFieldSurface = inputFieldSurface,
            inputFieldBorder = inputFieldBorder,
            inputBarHeight = inputBarHeight,
            inputBarMaxHeight = inputBarMaxHeight,
            content = inputContent,
            sendButtonSize = sendButtonSize,
            sendButtonEnabled = sendButtonEnabled,
            sendButtonBackgroundColor = sendButtonBackgroundColor,
            sendButtonTint = sendButtonTint,
            onSendClick = onSendClick
        )
    }
}

@Composable
@OptIn(
    ExperimentalComposeUiApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
    FlowPreview::class
)
fun ChatScreen() {
    val input = rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val inputSelectionColors = remember {
        TextSelectionColors(
            handleColor = CHAT_SELECTION_HANDLE_COLOR,
            backgroundColor = CHAT_SELECTION_BACKGROUND_COLOR
        )
    }
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
    val startupRecoverableUserMessageId = remember(chatScopeId, initialLocalMessages, hasRemoteHistorySource) {
        if (hasRemoteHistorySource) trailingRecoverableUserMessageId(initialLocalMessages) else null
    }
    val messages = remember(chatScopeId) {
        mutableStateListOf<ChatMessage>().apply { addAll(initialLocalMessages) }
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
        cacheWindow = LazyLayoutCacheWindow(
            ahead = STATIC_SELECTION_CACHE_WINDOW,
            behind = STATIC_SELECTION_CACHE_WINDOW
        ),
        initialFirstVisibleItemIndex = initialListIndex,
        initialFirstVisibleItemScrollOffset = initialListScrollOffset
    )
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    var fakeStreamJob by remember { mutableStateOf<Job?>(null) }
    var streamRevealJob by remember { mutableStateOf<Job?>(null) }
    val density = LocalDensity.current
    val startupBottomBarHeightEstimatePx = with(density) { STARTUP_BOTTOM_BAR_HEIGHT_ESTIMATE.roundToPx() }
    val startupInputChromeRowHeightEstimatePx = with(density) { STARTUP_INPUT_CHROME_ROW_HEIGHT_ESTIMATE.roundToPx() }
    val startupInputContentHeightEstimatePx = with(density) { 22.sp.roundToPx() }

    var isStreaming by rememberSaveable(chatScopeId) { mutableStateOf(false) }
    var streamingMessageId by rememberSaveable(chatScopeId) { mutableStateOf<String?>(null) }
    var streamingMessageContent by rememberSaveable(chatScopeId) { mutableStateOf("") }
    var streamingRevealBuffer by rememberSaveable(chatScopeId) { mutableStateOf("") }
    var scrollMode by remember { mutableStateOf(ScrollMode.Idle) }
    var autoScrollMode by remember { mutableStateOf(AutoScrollMode.Idle) }
    var userInteracting by remember { mutableStateOf(false) }
    var streamTick by remember { mutableIntStateOf(0) }
    var streamingLineAdvanceTick by remember { mutableIntStateOf(0) }
    var streamingFreshStart by remember { mutableIntStateOf(-1) }
    var streamingFreshEnd by remember { mutableIntStateOf(-1) }
    var streamingFreshTick by remember { mutableIntStateOf(0) }
    var lastStreamingFreshRevealMs by remember { mutableStateOf(0L) }
    var sendTick by remember { mutableIntStateOf(0) }
    var programmaticScroll by remember { mutableStateOf(false) }
    var lastProgrammaticScrollMs by remember { mutableStateOf(0L) }
    var persistTick by remember { mutableIntStateOf(0) }
    var bottomBarHeightPx by remember(chatScopeId, startupBottomBarHeightEstimatePx) {
        mutableIntStateOf(startupBottomBarHeightEstimatePx)
    }
    var inputChromeRowHeightPx by remember(chatScopeId, startupInputChromeRowHeightEstimatePx) {
        mutableIntStateOf(startupInputChromeRowHeightEstimatePx)
    }
    var chatRootWidthPx by remember { mutableIntStateOf(0) }
    var chatRootHeightPx by remember { mutableIntStateOf(0) }
    var messageViewportWidthPx by remember { mutableIntStateOf(0) }
    var messageViewportHeightPx by remember { mutableIntStateOf(0) }
    var streamAnchorReservePx by rememberSaveable(chatScopeId) { mutableStateOf(0) }
    var initialStreamAnchorReservePx by rememberSaveable(chatScopeId) { mutableStateOf(0) }
    var chatRootLeftPx by remember { mutableStateOf(0f) }
    var chatRootTopPx by remember { mutableStateOf(0f) }
    var messageViewportLeftPx by remember { mutableStateOf(0f) }
    var messageViewportTopPx by remember { mutableStateOf(0f) }
    var composerTopInViewportPx by remember { mutableIntStateOf(-1) }
    var topChromeMaskBottomPx by remember { mutableIntStateOf(-1) }
    var anchoredUserMessageId by rememberSaveable(chatScopeId) { mutableStateOf<String?>(null) }
    var streamingAnchorTopPx by remember { mutableIntStateOf(-1) }
    var streamingContentBottomPx by remember { mutableIntStateOf(-1) }
    var streamBottomFollowActive by remember { mutableStateOf(false) }
    var initialBottomSnapDone by remember(chatScopeId) { mutableStateOf(false) }
    var hasStartedConversation by rememberSaveable(chatScopeId) { mutableStateOf(false) }
    var jumpButtonVisible by remember { mutableStateOf(false) }
    var userDetachedFromBottom by remember { mutableStateOf(false) }
    var pendingResumeAutoFollow by remember { mutableStateOf(false) }
    var remoteRecoveryJob by remember(chatScopeId) { mutableStateOf<Job?>(null) }
    var remoteRecoverySourceUserMessageId by rememberSaveable(chatScopeId) { mutableStateOf<String?>(null) }
    var pendingFinalBottomSnap by remember { mutableStateOf(false) }
    var pendingFinalBottomSnapAfterSpacer by remember(chatScopeId) { mutableStateOf(false) }
    var pendingStreamSpacerRelease by remember(chatScopeId) { mutableStateOf(false) }
    var restoreBottomAfterImeClose by remember { mutableStateOf(false) }
    var suppressJumpButtonForImeTransition by remember { mutableStateOf(false) }
    var restoreBottomAfterLifecycleResume by remember { mutableStateOf(false) }
    var suppressJumpButtonForLifecycleResume by remember { mutableStateOf(false) }
    var lifecycleResumeReady by remember { mutableStateOf(false) }
    var streamingBackgrounded by rememberSaveable(chatScopeId) { mutableStateOf(false) }
    var inputLimitHintVisible by remember { mutableStateOf(false) }
    var inputLimitHintTick by remember { mutableIntStateOf(0) }
    var composerStatusHintVisible by remember { mutableStateOf(false) }
    var composerStatusHintTick by remember { mutableIntStateOf(0) }
    var composerStatusHintText by remember { mutableStateOf("") }
    var inputFieldFocused by remember(chatScopeId) { mutableStateOf(false) }
    var suppressInputCursor by remember(chatScopeId) { mutableStateOf(false) }
    var inputContentHeightPx by remember(chatScopeId, startupInputContentHeightEstimatePx) {
        mutableIntStateOf(startupInputContentHeightEstimatePx)
    }
    var composerSettlingMinHeightPx by remember(chatScopeId) { mutableIntStateOf(0) }
    var composerSettlingChromeHeightPx by remember(chatScopeId) { mutableIntStateOf(0) }
    var sendUiSettling by remember(chatScopeId) { mutableStateOf(false) }
    val failedUserMessageStates = remember(chatScopeId) { mutableStateMapOf<String, String>() }
    val failedAssistantMessageStates = remember(chatScopeId) {
        mutableStateMapOf<String, FailedAssistantMessageState>()
    }
    val minSendAnchorExtraBottomSpacePx = with(density) { MIN_SEND_ANCHOR_EXTRA_BOTTOM_SPACE.toPx().roundToInt() }
    val streamAnchorReleaseThresholdPx = with(density) { 50.dp.toPx().roundToInt() }
    val assistantStartAnchorTopPx = with(density) { ASSISTANT_START_ANCHOR_TOP.toPx().roundToInt() }
    val streamVisibleBottomGapPx = with(density) { STREAM_VISIBLE_BOTTOM_GAP.toPx().roundToInt() }
    val bottomPositionTolerancePx = with(density) { BOTTOM_POSITION_TOLERANCE.roundToPx() }
    val finalBottomSnapTolerancePx = with(density) { FINAL_BOTTOM_SNAP_TOLERANCE.roundToPx() }
    val lifecycleResumeBottomSnapThresholdPx = with(density) { LIFECYCLE_RESUME_BOTTOM_SNAP_THRESHOLD.roundToPx() }
    val assistantLineStepPx = with(density) {
        assistantParagraphTextStyle().lineHeight.toPx().roundToInt().coerceAtLeast(STREAM_BOTTOM_FOLLOW_STEP_PX)
    }
    val finalBottomSnapThresholdPx = remember(finalBottomSnapTolerancePx, assistantLineStepPx) {
        maxOf(finalBottomSnapTolerancePx, (assistantLineStepPx * 0.28f).roundToInt().coerceAtLeast(10))
    }
    // Keep anchor reserve and visible spacer as separate concepts.
    // The reserve supports send/start anchoring; the visible spacer is only the current presentation.
    val streamAnchorReserveActive by remember(
        isStreaming,
        pendingStreamSpacerRelease,
        anchoredUserMessageId
    ) {
        derivedStateOf {
            (isStreaming || pendingStreamSpacerRelease) && anchoredUserMessageId != null
        }
    }
    val activeStreamBottomSpacerPx = if (streamAnchorReserveActive) {
        streamAnchorReservePx
    } else {
        0
    }
    val guardedStreamBottomSpacerPx = if (streamAnchorReserveActive) {
        streamAnchorReservePx
    } else {
        0
    }
    val imeVisible = WindowInsets.isImeVisible
    val streamBottomSpacerDp = with(density) { activeStreamBottomSpacerPx.toDp() }
    val hasStreamAnchorSpacer by remember(activeStreamBottomSpacerPx) {
        derivedStateOf { activeStreamBottomSpacerPx > 0 }
    }
    val lineRevealLockThresholdPx = remember(assistantLineStepPx) {
        (assistantLineStepPx * 0.16f).roundToInt().coerceAtLeast(6)
    }
    val lineRevealUnlockThresholdPx = remember(assistantLineStepPx) {
        (assistantLineStepPx * 0.06f).roundToInt().coerceAtLeast(3)
    }
    val hasStreamingItem by remember(isStreaming, pendingStreamSpacerRelease, streamingMessageId) {
        derivedStateOf { isStreaming || pendingStreamSpacerRelease || !streamingMessageId.isNullOrBlank() }
    }
    val streamingWorklineBottomPx by remember(
        messageViewportHeightPx,
        bottomBarHeightPx,
        composerTopInViewportPx,
        streamVisibleBottomGapPx,
        imeVisible
    ) {
        derivedStateOf {
            if (!imeVisible && composerTopInViewportPx > 0) {
                (composerTopInViewportPx - streamVisibleBottomGapPx).coerceAtLeast(0)
            } else {
                (
                    messageViewportHeightPx -
                        bottomBarHeightPx -
                        streamVisibleBottomGapPx
                    ).coerceAtLeast(0)
            }
        }
    }
    val visibleStreamingBottomBlankPx by remember(
        isStreaming,
        streamingMessageContent,
        guardedStreamBottomSpacerPx,
        streamingContentBottomPx,
        streamingWorklineBottomPx
    ) {
        derivedStateOf {
            if (
                !isStreaming ||
                streamingMessageContent.isBlank() ||
                guardedStreamBottomSpacerPx <= 0
            ) {
                return@derivedStateOf 0
            }
            if (streamingContentBottomPx <= 0 || streamingWorklineBottomPx <= 0) {
                return@derivedStateOf 0
            }
            (streamingWorklineBottomPx - streamingContentBottomPx).coerceAtLeast(0)
        }
    }
    var lineRevealLocked by remember(chatScopeId) { mutableStateOf(false) }
    val lockUserScrollDuringBall by remember(isStreaming, streamingMessageContent, guardedStreamBottomSpacerPx) {
        derivedStateOf {
            isStreaming &&
                streamingMessageContent.isBlank() &&
                guardedStreamBottomSpacerPx > 0
        }
    }
    val lockBottomBlankDuringStreaming by remember(
        isStreaming,
        streamingMessageContent,
        visibleStreamingBottomBlankPx
    ) {
        derivedStateOf {
            isStreaming &&
                streamingMessageContent.isNotBlank() &&
                visibleStreamingBottomBlankPx > 0
        }
    }
    LaunchedEffect(
        isStreaming,
        userDetachedFromBottom,
        userInteracting,
        streamBottomFollowActive,
        streamingContentBottomPx,
        streamingWorklineBottomPx,
        lineRevealLockThresholdPx,
        lineRevealUnlockThresholdPx
    ) {
        val overflowPx = if (streamingContentBottomPx > 0 && streamingWorklineBottomPx > 0) {
            (streamingContentBottomPx - streamingWorklineBottomPx).coerceAtLeast(0)
        } else {
            0
        }
        val nextLocked = when {
            !isStreaming || userDetachedFromBottom || userInteracting -> false
            streamBottomFollowActive -> true
            overflowPx <= 0 -> false
            lineRevealLocked -> overflowPx > lineRevealUnlockThresholdPx
            else -> overflowPx > lineRevealLockThresholdPx
        }
        if (lineRevealLocked != nextLocked) {
            lineRevealLocked = nextLocked
        }
    }
    fun currentStreamingMeasuredBottomPx(): Int {
        if (streamingContentBottomPx > 0) return streamingContentBottomPx
        val streamingItemIndex = messages.size - 1
        val info = listState.layoutInfo
        if (streamingItemIndex >= 0) {
            val streamingLayoutItem = info.visibleItemsInfo.lastOrNull { it.index == streamingItemIndex }
            if (streamingLayoutItem != null) {
                return streamingLayoutItem.offset + streamingLayoutItem.size
            }
        }
        val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return -1
        return lastVisible.offset + lastVisible.size
    }
    fun distanceToStreamAnchorSpacerRevealPx(): Int {
        if (!hasStreamAnchorSpacer || guardedStreamBottomSpacerPx <= 0) return Int.MAX_VALUE
        val spacerIndex = messages.size
        val contentIndex = spacerIndex - 1
        if (contentIndex < 0) return 0
        val info = listState.layoutInfo
        val contentItem = info.visibleItemsInfo.lastOrNull { it.index == contentIndex } ?: return Int.MAX_VALUE
        return (contentItem.offset + contentItem.size - info.viewportEndOffset).coerceAtLeast(0)
    }
    val streamingDirectionLock = remember(
        scrollMode,
        isStreaming,
        hasStreamingItem,
        lockUserScrollDuringBall,
        lockBottomBlankDuringStreaming,
        userDetachedFromBottom,
        guardedStreamBottomSpacerPx,
        hasStreamAnchorSpacer
    ) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (
                    isStreaming &&
                    hasStreamingItem &&
                    scrollMode == ScrollMode.AutoFollow &&
                    available.y != 0f
                ) {
                    userInteracting = true
                    streamBottomFollowActive = false
                    scrollMode = ScrollMode.UserBrowsing
                    pendingResumeAutoFollow = false
                    userDetachedFromBottom = true
                    autoScrollMode = AutoScrollMode.AnchorUser
                    jumpButtonVisible = false
                    return Offset.Zero
                }
                if (
                    isStreaming &&
                    hasStreamingItem &&
                    guardedStreamBottomSpacerPx > 0 &&
                    available.y < 0f
                ) {
                    val distanceToSpacer = distanceToStreamAnchorSpacerRevealPx()
                    if (distanceToSpacer != Int.MAX_VALUE) {
                        val requestedPx = -available.y
                        val allowedPx = distanceToSpacer.toFloat()
                        if (allowedPx <= 0f) {
                            return Offset(x = 0f, y = available.y)
                        }
                        if (requestedPx > allowedPx) {
                            val consumedPx = requestedPx - allowedPx
                            return Offset(x = 0f, y = -consumedPx)
                        }
                    }
                }
                if (
                    lockUserScrollDuringBall &&
                    guardedStreamBottomSpacerPx > 0 &&
                    available.y < 0f
                ) {
                    val dragPx = -available.y
                    val consumePx = dragPx.coerceAtMost(streamAnchorReservePx.toFloat())
                    if (consumePx > 0f) {
                        streamAnchorReservePx = consumeStreamingBottomSpacer(streamAnchorReservePx, consumePx)
                        return Offset(x = 0f, y = available.y)
                    }
                }
                if (
                    lockBottomBlankDuringStreaming &&
                    available.y < 0f &&
                    visibleStreamingBottomBlankPx > 0
                ) {
                    return Offset(x = 0f, y = available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (
                    isStreaming &&
                    hasStreamingItem &&
                    scrollMode == ScrollMode.AutoFollow &&
                    available.y != 0f
                ) {
                    userInteracting = true
                    streamBottomFollowActive = false
                    scrollMode = ScrollMode.UserBrowsing
                    pendingResumeAutoFollow = false
                    userDetachedFromBottom = true
                    autoScrollMode = AutoScrollMode.AnchorUser
                    jumpButtonVisible = false
                    return Velocity.Zero
                }
                if (
                    isStreaming &&
                    hasStreamingItem &&
                    guardedStreamBottomSpacerPx > 0 &&
                    available.y < 0f
                ) {
                    val distanceToSpacer = distanceToStreamAnchorSpacerRevealPx()
                    if (distanceToSpacer != Int.MAX_VALUE) {
                        return available
                    }
                }
                if (
                    lockBottomBlankDuringStreaming &&
                    available.y < 0f
                ) {
                    return available
                }
                return Velocity.Zero
            }
        }
    }
    val enableStreamingScrollLock by remember(
        lockUserScrollDuringBall,
        lockBottomBlankDuringStreaming,
        isStreaming,
        hasStreamingItem,
        scrollMode,
        userDetachedFromBottom,
        guardedStreamBottomSpacerPx
    ) {
        derivedStateOf {
            lockUserScrollDuringBall ||
                lockBottomBlankDuringStreaming ||
                (isStreaming && hasStreamingItem && scrollMode == ScrollMode.AutoFollow) ||
                (
                    isStreaming &&
                        hasStreamingItem &&
                        userDetachedFromBottom &&
                        guardedStreamBottomSpacerPx > 0
                    )
        }
    }
    fun currentBottomOverflowPx(): Int {
        if (!listState.canScrollForward) return 0
        val info = listState.layoutInfo
        val lastIndex = info.totalItemsCount - 1
        if (lastIndex < 0) return 0
        val lastVisible = info.visibleItemsInfo.lastOrNull { it.index == lastIndex } ?: return Int.MAX_VALUE
        return (lastVisible.offset + lastVisible.size - info.viewportEndOffset).coerceAtLeast(0)
    }
    fun isWithinBottomTolerance(): Boolean {
        val overflowPx = currentBottomOverflowPx()
        return overflowPx != Int.MAX_VALUE && overflowPx <= bottomPositionTolerancePx
    }
    fun isWithinFinalBottomSnapTolerance(): Boolean {
        val overflowPx = currentBottomOverflowPx()
        return overflowPx != Int.MAX_VALUE && overflowPx <= finalBottomSnapTolerancePx
    }
    val atBottom by remember(bottomPositionTolerancePx) {
        derivedStateOf { isWithinBottomTolerance() }
    }
    fun currentStreamingVisualBottomPx(): Int {
        return currentStreamingMeasuredBottomPx()
    }
    fun isNearStreamingReturnLine(): Boolean {
        if (!isStreaming || !hasStreamingItem) return atBottom
        val worklineBottom = streamingWorklineBottomPx
        if (worklineBottom <= 0) return atBottom
        val contentBottom = currentStreamingVisualBottomPx()
        if (contentBottom <= 0) return false
        return contentBottom >= (worklineBottom - bottomPositionTolerancePx)
    }
    fun isAtStreamingFollowBoundary(): Boolean {
        if (!isStreaming || !hasStreamingItem) return atBottom
        if (streamingMessageContent.isBlank()) return true
        if (streamingWorklineBottomPx <= 0) return false
        return isNearStreamingReturnLine()
    }
    fun resolveStreamingIdleMode(): AutoScrollMode {
        return if (
            isStreaming &&
            hasStreamingItem &&
            streamingMessageContent.isNotBlank()
        ) {
            AutoScrollMode.StreamAnchorFollow
        } else if (isStreaming && hasStreamingItem) {
            AutoScrollMode.AnchorUser
        } else {
            AutoScrollMode.Idle
        }
    }
    fun applyStreamingScrollState(
        detached: Boolean,
        mode: AutoScrollMode,
        hideJumpButton: Boolean = true
    ) {
        scrollMode = when {
            mode == AutoScrollMode.Idle -> ScrollMode.Idle
            mode == AutoScrollMode.StreamAnchorFollow && !detached -> ScrollMode.AutoFollow
            detached -> ScrollMode.UserBrowsing
            else -> ScrollMode.Returning
        }
        pendingResumeAutoFollow = false
        userDetachedFromBottom = detached
        autoScrollMode = mode
        if (hideJumpButton) {
            jumpButtonVisible = false
        }
    }
    LaunchedEffect(scrollMode) {
        val derivedAutoScrollMode = when (scrollMode) {
            ScrollMode.Idle -> AutoScrollMode.Idle
            ScrollMode.AutoFollow -> AutoScrollMode.StreamAnchorFollow
            ScrollMode.UserBrowsing, ScrollMode.Returning -> AutoScrollMode.AnchorUser
        }
        val derivedDetached = scrollMode == ScrollMode.UserBrowsing
        if (autoScrollMode != derivedAutoScrollMode) {
            autoScrollMode = derivedAutoScrollMode
        }
        if (userDetachedFromBottom != derivedDetached) {
            userDetachedFromBottom = derivedDetached
        }
        if (scrollMode == ScrollMode.Idle || scrollMode == ScrollMode.AutoFollow) {
            jumpButtonVisible = false
        }
    }
    val appCenterTint = Color.White
    val chromeSurface = Color.White
    val chromeBorder = Color(0xFFD8DADF).copy(alpha = 0.18f)
    val userBubbleColor = Color(0xFFF4F4F7)
    var inputChromeMeasured by remember(chatScopeId) { mutableStateOf(false) }
    var messageViewportMeasured by remember(chatScopeId) { mutableStateOf(false) }
    var composerMeasured by remember(chatScopeId) { mutableStateOf(false) }
    var historyHydrationComplete by remember(chatScopeId) {
        mutableStateOf(initialLocalMessages.isNotEmpty() || !shouldHydrateRemoteHistory)
    }
    val startupHydrationBarrierSatisfied by remember(
        historyHydrationComplete,
        shouldHydrateRemoteHistory,
        initialLocalMessages.size,
        startupRecoverableUserMessageId,
        hasStartedConversation,
        hasStreamingItem
    ) {
        derivedStateOf {
            historyHydrationComplete ||
                (
                    shouldHydrateRemoteHistory &&
                        initialLocalMessages.isEmpty() &&
                        startupRecoverableUserMessageId == null &&
                        !hasStartedConversation &&
                        !hasStreamingItem
                    )
        }
    }
    val startupLayoutReady by remember(
        startupHydrationBarrierSatisfied,
        messageViewportMeasured,
        inputChromeMeasured,
        composerMeasured,
        bottomBarHeightPx
    ) {
        derivedStateOf {
            startupHydrationBarrierSatisfied &&
                messageViewportMeasured &&
                inputChromeMeasured &&
                composerMeasured &&
                bottomBarHeightPx > 0
        }
    }
    val effectiveBottomBarHeightPx by remember(
        startupLayoutReady,
        bottomBarHeightPx,
        startupBottomBarHeightEstimatePx
    ) {
        derivedStateOf {
            if (startupLayoutReady) {
                bottomBarHeightPx
            } else {
                startupBottomBarHeightEstimatePx
            }
        }
    }
    val shouldRevealMessageList by remember(
        startupLayoutReady,
        startupHydrationBarrierSatisfied,
        hasStartedConversation,
        messages.size,
        hasStreamingItem,
        initialBottomSnapDone
    ) {
        derivedStateOf {
            when {
                !startupHydrationBarrierSatisfied -> false
                !startupLayoutReady -> false
                hasStartedConversation -> true
                hasStreamingItem -> true
                messages.isEmpty() -> true
                initialBottomSnapDone -> true
                else -> false
            }
        }
    }
    val showWelcomePlaceholder by remember(
        startupLayoutReady,
        startupHydrationBarrierSatisfied,
        messages.size,
        hasStreamingItem
    ) {
        derivedStateOf {
            startupLayoutReady && startupHydrationBarrierSatisfied && messages.isEmpty() && !hasStreamingItem
        }
    }
    val topInset = WindowInsets.safeDrawing
        .only(WindowInsetsSides.Top)
        .asPaddingValues()
        .calculateTopPadding()
    val safeBottomInsetPx = with(density) {
        WindowInsets.safeDrawing
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding()
            .roundToPx()
    }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var inputSelectionToolbarState by remember(chatScopeId) {
        mutableStateOf<InputSelectionToolbarState?>(null)
    }
    var pendingInputSelectionToolbarState by remember(chatScopeId) {
        mutableStateOf<PendingInputSelectionToolbarState?>(null)
    }
    var inputSelectionMenuBoundsInRoot by remember(chatScopeId) { mutableStateOf<Rect?>(null) }
    var inputFieldBoundsInWindow by remember(chatScopeId) { mutableStateOf<Rect?>(null) }
    var composerHostBoundsInWindow by remember(chatScopeId) { mutableStateOf<Rect?>(null) }
    var composerChromeBoundsInWindow by remember(chatScopeId) { mutableStateOf<Rect?>(null) }
    var composerCollapseOverlayVisible by remember(chatScopeId) { mutableStateOf(false) }
    var composerCollapseOverlayHostBoundsSnapshot by remember(chatScopeId) { mutableStateOf<Rect?>(null) }
    var composerCollapseOverlayChromeBoundsSnapshot by remember(chatScopeId) { mutableStateOf<Rect?>(null) }
    var composerCollapseOverlayBottomHeightPx by remember(chatScopeId) { mutableIntStateOf(0) }
    var composerCollapseOverlayPrewarmed by remember(chatScopeId) { mutableStateOf(false) }
    val bottomContentReservedHeightPx by remember(
        composerCollapseOverlayVisible,
        composerCollapseOverlayBottomHeightPx,
        effectiveBottomBarHeightPx
    ) {
        derivedStateOf {
            if (composerCollapseOverlayVisible && composerCollapseOverlayBottomHeightPx > 0) {
                composerCollapseOverlayBottomHeightPx
            } else {
                effectiveBottomBarHeightPx
            }
        }
    }
    val jumpButtonBottomPadding = with(density) {
        effectiveBottomBarHeightPx.toDp() + JUMP_BUTTON_EXTRA_BOTTOM_CLEARANCE
    }
    val keyboardVisibleForJumpButton = WindowInsets.isImeVisible
    val shouldOfferJumpButton by remember(
        startupLayoutReady,
        atBottom,
        isStreaming,
        hasStreamingItem,
        scrollMode,
        streamingContentBottomPx,
        streamingWorklineBottomPx,
        messages.size,
        pendingFinalBottomSnap,
        pendingStreamSpacerRelease,
        keyboardVisibleForJumpButton,
        suppressJumpButtonForImeTransition,
        suppressJumpButtonForLifecycleResume
    ) {
        derivedStateOf {
            val atFollowBoundary = isAtStreamingFollowBoundary()
            startupLayoutReady &&
                !pendingStreamSpacerRelease &&
                !pendingFinalBottomSnap &&
                !keyboardVisibleForJumpButton &&
                !suppressJumpButtonForImeTransition &&
                !suppressJumpButtonForLifecycleResume &&
                (messages.isNotEmpty() || hasStreamingItem) &&
                (
                    !isStreaming ||
                        scrollMode == ScrollMode.UserBrowsing
                    ) &&
                !atFollowBoundary
        }
    }
    LaunchedEffect(
        startupLayoutReady,
        startupHydrationBarrierSatisfied,
        hasStartedConversation,
        messages.size,
        hasStreamingItem,
        initialBottomSnapDone
    ) {
        LaunchUiGate.chatReady =
            when {
                !startupHydrationBarrierSatisfied -> false
                !startupLayoutReady -> false
                hasStartedConversation -> true
                hasStreamingItem -> true
                messages.isEmpty() -> true
                else -> initialBottomSnapDone
            }
    }
    var messageSelectionToolbarState by remember(chatScopeId) {
        mutableStateOf<MessageSelectionToolbarState?>(null)
    }
    var pendingMessageSelectionToolbarState by remember(chatScopeId) {
        mutableStateOf<PendingMessageSelectionToolbarState?>(null)
    }
    var messageSelectionResetEpoch by remember { mutableIntStateOf(0) }
    val messageSelectionBoundsById = remember(chatScopeId) { mutableStateMapOf<String, Rect>() }
    fun currentSelectionMessageBounds(state: MessageSelectionToolbarState): Rect? =
        messageSelectionBoundsById[state.messageId]

    fun buildResolvedMessageSelectionToolbarState(
        messageId: String,
        rect: Rect,
        bounds: Rect,
        onCopyRequested: (() -> Unit)?,
        onCopyFullRequested: (() -> Unit)?
    ): MessageSelectionToolbarState {
        val width = bounds.width.coerceAtLeast(1f)
        val height = bounds.height.coerceAtLeast(1f)
        return MessageSelectionToolbarState(
            messageId = messageId,
            anchorX = rect.center.x.roundToInt(),
            anchorY = rect.top.roundToInt(),
            selectionBottomY = rect.bottom.roundToInt(),
            anchorXRatio = ((rect.center.x - bounds.left) / width).coerceIn(0f, 1f),
            selectionTopRatio = ((rect.top - bounds.top) / height).coerceIn(0f, 1f),
            selectionBottomRatio = ((rect.bottom - bounds.top) / height).coerceIn(0f, 1f),
            onCopyRequested = onCopyRequested,
            onCopyFullRequested = onCopyFullRequested
        )
    }

    fun resolveMessageSelectionToolbarState(state: MessageSelectionToolbarState): MessageSelectionToolbarState? {
        val bounds = currentSelectionMessageBounds(state) ?: return null
        val width = bounds.width.coerceAtLeast(1f)
        val height = bounds.height.coerceAtLeast(1f)
        return state.copy(
            anchorX = (bounds.left + width * state.anchorXRatio).roundToInt(),
            anchorY = (bounds.top + height * state.selectionTopRatio).roundToInt(),
            selectionBottomY = (bounds.top + height * state.selectionBottomRatio).roundToInt()
        )
    }

    fun clearMessageSelection() {
        messageSelectionToolbarState = null
        pendingMessageSelectionToolbarState = null
        messageSelectionResetEpoch++
    }

    fun applyPendingMessageSelectionToolbarIfReady(messageId: String, bounds: Rect) {
        val pending = pendingMessageSelectionToolbarState ?: return
        if (pending.messageId != messageId) return
        pendingMessageSelectionToolbarState = null
        messageSelectionToolbarState = buildResolvedMessageSelectionToolbarState(
            messageId = pending.messageId,
            rect = pending.rect,
            bounds = bounds,
            onCopyRequested = pending.onCopyRequested,
            onCopyFullRequested = pending.onCopyFullRequested
        )
    }

    fun selectionDismissTapModifier(vararg keys: Any): Modifier =
        Modifier.pointerInput(*keys) {
            detectTapGestures(onTap = { clearMessageSelection() })
        }

    val activeMessageSelectionState =
        messageSelectionToolbarState?.let(::resolveMessageSelectionToolbarState)
    val hasActiveMessageSelection = activeMessageSelectionState != null
    val activeMessageSelectionMessageId = activeMessageSelectionState?.messageId
    val messageSelectionColors = remember {
        TextSelectionColors(
            handleColor = CHAT_SELECTION_HANDLE_COLOR,
            backgroundColor = CHAT_SELECTION_BACKGROUND_COLOR
        )
    }
    fun clearInputSelectionToolbar() {
        inputSelectionToolbarState = null
        pendingInputSelectionToolbarState = null
        inputSelectionMenuBoundsInRoot = null
    }

    fun buildResolvedInputSelectionToolbarState(
        rect: Rect,
        bounds: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ): InputSelectionToolbarState {
        val width = bounds.width.coerceAtLeast(1f)
        return InputSelectionToolbarState(
            anchorXRatio = ((rect.center.x - bounds.left) / width).coerceIn(0f, 1f),
            onCopyRequested = onCopyRequested,
            onPasteRequested = onPasteRequested,
            onCutRequested = onCutRequested,
            onSelectAllRequested = onSelectAllRequested
        )
    }

    fun applyPendingInputSelectionToolbarIfReady(bounds: Rect) {
        val pending = pendingInputSelectionToolbarState ?: return
        pendingInputSelectionToolbarState = null
        inputSelectionToolbarState = buildResolvedInputSelectionToolbarState(
            rect = pending.rect,
            bounds = bounds,
            onCopyRequested = pending.onCopyRequested,
            onPasteRequested = pending.onPasteRequested,
            onCutRequested = pending.onCutRequested,
            onSelectAllRequested = pending.onSelectAllRequested
        )
    }
    fun copyTextToClipboard(label: String, text: String) {
        val normalized = text.trim()
        if (normalized.isEmpty()) return
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(label, normalized))
    }

    fun buildInputSelectionTextToolbar(): TextToolbar {
        return object : TextToolbar {
            override val status: TextToolbarStatus
                get() =
                    if (inputSelectionToolbarState != null) {
                        TextToolbarStatus.Shown
                    } else {
                        TextToolbarStatus.Hidden
                    }

            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {
                val bounds = inputFieldBoundsInWindow
                if (bounds == null) {
                    pendingInputSelectionToolbarState = PendingInputSelectionToolbarState(
                        rect = rect,
                        onCopyRequested = onCopyRequested,
                        onPasteRequested = onPasteRequested,
                        onCutRequested = onCutRequested,
                        onSelectAllRequested = onSelectAllRequested
                    )
                    return
                }
                pendingInputSelectionToolbarState = null
                inputSelectionToolbarState = buildResolvedInputSelectionToolbarState(
                    rect = rect,
                    bounds = bounds,
                    onCopyRequested = onCopyRequested,
                    onPasteRequested = onPasteRequested,
                    onCutRequested = onCutRequested,
                    onSelectAllRequested = onSelectAllRequested
                )
            }

            override fun hide() {
                clearInputSelectionToolbar()
            }
        }
    }

    fun buildMessageSelectionTextToolbar(
        messageId: String,
        role: ChatRole,
        fullCopyText: String
    ): TextToolbar {
        return object : TextToolbar {
            override val status: TextToolbarStatus
                get() =
                    if (messageSelectionToolbarState?.messageId == messageId) {
                        TextToolbarStatus.Shown
                    } else {
                        TextToolbarStatus.Hidden
                    }

            @Suppress("UNUSED_PARAMETER")
            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {
                val onCopyFull = {
                    copyTextToClipboard(
                        label = if (role == ChatRole.USER) "user_message_full_copy" else "assistant_message_full_copy",
                        text = fullCopyText
                    )
                }
                val bounds = messageSelectionBoundsById[messageId]
                if (bounds == null) {
                    pendingMessageSelectionToolbarState = PendingMessageSelectionToolbarState(
                        messageId = messageId,
                        rect = rect,
                        onCopyRequested = onCopyRequested,
                        onCopyFullRequested = onCopyFull
                    )
                    return
                }
                val nextState = buildResolvedMessageSelectionToolbarState(
                    messageId = messageId,
                    rect = rect,
                    bounds = bounds,
                    onCopyRequested = onCopyRequested,
                    onCopyFullRequested = onCopyFull
                )
                val resolvedState = resolveMessageSelectionToolbarState(nextState) ?: nextState
                pendingMessageSelectionToolbarState = null
                messageSelectionToolbarState = resolvedState
            }

            override fun hide() {
                if (pendingMessageSelectionToolbarState?.messageId == messageId) {
                    pendingMessageSelectionToolbarState = null
                }
                if (messageSelectionToolbarState?.messageId == messageId) {
                    messageSelectionToolbarState = null
                }
            }
        }
    }
    BackHandler(enabled = messageSelectionToolbarState != null || inputSelectionToolbarState != null) {
        when {
            inputSelectionToolbarState != null -> {
                clearInputSelectionToolbar()
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
            messageSelectionToolbarState != null -> clearMessageSelection()
        }
    }
    fun performButtonHaptic() {
        val handled = view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (!handled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    LaunchedEffect(inputChromeMeasured, inputChromeRowHeightPx, safeBottomInsetPx) {
        if (!inputChromeMeasured) return@LaunchedEffect
        val stableBottomBarHeightPx =
            (inputChromeRowHeightPx + safeBottomInsetPx)
                .coerceAtLeast(startupBottomBarHeightEstimatePx)
        val deltaPx = kotlin.math.abs(bottomBarHeightPx - stableBottomBarHeightPx)
        if (
            bottomBarHeightPx != stableBottomBarHeightPx &&
            (imeVisible || deltaPx > BOTTOM_BAR_HEIGHT_JITTER_TOLERANCE_PX)
        ) {
            bottomBarHeightPx = stableBottomBarHeightPx
        }
    }

    LaunchedEffect(chatScopeId) {
        remoteRecoveryJob?.cancel()
        remoteRecoveryJob = null
        remoteRecoverySourceUserMessageId = null
        initialBottomSnapDone = false
        jumpButtonVisible = false
        restoreBottomAfterImeClose = false
        suppressJumpButtonForImeTransition = false
        restoreBottomAfterLifecycleResume = false
        suppressJumpButtonForLifecycleResume = false
        lifecycleResumeReady = false
        clearInputSelectionToolbar()
        LaunchUiGate.chatReady = false
    }

    fun showComposerStatusHint(text: String) {
        composerStatusHintText = text
        composerStatusHintTick++
    }

    fun pruneFailedMessageStates() {
        val currentMessageIds = messages.mapTo(mutableSetOf()) { it.id }
        failedUserMessageStates.keys
            .toList()
            .filterNot(currentMessageIds::contains)
            .forEach(failedUserMessageStates::remove)
        failedAssistantMessageStates.keys
            .toList()
            .filterNot(currentMessageIds::contains)
            .forEach(failedAssistantMessageStates::remove)
    }

    fun persistableMessagesSnapshot(): List<ChatMessage> {
        val failedIds = buildSet {
            addAll(failedUserMessageStates.keys)
            addAll(failedAssistantMessageStates.keys)
        }
        val transientAssistantId = streamingMessageId
        return messages.filterNot { message ->
            message.id in failedIds ||
                (
                    message.role == ChatRole.ASSISTANT &&
                        (
                            message.content.isBlank() ||
                                (
                                    transientAssistantId != null &&
                                        message.id == transientAssistantId &&
                                        isStreaming
                                    )
                            )
                    )
        }
    }

    fun upsertUserMessage(messageId: String, content: String) {
        val finalMessage = ChatMessage(messageId, ChatRole.USER, content)
        val existingIndex = messages.indexOfFirst { it.id == messageId }
        if (existingIndex >= 0) {
            if (messages[existingIndex] != finalMessage) {
                messages[existingIndex] = finalMessage
            }
        } else {
            messages.add(finalMessage)
        }
    }

    fun upsertAssistantMessagePlaceholder(messageId: String, sourceUserMessageId: String) {
        val placeholder = ChatMessage(messageId, ChatRole.ASSISTANT, "")
        val existingIndex = messages.indexOfFirst { it.id == messageId }
        if (existingIndex >= 0) {
            if (messages[existingIndex] != placeholder) {
                messages[existingIndex] = placeholder
            }
            return
        }
        val userIndex = messages.indexOfFirst { it.id == sourceUserMessageId && it.role == ChatRole.USER }
        if (userIndex >= 0) {
            messages.add(userIndex + 1, placeholder)
        } else {
            messages.add(placeholder)
        }
    }

    fun removeMessageById(messageId: String) {
        val index = messages.indexOfFirst { it.id == messageId }
        if (index >= 0) {
            messages.removeAt(index)
        }
    }

    fun clearFailedAssistantStateForUser(messageId: String) {
        failedAssistantMessageStates.entries
            .toList()
            .filter { it.value.sourceUserMessageId == messageId }
            .forEach { failedAssistantMessageStates.remove(it.key) }
    }

    fun hasSettledAssistantMessageForUser(sourceUserMessageId: String): Boolean {
        val assistantMessageId = assistantMessageIdForSourceUser(sourceUserMessageId)
        return failedAssistantMessageStates[assistantMessageId] == null &&
            messages.any { it.id == assistantMessageId && it.role == ChatRole.ASSISTANT && it.content.isNotBlank() }
    }

    fun interruptedHintText(reason: String): String =
        when (reason) {
            "network" -> "\u7f51\u7edc\u6ce2\u52a8\uff0c\u56de\u590d\u672a\u5b8c\u6210"
            "quota" -> "\u5f53\u524d\u6b21\u6570\u4e0d\u8db3\uff0c\u8bf7\u7a0d\u540e\u518d\u8bd5"
            "rate_limit" -> "\u5f53\u524d\u8bf7\u6c42\u8f83\u591a\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5"
            else -> "\u672c\u6b21\u56de\u590d\u672a\u5b8c\u6210\uff0c\u8bf7\u91cd\u8bd5"
        }

    fun canAttemptRemoteAssistantRecovery(reason: String): Boolean =
        hasRemoteHistorySource && reason !in setOf("quota", "rate_limit", "auth", "model_unavailable", "replay")

    fun finalizeInterruptedAssistant(
        sourceUserMessageId: String,
        assistantMessageId: String,
        finalContent: String,
        reason: String
    ) {
        if (finalContent.isNotBlank()) {
            applyCompletedAssistantMessageInPlace(
                target = messages,
                messageId = assistantMessageId,
                content = finalContent
            )
            failedAssistantMessageStates[assistantMessageId] = FailedAssistantMessageState(
                sourceUserMessageId = sourceUserMessageId
            )
            persistTick++
        } else {
            showComposerStatusHint(interruptedHintText(reason))
        }
    }

    fun applyRecoveredAssistantRound(sourceUserMessageId: String, recoveredRound: ARound) {
        remoteRecoveryJob?.cancel()
        remoteRecoveryJob = null
        remoteRecoverySourceUserMessageId = null
        failedUserMessageStates.remove(sourceUserMessageId)
        clearFailedAssistantStateForUser(sourceUserMessageId)
        if (recoveredRound.user.isNotBlank()) {
            upsertUserMessage(sourceUserMessageId, recoveredRound.user)
        }
        applyCompletedAssistantMessageInPlace(
            target = messages,
            messageId = assistantMessageIdForSourceUser(sourceUserMessageId),
            content = recoveredRound.assistant
        )
        val startIndex = trimWindowStartIndex(messages)
        if (startIndex > 0) {
            repeat(startIndex) { messages.removeAt(0) }
        }
        pruneFailedMessageStates()
        persistTick++
        val persistedMessages = persistableMessagesSnapshot()
        val prewarmMessages = persistedMessages.takeLast(2)
        snackbarScope.launch {
            context.saveLocalChatWindow(chatScopeId, persistedMessages)
            context.clearLocalStreamingDraft(chatScopeId)
            prewarmAssistantMarkdown(prewarmMessages)
        }
    }

    fun scheduleRemoteAssistantRecovery(
        sourceUserMessageId: String,
        assistantMessageId: String,
        fallbackContent: String,
        reason: String
    ) {
        if (
            remoteRecoverySourceUserMessageId == sourceUserMessageId &&
            remoteRecoveryJob?.isActive == true
        ) {
            return
        }
        remoteRecoveryJob?.cancel()
        remoteRecoverySourceUserMessageId = sourceUserMessageId
        remoteRecoveryJob = snackbarScope.launch {
            var recoveredRound: ARound? = null
            for (attempt in 0 until REMOTE_STREAM_RECOVERY_MAX_ATTEMPTS) {
                val snapshot = awaitRemoteSnapshot()
                recoveredRound = snapshot
                    ?.findRoundByClientMessageId(sourceUserMessageId)
                    ?.takeIf { it.assistant.isNotBlank() }
                if (recoveredRound != null) break
                if (attempt < REMOTE_STREAM_RECOVERY_MAX_ATTEMPTS - 1) {
                    delay(REMOTE_STREAM_RECOVERY_DELAY_MS)
                }
            }
            val matchedRound = recoveredRound
            if (matchedRound != null) {
                applyRecoveredAssistantRound(sourceUserMessageId, matchedRound)
            } else {
                remoteRecoverySourceUserMessageId = null
                finalizeInterruptedAssistant(
                    sourceUserMessageId = sourceUserMessageId,
                    assistantMessageId = assistantMessageId,
                    finalContent = fallbackContent,
                    reason = reason
                )
            }
        }
    }

    fun replaceMessages(newMessages: List<ChatMessage>) {
        val trimmed = sanitizeMessageWindow(newMessages)
        messages.clear()
        messages.addAll(trimmed)
        pruneFailedMessageStates()
    }

    fun trimMessagesInPlace() {
        val startIndex = trimWindowStartIndex(messages)
        if (startIndex > 0) {
            repeat(startIndex) { messages.removeAt(0) }
        }
        pruneFailedMessageStates()
    }

    fun resetStreamingUiState(clearVisibleContent: Boolean) {
        mainHandler.post {
            remoteRecoveryJob?.cancel()
            remoteRecoveryJob = null
            remoteRecoverySourceUserMessageId = null
            SessionApi.cancelCurrentStream()
            fakeStreamJob?.cancel()
            fakeStreamJob = null
            streamRevealJob?.cancel()
            streamRevealJob = null
            isStreaming = false
            streamingMessageId = null
            streamingRevealBuffer = ""
            streamingFreshStart = -1
            streamingFreshEnd = -1
            streamingLineAdvanceTick = 0
            lastStreamingFreshRevealMs = 0L
            streamAnchorReservePx = 0
            initialStreamAnchorReservePx = 0
            streamingContentBottomPx = -1
            streamBottomFollowActive = false
            pendingResumeAutoFollow = false
            pendingFinalBottomSnap = false
            pendingFinalBottomSnapAfterSpacer = false
            pendingStreamSpacerRelease = false
            scrollMode = ScrollMode.Idle
            userDetachedFromBottom = false
            autoScrollMode = AutoScrollMode.Idle
            composerSettlingMinHeightPx = 0
            composerSettlingChromeHeightPx = 0
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

    LaunchedEffect(chatScopeId, historyHydrationComplete, startupRecoverableUserMessageId) {
        val sourceUserMessageId = startupRecoverableUserMessageId ?: return@LaunchedEffect
        if (!hasRemoteHistorySource || !historyHydrationComplete) return@LaunchedEffect
        if (hasStartedConversation || isStreaming) return@LaunchedEffect
        if (hasSettledAssistantMessageForUser(sourceUserMessageId)) return@LaunchedEffect
        repeat(3) { attempt ->
            val snapshot = awaitRemoteSnapshot()
            val recoveredRound = snapshot
                ?.findRoundByClientMessageId(sourceUserMessageId)
                ?.takeIf { it.assistant.isNotBlank() }
            if (recoveredRound != null) {
                applyRecoveredAssistantRound(sourceUserMessageId, recoveredRound)
                initialBottomSnapDone = false
                return@LaunchedEffect
            }
            if (attempt < 2) {
                delay(1100L * (attempt + 1))
            }
        }
    }

    LaunchedEffect(persistTick) {
        if (persistTick == 0) return@LaunchedEffect
        delay(if (isStreaming) 220 else 80)
        context.saveLocalChatWindow(chatScopeId, persistableMessagesSnapshot())
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

    LaunchedEffect(composerStatusHintTick) {
        if (composerStatusHintTick == 0 || composerStatusHintText.isBlank()) return@LaunchedEffect
        val currentTick = composerStatusHintTick
        composerStatusHintVisible = true
        delay(COMPOSER_STATUS_HINT_MS)
        if (composerStatusHintTick == currentTick) {
            composerStatusHintVisible = false
            composerStatusHintText = ""
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
        delay(STREAM_DRAFT_SAVE_DEBOUNCE_MS)
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

    LaunchedEffect(listState.isScrollInProgress, programmaticScroll) {
        if (programmaticScroll) {
            userInteracting = false
            return@LaunchedEffect
        }
        if (listState.isScrollInProgress && imeVisible) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
        userInteracting = listState.isScrollInProgress
    }

    LaunchedEffect(listState, isStreaming, hasStreamingItem) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow {
            listOf(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                if (listState.isScrollInProgress) 1 else 0,
                streamTick,
                streamingWorklineBottomPx,
                currentStreamingVisualBottomPx(),
                streamAnchorReservePx
            )
        }.collect { state ->
            val currentIndex = state[0]
            val currentOffset = state[1]
            val scrollInProgress = state[2] == 1
            if (programmaticScroll) {
                previousIndex = currentIndex
                previousOffset = currentOffset
                return@collect
            }
            val movedTowardBottom =
                currentIndex > previousIndex ||
                    (currentIndex == previousIndex && currentOffset > previousOffset)
            val movedTowardTop =
                currentIndex < previousIndex ||
                    (currentIndex == previousIndex && currentOffset < previousOffset)
            val atFollowBoundary = isAtStreamingFollowBoundary()
            if (scrollMode == ScrollMode.Returning && atFollowBoundary && !scrollInProgress) {
                applyStreamingScrollState(
                    detached = false,
                    mode = resolveStreamingIdleMode()
                )
            } else if (scrollMode == ScrollMode.Returning && !scrollInProgress) {
                applyStreamingScrollState(
                    detached = true,
                    mode = AutoScrollMode.AnchorUser,
                    hideJumpButton = false
                )
            } else if (scrollMode == ScrollMode.UserBrowsing && !scrollInProgress) {
                userDetachedFromBottom = true
                autoScrollMode = AutoScrollMode.AnchorUser
            } else if (atFollowBoundary && !scrollInProgress) {
                applyStreamingScrollState(
                    detached = false,
                    mode = resolveStreamingIdleMode()
                )
            } else if (!isStreaming || !hasStreamingItem) {
                when {
                    movedTowardBottom -> {
                        applyStreamingScrollState(
                            detached = false,
                            mode = AutoScrollMode.Idle
                        )
                    }

                    movedTowardTop -> {
                        applyStreamingScrollState(
                            detached = true,
                            mode = AutoScrollMode.Idle,
                            hideJumpButton = false
                        )
                    }

                    else -> {
                        scrollMode = ScrollMode.Idle
                        autoScrollMode = AutoScrollMode.Idle
                    }
                }
            } else if (scrollInProgress) {
                when {
                    movedTowardBottom -> {
                        if (scrollMode == ScrollMode.UserBrowsing || scrollMode == ScrollMode.Returning) {
                            scrollMode = ScrollMode.Returning
                            pendingResumeAutoFollow = false
                            userDetachedFromBottom = true
                            autoScrollMode = AutoScrollMode.AnchorUser
                            jumpButtonVisible = false
                        } else {
                            applyStreamingScrollState(
                                detached = userDetachedFromBottom,
                                mode = AutoScrollMode.AnchorUser,
                                hideJumpButton = userDetachedFromBottom.not()
                            )
                        }
                    }

                    movedTowardTop -> {
                        applyStreamingScrollState(
                            detached = true,
                            mode = AutoScrollMode.AnchorUser,
                            hideJumpButton = false
                        )
                    }
                }
            } else if (userDetachedFromBottom) {
                val canReattachToStreaming = atFollowBoundary
                if (canReattachToStreaming) {
                    applyStreamingScrollState(
                        detached = false,
                        mode = resolveStreamingIdleMode()
                    )
                } else {
                    scrollMode = ScrollMode.UserBrowsing
                    autoScrollMode = AutoScrollMode.AnchorUser
                }
            } else {
                val idleMode = resolveStreamingIdleMode()
                scrollMode = when (idleMode) {
                    AutoScrollMode.StreamAnchorFollow -> ScrollMode.AutoFollow
                    AutoScrollMode.AnchorUser -> if (userDetachedFromBottom) ScrollMode.UserBrowsing else ScrollMode.Idle
                    AutoScrollMode.Idle -> ScrollMode.Idle
                }
                autoScrollMode = idleMode
            }
            previousIndex = currentIndex
            previousOffset = currentOffset
        }
    }

    LaunchedEffect(autoScrollMode, streamingMessageContent.length, userDetachedFromBottom, userInteracting) {
        if (streamAnchorReservePx <= 0) return@LaunchedEffect
        if ((!isStreaming || autoScrollMode == AutoScrollMode.Idle) && !pendingStreamSpacerRelease) {
            streamAnchorReservePx = 0
            initialStreamAnchorReservePx = 0
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
                    streamingMessageId = anchoredUserMessageId
                        ?.let(::assistantMessageIdForSourceUser)
                        ?: "assistant_${UUID.randomUUID()}"
                }
                streamingFreshStart = streamingMessageContent.length
                streamingMessageContent += batch.text
                streamingFreshEnd = streamingMessageContent.length
                val now = SystemClock.uptimeMillis()
                if (
                    streamingFreshTick <= 0 ||
                    now - lastStreamingFreshRevealMs >= STREAM_FRESH_SUFFIX_TRIGGER_INTERVAL_MS ||
                    batch.text.indexOf('\n') >= 0
                ) {
                    streamingFreshTick++
                    lastStreamingFreshRevealMs = now
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
                streamingMessageId = anchoredUserMessageId
                    ?.let(::assistantMessageIdForSourceUser)
                    ?: "assistant_${UUID.randomUUID()}"
            }
            streamingRevealBuffer += piece
            ensureStreamingRevealJob()
        }
    }

    fun currentStreamingOverflowDelta(): Int {
        val worklineBottom = streamingWorklineBottomPx
        val info = listState.layoutInfo
        val lastIndex = info.totalItemsCount - 1
        if (lastIndex < 0) return 0
        val visibleBottom = worklineBottom.takeIf { it > 0 }
            ?: (info.viewportEndOffset - streamVisibleBottomGapPx).coerceAtLeast(0)
        val contentBottom = currentStreamingVisualBottomPx()
        if (contentBottom > 0) {
            return (contentBottom - visibleBottom).coerceAtLeast(0)
        }
        val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return 0
        return (lastVisible.offset + lastVisible.size - visibleBottom).coerceAtLeast(0)
    }

    fun resolveStreamingFollowStepPx(overflow: Int): Int {
        if (overflow <= 0) return 0
        val steadyStepPx = (assistantLineStepPx * 0.16f).roundToInt().coerceAtLeast(7)
        val triggerThresholdPx = (steadyStepPx * 0.3f).roundToInt().coerceAtLeast(2)
        if (overflow < triggerThresholdPx) return 0
        return overflow.coerceAtMost(steadyStepPx)
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

    suspend fun scrollToStreamingFollowBoundary(maxFrames: Int = 180) {
        if (messages.isEmpty() && !hasStreamingItem) return
        lastProgrammaticScrollMs = SystemClock.uptimeMillis()
        programmaticScroll = true
        try {
            val stickyStepPx = messageViewportHeightPx
                .takeIf { it > 0 }
                ?.let { (it * 0.24f).roundToInt() }
                ?.coerceAtLeast(STREAM_STICKY_SCROLL_STEP_PX)
                ?: STREAM_STICKY_SCROLL_STEP_PX
            repeat(maxFrames) {
                withFrameNanos { }
                val overflow = currentStreamingOverflowDelta()
                if (overflow <= bottomPositionTolerancePx) return
                val stepPx = overflow.coerceAtMost(stickyStepPx).coerceAtLeast(1)
                val consumed = listState.scrollBy(stepPx.toFloat())
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
                !userDetachedFromBottom &&
                    currentStreamingOverflowDelta() > finalBottomSnapThresholdPx
            streamRevealJob?.cancel()
            streamRevealJob = null
            if (streamingRevealBuffer.isNotEmpty()) {
                if (streamingMessageId.isNullOrBlank()) {
                    streamingMessageId = anchoredUserMessageId
                        ?.let(::assistantMessageIdForSourceUser)
                        ?: "assistant_${UUID.randomUUID()}"
                }
                streamingMessageContent += streamingRevealBuffer
                streamingRevealBuffer = ""
                streamTick++
            }
            val finalContent = streamingMessageContent
            val finalId = streamingMessageId
            fakeStreamJob = null
            isStreaming = false
            streamingRevealBuffer = ""
            streamingFreshStart = -1
            streamingFreshEnd = -1
            streamingLineAdvanceTick = 0
            streamingAnchorTopPx = -1
            streamingContentBottomPx = -1
            streamBottomFollowActive = false
            pendingResumeAutoFollow = false
            streamingBackgrounded = false
            scrollMode = ScrollMode.Idle
            userDetachedFromBottom = false
            autoScrollMode = AutoScrollMode.Idle
            jumpButtonVisible = false
            if (finalContent.isNotBlank()) {
                applyCompletedAssistantMessageInPlace(
                    target = messages,
                    messageId = finalId.orEmpty(),
                    content = finalContent
                )
                pendingStreamSpacerRelease = true
                pendingFinalBottomSnap = false
                pendingFinalBottomSnapAfterSpacer = shouldSnapToBottomOnFinish
                persistTick++
                val persistedMessages = persistableMessagesSnapshot()
                val prewarmMessages = persistedMessages.takeLast(2)
                snackbarScope.launch {
                    context.saveLocalChatWindow(chatScopeId, persistedMessages)
                    context.clearLocalStreamingDraft(chatScopeId)
                    prewarmAssistantMarkdown(prewarmMessages)
                }
            } else {
                finalId?.let(::removeMessageById)
                streamingMessageId = null
                streamingMessageContent = ""
                pendingStreamSpacerRelease = false
                persistTick++
                pendingFinalBottomSnap = shouldSnapToBottomOnFinish
                pendingFinalBottomSnapAfterSpacer = false
            }
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
            while (isActive && streamingRevealBuffer.isNotEmpty()) {
                delay(STREAM_TYPEWRITER_IDLE_POLL_MS)
            }
            for (attempt in 0 until 18) {
                if (!isActive || !isStreaming) break
                val overflow = currentStreamingOverflowDelta()
                if (!streamBottomFollowActive && overflow <= lineRevealUnlockThresholdPx) {
                    break
                }
                if (attempt < 17) {
                    delay(STREAM_TYPEWRITER_IDLE_POLL_MS)
                }
            }
            if (isActive) finishStreaming()
        }
    }

    fun recoverStreamingAfterLifecycleLoss() {
        mainHandler.post {
            if (!isStreaming) return@post
            if (hasRemoteHistorySource) return@post
            scrollMode = ScrollMode.AutoFollow
            autoScrollMode = AutoScrollMode.StreamAnchorFollow
            if (streamRevealJob?.isActive == true) {
                streamRevealJob?.cancel()
                streamRevealJob = null
            }
            if (streamingRevealBuffer.isNotEmpty()) {
                if (streamingMessageId.isNullOrBlank()) {
                    streamingMessageId = anchoredUserMessageId
                        ?.let(::assistantMessageIdForSourceUser)
                        ?: "assistant_${UUID.randomUUID()}"
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

    fun handleAssistantInterrupted(sourceUserMessageId: String, reason: String) {
        mainHandler.post {
            val finalId = streamingMessageId ?: assistantMessageIdForSourceUser(sourceUserMessageId)
            val finalContent = normalizeAssistantText(streamingMessageContent + streamingRevealBuffer)
            fakeStreamJob?.cancel()
            fakeStreamJob = null
            streamRevealJob?.cancel()
            streamRevealJob = null
            isStreaming = false
            streamingMessageId = null
            streamingMessageContent = ""
            streamingRevealBuffer = ""
            streamingFreshStart = -1
            streamingFreshEnd = -1
            streamingLineAdvanceTick = 0
            lastStreamingFreshRevealMs = 0L
            streamAnchorReservePx = 0
            initialStreamAnchorReservePx = 0
            streamingAnchorTopPx = -1
            streamingContentBottomPx = -1
            streamBottomFollowActive = false
            pendingResumeAutoFollow = false
            pendingFinalBottomSnap = false
            pendingFinalBottomSnapAfterSpacer = false
            pendingStreamSpacerRelease = false
            streamingBackgrounded = false
            scrollMode = ScrollMode.Idle
            userDetachedFromBottom = false
            autoScrollMode = AutoScrollMode.Idle
            jumpButtonVisible = false
            context.clearLocalStreamingDraftSync(chatScopeId)
            if (canAttemptRemoteAssistantRecovery(reason)) {
                if (finalContent.isNotBlank()) {
                    applyCompletedAssistantMessageInPlace(
                        target = messages,
                        messageId = finalId,
                        content = finalContent
                    )
                } else {
                    removeMessageById(finalId)
                }
                scheduleRemoteAssistantRecovery(
                    sourceUserMessageId = sourceUserMessageId,
                    assistantMessageId = finalId,
                    fallbackContent = finalContent,
                    reason = reason
                )
                return@post
            }
            if (finalContent.isNotBlank()) {
                applyCompletedAssistantMessageInPlace(
                    target = messages,
                    messageId = finalId,
                    content = finalContent
                )
                failedAssistantMessageStates[finalId] = FailedAssistantMessageState(
                    sourceUserMessageId = sourceUserMessageId
                )
                persistTick++
            } else {
                removeMessageById(finalId)
                showComposerStatusHint(
                    when (reason) {
                        "network" -> "网络波动，回复未完成"
                        "quota" -> "当前次数不足，请稍后再试"
                        "rate_limit" -> "当前请求较多，请稍后重试"
                        else -> "本次回复未完成，请重试"
                    }
                )
            }
        }
    }

    fun markUserMessageSendFailed(
        text: String,
        existingUserMessageId: String? = null,
        collapseComposer: Boolean = true
    ) {
        if (text.isEmpty() || isStreaming || sendUiSettling) return
        composerCollapseOverlayVisible = false
        sendUiSettling = true
        if (collapseComposer) {
            composerSettlingMinHeightPx =
                inputContentHeightPx.coerceAtLeast(startupInputContentHeightEstimatePx)
            composerSettlingChromeHeightPx = inputChromeRowHeightPx
            suppressInputCursor = true
            inputFieldFocused = false
            clearInputSelectionToolbar()
            input.value = TextFieldValue("")
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
        snackbarScope.launch {
            try {
                hasStartedConversation = true
                initialBottomSnapDone = true
                LaunchUiGate.chatReady = true
                if (collapseComposer) {
                    restoreBottomAfterImeClose = false
                    suppressJumpButtonForImeTransition = true
                }
                val userId = existingUserMessageId ?: "user_${UUID.randomUUID()}"
                upsertUserMessage(userId, text)
                failedUserMessageStates[userId] = "network"
                clearFailedAssistantStateForUser(userId)
                anchoredUserMessageId = userId
                userDetachedFromBottom = false
                jumpButtonVisible = false
                trimMessagesInPlace()
                sendUiSettling = false
                showComposerStatusHint("当前网络不可用")
            } finally {
                sendUiSettling = false
            }
        }
    }

    fun commitSendMessage(
        text: String,
        existingUserMessageId: String? = null,
        collapseComposer: Boolean = true
    ) {
        if (text.isEmpty() || isStreaming || sendUiSettling) return
        composerCollapseOverlayVisible = false
        sendUiSettling = true
        if (collapseComposer) {
            composerSettlingMinHeightPx =
                inputContentHeightPx.coerceAtLeast(startupInputContentHeightEstimatePx)
            composerSettlingChromeHeightPx = inputChromeRowHeightPx
            suppressInputCursor = true
            inputFieldFocused = false
            clearInputSelectionToolbar()
            input.value = TextFieldValue("")
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
        snackbarScope.launch {
            try {
                hasStartedConversation = true
                initialBottomSnapDone = true
                LaunchUiGate.chatReady = true
                if (collapseComposer) {
                    restoreBottomAfterImeClose = false
                    suppressJumpButtonForImeTransition = true
                }
                val userId = existingUserMessageId ?: "user_${UUID.randomUUID()}"
                failedUserMessageStates.remove(userId)
                clearFailedAssistantStateForUser(userId)
                upsertUserMessage(userId, text)
                anchoredUserMessageId = userId
                streamingAnchorTopPx = -1
                streamingContentBottomPx = -1
                streamBottomFollowActive = false
                pendingResumeAutoFollow = false
                pendingFinalBottomSnap = false
                pendingStreamSpacerRelease = false
                streamingFreshStart = -1
                streamingFreshEnd = -1
                streamingLineAdvanceTick = 0
                lastStreamingFreshRevealMs = 0L
                userDetachedFromBottom = false
                jumpButtonVisible = false
                isStreaming = true

                val assistantId = assistantMessageIdForSourceUser(userId)
                streamingMessageId = assistantId
                upsertAssistantMessagePlaceholder(
                    messageId = assistantId,
                    sourceUserMessageId = userId
                )
                trimMessagesInPlace()
                sendUiSettling = false
                persistTick++
                snackbarScope.launch {
                    context.saveLocalChatWindow(chatScopeId, persistableMessagesSnapshot())
                }
                remoteRecoveryJob?.cancel()
                remoteRecoveryJob = null
                remoteRecoverySourceUserMessageId = null
                streamingMessageContent = ""
                streamingRevealBuffer = ""
                streamingFreshStart = -1
                streamingFreshEnd = -1
                streamingLineAdvanceTick = 0
                lastStreamingFreshRevealMs = 0L
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
                streamAnchorReservePx = maxOf(
                    (messageViewportHeightPx * SEND_ANCHOR_EXTRA_BOTTOM_SPACE_RATIO).roundToInt(),
                    minSendAnchorExtraBottomSpacePx
                )
                initialStreamAnchorReservePx = streamAnchorReservePx
                streamingBackgrounded = false
                scrollMode = ScrollMode.Idle
                autoScrollMode = AutoScrollMode.AnchorUser
                userInteracting = false
                sendTick++

                fakeStreamJob?.cancel()
                streamRevealJob?.cancel()
                streamRevealJob = null
                if (hasRemoteHistorySource) {
                    SessionApi.cancelCurrentStream()
                    SessionApi.streamChat(
                        options = SessionApi.StreamOptions(
                            clientMsgId = userId,
                            text = text,
                            images = emptyList()
                        ),
                        onChunk = { piece -> appendAssistantChunk(piece) },
                        onComplete = { finishStreaming() },
                        onInterrupted = { reason -> handleAssistantInterrupted(userId, reason) }
                    )
                } else {
                    launchLocalFakeStream(applyInitialDelay = true)
                }
            } finally {
                sendUiSettling = false
            }
        }
    }

    fun retryFailedUserMessage(messageId: String) {
        val failedMessage = messages.firstOrNull { it.id == messageId } ?: return
        if (hasRemoteHistorySource && !context.hasActiveNetworkConnection()) {
            showComposerStatusHint("当前网络不可用")
            return
        }
        commitSendMessage(
            text = failedMessage.content,
            existingUserMessageId = failedMessage.id,
            collapseComposer = false
        )
    }

    fun retryFailedAssistantMessage(assistantMessageId: String) {
        val failedState = failedAssistantMessageStates[assistantMessageId] ?: return
        val sourceUserMessage = messages.firstOrNull { it.id == failedState.sourceUserMessageId } ?: return
        if (hasRemoteHistorySource && !context.hasActiveNetworkConnection()) {
            showComposerStatusHint("当前网络不可用")
            return
        }
        failedAssistantMessageStates.remove(assistantMessageId)
        val existingAssistantIndex = messages.indexOfFirst { it.id == assistantMessageId }
        if (existingAssistantIndex >= 0) {
            messages.removeAt(existingAssistantIndex)
        }
        commitSendMessage(
            text = sourceUserMessage.content,
            existingUserMessageId = sourceUserMessage.id,
            collapseComposer = false
        )
    }

    LaunchedEffect(
        composerSettlingMinHeightPx,
        composerSettlingChromeHeightPx,
        sendUiSettling,
        imeVisible,
        inputFieldFocused,
        input.value.text,
        bottomBarHeightPx,
        inputChromeRowHeightPx,
        safeBottomInsetPx
    ) {
        if (input.value.text.isNotEmpty() || inputFieldFocused) {
            composerSettlingMinHeightPx = 0
            composerSettlingChromeHeightPx = 0
            return@LaunchedEffect
        }
        if (composerSettlingMinHeightPx <= 0 && composerSettlingChromeHeightPx <= 0) return@LaunchedEffect
        if (sendUiSettling) return@LaunchedEffect
        if (!imeVisible) {
            val stableBottomBarHeightPx =
                (inputChromeRowHeightPx + safeBottomInsetPx)
                    .coerceAtLeast(startupBottomBarHeightEstimatePx)
            val bottomBarStable =
                kotlin.math.abs(bottomBarHeightPx - stableBottomBarHeightPx) <=
                    BOTTOM_BAR_HEIGHT_JITTER_TOLERANCE_PX
            if (!bottomBarStable) return@LaunchedEffect
            composerSettlingMinHeightPx = 0
            composerSettlingChromeHeightPx = 0
            return@LaunchedEffect
        }
        withFrameNanos { }
        if (!sendUiSettling && !inputFieldFocused && input.value.text.isEmpty()) {
            composerSettlingMinHeightPx = 0
            composerSettlingChromeHeightPx = 0
        }
    }

    LaunchedEffect(
        composerCollapseOverlayVisible,
        sendUiSettling,
        imeVisible,
        composerSettlingChromeHeightPx,
        bottomBarHeightPx,
        inputChromeRowHeightPx,
        safeBottomInsetPx,
        inputFieldFocused,
        input.value.text
    ) {
        if (!composerCollapseOverlayVisible) return@LaunchedEffect
        if (input.value.text.isNotEmpty() || inputFieldFocused) {
            composerCollapseOverlayVisible = false
            return@LaunchedEffect
        }
        val stableBottomBarHeightPx =
            (inputChromeRowHeightPx + safeBottomInsetPx).coerceAtLeast(startupBottomBarHeightEstimatePx)
        val bottomBarStable =
            kotlin.math.abs(bottomBarHeightPx - stableBottomBarHeightPx) <= BOTTOM_BAR_HEIGHT_JITTER_TOLERANCE_PX
        if (!sendUiSettling && !imeVisible && composerSettlingChromeHeightPx <= 0 && bottomBarStable) {
            repeat(2) { withFrameNanos { } }
            val settledBottomBarHeightPx =
                (inputChromeRowHeightPx + safeBottomInsetPx).coerceAtLeast(startupBottomBarHeightEstimatePx)
            val settledBottomBarStable =
                kotlin.math.abs(bottomBarHeightPx - settledBottomBarHeightPx) <= BOTTOM_BAR_HEIGHT_JITTER_TOLERANCE_PX
            if (
                !sendUiSettling &&
                !imeVisible &&
                composerSettlingChromeHeightPx <= 0 &&
                settledBottomBarStable &&
                !inputFieldFocused &&
                input.value.text.isEmpty()
            ) {
                composerCollapseOverlayVisible = false
            }
        }
    }

    LaunchedEffect(
        composerCollapseOverlayVisible,
        composerHostBoundsInWindow,
        composerChromeBoundsInWindow,
        effectiveBottomBarHeightPx,
        composerCollapseOverlayPrewarmed
    ) {
        if (composerCollapseOverlayVisible) return@LaunchedEffect
        val hostBounds = composerHostBoundsInWindow ?: return@LaunchedEffect
        val chromeBounds = composerChromeBoundsInWindow ?: return@LaunchedEffect
        if (composerCollapseOverlayPrewarmed) return@LaunchedEffect
        composerCollapseOverlayHostBoundsSnapshot = hostBounds
        composerCollapseOverlayChromeBoundsSnapshot = chromeBounds
        composerCollapseOverlayBottomHeightPx = effectiveBottomBarHeightPx
        composerCollapseOverlayPrewarmed = true
    }

    LaunchedEffect(
        imeVisible,
        inputFieldFocused,
        composerCollapseOverlayVisible,
        composerHostBoundsInWindow,
        composerChromeBoundsInWindow,
        effectiveBottomBarHeightPx
    ) {
        if (composerCollapseOverlayVisible) return@LaunchedEffect
        if (!imeVisible && !inputFieldFocused) return@LaunchedEffect
        if (composerHostBoundsInWindow == null || composerChromeBoundsInWindow == null) {
            return@LaunchedEffect
        }
        repeat(2) { withFrameNanos { } }
        if (composerCollapseOverlayVisible) return@LaunchedEffect
        if (!imeVisible && !inputFieldFocused) return@LaunchedEffect
        val hostBounds = composerHostBoundsInWindow ?: return@LaunchedEffect
        val chromeBounds = composerChromeBoundsInWindow ?: return@LaunchedEffect
        composerCollapseOverlayHostBoundsSnapshot = hostBounds
        composerCollapseOverlayChromeBoundsSnapshot = chromeBounds
        composerCollapseOverlayBottomHeightPx = effectiveBottomBarHeightPx
        composerCollapseOverlayPrewarmed = true
    }

    LaunchedEffect(chatScopeId) {
        composerCollapseOverlayBottomHeightPx = 0
        composerCollapseOverlayHostBoundsSnapshot = null
        composerCollapseOverlayChromeBoundsSnapshot = null
        composerCollapseOverlayPrewarmed = false
    }

    LaunchedEffect(startupLayoutReady) {
        if (!startupLayoutReady) {
            composerCollapseOverlayPrewarmed = false
        }
    }

    fun sendMessage() {
        val text = input.value.text
        val sendGate = buildSendGateState(
            rawInput = text,
            isStreaming = isStreaming || sendUiSettling,
            exceedsInputLimit = text.length > INPUT_MAX_CHARS
        )
        if (!sendGate.canSubmit) return
        val trimmedText = text.trim()
        if (hasRemoteHistorySource && !context.hasActiveNetworkConnection()) {
            markUserMessageSendFailed(trimmedText)
            return
        }
        commitSendMessage(trimmedText)
    }

    suspend fun scrollToBottom(animated: Boolean, includeAnchorSpacer: Boolean = true) {
        if (messages.isEmpty() && !hasStreamingItem && !hasStreamAnchorSpacer) return
        lastProgrammaticScrollMs = SystemClock.uptimeMillis()
        programmaticScroll = true
        try {
            withFrameNanos { }
            val lastIndex = (
                messages.size +
                    if (hasStreamingItem) 1 else 0 +
                    if (hasStreamAnchorSpacer && includeAnchorSpacer) 1 else 0
                ) - 1
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
        if (messages.isEmpty() && !hasStreamingItem && !hasStreamAnchorSpacer) return
        lastProgrammaticScrollMs = SystemClock.uptimeMillis()
        programmaticScroll = true
        try {
            withFrameNanos { }
            val lastIndex = (
                messages.size +
                    if (hasStreamingItem) 1 else 0 +
                    if (hasStreamAnchorSpacer) 1 else 0
                ) - 1
            if (lastIndex < 0) return
            listState.scrollToItem(lastIndex)
            withFrameNanos { }
        } finally {
            programmaticScroll = false
            lastProgrammaticScrollMs = SystemClock.uptimeMillis()
        }
    }

    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            restoreBottomAfterImeClose =
                atBottom &&
                    !isStreaming &&
                    !hasStreamAnchorSpacer &&
                    !userDetachedFromBottom &&
                    !listState.isScrollInProgress &&
                    !programmaticScroll
            suppressJumpButtonForImeTransition = true
            jumpButtonVisible = false
            return@LaunchedEffect
        }

        if (restoreBottomAfterImeClose && !isStreaming && !hasStreamAnchorSpacer) {
            withFrameNanos { }
            if (!userDetachedFromBottom && !listState.isScrollInProgress && !programmaticScroll) {
                scrollToBottom(
                    animated = false,
                    includeAnchorSpacer = true
                )
            }
            jumpButtonVisible = false
            restoreBottomAfterImeClose = false
        } else {
            restoreBottomAfterImeClose = false
        }

        repeat(2) { withFrameNanos { } }
        suppressJumpButtonForImeTransition = false
    }

    LaunchedEffect(pendingFinalBottomSnap, messages.size, isStreaming) {
        if (!pendingFinalBottomSnap || isStreaming) return@LaunchedEffect
        repeat(2) { withFrameNanos { } }
        for (attempt in 0 until 4) {
            withFrameNanos { }
            if (isWithinFinalBottomSnapTolerance()) {
                break
            }
            scrollToBottom(animated = false)
            if (!listState.canScrollForward || isWithinFinalBottomSnapTolerance()) {
                break
            }
            if (attempt < 3) {
                delay(16)
            }
        }
        if (isWithinFinalBottomSnapTolerance()) {
            repeat(2) { withFrameNanos { } }
        }
        jumpButtonVisible = false
        pendingFinalBottomSnap = false
    }

    LaunchedEffect(
        pendingStreamSpacerRelease,
        pendingFinalBottomSnap,
        isStreaming,
        streamingMessageId,
        streamAnchorReservePx
    ) {
        if (!pendingStreamSpacerRelease || isStreaming || pendingFinalBottomSnap) return@LaunchedEffect
        if (streamAnchorReservePx > 0) {
            repeat(2) { withFrameNanos { } }
        }
        streamAnchorReservePx = 0
        initialStreamAnchorReservePx = 0
        pendingStreamSpacerRelease = false
        pendingFinalBottomSnap = pendingFinalBottomSnapAfterSpacer
        pendingFinalBottomSnapAfterSpacer = false
        streamingMessageId = null
        streamingMessageContent = ""
    }

    fun completeStreamingImmediatelyFromBackground() {
        mainHandler.post {
            if (!isStreaming) return@post
            val shouldSnapToBottomOnFinish =
                autoScrollMode == AutoScrollMode.StreamAnchorFollow &&
                    !userInteracting &&
                    !userDetachedFromBottom &&
                    currentStreamingOverflowDelta() > finalBottomSnapThresholdPx
            val finalId = streamingMessageId
                ?: anchoredUserMessageId?.let(::assistantMessageIdForSourceUser)
                ?: "assistant_${UUID.randomUUID()}"
            val finalContent = normalizeAssistantText(FAKE_STREAM_TEXT)
            fakeStreamJob?.cancel()
            fakeStreamJob = null
            streamRevealJob?.cancel()
            streamRevealJob = null
            streamingMessageId = finalId
            streamingMessageContent = finalContent
            streamingRevealBuffer = ""
            isStreaming = false
            streamingRevealBuffer = ""
            streamingFreshStart = -1
            streamingFreshEnd = -1
            streamingLineAdvanceTick = 0
            streamingContentBottomPx = -1
            streamBottomFollowActive = false
            pendingResumeAutoFollow = false
            streamingBackgrounded = false
            scrollMode = ScrollMode.Idle
            userDetachedFromBottom = false
            autoScrollMode = AutoScrollMode.Idle
            jumpButtonVisible = false
            if (finalContent.isNotBlank()) {
                applyCompletedAssistantMessageInPlace(
                    target = messages,
                    messageId = finalId,
                    content = finalContent
                )
                pendingStreamSpacerRelease = true
                pendingFinalBottomSnap = false
                pendingFinalBottomSnapAfterSpacer = shouldSnapToBottomOnFinish
                persistTick++
                val persistedMessages = persistableMessagesSnapshot()
                val prewarmMessages = persistedMessages.takeLast(2)
                snackbarScope.launch {
                    context.saveLocalChatWindow(chatScopeId, persistedMessages)
                    context.clearLocalStreamingDraft(chatScopeId)
                    prewarmAssistantMarkdown(prewarmMessages)
                }
            } else {
                removeMessageById(finalId)
                streamingMessageId = null
                streamingMessageContent = ""
                pendingStreamSpacerRelease = false
                pendingFinalBottomSnap = shouldSnapToBottomOnFinish
                pendingFinalBottomSnapAfterSpacer = false
            }
        }
    }

    DisposableEffect(lifecycleOwner, focusManager, keyboardController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                lifecycleResumeReady = false
                if (!isStreaming) {
                    restoreBottomAfterLifecycleResume =
                        atBottom &&
                            (messages.isNotEmpty() || hasStreamingItem) &&
                            !listState.isScrollInProgress &&
                            !programmaticScroll
                    suppressJumpButtonForLifecycleResume = restoreBottomAfterLifecycleResume
                    if (restoreBottomAfterLifecycleResume) {
                        jumpButtonVisible = false
                    }
                }
                if (isStreaming) {
                    streamingBackgrounded = true
                    if (!hasRemoteHistorySource) {
                        completeStreamingImmediatelyFromBackground()
                    }
                }
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            } else if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                streamingBackgrounded = false
                lifecycleResumeReady = true
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

    LaunchedEffect(
        restoreBottomAfterLifecycleResume,
        lifecycleResumeReady,
        isStreaming,
        messages.size,
        hasStreamingItem
    ) {
        if (!restoreBottomAfterLifecycleResume || !lifecycleResumeReady) return@LaunchedEffect
        if (isStreaming || (messages.isEmpty() && !hasStreamingItem)) {
            restoreBottomAfterLifecycleResume = false
            suppressJumpButtonForLifecycleResume = false
            return@LaunchedEffect
        }
        var stableResumeOverflowPx = Int.MAX_VALUE
        repeat(4) {
            withFrameNanos { }
            val overflowPx = currentBottomOverflowPx()
            if (overflowPx == Int.MAX_VALUE) return@repeat
            stableResumeOverflowPx = minOf(stableResumeOverflowPx, overflowPx)
        }
        if (
            !listState.isScrollInProgress &&
            !programmaticScroll &&
            stableResumeOverflowPx != Int.MAX_VALUE &&
            stableResumeOverflowPx > lifecycleResumeBottomSnapThresholdPx
        ) {
            scrollToBottom(
                animated = false,
                includeAnchorSpacer = true
            )
            jumpButtonVisible = false
        }
        repeat(2) { withFrameNanos { } }
        suppressJumpButtonForLifecycleResume = false
        restoreBottomAfterLifecycleResume = false
    }

    suspend fun isBottomSettled(stableFrames: Int = 4): Boolean {
        repeat(stableFrames) {
            withFrameNanos { }
            if (!isWithinBottomTolerance()) return false
        }
        return true
    }

    suspend fun scrollAfterSendAnchor() {
        if (messages.isEmpty() && !hasStreamingItem) return
        lastProgrammaticScrollMs = SystemClock.uptimeMillis()
        programmaticScroll = true
        try {
            val anchorTop = assistantStartAnchorTopPx
            val anchorIndex = run {
                val sourceUserId = anchoredUserMessageId
                sourceUserId
                    ?.let(::assistantMessageIdForSourceUser)
                    ?.let { assistantId -> messages.indexOfFirst { it.id == assistantId } }
                    ?.takeIf { it >= 0 }
                    ?: sourceUserId
                        ?.let { userId -> messages.indexOfFirst { it.id == userId } }
                        ?.takeIf { it >= 0 }
                    ?: messages.lastIndex
            }
            if (anchorIndex >= 0) {
                listState.scrollToItem(anchorIndex, scrollOffset = -anchorTop)
                repeat(4) { withFrameNanos { } }
            }
        } finally {
            programmaticScroll = false
            lastProgrammaticScrollMs = SystemClock.uptimeMillis()
        }
    }

    LaunchedEffect(
        scrollMode,
        isStreaming,
        hasStreamingItem,
        userInteracting,
        userDetachedFromBottom,
        listState.isScrollInProgress
    ) {
        if (!hasStreamingItem || !isStreaming) {
            streamBottomFollowActive = false
            return@LaunchedEffect
        }
        while (isActive && hasStreamingItem && isStreaming) {
            withFrameNanos { }
            if (
                scrollMode != ScrollMode.AutoFollow ||
                listState.isScrollInProgress ||
                userInteracting
            ) {
                streamBottomFollowActive = false
                return@LaunchedEffect
            }
            if (streamingMessageContent.isBlank()) {
                streamBottomFollowActive = false
                continue
            }
            val overflow = currentStreamingOverflowDelta()
            val stepPx = resolveStreamingFollowStepPx(overflow)
            if (stepPx <= 0) {
                streamBottomFollowActive = false
                continue
            }
            streamBottomFollowActive = true
            lastProgrammaticScrollMs = SystemClock.uptimeMillis()
            programmaticScroll = true
            try {
                val consumed = listState.scrollBy(stepPx.toFloat())
                if (consumed > 0f) {
                    streamingLineAdvanceTick++
                }
            } finally {
                programmaticScroll = false
                lastProgrammaticScrollMs = SystemClock.uptimeMillis()
                streamBottomFollowActive = false
            }
        }
    }

    LaunchedEffect(sendTick) {
        if (messages.isEmpty()) return@LaunchedEffect
        scrollMode = ScrollMode.Idle
        autoScrollMode = AutoScrollMode.AnchorUser
        userInteracting = false
        scrollAfterSendAnchor()
        scrollMode = if (isStreaming && streamingMessageContent.isNotBlank()) {
            ScrollMode.AutoFollow
        } else {
            ScrollMode.Idle
        }
        autoScrollMode = if (isStreaming && streamingMessageContent.isNotBlank()) {
            AutoScrollMode.StreamAnchorFollow
        } else {
            AutoScrollMode.AnchorUser
        }
    }

    LaunchedEffect(
        isStreaming,
        hasStreamingItem,
        scrollMode,
        streamAnchorReservePx,
        initialStreamAnchorReservePx,
        streamingMessageContent.length,
        listState.isScrollInProgress,
        userInteracting
    ) {
        if (!isStreaming || !hasStreamingItem) return@LaunchedEffect
        if (scrollMode != ScrollMode.Idle) return@LaunchedEffect
        if (streamingMessageContent.isBlank()) return@LaunchedEffect
        if (userInteracting || listState.isScrollInProgress) return@LaunchedEffect
        val initialReservePx = initialStreamAnchorReservePx
        if (initialReservePx <= 0) return@LaunchedEffect
        val releaseThresholdPx = maxOf(
            (initialReservePx * 0.1f).roundToInt(),
            streamAnchorReleaseThresholdPx
        )
        if (streamAnchorReservePx <= releaseThresholdPx) {
            scrollMode = ScrollMode.AutoFollow
        }
    }

    LaunchedEffect(
        hasStartedConversation,
        messages.size,
        isStreaming,
        startupLayoutReady,
        hasStreamingItem,
        initialBottomSnapDone,
        startupHydrationBarrierSatisfied
    ) {
        if (hasStartedConversation) return@LaunchedEffect
        if (initialBottomSnapDone) return@LaunchedEffect
        if (!startupHydrationBarrierSatisfied) return@LaunchedEffect
        if (!startupLayoutReady) return@LaunchedEffect
        if (messages.isEmpty() && !hasStreamingItem) {
            jumpButtonVisible = false
            initialBottomSnapDone = true
            return@LaunchedEffect
        }
        if (messages.isEmpty() || isStreaming || hasStreamingItem) return@LaunchedEffect
        repeat(3) { withFrameNanos { } }
        repeat(4) { attempt ->
            scrollToBottom(animated = false)
            if (!listState.canScrollForward || isBottomSettled()) {
                jumpButtonVisible = false
                initialBottomSnapDone = true
                return@LaunchedEffect
            }
            if (attempt < 3) {
                delay(60)
            }
        }
        jumpButtonVisible = false
        initialBottomSnapDone = true
    }

    fun jumpToBottom() {
        snackbarScope.launch {
            if (messages.isEmpty() && !hasStreamingItem) return@launch
            val jumpingIntoStreaming = isStreaming && hasStreamingItem
            scrollMode = if (jumpingIntoStreaming) {
                ScrollMode.AutoFollow
            } else {
                ScrollMode.Idle
            }
            autoScrollMode = if (jumpingIntoStreaming) {
                AutoScrollMode.StreamAnchorFollow
            } else {
                AutoScrollMode.Idle
            }
            userInteracting = false
            pendingResumeAutoFollow = false
            jumpButtonVisible = false
            if (jumpingIntoStreaming) {
                userDetachedFromBottom = false
                scrollToStreamingFollowBoundary()
            } else {
                userDetachedFromBottom = false
                scrollToBottom(animated = false)
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(appCenterTint)
            .onSizeChanged {
                chatRootWidthPx = it.width
                chatRootHeightPx = it.height
            }
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                chatRootLeftPx = bounds.left
                chatRootTopPx = bounds.top
            }
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
        val topBarReservedHeight = topInset + chromeButtonSize + TOP_CHROME_MASK_EXTRA
        val pageSurface = Color(0xFFFFFFFF)
        val navigationBottomInset: Dp = WindowInsets.safeDrawing
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding()
        val inputChromeHorizontalPadding = when {
            maxWidth < 360.dp -> 8.dp
            maxWidth < 600.dp -> 10.dp
            else -> chromeHorizontalPadding
        }
        val inputChromeBottomPadding = 8.dp
        val inputLimitHintOffsetPx = with(density) { 14.dp.roundToPx() } + inputChromeRowHeightPx
        val composerSettlingChromeMinHeight = with(density) {
            composerSettlingChromeHeightPx.coerceAtLeast(0).toDp()
        }
        val inputChromeSurface = Color.White
        val inputChromeBorder = Color(0xFFBCC2CA).copy(alpha = 0.9f)
        val inputFieldSurface = Color.White
        val inputFieldBorder = Color(0xFFBCC2CA).copy(alpha = 0.88f)
        val composerHostBounds =
            if (composerCollapseOverlayVisible) {
                composerCollapseOverlayHostBoundsSnapshot ?: composerHostBoundsInWindow
            } else {
                composerHostBoundsInWindow
            }
        val composerChromeBounds =
            if (composerCollapseOverlayVisible) {
                composerCollapseOverlayChromeBoundsSnapshot ?: composerChromeBoundsInWindow
            } else {
                composerChromeBoundsInWindow
            }
        val composerCollapseOverlayHostTop =
            composerHostBounds?.let { with(density) { (it.top - chatRootTopPx).toDp() } }
        val composerCollapseOverlayHostStart =
            composerHostBounds?.let { with(density) { (it.left - chatRootLeftPx).toDp() } }
        val composerCollapseOverlayHostWidth =
            composerHostBounds?.let { with(density) { it.width.toDp() } }
        val composerCollapseOverlayHostHeight =
            composerHostBounds?.let { with(density) { it.height.toDp() } }
        val composerCollapseOverlayWidth =
            composerChromeBounds?.let { with(density) { it.width.toDp() } }
        val composerCollapseOverlayHeight =
            composerChromeBounds?.let { with(density) { it.height.toDp() } }
        val composerCollapseOverlayRowTop =
            if (composerHostBounds != null && composerChromeBounds != null) {
                with(density) { (composerChromeBounds.top - composerHostBounds.top).toDp() }
            } else {
                null
            }
        val composerCollapseOverlayRowStart =
            if (composerHostBounds != null && composerChromeBounds != null) {
                with(density) { (composerChromeBounds.left - composerHostBounds.left).toDp() }
            } else {
                null
            }
        val composerOverlayHintText = when {
            composerStatusHintVisible && composerStatusHintText.isNotBlank() -> composerStatusHintText
            inputLimitHintVisible -> "已超过6000字，暂时不能发送"
            else -> null
        }
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
                        .alpha(if (composerCollapseOverlayVisible) 0f else 1f)
                        .onGloballyPositioned { coordinates ->
                            composerHostBoundsInWindow = coordinates.boundsInWindow()
                        }
                        .background(pageSurface)
                ) {
                    if (composerOverlayHintText != null) {
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
                                text = composerOverlayHintText,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                    val sendGate = buildSendGateState(
                        rawInput = input.value.text,
                        isStreaming = isStreaming || sendUiSettling,
                        exceedsInputLimit = input.value.text.length > INPUT_MAX_CHARS
                    )
                    val inputTextToolbar = remember(chatScopeId) {
                        buildInputSelectionTextToolbar()
                    }
                    val canPressSend = sendGate.canPress
                    val canSend = sendGate.canSubmit
                    val actionBg = if (canPressSend) Color(0xFF111111) else Color(0xFFD3D4D6)
                    val actionTint = if (canPressSend) Color.White else Color(0xFF7F8083)
                    ComposerChromeRow(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .widthIn(max = chromeMaxWidth)
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            .heightIn(min = composerSettlingChromeMinHeight)
                            .onSizeChanged {
                                inputChromeRowHeightPx = it.height
                                if (it.height > 0) {
                                    inputChromeMeasured = true
                                }
                            }
                            .onGloballyPositioned { coordinates ->
                                composerChromeBoundsInWindow = coordinates.boundsInWindow()
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
                        onAddClick = { performButtonHaptic() },
                        inputShellModifier = Modifier.onGloballyPositioned { coordinates ->
                            val bounds = coordinates.boundsInWindow()
                            inputFieldBoundsInWindow = bounds
                            composerTopInViewportPx =
                                (bounds.top - messageViewportTopPx).roundToInt()
                            composerMeasured = true
                            applyPendingInputSelectionToolbarIfReady(bounds)
                        },
                        inputContent = {
                            CompositionLocalProvider(
                                LocalTextSelectionColors provides inputSelectionColors,
                                LocalTextToolbar provides inputTextToolbar
                            ) {
                                ChatInputField(
                                    value = input.value,
                                    focused = inputFieldFocused,
                                    suppressCursor = suppressInputCursor,
                                    settlingMinHeightPx = composerSettlingMinHeightPx,
                                    suppressPlaceholder =
                                        sendUiSettling ||
                                            (imeVisible && composerSettlingMinHeightPx > 0) ||
                                            composerSettlingChromeHeightPx > 0,
                                    onFocusChanged = { focused ->
                                        inputFieldFocused = focused
                                        if (focused) {
                                            suppressInputCursor = false
                                            inputContentHeightPx = inputContentHeightPx.coerceAtLeast(
                                                startupInputContentHeightEstimatePx
                                            )
                                        }
                                    },
                                    onContentHeightChanged = { height ->
                                        inputContentHeightPx = height
                                    },
                                    onValueChange = {
                                        if (
                                            it.text.length > INPUT_MAX_CHARS &&
                                            input.value.text.length <= INPUT_MAX_CHARS
                                        ) {
                                            inputLimitHintTick++
                                        }
                                        suppressInputCursor = false
                                        input.value = it
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 2.dp),
                                    placeholder = { Text("描述种植问题", color = Color(0xFFAEAFB4)) },
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
                            performButtonHaptic()
                            if (sendGate.blockReason == SendBlockReason.InputTooLong) {
                                inputLimitHintTick++
                            } else if (canSend) {
                                sendMessage()
                            }
                        }
                    )
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
                    .pointerInput(imeVisible) {
                        awaitEachGesture {
                            awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial
                            )
                            val up = waitForUpIgnoringConsumption(pass = PointerEventPass.Initial)
                            if (up == null) return@awaitEachGesture
                            val tapInWindow = Offset(
                                x = up.position.x + messageViewportLeftPx,
                                y = up.position.y + messageViewportTopPx
                            )
                            if (inputSelectionMenuBoundsInRoot?.containsPoint(tapInWindow) == true) {
                                return@awaitEachGesture
                            }
                            val tappedInsideInputField =
                                inputFieldBoundsInWindow?.containsPoint(tapInWindow) == true
                            if (inputSelectionToolbarState != null && !tappedInsideInputField) {
                                clearInputSelectionToolbar()
                            }
                            if (imeVisible) {
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                            }
                        }
                    }
                    .onSizeChanged {
                        messageViewportWidthPx = it.width
                        messageViewportHeightPx = it.height
                        if (it.width > 0 && it.height > 0) {
                            messageViewportMeasured = true
                        }
                    }
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInWindow()
                        messageViewportLeftPx = bounds.left
                        messageViewportTopPx = bounds.top
                    }
            ) {
                CompositionLocalProvider(
                    LocalBringIntoViewSpec provides StaticMessageSelectionBringIntoViewSpec
                ) {
                    LazyColumn(
                    state = listState,
                    userScrollEnabled = true,
                    modifier = Modifier
                        .then(
                            if (enableStreamingScrollLock) {
                                Modifier.nestedScroll(streamingDirectionLock)
                            } else {
                                Modifier
                            }
                        )
                        .fillMaxSize()
                        .then(
                            if (hasActiveMessageSelection) {
                                selectionDismissTapModifier(activeMessageSelectionMessageId ?: "selection")
                            } else {
                                Modifier
                            }
                        )
                        .then(
                            if (shouldRevealMessageList) {
                                Modifier
                            } else {
                                Modifier.graphicsLayer(alpha = 0f)
                            }
                        ),
                    contentPadding = PaddingValues(
                        top = topBarReservedHeight,
                        bottom = with(density) { bottomContentReservedHeightPx.toDp() } +
                            BOTTOM_OVERLAY_CONTENT_CLEARANCE
                    )
                    ) {
                        items(
                            items = messages,
                            key = { it.id },
                            contentType = { it.role }
                        ) { msg ->
                            DisposableEffect(msg.id) {
                                onDispose {
                                    messageSelectionBoundsById.remove(msg.id)
                                    if (pendingMessageSelectionToolbarState?.messageId == msg.id) {
                                        pendingMessageSelectionToolbarState = null
                                    }
                                    if (messageSelectionToolbarState?.messageId == msg.id) {
                                        messageSelectionToolbarState = null
                                    }
                                }
                            }
                            val isActiveStreamingAssistant =
                                msg.role == ChatRole.ASSISTANT &&
                                    msg.id == streamingMessageId &&
                                    (isStreaming || pendingStreamSpacerRelease)
                            val assistantDisplayContent =
                                if (isActiveStreamingAssistant && (isStreaming || streamingMessageContent.isNotBlank())) {
                                    streamingMessageContent
                                } else {
                                    msg.content
                                }
                            val copySourceContent =
                                if (msg.role == ChatRole.ASSISTANT) assistantDisplayContent else msg.content
                            val fullCopyText = remember(msg.role, copySourceContent) {
                                buildRenderedMessageCopyText(msg.role, copySourceContent)
                            }
                            val messageTextToolbar = remember(msg.id, msg.role, fullCopyText) {
                                buildMessageSelectionTextToolbar(
                                    messageId = msg.id,
                                    role = msg.role,
                                    fullCopyText = fullCopyText
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = listHorizontalPadding, vertical = 8.dp)
                                    .then(
                                        if (
                                            hasActiveMessageSelection &&
                                            activeMessageSelectionMessageId != msg.id
                                        ) {
                                            selectionDismissTapModifier(
                                                activeMessageSelectionMessageId ?: "selection",
                                                msg.id
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .widthIn(max = chromeMaxWidth)
                                        .fillMaxWidth()
                                        .onGloballyPositioned { coordinates ->
                                            if (msg.role == ChatRole.ASSISTANT) {
                                                val bounds = coordinates.boundsInWindow()
                                                if (messageSelectionBoundsById[msg.id] != bounds) {
                                                    messageSelectionBoundsById[msg.id] = bounds
                                                }
                                                applyPendingMessageSelectionToolbarIfReady(
                                                    messageId = msg.id,
                                                    bounds = bounds
                                                )
                                            }
                                        }
                                ) {
                                    val failedUserState = failedUserMessageStates[msg.id]
                                    val failedAssistantState = failedAssistantMessageStates[msg.id]
                                    if (msg.role == ChatRole.ASSISTANT) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (isActiveStreamingAssistant) {
                                                CompositionLocalProvider(
                                                    LocalTextSelectionColors provides messageSelectionColors,
                                                    LocalTextToolbar provides messageTextToolbar
                                                ) {
                                                    key(messageSelectionResetEpoch) {
                                                        AssistantMessageContent(
                                                            content = assistantDisplayContent,
                                                            isStreaming = isStreaming,
                                                            streamingFreshStart = streamingFreshStart,
                                                            streamingFreshEnd = streamingFreshEnd,
                                                            streamingFreshTick = streamingFreshTick,
                                                            streamingLineAdvanceTick = streamingLineAdvanceTick,
                                                            strictLineReveal =
                                                                isStreaming &&
                                                                    !userDetachedFromBottom &&
                                                                    !userInteracting,
                                                            lineRevealLocked = lineRevealLocked,
                                                            selectionEnabled = !isStreaming,
                                                            showDisclaimer = true,
                                                            onStreamingContentBoundsChanged = { bounds ->
                                                                if (bounds != null) {
                                                                    streamingAnchorTopPx =
                                                                        (bounds.top - messageViewportTopPx).roundToInt()
                                                                    streamingContentBottomPx =
                                                                        (bounds.bottom - messageViewportTopPx).roundToInt()
                                                                }
                                                            },
                                                            modifier = Modifier.fillMaxWidth()
                                                        )
                                                    }
                                                }
                                            } else {
                                                SelectableRenderedStaticMessageContent(
                                                    content = msg.content,
                                                    textSelectionColors = messageSelectionColors,
                                                    textToolbar = messageTextToolbar,
                                                    selectionResetKey = messageSelectionResetEpoch,
                                                    showDisclaimer = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                            if (failedAssistantState != null) {
                                                MessageStatusFooter(
                                                    statusText = "回复未完成",
                                                    actionText = "重试",
                                                    alignEnd = false,
                                                    onActionClick = {
                                                        retryFailedAssistantMessage(msg.id)
                                                    }
                                                )
                                            }
                                        }
                                    } else {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.End,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            SelectableRenderedUserMessageBubble(
                                                content = msg.content,
                                                textSelectionColors = messageSelectionColors,
                                                textToolbar = messageTextToolbar,
                                                selectionResetKey = messageSelectionResetEpoch,
                                                userBubbleMaxWidth = userBubbleMaxWidth,
                                                userBubbleColor = userBubbleColor,
                                                onBubbleBoundsChanged = { bounds ->
                                                    if (bounds != null && messageSelectionBoundsById[msg.id] != bounds) {
                                                        messageSelectionBoundsById[msg.id] = bounds
                                                    }
                                                    if (bounds != null) {
                                                        applyPendingMessageSelectionToolbarIfReady(
                                                            messageId = msg.id,
                                                            bounds = bounds
                                                        )
                                                    }
                                                }
                                            )
                                            if (failedUserState != null) {
                                                MessageStatusFooter(
                                                    statusText = "未发送",
                                                    actionText = "重发",
                                                    alignEnd = true,
                                                    onActionClick = {
                                                        retryFailedUserMessage(msg.id)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (hasStreamAnchorSpacer) {
                            item(
                                key = "stream_anchor_spacer",
                                contentType = "stream_anchor_spacer"
                            ) {
                                Spacer(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(streamBottomSpacerDp)
                                )
                            }
                        }
                    }
                    }
                }

                if (showWelcomePlaceholder) {
                    val welcomeBottomInset =
                        with(density) { bottomContentReservedHeightPx.toDp() } +
                            BOTTOM_OVERLAY_CONTENT_CLEARANCE +
                            24.dp
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxSize()
                            .padding(
                                start = listHorizontalPadding,
                                end = listHorizontalPadding,
                                top = topBarReservedHeight,
                                bottom = welcomeBottomInset
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 360.dp)
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "欢迎咨询种植\n病虫害防治、施肥等问题\n必要时可上传图片",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color(0xFF141414),
                                lineHeight = 31.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                if (
                    composerCollapseOverlayVisible &&
                    composerCollapseOverlayHostTop != null &&
                    composerCollapseOverlayHostStart != null &&
                    composerCollapseOverlayHostWidth != null &&
                    composerCollapseOverlayHostHeight != null &&
                    composerCollapseOverlayRowTop != null &&
                    composerCollapseOverlayRowStart != null &&
                    composerCollapseOverlayWidth != null
                ) {
                    Box(
                        modifier = Modifier
                            .zIndex(44f)
                            .offset(
                                x = composerCollapseOverlayHostStart,
                                y = composerCollapseOverlayHostTop
                            )
                            .width(composerCollapseOverlayHostWidth)
                            .height(composerCollapseOverlayHostHeight)
                            .background(pageSurface)
                    ) {
                        ComposerChromeRow(
                            modifier = Modifier
                                .offset(
                                    x = composerCollapseOverlayRowStart,
                                    y = composerCollapseOverlayRowTop
                                )
                                .width(composerCollapseOverlayWidth)
                                .heightIn(min = composerCollapseOverlayHeight ?: 0.dp),
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

            if (navigationBottomInset > 0.dp) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                            .height(navigationBottomInset)
                        .background(pageSurface)
                )
            }

            if (hasActiveMessageSelection) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(with(density) { bottomContentReservedHeightPx.toDp() })
                        .background(pageSurface)
                        .zIndex(45f)
                )
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
                    .height(topBarReservedHeight)
                    .onGloballyPositioned { coordinates ->
                        topChromeMaskBottomPx = coordinates.boundsInWindow().bottom.roundToInt()
                    }
                    .background(pageSurface)
                    .zIndex(45f)
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(top = topInset + 4.dp, bottom = 2.dp)
                    .zIndex(46f)
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
                            text = "农技千查",
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

            inputSelectionToolbarState?.let { state ->
                val inputBounds = inputFieldBoundsInWindow
                if (inputBounds != null) {
                    InputSelectionMenuPopup(
                        state = state,
                        inputFieldBoundsInWindow = inputBounds,
                        viewportLeftPx = chatRootLeftPx,
                        viewportTopPx = chatRootTopPx,
                        topChromeMaskBottomPx = topChromeMaskBottomPx,
                        onMenuBoundsChanged = { bounds -> inputSelectionMenuBoundsInRoot = bounds },
                        onDismiss = { clearInputSelectionToolbar() }
                    )
                }
            }

            activeMessageSelectionState?.let { state ->
                MessageActionMenuPopup(
                    state = state,
                    viewportLeftPx = chatRootLeftPx,
                    viewportTopPx = chatRootTopPx,
                    contentViewportLeftPx = messageViewportLeftPx,
                    contentViewportTopPx = messageViewportTopPx,
                    contentViewportWidthPx = messageViewportWidthPx,
                    contentViewportHeightPx = messageViewportHeightPx,
                    topChromeMaskBottomPx = topChromeMaskBottomPx,
                    composerTopInViewportPx = composerTopInViewportPx,
                    onCopy = {
                        performButtonHaptic()
                        state.onCopyRequested?.invoke()
                        clearMessageSelection()
                    },
                    onCopyFull = {
                        performButtonHaptic()
                        state.onCopyFullRequested?.invoke()
                        clearMessageSelection()
                    }
                )
            }

            if (hasActiveMessageSelection) {
                val selectionMaskWidth = with(density) {
                    maxOf(view.width, messageViewportWidthPx).toDp()
                }
                if (topInset > 0.dp) {
                    Popup(
                        alignment = Alignment.TopStart,
                        properties = PopupProperties(focusable = false, clippingEnabled = false)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(selectionMaskWidth)
                                .height(topInset)
                                .background(pageSurface)
                        )
                    }
                }
                val bottomMaskHeight = with(density) { safeBottomInsetPx.toDp() }
                if (bottomMaskHeight > 0.dp) {
                    Popup(
                        alignment = Alignment.BottomStart,
                        properties = PopupProperties(focusable = false, clippingEnabled = false)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(selectionMaskWidth)
                                .height(bottomMaskHeight)
                                .background(pageSurface)
                        )
                    }
                }
            }

        }
    }
}

@Composable
private fun MessageActionMenuButton(
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
private fun MessageActionMenuCardContent(
    modifier: Modifier = Modifier,
    onCopy: () -> Unit,
    onCopyFull: () -> Unit
) {
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
            MessageActionMenuButton(
                label = "复制",
                minWidth = 78.dp,
                horizontalPadding = 17.dp,
                onClick = onCopy
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(16.dp)
                    .background(Color.White.copy(alpha = 0.16f))
            )
            MessageActionMenuButton(
                label = "全文复制",
                minWidth = 0.dp,
                horizontalPadding = 12.dp,
                onClick = onCopyFull
            )
        }
    }
}

private data class InputActionMenuItem(
    val label: String,
    val minWidth: Dp,
    val horizontalPadding: Dp,
    val onClick: () -> Unit
)

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
                MessageActionMenuButton(
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

@Composable
private fun InputSelectionMenuPopup(
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
    val marginPx = with(density) { MESSAGE_ACTION_MENU_MARGIN.roundToPx() }
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
        if (cardSize.height > 0) cardSize.height else with(density) { MESSAGE_ACTION_MENU_ESTIMATED_HEIGHT.roundToPx() }
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
private fun MessageActionMenuPopup(
    state: MessageSelectionToolbarState,
    viewportLeftPx: Float,
    viewportTopPx: Float,
    contentViewportLeftPx: Float,
    contentViewportTopPx: Float,
    contentViewportWidthPx: Int,
    contentViewportHeightPx: Int,
    topChromeMaskBottomPx: Int,
    composerTopInViewportPx: Int,
    onCopy: () -> Unit,
    onCopyFull: () -> Unit
) {
    val density = LocalDensity.current
    val verticalSpacingPx = with(density) { MESSAGE_ACTION_MENU_VERTICAL_SPACING.roundToPx() }
    val marginPx = with(density) { MESSAGE_ACTION_MENU_MARGIN.roundToPx() }
    val switchThresholdPx = with(density) { MESSAGE_ACTION_MENU_SWITCH_THRESHOLD.roundToPx() }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    var lockedSide by remember(state.messageId) { mutableStateOf<MessageActionMenuSide?>(null) }
    val anchorLocalX = (state.anchorX - viewportLeftPx).roundToInt()
    val anchorLocalY = (state.anchorY - viewportTopPx).roundToInt()
    val selectionBottomLocalY = (state.selectionBottomY - viewportTopPx).roundToInt()
    val contentLocalLeft = (contentViewportLeftPx - viewportLeftPx).roundToInt()
    val contentLocalTop = (contentViewportTopPx - viewportTopPx).roundToInt()
    val resolvedWidth = if (cardSize.width > 0) cardSize.width else with(density) { 148.dp.roundToPx() }
    val resolvedHeight =
        if (cardSize.height > 0) cardSize.height else with(density) { MESSAGE_ACTION_MENU_ESTIMATED_HEIGHT.roundToPx() }
    val minX = (contentLocalLeft + marginPx).coerceAtLeast(marginPx)
    val maxX = (
        contentLocalLeft +
            contentViewportWidthPx -
            resolvedWidth -
            marginPx
        ).coerceAtLeast(minX)
    val preferredX = (anchorLocalX - resolvedWidth / 2).coerceIn(minX, maxX)
    val topMaskBottomLocal =
        if (topChromeMaskBottomPx > 0) {
            (topChromeMaskBottomPx - viewportTopPx.roundToInt()).coerceAtLeast(0)
        } else {
            Int.MIN_VALUE
        }
    val bottomMaskTopLocal =
        if (composerTopInViewportPx > 0) {
            composerTopInViewportPx
        } else {
            Int.MAX_VALUE
        }
    val contentTopLimit = (contentLocalTop + marginPx).coerceAtLeast(marginPx)
    val contentBottomLimit = contentLocalTop + contentViewportHeightPx - marginPx
    val protectedTopLimit = maxOf(contentTopLimit, topMaskBottomLocal + marginPx)
    val protectedBottomLimit = minOf(contentBottomLimit, bottomMaskTopLocal - marginPx)
    val topHandleLocalY = minOf(anchorLocalY, selectionBottomLocalY)
    val bottomHandleLocalY = maxOf(anchorLocalY, selectionBottomLocalY)
    val preferredTop = topHandleLocalY - resolvedHeight - verticalSpacingPx
    val belowCandidate = bottomHandleLocalY + verticalSpacingPx
    val aboveMinTop = protectedTopLimit
    val aboveMaxTop = minOf(
        topHandleLocalY - verticalSpacingPx - resolvedHeight,
        protectedBottomLimit - resolvedHeight
    )
    val belowMinTop = maxOf(bottomHandleLocalY + verticalSpacingPx, protectedTopLimit)
    val belowMaxTop = protectedBottomLimit - resolvedHeight
    val topOverflow = (protectedTopLimit - preferredTop).coerceAtLeast(0)
    val bottomOverflow = (belowCandidate + resolvedHeight - protectedBottomLimit).coerceAtLeast(0)
    val canPlaceAbove = aboveMaxTop >= aboveMinTop
    val canPlaceBelow = belowMaxTop >= belowMinTop
    val canPlaceWithoutOverlap = canPlaceAbove || canPlaceBelow
    val resolvedSide =
        when (lockedSide) {
            MessageActionMenuSide.Above -> {
                if ((!canPlaceAbove || topOverflow > switchThresholdPx) && canPlaceBelow) {
                    MessageActionMenuSide.Below
                } else {
                    MessageActionMenuSide.Above
                }
            }

            MessageActionMenuSide.Below -> {
                if ((!canPlaceBelow || bottomOverflow > switchThresholdPx) && canPlaceAbove) {
                    MessageActionMenuSide.Above
                } else {
                    MessageActionMenuSide.Below
                }
            }

            null -> {
                when {
                    canPlaceAbove -> MessageActionMenuSide.Above
                    canPlaceBelow -> MessageActionMenuSide.Below
                    topOverflow <= bottomOverflow -> MessageActionMenuSide.Above
                    else -> MessageActionMenuSide.Below
                }
            }
        }
    LaunchedEffect(
        state.messageId,
        resolvedSide,
        state.anchorY,
        state.selectionBottomY,
        topChromeMaskBottomPx,
        composerTopInViewportPx
    ) {
        if (lockedSide != resolvedSide) {
            lockedSide = resolvedSide
        }
    }
    val rawPreferredY =
        if (resolvedSide == MessageActionMenuSide.Above) {
            preferredTop
        } else {
            belowCandidate
        }
    val fullyVisibleMinTop = protectedTopLimit
    val fullyVisibleMaxTop = (protectedBottomLimit - resolvedHeight).coerceAtLeast(fullyVisibleMinTop)
    val centeredFallbackTop =
        ((protectedTopLimit + protectedBottomLimit - resolvedHeight) / 2)
            .coerceIn(fullyVisibleMinTop, fullyVisibleMaxTop)
    val minAllowedTop =
        when {
            !canPlaceWithoutOverlap -> centeredFallbackTop
            resolvedSide == MessageActionMenuSide.Above && canPlaceAbove -> aboveMinTop
            resolvedSide == MessageActionMenuSide.Below && canPlaceBelow -> belowMinTop
            else -> fullyVisibleMinTop
        }
    val maxAllowedTop =
        when {
            !canPlaceWithoutOverlap -> centeredFallbackTop
            resolvedSide == MessageActionMenuSide.Above && canPlaceAbove -> aboveMaxTop.coerceAtLeast(minAllowedTop)
            resolvedSide == MessageActionMenuSide.Below && canPlaceBelow -> belowMaxTop.coerceAtLeast(minAllowedTop)
            else -> fullyVisibleMaxTop
        }
    val preferredY = rawPreferredY.coerceIn(minAllowedTop, maxAllowedTop)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { clip = true }
            .zIndex(40f)
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(preferredX, preferredY) }
                .onGloballyPositioned { coordinates ->
                    cardSize = coordinates.size
                }
                .pointerInput(state.anchorX, state.anchorY, state.selectionBottomY) {
                    detectTapGestures(onTap = {})
                }
        ) {
            MessageActionMenuCardContent(onCopy = onCopy, onCopyFull = onCopyFull)
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SelectableRenderedStaticMessageContent(
    content: String,
    textSelectionColors: TextSelectionColors,
    textToolbar: TextToolbar,
    selectionResetKey: Int,
    showDisclaimer: Boolean,
    expandToFullWidth: Boolean = true,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(
        LocalTextSelectionColors provides textSelectionColors,
        LocalTextToolbar provides textToolbar
    ) {
        key(selectionResetKey) {
            AssistantMessageContent(
                content = content,
                isStreaming = false,
                selectionEnabled = true,
                showDisclaimer = showDisclaimer,
                expandToFullWidth = expandToFullWidth,
                modifier = modifier
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SelectableRenderedUserMessageBubble(
    content: String,
    textSelectionColors: TextSelectionColors,
    textToolbar: TextToolbar,
    selectionResetKey: Int,
    userBubbleMaxWidth: Dp,
    userBubbleColor: Color,
    onBubbleBoundsChanged: (Rect?) -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .wrapContentWidth(Alignment.End)
                .widthIn(max = userBubbleMaxWidth)
                .clip(RoundedCornerShape(20.dp))
                .background(userBubbleColor)
                .onGloballyPositioned { coordinates ->
                    onBubbleBoundsChanged(coordinates.boundsInWindow())
                }
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            CompositionLocalProvider(
                LocalTextSelectionColors provides textSelectionColors,
                LocalTextToolbar provides textToolbar
            ) {
                key(selectionResetKey) {
                    SelectionContainer(
                        modifier = Modifier.wrapContentWidth(Alignment.Start)
                    ) {
                        Text(
                            text = content,
                            modifier = Modifier.wrapContentWidth(Alignment.Start),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF161616)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageStatusFooter(
    statusText: String,
    actionText: String,
    alignEnd: Boolean,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = statusText,
            fontSize = 13.sp,
            color = Color(0xFF7F8083)
        )
        Text(
            text = "  $actionText",
            modifier = Modifier
                .padding(start = 2.dp)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onActionClick
                )
                .padding(horizontal = 4.dp, vertical = 6.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF111111)
        )
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
