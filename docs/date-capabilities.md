# Date capabilities

## Calendar systems

An entry stores a canonical Gregorian date for ordering and interoperability. Gregorian entries derive an informational Chinese-lunar label. Chinese-lunar entries additionally store lunar year/month/day and the leap-month flag; the canonical date must exactly match that lunar anchor.

Athena intentionally supports lunar years **1900 through 2100**, inclusive. The editor validates that range and exposes a leap-month option only when the selected lunar year contains it. A yearly lunar rule calculates occurrences in lunar years and skips years that do not contain the selected leap month. A day 30 anchor clamps to day 29 when that target lunar month is short.

Conversion uses `cn.6tail:lunar:1.7.7` (`lunar-java`), an MIT-licensed Java library with no transitive dependencies. It was selected to avoid maintaining a second calendar algorithm inside the app; Athena's explicit range and invariants remain app-owned.

## Recurrence rules

The model supports:

- no repeat;
- daily (used by interchange/provider input);
- weekly;
- monthly;
- yearly;
- interval 1–99 for repeating rules;
- optional inclusive end date.

The editor exposes no repeat, weekly, monthly, and yearly plus the custom interval/end controls. Chinese-lunar entries allow no repeat or yearly repeat because weekly/monthly Gregorian periods do not have an unambiguous lunar meaning.

Monthly anchors clamp to the target month's last day without drifting (January 31 → February 28 → March 31). Gregorian February 29 yearly anchors use February 28 in non-leap years. An ended rule returns no next occurrence. One-time expired countdowns alone are auto-archived; no repeating item is treated as expired solely because its anchor is in the past.

## Persistence migration

Room schema version 6 retains the v5 normalized relations and adds optional timed-event fields, stable external-source fields, and `reminder_snoozes` operational rows. The v5 normalized relations are:

- `date_entries` for the calendar anchor, recurrence, and archive state;
- `entry_reminders`, one row per reminder instance with cascade deletion and its own delivery key;
- `date_tags` for reusable name/color records;
- `date_entry_tags` for the extensible many-to-many assignment.

Migration 4→5 copies every legacy entry, maps `repeatsYearly`, and creates one stable `:legacy-reminder` row whenever the old reminder switch was enabled. Migrations 1→2→3→4→5 remain registered and are tested as a complete path.

Migration 5→6 uses additive nullable columns for `eventTime`, `eventTimeZone`, `externalSource`, and `externalKey`, so existing dates are unchanged. The snooze table uses `(entryId, reminderId, occurrenceDate)` as its key and cascades on entry deletion.
