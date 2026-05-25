package com.e7.autoplatform.core.task.domain

import android.util.Log
import com.e7.autoplatform.core.engine.QueuedTask
import com.e7.autoplatform.core.engine.TaskRunResult
import com.e7.autoplatform.core.engine.TaskRuntimeSnapshotProvider
import com.e7.autoplatform.core.engine.WatchdogFallbackHandler
import com.e7.autoplatform.core.image.ScanRegion
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class StageTask(
    private val context: TaskContext,
    private val rulesJson: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
    maxSteps: Int = 10
) : QueuedTask, WatchdogFallbackHandler, TaskRuntimeSnapshotProvider, TaskStateMachine<StageTask.StageState>(maxSteps) {

    private var state: StageState = StageState.ENTER_STAGE
    private var taskExceptionCount: Int = 0
    private lateinit var rules: List<StageRule>

    override suspend fun run(): TaskRunResult {
        return runCatching {
            rules = parseRules(rulesJson)
            run(state)
        }.getOrElse {
            taskExceptionCount += 1
            state = StageState.DONE
            TaskRunResult.Interrupted
        }
    }

    override suspend fun executeState(state: StageState): StepOutcome<StageState> {
        this.state = state
        Log.d("StageTask", "state=${state.name}")
        return when (state) {
            StageState.ENTER_STAGE -> {
                context.convergeHomeOrThrow()
                StepOutcome(StageState.DETECT_BATTLE_BUTTON)
            }
            StageState.DETECT_BATTLE_BUTTON -> {
                val rule = rules.firstOrNull() ?: return StepOutcome(StageState.DONE, TaskRunResult.Interrupted)
                if (detect(rule)) {
                    StepOutcome(StageState.CLICK_BATTLE_BUTTON)
                } else {
                    StepOutcome(StageState.DONE, TaskRunResult.Retry)
                }
            }
            StageState.CLICK_BATTLE_BUTTON -> {
                Log.d("StageTask", "action=CLICK_BATTLE_BUTTON")
                val rule = rules.firstOrNull() ?: return StepOutcome(StageState.DONE, TaskRunResult.Interrupted)
                doTap(rule)
                StepOutcome(StageState.WAIT)
            }
            StageState.WAIT -> {
                context.automation.waitMs(WAIT_AFTER_CLICK_MS)
                StepOutcome(StageState.DONE)
            }
            StageState.DONE -> StepOutcome(StageState.DONE, TaskRunResult.Success)
        }
    }

    override suspend fun persistState(state: StageState) {
        this.state = state
    }

    override suspend fun onExecutionTimeout(): Boolean {
        taskExceptionCount += 1
        return context.homeResolver.resolveToHome().success
    }

    override suspend fun currentStateName(): String = state.name

    override suspend fun currentTaskExceptionCount(): Int = taskExceptionCount

    private suspend fun detect(rule: StageRule): Boolean {
        val anchor = rule.anchor ?: return false
        return context.image.findColor(
            color = anchor.rgb.parseRgb(),
            tolerance = rule.tolerance,
            region = rule.region.toScanRegion(),
            step = 1
        ).matched
    }

    private suspend fun doTap(rule: StageRule) {
        val region = rule.region
        val x = rule.anchor?.x ?: (region.left + region.right) / 2
        val y = rule.anchor?.y ?: (region.top + region.bottom) / 2
        context.automation.tap(x, y)
    }

    private fun parseRules(raw: String): List<StageRule> = json.decodeFromString(StageRulePayload.serializer(), raw).rules

    enum class StageState {
        ENTER_STAGE,
        DETECT_BATTLE_BUTTON,
        CLICK_BATTLE_BUTTON,
        WAIT,
        DONE
    }

    @Serializable
    private data class StageRulePayload(val rules: List<StageRule>)

    @Serializable
    private data class StageRule(
        val id: String,
        val type: String,
        val region: RegionJson,
        val tolerance: Int = 0,
        val anchor: AnchorJson? = null
    )

    @Serializable private data class RegionJson(val left: Int, val top: Int, val right: Int, val bottom: Int)
    @Serializable private data class AnchorJson(val x: Int, val y: Int, val rgb: String)

    private fun RegionJson.toScanRegion(): ScanRegion = ScanRegion(left, top, right, bottom)
    private fun String.parseRgb(): Int = (0xFF shl 24) or removePrefix("#").toInt(16)

    companion object {
        private const val WAIT_AFTER_CLICK_MS = 1500L
    }
}
