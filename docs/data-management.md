# Data management and interchange

Athena uses Android's Storage Access Framework (SAF) for JSON and ICS files. The user chooses one URI through the system picker; Athena does not request broad storage access. System-calendar import is a separate, one-time read flow described below.

## JSON full backup, version 4

The root has `schema: "athena-backup"` and `version: 4`. Imports accept versions 1–4. Version 4 adds optional event time/time-zone and stable external-source identity fields. UTF-8 input is limited to 10 MiB, 10,000 entries, 1,000 tags, and 32 reminders per entry. Parsing is strict: unknown or missing fields, wrong primitive types, invalid dates/zones, inconsistent lunar/Gregorian anchors, dangling tag references, duplicate IDs/configurations, or unsupported values are reported before any write is enabled.

```json
{
  "schema": "athena-backup",
  "version": 4,
  "exportedAt": "2026-08-25T00:00:00Z",
  "app": { "package": "com.athena.dates", "version": "1.0.0" },
  "settings": {
    "palette": "Ocean",
    "themeMode": "System",
    "dynamicColor": true
  },
  "tags": [
    { "id": "family", "name": "家人", "colorArgb": -10003239 }
  ],
  "entries": [
    {
      "id": "spring-festival",
      "title": "春节",
      "note": "团圆",
      "date": "2026-02-17",
      "eventTime": null,
      "eventTimeZone": null,
      "externalSource": null,
      "externalKey": null,
      "kind": "anniversary",
      "calendar": {
        "system": "chinese_lunar",
        "lunarYear": 2026,
        "lunarMonth": 1,
        "lunarDay": 1,
        "lunarLeapMonth": false
      },
      "recurrence": {
        "frequency": "yearly",
        "interval": 1,
        "endDate": null
      },
      "reminders": [
        { "id": "one-week", "daysBefore": 7, "time": "08:00" },
        { "id": "same-day", "daysBefore": 0, "time": "18:00" }
      ],
      "tagIds": ["family"],
      "isArchived": false,
      "keepVisibleWhenExpired": false
    }
  ]
}
```

Versions 1 and 2 are mapped to Gregorian dates; version 3 defaults the v4 fields to null. Legacy `repeatsYearly` becomes a yearly interval-1 rule, and an enabled legacy reminder becomes one stable reminder instance. Version 1 retains current display-mode/dynamic-color settings because those fields did not exist. Delivery claims and pending snoozes are operational state and are deliberately excluded from portable backup.

## Preview, duplicate recognition, and atomicity

Every import reports added, updated, duplicate, skipped, and error counts before applying:

1. Matching persistent entry ID is authoritative.
2. Otherwise Athena compares normalized title, canonical date, type, calendar system/lunar anchor, and recurrence rule.
3. A unique semantic match is updated or counted as unchanged; ambiguous local matches are skipped.
4. ICS `UID` and Calendar Provider `(calendarId,eventId)` pairs produce stable name-based UUIDs, so importing the same source again does not multiply records.
5. Completely duplicate reminder configurations `(daysBefore,time)` are collapsed or rejected; reminder IDs scope delivery dedupe.
6. Tags match by stable ID first and normalized name second. Safe merge retains an existing same-name tag's local color and reports that choice.

Safe merge is the default for every format. Full replacement is available only for a valid JSON backup and requires a second confirmation. Room writes entries, reminders, tags, joins, archive changes, and deletions in one transaction. Any constraint or write failure rolls everything back. Settings are written only after the database commits; final reminders and all widget instances are rebuilt afterward.

## ICS interoperability

Export uses RFC 5545 `VCALENDAR`/`VEVENT`, CRLF, and UTF-8-safe 75-octet folding.

| Athena data | ICS mapping |
| --- | --- |
| Canonical all-day date | `DTSTART;VALUE=DATE` |
| Timed event | local `DTSTART` with `TZID` |
| Title / note | `SUMMARY` / `DESCRIPTION` |
| Daily, weekly, monthly, yearly interval/end | `RRULE` with `FREQ`, `INTERVAL`, optional date `UNTIL` |
| Multiple reminders | one display `VALARM` per reminder |
| Tags | `CATEGORIES` plus exact `X-ATHENA-TAG` extensions |
| Lunar anchor and recurrence | Gregorian `DTSTART` plus exact `X-ATHENA-LUNAR-DATE` / recurrence extensions |
| Identity / type | `UID`, `X-ATHENA-ID`, `X-ATHENA-KIND` |
| Stable provider identity | `X-ATHENA-EXTERNAL` |

Generic ICS import accepts all-day and date-time starts, the supported RRULE subset, categories, and representable display alarms. Floating date-times are interpreted in the current device zone and reported. `RDATE`/`EXDATE`, recurrence fields that the model cannot express, and malformed events are skipped with a report. Unknown properties are reported rather than silently treated as imported. Other calendar apps can see the Gregorian occurrence of a lunar entry, but only Athena extensions preserve its lunar annual semantics exactly.

## Explicit Android provider transfers

Calendar import requests `READ_CALENDAR` only at its entry. Calendar export requests `READ_CALENDAR` for the duplicate preview and `WRITE_CALENDAR` for the confirmed write. The user selects source events or destination entries and a target writable calendar. Queries/writes run off the main thread. There is no observer, scheduled sync, account access, or automatic write-back.

- All-day events map directly to date-level schedules.
- Timed events retain local start time and IANA time zone. Location is appended to the note. Athena has no end-time/duration field, so the import preview reports that boundary and later system-calendar export uses a reported one-hour duration. Multi-day all-day events similarly report that only the start date is retained.
- Supported daily/weekly/monthly/yearly rules map to Athena recurrence. `COUNT`, multi-day weekly patterns, mismatched BY-fields, `RDATE`, and `EXDATE` are skipped and reported.
- Alert/default reminders within 0–365 days are converted to reminder instances. Unsupported methods or out-of-range reminders are not silently claimed as imported.
- Provider calendar/event IDs form a stable source ID. Export stores `CUSTOM_APP_PACKAGE` and an `athena://date/<id>` `CUSTOM_APP_URI`; repeated export updates the stable match instead of inserting endlessly. Import remains merge-only.
- Provider reminders are exported only when their offset is safely representable before event start. Lunar semantics, tags, archive state, after-start reminders, and other unsupported fields are reported.

Contacts birthday import requests `READ_CONTACTS` only after its purpose dialog. Its projection contains display name, birthday, and lookup key; it does not query phone numbers, messages, email, or other contact data. A lookup-key-derived identity maps each birthday to an annual anniversary and makes repeated imports idempotent.

## Verification coverage

JVM tests cover JSON v1/v3/v4 compatibility, timed JSON/ICS round trips, import planning, contacts/provider identities, export reminder mapping, tags, recurrence, and duplicate reminder configurations. Room instrumentation validates v1→v5, v4→v5, and v5→v6 migrations, normalized relations, snooze dedupe/cascade, delivery claims, archive rules, and replace rollback.
