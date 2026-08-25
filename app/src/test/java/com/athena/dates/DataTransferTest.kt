package com.athena.dates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class DataTransferTest {
    @Test
    fun `version four JSON and ICS preserve lunar custom recurrence multiple reminders and tags`() {
        val lunar = LunarDateSpec(2025, 6, 1, isLeapMonth = true)
        val tag = DateTag("family", "家人", DEFAULT_TAG_COLORS[4])
        val original = DateEntry(
            id = "lunar-complete",
            title = "闰月纪念",
            note = "完整字段",
            date = lunar.toSolarDate(),
            kind = DateKind.Anniversary,
            calendarSystem = DateCalendarSystem.ChineseLunar,
            lunarDate = lunar,
            recurrence = RecurrenceRule(RepeatFrequency.Yearly, interval = 2, endDate = LocalDate.of(2099, 12, 31)),
            reminders = listOf(
                EntryReminder("r1", 7, LocalTime.of(8, 0)),
                EntryReminder("r2", 0, LocalTime.of(20, 30)),
            ),
            tags = listOf(tag),
        )

        val json = JsonBackupCodec.encode(
            BackupSnapshot(listOf(original), "Rose", Instant.EPOCH, "2.0", tags = listOf(tag)),
        )
        val jsonParsed = JsonBackupCodec.parse(json)
        val icsParsed = IcsCodec.parse(IcsCodec.encode(listOf(original), Instant.EPOCH))

        assertEquals(4, BACKUP_VERSION)
        assertEquals(
            original.copy(reminders = original.reminders.sortedWith(compareBy(EntryReminder::daysBefore, EntryReminder::time))),
            jsonParsed.entries.single(),
        )
        assertEquals(listOf(tag), jsonParsed.tags)
        assertEquals(listOf(original), icsParsed.entries)
    }

    @Test
    fun `JSON backup round trips every user field and settings`() {
        val original = entry(
            id = "leap-day",
            title = "闰日纪念",
            note = "第一行\n第二行",
            date = LocalDate.of(2024, 2, 29),
            repeats = true,
            reminderDays = 7,
            reminderTime = LocalTime.of(8, 35),
        )
        val archived = entry(
            id = "archived-countdown",
            title = "已归档倒数",
            date = LocalDate.of(2020, 1, 1),
        ).copy(kind = DateKind.Countdown, isArchived = true)

        val raw = JsonBackupCodec.encode(
            BackupSnapshot(
                entries = listOf(original, archived),
                paletteName = "Ocean",
                exportedAt = Instant.parse("2026-08-24T01:02:03Z"),
                appVersion = "1.2.3",
                themeMode = ThemeMode.Dark,
                dynamicColor = true,
            ),
        )
        val parsed = JsonBackupCodec.parse(raw)

        assertFalse(parsed.fatal)
        assertEquals("Ocean", parsed.paletteName)
        assertEquals(ThemeMode.Dark, parsed.themeMode)
        assertEquals(true, parsed.dynamicColor)
        assertEquals(mapOf(original.id to original, archived.id to archived), parsed.entries.associateBy(DateEntry::id))
        assertTrue(parsed.restoresSettings)
    }

    @Test
    fun `version four JSON and ICS preserve timed event and external identity`() {
        val original = entry("timed", "定时会议", date = LocalDate.of(2030, 1, 2)).copy(
            eventTime = LocalTime.of(14, 35),
            eventTimeZone = "Asia/Shanghai",
            externalIdentity = ExternalIdentity(EXTERNAL_SOURCE_CALENDAR, "9:88"),
            reminders = listOf(EntryReminder("before", 0, LocalTime.of(14, 5))),
        )

        val json = JsonBackupCodec.encode(BackupSnapshot(listOf(original), null, Instant.EPOCH, "3.0"))
        val ics = IcsCodec.encode(listOf(original), Instant.EPOCH)

        assertEquals(original, JsonBackupCodec.parse(json).entries.single())
        assertEquals(original, IcsCodec.parse(ics).entries.single())
        assertTrue(ics.contains("DTSTART;TZID=Asia/Shanghai:20300102T143500"))
    }

    @Test
    fun `version one JSON remains importable without overwriting newer appearance settings`() {
        val raw = """
            {
              "schema": "athena-backup",
              "version": 1,
              "exportedAt": "2026-08-24T01:02:03Z",
              "app": { "package": "com.athena.dates", "version": "1.0.0" },
              "settings": { "palette": "Sage" },
              "entries": [{
                "id": "v1", "title": "旧备份", "note": "", "date": "2026-08-25",
                "kind": "countdown", "repeatsYearly": false,
                "reminder": { "enabled": false, "daysBefore": 0, "time": "09:00" }
              }]
            }
        """.trimIndent()

        val parsed = JsonBackupCodec.parse(raw)

        assertFalse(parsed.fatal)
        assertEquals("Sage", parsed.paletteName)
        assertEquals(null, parsed.themeMode)
        assertEquals(null, parsed.dynamicColor)
        assertFalse(parsed.entries.single().isArchived)
    }

    @Test
    fun `version two JSON migrates archive appearance and legacy reminder`() {
        val raw = """
            {
              "schema": "athena-backup",
              "version": 2,
              "exportedAt": "2026-08-24T01:02:03Z",
              "app": { "package": "com.athena.dates", "version": "1.0.0" },
              "settings": { "palette": "Ocean", "themeMode": "Dark", "dynamicColor": true },
              "entries": [{
                "id": "v2", "title": "旧提醒", "note": "", "date": "2026-08-25",
                "kind": "countdown", "repeatsYearly": false,
                "isArchived": false, "keepVisibleWhenExpired": false,
                "reminder": { "enabled": true, "daysBefore": 3, "time": "08:30" }
              }]
            }
        """.trimIndent()

        val parsed = JsonBackupCodec.parse(raw)

        assertFalse(parsed.fatal)
        assertEquals(ThemeMode.Dark, parsed.themeMode)
        assertEquals(EntryReminder("v2:legacy-reminder", 3, LocalTime.of(8, 30)), parsed.entries.single().reminders.single())
    }

    @Test
    fun `JSON parser rejects unknown fields and quoted primitive values`() {
        val valid = JsonBackupCodec.encode(
            BackupSnapshot(listOf(entry("strict")), null, Instant.EPOCH, "1.0"),
        )

        val unknown = JsonBackupCodec.parse(valid.replace("\"entries\":", "\"unexpected\": 1, \"entries\":"))
        val quotedBoolean = JsonBackupCodec.parse(valid.replace("\"dynamicColor\": false", "\"dynamicColor\": \"false\""))

        assertTrue(unknown.fatal)
        assertEquals(1, unknown.issues.count { it.severity == IssueSeverity.Error })
        assertTrue(quotedBoolean.fatal)
        assertEquals(0, quotedBoolean.skipped)
    }

    @Test
    fun `planner uses id first then unique semantic identity`() {
        val existing = listOf(
            entry("same-id", title = "按 ID 更新", note = "旧"),
            entry("local-semantic", title = "  语义   相同 ", note = "旧", date = LocalDate.of(2030, 1, 1)),
            entry("exact", title = "完全相同"),
        )
        val incoming = listOf(
            entry("same-id", title = "按 ID 更新", note = "新"),
            entry("external-id", title = "语义 相同", note = "新", date = LocalDate.of(2030, 1, 1)),
            existing.last(),
            entry("new", title = "新增"),
        )

        val prepared = ImportPlanner.prepare(ParsedTransfer(TransferFormat.Ics, incoming), existing)

        assertEquals(ImportCounts(added = 1, updated = 2, duplicates = 1), prepared.preview.counts)
        assertEquals(setOf("same-id", "local-semantic", "new"), prepared.mergeEntries.mapTo(mutableSetOf(), DateEntry::id))
    }

    @Test
    fun `replacement preserves distinct source ids even when semantics match`() {
        val first = entry("first", title = "同一天", note = "A")
        val second = entry("second", title = "同一天", note = "B")

        val prepared = ImportPlanner.prepare(
            ParsedTransfer(TransferFormat.Json, listOf(first, second), restoresSettings = true),
            emptyList(),
        )

        assertTrue(prepared.preview.canReplace)
        assertEquals(listOf(first, second), prepared.replacementEntries)
    }

    @Test
    fun `replacement keeps backup tag ids while merge reuses local same-name tag`() {
        val localTag = DateTag("local-family", "家人", DEFAULT_TAG_COLORS[0])
        val backupTag = DateTag("backup-family", "家人", DEFAULT_TAG_COLORS[1])
        val incoming = entry("tagged").copy(tags = listOf(backupTag))
        val prepared = ImportPlanner.prepare(
            ParsedTransfer(TransferFormat.Json, listOf(incoming), tags = listOf(backupTag), restoresSettings = true),
            DateDataSnapshot(entries = emptyList(), tags = listOf(localTag)),
        )

        assertEquals(listOf(localTag), prepared.mergeEntries.single().tags)
        assertEquals(listOf(backupTag), prepared.replacementEntries.single().tags)
        assertEquals(listOf(backupTag), prepared.replacementTags)
    }

    @Test
    fun `conflicting duplicate ids make JSON restore inapplicable`() {
        val prepared = ImportPlanner.prepare(
            ParsedTransfer(
                TransferFormat.Json,
                listOf(entry("duplicate", note = "A"), entry("duplicate", note = "B")),
            ),
            emptyList(),
        )

        assertFalse(prepared.preview.canApply)
        assertEquals(1, prepared.preview.counts.errors)
        assertEquals(1, prepared.preview.counts.skipped)
    }

    @Test
    fun `ICS round trip preserves all day leap date yearly rule notes kinds and reminders`() {
        val original = entry(
            id = "ics-id",
            title = "跨平台，纪念日",
            note = "带分号；and comma,\n第二行",
            date = LocalDate.of(2024, 2, 29),
            repeats = true,
            reminderDays = 3,
            reminderTime = LocalTime.of(7, 45),
        )

        val raw = IcsCodec.encode(listOf(original), Instant.parse("2026-08-24T01:02:03Z"))
        val parsed = IcsCodec.parse(raw)

        assertFalse(parsed.fatal)
        assertEquals(listOf(original), parsed.entries)
        assertTrue("ICS lines must be folded at 75 octets", raw.lineSequence().all { it.toByteArray().size <= 75 })
    }

    @Test
    fun `ICS imports standard display alarm relative to all day start`() {
        val parsed = IcsCodec.parse(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:external@example.com
            DTSTART;VALUE=DATE:20301231
            SUMMARY:跨年
            BEGIN:VALARM
            ACTION:DISPLAY
            DESCRIPTION:提醒
            TRIGGER;RELATED=START:-P2DT15H30M
            END:VALARM
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
        )

        val entry = parsed.entries.single()
        assertTrue(entry.reminderEnabled)
        assertEquals(3, entry.reminderDaysBefore)
        assertEquals(LocalTime.of(8, 30), entry.reminderTime)
    }

    @Test
    fun `ICS imports timed and monthly events without dropping time zone`() {
        val parsed = IcsCodec.parse(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:timed
            DTSTART:20300101T090000Z
            SUMMARY:定时事件
            END:VEVENT
            BEGIN:VEVENT
            UID:monthly
            DTSTART;VALUE=DATE:20300102
            SUMMARY:每月
            RRULE:FREQ=MONTHLY
            END:VEVENT
            END:VCALENDAR
            """.trimIndent(),
        )

        assertFalse(parsed.fatal)
        assertEquals(2, parsed.entries.size)
        assertEquals(LocalTime.of(9, 0), parsed.entries.first().eventTime)
        assertEquals("UTC", parsed.entries.first().eventTimeZone)
        assertEquals(RepeatFrequency.Monthly, parsed.entries.last().recurrence.frequency)
        assertEquals(0, parsed.skipped)
        assertTrue(parsed.issues.isEmpty())
    }

    @Test
    fun `stable external UID prevents repeated ICS import duplicates`() {
        val raw = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:stable@example.com
            DTSTART;VALUE=DATE:20300520
            SUMMARY:稳定 ID
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val first = IcsCodec.parse(raw).entries.single()

        val repeated = ImportPlanner.prepare(IcsCodec.parse(raw), listOf(first))

        assertEquals(1, repeated.preview.counts.duplicates)
        assertTrue(repeated.mergeEntries.isEmpty())
    }

    private fun entry(
        id: String,
        title: String = "标题",
        note: String = "备注",
        date: LocalDate = LocalDate.of(2028, 8, 24),
        repeats: Boolean = false,
        reminderDays: Int = 0,
        reminderTime: LocalTime = LocalTime.of(9, 0),
    ) = DateEntry(
        id = id,
        title = title,
        note = note,
        date = date,
        kind = DateKind.Anniversary,
        recurrence = if (repeats) RecurrenceRule(RepeatFrequency.Yearly) else RecurrenceRule(),
        reminders = listOf(EntryReminder("$id-reminder", reminderDays, reminderTime)),
    )
}
