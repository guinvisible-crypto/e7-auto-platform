package com.e7.autoplatform.core.task

data class SchedulerState(
    val scriptStatus: ScriptStatus = ScriptStatus.IDLE,
    val exceptionCount: Int = 0,
    val currentTaskIndex: Int = 0
)
