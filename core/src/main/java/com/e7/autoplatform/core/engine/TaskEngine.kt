package com.e7.autoplatform.core.engine

import com.e7.autoplatform.core.task.TaskScheduler
import com.e7.autoplatform.core.task.RecoveryDecision
import com.e7.autoplatform.core.task.ScriptStatus
import com.e7.autoplatform.core.task.UnifiedExceptionLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class TaskEngine(
    private val scheduler: TaskScheduler,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val maxRetryPerTask: Int = DEFAULT_MAX_RETRY_PER_TASK,
    private val maxExecutionTimeMs: Long = DEFAULT_MAX_EXECUTION_TIME_MS
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutex = Mutex()
    private var engineJob: Job? = null

    private val _engineState = MutableStateFlow(EngineState.Idle)
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    fun start(queue: TaskQueue) {
        engineJob?.cancel()
        engineJob = scope.launch {
            runQueue(queue)
        }
    }

    suspend fun pause() = mutex.withLock {
        _engineState.value = EngineState.Paused
    }

    suspend fun resume() = mutex.withLock {
        if (_engineState.value == EngineState.Paused) {
            _engineState.value = EngineState.Running
        }
    }

    suspend fun stop() = mutex.withLock {
        _engineState.value = EngineState.Stopped
        engineJob?.cancel()
        engineJob = null
    }

    private suspend fun runQueue(queue: TaskQueue) {
        if (queue.isEmpty()) {
            _engineState.value = EngineState.Completed
            return
        }

        _engineState.value = EngineState.Running
        val restored = scheduler.initializeFromStorage()
        if (restored.scriptStatus == ScriptStatus.INTERRUPTED) {
            val decision = scheduler.recoverAfterCrash()
            if (decision == RecoveryDecision.Stop) {
                _engineState.value = EngineState.Stopped
                return
            }
        }
        scheduler.markRunning()

        try {
            var running = true
            while (running) {
                val snapshot = scheduler.state.value
                var index = snapshot.currentTaskIndex.coerceIn(0, queue.size - 1)

                while (index < queue.size) {
                    waitIfPausedOrStopped()
                    val task = queue.tasks[index]
                    var retryCount = 0

                    while (true) {
                        waitIfPausedOrStopped()
                        val result = withTimeoutOrNull(maxExecutionTimeMs) { task.run() }
                            ?: handleTaskExecutionTimeout(task)

                        when (result) {
                            TaskRunResult.Success -> {
                                logUnified(task, index, "Success")
                                retryCount = 0
                                index += 1
                                scheduler.persistCurrentTaskIndex(index)
                                break
                            }
                            TaskRunResult.Retry -> {
                                logUnified(task, index, "Retry")
                                retryCount += 1
                                if (retryCount > maxRetryPerTask) {
                                    scheduler.markInterruptedAndCountException()
                                    logUnified(task, index, "Interrupted")
                                    _engineState.value = EngineState.Interrupted
                                    return
                                }
                                scheduler.persistCurrentTaskIndex(index)
                                delay(RETRY_DELAY_MS)
                            }
                            TaskRunResult.Interrupted -> {
                                scheduler.markInterruptedAndCountException()
                                logUnified(task, index, "Interrupted")
                                _engineState.value = EngineState.Interrupted
                                return
                            }
                        }
                    }
                }

                if (queue.loop) {
                    scheduler.persistCurrentTaskIndex(0)
                    if (queue.intervalMs > 0) delay(queue.intervalMs)
                } else {
                    running = false
                }
            }

            scheduler.resetRecoveryState()
            _engineState.value = EngineState.Completed
        } catch (_: CancellationException) {
            _engineState.value = EngineState.Stopped
            throw
        } catch (_: Throwable) {
            scheduler.markInterruptedAndCountException()
            _engineState.value = EngineState.Interrupted
        }
    }

    private suspend fun waitIfPausedOrStopped() {
        while (true) {
            when (_engineState.value) {
                EngineState.Paused -> delay(PAUSE_POLL_MS)
                EngineState.Stopped -> throw CancellationException("Engine stopped")
                else -> return
            }
        }
    }

    companion object {
        private const val PAUSE_POLL_MS = 50L
        private const val RETRY_DELAY_MS = 100L
        private const val DEFAULT_MAX_RETRY_PER_TASK = 50
        private const val DEFAULT_MAX_EXECUTION_TIME_MS = 120_000L
    }

    private suspend fun handleTaskExecutionTimeout(task: QueuedTask): TaskRunResult {
        if (task is WatchdogFallbackHandler) {
            task.onExecutionTimeout()
        }
        return TaskRunResult.Interrupted
    }

    private suspend fun logUnified(task: QueuedTask, currentTaskIndex: Int, outcome: String) {
        val snapshot = scheduler.state.value
        val stateName = if (task is TaskRuntimeSnapshotProvider) task.currentStateName() else "UNKNOWN"
        val taskExceptionCount = if (task is TaskRuntimeSnapshotProvider) task.currentTaskExceptionCount() else 0
        UnifiedExceptionLogger.log(
            taskName = task::class.simpleName ?: "UnknownTask",
            state = stateName,
            outcome = outcome,
            schedulerExceptionCount = snapshot.exceptionCount,
            taskExceptionCount = taskExceptionCount,
            currentTaskIndex = currentTaskIndex
        )
    }
}

enum class EngineState {
    Idle,
    Running,
    Paused,
    Interrupted,
    Completed,
    Stopped
}
