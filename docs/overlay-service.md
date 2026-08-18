# 核心服务详解：Overlay Service

悬浮窗是整个桌宠的「身体」所在。所有表情、气泡、手势都发生在这里。

## 一个前台 Service

必须使用**前台服务（foreground service）**，否则系统在后台几分钟就会杀掉它。前台服务需要：

- 一个常驻通知（用来保活 + 给用户一个可见入口）
- `FOREGROUND_SERVICE` 权限
- Android 8.0+ 需要先创建 NotificationChannel

## 悬浮窗层级

用 `WindowManager.addView()` 把视图加到 `TYPE_APPLICATION_OVERLAY` 层（Android 8.0+）。

```kotlin
val params = WindowManager.LayoutParams(
    WRAP_CONTENT, WRAP_CONTENT,
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
    PixelFormat.TRANSLUCENT
)
```

`FLAG_NOT_FOCUSABLE` 让它不抢焦点；`FLAG_LAYOUT_NO_LIMITS` 允许拖到屏幕边缘外。

## 透明 WebView

`TYPE_APPLICATION_OVERLAY` 不支持直接放 SurfaceView；用 WebView 渲染 SVG 是最省事的跨版本方案。

**白屏三大坑（必须同时满足）：**

1. `setBackgroundColor(0x00000000)` 必须在 `loadUrl()` **之前**调用
2. HTML 的 `<body>` 要写 `background: transparent`
3. 别给 WebView 设不透明的 layout background

## 主线程问题

WindowManager 和 WebView 都只能主线程操作。所有从后台线程（FileObserver、Battery 回调）来的 UI 操作都要切主线程：

```kotlin
Handler(Looper.getMainLooper()).post {
    windowManager.updateViewLayout(view, params)
}
```

## 保活

- 通知设 `setOngoing(true)`，不可滑动删除
- 引导用户加电池白名单（尤其华为/小米）
- 如需更强保活，可监听 `ACTION_SCREEN_OFF` / 充电事件等做轻量唤醒

## 权限

- `SYSTEM_ALERT_WINDOW`：悬浮窗，需引导到系统设置手动授权
- `FOREGROUND_SERVICE`：前台服务
