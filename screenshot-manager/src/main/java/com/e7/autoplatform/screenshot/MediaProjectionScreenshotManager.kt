package com.e7.autoplatform.screenshot

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max

class MediaProjectionScreenshotManager(
    private val mediaProjection: MediaProjection,
    private val config: ScreenCaptureConfig
) : ScreenshotManager {

    private val handlerThread = HandlerThread(CAPTURE_THREAD_NAME).apply { start() }
    private val handler = Handler(handlerThread.looper)

    @Volatile
    private var imageReader: ImageReader? = null

    @Volatile
    private var virtualDisplay: VirtualDisplay? = null

    init {
        initializePipeline()
    }

    override suspend fun captureBitmap(): Bitmap = withContext(Dispatchers.IO) {
        val reader = checkNotNull(imageReader) { "ImageReader is not initialized" }
        val timeoutAt = System.currentTimeMillis() + config.captureTimeoutMs

        while (System.currentTimeMillis() < timeoutAt) {
            reader.acquireLatestImage()?.use { image ->
                val plane = image.planes.firstOrNull()
                    ?: throw IllegalStateException("No image planes available")

                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * config.width
                val paddedWidth = config.width + rowPadding / max(pixelStride, 1)

                val tempBitmap = Bitmap.createBitmap(
                    paddedWidth,
                    config.height,
                    Bitmap.Config.ARGB_8888
                )
                tempBitmap.copyPixelsFromBuffer(buffer)

                val result = Bitmap.createBitmap(tempBitmap, 0, 0, config.width, config.height)
                tempBitmap.recycle()
                Log.d(TAG, "captureBitmap success. size=${result.width}x${result.height}")
                return@withContext result
            }

            delay(RETRY_DELAY_MS)
        }

        throw IllegalStateException("captureBitmap timeout after ${config.captureTimeoutMs}ms")
    }

    override fun release() {
        Log.i(TAG, "Releasing screenshot resources")
        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.close()
        imageReader = null

        handlerThread.quitSafely()
    }

    private fun initializePipeline() {
        imageReader = ImageReader.newInstance(
            config.width,
            config.height,
            PixelFormat.RGBA_8888,
            config.maxImages
        )

        virtualDisplay = mediaProjection.createVirtualDisplay(
            config.virtualDisplayName,
            config.width,
            config.height,
            config.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler
        )

        Log.i(
            TAG,
            "MediaProjection pipeline initialized for background capture. " +
                "size=${config.width}x${config.height}, dpi=${config.densityDpi}, sdk=${Build.VERSION.SDK_INT}, manufacturer=${Build.MANUFACTURER}"
        )
    }
    companion object {
        private const val TAG = "ScreenshotManager"
        private const val CAPTURE_THREAD_NAME = "MediaProjectionCaptureThread"
        private const val RETRY_DELAY_MS = 30L
    }
}
