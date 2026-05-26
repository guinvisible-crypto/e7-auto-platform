package com.e7.autoplatform.core.home

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeResolverTest {

    @Test
    fun `resolves when home appears after popup sequence`() = runBlocking {
        val detector = QueueDetector(
            HomeUiState.POPUP,
            HomeUiState.RESULT,
            HomeUiState.HOME
        )
        val action = RecordingActionExecutor()
        val resolver = HomeResolver(detector, action, maxSteps = 10, stepDelayMs = 1)

        val result = resolver.resolveToHome()

        assertTrue(result.success)
        assertEquals(3, result.stepsUsed)
        assertTrue(action.tapDismissCount >= 2)
    }

    @Test
    fun `uses back action for repeated unknown states`() = runBlocking {
        val detector = QueueDetector(
            HomeUiState.UNKNOWN,
            HomeUiState.UNKNOWN,
            HomeUiState.HOME
        )
        val action = RecordingActionExecutor()
        val resolver = HomeResolver(detector, action, maxSteps = 10, stepDelayMs = 1, unknownBackThreshold = 2)

        val result = resolver.resolveToHome()

        assertTrue(result.success)
        assertEquals(1, action.backCount)
    }

    @Test
    fun `fails safely after max steps`() = runBlocking {
        val detector = ConstantDetector(HomeUiState.UNKNOWN)
        val action = RecordingActionExecutor()
        val resolver = HomeResolver(detector, action, maxSteps = 5, stepDelayMs = 1)

        val result = resolver.resolveToHome()

        assertTrue(!result.success)
        assertEquals(5, result.stepsUsed)
    }

    private class QueueDetector(vararg states: HomeUiState) : HomeStateDetector {
        private val queue = ArrayDeque(states.toList())
        override suspend fun detect(): HomeDetectionResult {
            val s = if (queue.isEmpty()) HomeUiState.HOME else queue.removeFirst()
            return HomeDetectionResult(s)
        }
    }

    private class ConstantDetector(private val state: HomeUiState) : HomeStateDetector {
        override suspend fun detect(): HomeDetectionResult = HomeDetectionResult(state)
    }

    private class RecordingActionExecutor : HomeActionExecutor {
        var tapDismissCount = 0
        var backCount = 0
        override suspend fun tapDismiss() { tapDismissCount++ }
        override suspend fun pressBack() { backCount++ }
        override suspend fun waitStep(delayMs: Long) = Unit
        override suspend fun ensureForeground() = Unit
    }
}
