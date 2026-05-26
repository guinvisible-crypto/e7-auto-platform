package com.e7.autoplatform.accessibility

import android.util.Log

class MyAccessibilityService : E7AccessibilityService() {
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service Connected")
    }

    override fun onDestroy() {
        Log.d(TAG, "Service Destroyed")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Accessibility"
    }
}
