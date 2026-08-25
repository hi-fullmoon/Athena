# High-frequency experience

## Home-screen widget

Athena ships an Android `AppWidgetProvider`/`RemoteViews` home-screen widget compatible with the app's API 26 minimum. It reads only the local Room database and has no network dependency. Each widget ID has independent date-type/tag filters, a 1–6 item count, and system or transparent styling. Compact size displays up to three rows; expanded size displays up to six.

- Tapping the widget opens Athena.
- The **Refresh** action rebuilds only that widget ID; resizing switches compact/expanded layouts.
- Removing a widget deletes only that instance's dedicated SharedPreferences configuration.
- Add, edit, delete, archive restore, JSON restore, and ICS import paths request an app-widget refresh after their database write.
- The existing boot/date/time/time-zone/package-change receiver archives newly expired countdowns, rebuilds reminders, and refreshes all widget instances.
- The provider also requests a daily system update as a fallback. Android controls the exact delivery time; Athena does not run a foreground service or request alarm/storage permissions for the widget.

To add it, long-press an empty home-screen area, open the system widget picker, and select **Athena 近期日期**.

## Search, filters, and sorting

The search field matches title and note in real time. Whitespace-separated search terms must all occur across the combined title/note text. Filters cover:

- anniversary, countdown, and schedule types;
- active or expired entries;
- yearly or non-yearly entries;
- reminder enabled or disabled;
- one or more selected tags (matching any selected tag).

Sort order can be next occurrence or normalized name. Archived entries are always excluded from the main calendar and lists. The query lives in `AthenaViewModel`, so an Activity recreation such as screen rotation does not reset it. Filtering is an in-memory linear pass followed by one deterministic sort; unit coverage uses 250 rows.

## Expired countdown archive

Room schema version 4 introduced `isArchived` and `keepVisibleWhenExpired`; Room v6 retains both fields. The complete v1-to-v6 migration never guesses that an existing row is archived.

On app startup, date/time/time-zone change, save, and data import, Athena archives only rows that satisfy every condition:

1. type is countdown;
2. its recurrence rule is `none`;
3. its date is before today;
4. it is not already archived;
5. the user has not restored it previously.

Restoring an archived row sets `keepVisibleWhenExpired`, keeping the still-expired row visible instead of immediately re-archiving it. Editing it to a future date, another type, or any repeating rule clears both archive flags. Archive entries can also be permanently deleted through the normal confirmation dialog.

JSON backup version 4 preserves both archive fields, recurrence, reminders, lunar fields, tags, timed events, and external identities. JSON versions 1–3 remain supported. ICS deliberately omits archive state because it is Athena application state rather than an iCalendar event concept.

## Appearance and settings

The Settings sheet replaces the former theme-heavy top menu and groups appearance, reminder status, archive, data management, and widget discovery.

- Display mode supports system, light, and dark.
- Android 12 and newer can opt into Material 3 wallpaper-derived dynamic colors.
- When dynamic color is unavailable or disabled, all five Athena palettes remain available in both light and audited dark schemes.
- Status- and navigation-bar icon brightness follows the active appearance.
- Display mode, dynamic color, and palette are persisted in SharedPreferences and included in JSON version 4 backup.

Unit tests check key custom light/dark text pairs against a 4.5:1 contrast threshold. Compose instrumentation covers search retention across Activity recreation, settings discoverability, and archive restore/delete actions.
