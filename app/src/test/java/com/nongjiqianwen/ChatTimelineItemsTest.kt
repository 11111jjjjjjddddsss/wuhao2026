package com.nongjiqianwen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTimelineItemsTest {
    @Test
    fun todayAgriCardDoesNotOccupyEmptyWelcomeState() {
        val startAnchoredItems = buildChatTimelineItems(
            messages = emptyList(),
            todayAgriCard = todayAgriCard(),
            todayAgriCardAnchorMessageId = TODAY_AGRI_CARD_ANCHOR_START
        )
        val unanchoredItems = buildChatTimelineItems(
            messages = emptyList(),
            todayAgriCard = todayAgriCard(),
            todayAgriCardAnchorMessageId = null
        )

        assertEquals(emptyList<ChatTimelineItem>(), startAnchoredItems)
        assertEquals(emptyList<ChatTimelineItem>(), unanchoredItems)
    }

    @Test
    fun todayAgriCardAnchoredAfterCompletedAssistantStaysInsideTimeline() {
        val first = userMessage("m1")
        val second = assistantMessage("m2")

        val items = buildChatTimelineItems(
            messages = listOf(first, second),
            todayAgriCard = todayAgriCard(),
            todayAgriCardAnchorMessageId = second.id
        )

        assertEquals(
            listOf(
                ChatTimelineItem.Message(first),
                ChatTimelineItem.Message(second),
                ChatTimelineItem.TodayAgriCard(todayAgriCard())
            ),
            items
        )
    }

    @Test
    fun legacyTodayAgriStartAnchorDoesNotMigrateAfterCompletedAssistant() {
        val first = userMessage("m1")
        val second = assistantMessage("m2")

        val items = buildChatTimelineItems(
            messages = listOf(first, second),
            todayAgriCard = todayAgriCard(),
            todayAgriCardAnchorMessageId = TODAY_AGRI_CARD_ANCHOR_START
        )

        assertEquals(
            listOf(
                ChatTimelineItem.Message(first),
                ChatTimelineItem.Message(second)
            ),
            items
        )
    }

    @Test
    fun todayAgriCardWithoutAnchorFallsBackAfterLatestCompletedAssistant() {
        val first = userMessage("m1")
        val second = assistantMessage("m2")

        val items = buildChatTimelineItems(
            messages = listOf(first, second),
            todayAgriCard = todayAgriCard(),
            todayAgriCardAnchorMessageId = null
        )

        assertEquals(
            listOf(
                ChatTimelineItem.Message(first),
                ChatTimelineItem.Message(second),
                ChatTimelineItem.TodayAgriCard(todayAgriCard())
            ),
            items
        )
    }

    @Test
    fun todayAgriCardWithMissingAnchorFallsBackAfterHistoryNotice() {
        val first = userMessage("m1")
        val second = assistantMessage("m2")

        val items = buildChatTimelineItems(
            messages = listOf(first, second),
            todayAgriCard = todayAgriCard(),
            todayAgriCardAnchorMessageId = "trimmed-old-message",
            hiddenRoundCount = 6
        )

        assertTrue(items[0] is ChatTimelineItem.HistoryNotice)
        assertEquals(ChatTimelineItem.Message(first), items[1])
        assertEquals(ChatTimelineItem.Message(second), items[2])
        assertTrue(items[3] is ChatTimelineItem.TodayAgriCard)
    }

    @Test
    fun todayAgriCardDoesNotTreatFailedAssistantAsCompletedTail() {
        val first = userMessage("m1")
        val failedAssistant = assistantMessage("m2")

        val items = buildChatTimelineItems(
            messages = listOf(first, failedAssistant),
            todayAgriCard = todayAgriCard(),
            todayAgriCardAnchorMessageId = failedAssistant.id,
            failedAssistantMessageIds = setOf(failedAssistant.id)
        )

        assertEquals(
            listOf(
                ChatTimelineItem.Message(first),
                ChatTimelineItem.Message(failedAssistant)
            ),
            items
        )
        assertFalse(
            hasCompletedAssistantAnswerTail(
                messages = listOf(first, failedAssistant),
                failedAssistantMessageIds = setOf(failedAssistant.id)
            )
        )
    }

    @Test
    fun todayAgriCardPlainTextKeepsDateItemsAndSources() {
        val text = todayAgriCard().toTodayAgriPlainText()

        assertEquals(
            "今日农情 · 6月15日\n\n" +
                "一、病虫监测\n" +
                "多地提醒加强田间巡查。\n" +
                "来源：全国农技中心\n\n" +
                "二、栽培管理\n" +
                "雨后注意排水和控旺。\n\n" +
                "三、产地流通\n" +
                "部分蔬菜产区供应恢复。",
            text
        )
    }

    @Test
    fun todayAgriContextDayOnlyAppliesToThreeUserMessagesAfterCard() {
        val items = listOf(
            ChatTimelineItem.TodayAgriCard(todayAgriCard()),
            ChatTimelineItem.Message(userMessage("u1")),
            ChatTimelineItem.Message(assistantMessage("a1")),
            ChatTimelineItem.Message(userMessage("u2")),
            ChatTimelineItem.Message(assistantMessage("a2")),
            ChatTimelineItem.Message(userMessage("u3")),
            ChatTimelineItem.Message(assistantMessage("a3")),
            ChatTimelineItem.Message(userMessage("u4"))
        )

        assertEquals(
            "20260615",
            resolveTodayAgriContextDayForTimeline(
                chatListItems = items.take(1),
                currentTodayAgriCardDay = "20260615",
                currentDayKey = "20260615",
                remoteConfirmedDay = "20260615"
            )
        )
        assertEquals(
            "20260615",
            resolveTodayAgriContextDayForTimeline(
                chatListItems = items.take(4),
                currentTodayAgriCardDay = "20260615",
                currentDayKey = "20260615",
                remoteConfirmedDay = "20260615"
            )
        )
        assertEquals(
            "20260615",
            resolveTodayAgriContextDayForTimeline(
                chatListItems = items.take(5),
                currentTodayAgriCardDay = "20260615",
                currentDayKey = "20260615",
                remoteConfirmedDay = "20260615"
            )
        )
        assertEquals(
            null,
            resolveTodayAgriContextDayForTimeline(
                chatListItems = items,
                currentTodayAgriCardDay = "20260615",
                currentDayKey = "20260615",
                remoteConfirmedDay = "20260615"
            )
        )
    }

    @Test
    fun todayAgriContextDayKeepsRetryAndRequiresRemoteConfirmation() {
        val items = listOf(
            ChatTimelineItem.TodayAgriCard(todayAgriCard()),
            ChatTimelineItem.Message(userMessage("u1")),
            ChatTimelineItem.Message(assistantMessage("a1")),
            ChatTimelineItem.Message(userMessage("u2"))
        )

        assertEquals(
            "20260615",
            resolveTodayAgriContextDayForTimeline(
                chatListItems = items,
                currentTodayAgriCardDay = "20260615",
                currentDayKey = "20260615",
                remoteConfirmedDay = "20260615",
                existingUserMessageId = "u1"
            )
        )
        assertEquals(
            null,
            resolveTodayAgriContextDayForTimeline(
                chatListItems = items,
                currentTodayAgriCardDay = "20260615",
                currentDayKey = "20260615",
                remoteConfirmedDay = null,
                existingUserMessageId = "u1"
            )
        )
        assertEquals(
            null,
            resolveTodayAgriContextDayForTimeline(
                chatListItems = items,
                currentTodayAgriCardDay = "20260615",
                currentDayKey = "20260616",
                remoteConfirmedDay = "20260615",
                existingUserMessageId = "u1"
            )
        )
    }

    @Test
    fun todayAgriMainCardShowsOncePerDayButStaysVisibleForCurrentRuntime() {
        assertFalse(
            shouldShowTodayAgriMainCard(
                card = todayAgriCard(),
                currentDayKey = "20260615",
                shownDayKey = "",
                shownThisRuntime = false,
                hasAssistantAnswerTail = false,
                suppressedThisRuntime = false
            )
        )

        assertTrue(
            shouldShowTodayAgriMainCard(
                card = todayAgriCard(),
                currentDayKey = "20260615",
                shownDayKey = "",
                shownThisRuntime = false,
                hasAssistantAnswerTail = true,
                suppressedThisRuntime = false
            )
        )

        assertFalse(
            shouldShowTodayAgriMainCard(
                card = todayAgriCard(),
                currentDayKey = "20260615",
                shownDayKey = "",
                shownThisRuntime = false,
                hasAssistantAnswerTail = true,
                suppressedThisRuntime = true
            )
        )

        assertFalse(
            shouldShowTodayAgriMainCard(
                card = todayAgriCard(),
                currentDayKey = "20260615",
                shownDayKey = "20260615",
                shownThisRuntime = false,
                hasAssistantAnswerTail = true,
                suppressedThisRuntime = false
            )
        )

        assertTrue(
            shouldShowTodayAgriMainCard(
                card = todayAgriCard(),
                currentDayKey = "20260615",
                shownDayKey = "20260615",
                shownThisRuntime = true,
                hasAssistantAnswerTail = false,
                suppressedThisRuntime = true
            )
        )

        assertFalse(
            shouldShowTodayAgriMainCard(
                card = todayAgriCard(),
                currentDayKey = "20260616",
                shownDayKey = "",
                shownThisRuntime = false,
                hasAssistantAnswerTail = true,
                suppressedThisRuntime = false
            )
        )
    }

    @Test
    fun startupRevealWaitsOnlyWhileRemoteHistoryHasNoVisualContent() {
        assertFalse(
            shouldRevealChatMessageList(
                startupHydrationBarrierSatisfied = true,
                historyHydrationComplete = false,
                shouldHydrateRemoteHistory = true,
                hasStartedConversation = false,
                isStreaming = false,
                hasStreamingItem = false,
                hasTodayAgriCard = false,
                messageCount = 0
            )
        )

        assertTrue(
            shouldRevealChatMessageList(
                startupHydrationBarrierSatisfied = true,
                historyHydrationComplete = false,
                shouldHydrateRemoteHistory = true,
                hasStartedConversation = false,
                isStreaming = false,
                hasStreamingItem = false,
                hasTodayAgriCard = false,
                messageCount = 2
            )
        )

        assertFalse(
            shouldRevealChatMessageList(
                startupHydrationBarrierSatisfied = false,
                historyHydrationComplete = false,
                shouldHydrateRemoteHistory = true,
                hasStartedConversation = false,
                isStreaming = false,
                hasStreamingItem = false,
                hasTodayAgriCard = true,
                messageCount = 0
            )
        )
    }

    @Test
    fun welcomePlaceholderShowsWhileRemoteHistoryHydrates() {
        assertTrue(
            shouldShowChatWelcomePlaceholder(
                startupHydrationBarrierSatisfied = false,
                hasStartedConversation = false,
                hasStreamingItem = false,
                hasTodayAgriCard = false,
                messageCount = 0
            )
        )

        assertFalse(
            shouldShowChatWelcomePlaceholder(
                startupHydrationBarrierSatisfied = false,
                hasStartedConversation = false,
                hasStreamingItem = false,
                hasTodayAgriCard = false,
                messageCount = 1
            )
        )

        assertTrue(
            shouldShowChatWelcomePlaceholder(
                startupHydrationBarrierSatisfied = false,
                hasStartedConversation = false,
                hasStreamingItem = false,
                hasTodayAgriCard = true,
                messageCount = 0
            )
        )

        assertEquals(
            emptyList<ChatTimelineItem>(),
            buildChatTimelineItems(
                messages = emptyList(),
                todayAgriCard = todayAgriCard(),
                todayAgriCardAnchorMessageId = null
            )
        )
    }

    @Test
    fun emptyRemoteSnapshotReplacesCompletedLocalHistory() {
        assertTrue(
            shouldReplaceHydratedMessages(
                currentMessages = listOf(userMessage("m1"), assistantMessage("m2")),
                remoteMessages = emptyList()
            )
        )

        assertFalse(
            shouldReplaceHydratedMessages(
                currentMessages = emptyList(),
                remoteMessages = emptyList()
            )
        )
    }

    private fun userMessage(id: String): ChatMessage =
        ChatMessage(id = id, role = ChatRole.USER, content = "用户问题")

    private fun assistantMessage(id: String): ChatMessage =
        ChatMessage(id = id, role = ChatRole.ASSISTANT, content = "参考建议")

    private fun todayAgriCard(): SessionApi.TodayAgriCard =
        SessionApi.TodayAgriCard(
            dateCn = "2026-06-15",
            title = "今日农情",
            items = listOf(
                SessionApi.TodayAgriCardItem(title = "病虫监测", summary = "多地提醒加强田间巡查。", source = "全国农技中心"),
                SessionApi.TodayAgriCardItem(title = "栽培管理", summary = "雨后注意排水和控旺。"),
                SessionApi.TodayAgriCardItem(title = "产地流通", summary = "部分蔬菜产区供应恢复。")
            )
        )
}
