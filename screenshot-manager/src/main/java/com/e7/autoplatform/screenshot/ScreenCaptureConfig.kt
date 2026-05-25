package com.e7.autoplatform.screenshot

data class ScreenCaptureConfig(
    val width: Int,
    val height: Int,
    val densityDpi: Int,
    val maxImages: Int = 2,
    val captureTimeoutMs: Long = 1_500L,
    val virtualDisplayName: String = "E7BackgroundCapture"
)
