package com.e7.autoplatform.core.task

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskSchedulerTest {

    @Test
    fun `initializeFromStorage restores persisted state`() = runBlocking {
        val store = InMemoryStateManager(SchedulerState(ScriptStatus.INTERRUPTED, 2, 7))
        val scheduler = TaskScheduler(store, maxRetryCount = 5, logger = RecordingLogger())

        val restored = scheduler.initializeFromStorage()

        assertEquals(ScriptStatus.INTERRUPTED, restored.scriptStatus)
        assertEquals(2, restored.exceptionCount)
        assertEquals(7, restored.currentTaskIndex)
    }

    @Test
    fun `recoverAfterCrash resumes within retry limit`() = runBlocking {
        val store = InMemoryStateManager(SchedulerState(ScriptStatus.INTERRUPTED, 1, 3))
        val scheduler = TaskScheduler(store, maxRetryCount = 3, logger = RecordingLogger())

        val decision = scheduler.recoverAfterCrash()
        val state = store.read()

        assertEquals(RecoveryDecision.Resume, decision)
        assertEquals(ScriptStatus.RECOVERING, state.scriptStatus)
        assertEquals(1, state.exceptionCount)
        assertEquals(3, state.currentTaskIndex)
    }

    @Test
    fun `recoverAfterCrash stops when retry limit exceeded`() = runBlocking {
        val store = InMemoryStateManager(SchedulerState(ScriptStatus.INTERRUPTED, 3, 9))
        val scheduler = TaskScheduler(store, maxRetryCount = 3, logger = RecordingLogger())

        val decision = scheduler.recoverAfterCrash()
        val state = store.read()

        assertEquals(RecoveryDecision.Stop, decision)
        assertEquals(ScriptStatus.IDLE, state.scriptStatus)
        assertEquals(0, state.exceptionCount)
        assertEquals(9, state.currentTaskIndex)
    }

    @Test
    fun `concurrent index updates remain thread-safe`() = runBlocking {
        val store = InMemoryStateManager()
        val scheduler = TaskScheduler(store, maxRetryCount = 3, logger = RecordingLogger())

        (0..50).map { i ->
            async { scheduler.persistCurrentTaskIndex(i) }
        }.awaitAll()

        val finalState = store.read()
        assertTrue(finalState.currentTaskIndex in 0..50)
    }

    private class RecordingLogger : SchedulerLogger {
        val lines = mutableListOf<String>()
        override fun log(message: String) {
            lines += message
        }
    }
}
