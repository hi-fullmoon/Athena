# Athena 仓库说明

## 项目定位

Athena 是一个本地优先的 Android 日期管理应用，用来统一管理日历事件、纪念日和倒数日。它不仅提供日期记录和展示，还覆盖中国农历、重复规则、多提醒、标签筛选、系统日历与联系人导入、备份恢复、桌面小组件和离线分享等完整使用链路。

应用不依赖网络服务，也不执行静默后台同步。用户数据主要保存在设备本地的 Room 数据库中；涉及通知、系统日历、联系人或文件的能力，都由用户主动触发并按需申请权限。

## 核心能力

- 通过月历、纪念日列表和倒数日列表查看日期；普通日程也可作为独立类型保存。
- 支持公历和 1900–2100 年中国农历，包括农历闰月和农历年度重复。
- 支持不重复、每日、每周、每月、每年以及 1–99 的自定义重复间隔，并可设置截止日期。
- 每条日期最多配置 32 个本地提醒，每个提醒拥有独立的提前天数、时间和投递去重状态。
- 支持标题/备注实时搜索，以及类型、状态、重复方式、提醒和标签筛选。
- 自动归档已过期的一次性倒数日，并支持恢复或永久删除。
- 通过 JSON 完整备份、ICS 导入导出、Android Calendar Provider 和联系人生日进行显式数据交换。
- 提供可独立配置筛选条件、显示数量和样式的 Android 桌面小组件。
- 通过 Android Canvas 生成离线 PNG 分享卡片，并使用系统分享面板发送。
- 支持系统/浅色/深色模式、五套内置配色和 Android 12+ 动态颜色。

## 技术栈与运行基线

| 类别 | 选型 |
| --- | --- |
| 语言 | Kotlin 2.3.21 |
| UI | Jetpack Compose、Material 3 |
| 状态管理 | `ViewModel`、Kotlin Flow、Compose lifecycle state |
| 本地存储 | Room 2.8.4、SQLite，当前 schema version 6 |
| 异步处理 | Kotlin Coroutines |
| 农历转换 | `cn.6tail:lunar:1.7.7` |
| 构建系统 | Gradle Kotlin DSL、Android Gradle Plugin 8.13.2、KSP |
| Java 版本 | JDK 17 |
| Android 版本 | minSdk 26、targetSdk/compileSdk 36 |
| 测试 | JUnit 4、Compose UI Test、AndroidX Test、Room Testing |

仓库只有一个 Gradle 应用模块 `:app`，包名和 application ID 均为 `com.athena.dates`。

## 架构概览

项目采用适合中小型单模块应用的分层组织。文件按功能拆分，但全部位于同一个 Kotlin package 中。

```text
Compose 界面与系统入口
        │ 用户操作 / 生命周期事件
        ▼
AthenaViewModel
        │ 协调状态与业务流程
        ├──────────────► DataTransferService ──► 文件 / 系统日历 / 联系人
        │
        ▼
DateEntryRepository
        │
        ▼
Room DAO / AthenaDatabase
        │ 数据变化后
        ├──────────────► ReminderScheduler ──► AlarmManager / Notification
        └──────────────► WidgetRefresher ────► AppWidget / RemoteViews
```

主要职责如下：

- `MainActivity.kt`：应用入口，创建 `AthenaViewModel`，处理静态快捷方式和通知跳转动作。
- `AthenaApp.kt`：顶层 Compose 状态编排，管理主导航、编辑器、设置、权限说明和各类导入导出对话框。
- `AthenaViewModel.kt`：连接 UI 与数据层，持有查询/外观/导入导出状态，并协调保存、删除、归档、提醒和系统集成流程。
- `DateEntryRepository.kt`：定义日期仓储接口；Room 实现负责实体映射、旧 SharedPreferences 数据迁移和小组件刷新。
- `AthenaDatabase.kt`：Room entity、DAO、事务和 v1→v6 数据库迁移。
- `DateOccurrence.kt`：统一计算下一次发生日期，集中处理月末、闰年、重复间隔和农历年度日期等边界。
- `DataTransferService.kt`：为文件、Calendar Provider 和联系人数据交换提供面向 ViewModel 的编排层。

## 核心数据模型

领域模型的中心是 `DateEntry`，主要包含：

- 基础信息：ID、标题、备注、日期类型；
- 日期信息：作为排序与互操作基准的公历锚点，以及可选农历年月日和闰月标记；
- 时间信息：从系统日历导入时可保存本地开始时间和 IANA 时区；
- 行为信息：重复规则、多条提醒、标签、归档状态；
- 外部身份：用于系统日历和联系人重复导入时保持幂等。

Room v6 将数据规范化存放在以下表中：

| 表 | 用途 |
| --- | --- |
| `date_entries` | 日期主体、日历制式、重复规则、归档状态和外部来源 |
| `entry_reminders` | 每个日期的多条提醒及最后投递标记 |
| `date_tags` | 可复用的标签名称和颜色 |
| `date_entry_tags` | 日期与标签的多对多关系 |
| `reminder_snoozes` | 持久化的“稍后 1 小时”提醒状态 |

数据库写入、恢复和批量导入使用事务，失败时整体回滚。仓库保留了 v1–v6 的导出 schema，并注册了完整迁移链，避免升级时破坏既有数据。

## 功能模块索引

