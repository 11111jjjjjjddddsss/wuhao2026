package com.nongjiqianwen

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

internal enum class StreamingRenderMode {
    Waiting,
    Streaming,
    Settled
}

internal enum class StreamingRevealMode {
    Free,
    Conservative
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
        strictLineReveal = renderMode != StreamingRenderMode.Settled &&
            revealMode != StreamingRevealMode.Free,
        lineRevealLocked = revealMode == StreamingRevealMode.Conservative,
        selectionEnabled = selectionEnabled,
        showDisclaimer = showDisclaimer,
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
        Box(
            modifier = if (expandToFullWidth) {
                stableModifier.fillMaxWidth()
            } else {
                stableModifier
            },
            contentAlignment = Alignment.TopStart
        ) {
            if (content.isBlank()) {
                RendererAssistantStreamingWaitingIndicatorImpl(
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coordinates ->
                                onStreamingContentBoundsChanged?.invoke(coordinates.boundsInWindow())
                            }
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
private fun RendererAssistantStreamingWaitingIndicatorImpl(modifier: Modifier = Modifier) {
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
        RendererGPTBreathingBallImpl()
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
