package com.e7.autoplatform.ui.main

import android.content.Context
import android.util.Log
import com.e7.autoplatform.accessibility.E7AccessibilityService
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object AutomationRuntime {
    private var taskEngine: TaskEngine? = null
    @Volatile
    var isRunning: Boolean = false
        private set

    fun start(context: Context) {
        Log.d("TASK", "AutomationRuntime.start CALLED")
        if (taskEngine != null) {
            Log.d("TASK", "ENGINE_ALREADY_RUNNING")
            return
        }
        if (isRunning) return
        if (!E7AccessibilityService.isConnected()) {
            Log.e("AUTO", "ACCESSIBILITY_NOT_CONNECTED")
            return
        }
        isRunning = true
        Log.d(TAG, "ACCESSIBILITY_STATUS = GESTURE_MODE")
        val appContext = context.applicationContext
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
                override suspend fun findColor(color: Int, tolerance: Int, region: ScanRegion?, step: Int): MatchResult = MatchResult(false)
                override suspend fun findPattern(pattern: PatternDefinition, tolerance: Int, region: ScanRegion?, step: Int): MatchResult = MatchResult(false)
                override suspend fun matchTemplate(template: TemplateDefinition, threshold: Float): MatchResult = MatchResult(false)
            },
            automation = object : AutomationGateway {
                override suspend fun tap(x: Int, y: Int): Boolean {
                    val ok = E7AccessibilityService.performGestureTap(x, y)
                    delay(350)
                    return ok
                }
                override suspend fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long): Boolean {
                    val safeDuration = durationMs.coerceAtLeast(1L)
                    val ok = E7AccessibilityService.performGestureSwipe(startX, startY, endX, endY, safeDuration)
                    delay(400)
                    return ok
                }
                override suspend fun isGestureRunning(): Boolean = false
                override suspend fun back(): Boolean = true
                override suspend fun waitMs(ms: Long) = delay(ms)
            }
        )

        val queue = TaskDomainQueueFactory().build(
            context = taskContext,
            includeStage = true,
            includeArena = false,
            includeGuild = true,
            includeBookmark = false,
            loop = true,
            intervalMs = 1_000L,
            runtimeStateStore = InMemoryRuntimeStateStore(),
            stageRulesJson = appContext.assets.open("image_rules/stage/stage_rules.json").bufferedReader().use { it.readText() },
            arenaRulesJson = appContext.assets.open("image_rules/arena/arena_rules.json").bufferedReader().use { it.readText() },
            bookmarkRulesJson = appContext.assets.open("image_rules/shop/bookmark_rules.json").bufferedReader().use { it.readText() }
        )

        val scheduler = TaskScheduler(
            stateManager = SharedPrefsStateManager(appContext),
            maxRetryCount = 3
        )
        val engine = TaskEngine(scheduler)
        taskEngine = engine
        CoroutineScope(Dispatchers.Main).launch {
            delay(3000)
            Log.d("TASK", "ENGINE_START")
            engine.start(queue)
        }
    }

    fun stop() {
        CoroutineScope(Dispatchers.Main).launch {
            taskEngine?.stop()
            taskEngine = null
            isRunning = false
        }
    }

    private const val TAG = "AutomationRuntime"
}
