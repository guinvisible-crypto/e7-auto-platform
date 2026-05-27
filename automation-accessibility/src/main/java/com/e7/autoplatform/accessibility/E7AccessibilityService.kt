package com.e7.autoplatform.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlin.math.max
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
        val safeDuration = max(1L, durationMs)
        Log.d(
            TAG,
            "swipe requested. start=($startX,$startY), end=($endX,$endY), durationMs=$safeDuration, gestureId=$gestureId"
        )
        Log.i(TAG, "FINAL_DURATION_MS=$safeDuration")
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
        Log.e("E7_DEBUG", "DISPATCH_GESTURE")
        val dispatched = runBlocking {
            withContext(Dispatchers.Main) {
                dispatchGesture(
                    gesture,
                    object : GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            super.onCompleted(gestureDescription)
                            callback?.onCompleted(gestureId)
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            super.onCancelled(gestureDescription)
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
        private const val GESTURE_START_DELAY_MS = 300L

        @Volatile
        private var activeInstance: E7AccessibilityService? = null
        enum class GestureState { IDLE, RUNNING }
        @Volatile
        private var gestureState: GestureState = GestureState.IDLE
        private val swipeLock = Any()

        fun isConnected(): Boolean = activeInstance != null
        fun isGestureRunning(): Boolean = gestureState == GestureState.RUNNING

        suspend fun performGestureTap(x: Int, y: Int): Boolean {
            Log.e("E7_DEBUG", "ACTIVE_INSTANCE=" + (activeInstance != null))
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
            if (dispatched) {
                Log.d("AUTO", "GESTURE_TAP_DISPATCHED")
                Log.d(TAG, "TAP_DISPATCH_SUCCESS")
            } else {
                Log.e(TAG, "TAP_DISPATCH_FAIL")
            }
            return dispatched
        }

        suspend fun performGestureSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long): Boolean {
            Log.e("E7_DEBUG", "SWIPE_ENTER")
            Log.e("E7_DEBUG", "ACTIVE_INSTANCE=" + (activeInstance != null))
            synchronized(swipeLock) {
                if (gestureState == GestureState.RUNNING) {
                    Log.w(TAG, "SWIPE_BLOCKED_RUNNING")
                    return false
                }
                gestureState = GestureState.RUNNING
            }
            val service = activeInstance
            if (service == null) {
                Log.w(TAG, "ACCESSIBILITY_NOT_CONNECTED")
                Log.e(TAG, "SWIPE_FAIL")
                synchronized(swipeLock) { gestureState = GestureState.IDLE }
                return false
            }
            Log.i(TAG, "GESTURE_START")
            Log.d(TAG, "SWIPE_ATTEMPT startX=$startX startY=$startY endX=$endX endY=$endY durationMs=$durationMs")
            val dispatched = service.swipe(
                startX = startX.toFloat(),
                startY = startY.toFloat(),
                endX = endX.toFloat(),
                endY = endY.toFloat(),
                durationMs = durationMs,
                    gestureId = "stage_swipe",
                callback = object : AutomationGestureCallback {
                    override fun onCompleted(gestureId: String) {
                        synchronized(swipeLock) { gestureState = GestureState.IDLE }
                        Log.d("AUTO", "GESTURE_COMPLETED")
                    }

                    override fun onCancelled(gestureId: String) {
                        synchronized(swipeLock) { gestureState = GestureState.IDLE }
                        Log.d("AUTO", "GESTURE_CANCELLED")
                    }
                }
            )
            if (!dispatched) {
                synchronized(swipeLock) { gestureState = GestureState.IDLE }
            }
            if (dispatched) {
                Log.d("AUTO", "GESTURE_SWIPE_DISPATCHED")
                Log.d(TAG, "SWIPE_SUCCESS")
            } else {
                Log.e(TAG, "SWIPE_FAIL")
            }
            return dispatched
        }
    }
}
