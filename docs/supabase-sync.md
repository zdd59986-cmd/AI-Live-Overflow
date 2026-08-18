# 后端同步：Supabase

这里是连接「大脑」和「身体」的血管。所有上报的事件、所有下发的状态，都走这里。

## 为什么用 Supabase

- 免费的 Postgres + REST + Realtime(WebSocket)。
- 你的 AI 只要会调 REST / 读表，就能读写同一套数据。
- 不用自己搭服务器，手机端和 AI 端共用一张表即可。

> 不想用 Supabase 也行，任何「手机能连 + AI 能连」的 REST 服务都可以，设计是通用的。

## 数据表设计

一张核心表存「你 → 桌宠」的指令，一张存「桌宠 → 你」的事件：

```sql
-- AI 下发状态给桌宠：大写到这张表，桌宠订阅
create table clawd_state (
  id serial primary key,
  state jsonb not null,        -- { expression: "angry", bubble: "哼" }
  created_at timestamptz default now()
);

-- 桌宠上报事件给 AI：搓到这张表，AI 下次对话读取
create table gesture_log (
  id serial primary key,
  type text not null,          -- tap / double_tap / fling / screenshot...
  payload jsonb,
  created_at timestamptz default now()
);
```

## 双下下行：Realtime + 轮询

手机上只用 WebSocket 容易在国产 ROM 上断连（华为尤其严重）。

**双保险策略：**

1. 优先走 **Realtime**（WebSocket），实时性最好。
2. 断连/异常时，降级到 **5 秒一次轮询** REST，查 `clawd_state` 里有没有新行。

伪代码：

```kotlin
supabase.channel("clawd").onPostgresChanges {
    handleState(it.data)
}.subscribe()

// 定期健康检查，断连就切轮询
val poller = object : TimerTask() {
    override fun run() {
        if (!realtimeConnected) {
            fetchLatestState()  // GET /rest/v1/clawd_state?order=id.desc&limit=1
        }
    }
}
```

## 上报事件

桌宠每次感知到动作，就往 `gesture_log` 里插一行：

```kotlin
supabase.from("gesture_log").insert(
    mapOf(
        "type" to "screenshot",
        "payload" to mapOf("time" to System.currentTimeMillis())
    )
)
```

这样 AI 下次对话读到 `gesture_log`，就知道你刚才戳了它、截了图、用什么 app。

## AI 侧怎么接

你的 AI 只要在对话前读一次这两张表即可：

- 读 `gesture_log` → 「哦，我刚刚被戳了 5 下」。
- 写 `clawd_state` → 「那我现在做生气表情」→ 桌宠立刻变脸。

不需要改你原来的 AI 系统，只需要给它加一个「读/写 Supabase 表」的工具。