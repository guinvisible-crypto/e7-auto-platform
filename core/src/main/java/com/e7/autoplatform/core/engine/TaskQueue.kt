package com.e7.autoplatform.core.engine

data class TaskQueue(
    val tasks: List<QueuedTask>,
    val loop: Boolean,
    val intervalMs: Long
) {
    init {
        require(intervalMs >= 0) { "intervalMs must be >= 0" }
    }

    val size: Int get() = tasks.size
    fun isEmpty(): Boolean = tasks.isEmpty()
}

fun interface QueuedTask {
    suspend fun run(): TaskRunResult
}

interface WatchdogFallbackHandler {
    suspend fun onExecutionTimeout(): Boolean
}

interface TaskRuntimeSnapshotProvider {
    suspend fun currentStateName(): String
    suspend fun currentTaskExceptionCount(): Int
}

enum class TaskRunResult {
    Success,
    Retry,
    Interrupted
}
