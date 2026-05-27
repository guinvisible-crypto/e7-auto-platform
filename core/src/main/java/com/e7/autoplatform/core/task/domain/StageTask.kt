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
    private val refreshCost = 3
    private var usedSkyStone = 0
    private val maxSkyStone = 300

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
                Log.d("StageTask", "FORCE_ENTER_CLICK")
                this.matchedRule = null
                this.matchedResult = null
                StepOutcome(StageState.CLICK_ITEM)
            }
            StageState.CLICK_ITEM -> {
                Log.e("E7_DEBUG", "STAGE_CLICK")
                Log.d("StageTask", "state=CLICK_ITEM")
                val rule = matchedRule
                if (rule == null) {
                    val clicked = performClick(500, 500)
                    Log.d("StageTask", "CLICK_ATTEMPT x=500 y=500")
                    Log.d("StageTask", if (clicked) "CLICK_DISPATCHED" else "CLICK_FAILED")
                    return StepOutcome(StageState.WAIT_ANIMATION)
                }
                Log.d("StageTask", "RULE_ID=${rule.id}")
                val match = matchedResult?.point
                if (match == null) {
                    Log.d("StageTask", "NO_MATCH_POINT_FOUND")
                    return StepOutcome(StageState.DETECT)
                }
                val x = match.x
                val y = match.y
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
                Log.d("StageTask", "event=state_transition task=StageTask from=CLICK_ITEM to=CONFIRM_BUY")
                StepOutcome(StageState.CONFIRM_BUY)
            }
            StageState.CONFIRM_BUY -> {
                context.automation.waitMs(500)
                val clicked = performClick(900, 1600)
                Log.d("StageTask", "CONFIRM_BUY_CLICKED=$clicked")
                StepOutcome(StageState.WAIT_ANIMATION)
            }
            StageState.WAIT_ANIMATION -> {
                Log.d("StageTask", "state=WAIT_ANIMATION")
                context.automation.waitMs(1500)
                matchedRule = null
                matchedResult = null
                StepOutcome(StageState.DETECT)
            }
            StageState.REFRESH -> {
                if (!canRefresh()) {
                    Log.d("StageTask", "STOP_SKYSTONE_LIMIT used=$usedSkyStone limit=$maxSkyStone")
                    return StepOutcome(StageState.DONE)
                }
                Log.d("StageTask", "REFRESH_ATTEMPT")
                Log.d("AUTO", "SWIPE_DEBUG x=800 y1=1600 y2=600")
                val swiped = context.automation.swipe(
                    startX = 800,
                    startY = 1600,
                    endX = 800,
                    endY = 600,
                    durationMs = 1800
                )
                if (swiped) {
                    usedSkyStone += refreshCost
                    Log.d("StageTask", "REFRESH_SUCCESS")
                    Log.d("StageTask", "SKYSTONE_USED=$usedSkyStone")
                } else {
                    Log.d("StageTask", "REFRESH_FAILED")
                }
                while (context.automation.isGestureRunning()) {
                    Log.d("StageTask", "TASK_WAITING_GESTURE")
                    context.automation.waitMs(100)
                }
                Log.d("StageTask", "TASK_RESUME_AFTER_GESTURE")
                StepOutcome(StageState.DETECT)
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

    private fun canRefresh(): Boolean {
        return usedSkyStone + refreshCost <= maxSkyStone
    }

    private fun parseRules(raw: String): List<StageRule> = json.decodeFromString(StageRulePayload.serializer(), raw).rules

    enum class StageState {
        ENTER,
        DETECT,
        CLICK_ITEM,
        CONFIRM_BUY,
        WAIT_ANIMATION,
        REFRESH,
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
        private val prioritizedRuleIds = setOf(
            "cmp_shop_bookmark_blue",
            "cmp_shop_bookmark_red"
        )
    }
}
