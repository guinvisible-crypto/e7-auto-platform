package com.e7.autoplatform.core.task.domain

import com.e7.autoplatform.core.engine.QueuedTask
import com.e7.autoplatform.core.engine.TaskQueue
import com.e7.autoplatform.core.config.RuntimeStateStore
import com.e7.autoplatform.core.engine.TaskRuntimeSnapshotProvider

/**
 * Builds TaskEngine queues from task-domain units.
 * No task business logic is embedded here.
 */
class TaskDomainQueueFactory {
    fun build(
        context: TaskContext,
        includeStage: Boolean,
        includeArena: Boolean,
        includeGuild: Boolean,
        includeBookmark: Boolean,
        loop: Boolean,
        intervalMs: Long,
        runtimeStateStore: RuntimeStateStore,
        stageRulesJson: String,
        arenaRulesJson: String,
        bookmarkRulesJson: String
    ): TaskQueue {
        val tasks = mutableListOf<QueuedTask>()
        if (includeStage) tasks += StageTask(context, stageRulesJson)
        if (includeArena) tasks += ArenaTask(context, runtimeStateStore, arenaRulesJson)
        if (includeGuild) tasks += GuildTask(context)
        if (includeBookmark) tasks += BookmarkTask(context, runtimeStateStore, bookmarkRulesJson)
        validateRuntimeSnapshotContract(tasks)
        return TaskQueue(tasks = tasks, loop = loop, intervalMs = intervalMs)
    }

    private fun validateRuntimeSnapshotContract(tasks: List<QueuedTask>) {
        tasks.forEach { task ->
            require(task is TaskRuntimeSnapshotProvider) {
                "Task ${task::class.simpleName ?: "UnknownTask"} must implement TaskRuntimeSnapshotProvider for unified logging"
            }
        }
    }
}
