package com.e7.autoplatform.core.task

object RuleValidationTraceLogger {
    private fun enabled(): Boolean = System.getProperty("e7.validation.mode") == "true"

    fun logState(task: String, state: String, step: Int) {
        if (!enabled()) return
        println("event=validation_state task=$task step=$step state=$state")
    }

    fun logDecision(
        task: String,
        state: String,
        detectRule: String,
        detectResult: Boolean,
        postconditionRule: String,
        postconditionResult: Boolean,
        actionDecision: String
    ) {
        if (!enabled()) return
        println(
            "event=validation_rule_decision " +
                "task=$task " +
                "state=$state " +
                "detectRule=$detectRule detectResult=$detectResult " +
                "postconditionRule=$postconditionRule postconditionResult=$postconditionResult " +
                "actionDecision=$actionDecision"
        )
    }
}
