# 本地提醒

每条日期可配置多条提醒。每条提醒有稳定实例 ID、0–365 天提前量和独立的分钟级本地时间；
编辑器支持当天/提前 1/3/7 天的常用语义，同时允许任意提前天数。重复规则由统一发生日期算法计算，
包括月末、2 月 29 日和农历年度纪念日。

## 调度与投递

提醒链路为：

`EditorSheet` → `AthenaViewModel` → Room → `AndroidReminderScheduler` →
`AlarmManager` → `ReminderReceiver` → Android 通知渠道。

- 新增或编辑后，Room 成功写入才会调度；关闭提醒或已经错过的一次性提醒会取消旧闹钟。
- 删除时先删除 Room 记录，再取消该条目的全部提醒实例。
- 闹钟以条目 ID + 提醒实例 ID 对应的唯一 URI 标识，更新会取消已移除的实例并替换仍存在的实例，不依赖可能碰撞的
  `String.hashCode()`。
- Receiver 不信任闹钟中缓存的标题。它会重新读取 Room，并校验条目 ID、发生日期和预期
  触发时间；编辑或时区变化遗留的旧广播不会发出通知。
- Room v5 在每条 `entry_reminders` 记录上保存 `lastNotifiedReminderKey`，按提醒实例原子认领发生日。重复广播、进程重启
  或向西跨时区后再次落入同一提醒窗口，都不会重复通知。
- 重复提醒投递后会重新读取最新条目再安排下一次；一次性提醒投递后不再续排。

应用在以下时机会从 Room 重建全部闹钟：

- 应用启动；
- 设备启动完成；
- 应用包升级；
- 系统日期、时间或时区变化。

`TIME_SET` 和 `TIMEZONE_CHANGED` 是 Android 8.0 后仍允许 manifest receiver 接收的隐式
广播。正常跨午夜不会破坏已经使用绝对时间保存的 RTC 闹钟；`DATE_CHANGED` 也注册用于
支持会发送该广播的系统版本。

## 权限与可靠性

Android 13 及以上首次启用提醒时，Athena 会先解释用途，再请求 `POST_NOTIFICATIONS`。
如果用户拒绝，或之后在系统中关闭应用/渠道通知，提醒配置仍会保存，并提供进入通知设置
的引导。

通知统一使用 `date_reminders` 高重要性渠道。Athena 使用
`AlarmManager.setAndAllowWhileIdle()` 的非精确 RTC 唤醒闹钟，不声明
`SCHEDULE_EXACT_ALARM` 或 `USE_EXACT_ALARM`。这适合日期级提醒并避免不必要的特殊权限；
系统保证不会早于目标时间投递，但 Android 12 及以上通常可延后最多约一小时，省电模式下
可能更久。

通知提供“查看”和“稍后 1 小时”操作。稍后提醒写入 Room v6 的 `reminder_snoozes`，键为
条目 ID、提醒实例 ID 和发生日期；重复点击会更新同一行和同一个 PendingIntent，而不会累积。
它使用独立的非精确闹钟，不修改原提醒的下一次重复计划。重启和日期/时间/时区变化会按保存的
绝对毫秒时间重建；已经到期的 snooze 会尽快尝试投递。删除条目会级联删除 snooze，编辑移除
某提醒实例时会清理对应孤儿行。

参考 Android 官方说明：

- [安排闹钟](https://developer.android.com/develop/background-work/services/alarms)
- [通知运行时权限](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- [隐式广播例外](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions)

## 数据迁移

- v1 → v2：增加提醒开关、提前天数与时间，默认关闭、当天、09:00。
- v2 → v3：增加最后投递键；旧版任意提前天数会归一化到最近的 0/1/3/7 天选项
  （分界为 0、1–2、3–5、6 及以上）。
- v3 → v4：增加归档状态。
- v4 → v5：日期表增加日历制式和通用重复规则；旧年度开关无损映射。旧单提醒迁移为一条
  ID 为 `<entryId>:legacy-reminder` 的提醒记录，并保留最后投递键。
- v5 → v6：增加定时事件、外部来源身份字段和持久化 snooze 表；旧记录的新增字段均为 null，
  不改变任何既有日期或提醒计划。

Room schema 导出在 `app/schemas/com.athena.dates.AthenaDatabase/`，迁移由
`AthenaDatabaseTest` 验证。

## 自动化验证

本地 JVM 调度/业务测试、lint 和 Debug 构建：

```bash
./gradlew test lint assembleDebug
```

连接 API 26 或更高版本的设备/模拟器后，验证 Room migration、真实 manifest Receiver、
通知渠道和重复投递抑制：

```bash
./gradlew connectedDebugAndroidTest
```

仅运行通知端到端用例：

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.athena.dates.ReminderNotificationTest
```

Instrumentation 用例会为 Debug 包授予通知权限、写入一条测试记录、向 Receiver 发送匹配
的提醒广播，并通过系统 `NotificationManager` 断言通知发布；再次发送相同发生日后断言
不会重复发布。

## 手工验收

1. 安装 Debug 包，在未来几分钟创建日期并启用提醒。
2. 添加多条不同提前量/时间提醒，确认完全相同的配置无法重复添加，且保存后可再次编辑。
3. Android 13+ 上确认先出现用途说明，再出现系统通知权限弹窗；拒绝后确认可进入设置。
4. 保存后退出应用，等待通知并点击，确认 Athena 被打开。
5. 分别编辑提醒时间、关闭提醒、删除条目，确认旧时间不再通知。
6. 对启用多提醒的条目模拟重启以及日期、时间、时区变化，确认全部实例被重建，且每个实例
   在同一发生日只通知一次。
