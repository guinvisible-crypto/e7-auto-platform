package com.e7.autoplatform.core.task.domain

import com.e7.autoplatform.core.engine.TaskRunResult
import com.e7.autoplatform.core.home.HomeActionExecutor
import com.e7.autoplatform.core.home.HomeDetectionResult
import com.e7.autoplatform.core.home.HomeResolver
import com.e7.autoplatform.core.home.HomeStateDetector
import com.e7.autoplatform.core.home.HomeUiState
import com.e7.autoplatform.core.image.MatchResult
import com.e7.autoplatform.core.image.PatternDefinition
import com.e7.autoplatform.core.image.ScanRegion
import com.e7.autoplatform.core.image.TemplateDefinition
import com.e7.autoplatform.core.config.InMemoryRuntimeStateStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskDomainDecouplingTest {

    @Test
    fun `tasks run through home resolver boundary`() = runBlocking {
        val context = TaskContext(
            homeResolver = HomeResolver(AlwaysHomeDetector(), NoOpHomeActionExecutor(), maxSteps = 1),
            image = NoOpImageGateway(),
            automation = NoOpAutomationGateway()
        )

        assertEquals(TaskRunResult.Success, StageTask(context).run())
        assertEquals(TaskRunResult.Success, ArenaTask(context).run())
        assertEquals(TaskRunResult.Success, GuildTask(context).run())
        assertEquals(
            TaskRunResult.Success,
            BookmarkTask(
                context = context,
                runtimeStateStore = InMemoryRuntimeStateStore(),
                rulesJson = """{"rules":[{"id":"cmp_shop_refresh_btn","type":"single_color","region":{"left":0,"top":0,"right":10,"bottom":10},"tolerance":0,"anchor":{"x":1,"y":1,"rgb":"FFFFFF"}},{"id":"mul_shop_buy_btn","type":"multi_point","region":{"left":0,"top":0,"right":10,"bottom":10},"tolerance":0,"anchorRgb":"FFFFFF","offsets":[]}]}"""
            ).run()
        )
    }

    private class AlwaysHomeDetector : HomeStateDetector {
        override suspend fun detect(): HomeDetectionResult = HomeDetectionResult(HomeUiState.HOME)
    }

    private class NoOpHomeActionExecutor : HomeActionExecutor {
        override suspend fun tapDismiss() = Unit
        override suspend fun pressBack() = Unit
        override suspend fun waitStep(delayMs: Long) = Unit
        override suspend fun ensureForeground() = Unit
    }

    private class NoOpAutomationGateway : AutomationGateway {
        override suspend fun tap(x: Int, y: Int): Boolean = true
        override suspend fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long): Boolean = true
        override suspend fun isGestureRunning(): Boolean = false
        override suspend fun back(): Boolean = true
        override suspend fun waitMs(ms: Long) = Unit
    }

    private class NoOpImageGateway : ImageGateway {
        override suspend fun findColor(color: Int, tolerance: Int, region: ScanRegion?, step: Int): MatchResult = MatchResult(false)
        override suspend fun findPattern(pattern: PatternDefinition, tolerance: Int, region: ScanRegion?, step: Int): MatchResult = MatchResult(false)
        override suspend fun matchTemplate(template: TemplateDefinition, threshold: Float): MatchResult = MatchResult(false)
    }
}
