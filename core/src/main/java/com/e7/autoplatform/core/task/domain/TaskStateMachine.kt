package com.e7.autoplatform.core.task.domain

import com.e7.autoplatform.core.engine.TaskRunResult
import com.e7.autoplatform.core.task.RuleValidationTraceLogger

/**
 * Reusable task-level state machine template.
 *
 * Each state step performs:
 * Detect -> Action -> Transition
 */
abstract class TaskStateMachine<S : Enum<S>>(
    private val maxSteps: Int
) {
    protected data class StepOutcome<S>(
        val nextState: S,
        val result: TaskRunResult? = null
    )

    suspend fun run(initialState: S): TaskRunResult {
        var current = initialState
        repeat(maxSteps) { step ->
            RuleValidationTraceLogger.logState(task = taskName(), state = current.name, step = step)
            val outcome = executeState(current)
            persistState(outcome.nextState)
            outcome.result?.let { return it }
            current = outcome.nextState
        }
        return TaskRunResult.Retry
    }

    protected open fun taskName(): String = this::class.simpleName ?: "UnknownTask"

    protected abstract suspend fun executeState(state: S): StepOutcome<S>
    protected abstract suspend fun persistState(state: S)
}
