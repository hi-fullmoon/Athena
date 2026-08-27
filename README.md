# Athena

Athena is an Android app for keeping calendars, anniversaries, and countdowns together.

中文项目、架构与模块说明见 [docs/repository-overview.zh-CN.md](docs/repository-overview.zh-CN.md)。

## Included

- Month calendar with important-day markers and date selection
- Separate anniversary and countdown lists
- Add Gregorian or Chinese-lunar dates (1900–2100), including lunar leap months
- Edit and delete existing dates from any list or calendar card
- Weekly, monthly, yearly, and custom-interval recurrence with an optional end date
- Local SQLite persistence for user-added dates using Room, including automatic migration from the earlier SharedPreferences format
- Five persistent in-app themes: Mist Violet, Sage, Sunrise Amber, Ocean Blue, and Rose
- Multiple per-date local reminders with independent 0–365 day lead times and times
- Reminder recovery after reboot and clock/time-zone changes, with persistent duplicate suppression
- Versioned JSON full backup and transactional restore through Android's system document picker
- Reusable colored tags, tag filtering, and dark-mode-safe tag presentation
- ICS import/export for all-day and timed dates, supported recurrence, lunar extensions, tags, notes, and multiple reminders
- Explicit Android Calendar Provider import/export with per-operation permissions, preview, stable identity, and no background sync
- Explicit contacts-birthday import using only names, birthdays, and stable lookup keys
- Per-instance configurable home-screen widget with compact/expanded layouts, filters, styling, tap-to-open, and manual refresh
- Native offline PNG share cards through FileProvider and the Android Sharesheet
- Static Add/Upcoming/Settings shortcuts plus View/Snooze notification actions
- Real-time title/note search with type, status, recurrence, reminder, and tag filters
- Automatic archive for expired one-time countdowns, with restore and delete controls
- System/light/dark appearance modes and optional Android 12+ dynamic colors

## Open and run

1. Open `D:\Developer\Athena` in Android Studio.
2. Use JDK 17 and let Android Studio sync the Gradle Wrapper.
3. Select an Android device or emulator running Android 8.0 (API 26) or newer.
4. Run the `app` configuration.

The project targets Android API 36 and uses Jetpack Compose.

See [docs/reminders.md](docs/reminders.md) for the local-notification architecture and device verification command.
See [docs/data-management.md](docs/data-management.md) for the backup schema, import safety model, duplicate strategy, and ICS compatibility.
See [docs/high-frequency.md](docs/high-frequency.md) for widget refresh rules, archive behavior, search controls, and appearance settings.
See [docs/date-capabilities.md](docs/date-capabilities.md) for lunar range, recurrence semantics, dependency rationale, and Room v6 migration.
See [docs/system-integration.md](docs/system-integration.md) for runtime permissions, provider transfers, widget configuration, sharing, shortcuts, and notification actions.

## Releases

Pushing a `vMAJOR.MINOR.PATCH` tag runs the signed Android release pipeline and publishes the APK plus its SHA-256 checksum to GitHub Releases. See [docs/releasing.md](docs/releasing.md) for one-time signing setup and release instructions.
