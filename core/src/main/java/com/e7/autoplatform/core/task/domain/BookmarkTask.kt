package com.e7.autoplatform.core.task.domain

import com.e7.autoplatform.core.config.RuntimeStateStore
import com.e7.autoplatform.core.engine.QueuedTask
import com.e7.autoplatform.core.engine.TaskRuntimeSnapshotProvider
import com.e7.autoplatform.core.engine.TaskRunResult
import com.e7.autoplatform.core.engine.WatchdogFallbackHandler
import com.e7.autoplatform.core.image.OffsetColor
import com.e7.autoplatform.core.task.RuleValidationTraceLogger
import com.e7.autoplatform.core.image.PatternDefinition
import com.e7.autoplatform.core.image.ScanRegion
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class BookmarkTask(
    private val context: TaskContext,
    private val runtimeStateStore: RuntimeStateStore,
    private val rulesJson: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
    maxSteps: Int = 30
) : QueuedTask, WatchdogFallbackHandler, TaskRuntimeSnapshotProvider, TaskStateMachine<BookmarkTask.BookmarkState>(maxSteps) {

    override suspend fun run(): TaskRunResult {
        return runCatching {
            context.convergeHomeOrThrow()
            rules = parseRules(rulesJson)
            run(loadState())
        }.getOrElse { interruptAndPersist() }
    }

    override suspend fun onExecutionTimeout(): Boolean {
        return context.homeResolver.resolveToHome().success
    }

    private lateinit var rules: List<BookmarkRule>

    override suspend fun executeState(state: BookmarkState): StepOutcome<BookmarkState> {
        return when (state) {
            BookmarkState.DETECT_REFRESH -> detectStep(RULE_REFRESH, BookmarkState.ACTION_REFRESH, BookmarkState.DETECT_BUY)
            BookmarkState.ACTION_REFRESH -> actionStepWithLimit(
                stateName = "ACTION_REFRESH",
                detectRuleId = RULE_REFRESH,
                postconditionRuleId = RULE_REFRESH_APPLIED,
                nextState = BookmarkState.DETECT_BUY,
                counterKey = KEY_REFRESH_COUNT,
                maxCount = MAX_REFRESH_COUNT
            )
            BookmarkState.DETECT_BUY -> detectStep(RULE_BUY, BookmarkState.ACTION_BUY, BookmarkState.DONE)
            BookmarkState.ACTION_BUY -> actionStepWithLimit(
                stateName = "ACTION_BUY",
                detectRuleId = RULE_BUY,
                postconditionRuleId = RULE_BUY_COMPLETED,
                nextState = BookmarkState.DETECT_BUY,
                counterKey = KEY_PURCHASE_COUNT,
                maxCount = MAX_PURCHASE_COUNT
            )
            BookmarkState.DONE -> {
                clearState()
                persistExceptionCount(0)
                persistCounter(KEY_REFRESH_COUNT, 0)
                persistCounter(KEY_PURCHASE_COUNT, 0)
                StepOutcome(BookmarkState.DONE, TaskRunResult.Success)
            }
        }
    }

    private suspend fun detectStep(ruleId: String, trueState: BookmarkState, falseState: BookmarkState): StepOutcome<BookmarkState> {
        val rule = rules.firstOrNull { it.id == ruleId }
            ?: return StepOutcome(loadState(), interruptAndPersist())
        return StepOutcome(if (detect(rule)) trueState else falseState)
    }

    private suspend fun actionStepWithLimit(
        stateName: String,
        detectRuleId: String,
        postconditionRuleId: String,
        nextState: BookmarkState,
        counterKey: String,
        maxCount: Int
    ): StepOutcome<BookmarkState> {
        val currentCount = loadCounter(counterKey)
        if (currentCount >= maxCount) {
            return StepOutcome(BookmarkState.DONE)
        }

        val detectRule = rules.firstOrNull { it.id == detectRuleId }
            ?: return StepOutcome(loadState(), interruptAndPersist())
        val postconditionRule = rules.firstOrNull { it.id == postconditionRuleId }
            ?: return StepOutcome(loadState(), interruptAndPersist())

        val postconditionResult = detect(postconditionRule)
        val detectResult = detect(detectRule)

        if (postconditionResult) {
            RuleValidationTraceLogger.logDecision(
                task = taskName(),
                state = stateName,
                detectRule = detectRule.id,
                detectResult = detectResult,
                postconditionRule = postconditionRule.id,
                postconditionResult = postconditionResult,
                actionDecision = "SKIP"
            )
            println("[BookmarkTask] state=$stateName action=${detectRule.id} decision=SKIP reason=postcondition_already_achieved postconditionRule=${postconditionRule.id}")
            return StepOutcome(nextState)
        }

        if (!detectResult) {
            RuleValidationTraceLogger.logDecision(
                task = taskName(),
                state = stateName,
                detectRule = detectRule.id,
                detectResult = detectResult,
                postconditionRule = postconditionRule.id,
                postconditionResult = postconditionResult,
                actionDecision = "SKIP"
            )
            println("[BookmarkTask] state=$stateName action=${detectRule.id} decision=SKIP reason=detect_rule_not_matched detectRule=${detectRule.id}")
            return StepOutcome(nextState)
        }

        RuleValidationTraceLogger.logDecision(
            task = taskName(),
            state = stateName,
            detectRule = detectRule.id,
            detectResult = detectResult,
            postconditionRule = postconditionRule.id,
            postconditionResult = postconditionResult,
            actionDecision = "EXECUTE"
        )
        println("[BookmarkTask] state=$stateName action=${detectRule.id} decision=EXECUTE detectRule=${detectRule.id} postconditionRule=${postconditionRule.id}")
        doTap(detectRule)
        persistCounter(counterKey, currentCount + 1)
        context.automation.waitMs(ACTION_WAIT_MS)
        return StepOutcome(nextState)
    }

    private suspend fun detect(rule: BookmarkRule): Boolean = when (rule.type) {
        "single_color" -> {
            val anchor = rule.anchor ?: return false
            context.image.findColor(anchor.rgb.parseRgb(), rule.tolerance, rule.region.toScanRegion(), 1).matched
        }

        "multi_point" -> {
            val anchorRgb = rule.anchorRgb ?: return false
            val pattern = PatternDefinition(anchorRgb.parseRgb(), rule.offsets.map { OffsetColor(it.dx, it.dy, it.rgb.parseRgb()) })
            context.image.findPattern(pattern, rule.tolerance, rule.region.toScanRegion(), 1).matched
        }

        else -> false
    }

    private suspend fun doTap(rule: BookmarkRule) {
        val region = rule.region
        val x = rule.anchor?.x ?: (region.left + region.right) / 2
        val y = rule.anchor?.y ?: (region.top + region.bottom) / 2
        context.automation.tap(x, y)
    }

    private suspend fun loadExceptionCount(): Int = runtimeStateStore.getInt(KEY_EXCEPTION_COUNT, 0)
    private suspend fun persistExceptionCount(value: Int) = runtimeStateStore.putInt(KEY_EXCEPTION_COUNT, value.coerceAtLeast(0))

    private suspend fun interruptAndPersist(): TaskRunResult {
        persistExceptionCount(loadExceptionCount() + 1)
        return TaskRunResult.Interrupted
    }

    private suspend fun loadCounter(key: String): Int = runtimeStateStore.getInt(key, 0)
    private suspend fun persistCounter(key: String, value: Int) = runtimeStateStore.putInt(key, value.coerceAtLeast(0))

    private suspend fun loadState(): BookmarkState {
        val raw = runtimeStateStore.getString(KEY_STATE, BookmarkState.DETECT_REFRESH.name)
        return BookmarkState.entries.firstOrNull { it.name == raw } ?: BookmarkState.DETECT_REFRESH
    }

    override suspend fun persistState(state: BookmarkState) {
        runtimeStateStore.putString(KEY_STATE, state.name)
    }

    override suspend fun currentStateName(): String = loadState().name

    override suspend fun currentTaskExceptionCount(): Int = loadExceptionCount()

    private suspend fun clearState() = runtimeStateStore.putString(KEY_STATE, BookmarkState.DETECT_REFRESH.name)

    private fun parseRules(raw: String): List<BookmarkRule> = json.decodeFromString(BookmarkRulePayload.serializer(), raw).rules

    enum class BookmarkState { DETECT_REFRESH, ACTION_REFRESH, DETECT_BUY, ACTION_BUY, DONE }

    @Serializable
    private data class BookmarkRulePayload(val rules: List<BookmarkRule>)

    @Serializable
    private data class BookmarkRule(
        val id: String,
        val type: String,
        val region: RegionJson,
        val tolerance: Int = 0,
        val anchor: AnchorJson? = null,
        val anchorRgb: String? = null,
        val offsets: List<OffsetJson> = emptyList()
    )

    @Serializable private data class RegionJson(val left: Int, val top: Int, val right: Int, val bottom: Int)
    @Serializable private data class AnchorJson(val x: Int, val y: Int, val rgb: String)
    @Serializable private data class OffsetJson(val dx: Int, val dy: Int, val rgb: String)

    private fun RegionJson.toScanRegion(): ScanRegion = ScanRegion(left, top, right, bottom)
    private fun String.parseRgb(): Int = (0xFF shl 24) or removePrefix("#").toInt(16)

    companion object {
        private const val KEY_STATE = "bookmark_task_state"
        private const val RULE_REFRESH = "cmp_shop_refresh_btn"
        private const val RULE_BUY = "mul_shop_buy_btn"
        private const val RULE_REFRESH_APPLIED = "cmp_shop_refresh_applied"
        private const val RULE_BUY_COMPLETED = "cmp_shop_buy_completed"
        private const val KEY_EXCEPTION_COUNT = "bookmark_task_exception_count"
        private const val KEY_PURCHASE_COUNT = "bookmark_task_purchase_count"
        private const val KEY_REFRESH_COUNT = "bookmark_task_refresh_count"
        private const val MAX_PURCHASE_COUNT = 30
        private const val MAX_REFRESH_COUNT = 10
        private const val ACTION_WAIT_MS = 300L
    }
}
