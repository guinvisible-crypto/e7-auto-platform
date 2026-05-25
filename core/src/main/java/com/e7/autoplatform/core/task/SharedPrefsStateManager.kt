package com.e7.autoplatform.core.task

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SharedPrefsStateManager(context: Context) : StateManager {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREF_NAME,
        Context.MODE_PRIVATE
    )
    private val mutex = Mutex()

    override suspend fun read(): SchedulerState = mutex.withLock {
        SchedulerState(
            scriptStatus = ScriptStatus.fromCode(prefs.getInt(KEY_SCRIPT_STATUS, ScriptStatus.IDLE.code)),
            exceptionCount = prefs.getInt(KEY_EXCEPTION_COUNT, 0),
            currentTaskIndex = prefs.getInt(KEY_CURRENT_TASK_INDEX, 0)
        )
    }

    override suspend fun write(state: SchedulerState) = mutex.withLock {
        persist(state)
    }

    override suspend fun update(transform: (SchedulerState) -> SchedulerState): SchedulerState = mutex.withLock {
        val next = transform(readUnsafe())
        persist(next)
        next
    }

    private fun readUnsafe(): SchedulerState = SchedulerState(
        scriptStatus = ScriptStatus.fromCode(prefs.getInt(KEY_SCRIPT_STATUS, ScriptStatus.IDLE.code)),
        exceptionCount = prefs.getInt(KEY_EXCEPTION_COUNT, 0),
        currentTaskIndex = prefs.getInt(KEY_CURRENT_TASK_INDEX, 0)
    )

    private fun persist(state: SchedulerState) {
        prefs.edit()
            .putInt(KEY_SCRIPT_STATUS, state.scriptStatus.code)
            .putInt(KEY_EXCEPTION_COUNT, state.exceptionCount)
            .putInt(KEY_CURRENT_TASK_INDEX, state.currentTaskIndex)
            .commit()
    }

    companion object {
        private const val PREF_NAME = "task_scheduler_state"
        private const val KEY_SCRIPT_STATUS = "scriptStatus"
        private const val KEY_EXCEPTION_COUNT = "exception_count"
        private const val KEY_CURRENT_TASK_INDEX = "current_task_index"
    }
}
