package com.e7.autoplatform.ui.main

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku

object ShizukuShellExecutor {
    private const val TAG = "ShizukuShellExecutor"

    fun isReady(): Boolean {
        if (!Shizuku.pingBinder()) return false
        return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    fun execute(cmd: String): Boolean {
        Log.i(TAG, "INPUT_EXECUTE cmd=$cmd")
        return runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                Log.i(TAG, "INPUT_SUCCESS")
                true
            } else {
                Log.e(TAG, "INPUT_FAILED")
                false
            }
        }.getOrElse {
            Log.e(TAG, "INPUT_EXCEPTION", it)
            false
        }
    }
}
