package com.nongjiqianwen

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun streamingPendingBoldMarkerDoesNotFlashRawMarkers() {
        val rendered = buildRendererInlineAnnotatedString(
            text = "建议**",
            mode = RendererInlineMode.Streaming
        )

        assertEquals("建议", rendered.text)
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
    fun streamingClosedBoldKeepsStableTextAndStyle() {
        val rendered = buildRendererInlineAnnotatedString(
            text = "建议**控水**后观察",
            mode = RendererInlineMode.Streaming
        )

        assertEquals("建议控水后观察", rendered.text)
        assertTrue(rendered.hasSpanFor("控水") { it.fontWeight == FontWeight.SemiBold })
    }

    @Test
    fun settledTrailingBoldMarkerKeepsRawMarkers() {
        val rendered = buildRendererInlineAnnotatedString(
            text = "建议**",
            mode = RendererInlineMode.Settled
        )

        assertEquals("建议**", rendered.text)
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
    fun streamingPendingItalicMarkerDoesNotFlashRawMarker() {
        val rendered = buildRendererInlineAnnotatedString(
            text = "建议 *",
            mode = RendererInlineMode.Streaming
        )

        assertEquals("建议 ", rendered.text)
    }

    @Test
    fun settledTrailingItalicMarkerKeepsRawMarker() {
        val rendered = buildRendererInlineAnnotatedString(
            text = "建议 *",
            mode = RendererInlineMode.Settled
        )

        assertEquals("建议 *", rendered.text)
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
    fun streamingPendingInlineCodeMarkerDoesNotFlashRawBacktick() {
        val rendered = buildRendererInlineAnnotatedString(
            text = "查看`",
            mode = RendererInlineMode.Streaming
        )

        assertEquals("查看", rendered.text)
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
        assertTrue(rendered.hasUrlFor("官网", "https://www.moa.gov.cn"))
        assertTrue(rendered.hasUrlFor("https://www.natesc.org.cn/", "https://www.natesc.org.cn/"))
    }

    @Test
    fun linksDisabledKeepsDisplayTextWithoutUrlAnnotations() {
        val rendered = buildRendererInlineAnnotatedString(
            text = "看[官网](www.moa.gov.cn)，或 https://www.natesc.org.cn/。",
            mode = RendererInlineMode.Streaming,
            linksEnabled = false
        )

        assertEquals("看官网，或 https://www.natesc.org.cn/。", rendered.text)
        assertFalse(rendered.hasUrlFor("官网", "https://www.moa.gov.cn"))
        assertFalse(rendered.hasUrlFor("https://www.natesc.org.cn/", "https://www.natesc.org.cn/"))
    }

    @Test
    fun finishShouldFlushInvisibleStructureMarkers() {
        assertTrue(shouldForceFlushStreamingRevealBufferForFinish("**"))
        assertTrue(shouldForceFlushStreamingRevealBufferForFinish("`"))
        assertFalse(shouldForceFlushStreamingRevealBufferForFinish("控"))
        assertFalse(shouldForceFlushStreamingRevealBufferForFinish(""))
    }

    @Test
    fun markdownTableRendersAsTableBlock() {
        val state = splitStreamingBlockState(
            "|项目|建议|\n|---|---|\n|水分|控水|\n"
        )
        val model = classifyStreamingLine(state.completedBlocks.first())

        assertTrue(model is StreamingLineModel.Table)
        val table = (model as StreamingLineModel.Table).table
        assertEquals(listOf("项目", "建议"), table.headers)
        assertEquals(listOf(listOf("水分", "控水")), table.rows)
        assertEquals("项目\t建议\n水分\t控水", table.toPlainCopyText())
    }

    @Test
    fun shortSeparatorDoesNotRenderAsTable() {
        val stats = buildRendererStructureStats(
            "成品含腐植酸尿素 | 普通尿素 + 矿源黄腐酸钾\n" +
                "- | -\n" +
                "前者方便，后者灵活。"
        )

        assertEquals(0, stats.tableCount)
    }

    @Test
    fun singleColumnSeparatorDoesNotRenderAsTable() {
        val stats = buildRendererStructureStats(
            "|项目|\n" +
                "|---|\n" +
                "|水分|\n"
        )

        assertEquals(0, stats.tableCount)
    }

    @Test
    fun markdownTableKeepsFourColumnsAndRaggedRows() {
        val state = splitStreamingBlockState(
            "|维度|成品肥|自配方案|销售提示|\n" +
                "|---|---|---|---|\n" +
                "|便利性|高|中|需要讲清配比|\n" +
                "|灵活性|固定|可调整|\n"
        )
        val model = classifyStreamingLine(state.completedBlocks.first())

        assertTrue(model is StreamingLineModel.Table)
        val table = (model as StreamingLineModel.Table).table
        assertEquals(listOf("维度", "成品肥", "自配方案", "销售提示"), table.headers)
        assertEquals(listOf("灵活性", "固定", "可调整", ""), table.rows[1])
        assertEquals(
            "维度\t成品肥\t自配方案\t销售提示\n" +
                "便利性\t高\t中\t需要讲清配比\n" +
                "灵活性\t固定\t可调整\t",
            table.toPlainCopyText()
        )
        assertEquals(
            "便利性：\n" +
                "成品肥：高\n" +
                "自配方案：中\n" +
                "销售提示：需要讲清配比\n\n" +
                "灵活性：\n" +
                "成品肥：固定\n" +
                "自配方案：可调整",
            buildRendererPlainCopyText(
                "|维度|成品肥|自配方案|销售提示|\n" +
                    "|---|---|---|---|\n" +
                    "|便利性|高|中|需要讲清配比|\n" +
                    "|灵活性|固定|可调整|\n"
            )
        )
    }

    @Test
    fun markdownTableCopyRemovesInlineMarkdownMarkers() {
        val state = splitStreamingBlockState(
            "|维度|**成品肥**|自配方案|链接|\n" +
                "|---|---|---|---|\n" +
                "|便利性|`开袋即用`|[配比](https://example.com)|保留|\n"
        )
        val model = classifyStreamingLine(state.completedBlocks.first())

        assertTrue(model is StreamingLineModel.Table)
        val table = (model as StreamingLineModel.Table).table
        assertEquals(
            "维度\t成品肥\t自配方案\t链接\n" +
                "便利性\t开袋即用\t配比\t保留",
            table.toPlainCopyText()
        )
        assertEquals(
            "便利性：\n" +
                "成品肥：开袋即用\n" +
                "自配方案：配比\n" +
                "链接：保留",
            buildRendererPlainCopyText(
                "|维度|**成品肥**|自配方案|链接|\n" +
                    "|---|---|---|---|\n" +
                    "|便利性|`开袋即用`|[配比](https://example.com)|保留|\n"
            )
        )
    }

    @Test
    fun tildeCodeFenceDoesNotRenderPipeTextAsTable() {
        val stats = buildRendererStructureStats(
            "~~~\n" +
                "|项目|建议|\n" +
                "|---|---|\n" +
                "|水分|控水|\n" +
                "~~~"
        )

        assertEquals(0, stats.tableCount)
    }

    @Test
    fun indentedCodeBlockDoesNotRenderPipeTextAsTable() {
        val stats = buildRendererStructureStats(
            "    |项目|建议|\n" +
                "    |---|---|\n" +
                "    |水分|控水|\n"
        )

        assertEquals(0, stats.tableCount)
    }

    @Test
    fun rendererStructureStatsKeepsDividerDecisionStable() {
        val stats = buildRendererStructureStats(
            "先说清楚。\n\n" +
                "**处理建议**\n\n" +
                "继续观察。\n\n" +
                "一、用肥策略\n" +
                "少量多次。"
        )

        assertEquals(5, stats.blockCount)
        assertEquals(2, stats.headingCount)
        assertEquals(0, stats.tableCount)
        assertEquals(2, stats.dividerHeadingCount)
    }

    @Test
    fun leadingHeadingDoesNotCreateTopDivider() {
        val stats = buildRendererStructureStats(
            "**处理建议**\n\n" +
                "继续观察。\n\n" +
                "一、用肥策略\n" +
                "少量多次。"
        )

        assertEquals(4, stats.blockCount)
        assertEquals(2, stats.headingCount)
        assertEquals(1, stats.dividerHeadingCount)
    }

    @Test
    fun partialMarkdownTableHeaderStaysVisibleAsTextBeforeSeparator() {
        val state = splitStreamingBlockState("|维度|成品|自配|")

        assertEquals(emptyList<String>(), state.completedBlocks)
        assertEquals("|维度|成品|自配|", state.activeBlock)
    }

    @Test
    fun inlinePipeParagraphDoesNotWaitAsPartialTable() {
        val state = splitStreamingBlockState("A | B")

        assertEquals(emptyList<String>(), state.completedBlocks)
        assertEquals("A | B", state.activeBlock)
    }

    @Test
    fun markdownTableWaitsForFirstBodyRowBeforeRenderingTable() {
        val state = splitStreamingBlockState("|维度|成品|自配|\n|---|---|---|")
        val model = classifyActiveStreamingLine(state.activeBlock.orEmpty())

        assertTrue(model is StreamingLineModel.Paragraph)
        assertEquals(
            "|维度|成品|自配|\n|---|---|---|",
            (model as StreamingLineModel.Paragraph).text
        )
    }

    @Test
    fun standaloneBoldHeadingDividerSurvivesStreamingToSettled() {
        val streamingState = splitStreamingBlockState("先说清楚。\n\n**处理建议")
        val streamingModels = streamingState.completedBlocks.map(::classifyStreamingLine) +
            listOfNotNull(streamingState.activeBlock?.let(::classifyActiveStreamingLine))
        assertTrue(streamingModels[1] is StreamingLineModel.Heading)
        assertTrue(
            shouldShowStreamingSectionDivider(
                previous = streamingModels[0],
                current = streamingModels[1]
            )
        )

        val settledState = splitStreamingBlockState("先说清楚。\n\n**处理建议**\n\n继续观察。")
        val settledModels = settledState.completedBlocks.map(::classifyStreamingLine) +
            listOfNotNull(settledState.activeBlock?.let(::classifyStreamingLine))
        assertTrue(settledModels[1] is StreamingLineModel.Heading)
        assertTrue(
            shouldShowStreamingSectionDivider(
                previous = settledModels[0],
                current = settledModels[1]
            )
        )
    }

    @Test
    fun unclosedStandaloneBoldHeadingDividerSurvivesInSettledHistory() {
        val state = splitStreamingBlockState("先说清楚。\n\n**处理建议\n\n继续观察。")
        val models = state.completedBlocks.map(::classifyStreamingLine) +
            listOfNotNull(state.activeBlock?.let(::classifyStreamingLine))

        assertTrue(models[1] is StreamingLineModel.Heading)
        assertEquals("处理建议", (models[1] as StreamingLineModel.Heading).text)
        assertTrue(
            shouldShowStreamingSectionDivider(
                previous = models[0],
                current = models[1]
            )
        )
    }

    @Test
    fun chineseSectionHeadingKeepsDividerInSettledHistory() {
        val state = splitStreamingBlockState("先说清楚。\n\n一、成品含腐植酸尿素 vs. 自配方案\n\n继续观察。")
        val models = state.completedBlocks.map(::classifyStreamingLine) +
            listOfNotNull(state.activeBlock?.let(::classifyStreamingLine))

        assertTrue(models[1] is StreamingLineModel.Heading)
        assertEquals("一、成品含腐植酸尿素 vs. 自配方案", (models[1] as StreamingLineModel.Heading).text)
        assertTrue(
            shouldShowStreamingSectionDivider(
                previous = models[0],
                current = models[1]
            )
        )
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
    fun streamingTailBlockKeepsStreamingInlineModeAfterNewline() {
        assertEquals(
            RendererInlineMode.Streaming,
            rendererInlineModeForStreamingBlock(index = 1, lastIndex = 1)
        )
    }

    @Test
    fun earlierStreamingBlocksUseSettledInlineMode() {
        assertEquals(
            RendererInlineMode.Settled,
            rendererInlineModeForStreamingBlock(index = 0, lastIndex = 1)
        )
    }

    @Test
    fun committedStreamingBlockWithUnclosedBoldKeepsStreamingInlineMode() {
        val blockState = splitStreamingBlockState("建议**控水\n\n继续观察")
        val firstModel = classifyStreamingLine(blockState.completedBlocks.first())
        val mode = rendererInlineModeForStreamingBlock(
            index = 0,
            lastIndex = 1,
            model = firstModel
        )
        require(firstModel is StreamingLineModel.Paragraph)
        val rendered = buildRendererInlineAnnotatedString(
            text = firstModel.text,
            mode = mode
        )

        assertEquals(RendererInlineMode.Streaming, mode)
        assertEquals("建议控水", rendered.text)
        assertTrue(rendered.hasSpanFor("控水") { it.fontWeight == FontWeight.SemiBold })
    }

    @Test
    fun committedStreamingBlockWithClosedBoldCanUseSettledInlineMode() {
        val blockState = splitStreamingBlockState("建议**控水**\n\n继续观察")
        val firstModel = classifyStreamingLine(blockState.completedBlocks.first())
        val mode = rendererInlineModeForStreamingBlock(
            index = 0,
            lastIndex = 1,
            model = firstModel
        )

        assertEquals(RendererInlineMode.Settled, mode)
    }

    @Test
    fun standaloneBoldLineIsHeadingBlockForLightDivider() {
        val blockState = splitStreamingBlockState("先观察叶片变化。\n**处理建议**\n及时通风。")
        val models = blockState.completedBlocks.map(::classifyStreamingLine) +
            listOfNotNull(blockState.activeBlock?.let(::classifyStreamingLine))

        assertEquals(3, models.size)
        assertTrue(models[0] is StreamingLineModel.Paragraph)
        val heading = models[1]
        assertTrue(heading is StreamingLineModel.Heading)
        assertEquals("处理建议", (heading as StreamingLineModel.Heading).text)
        assertTrue(shouldShowStreamingSectionDivider(models[0], heading))
    }

    @Test
    fun inlineBoldSentenceStaysParagraphWithoutSectionDivider() {
        val blockState = splitStreamingBlockState("建议**控水**后观察。\n继续记录。")
        val model = classifyStreamingLine(blockState.activeBlock.orEmpty())

        assertTrue(model is StreamingLineModel.Paragraph)
    }

    @Test
    fun consecutiveBoldHeadingsDoNotStackDividers() {
        val first = classifyStreamingLine("**病因分析**")
        val second = classifyStreamingLine("**处理建议：**")

        assertTrue(first is StreamingLineModel.Heading)
        assertTrue(second is StreamingLineModel.Heading)
        assertFalse(shouldShowStreamingSectionDivider(first, second))
        assertEquals("处理建议：", (second as StreamingLineModel.Heading).text)
    }

    @Test
    fun activeStandaloneBoldHeadingStreamsAsHeading() {
        val model = classifyActiveStreamingLine("**处理建议")

        assertTrue(model is StreamingLineModel.Heading)
        assertEquals("处理建议", (model as StreamingLineModel.Heading).text)
    }

    @Test
    fun inlineBoldWithTrailingBodyStaysParagraph() {
        val model = classifyStreamingLine("**重点** 后面还有正文")

        assertTrue(model is StreamingLineModel.Paragraph)
    }

    @Test
    fun thirdLevelMarkdownHeadingCanUseLightDivider() {
        val previous = classifyStreamingLine("先看整体长势。")
        val heading = classifyStreamingLine("### 处理建议")

        assertTrue(heading is StreamingLineModel.Heading)
        assertTrue(shouldShowStreamingSectionDivider(previous, heading))
    }

    @Test
    fun pendingBoldMarkerDoesNotReplaceWaitingBallBeforeVisibleText() {
        val advanced = consumeStreamingRevealBatch(
            currentMessageId = null,
            currentContent = "",
            currentRevealBuffer = "**",
            anchoredUserMessageId = "user_1",
            assistantIdProvider = { "assistant_$it" },
            fallbackIdProvider = { "assistant_fallback" },
        )

        assertEquals("", advanced?.content)
        assertEquals("**", advanced?.revealBuffer)
    }

    @Test
    fun pendingBoldMarkerRevealsWithFirstVisibleText() {
        val advanced = consumeStreamingRevealBatch(
            currentMessageId = null,
            currentContent = "",
            currentRevealBuffer = "**控",
            anchoredUserMessageId = "user_1",
            assistantIdProvider = { "assistant_$it" },
            fallbackIdProvider = { "assistant_fallback" },
        )
        requireNotNull(advanced)
        val rendered = buildRendererInlineAnnotatedString(
            text = advanced.content,
            mode = RendererInlineMode.Streaming
        )

        assertEquals("**控", advanced.content)
        assertEquals("", advanced.revealBuffer)
        assertEquals("控", rendered.text)
        assertTrue(rendered.hasSpanFor("控") { it.fontWeight == FontWeight.SemiBold })
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
            anchoredUserMessageId = "user_1",
            assistantIdProvider = { "assistant_$it" },
            fallbackIdProvider = { "assistant_fallback" },
        )

        assertEquals("控", advanced?.content)
        assertEquals("水排湿", advanced?.revealBuffer)
    }

    @Test
    fun supplementaryCjkRevealDoesNotSplitSurrogatePair() {
        val extensionBCharacter = "\uD840\uDC00"
        val queued = queueStreamingChunk(
            currentMessageId = null,
            currentRevealBuffer = "",
            piece = "${extensionBCharacter}田",
            anchoredUserMessageId = "user_1",
            assistantIdProvider = { "assistant_$it" },
            fallbackIdProvider = { "assistant_fallback" }
        )

        val advanced = consumeStreamingRevealBatch(
            currentMessageId = queued?.messageId,
            currentContent = "",
            currentRevealBuffer = queued?.revealBuffer.orEmpty(),
            anchoredUserMessageId = "user_1",
            assistantIdProvider = { "assistant_$it" },
            fallbackIdProvider = { "assistant_fallback" },
        )

        assertEquals(extensionBCharacter, advanced?.content)
        assertEquals("田", advanced?.revealBuffer)
    }

    @Test
    fun emojiRevealDoesNotSplitSurrogatePair() {
        val seedling = "\uD83C\uDF31"
        val queued = queueStreamingChunk(
            currentMessageId = null,
            currentRevealBuffer = "",
            piece = "${seedling}增产",
            anchoredUserMessageId = "user_1",
            assistantIdProvider = { "assistant_$it" },
            fallbackIdProvider = { "assistant_fallback" }
        )

        val advanced = consumeStreamingRevealBatch(
            currentMessageId = queued?.messageId,
            currentContent = "",
            currentRevealBuffer = queued?.revealBuffer.orEmpty(),
            anchoredUserMessageId = "user_1",
            assistantIdProvider = { "assistant_$it" },
            fallbackIdProvider = { "assistant_fallback" },
        )

        assertEquals(seedling, advanced?.content)
        assertEquals("增产", advanced?.revealBuffer)
    }

    @Test
    fun zwjEmojiRevealDoesNotSplitVisibleCluster() {
        val farmer = "\uD83D\uDC68\u200D\uD83C\uDF3E"
        val queued = queueStreamingChunk(
            currentMessageId = null,
            currentRevealBuffer = "",
            piece = "${farmer}正在查看",
            anchoredUserMessageId = "user_1",
            assistantIdProvider = { "assistant_$it" },
            fallbackIdProvider = { "assistant_fallback" }
        )

        val advanced = consumeStreamingRevealBatch(
            currentMessageId = queued?.messageId,
            currentContent = "",
            currentRevealBuffer = queued?.revealBuffer.orEmpty(),
            anchoredUserMessageId = "user_1",
            assistantIdProvider = { "assistant_$it" },
            fallbackIdProvider = { "assistant_fallback" },
        )

        assertEquals(farmer, advanced?.content)
        assertEquals("正在查看", advanced?.revealBuffer)
    }

    @Test
    fun emojiWithSkinToneAndZwjDoesNotSplitVisibleCluster() {
        val farmer = "\uD83D\uDC68\uD83C\uDFFB\u200D\uD83C\uDF3E"
        val queued = queueStreamingChunk(
            currentMessageId = null,
            currentRevealBuffer = "",
            piece = "${farmer}正在查看",
            anchoredUserMessageId = "user_1",
            assistantIdProvider = { "assistant_$it" },
            fallbackIdProvider = { "assistant_fallback" }
        )

        val advanced = consumeStreamingRevealBatch(
            currentMessageId = queued?.messageId,
            currentContent = "",
            currentRevealBuffer = queued?.revealBuffer.orEmpty(),
            anchoredUserMessageId = "user_1",
            assistantIdProvider = { "assistant_$it" },
            fallbackIdProvider = { "assistant_fallback" },
        )

        assertEquals(farmer, advanced?.content)
        assertEquals("正在查看", advanced?.revealBuffer)
    }

    @Test
    fun flagEmojiRevealDoesNotSplitRegionalIndicatorPair() {
        val flag = "\uD83C\uDDE8\uD83C\uDDF3"
        val queued = queueStreamingChunk(
            currentMessageId = null,
            currentRevealBuffer = "",
            piece = "${flag}地区",
            anchoredUserMessageId = "user_1",
            assistantIdProvider = { "assistant_$it" },
            fallbackIdProvider = { "assistant_fallback" }
        )

        val advanced = consumeStreamingRevealBatch(
            currentMessageId = queued?.messageId,
            currentContent = "",
            currentRevealBuffer = queued?.revealBuffer.orEmpty(),
            anchoredUserMessageId = "user_1",
            assistantIdProvider = { "assistant_$it" },
            fallbackIdProvider = { "assistant_fallback" },
        )

        assertEquals(flag, advanced?.content)
        assertEquals("地区", advanced?.revealBuffer)
    }

    @Test
    fun longLatinTokenRevealIsCappedToAvoidLargePopIn() {
        val advanced = consumeStreamingRevealBatch(
            currentMessageId = null,
            currentContent = "",
            currentRevealBuffer = "chlorantraniliprole",
            anchoredUserMessageId = "user_1",
            assistantIdProvider = { "assistant_$it" },
            fallbackIdProvider = { "assistant_fallback" },
        )

        assertEquals("chlorant", advanced?.content)
        assertEquals("raniliprole", advanced?.revealBuffer)
    }

    @Test
    fun chineseRevealDrainsOneVisibleCharacterAtATime() {
        val queued = queueStreamingChunk(
            currentMessageId = null,
            currentRevealBuffer = "",
            piece = "控水排湿",
            anchoredUserMessageId = "user_1",
            assistantIdProvider = { "assistant_$it" },
            fallbackIdProvider = { "assistant_fallback" }
        )
        var content = ""
        var buffer = queued?.revealBuffer.orEmpty()
        val steps = mutableListOf<String>()

        while (buffer.isNotEmpty()) {
            val advanced = consumeStreamingRevealBatch(
                currentMessageId = queued?.messageId,
                currentContent = content,
                currentRevealBuffer = buffer,
                anchoredUserMessageId = "user_1",
                assistantIdProvider = { "assistant_$it" },
                fallbackIdProvider = { "assistant_fallback" },
            )

            requireNotNull(advanced)
            content = advanced.content
            buffer = advanced.revealBuffer
            steps += content
        }

        assertEquals(listOf("控", "控水", "控水排", "控水排湿"), steps)
        assertEquals("控水排湿", content)
    }

    @Test
    fun chineseRevealKeepsReadablePacing() {
        val advanced = consumeStreamingRevealBatch(
            currentMessageId = null,
            currentContent = "",
            currentRevealBuffer = "控",
            anchoredUserMessageId = "user_1",
            assistantIdProvider = { "assistant_$it" },
            fallbackIdProvider = { "assistant_fallback" },
        )

        assertEquals("控", advanced?.content)
        assertTrue((advanced?.delayMs ?: 0L) >= 20L)
    }

    @Test
    fun strongPunctuationPausesLongerThanPlainChineseReveal() {
        val plain = consumeStreamingRevealBatch(
            currentMessageId = null,
            currentContent = "",
            currentRevealBuffer = "控",
            anchoredUserMessageId = "user_1",
            assistantIdProvider = { "assistant_$it" },
            fallbackIdProvider = { "assistant_fallback" },
        )
        val punctuation = consumeStreamingRevealBatch(
            currentMessageId = null,
            currentContent = "",
            currentRevealBuffer = "。",
            anchoredUserMessageId = "user_1",
            assistantIdProvider = { "assistant_$it" },
            fallbackIdProvider = { "assistant_fallback" },
        )

        assertTrue((punctuation?.delayMs ?: 0L) > (plain?.delayMs ?: Long.MAX_VALUE))
    }

    @Test
    fun ellipsisPausesLikeStrongPunctuation() {
        val plain = consumeStreamingRevealBatch(
            currentMessageId = null,
            currentContent = "",
            currentRevealBuffer = "控",
            anchoredUserMessageId = "user_1",
            assistantIdProvider = { "assistant_$it" },
            fallbackIdProvider = { "assistant_fallback" },
        )
        val ellipsis = consumeStreamingRevealBatch(
            currentMessageId = null,
            currentContent = "",
            currentRevealBuffer = "…",
            anchoredUserMessageId = "user_1",
            assistantIdProvider = { "assistant_$it" },
            fallbackIdProvider = { "assistant_fallback" },
        )

        assertTrue((ellipsis?.delayMs ?: 0L) > (plain?.delayMs ?: Long.MAX_VALUE))
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

private fun AnnotatedString.hasUrlFor(
    needle: String,
    expectedUrl: String
): Boolean {
    val start = text.indexOf(needle)
    if (start < 0) return false
    val end = start + needle.length
    return getLinkAnnotations(start, end).any { range ->
        (range.item as? LinkAnnotation.Url)?.url == expectedUrl
    }
}
