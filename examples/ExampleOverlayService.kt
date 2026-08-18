package com.example.aioverflow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView

/**
 * AI-Live-Overflow — 最简悬浮窗示例（能跑）
 *
 * 这是把「AI 身体」搬到屏幕上的最小骨架：
 *  - 前台服务保活
 *  - WindowManager 放一个悬浮窗
 *  - 透明 WebView 加载本地 HTML/SVG
 *  - 可拖拽（用 rawX/rawY 避免第一帧瞬移）
 *
 * 请记得在 AndroidManifest.xml 声明本服务，并授予
 * SYSTEM_ALERT_WINDOW 悬浮窗权限。
 */
class ExampleOverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "overlay_alive"
        private const val NOTIFICATION_ID = 101
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (overlayView == null) {
            showOverlay()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "桌宠保活",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "让桌宠在后台保持活着" }
            manager.createNotificationChannel(channel)
        }

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("桌宠在陪着你(ᴗ˳ᴗ)")
            .setContentText("点我回来陪我说说话")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .build()
    }

    private fun showOverlay() {
        // 注意：setBackgroundColor 必须在 loadUrl 之前调用，否则 WebView 白屏
        val webView = WebView(this).apply {
            setBackgroundColor(0x00000000) // 关键：透明背景
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
        }

        // 加载本地素材（assets/overlay/index.html）
        webView.loadUrl("file:///android_asset/overlay/index.html")

        // 用 rawX/rawY，避免拖拽第一帧瞬移
        var inTouch = false
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        webView.setOnTouchListener(object : View.OnTouchListener {
            var lastX = 0
            var lastY = 0
            var downX = 0
            var downY = 0
            var isDragging = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.rawX.toInt()
                        downY = e.rawY.toInt()
                        lastX = e.rawX.toInt()
                        lastY = e.rawY.toInt()
                        inTouch = true
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val rawX = e.rawX.toInt()
                        val rawY = e.rawY.toInt()
                        if (Math.abs(rawX - downX) > 12 || Math.abs(rawY - downY) > 12) {
                            isDragging = true
                        }
                        if (isDragging) {
                            params.x += rawX - lastX
                            params.y += rawY - lastY
                            lastX = rawX
                            lastY = rawY
                            runOnMain { windowManager.updateViewLayout(v, params) }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            // 单击：随机反应示例
                            runOnMain { webView.loadUrl("javascript:window.onTap&&window.onTap()") }
                        }
                        isDragging = false
                        inTouch = false
                        return true
                    }
                }
                return false
            }
        })

        overlayView = webView
        windowManager.addView(webView, params)
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
