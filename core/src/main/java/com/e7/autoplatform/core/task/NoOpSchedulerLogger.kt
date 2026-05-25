package com.e7.autoplatform.core.task

object NoOpSchedulerLogger : SchedulerLogger {
    override fun log(message: String) = Unit
}
