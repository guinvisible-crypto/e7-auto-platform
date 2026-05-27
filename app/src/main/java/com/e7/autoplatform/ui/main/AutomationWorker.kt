package com.e7.autoplatform.ui.main

import android.util.Log
import com.e7.autoplatform.accessibility.E7AccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

object AutomationWorker {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var running = false

    fun startAutomation() {
        if (running) return
        if (!E7AccessibilityService.isConnected()) {
            Log.e("AUTO", "ACCESSIBILITY_NOT_CONNECTED")
            return
        }
        running = true
        Log.d("AUTO", "WORKER_STARTED")
        Log.d("AUTO", "MAIN_THREAD_BLOCK_FIXED")
        scope.launch {
            while (running) {
                try {
                    Log.d("AUTO", "SWIPE_DEBUG x=800 y1=1600 y2=600")
                    val ok = E7AccessibilityService.performGestureSwipe(800, 1600, 800, 600, 1800)
                    if (ok) Log.d("AUTO", "SWIPE_EXECUTED")
                    delay(Random.nextLong(2500, 4500))
                } catch (e: Exception) {
                    Log.e("AUTO", "LOOP_ERROR", e)
                }
            }
        }
    }

    fun stopAutomation() {
        running = false
        Log.d("AUTO", "WORKER_STOPPED")
    }
}
