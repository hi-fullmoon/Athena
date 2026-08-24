# 本地提醒技术验证

提醒链路为：编辑器保存 → `AthenaViewModel` → Room Repository → `AlarmManager` →
`ReminderReceiver` → Android 通知渠道。设备重启或应用升级后，
`ReminderRescheduleReceiver` 会从 Room 重新建立所有启用的提醒。

## 自动化验证

连接 API 26 或更高版本的设备/模拟器后运行：

```bash
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.athena.dates.ReminderNotificationTest
```

该用例会授予 Debug 包通知权限，向真实 manifest Receiver 发送一条标题为
“本地通知端到端验证”的广播，并通过系统 `NotificationManager` 断言通知已发布。

调度时间计算由 `ReminderScheduleTest` 覆盖，包括提前天数、提醒时间、过期的一次性提醒，
以及年度重复（含闰日）滚动到下一年度的行为。

## 手工验收

1. 安装 Debug 包并允许通知权限。
2. 新增一个未来日期，启用“本地提醒”，填写提前天数与 `HH:mm` 时间。
3. 保存后退出应用，等待系统通知。
4. 点击通知，确认 Athena 被打开。

Athena 使用 `setAndAllowWhileIdle`，不申请“精确闹钟”特殊权限；Android 可能为省电而对
实际送达时间做小幅合并或延后。
