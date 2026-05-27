package com.e7.autoplatform.ui.main

import android.util.Log
import kotlinx.coroutines.delay
import rikka.shizuku.Shizuku

object ShizukuInputExecutor {
    private const val TAG = "ShizukuInput"

    suspend fun tap(x: Int, y: Int): Boolean {
        val ok = exec("input tap $x $y")
        if (ok) Log.i(TAG, "ADB_TAP_EXECUTED x=$x y=$y")
        delay(350)
        return ok
    }

    suspend fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long): Boolean {
        val duration = durationMs.coerceAtLeast(1L)
        val ok = exec("input swipe $startX $startY $endX $endY $duration")
        if (ok) {
            Log.i(
                TAG,
                "ADB_SWIPE_EXECUTED startX=$startX startY=$startY endX=$endX endY=$endY durationMs=$duration"
            )
        }
        delay(400)
        return ok
    }

    private fun exec(command: String): Boolean {
        if (!isShizukuReady()) {
            Log.w(TAG, "SHIZUKU_NOT_READY")
            return false
        }
        return runCatching {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val exitCode = process.waitFor()
            exitCode == 0
        }.onFailure {
            Log.e(TAG, "SHIZUKU_EXEC_FAILED cmd=$command", it)
        }.getOrDefault(false)
    }

    private fun isShizukuReady(): Boolean {
        if (!Shizuku.pingBinder()) return false
        return Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
