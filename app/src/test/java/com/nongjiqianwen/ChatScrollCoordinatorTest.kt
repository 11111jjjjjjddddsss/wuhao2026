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
}
