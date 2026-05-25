package com.e7.autoplatform.core.task

enum class ScriptStatus(val code: Int) {
    IDLE(0),
    RUNNING(1),
    RECOVERING(2),
    INTERRUPTED(3);

    companion object {
        fun fromCode(code: Int): ScriptStatus {
            return entries.firstOrNull { it.code == code } ?: IDLE
        }
    }
}
