package com.e7.autoplatform.accessibility

interface AutoClickController {
    fun click(
        x: Float,
        y: Float,
        gestureId: String = "click",
        callback: AutomationGestureCallback? = null
    ): Boolean

    fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long,
        gestureId: String = "swipe",
        callback: AutomationGestureCallback? = null
    ): Boolean
}
