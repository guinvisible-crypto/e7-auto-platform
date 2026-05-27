package com.e7.autoplatform.ui.main

import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

object ShizukuShellExecutor {
    private const val TAG = "INPUT"

    fun isReady(): Boolean {
        if (!Shizuku.pingBinder()) return false
        return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    suspend fun execute(cmd: String): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "EXECUTE: $cmd")
        runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                Log.d(TAG, "SUCCESS")
                true
            } else {
                Log.e(TAG, "FAILED")
                false
            }
        }.getOrElse {
            Log.e(TAG, "INPUT_EXCEPTION", it)
            false
        }
    }
}
