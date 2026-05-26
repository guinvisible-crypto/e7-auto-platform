package com.e7.autoplatform.core.task.domain

import android.util.Log
import com.e7.autoplatform.core.engine.QueuedTask
import com.e7.autoplatform.core.engine.TaskRunResult
import com.e7.autoplatform.core.engine.TaskRuntimeSnapshotProvider
import com.e7.autoplatform.core.engine.WatchdogFallbackHandler
import com.e7.autoplatform.core.image.MatchResult
import com.e7.autoplatform.core.image.ScanRegion
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ShopTask(
    private val context: TaskContext,
    private val rulesJson: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
    maxSteps: Int = 30
) : QueuedTask, WatchdogFallbackHandler, TaskRuntimeSnapshotProvider, TaskStateMachine<ShopTask.ShopState>(maxSteps) {

    private var state: ShopState = ShopState.ENTER
    private var taskExceptionCount: Int = 0
    private lateinit var rules: List<ShopRule>
    private var matchedResult: MatchResult? = null

    override suspend fun run(): TaskRunResult {
        return runCatching {
            rules = parseRules(rulesJson)
            run(state)
        }.getOrElse {
            taskExceptionCount += 1
            state = ShopState.DONE
            TaskRunResult.Interrupted
        }
    }

    override suspend fun executeState(state: ShopState): StepOutcome<ShopState> {
        this.state = state
        return when (state) {
            ShopState.ENTER -> StepOutcome(ShopState.DETECT)
            ShopState.DETECT -> {
                Log.d(TAG, "SHOP_DETECT_START")
                val bookmarkRules = rules.filter { it.id in bookmarkRuleIds }
                val matchedEntry = bookmarkRules.firstNotNullOfOrNull { rule ->
                    val result = detect(rule)
                    if (result.matched) rule to result else null
                }
                if (matchedEntry != null) {
                    matchedResult = matchedEntry.second
                    Log.d(TAG, "BOOKMARK_FOUND ruleId=${matchedEntry.first.id}")
                    StepOutcome(ShopState.CLICK_ITEM)
                } else {
                    StepOutcome(ShopState.SCROLL)
                }
            }

            ShopState.SCROLL -> {
                context.automation.swipe(600, 1600, 600, 600, 300)
                StepOutcome(ShopState.DETECT)
            }

            ShopState.CLICK_ITEM -> {
                val point = matchedResult?.point
                if (point == null) {
                    StepOutcome(ShopState.DETECT)
                } else {
                    Log.d(TAG, "CLICK_ITEM x=${point.x} y=${point.y}")
                    context.automation.tap(point.x, point.y)
                    StepOutcome(ShopState.CONFIRM_BUY)
                }
            }

            ShopState.CONFIRM_BUY -> {
                Log.d(TAG, "CONFIRM_BUY")
                context.automation.waitMs(500)
                context.automation.tap(900, 1600)
                StepOutcome(ShopState.WAIT)
            }

            ShopState.WAIT -> {
                context.automation.waitMs(1000)
                matchedResult = null
                StepOutcome(ShopState.DETECT)
            }

            ShopState.DONE -> StepOutcome(ShopState.DONE, TaskRunResult.Success)
        }
    }

    override suspend fun persistState(state: ShopState) {
        this.state = state
    }

    override suspend fun onExecutionTimeout(): Boolean = context.homeResolver.resolveToHome().success

    override suspend fun currentStateName(): String = state.name

    override suspend fun currentTaskExceptionCount(): Int = taskExceptionCount

    private suspend fun detect(rule: ShopRule): MatchResult {
        val anchor = rule.anchor ?: return MatchResult(matched = false)
        return context.image.findColor(anchor.rgb.parseRgb(), rule.tolerance, rule.region.toScanRegion(), 1)
    }

    private fun parseRules(raw: String): List<ShopRule> = json.decodeFromString(ShopRulePayload.serializer(), raw).rules

    enum class ShopState { ENTER, DETECT, SCROLL, CLICK_ITEM, CONFIRM_BUY, WAIT, DONE }

    @Serializable
    private data class ShopRulePayload(val rules: List<ShopRule>)

    @Serializable
    private data class ShopRule(
        val id: String,
        val type: String,
        val region: RegionJson,
        val tolerance: Int = 0,
        val anchor: AnchorJson? = null,
        @SerialName("anchor_rgb") val anchorRgb: String? = null
    )

    @Serializable private data class RegionJson(val left: Int, val top: Int, val right: Int, val bottom: Int)
    @Serializable private data class AnchorJson(val x: Int, val y: Int, val rgb: String)

    private fun RegionJson.toScanRegion(): ScanRegion = ScanRegion(left, top, right, bottom)
    private fun String.parseRgb(): Int = (0xFF shl 24) or removePrefix("#").toInt(16)

    companion object {
        private const val TAG = "ShopTask"
        private val bookmarkRuleIds = setOf("cmp_shop_bookmark_blue", "cmp_shop_bookmark_red")
    }
}
