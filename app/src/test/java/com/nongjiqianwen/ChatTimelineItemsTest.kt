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
    fun todayAgriCardStableKeyNormalizesServerDayFormats() {
        val dashed = ChatTimelineItem.TodayAgriCard(todayAgriCard().copy(dateCn = "2026-06-15"))
        val compact = ChatTimelineItem.TodayAgriCard(todayAgriCard().copy(dateCn = "20260615"))

        assertEquals("today-agri-card-20260615", dashed.stableKey)
        assertEquals(dashed.stableKey, compact.stableKey)
    }

    @Test
    fun todayAgriMainItemRestoresOnlyCurrentDaySingleItem() {
        val current = TodayAgriMainItem(
            day_cn = "20260615",
            anchor_client_msg_id = "assistant_1",
            card = todayAgriCard().copy(dateCn = "2026-06-15")
        )
        val previousDay = current.copy(day_cn = "20260614", card = todayAgriCard().copy(dateCn = "20260614"))
        val mismatchedCardDay = current.copy(card = todayAgriCard().copy(dateCn = "20260616"))
        val missingAnchor = current.copy(anchor_client_msg_id = "")
        val missingCardDay = current.copy(card = todayAgriCard().copy(dateCn = ""))

        assertEquals(current, validTodayAgriMainItemForCurrentDay(current, "20260615"))
        assertEquals(null, validTodayAgriMainItemForCurrentDay(previousDay, "20260615"))
        assertEquals(null, validTodayAgriMainItemForCurrentDay(mismatchedCardDay, "20260615"))
        assertEquals(null, validTodayAgriMainItemForCurrentDay(missingAnchor, "20260615"))
        assertEquals(null, validTodayAgriMainItemForCurrentDay(missingCardDay, "20260615"))
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
    fun todayAgriCardVisibilityRequiresViewportIndex() {
        val user = userMessage("u1")
        val assistant = assistantMessage("assistant_u1")
        val items = buildChatTimelineItems(
            messages = listOf(user, assistant),
            todayAgriCard = todayAgriCard(),
            todayAgriAfterMessageId = assistant.id
        )
        val todayAgriIndex = items.indexOfFirst { item ->
            item is ChatTimelineItem.TodayAgriCard
        }

        assertTrue(todayAgriIndex >= 0)
        assertFalse(
            isTodayAgriCardVisibleInViewport(
                chatListItems = items,
                visibleItems = listOf(
                    VisibleChatListItem(index = 0, offset = 0, size = 100),
                    VisibleChatListItem(index = 1, offset = 100, size = 100)
                ),
                viewportStartOffset = 0,
                viewportEndOffset = 400,
                minVisiblePx = 24
            )
        )
        assertTrue(
            isTodayAgriCardVisibleInViewport(
                chatListItems = items,
                visibleItems = listOf(
                    VisibleChatListItem(index = todayAgriIndex, offset = 120, size = 100)
                ),
                viewportStartOffset = 0,
                viewportEndOffset = 400,
                minVisiblePx = 24
            )
        )
        assertFalse(
            isTodayAgriCardVisibleInViewport(
                chatListItems = items,
                visibleItems = listOf(
                    VisibleChatListItem(index = todayAgriIndex, offset = 390, size = 100)
                ),
                viewportStartOffset = 0,
                viewportEndOffset = 400,
                minVisiblePx = 24
            )
        )
    }

    @Test
    fun todayAgriCardVisibilityIgnoresComposerCoveredBottom() {
        val user = userMessage("u1")
        val assistant = assistantMessage("assistant_u1")
        val items = buildChatTimelineItems(
            messages = listOf(user, assistant),
            todayAgriCard = todayAgriCard(),
            todayAgriAfterMessageId = assistant.id
        )
        val todayAgriIndex = items.indexOfFirst { item ->
            item is ChatTimelineItem.TodayAgriCard
        }

        assertFalse(
            isTodayAgriCardVisibleInViewport(
                chatListItems = items,
                visibleItems = listOf(
                    VisibleChatListItem(index = todayAgriIndex, offset = 330, size = 100)
                ),
                viewportStartOffset = 0,
                viewportEndOffset = 400,
                minVisiblePx = 24,
                coveredBottomPx = 80
            )
        )
        assertTrue(
            isTodayAgriCardVisibleInViewport(
                chatListItems = items,
                visibleItems = listOf(
                    VisibleChatListItem(index = todayAgriIndex, offset = 250, size = 100)
                ),
                viewportStartOffset = 0,
                viewportEndOffset = 400,
                minVisiblePx = 24,
                coveredBottomPx = 80
            )
        )
    }

    @Test
    fun todayAgriCardProductionVisibilityRequiresEnoughExposedHeight() {
        val user = userMessage("u1")
        val assistant = assistantMessage("assistant_u1")
        val items = buildChatTimelineItems(
            messages = listOf(user, assistant),
            todayAgriCard = todayAgriCard(),
            todayAgriAfterMessageId = assistant.id
        )
        val todayAgriIndex = items.indexOfFirst { item ->
            item is ChatTimelineItem.TodayAgriCard
        }
        val productionMinVisiblePx = 96

        assertFalse(
            isTodayAgriCardVisibleInViewport(
                chatListItems = items,
                visibleItems = listOf(
                    VisibleChatListItem(index = todayAgriIndex, offset = 405, size = 200)
                ),
                viewportStartOffset = 0,
                viewportEndOffset = 500,
                minVisiblePx = productionMinVisiblePx
            )
        )
        assertFalse(
            isTodayAgriCardVisibleInViewport(
                chatListItems = items,
                visibleItems = listOf(
                    VisibleChatListItem(index = todayAgriIndex, offset = 330, size = 200)
                ),
                viewportStartOffset = 0,
                viewportEndOffset = 500,
                minVisiblePx = productionMinVisiblePx,
                coveredBottomPx = 100
            )
        )
        assertTrue(
            isTodayAgriCardVisibleInViewport(
                chatListItems = items,
                visibleItems = listOf(
                    VisibleChatListItem(index = todayAgriIndex, offset = 304, size = 200)
                ),
                viewportStartOffset = 0,
                viewportEndOffset = 500,
                minVisiblePx = productionMinVisiblePx,
                coveredBottomPx = 100
            )
        )
    }

    @Test
    fun todayAgriVisualAnchorKeepsLocalAnchorUntilServerSaveReturns() {
        assertEquals(
            "assistant_u1",
            resolveTodayAgriVisualAnchorMessageId(
                savedAnchorMessageId = null,
                localAnchorMessageId = "assistant_u1",
                latestCompletedAssistantTailId = "assistant_u2",
                existingMessageIds = setOf("u1", "assistant_u1", "u2", "assistant_u2")
            )
        )
    }

    @Test
    fun todayAgriVisualAnchorFallsBackWhenLocalAnchorMissing() {
        assertEquals(
            "assistant_u2",
            resolveTodayAgriVisualAnchorMessageId(
                savedAnchorMessageId = null,
                localAnchorMessageId = "assistant_trimmed",
                latestCompletedAssistantTailId = "assistant_u2",
                existingMessageIds = setOf("u1", "assistant_u1", "u2", "assistant_u2")
            )
        )
    }

    @Test
    fun todayAgriVisualAnchorPrefersSavedServerAnchor() {
        assertEquals(
            "u1",
            resolveTodayAgriVisualAnchorMessageId(
                savedAnchorMessageId = "u1",
                localAnchorMessageId = "assistant_u2",
                latestCompletedAssistantTailId = "assistant_u3",
                existingMessageIds = setOf("u2", "assistant_u2", "u3", "assistant_u3")
            )
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
    fun todayAgriCardWithMissingAnchorStaysNearFirstVisibleAssistantWhenNoHistoryNotice() {
        val firstUser = userMessage("u1")
        val firstAssistant = assistantMessage("a1")
        val secondUser = userMessage("u2")
        val secondAssistant = assistantMessage("a2")

        val items = buildChatTimelineItems(
            messages = listOf(firstUser, firstAssistant, secondUser, secondAssistant),
            todayAgriCard = todayAgriCard(),
            todayAgriAfterMessageId = "trimmed-old-message",
            hiddenRoundCount = 0
        )

        assertEquals(
            listOf(
                ChatTimelineItem.Message(firstUser),
                ChatTimelineItem.Message(firstAssistant),
                ChatTimelineItem.TodayAgriCard(todayAgriCard()),
                ChatTimelineItem.Message(secondUser),
                ChatTimelineItem.Message(secondAssistant)
            ),
            items
        )

        val thirdUser = userMessage("u3")
        val thirdAssistant = assistantMessage("a3")
        val updatedItems = buildChatTimelineItems(
            messages = listOf(firstUser, firstAssistant, secondUser, secondAssistant, thirdUser, thirdAssistant),
            todayAgriCard = todayAgriCard(),
            todayAgriAfterMessageId = "trimmed-old-message",
            hiddenRoundCount = 0
        )

        assertEquals(2, updatedItems.indexOf(ChatTimelineItem.TodayAgriCard(todayAgriCard())))
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
    fun todayAgriContextDayOnlyAppliesToTwoUserMessagesAfterCard() {
        val items = listOf(
            ChatTimelineItem.TodayAgriCard(todayAgriCard()),
            ChatTimelineItem.Message(userMessage("u1")),
            ChatTimelineItem.Message(assistantMessage("a1")),
            ChatTimelineItem.Message(userMessage("u2")),
            ChatTimelineItem.Message(assistantMessage("a2")),
            ChatTimelineItem.Message(userMessage("u3"))
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
                chatListItems = items.take(3),
                currentTodayAgriCardDay = "20260615",
                currentDayKey = "20260615",
                remoteConfirmedDay = "20260615"
            )
        )
        assertEquals(
            null,
            resolveTodayAgriContextDayForTimeline(
                chatListItems = items.take(4),
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
    fun todayAgriContextRequiresCurrentRuntimeVisibilityBeforeNewSend() {
        assertFalse(
            canAttachTodayAgriContextForCurrentRuntime(
                hasTodayAgriCard = true,
                shouldRenderTodayAgriCardInTimeline = true,
                shownThisRuntime = false
            )
        )
        assertTrue(
            canAttachTodayAgriContextForCurrentRuntime(
                hasTodayAgriCard = true,
                shouldRenderTodayAgriCardInTimeline = true,
                shownThisRuntime = true
            )
        )
        assertFalse(
            canAttachTodayAgriContextForCurrentRuntime(
                hasTodayAgriCard = false,
                shouldRenderTodayAgriCardInTimeline = true,
                shownThisRuntime = true
            )
        )
    }

    @Test
    fun todayAgriContextDoesNotTrustPersistedShownDayWithoutRuntimeVisibility() {
        val persistedShownDayMatchesToday = true

        assertTrue(persistedShownDayMatchesToday)
        assertFalse(
            canAttachTodayAgriContextForCurrentRuntime(
                hasTodayAgriCard = true,
                shouldRenderTodayAgriCardInTimeline = true,
                shownThisRuntime = false
            )
        )
    }

    @Test
    fun todayAgriContextDayIgnoresFailedLocalUserMessages() {
        val items = listOf(
            ChatTimelineItem.TodayAgriCard(todayAgriCard()),
            ChatTimelineItem.Message(userMessage("u1")),
            ChatTimelineItem.Message(assistantMessage("a1")),
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
    fun hydratedVisualMutationCanApplyOnlyWhenIdleAndNotBrowsing() {
        assertTrue(
            canApplyHydratedVisualMutation(
                hasStartedConversation = false,
                isStreaming = false,
                hasStreamingItem = false,
                userBlocksHydratedVisualMutation = false
            )
        )
        assertFalse(
            canApplyHydratedVisualMutation(
                hasStartedConversation = false,
                isStreaming = false,
                hasStreamingItem = false,
                userBlocksHydratedVisualMutation = true
            )
        )
        assertFalse(
            canApplyHydratedVisualMutation(
                hasStartedConversation = true,
                isStreaming = false,
                hasStreamingItem = false,
                userBlocksHydratedVisualMutation = false
            )
        )
        assertFalse(
            canApplyHydratedVisualMutation(
                hasStartedConversation = false,
                isStreaming = true,
                hasStreamingItem = false,
                userBlocksHydratedVisualMutation = false
            )
        )
    }

    @Test
    fun hydratedVisualMutationHoldsOnlyForBrowsingBeforeConversationStarts() {
        assertTrue(
            shouldHoldHydratedVisualMutationForBrowsing(
                hasStartedConversation = false,
                isStreaming = false,
                hasStreamingItem = false,
                userBlocksHydratedVisualMutation = true
            )
        )
        assertFalse(
            shouldHoldHydratedVisualMutationForBrowsing(
                hasStartedConversation = true,
                isStreaming = false,
                hasStreamingItem = false,
                userBlocksHydratedVisualMutation = true
            )
        )
        assertFalse(
            shouldHoldHydratedVisualMutationForBrowsing(
                hasStartedConversation = false,
                isStreaming = false,
                hasStreamingItem = true,
                userBlocksHydratedVisualMutation = true
            )
        )
        assertFalse(
            shouldHoldHydratedVisualMutationForBrowsing(
                hasStartedConversation = false,
                isStreaming = false,
                hasStreamingItem = false,
                userBlocksHydratedVisualMutation = false
            )
        )
    }

    @Test
    fun archiveUnavailableFallbackKeepsOrdinaryLocalHistory() {
        val user = userMessage("u1", "辣椒叶片发黄")
        val assistant = assistantMessage("a1", "先看排水和根系。")

        val retained = retainLocalSnapshotForArchiveUnavailable(
            localSnapshot = LocalChatWindowSnapshot(messages = listOf(user, assistant))
        )

        assertEquals(listOf(user, assistant), retained.messages)
        assertEquals(emptyMap<String, String>(), retained.failedUserMessageStates)
    }

    @Test
    fun archiveUnavailableFallbackUsesRemoteAWindowWhenLocalHistoryIsEmpty() {
        val remoteUser = userMessage("u_remote", "黄瓜叶背有虫")
        val remoteAssistant = assistantMessage("a_remote", "先看蚜虫和白粉虱。")

        val retained = retainLocalSnapshotForArchiveUnavailable(
            localSnapshot = LocalChatWindowSnapshot(),
            remoteMessages = listOf(remoteUser, remoteAssistant)
        )

        assertEquals(listOf(remoteUser, remoteAssistant), retained.messages)
    }

    @Test
    fun hydratedSnapshotAfterLocalUserSendKeepsNewLocalTail() {
        val localInitialUser = userMessage("local_u1", "本地旧问题")
        val remoteUser = userMessage("remote_u1", "远端旧问题")
        val remoteAssistant = assistantMessage("remote_a1", "远端旧回复")
        val newUser = userMessage("new_u2", "冷启动后抢先发送")
        val newAssistant = assistantMessage("assistant_new_u2", "新回复已完成")

        val merged = mergeHydratedSnapshotAfterLocalUserSend(
            hydratedSnapshot = LocalChatWindowSnapshot(messages = listOf(remoteUser, remoteAssistant)),
            currentSnapshot = LocalChatWindowSnapshot(messages = listOf(localInitialUser, newUser, newAssistant)),
            localMessageIdsAtHydrateStart = setOf(localInitialUser.id)
        )

        assertEquals(listOf(remoteUser, remoteAssistant, newUser, newAssistant), merged.messages)
    }

    @Test
    fun hydratedSnapshotAfterLocalUserSendKeepsNewFailureState() {
        val localInitialUser = userMessage("local_u1", "本地旧问题")
        val remoteUser = userMessage("remote_u1", "远端旧问题")
        val remoteAssistant = assistantMessage("remote_a1", "远端旧回复")
        val newUser = userMessage("new_u2", "冷启动后抢先发送")
        val newAssistant = assistantMessage("assistant_new_u2", "")
        val failedState = FailedAssistantMessageState(sourceUserMessageId = newUser.id, reason = "network")

        val merged = mergeHydratedSnapshotAfterLocalUserSend(
            hydratedSnapshot = LocalChatWindowSnapshot(messages = listOf(remoteUser, remoteAssistant)),
            currentSnapshot = LocalChatWindowSnapshot(
                messages = listOf(localInitialUser, newUser, newAssistant),
                failedAssistantMessageStates = mapOf(newAssistant.id to failedState)
            ),
            localMessageIdsAtHydrateStart = setOf(localInitialUser.id)
        )

        assertEquals(listOf(remoteUser, remoteAssistant, newUser, newAssistant), merged.messages)
        assertEquals(failedState, merged.failedAssistantMessageStates[newAssistant.id])
    }

    @Test
    fun pendingTodayAgriMainItemCanApplyAfterUserSendWhenIdle() {
        assertTrue(
            shouldApplyPendingTodayAgriMainItem(
                pendingDayCn = "20260620",
                currentDayKey = "20260620",
                isStreaming = false,
                hasStreamingItem = false,
                userBlocksHydratedVisualMutation = false
            )
        )
        assertFalse(
            shouldApplyPendingTodayAgriMainItem(
                pendingDayCn = "20260620",
                currentDayKey = "20260620",
                isStreaming = true,
                hasStreamingItem = false,
                userBlocksHydratedVisualMutation = false
            )
        )
        assertFalse(
            shouldApplyPendingTodayAgriMainItem(
                pendingDayCn = "20260619",
                currentDayKey = "20260620",
                isStreaming = false,
                hasStreamingItem = false,
                userBlocksHydratedVisualMutation = false
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
                hasAssistantAnswerTail = false,
                insertedThisRuntime = true,
                suppressedThisRuntime = false
            )
        )

        assertTrue(
            shouldShowTodayAgriMainCard(
                card = todayAgriCard(),
                currentDayKey = "20260615",
                shownDayKey = "",
                shownThisRuntime = false,
                hasAssistantAnswerTail = false,
                insertedThisRuntime = true,
                suppressedThisRuntime = true
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
    fun todayAgriDayChangeWaitsForNextCompletedAssistantBeforeAutoInsert() {
        assertTrue(
            shouldWaitForNextTodayAgriAssistantAfterDayChange(
                hasStartedConversation = false,
                latestCompletedAssistantTailId = "assistant_old"
            )
        )
        assertTrue(
            shouldWaitForNextTodayAgriAssistantAfterDayChange(
                hasStartedConversation = true,
                latestCompletedAssistantTailId = null
            )
        )
        assertFalse(
            shouldWaitForNextTodayAgriAssistantAfterDayChange(
                hasStartedConversation = false,
                latestCompletedAssistantTailId = null
            )
        )
        assertFalse(
            shouldReleaseTodayAgriAutoInsertAfterDayChange(
                waitingForNextAssistant = true,
                baselineCompletedAssistantTailId = "assistant_old",
                latestCompletedAssistantTailId = "assistant_old"
            )
        )
        assertFalse(
            shouldReleaseTodayAgriAutoInsertAfterDayChange(
                waitingForNextAssistant = true,
                baselineCompletedAssistantTailId = "assistant_old",
                latestCompletedAssistantTailId = null
            )
        )
        assertTrue(
            shouldReleaseTodayAgriAutoInsertAfterDayChange(
                waitingForNextAssistant = true,
                baselineCompletedAssistantTailId = "assistant_old",
                latestCompletedAssistantTailId = "assistant_new"
            )
        )
        assertTrue(
            shouldReleaseTodayAgriAutoInsertAfterDayChange(
                waitingForNextAssistant = true,
                baselineCompletedAssistantTailId = null,
                latestCompletedAssistantTailId = "assistant_new"
            )
        )
        assertFalse(
            shouldReleaseTodayAgriAutoInsertAfterDayChange(
                waitingForNextAssistant = false,
                baselineCompletedAssistantTailId = "assistant_old",
                latestCompletedAssistantTailId = "assistant_new"
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
    fun remoteTodayAgriFetchDoesNotSkipWhenShownDayHasNoSavedItem() {
        assertFalse(
            shouldSkipTodayAgriCardFetch(
                hasRemoteHistorySource = true,
                todayAgriShownThisRuntime = false,
                shownDayKey = "20260619",
                refreshDayKey = "20260619",
                hasRefreshDayItem = false
            )
        )
        assertTrue(
            shouldSkipTodayAgriCardFetch(
                hasRemoteHistorySource = false,
                todayAgriShownThisRuntime = false,
                shownDayKey = "20260619",
                refreshDayKey = "20260619",
                hasRefreshDayItem = false
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
    fun hydratedMessagesReplaceWhenStableIdsChangeEvenIfContentMatches() {
        assertTrue(
            shouldReplaceHydratedMessages(
                currentMessages = listOf(
                    userMessage("local_user", "同一段问题"),
                    assistantMessage("assistant_local_user", "同一段回答")
                ),
                remoteMessages = listOf(
                    userMessage("remote_user", "同一段问题"),
                    assistantMessage("assistant_remote_user", "同一段回答")
                )
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
        val uploadedImageUser = pendingImageUser.copy(
            imageUris = emptyList(),
            imageUrls = listOf("/uploads/u1.jpg")
        )

        assertTrue(
            shouldTrackPendingImageAssistantRecovery(
                message = pendingImageUser,
                pendingExists = true,
                terminalFailureExists = false,
                remoteCompletionExists = false,
                hasSettledAssistant = false
            )
        )
        assertTrue(
            shouldTrackPendingImageAssistantRecovery(
                message = pendingImageUser,
                pendingExists = false,
                terminalFailureExists = true,
                remoteCompletionExists = false,
                hasSettledAssistant = false
            )
        )
        assertTrue(
            shouldTrackPendingImageAssistantRecovery(
                message = uploadedImageUser,
                pendingExists = false,
                terminalFailureExists = false,
                remoteCompletionExists = true,
                hasSettledAssistant = false
            )
        )
        assertTrue(
            shouldTrackPendingImageAssistantRecovery(
                message = uploadedImageUser,
                pendingExists = false,
                terminalFailureExists = true,
                remoteCompletionExists = false,
                hasSettledAssistant = false
            )
        )
        assertFalse(
            shouldTrackPendingImageAssistantRecovery(
                message = pendingImageUser,
                pendingExists = false,
                terminalFailureExists = true,
                remoteCompletionExists = true,
                hasSettledAssistant = true
            )
        )
        assertFalse(
            shouldTrackPendingImageAssistantRecovery(
                message = userMessage("u2"),
                pendingExists = true,
                terminalFailureExists = true,
                remoteCompletionExists = true,
                hasSettledAssistant = false
            )
        )
    }

    @Test
    fun completedBackgroundImageSendDoesNotBecomeLocalFailureWhileAwaitingSnapshot() {
        val pendingImageUser = ChatMessage(
            id = "u1",
            role = ChatRole.USER,
            content = "看下这个病害",
            imageUris = listOf("file:///tmp/local.jpg"),
            imageUrls = emptyList()
        )

        assertFalse(
            shouldMarkLocalImageUploadPendingAsFailed(
                message = pendingImageUser,
                shouldKeepPending = true,
                hasSettledAssistant = false
            )
        )
        assertTrue(
            shouldMarkLocalImageUploadPendingAsFailed(
                message = pendingImageUser,
                shouldKeepPending = false,
                hasSettledAssistant = false
            )
        )
        assertFalse(
            shouldMarkLocalImageUploadPendingAsFailed(
                message = pendingImageUser,
                shouldKeepPending = false,
                hasSettledAssistant = true
            )
        )
    }

    @Test
    fun remoteCompletionAwaitingSnapshotTimesOutOnlyBeforeAssistantSettles() {
        assertTrue(
            shouldTimeoutRemoteCompletionAwaitingSnapshot(
                remoteCompletionExists = true,
                hasSettledAssistant = false
            )
        )
        assertFalse(
            shouldTimeoutRemoteCompletionAwaitingSnapshot(
                remoteCompletionExists = true,
                hasSettledAssistant = true
            )
        )
        assertFalse(
            shouldTimeoutRemoteCompletionAwaitingSnapshot(
                remoteCompletionExists = false,
                hasSettledAssistant = false
            )
        )
    }

    @Test
    fun stalePendingImageRecoveryFailsOnlyAfterRemoteStartedGrace() {
        val grace = PendingChatSendStore.REMOTE_STARTED_GRACE_MS
        val failedRetryGrace = STALE_PENDING_IMAGE_FAILED_RETRY_GRACE_MS

        assertTrue(
            shouldFailStalePendingImageRecovery(
                remoteStartedAtMs = 1_000L,
                recoverableFailureCount = 0,
                hasSettledAssistant = false,
                nowMs = 1_000L + grace
            )
        )
        assertFalse(
            shouldFailStalePendingImageRecovery(
                remoteStartedAtMs = 1_000L,
                recoverableFailureCount = 0,
                hasSettledAssistant = false,
                nowMs = 1_000L + grace - 1
            )
        )
        assertTrue(
            shouldFailStalePendingImageRecovery(
                remoteStartedAtMs = 1_000L,
                recoverableFailureCount = 1,
                hasSettledAssistant = false,
                nowMs = 1_000L + failedRetryGrace
            )
        )
        assertFalse(
            shouldFailStalePendingImageRecovery(
                remoteStartedAtMs = 1_000L,
                recoverableFailureCount = 1,
                hasSettledAssistant = false,
                nowMs = 1_000L + failedRetryGrace - 1
            )
        )
        assertFalse(
            shouldFailStalePendingImageRecovery(
                remoteStartedAtMs = 0L,
                recoverableFailureCount = 1,
                hasSettledAssistant = false,
                nowMs = 1_000L + grace
            )
        )
        assertFalse(
            shouldFailStalePendingImageRecovery(
                remoteStartedAtMs = 1_000L,
                recoverableFailureCount = 1,
                hasSettledAssistant = true,
                nowMs = 1_000L + grace
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
        assertTrue(
            shouldApplyPendingImageTerminalFailure(
                terminalFailureReason = "retry_exhausted",
                snapshotAvailable = false,
                isLastAttempt = false
            )
        )
        assertTrue(
            shouldApplyPendingImageTerminalFailure(
                terminalFailureReason = "server_failure",
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

    private fun userMessage(id: String, content: String = "用户问题"): ChatMessage =
        ChatMessage(id = id, role = ChatRole.USER, content = content)

    private fun assistantMessage(id: String, content: String = "参考建议"): ChatMessage =
        ChatMessage(id = id, role = ChatRole.ASSISTANT, content = content)

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
