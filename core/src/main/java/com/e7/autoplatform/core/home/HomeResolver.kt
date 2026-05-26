package com.e7.autoplatform.core.home

class HomeResolver(
    private val detector: HomeStateDetector,
    private val executor: HomeActionExecutor,
    private val maxSteps: Int = 120,
    private val stepDelayMs: Long = 300,
    private val unknownBackThreshold: Int = 2,
    private val fallbackBackPresses: Int = 5,
    private val maxFallbackAttempts: Int = 2
) {

    suspend fun resolveToHome(): HomeResolveResult {
        val normal = convergeLoop(maxSteps, aggressive = false)
        if (normal.success) return normal

        repeat(maxFallbackAttempts) {
            aggressiveFallbackReset()
            val aggressive = convergeLoop(maxSteps, aggressive = true)
            if (aggressive.success) return aggressive
        }

        // Safe abort: unable to converge after normal + aggressive tiers.
        return HomeResolveResult(false, maxSteps * (maxFallbackAttempts + 1), HomeUiState.UNKNOWN)
    }

    private suspend fun convergeLoop(steps: Int, aggressive: Boolean): HomeResolveResult {
        var unknownCount = 0
        var last = HomeUiState.UNKNOWN

        repeat(steps) { step ->
            executor.ensureForeground()
            val detection = detector.detect()
            last = detection.state

            when (detection.state) {
                HomeUiState.HOME -> return HomeResolveResult(true, step + 1, HomeUiState.HOME)
                HomeUiState.POPUP,
                HomeUiState.RESULT,
                HomeUiState.LOGIN -> {
                    unknownCount = 0
                    executor.tapDismiss()
                }
                HomeUiState.UNKNOWN -> {
                    unknownCount++
                    val threshold = if (aggressive) 1 else unknownBackThreshold
                    if (unknownCount >= threshold) {
                        executor.pressBack()
                        unknownCount = 0
                    } else {
                        executor.tapDismiss()
                    }
                }
            }

            executor.waitStep(stepDelayMs)
        }

        return HomeResolveResult(false, steps, last)
    }

    private suspend fun aggressiveFallbackReset() {
        repeat(fallbackBackPresses) {
            executor.pressBack()
            executor.waitStep(stepDelayMs)
        }
        // Soft reset hook delegated to platform implementation.
        executor.ensureForeground()
    }
}

data class HomeResolveResult(
    val success: Boolean,
    val stepsUsed: Int,
    val lastState: HomeUiState
)
