package com.nongjiqianwen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTimelineItemsTest {
    @Test
    fun interruptedStreamingDraftRecoveryDoesNotExposeUnrevealedBuffer() {
        assertEquals(
            "已显示的内容",
            visibleContentForInterruptedStreamingDraft(
                content = "已显示的内容",
                revealBuffer = "还没吐出来的尾巴"
            )
        )
    }

    @Test
    fun interruptedBlankStreamingDraftRestoresRetryAssistantPlaceholder() {
        val user = userMessage("u1")
        val recovered = recoverStreamingDraftAsInterruptedSnapshot(
            localSnapshot = LocalChatWindowSnapshot(messages = listOf(user)),
            draft = LocalStreamingDraft(
                messageId = "a1",
                content = "",
                revealBuffer = "尚未显示的完整尾巴",
                anchoredUserMessageId = user.id,
                savedAtMs = 1700000000000
            )
        )

        assertEquals(
            listOf(user, ChatMessage(id = "a1", role = ChatRole.ASSISTANT, content = "")),
            recovered.messages
        )
        assertEquals(user.id, recovered.failedAssistantMessageStates["a1"]?.sourceUserMessageId)
    }

    @Test
    fun todayAgriCardDoesNotOccupyEmptyWelcomeState() {
        val unanchoredItems = buildChatTimelineItems(
            messages = emptyList(),
            todayAgriCard = todayAgriCard(),
            todayAgriAfterMessageId = null
        )

        assertEquals(emptyList<ChatTimelineItem>(), unanchoredItems)
    }

    @Test
    fun todayAgriCardAfterCompletedAssistantStaysInsideTimeline() {
        val first = userMessage("m1")
        val second = assistantMessage("m2")

        val items = buildChatTimelineItems(
            messages = listOf(first, second),
            todayAgriCard = todayAgriCard(),
            todayAgriAfterMessageId = second.id
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
    fun todayAgriCardAcceptsServerNormalizedUserAnchorAfterAssistantRound() {
        val user = userMessage("u1")
        val assistant = assistantMessage("assistant_u1")

        val items = buildChatTimelineItems(
            messages = listOf(user, assistant),
            todayAgriCard = todayAgriCard(),
            todayAgriAfterMessageId = user.id
        )

        assertEquals(
            listOf(
                ChatTimelineItem.Message(user),
                ChatTimelineItem.Message(assistant),
                ChatTimelineItem.TodayAgriCard(todayAgriCard())
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
            todayAgriAfterMessageId = null
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
            todayAgriAfterMessageId = "trimmed-old-message",
            hiddenRoundCount = 6
        )

        assertTrue(items[0] is ChatTimelineItem.HistoryNotice)
        assertTrue(items[1] is ChatTimelineItem.TodayAgriCard)
        assertEquals(ChatTimelineItem.Message(first), items[2])
        assertEquals(ChatTimelineItem.Message(second), items[3])
    }

    @Test
    fun todayAgriMissingAnchorFallbackDoesNotRestartContextWindow() {
        val items = buildChatTimelineItems(
            messages = listOf(
                userMessage("u1"),
                assistantMessage("a1"),
                userMessage("u2"),
                assistantMessage("a2"),
                userMessage("u3")
            ),
            todayAgriCard = todayAgriCard(),
            todayAgriAfterMessageId = "trimmed-old-message",
            hiddenRoundCount = 10
        )

        assertTrue(items[0] is ChatTimelineItem.HistoryNotice)
        assertTrue(items[1] is ChatTimelineItem.TodayAgriCard)
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
    fun todayAgriCardDoesNotTreatFailedAssistantAsCompletedTail() {
        val first = userMessage("m1")
        val failedAssistant = assistantMessage("m2")

        val items = buildChatTimelineItems(
            messages = listOf(first, failedAssistant),
            todayAgriCard = todayAgriCard(),
            todayAgriAfterMessageId = failedAssistant.id,
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
    fun todayAgriContextDayIgnoresFailedLocalUserMessages() {
        val items = listOf(
            ChatTimelineItem.TodayAgriCard(todayAgriCard()),
            ChatTimelineItem.Message(userMessage("u1")),
            ChatTimelineItem.Message(assistantMessage("a1")),
            ChatTimelineItem.Message(userMessage("u2")),
            ChatTimelineItem.Message(assistantMessage("a2")),
            ChatTimelineItem.Message(userMessage("failed-local"))
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
        assertEquals(
            "20260615",
            resolveTodayAgriContextDayForTimeline(
                chatListItems = items,
                currentTodayAgriCardDay = "20260615",
                currentDayKey = "20260615",
                remoteConfirmedDay = "20260615",
                failedUserMessageIds = setOf("failed-local")
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
                shownThisRuntime = false,
                hasAssistantAnswerTail = true,
                hasSavedItem = true,
                suppressedThisRuntime = false
            )
        )

        assertTrue(
            shouldShowTodayAgriMainCard(
                card = todayAgriCard(),
                currentDayKey = "20260615",
                shownDayKey = "20260615",
                shownThisRuntime = false,
                hasAssistantAnswerTail = true,
                hasSavedItem = true,
                suppressedThisRuntime = true
            )
        )

        assertFalse(
            shouldShowTodayAgriMainCard(
                card = todayAgriCard().copy(dateCn = "20260615"),
                currentDayKey = "20260616",
                shownDayKey = "20260615",
                shownThisRuntime = false,
                hasAssistantAnswerTail = true,
                hasSavedItem = true,
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
    fun historyClearLeavesTodayAgriShownDayEmptyForNextCompletedAssistant() {
        val shownDayAfterClear = todayAgriMainShownDayAfterHistoryClear()

        assertEquals("", shownDayAfterClear)
        assertTrue(
            shouldShowTodayAgriMainCard(
                card = todayAgriCard(),
                currentDayKey = "20260615",
                shownDayKey = shownDayAfterClear,
                shownThisRuntime = false,
                hasAssistantAnswerTail = true,
                suppressedThisRuntime = false
            )
        )
    }

    @Test
    fun todayAgriCardWaitsForRemoteHydrationBeforeTimelineRender() {
        assertFalse(
            shouldRenderTodayAgriMainCardInTimeline(
                shouldShowTodayAgriCard = true,
                shouldHydrateRemoteHistory = true,
                remoteSnapshotHydrationComplete = false,
                shownThisRuntime = false
            )
        )

        assertTrue(
            shouldRenderTodayAgriMainCardInTimeline(
                shouldShowTodayAgriCard = true,
                shouldHydrateRemoteHistory = true,
                remoteSnapshotHydrationComplete = true,
                shownThisRuntime = false
            )
        )

        assertTrue(
            shouldRenderTodayAgriMainCardInTimeline(
                shouldShowTodayAgriCard = true,
                shouldHydrateRemoteHistory = true,
                remoteSnapshotHydrationComplete = false,
                shownThisRuntime = true
            )
        )
    }

    @Test
    fun todayAgriSnapshotUnavailableDoesNotClearExistingMainItem() {
        assertFalse(
            shouldClearTodayAgriMainItemAfterSnapshot(
                restoredItemFound = false,
                todayAgriItemsUnavailable = true
            )
        )
        assertTrue(
            shouldClearTodayAgriMainItemAfterSnapshot(
                restoredItemFound = false,
                todayAgriItemsUnavailable = false
            )
        )
        assertFalse(
            shouldClearTodayAgriMainItemAfterSnapshot(
                restoredItemFound = true,
                todayAgriItemsUnavailable = false
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
                todayAgriAfterMessageId = null
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

    @Test
    fun pendingImageRecoveryTracksPendingOrTerminalFailureButNotSettledAssistant() {
        val pendingImageUser = ChatMessage(
            id = "u1",
            role = ChatRole.USER,
            content = "看下这个病害",
            imageUris = listOf("file:///tmp/local.jpg"),
            imageUrls = emptyList()
        )

        assertTrue(
            shouldTrackPendingImageAssistantRecovery(
                message = pendingImageUser,
                pendingExists = true,
                terminalFailureExists = false,
                hasSettledAssistant = false
            )
        )
        assertTrue(
            shouldTrackPendingImageAssistantRecovery(
                message = pendingImageUser,
                pendingExists = false,
                terminalFailureExists = true,
                hasSettledAssistant = false
            )
        )
        assertFalse(
            shouldTrackPendingImageAssistantRecovery(
                message = pendingImageUser,
                pendingExists = false,
                terminalFailureExists = true,
                hasSettledAssistant = true
            )
        )
        assertFalse(
            shouldTrackPendingImageAssistantRecovery(
                message = userMessage("u2"),
                pendingExists = true,
                terminalFailureExists = true,
                hasSettledAssistant = false
            )
        )
    }

    @Test
    fun terminalImageFailureWaitsForSnapshotUnlessFailureCannotRecoverByWaiting() {
        assertFalse(
            shouldApplyPendingImageTerminalFailure(
                terminalFailureReason = null,
                snapshotAvailable = true,
                isLastAttempt = false
            )
        )
        assertTrue(
            shouldApplyPendingImageTerminalFailure(
                terminalFailureReason = "image_read_failed",
                snapshotAvailable = false,
                isLastAttempt = false
            )
        )
        assertFalse(
            shouldApplyPendingImageTerminalFailure(
                terminalFailureReason = "retry_exhausted",
                snapshotAvailable = false,
                isLastAttempt = false
            )
        )
        assertTrue(
            shouldApplyPendingImageTerminalFailure(
                terminalFailureReason = "retry_exhausted",
                snapshotAvailable = true,
                isLastAttempt = false
            )
        )
        assertTrue(
            shouldApplyPendingImageTerminalFailure(
                terminalFailureReason = "retry_exhausted",
                snapshotAvailable = false,
                isLastAttempt = true
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