| 领域 | 主要文件 | 说明 |
| --- | --- | --- |
| 主界面与导航 | `AthenaApp.kt`、`CommonComponents.kt` | 顶层界面、底部导航、通用日期卡片与空状态 |
| 日期展示 | `CalendarScreen.kt`、`AnniversaryScreen.kt`、`CountdownScreen.kt` | 月历、纪念日和倒数日视图 |
| 编辑日期 | `EditorScreen.kt` | 日期类型、公历/农历、重复、提醒、标签等输入与校验 |
| 搜索筛选 | `EntryQuery.kt`、`SearchFilterBar.kt` | 查询模型、内存筛选和确定性排序 |
| 主题设置 | `AthenaTheme.kt`、`SettingsSheet.kt` | 主题模式、调色板、动态颜色和设置入口 |
| 本地提醒 | `ReminderScheduler.kt`、`ReminderOperations.kt` | 闹钟调度、通知、重建、去重和稍后提醒 |
| 备份与 ICS | `DataTransfer.kt`、`DataTransferService.kt` | 严格解析、预览、去重、合并/替换和格式编解码 |
| 系统日历 | `CalendarProviderImport.kt`、`CalendarProviderExport.kt` | 显式读取/写入 Calendar Provider |
| 联系人生日 | `ContactsBirthdayImport.kt` | 只读取姓名、生日和稳定 lookup key |
| 桌面小组件 | `UpcomingDatesWidget.kt`、`WidgetConfiguration.kt` | RemoteViews 渲染和按实例配置 |
| 分享卡片 | `ShareCard.kt` | Canvas 渲染、FileProvider 和系统分享 |

## 数据交换与系统集成

Athena 的数据交换以“先预览、后确认”为原则：

- JSON：当前备份格式为 version 4，包含日期、标签、重复、提醒、归档、外部身份和外观设置；兼容导入 version 1–4。
- ICS：映射全天/定时事件、受支持的重复规则、多提醒和标签；Athena 专有扩展用于无损保留农历语义。
- 系统日历：由用户选择来源或目标日历；重复导入/导出通过稳定身份避免无限新增重复项。
- 联系人：只在用户主动导入生日时读取姓名、生日和 lookup key，不访问电话、短信或邮箱。

应用声明的功能权限只有通知、日历读写、联系人读取和开机完成接收。它不声明互联网、广泛存储、精确闹钟、账户、电话、短信或位置权限。文件导入导出使用 Android Storage Access Framework，由系统选择器授予单个 URI 的访问权。

## 目录结构

```text
Athena/
├── app/
│   ├── build.gradle.kts              # Android 应用模块配置
│   ├── schemas/                      # Room v1–v6 schema 快照
│   └── src/
│       ├── main/                     # Kotlin 源码、Manifest 与资源
│       ├── test/                     # JVM 单元测试
│       └── androidTest/              # 设备/模拟器仪器测试
├── docs/                             # 专题设计与运维文档
├── .github/workflows/
│   ├── ci.yml                        # 单测、lint 和 Debug 构建
│   └── release.yml                   # 签名 APK 与 GitHub Release
├── build.gradle.kts                  # 根插件版本
├── settings.gradle.kts               # 单模块工程声明
└── gradlew / gradlew.bat             # Gradle Wrapper
```

## 本地构建与测试

准备 JDK 17 和 Android SDK 后，可在仓库根目录执行：

```bash
# JVM 单元测试
./gradlew test

# 静态检查
./gradlew lint

# 构建 Debug APK
./gradlew assembleDebug

# 一次执行 CI 的主要验证项
./gradlew test lint assembleDebug
```

连接 Android 8.0（API 26）或更高版本的设备/模拟器后执行：

```bash
./gradlew connectedDebugAndroidTest
```

JVM 测试主要覆盖日期发生规则、农历转换、提醒调度、搜索筛选、备份/ICS、Calendar Provider 映射和小组件逻辑；仪器测试覆盖 Room 迁移与事务、Compose 高频交互、通知投递、FileProvider 和 RemoteViews。

## CI 与发布

每次 push 和 pull request 都会通过 GitHub Actions 运行单元测试、lint 和 Debug 构建。

推送符合 `vMAJOR.MINOR.PATCH` 格式的 Git tag 会触发发布流水线。流水线会：

1. 校验版本号并生成 Android `versionCode`；
2. 运行测试和 lint；
3. 使用 GitHub Secrets 中的签名信息生成并验证 Release APK；
4. 生成 SHA-256 校验文件；
5. 创建或更新 GitHub Release。

发布签名配置和具体操作见 `docs/releasing.md`。

## 重要边界

- 农历转换只保证 1900–2100 年范围；超出范围的日期不进入农历模型。
- 农历日期只允许不重复或按年重复，避免把公历周/月间隔误解为农历周期。
- 本地提醒使用非精确 `AlarmManager`，不会早于目标时间，但可能受系统省电策略影响而延迟。
- Athena 没有事件结束时间/持续时长模型；系统日历中的复杂持续时间、例外日期和部分高级重复规则只能降级或跳过，并在导入预览中报告。
- 桌面小组件配置与 launcher 分配的 widget ID 绑定，因此不会进入可移植备份。
- 所有核心数据均为本地数据；若卸载前需要迁移，应先创建 JSON 完整备份。

## 延伸文档

- `docs/date-capabilities.md`：公历/农历、重复规则和 Room 迁移。
- `docs/reminders.md`：提醒调度、通知可靠性和端到端验证。
- `docs/data-management.md`：JSON/ICS 格式、导入安全与去重规则。
- `docs/system-integration.md`：权限、系统日历、联系人、分享和快捷方式。
- `docs/high-frequency.md`：小组件、搜索筛选、归档和外观设置。
- `docs/releasing.md`：签名配置和发布流水线。

## 一句话总结

Athena 是一个以本地数据安全和显式用户控制为核心、同时具备完整日期计算与 Android 系统集成能力的单模块 Compose 应用。
