package com.e7.autoplatform.accessibility

import android.util.Log
import android.view.accessibility.AccessibilityEvent

class MyAccessibilityService : E7AccessibilityService() {
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "ACCESSIBILITY_CONNECTED")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        super.onAccessibilityEvent(event)
        Log.d(TAG, "ACCESSIBILITY_EVENT_RECEIVED type=${event?.eventType ?: -1}")
    }

    override fun onInterrupt() {
        super.onInterrupt()
        Log.d(TAG, "ACCESSIBILITY_INTERRUPTED")
    }

    override fun onDestroy() {
        Log.d(TAG, "Service Destroyed")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Accessibility"
    }
}
