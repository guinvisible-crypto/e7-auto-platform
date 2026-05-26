package com.e7.autoplatform.core.engine

import com.e7.autoplatform.core.task.InMemoryStateManager
import com.e7.autoplatform.core.task.NoOpSchedulerLogger
import com.e7.autoplatform.core.task.SchedulerState
import com.e7.autoplatform.core.task.ScriptStatus
import com.e7.autoplatform.core.task.TaskScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class TaskEngineTest {

    @Test
    fun `runs queue and completes`() = runBlocking {
        val scheduler = TaskScheduler(InMemoryStateManager(), maxRetryCount = 3, logger = NoOpSchedulerLogger)
        val counter = AtomicInteger(0)
        val queue = TaskQueue(
            tasks = listOf(
                QueuedTask { counter.incrementAndGet(); TaskRunResult.Success },
                QueuedTask { counter.incrementAndGet(); TaskRunResult.Success }
            ),
            loop = false,
            intervalMs = 0
        )
        val engine = TaskEngine(scheduler)

        engine.start(queue)
        waitFor { engine.engineState.value == EngineState.Completed }

        assertEquals(2, counter.get())
        assertEquals(0, scheduler.state.value.currentTaskIndex)
    }

    @Test
    fun `respects currentTaskIndex for resume`() = runBlocking {
        val scheduler = TaskScheduler(
            InMemoryStateManager(SchedulerState(currentTaskIndex = 1)),
            maxRetryCount = 3,
            logger = NoOpSchedulerLogger
        )
        val first = AtomicInteger(0)
        val second = AtomicInteger(0)
        val queue = TaskQueue(
            tasks = listOf(
                QueuedTask { first.incrementAndGet(); TaskRunResult.Success },
                QueuedTask { second.incrementAndGet(); TaskRunResult.Success }
            ),
            loop = false,
            intervalMs = 0
        )
        val engine = TaskEngine(scheduler)

        engine.start(queue)
        waitFor { engine.engineState.value == EngineState.Completed }

        assertEquals(0, first.get())
        assertEquals(1, second.get())
    }

    @Test
    fun `interrupted task updates scheduler exception`() = runBlocking {
        val scheduler = TaskScheduler(InMemoryStateManager(), maxRetryCount = 3, logger = NoOpSchedulerLogger)
        val queue = TaskQueue(
            tasks = listOf(QueuedTask { TaskRunResult.Interrupted }),
            loop = false,
            intervalMs = 0
        )
        val engine = TaskEngine(scheduler)

        engine.start(queue)
        waitFor { engine.engineState.value == EngineState.Interrupted }

        assertEquals(1, scheduler.state.value.exceptionCount)
    }

    @Test
    fun `does not run queue when recovery retry limit is exceeded`() = runBlocking {
        val scheduler = TaskScheduler(
            InMemoryStateManager(SchedulerState(ScriptStatus.INTERRUPTED, exceptionCount = 3, currentTaskIndex = 0)),
            maxRetryCount = 3,
            logger = NoOpSchedulerLogger
        )
        val counter = AtomicInteger(0)
        val queue = TaskQueue(
            tasks = listOf(QueuedTask { counter.incrementAndGet(); TaskRunResult.Success }),
            loop = false,
            intervalMs = 0
        )
        val engine = TaskEngine(scheduler)

        engine.start(queue)
        waitFor { engine.engineState.value == EngineState.Stopped }

        assertEquals(0, counter.get())
    }

    @Test
    fun `pause and resume continues execution`() = runBlocking {
        val scheduler = TaskScheduler(InMemoryStateManager(), maxRetryCount = 3, logger = NoOpSchedulerLogger)
        val counter = AtomicInteger(0)
        val queue = TaskQueue(
            tasks = listOf(
                QueuedTask { delay(30); counter.incrementAndGet(); TaskRunResult.Success },
                QueuedTask { delay(30); counter.incrementAndGet(); TaskRunResult.Success }
            ),
            loop = false,
            intervalMs = 0
        )
        val engine = TaskEngine(scheduler)

        engine.start(queue)
        delay(10)
        engine.pause()
        assertTrue(engine.engineState.value == EngineState.Paused || engine.engineState.value == EngineState.Running)
        delay(30)
        engine.resume()

        waitFor { engine.engineState.value == EngineState.Completed }
        assertEquals(2, counter.get())
    }

    private suspend fun waitFor(predicate: () -> Boolean, timeoutMs: Long = 1500) {
        val start = System.currentTimeMillis()
        while (!predicate()) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                throw AssertionError("Timeout waiting for condition")
            }
            delay(20)
        }
    }
}
