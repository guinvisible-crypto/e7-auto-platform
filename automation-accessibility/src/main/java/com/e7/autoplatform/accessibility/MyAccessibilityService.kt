package com.e7.autoplatform.accessibility

import android.util.Log

class MyAccessibilityService : E7AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service Connected")
    }

    companion object {
        private const val TAG = "Accessibility"
    }
}
