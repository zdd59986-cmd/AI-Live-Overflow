# 手势状态机

桌宠的互动全靠手势。用状态机管理，避免逻辑散落各处。

## 事件 → 动作

| 手势 | 判定 | 触发动作 |
|---|---|---|
| 单击 | down→up 且位移 < 12px、时长 < 300ms | 随机反应（眨眼/说话/跟随） |
| 双击 | 两次单击间隔 < 300ms | 特殊动画 |
| 长按 | down 按住 > 600ms 不移动 | 隐藏表情 |
| 拖拽 | 位移 > 12px | 移动悬浮窗 |
| Fling | up 时速度 > 阈值 | 甩出屏幕 → 自动爬回来 |
| 连击 | 2 秒内戳 3/5/8 次 | 层层递进反应 |

## 坐标用 rawX/rawY

**关键坑：** 一定要用 `event.rawX/rawY`（屏幕绝对坐标），不要用 `event.x/y`（相对视图左上角）。用相对坐标，第一帧会因为视图本身被移动而产生瞬移跳变。

```kotlin
var lastX = 0; var lastY = 0
var isDragging = false

override fun onTouch(v: View, e: MotionEvent): Boolean {
    when (e.action) {
        ACTION_DOWN -> {
            downX = e.rawX.toInt(); downY = e.rawY.toInt()
            lastX = e.rawX.toInt(); lastY = e.rawY.toInt()
            return true
        }
        ACTION_MOVE -> {
            val rawX = e.rawX.toInt(); val rawY = e.rawY.toInt()
            if (abs(rawX - downX) > 12 || abs(rawY - downY) > 12) isDragging = true
            if (isDragging) {
                params.x += rawX - lastX
                params.y += rawY - lastY
                lastX = rawX; lastY = rawY
                updateViewLayout(v, params)
            }
            return true
        }
        ACTION_UP -> {
            if (!isDragging) handleTap()
            isDragging = false
            return true
        }
    }
    return false
}
```

## 连击计数

用一个时间戳记录上次点击，2 秒内计数递增：

```
上次点击时刻 距今 > 2000ms → 重置计数为 1
否则 → 计数 + 1
计数 % 3/5/8 → 触发对应反应
```

## Fling：甩出去爬回来

捕获 up 事件的速度矢量，若超过阈值，用 ValueAnimator 沿矢量方向飞出屏幕，再反向弹回。可以用 CSS 动画交给 WebView 做，Kotlin 只负责上报方向和力度。

## 上报

每次手势都上报后端（Supabase `gesture_log` 表），这样 AI 下次对话能读到「发生了什么」。
