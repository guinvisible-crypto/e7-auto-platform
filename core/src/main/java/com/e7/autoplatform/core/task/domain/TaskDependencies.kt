package com.e7.autoplatform.core.task.domain

import com.e7.autoplatform.core.home.HomeResolveResult
import com.e7.autoplatform.core.home.HomeResolver
import com.e7.autoplatform.core.image.MatchResult
import com.e7.autoplatform.core.image.PatternDefinition
import com.e7.autoplatform.core.image.ScanRegion
import com.e7.autoplatform.core.image.TemplateDefinition

/**
 * Abstractions for domain tasks.
 * Tasks must not directly access system APIs.
 */
interface AutomationGateway {
    suspend fun tap(x: Int, y: Int): Boolean
    suspend fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long): Boolean
    suspend fun back(): Boolean
    suspend fun waitMs(ms: Long)
}

interface ImageGateway {
    suspend fun findColor(color: Int, tolerance: Int = 0, region: ScanRegion? = null, step: Int = 1): MatchResult
    suspend fun findPattern(pattern: PatternDefinition, tolerance: Int = 0, region: ScanRegion? = null, step: Int = 1): MatchResult
    suspend fun matchTemplate(template: TemplateDefinition, threshold: Float = 1f): MatchResult
}

class TaskContext(
    val homeResolver: HomeResolver,
    val image: ImageGateway,
    val automation: AutomationGateway
) {
    suspend fun convergeHomeOrThrow(): HomeResolveResult {
        val result = homeResolver.resolveToHome()
        if (!result.success) error("home convergence failed: last=${result.lastState}")
        return result
    }
}
