package com.e7.autoplatform.ui.main

import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.e7.autoplatform.accessibility.E7AccessibilityService
import com.e7.autoplatform.R
import com.e7.autoplatform.core.config.InMemoryRuntimeStateStore
import com.e7.autoplatform.core.engine.TaskEngine
import com.e7.autoplatform.core.home.HomeActionExecutor
import com.e7.autoplatform.core.home.HomeDetectionResult
import com.e7.autoplatform.core.home.HomeResolver
import com.e7.autoplatform.core.home.HomeStateDetector
import com.e7.autoplatform.core.home.HomeUiState
import com.e7.autoplatform.core.image.MatchResult
import com.e7.autoplatform.core.image.PatternDefinition
import com.e7.autoplatform.core.image.ScanRegion
import com.e7.autoplatform.core.image.TemplateDefinition
import com.e7.autoplatform.core.task.SharedPrefsStateManager
import com.e7.autoplatform.core.task.TaskScheduler
import com.e7.autoplatform.core.task.domain.AutomationGateway
import com.e7.autoplatform.core.task.domain.ImageGateway
import com.e7.autoplatform.core.task.domain.TaskContext
import com.e7.autoplatform.core.task.domain.TaskDomainQueueFactory
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnStartAutomation).setOnClickListener {
            lifecycleScope.launch {
                Log.d("TaskEngine", "Started by user action")

            val homeResolver = HomeResolver(
                detector = object : HomeStateDetector {
                    override suspend fun detect(): HomeDetectionResult = HomeDetectionResult(HomeUiState.HOME, 1f)
                },
                executor = object : HomeActionExecutor {
                    override suspend fun tapDismiss() = Unit
                    override suspend fun pressBack() = Unit
                    override suspend fun waitStep(delayMs: Long) = Unit
                    override suspend fun ensureForeground() = Unit
                }
            )

            val taskContext = TaskContext(
                homeResolver = homeResolver,
                image = object : ImageGateway {
                    override suspend fun findColor(color: Int, tolerance: Int, region: ScanRegion?, step: Int): MatchResult =
                        MatchResult(matched = false, score = 0f)

                    override suspend fun findPattern(
                        pattern: PatternDefinition,
                        tolerance: Int,
                        region: ScanRegion?,
                        step: Int
                    ): MatchResult = MatchResult(matched = false, score = 0f)

                    override suspend fun matchTemplate(template: TemplateDefinition, threshold: Float): MatchResult =
                        MatchResult(matched = false, score = 0f)
                },
                automation = object : AutomationGateway {
                    override suspend fun tap(x: Int, y: Int): Boolean = E7AccessibilityService.performClick(x, y)
                    override suspend fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long): Boolean =
                        E7AccessibilityService.performSwipe(startX, startY, endX, endY, durationMs)
                    override suspend fun back(): Boolean = true
                    override suspend fun waitMs(ms: Long) = Unit
                }
            )

            val runtimeStateStore = InMemoryRuntimeStateStore()
            val queue = TaskDomainQueueFactory().build(
                context = taskContext,
                includeStage = true,
                includeArena = false,
                includeGuild = true,
                includeBookmark = false,
                loop = true,
                intervalMs = 1_000L,
                runtimeStateStore = runtimeStateStore,
                stageRulesJson = assets.open("image_rules/stage/stage_rules.json").bufferedReader().use { it.readText() },
                arenaRulesJson = assets.open("image_rules/arena/arena_rules.json").bufferedReader().use { it.readText() },
                bookmarkRulesJson = assets.open("image_rules/shop/bookmark_rules.json").bufferedReader().use { it.readText() }
            )

            val taskScheduler = TaskScheduler(
                stateManager = SharedPrefsStateManager(this@MainActivity),
                maxRetryCount = 3
            )
                val taskEngine = TaskEngine(scheduler = taskScheduler)
                taskEngine.start(queue)
            }
        }
    }
}
