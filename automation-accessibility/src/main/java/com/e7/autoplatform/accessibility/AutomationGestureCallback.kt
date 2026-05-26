package com.e7.autoplatform.accessibility

interface AutomationGestureCallback {
    fun onCompleted(gestureId: String)
    fun onCancelled(gestureId: String)
}
