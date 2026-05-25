package com.e7.autoplatform.core.task.domain

import com.e7.autoplatform.core.config.RuntimeStateStore
import com.e7.autoplatform.core.engine.QueuedTask
import com.e7.autoplatform.core.engine.TaskRunResult
import com.e7.autoplatform.core.engine.TaskRuntimeSnapshotProvider
import com.e7.autoplatform.core.engine.WatchdogFallbackHandler
import com.e7.autoplatform.core.image.OffsetColor
import com.e7.autoplatform.core.image.PatternDefinition
import com.e7.autoplatform.core.image.ScanRegion
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ArenaTask(
    private val context: TaskContext,
    private val runtimeStateStore: RuntimeStateStore,
    private val rulesJson: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
    maxSteps: Int = 30
) : QueuedTask, WatchdogFallbackHandler, TaskRuntimeSnapshotProvider, TaskStateMachine<ArenaTask.ArenaState>(maxSteps) {

    override suspend fun onExecutionTimeout(): Boolean {
        repeat(FALLBACK_BACK_STEPS) {
            context.automation.back()
            context.automation.waitMs(ACTION_WAIT_MS)
        }
        return context.homeResolver.resolveToHome().success
    }

    override suspend fun run(): TaskRunResult {
        return runCatching {
            context.convergeHomeOrThrow()
            rules = parseRules(rulesJson)
            run(loadState())
        }.getOrElse { interruptAndPersist() }
    }

    private lateinit var rules: List<ArenaRule>

    override suspend fun executeState(state: ArenaState): StepOutcome<ArenaState> {
        return when (state) {
            ArenaState.ENTER_ARENA -> detectActionStep(
                state = ArenaState.ENTER_ARENA,
                detectRuleId = RULE_ARENA_ENTRY,
                actionRuleId = RULE_CHALLENGE,
                next = ArenaState.START_BATTLE,
                waitMs = ACTION_WAIT_MS,
                maxRetry = ENTER_ARENA_MAX_RETRY,
                retryKey = KEY_ENTER_ARENA_RETRY_COUNT
            )
            ArenaState.START_BATTLE -> detectActionStep(
                state = ArenaState.START_BATTLE,
                detectRuleId = RULE_START,
                actionRuleId = RULE_START,
                next = ArenaState.WAIT_RESULT,
                waitMs = ACTION_WAIT_MS,
                maxRetry = START_BATTLE_MAX_RETRY,
                retryKey = KEY_START_BATTLE_RETRY_COUNT,
                postconditionAlreadyAchieved = { detectById(RULE_CHALLENGE) || detectById(RULE_ARENA_ENTRY) }
            )
            ArenaState.WAIT_RESULT -> {
                if (detectById(RULE_CHALLENGE) || detectById(RULE_ARENA_ENTRY)) {
                    persistWaitResultRetryCount(0)
                    persistRetryCount(KEY_ENTER_ARENA_RETRY_COUNT, 0)
                    persistRetryCount(KEY_START_BATTLE_RETRY_COUNT, 0)
                    StepOutcome(ArenaState.EXIT)
                } else {
                    val retryCount = loadWaitResultRetryCount() + 1
                    persistWaitResultRetryCount(retryCount)
                    if (retryCount >= WAIT_RESULT_MAX_RETRY) {
                        repeat(FALLBACK_BACK_STEPS) {
                            context.automation.back()
                            context.automation.waitMs(ACTION_WAIT_MS)
                        }
                        persistWaitResultRetryCount(0)
                        return if (context.homeResolver.resolveToHome().success) {
                            StepOutcome(ArenaState.ENTER_ARENA, TaskRunResult.Retry)
                        } else {
                            StepOutcome(ArenaState.WAIT_RESULT, interruptAndPersist())
                        }
                    }
                    context.automation.waitMs(RESULT_WAIT_MS)
                    StepOutcome(ArenaState.WAIT_RESULT)
                }
            }

            ArenaState.EXIT -> {
                context.automation.back()
                context.automation.waitMs(ACTION_WAIT_MS)
                clearState()
                persistExceptionCount(0)
                persistWaitResultRetryCount(0)
                persistRetryCount(KEY_ENTER_ARENA_RETRY_COUNT, 0)
                persistRetryCount(KEY_START_BATTLE_RETRY_COUNT, 0)
                StepOutcome(ArenaState.EXIT, TaskRunResult.Success)
            }
        }
    }

    private suspend fun detectActionStep(
        state: ArenaState,
        detectRuleId: String,
        actionRuleId: String,
        next: ArenaState,
        waitMs: Long,
        maxRetry: Int,
        retryKey: String,
        postconditionAlreadyAchieved: (suspend () -> Boolean)? = null
    ): StepOutcome<ArenaState> {
        if (!detectById(detectRuleId)) {
            val retryCount = loadRetryCount(retryKey) + 1
            persistRetryCount(retryKey, retryCount)
            if (retryCount >= maxRetry) {
                persistRetryCount(retryKey, 0)
                repeat(FALLBACK_BACK_STEPS) {
                    context.automation.back()
                    context.automation.waitMs(ACTION_WAIT_MS)
                }
                return if (context.homeResolver.resolveToHome().success) {
                    clearState()
                    StepOutcome(ArenaState.ENTER_ARENA, TaskRunResult.Retry)
                } else {
                    StepOutcome(state, interruptAndPersist())
                }
            }
            return StepOutcome(state, TaskRunResult.Retry)
        }

        persistRetryCount(retryKey, 0)

        if (postconditionAlreadyAchieved?.invoke() == true) {
            println("[ArenaTask] state=$state action=$actionRuleId decision=SKIP reason=postcondition_already_achieved")
            return StepOutcome(next)
        }

        val actionRule = rules.firstOrNull { it.id == actionRuleId }
            ?: return StepOutcome(loadState(), interruptAndPersist())
        println("[ArenaTask] state=$state action=$actionRuleId decision=EXECUTE")
        doTap(actionRule)
        context.automation.waitMs(waitMs)
        return StepOutcome(next)
    }

    private suspend fun detectById(ruleId: String): Boolean {
        val rule = rules.firstOrNull { it.id == ruleId } ?: return false
        return detect(rule)
    }

    private suspend fun detect(rule: ArenaRule): Boolean = when (rule.type) {
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

    private suspend fun doTap(rule: ArenaRule) {
        val region = rule.region
        val x = rule.anchor?.x ?: (region.left + region.right) / 2
        val y = rule.anchor?.y ?: (region.top + region.bottom) / 2
        context.automation.tap(x, y)
    }

    private suspend fun loadState(): ArenaState {
        val raw = runtimeStateStore.getString(KEY_STATE, ArenaState.ENTER_ARENA.name)
        return ArenaState.entries.firstOrNull { it.name == raw } ?: ArenaState.ENTER_ARENA
    }

    override suspend fun persistState(state: ArenaState) {
        runtimeStateStore.putString(KEY_STATE, state.name)
    }

    override suspend fun currentStateName(): String = loadState().name

    override suspend fun currentTaskExceptionCount(): Int = loadExceptionCount()

    private suspend fun clearState() = runtimeStateStore.putString(KEY_STATE, ArenaState.ENTER_ARENA.name)
    private suspend fun loadExceptionCount(): Int = runtimeStateStore.getInt(KEY_EXCEPTION_COUNT, 0)
    private suspend fun persistExceptionCount(value: Int) = runtimeStateStore.putInt(KEY_EXCEPTION_COUNT, value.coerceAtLeast(0))
    private suspend fun loadWaitResultRetryCount(): Int = runtimeStateStore.getInt(KEY_WAIT_RESULT_RETRY_COUNT, 0)
    private suspend fun persistWaitResultRetryCount(value: Int) =
        runtimeStateStore.putInt(KEY_WAIT_RESULT_RETRY_COUNT, value.coerceAtLeast(0))
    private suspend fun loadRetryCount(key: String): Int = runtimeStateStore.getInt(key, 0)
    private suspend fun persistRetryCount(key: String, value: Int) = runtimeStateStore.putInt(key, value.coerceAtLeast(0))

    private suspend fun interruptAndPersist(): TaskRunResult {
        persistExceptionCount(loadExceptionCount() + 1)
        return TaskRunResult.Interrupted
    }

    private fun parseRules(raw: String): List<ArenaRule> = json.decodeFromString(ArenaRulePayload.serializer(), raw).rules

    enum class ArenaState { ENTER_ARENA, START_BATTLE, WAIT_RESULT, EXIT }

    @Serializable private data class ArenaRulePayload(val rules: List<ArenaRule>)

    @Serializable
    private data class ArenaRule(
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
        private const val KEY_STATE = "arena_task_state"
        private const val KEY_EXCEPTION_COUNT = "arena_task_exception_count"
        private const val RULE_ARENA_ENTRY = "cmp_arena_upgrade_cn"
        private const val RULE_CHALLENGE = "mul_arena_challenge_cn"
        private const val RULE_START = "cmp_arena_start_cn"
        private const val ACTION_WAIT_MS = 400L
        private const val RESULT_WAIT_MS = 1500L
        private const val WAIT_RESULT_MAX_RETRY = 10
        private const val ENTER_ARENA_MAX_RETRY = 8
        private const val START_BATTLE_MAX_RETRY = 8
        private const val FALLBACK_BACK_STEPS = 3
        private const val KEY_WAIT_RESULT_RETRY_COUNT = "arena_task_wait_result_retry_count"
        private const val KEY_ENTER_ARENA_RETRY_COUNT = "arena_task_enter_retry_count"
        private const val KEY_START_BATTLE_RETRY_COUNT = "arena_task_start_retry_count"
    }
}
