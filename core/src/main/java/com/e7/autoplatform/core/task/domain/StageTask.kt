package com.e7.autoplatform.core.task.domain

import android.util.Log
import android.content.res.Resources
import com.e7.autoplatform.core.engine.QueuedTask
import com.e7.autoplatform.core.engine.TaskRunResult
import com.e7.autoplatform.core.engine.TaskRuntimeSnapshotProvider
import com.e7.autoplatform.core.engine.WatchdogFallbackHandler
import com.e7.autoplatform.core.image.MatchResult
import com.e7.autoplatform.core.image.ScanRegion
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class StageTask(
    private val context: TaskContext,
    private val rulesJson: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
    maxSteps: Int = 10
) : QueuedTask, WatchdogFallbackHandler, TaskRuntimeSnapshotProvider, TaskStateMachine<StageTask.StageState>(maxSteps) {

    private var state: StageState = StageState.ENTER
    private var taskExceptionCount: Int = 0
    private lateinit var rules: List<StageRule>
    private var matchedRule: StageRule? = null
    private var matchedResult: MatchResult? = null

    override suspend fun run(): TaskRunResult {
        return runCatching {
            Log.d("StageTask", "RULES_JSON=$rulesJson")
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
            StageState.ENTER -> StepOutcome(StageState.DETECT)
            StageState.DETECT -> {
                Log.d("StageTask", "DETECT_RULE_COUNT=${rules.size}")
                val detectionResults = mutableListOf<Pair<StageRule, MatchResult>>()
                rules.forEach { rule ->
                    Log.d("StageTask", "DETECT_RULE_ID=${rule.id}")
                    val result = detect(rule)
                    Log.d("StageTask", "DETECT_RESULT ruleId=${rule.id} matched=${result.matched}")
                    detectionResults += rule to result
                }

                val matchedEntry = detectionResults.firstOrNull { (_, result) -> result.matched }
                val matchedRule = matchedEntry?.first
                return if (matchedRule != null) {
                    this.matchedRule = matchedRule
                    this.matchedResult = matchedEntry.second
                    Log.d("StageTask", "DETECT_SUCCESS ruleId=${matchedRule.id}")
                    Log.d("StageTask", "RULE_ID=${matchedRule.id}")
                    Log.d("StageTask", "event=state_transition task=StageTask from=DETECT to=CLICK")
                    StepOutcome(StageState.CLICK)
                } else {
                    this.matchedRule = null
                    this.matchedResult = null
                    Log.d("StageTask", "DETECT_FAIL ruleCount=${rules.size}")
                    Log.d("StageTask", "event=state_transition task=StageTask from=DETECT to=WAIT")
                    StepOutcome(StageState.WAIT)
                }
            }
            StageState.CLICK -> {
                Log.d("StageTask", "state=CLICK")
                val rule = matchedRule
                if (rule == null) {
                    Log.d("StageTask", "NO_MATCHED_RULE")
                    return StepOutcome(StageState.WAIT)
                }
                Log.d("StageTask", "RULE_ID=${rule.id}")
                val anchor = rule.anchor
                if (anchor == null) {
                    Log.d("StageTask", "NO_ANCHOR_FOUND")
                    return StepOutcome(StageState.WAIT)
                }
                val matchPoint = matchedResult?.point
                val x = matchPoint?.x ?: anchor.x
                val y = matchPoint?.y ?: anchor.y
                Log.d("StageTask", "ANCHOR x=${anchor.x} y=${anchor.y}")
                val dm = Resources.getSystem().displayMetrics
                Log.d("StageTask", "SCREEN_INFO width=${dm.widthPixels} height=${dm.heightPixels} clickX=$x clickY=$y")
                Log.d("StageTask", "FOREGROUND_PACKAGE=unavailable_in_core")
                Log.d("StageTask", "CLICK_ATTEMPT x=$x y=$y")
                val clicked = performClick(x, y)
                if (clicked) {
                    Log.d("StageTask", "CLICK_DISPATCHED")
                } else {
                    Log.d("StageTask", "CLICK_FAILED")
                }
                Log.d("StageTask", "event=state_transition task=StageTask from=CLICK to=WAIT")
                StepOutcome(StageState.WAIT)
            }
            StageState.WAIT -> {
                Log.d("StageTask", "state=WAIT")
                Thread.sleep(1000)
                matchedRule = null
                matchedResult = null
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

    private suspend fun detect(rule: StageRule): MatchResult {
        val anchor = rule.anchor ?: return MatchResult(matched = false)
        return context.image.findColor(
            color = anchor.rgb.parseRgb(),
            tolerance = rule.tolerance,
            region = rule.region.toScanRegion(),
            step = 1
        )
    }

    private suspend fun performClick(x: Int, y: Int): Boolean {
        return context.automation.tap(x, y)
    }

    private fun parseRules(raw: String): List<StageRule> = json.decodeFromString(StageRulePayload.serializer(), raw).rules

    enum class StageState {
        ENTER,
        DETECT,
        CLICK,
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
        private const val RULE_STAGE_ENTRY = "cmp_battle_surrender"
        private const val WAIT_MS = 1000L
    }
}
