package com.nongjiqianwen

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ABLayerManagerInstrumentationTest {

    private val context by lazy { ApplicationProvider.getApplicationContext<android.content.Context>() }

    @Before
    fun setUp() {
        IdManager.init(context)
        ABLayerManager.init(context)
    }

    @After
    fun tearDown() {
        ABLayerManager.clearTestHooks()
    }

    @Test
    fun freePlus_6_7_retryUntilSuccess() {
        val sessionId = "test_ab_free_${System.currentTimeMillis()}"
        val userId = "u_free"
        var extractCalls = 0
        ABLayerManager.setTestHooks(
            sessionId = sessionId,
            userId = userId,
            backendMode = false,
            extractExecutor = { _, _, _ ->
                extractCalls += 1
                if (extractCalls == 1) Result.failure(IllegalStateException("forced-fail"))
                else Result.success("b-summary-success")
            },
        )
        ABLayerManager.resetForTest(sessionId)

        repeat(6) { round ->
            ABLayerManager.onRoundComplete("u${round + 1}", "a${round + 1}", "plus")
        }

        assertEquals(6, ABLayerManager.debugGetRoundTotal(sessionId))
        assertTrue(ABLayerManager.debugGetPendingRetry(sessionId))
        assertEquals(1, extractCalls)

        ABLayerManager.onRoundComplete("u7", "a7", "plus")

        assertEquals(7, ABLayerManager.debugGetRoundTotal(sessionId))
        assertFalse(ABLayerManager.debugGetPendingRetry(sessionId))
        assertEquals(2, extractCalls)
    }

    @Test
    fun pro_9_10_retryUntilSuccess() {
        val sessionId = "test_ab_pro_${System.currentTimeMillis()}"
        val userId = "u_pro"
        var extractCalls = 0
        ABLayerManager.setTestHooks(
            sessionId = sessionId,
            userId = userId,
            backendMode = false,
            extractExecutor = { _, _, _ ->
                extractCalls += 1
                if (extractCalls == 1) Result.failure(IllegalStateException("forced-fail"))
                else Result.success("b-summary-success")
            },
        )
        ABLayerManager.resetForTest(sessionId)

        repeat(9) { round ->
            ABLayerManager.onRoundComplete("u${round + 1}", "a${round + 1}", "pro")
        }

        assertEquals(9, ABLayerManager.debugGetRoundTotal(sessionId))
        assertTrue(ABLayerManager.debugGetPendingRetry(sessionId))
        assertEquals(1, extractCalls)

        ABLayerManager.onRoundComplete("u10", "a10", "pro")

        assertEquals(10, ABLayerManager.debugGetRoundTotal(sessionId))
        assertFalse(ABLayerManager.debugGetPendingRetry(sessionId))
        assertEquals(2, extractCalls)
    }

    @Test
    fun roundTotalAndPendingRetry_persistAcrossRestartSameSession() {
        val sessionId = "test_ab_persist_${System.currentTimeMillis()}"
        val userId = "u_persist"
        ABLayerManager.setTestHooks(
            sessionId = sessionId,
            userId = userId,
            backendMode = false,
            extractExecutor = { _, _, _ -> Result.failure(IllegalStateException("forced-fail")) },
        )
        ABLayerManager.resetForTest(sessionId)

        repeat(6) { round ->
            ABLayerManager.onRoundComplete("u${round + 1}", "a${round + 1}", "free")
        }

        assertEquals(6, ABLayerManager.debugGetRoundTotal(sessionId))
        assertTrue(ABLayerManager.debugGetPendingRetry(sessionId))

        ABLayerManager.dropInMemoryStateForTest(sessionId)
        ABLayerManager.init(context)

        assertEquals(6, ABLayerManager.debugGetRoundTotal(sessionId))
        assertTrue(ABLayerManager.debugGetPendingRetry(sessionId))
    }

    @Test
    fun backendMode_neverCallsLocalExtractor() {
        val sessionId = "test_ab_backend_${System.currentTimeMillis()}"
        val userId = "u_backend"
        var localExtractCalls = 0
        ABLayerManager.setTestHooks(
            sessionId = sessionId,
            userId = userId,
            backendMode = true,
            extractExecutor = { _, _, _ ->
                localExtractCalls += 1
                Result.success("should_not_be_called")
            },
            backendSnapshotProvider = { _, _ -> null },
        )
        ABLayerManager.resetForTest(sessionId)

        repeat(6) { round ->
            ABLayerManager.onRoundComplete("u${round + 1}", "a${round + 1}", "plus")
        }

        assertEquals(6, ABLayerManager.debugGetRoundTotal(sessionId))
        assertEquals(0, localExtractCalls)
        assertTrue(ABLayerManager.debugGetPendingRetry(sessionId))
    }
}

