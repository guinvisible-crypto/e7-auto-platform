package com.e7.autoplatform.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

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
        val stroke = GestureDescription.StrokeDescription(path, GESTURE_START_DELAY_MS, CLICK_DURATION_MS)
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
        val safeDuration = durationMs.coerceIn(MIN_SWIPE_DURATION_MS, MAX_SWIPE_DURATION_MS)
        Log.d(
            TAG,
            "swipe requested. start=($startX,$startY), end=($endX,$endY), durationMs=$safeDuration, gestureId=$gestureId"
        )
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, GESTURE_START_DELAY_MS, safeDuration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureWithCallback(gesture, gestureId, callback)
    }

    private fun dispatchGestureWithCallback(
        gesture: GestureDescription,
        gestureId: String,
        callback: AutomationGestureCallback?
    ): Boolean {
        val dispatched = runBlocking {
            withContext(Dispatchers.Main) {
                dispatchGesture(
                    gesture,
                    object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            Log.i(TAG, "GESTURE_COMPLETED gestureId=$gestureId")
                            callback?.onCompleted(gestureId)
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            Log.w(TAG, "GESTURE_CANCELLED gestureId=$gestureId")
                            callback?.onCancelled(gestureId)
                        }
                    },
                    null
                )
            }
        }

        if (!dispatched) {
            Log.e(TAG, "GESTURE_CANCELLED gestureId=$gestureId")
            callback?.onCancelled(gestureId)
        } else {
            Log.d(TAG, "GESTURE_DISPATCHED gestureId=$gestureId")
        }
        return dispatched
    }

    companion object {
        private const val TAG = "E7Accessibility"
        private const val CLICK_DURATION_MS = 50L
        private const val MIN_SWIPE_DURATION_MS = 800L
        private const val MAX_SWIPE_DURATION_MS = 1200L
        private const val GESTURE_START_DELAY_MS = 300L

        @Volatile
        private var activeInstance: E7AccessibilityService? = null

        fun isConnected(): Boolean = activeInstance != null

        suspend fun performClick(x: Int, y: Int): Boolean {
            val service = activeInstance
            if (service == null) {
                Log.w(TAG, "ACCESSIBILITY_NOT_CONNECTED")
                return false
            }
            val displayMetrics = service.resources.displayMetrics
            val width = displayMetrics.widthPixels
            val height = displayMetrics.heightPixels
            val foregroundPackage = service.rootInActiveWindow?.packageName?.toString() ?: "unknown"

            Log.d(TAG, "click_validation screen_width=$width screen_height=$height")
            Log.d(TAG, "click_validation foreground_package=$foregroundPackage")
            Log.d(TAG, "TAP_DISPATCH_ATTEMPT x=$x y=$y")
            val dispatched = service.click(x.toFloat(), y.toFloat(), gestureId = "tap_${x}_${y}")
            if (dispatched) Log.d(TAG, "TAP_DISPATCH_SUCCESS") else Log.e(TAG, "TAP_DISPATCH_FAIL")
            return dispatched
        }

        suspend fun performSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long): Boolean {
            val service = activeInstance
            if (service == null) {
                Log.w(TAG, "ACCESSIBILITY_NOT_CONNECTED")
                Log.e(TAG, "SWIPE_FAIL")
                return false
            }
            Log.d(TAG, "SWIPE_ATTEMPT startX=$startX startY=$startY endX=$endX endY=$endY durationMs=$durationMs")
            val dispatched = service.swipe(
                startX = startX.toFloat(),
                startY = startY.toFloat(),
                endX = endX.toFloat(),
                endY = endY.toFloat(),
                durationMs = durationMs,
                gestureId = "stage_swipe"
            )
            if (dispatched) Log.d(TAG, "SWIPE_SUCCESS") else Log.e(TAG, "SWIPE_FAIL")
            return dispatched
        }
    }
}
