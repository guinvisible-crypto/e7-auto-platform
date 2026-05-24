package com.e7.autoplatform.core.task

interface StateManager {
    suspend fun read(): SchedulerState
    suspend fun write(state: SchedulerState)
    suspend fun update(transform: (SchedulerState) -> SchedulerState): SchedulerState
}
