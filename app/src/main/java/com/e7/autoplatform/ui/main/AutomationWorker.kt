package com.e7.autoplatform.ui.main

import android.util.Log
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
        if (!ShizukuShellExecutor.isReady()) {
            Log.e("AUTO", "SHIZUKU_NOT_READY")
            return
        }
        running = true
        Log.d("AUTO", "WORKER_STARTED")
        Log.d("AUTO", "MAIN_THREAD_BLOCK_FIXED")
        scope.launch {
            while (running) {
                try {
                    val ok = ShizukuShellExecutor.execute("input swipe 600 1200 600 900 1800")
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
