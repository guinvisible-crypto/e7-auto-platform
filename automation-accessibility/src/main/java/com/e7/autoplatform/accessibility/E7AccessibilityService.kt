package com.e7.autoplatform.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent

open class E7AccessibilityService : AccessibilityService(), AutoClickController {

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        Log.i(TAG, "Accessibility service connected. manufacturer=${Build.MANUFACTURER}, model=${Build.MODEL}, sdk=${Build.VERSION.SDK_INT}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val eventType = event?.eventType ?: -1
        Log.v(TAG, "onAccessibilityEvent received. type=$eventType")
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        activeInstance = null
        Log.i(TAG, "Accessibility service destroyed")
        super.onDestroy()
    }

    override fun click(
        x: Float,
        y: Float,
        gestureId: String,
        callback: AutomationGestureCallback?
    ): Boolean {
        Log.d(TAG, "click requested. x=$x, y=$y, gestureId=$gestureId")
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, CLICK_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureWithCallback(gesture, gestureId, callback)
    }

    override fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long,
        gestureId: String,
        callback: AutomationGestureCallback?
    ): Boolean {
        val safeDuration = durationMs.coerceAtLeast(MIN_SWIPE_DURATION_MS)
        Log.d(
            TAG,
            "swipe requested. start=($startX,$startY), end=($endX,$endY), durationMs=$safeDuration, gestureId=$gestureId"
        )
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, safeDuration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureWithCallback(gesture, gestureId, callback)
    }

    private fun dispatchGestureWithCallback(
        gesture: GestureDescription,
        gestureId: String,
        callback: AutomationGestureCallback?
    ): Boolean {
        val dispatched = dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.i(TAG, "gesture completed. gestureId=$gestureId")
                    callback?.onCompleted(gestureId)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "gesture cancelled. gestureId=$gestureId")
                    callback?.onCancelled(gestureId)
                }
            },
            null
        )

        if (!dispatched) {
            Log.e(TAG, "dispatchGesture returned false. gestureId=$gestureId")
            callback?.onCancelled(gestureId)
        } else {
            Log.d(TAG, "dispatchGesture submitted. gestureId=$gestureId")
        }
        return dispatched
    }

    companion object {
        private const val TAG = "E7Accessibility"
        private const val CLICK_DURATION_MS = 50L
        private const val MIN_SWIPE_DURATION_MS = 100L

        @Volatile
        private var activeInstance: E7AccessibilityService? = null

        fun performClick(x: Int, y: Int): Boolean {
            val service = activeInstance ?: return false
            val displayMetrics = service.resources.displayMetrics
            val width = displayMetrics.widthPixels
            val height = displayMetrics.heightPixels
            val foregroundPackage = service.rootInActiveWindow?.packageName?.toString() ?: "unknown"

            Log.d(TAG, "click_validation screen_width=$width screen_height=$height")
            Log.d(TAG, "click_validation foreground_package=$foregroundPackage")

            val points = listOf(
                Triple(width / 2, height / 2, "center"),
                Triple(10, 10, "top_left"),
                Triple((width - 10).coerceAtLeast(0), (height - 10).coerceAtLeast(0), "bottom_right")
            )

            var anyDispatched = false
            points.forEach { (px, py, label) ->
                Log.d(TAG, "click_validation target=$label x=$px y=$py")
                val dispatched = service.click(px.toFloat(), py.toFloat(), gestureId = "stage_click_$label")
                anyDispatched = anyDispatched || dispatched
                Thread.sleep(1000)
            }

            return anyDispatched
        }

        fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long): Boolean {
            val service = activeInstance ?: return false
            return service.swipe(
                startX = startX.toFloat(),
                startY = startY.toFloat(),
                endX = endX.toFloat(),
                endY = endY.toFloat(),
                durationMs = durationMs,
                gestureId = "stage_swipe"
            )
        }
    }
}
