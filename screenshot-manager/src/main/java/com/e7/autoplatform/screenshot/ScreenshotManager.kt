package com.e7.autoplatform.screenshot

import android.graphics.Bitmap

interface ScreenshotManager {
    suspend fun captureBitmap(): Bitmap
    fun release()
}
