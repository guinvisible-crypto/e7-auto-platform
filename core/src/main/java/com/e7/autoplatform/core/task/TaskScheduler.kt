package com.e7.autoplatform.core.task

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TaskScheduler(
    private val stateManager: StateManager,
    private val maxRetryCount: Int,
    private val logger: SchedulerLogger = NoOpSchedulerLogger
) {
    // exceptionCount semantics (single-source increment):
    // 1) Increment only when a run actually fails/interrupted (TaskRunResult.Interrupted or uncaught engine error).
    // 2) Recovery pass must not increment; it only evaluates persisted count against maxRetryCount.
    // 3) Retry (TaskRunResult.Retry) must not increment.
    // 4) Successful completion resets state via resetRecoveryState().

    private val schedulerMutex = Mutex()
    private val _state = MutableStateFlow(SchedulerState())
    val state: StateFlow<SchedulerState> = _state.asStateFlow()

    suspend fun initializeFromStorage(): SchedulerState = schedulerMutex.withLock {
        val restored = stateManager.read()
        _state.value = restored
        logger.log("initializeFromStorage status=${restored.scriptStatus} exception=${restored.exceptionCount} index=${restored.currentTaskIndex}")
        restored
    }

    suspend fun markRunning(): SchedulerState = schedulerMutex.withLock {
        mutate("markRunning") { it.copy(scriptStatus = ScriptStatus.RUNNING) }
    }

    suspend fun persistCurrentTaskIndex(index: Int): SchedulerState = schedulerMutex.withLock {
        val safeIndex = index.coerceAtLeast(0)
        mutate("persistCurrentTaskIndex($safeIndex)") { it.copy(currentTaskIndex = safeIndex) }
    }

    suspend fun markInterruptedAndCountException(): SchedulerState = schedulerMutex.withLock {
        mutate("markInterruptedAndCountException") {
            it.copy(scriptStatus = ScriptStatus.INTERRUPTED, exceptionCount = it.exceptionCount + 1)
        }
    }

    suspend fun recoverAfterCrash(): RecoveryDecision = schedulerMutex.withLock {
        val current = stateManager.read()
        val decision = if (current.exceptionCount > maxRetryCount) RecoveryDecision.Stop else RecoveryDecision.Resume
        val nextState = current.copy(
            scriptStatus = if (decision == RecoveryDecision.Resume) ScriptStatus.RECOVERING else ScriptStatus.IDLE,
            exceptionCount = if (decision == RecoveryDecision.Resume) current.exceptionCount else 0
        )
        stateManager.write(nextState)
        _state.value = nextState
        logger.log("recoverAfterCrash decision=$decision exception=${current.exceptionCount} maxRetry=$maxRetryCount index=${current.currentTaskIndex}")
        decision
    }

    suspend fun resetRecoveryState(): SchedulerState = schedulerMutex.withLock {
        mutate("resetRecoveryState") { SchedulerState() }
    }

    private suspend fun mutate(action: String, transform: (SchedulerState) -> SchedulerState): SchedulerState {
        val next = stateManager.update(transform)
        _state.value = next
        logger.log("$action -> status=${next.scriptStatus} exception=${next.exceptionCount} index=${next.currentTaskIndex}")
        return next
    }
}

enum class RecoveryDecision {
    Resume,
    Stop
}
