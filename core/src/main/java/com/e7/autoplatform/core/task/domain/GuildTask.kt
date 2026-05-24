package com.e7.autoplatform.core.task.domain

import com.e7.autoplatform.core.engine.QueuedTask
import com.e7.autoplatform.core.engine.TaskRunResult
import com.e7.autoplatform.core.engine.TaskRuntimeSnapshotProvider
import com.e7.autoplatform.core.engine.WatchdogFallbackHandler

/** Guild task (business flow placeholder). */
class GuildTask(
    private val context: TaskContext
) : QueuedTask, WatchdogFallbackHandler, TaskRuntimeSnapshotProvider {

    private var state: GuildState = GuildState.INIT
    private var taskExceptionCount: Int = 0

    override suspend fun run(): TaskRunResult {
        return runCatching {
            state = GuildState.CONVERGING_HOME
            context.convergeHomeOrThrow()
            state = GuildState.COMPLETED
            TaskRunResult.Success
        }.getOrElse {
            taskExceptionCount += 1
            state = GuildState.INTERRUPTED
            TaskRunResult.Interrupted
        }
    }

    override suspend fun onExecutionTimeout(): Boolean {
        state = GuildState.TIMEOUT_FALLBACK
        taskExceptionCount += 1
        return context.homeResolver.resolveToHome().success
    }

    override suspend fun currentStateName(): String = state.name

    override suspend fun currentTaskExceptionCount(): Int = taskExceptionCount

    private enum class GuildState {
        INIT,
        CONVERGING_HOME,
        COMPLETED,
        TIMEOUT_FALLBACK,
        INTERRUPTED
    }
}
