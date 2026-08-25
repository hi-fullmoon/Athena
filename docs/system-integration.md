# System integration

Athena integrations are user-triggered, local-first, and intentionally do not implement silent background synchronization.

## Permissions

The feature permission set is `POST_NOTIFICATIONS`, `READ_CALENDAR`, `WRITE_CALENDAR`, `READ_CONTACTS`, and `RECEIVE_BOOT_COMPLETED`. Notification permission is requested when reminders are enabled. Calendar read/write and contacts read are each requested only from their corresponding data-management entry after an in-app purpose explanation. Denial leaves every unrelated feature usable and offers a route to application settings. Athena declares no storage, exact-alarm, internet, account, phone, SMS, or location permission.

## Calendar and contacts

Calendar import remains a selected, merge-only operation. Calendar export requires selecting Athena entries and one writable provider calendar, displays add/update/duplicate counts plus lossy-field warnings, and writes only after confirmation. Athena-owned exports use `CUSTOM_APP_PACKAGE` and `CUSTOM_APP_URI`; imported events retain `(calendarId,eventId)` identity. Supported date-time, all-day, daily/weekly/monthly/yearly recurrence, and before-start alert reminders are mapped. Unsupported semantics are listed in the report.

Contacts import queries the birthday event MIME type with only lookup key, display name, and start date. The lookup key produces a stable annual-anniversary identity. Yearless February 29 uses a leap-year canonical anchor and follows Athena's yearly February 29 policy.

## Widget, sharing, and shortcuts

`WidgetConfigurationActivity` persists filter/style/count data in a dedicated preference file keyed by `appWidgetId`. It is deliberately excluded from portable backup because widget IDs belong to a launcher installation. Database mutation flows refresh widgets; the date/time/time-zone/boot receiver refreshes them across temporal changes.

Share cards are rendered with Android `Canvas` into `cacheDir/share_cards`, use fixed readable light/dark palettes and bounded wrapping, and are shared as read-only `content://` PNG URIs through the system chooser. FileProvider exposes only that cache subdirectory. Old generated files are pruned by age and count.

Static shortcuts route to Add Date, Upcoming, and Settings without extra permissions. Reminder notifications expose View and Snooze. Snooze state is durable and separate from the original recurrence schedule; see `docs/reminders.md`.
