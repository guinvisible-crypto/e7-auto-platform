package com.e7.autoplatform.core.task

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryStateManager(initial: SchedulerState = SchedulerState()) : StateManager {
    private val mutex = Mutex()
    private var state: SchedulerState = initial

    override suspend fun read(): SchedulerState = mutex.withLock { state }

    override suspend fun write(state: SchedulerState) {
        mutex.withLock { this.state = state }
    }

    override suspend fun update(transform: (SchedulerState) -> SchedulerState): SchedulerState = mutex.withLock {
        state = transform(state)
        state
    }
}
