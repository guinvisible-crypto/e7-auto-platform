package com.e7.autoplatform.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.e7.autoplatform.R
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {
    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode != SHIZUKU_REQUEST_CODE) return@OnRequestPermissionResultListener
        if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "SHIZUKU_PERMISSION_GRANTED")
        } else {
            Log.w(TAG, "SHIZUKU_PERMISSION_DENIED")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        ensureShizukuPermission()
        ensureOverlayPermissionAndStartService()
    }

    override fun onResume() {
        super.onResume()
        if (canDrawOverlays()) {
            startFloatingService()
        }
    }

    private fun ensureOverlayPermissionAndStartService() {
        if (canDrawOverlays()) {
            startFloatingService()
            return
        }
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun canDrawOverlays(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun ensureShizukuPermission() {
        if (!Shizuku.pingBinder()) {
            Log.w(TAG, "SHIZUKU_PERMISSION_DENIED")
            return
        }
        if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "SHIZUKU_PERMISSION_GRANTED")
            return
        }
        Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingControlService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val SHIZUKU_REQUEST_CODE = 1001
    }
}
