package com.nongjiqianwen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatComposerPanelTest {
    @Test
    fun emptyInputWithoutImagesCannotBePressedOrSubmitted() {
        val state = buildSendGateState(
            rawInput = "   ",
            isStreaming = false,
            exceedsInputLimit = false,
            hasImages = false
        )

        assertFalse(state.canPress)
        assertFalse(state.canSubmit)
        assertFalse(state.activeAppearance)
        assertEquals(SendBlockReason.EmptyInput, state.blockReason)
    }

    @Test
    fun imageOnlyMessageCanBeSubmitted() {
        val state = buildSendGateState(
            rawInput = "",
            isStreaming = false,
            exceedsInputLimit = false,
            hasImages = true
        )

        assertTrue(state.canPress)
        assertTrue(state.canSubmit)
        assertTrue(state.activeAppearance)
        assertEquals(SendBlockReason.None, state.blockReason)
    }

    @Test
    fun streamingDisablesSendingWithoutLookingActive() {
        val state = buildSendGateState(
            rawInput = "番茄叶片发黄",
            isStreaming = true,
            exceedsInputLimit = false,
            hasImages = false
        )

        assertFalse(state.canPress)
        assertFalse(state.canSubmit)
        assertFalse(state.activeAppearance)
        assertEquals(SendBlockReason.Streaming, state.blockReason)
    }

    @Test
    fun quotaExhaustedCanBePressedForHintButCannotSubmit() {
        val state = buildSendGateState(
            rawInput = "番茄叶片发黄",
            isStreaming = false,
            exceedsInputLimit = false,
            quotaExhausted = true,
            hasImages = false
        )

        assertTrue(state.canPress)
        assertFalse(state.canSubmit)
        assertFalse(state.activeAppearance)
        assertEquals(SendBlockReason.QuotaExhausted, state.blockReason)
    }

    @Test
    fun inputTooLongCanBePressedForHintButCannotSubmit() {
        val state = buildSendGateState(
            rawInput = "番茄叶片发黄",
            isStreaming = false,
            exceedsInputLimit = true,
            hasImages = false
        )

        assertTrue(state.canPress)
        assertFalse(state.canSubmit)
        assertTrue(state.activeAppearance)
        assertEquals(SendBlockReason.InputTooLong, state.blockReason)
    }
}
