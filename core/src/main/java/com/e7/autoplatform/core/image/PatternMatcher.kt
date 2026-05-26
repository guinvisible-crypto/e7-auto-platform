package com.e7.autoplatform.core.image

import android.graphics.Bitmap

interface PatternMatcher {
    fun findPattern(
        bitmap: Bitmap,
        pattern: PatternDefinition,
        tolerance: Int = 0,
        region: ScanRegion? = null,
        step: Int = 1
    ): MatchResult
}
