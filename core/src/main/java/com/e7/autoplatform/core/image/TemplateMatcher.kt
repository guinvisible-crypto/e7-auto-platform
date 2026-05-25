package com.e7.autoplatform.core.image

import android.graphics.Bitmap

interface TemplateMatcher {
    fun matchTemplate(
        bitmap: Bitmap,
        template: TemplateDefinition,
        threshold: Float = 1f
    ): MatchResult
}
