package com.e7.autoplatform.ui.main

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.e7.autoplatform.R

class FloatingControlService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingBall: TextView
    private lateinit var menuLayout: LinearLayout
    private lateinit var ballParams: WindowManager.LayoutParams
    private lateinit var menuParams: WindowManager.LayoutParams
    private var menuVisible = false
    private var isOverlayAdded = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isOverlayAdded) return START_STICKY
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FLOATING_SERVICE_CREATED")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupFloatingBall()
        setupFloatingMenu()
        isOverlayAdded = true
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::floatingBall.isInitialized && floatingBall.isAttachedToWindow) windowManager.removeView(floatingBall)
        if (this::menuLayout.isInitialized && menuLayout.isAttachedToWindow) windowManager.removeView(menuLayout)
        isOverlayAdded = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setupFloatingBall() {
        floatingBall = TextView(this).apply {
            text = "E7"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xCC000000.toInt())
            }
            setPadding(28, 28, 28, 28)
            setOnClickListener {
                Log.d(TAG, "FLOATING_BALL_CLICKED")
                toggleMenu()
            }
            setOnTouchListener(DragTouchListener())
        }

        ballParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 300
        }

        windowManager.addView(floatingBall, ballParams)
    }

    private fun setupFloatingMenu() {
        menuLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(0xCC111111.toInt())
            }
            setPadding(24, 24, 24, 24)
            visibility = View.GONE
        }

        val startBtn = Button(this).apply {
            text = "开始刷商店"
            setOnClickListener {
                Log.d(TAG, "TASK_START_REQUESTED")
                AutomationRuntime.start(this@FloatingControlService)
            }
        }
        val stopBtn = Button(this).apply {
            text = "停止任务"
            setOnClickListener {
                Log.d(TAG, "TASK_STOP_REQUESTED")
                AutomationRuntime.stop()
            }
        }
        val hideBtn = Button(this).apply {
            text = "隐藏菜单"
            setOnClickListener { toggleMenu(forceHide = true) }
        }

        menuLayout.addView(startBtn)
        menuLayout.addView(stopBtn)
        menuLayout.addView(hideBtn)

        menuParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 180
            y = 320
        }

        windowManager.addView(menuLayout, menuParams)
    }

    private fun toggleMenu(forceHide: Boolean = false) {
        menuVisible = if (forceHide) false else !menuVisible
        menuLayout.visibility = if (menuVisible) View.VISIBLE else View.GONE
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "E7 Automation", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("E7 Automation")
            .setContentText("Floating control is active")
            .setOngoing(true)
            .build()

    private inner class DragTouchListener : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var touchX = 0f
        private var touchY = 0f

        override fun onTouch(v: View?, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = ballParams.x
                    initialY = ballParams.y
                    touchX = event.rawX
                    touchY = event.rawY
                    return false
                }
                MotionEvent.ACTION_MOVE -> {
                    ballParams.x = initialX + (event.rawX - touchX).toInt()
                    ballParams.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(floatingBall, ballParams)
                    menuParams.x = ballParams.x + 100
                    menuParams.y = ballParams.y
                    windowManager.updateViewLayout(menuLayout, menuParams)
                    return true
                }
            }
            return false
        }
    }

    companion object {
        private const val TAG = "FloatingControlService"
        private const val CHANNEL_ID = "e7_automation_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
