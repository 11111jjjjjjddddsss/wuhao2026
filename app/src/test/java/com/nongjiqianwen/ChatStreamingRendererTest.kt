package com.nongjiqianwen

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStreamingRendererTest {
    @Test
    fun streamingBoldStartsImmediatelyWithoutRawMarkers() {
        val rendered = buildRendererInlineAnnotatedString(
            text = "建议**控水",
            mode = RendererInlineMode.Streaming
        )

        assertEquals("建议控水", rendered.text)
        assertTrue(rendered.hasSpanFor("控水") { it.fontWeight == FontWeight.SemiBold })
    }

    @Test
    fun settledUnclosedBoldKeepsRawMarkers() {
        val rendered = buildRendererInlineAnnotatedString(
            text = "建议**控水",
            mode = RendererInlineMode.Settled
        )

        assertEquals("建议**控水", rendered.text)
    }

    @Test
    fun settledClosedBoldHidesMarkersAndStylesText() {
        val rendered = buildRendererInlineAnnotatedString(
            text = "建议**控水**后观察",
            mode = RendererInlineMode.Settled
        )

        assertEquals("建议控水后观察", rendered.text)
        assertTrue(rendered.hasSpanFor("控水") { it.fontWeight == FontWeight.SemiBold })
    }

    @Test
    fun literalStarAndAsciiOperatorRunsSurvive() {
        val input = "按 2 * 3 配比，A**B 保留。"
        val rendered = buildRendererInlineAnnotatedString(
            text = input,
            mode = RendererInlineMode.Streaming
        )

        assertEquals(input, rendered.text)
    }

    @Test
    fun streamingInlineCodeStartsImmediatelyWithoutRawBacktick() {
        val rendered = buildRendererInlineAnnotatedString(
            text = "查看`标签",
            mode = RendererInlineMode.Streaming
        )

        assertEquals("查看标签", rendered.text)
        assertTrue(rendered.hasSpanFor("标签") { it.fontFamily == FontFamily.Monospace })
    }

    @Test
    fun settledUnclosedInlineCodeKeepsRawBacktick() {
        val rendered = buildRendererInlineAnnotatedString(
            text = "查看`标签",
            mode = RendererInlineMode.Settled
        )

        assertEquals("查看`标签", rendered.text)
    }

    @Test
    fun linksAndBareUrlsRenderWithoutDroppingTrailingPunctuation() {
        val rendered = buildRendererInlineAnnotatedString(
            text = "看[官网](www.moa.gov.cn)，或 https://www.natesc.org.cn/。",
            mode = RendererInlineMode.Settled
        )

        assertEquals("看官网，或 https://www.natesc.org.cn/。", rendered.text)
        assertTrue(rendered.hasSpanFor("官网") { it.textDecoration == TextDecoration.Underline })
        assertTrue(rendered.hasSpanFor("https://www.natesc.org.cn/") { it.textDecoration == TextDecoration.Underline })
    }

    @Test
    fun markdownTableDegradesToPhoneFriendlyBulletRows() {
        val state = splitStreamingBlockState(
            "|项目|建议|\n|---|---|\n|水分|控水|\n"
        )

        assertEquals(listOf("- 项目：水分；建议：控水"), state.completedBlocks)
    }

    @Test
    fun activeBulletCanContainStreamingBoldText() {
        val model = classifyActiveStreamingLine("- **控水")

        assertTrue(model is StreamingLineModel.Bullet)
        val rendered = buildRendererInlineAnnotatedString(
            text = (model as StreamingLineModel.Bullet).text,
            mode = RendererInlineMode.Streaming
        )
        assertEquals("控水", rendered.text)
        assertTrue(rendered.hasSpanFor("控水") { it.fontWeight == FontWeight.SemiBold })
    }

    @Test
    fun chineseRevealStillConsumesOneVisibleCharacterPerStep() {
        val queued = queueStreamingChunk(
            currentMessageId = null,
            currentRevealBuffer = "",
            piece = "控水排湿",
            anchoredUserMessageId = "user_1",
            assistantIdProvider = { "assistant_$it" },
            fallbackIdProvider = { "assistant_fallback" }
        )

        val advanced = consumeStreamingRevealBatch(
            currentMessageId = queued?.messageId,
            currentContent = "",
            currentRevealBuffer = queued?.revealBuffer.orEmpty(),
            currentFreshTick = 0,
            lastFreshRevealMs = 0L,
            anchoredUserMessageId = "user_1",
            assistantIdProvider = { "assistant_$it" },
            fallbackIdProvider = { "assistant_fallback" },
            nowMs = 100L
        )

        assertEquals("控", advanced?.content)
        assertEquals("水排湿", advanced?.revealBuffer)
    }
}

private fun AnnotatedString.hasSpanFor(
    needle: String,
    predicate: (androidx.compose.ui.text.SpanStyle) -> Boolean
): Boolean {
    val start = text.indexOf(needle)
    if (start < 0) return false
    val end = start + needle.length
    return spanStyles.any { range ->
        range.start <= start && range.end >= end && predicate(range.item)
    }
}
