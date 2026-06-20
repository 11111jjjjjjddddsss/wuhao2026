package com.nongjiqianwen

import androidx.compose.runtime.mutableStateOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatScrollCoordinatorTest {
    @Test
    fun streamingInternalScrollDoesNotBecomeUserBrowsing() {
        val scrollMode = mutableStateOf(ScrollMode.AutoFollow)
        val userInteracting = mutableStateOf(false)
        val programmaticScroll = mutableStateOf(false)

        handleChatListScrollStateChanged(
            scrollInProgress = true,
            userDragging = false,
            programmaticScroll = programmaticScroll.value,
            isStreaming = true,
            hasStreamingItem = true,
            scrollModeState = scrollMode,
            userInteractingState = userInteracting,
            endProgrammaticScroll = { programmaticScroll.value = false }
        )

        assertEquals(ScrollMode.AutoFollow, scrollMode.value)
        assertFalse(userInteracting.value)
    }

    @Test
    fun streamingProgrammaticScrollKeepsAutoFollowWhileInProgress() {
        val scrollMode = mutableStateOf(ScrollMode.AutoFollow)
        val userInteracting = mutableStateOf(false)
        val programmaticScroll = mutableStateOf(true)

        handleChatListScrollStateChanged(
            scrollInProgress = true,
            userDragging = false,
            programmaticScroll = programmaticScroll.value,
            isStreaming = true,
            hasStreamingItem = true,
            scrollModeState = scrollMode,
            userInteractingState = userInteracting,
            endProgrammaticScroll = { programmaticScroll.value = false }
        )

        assertEquals(ScrollMode.AutoFollow, scrollMode.value)
        assertFalse(userInteracting.value)
        assertTrue(programmaticScroll.value)
    }

    @Test
    fun streamingUserDragStillEntersUserBrowsing() {
        val scrollMode = mutableStateOf(ScrollMode.AutoFollow)
        val userInteracting = mutableStateOf(false)
        val programmaticScroll = mutableStateOf(false)

        handleChatListScrollStateChanged(
            scrollInProgress = true,
            userDragging = true,
            programmaticScroll = programmaticScroll.value,
            isStreaming = true,
            hasStreamingItem = true,
            scrollModeState = scrollMode,
            userInteractingState = userInteracting,
            endProgrammaticScroll = { programmaticScroll.value = false }
        )

        assertEquals(ScrollMode.UserBrowsing, scrollMode.value)
        assertTrue(userInteracting.value)
    }

    @Test
    fun streamingUserDragCancelsProgrammaticScrollAndEntersUserBrowsing() {
        val scrollMode = mutableStateOf(ScrollMode.AutoFollow)
        val userInteracting = mutableStateOf(false)
        val programmaticScroll = mutableStateOf(true)

        handleChatListScrollStateChanged(
            scrollInProgress = true,
            userDragging = true,
            programmaticScroll = programmaticScroll.value,
            isStreaming = true,
            hasStreamingItem = true,
            scrollModeState = scrollMode,
            userInteractingState = userInteracting,
            endProgrammaticScroll = { programmaticScroll.value = false }
        )

        assertEquals(ScrollMode.UserBrowsing, scrollMode.value)
        assertTrue(userInteracting.value)
        assertFalse(programmaticScroll.value)
    }

    @Test
    fun streamingFlingAfterUserDragStaysUserBrowsing() {
        val scrollMode = mutableStateOf(ScrollMode.UserBrowsing)
        val userInteracting = mutableStateOf(true)
        val programmaticScroll = mutableStateOf(false)

        handleChatListScrollStateChanged(
            scrollInProgress = true,
            userDragging = false,
            programmaticScroll = programmaticScroll.value,
            isStreaming = true,
            hasStreamingItem = true,
            scrollModeState = scrollMode,
            userInteractingState = userInteracting,
            endProgrammaticScroll = { programmaticScroll.value = false }
        )

        assertEquals(ScrollMode.UserBrowsing, scrollMode.value)
        assertTrue(userInteracting.value)
    }

    @Test
    fun streamingAutoFollowRequestsPostLayoutAnchorWhenTailDrifts() {
        assertTrue(
            shouldRequestStreamingAutoFollowAnchorAfterLayout(
                isStreaming = true,
                hasStreamingItem = true,
                streamingMessageContent = "正在生成一段较长的诊断回复",
                sendStartAnchorActive = false,
                scrollMode = ScrollMode.AutoFollow,
                userInteracting = false,
                listScrollInProgress = false,
                isComposerSettling = false,
                contentBottomPx = 900,
                legalBottomPx = 760,
                isNearStreamingWorkline = false
            )
        )
    }

    @Test
    fun streamingAutoFollowPostLayoutAnchorRespectsUserAndStableTail() {
        fun shouldAnchor(
            scrollMode: ScrollMode = ScrollMode.AutoFollow,
            userInteracting: Boolean = false,
            listScrollInProgress: Boolean = false,
            isNearStreamingWorkline: Boolean = false
        ): Boolean =
            shouldRequestStreamingAutoFollowAnchorAfterLayout(
                isStreaming = true,
                hasStreamingItem = true,
                streamingMessageContent = "正在生成一段较长的诊断回复",
                sendStartAnchorActive = false,
                scrollMode = scrollMode,
                userInteracting = userInteracting,
                listScrollInProgress = listScrollInProgress,
                isComposerSettling = false,
                contentBottomPx = 900,
                legalBottomPx = 760,
                isNearStreamingWorkline = isNearStreamingWorkline
            )

        assertFalse(shouldAnchor(scrollMode = ScrollMode.UserBrowsing))
        assertFalse(shouldAnchor(userInteracting = true))
        assertFalse(shouldAnchor(listScrollInProgress = true))
        assertFalse(shouldAnchor(isNearStreamingWorkline = true))
    }
}
