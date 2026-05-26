package com.e7.autoplatform.core.image

import android.graphics.Bitmap

interface RegionScanner {
    fun findColor(
        bitmap: Bitmap,
        targetColor: Int,
        tolerance: Int = 0,
        region: ScanRegion? = null,
        step: Int = 1
    ): MatchResult
}
