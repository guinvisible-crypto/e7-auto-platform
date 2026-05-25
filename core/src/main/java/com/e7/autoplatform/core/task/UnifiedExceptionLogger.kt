package com.e7.autoplatform.core.task

object UnifiedExceptionLogger {
    fun log(
        taskName: String,
        state: String,
        outcome: String,
        schedulerExceptionCount: Int,
        taskExceptionCount: Int,
        currentTaskIndex: Int
    ) {
        println(
            "event=task_status " +
                "task=$taskName " +
                "state=$state " +
                "outcome=$outcome " +
                "schedulerExceptionCount=$schedulerExceptionCount " +
                "taskExceptionCount=$taskExceptionCount " +
                "currentTaskIndex=$currentTaskIndex"
        )
    }
}
