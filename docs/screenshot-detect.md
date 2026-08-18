# 截图检测：FileObserver

想在被截图的时候被拍到，桌宠得第一时间知道自己被截屏了。

## 原理

监听系统截图目录（`/sdcard/DCIM/Screenshots` 等）的文件变化，用 `FileObserver`。

**坑：** FileObserver 的回调运行在后台线程，不能直接操作 WebView / WindowManager，必须切主线程。

```kotlin
class ScreenshotObserver(path: String, private val onShot: () -> Unit) :
    FileObserver(path, FileObserver.CREATE or FileObserver.MOVED_TO) {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onEvent(event: Int, path: String?) {
        val name = path ?: return
        if (!screenshotEndings.any { name.endsWith(it) }) return
        // 切主线程，触发摆 pose + 上报
        mainHandler.post(onShot)
    }
}

// 监听
val obs = ScreenshotObserver(
    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        .resolve("Screenshots")
        .absolutePath
) {
    overlay.showPose("screenshot")
    reportToBackend("screenshot")
}
obs.startWatching()
```

## 注意

- 不同 ROM 截图目录可能不同（有些在 `/sdcard/Pictures/Screenshots`），多监听几个目录更稳。
- 用文件名后缀（`.jpg`/`.png`）过滤，避免其它文件变化误触发。
- 切主线程要用 `Handler(Looper.getMainLooper()).post {}`，千万别直接在 onEvent 里操作 UI。

## 触发表现

检测到截图 → 桌宠摆 pose、发气泡（「偷拍我？」「哈！被我发现了吧」），并上报后端让 AI 知道。