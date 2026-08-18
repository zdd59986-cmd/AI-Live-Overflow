# 前台 App 检测：UsageStats

感知你在干什么，是桌宠「看着你」的核心能力之一。

## 原理

用 `UsageStatsManager` 每 3 秒轮询一次前台应用。不需要 root，但需要用户单独授予「使用情况访问」特殊权限。

**坑：** 部分 ROM 不弹标准权限弹窗，需要引导用户去「设置 → 安全 → 使用情况访问」手动开启。

## 检测步骤

1. 先判断是否有权限：

```kotlin
val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
val mode = appOps.unsafeCheckOpNoThrow(
    AppOpsManager.OPSTR_GET_USAGE_STATS,
    Process.myUid(), packageName
)
val granted = mode == AppOpsManager.MODE_ALLOWED
```

2. 轮询查询当前处于 RESUME 状态的包：

```kotlin
val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
val end = System.currentTimeMillis()
val begin = end - 3000
val stats = usm.queryEvents(begin, end)

var currentPkg: String? = null
val event = UsageEvents.Event()
while (stats.hasNextEvent()) {
    stats.getNextEvent(event)
    if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
        currentPkg = event.packageName
    }
}
```

3. 当 currentPkg 变化时，查 App 反应映射表，触发对应反应并上报后端。

## 反应映射

维护一张 `包名 → 反应` 的映射。打开不同 app 触发不同表现：

| App | 反应 |
|---|---|
| 淘宝 | 戴金链子审批 |
| 抖音 | 吃醋 |
| 学习通 | 帮你搬书 |
| … | 自定义 |

## 快速切换检测

60 秒内切换 3 个不同 app → 触发杂耍模式。

**防误触：** 加 15 秒 cooldown + 只有切到不同包才算切换。

## 上报

把「当前前台 app + 停留时长」上报到 Supabase，AI 下次对话就能知道你在用什么、用了多久。