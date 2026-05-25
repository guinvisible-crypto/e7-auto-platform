package com.e7.autoplatform.core.task.domain

import com.e7.autoplatform.core.engine.QueuedTask
import com.e7.autoplatform.core.engine.TaskRunResult
import com.e7.autoplatform.core.engine.TaskRuntimeSnapshotProvider
import com.e7.autoplatform.core.engine.WatchdogFallbackHandler

/** Stage farming task (business flow placeholder). */
class StageTask(
    private val context: TaskContext
) : QueuedTask, WatchdogFallbackHandler, TaskRuntimeSnapshotProvider {

    private var state: StageState = StageState.INIT
    private var taskExceptionCount: Int = 0

    override suspend fun run(): TaskRunResult {
        return runCatching {
            state = StageState.CONVERGING_HOME
            context.convergeHomeOrThrow()
            state = StageState.COMPLETED
            // Reserved for future stage-farming state transitions via image + automation gateways.
            TaskRunResult.Success
        }.getOrElse {
            taskExceptionCount += 1
            state = StageState.INTERRUPTED
            TaskRunResult.Interrupted
        }
    }

    override suspend fun onExecutionTimeout(): Boolean {
        state = StageState.TIMEOUT_FALLBACK
        taskExceptionCount += 1
        return context.homeResolver.resolveToHome().success
    }

    override suspend fun currentStateName(): String = state.name

    override suspend fun currentTaskExceptionCount(): Int = taskExceptionCount

    private enum class StageState {
        INIT,
        CONVERGING_HOME,
        COMPLETED,
        TIMEOUT_FALLBACK,
        INTERRUPTED
    }
}
