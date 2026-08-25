package com.athena.dates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class CalendarProviderImportTest {
    private val anchor = LocalDate.of(2026, 8, 25)

    @Test
    fun `provider rule maps supported interval and inclusive until`() {
        assertEquals(
            RecurrenceRule(RepeatFrequency.Weekly, 2, LocalDate.of(2026, 12, 31)),
            parseCalendarRecurrence("FREQ=WEEKLY;INTERVAL=2;BYDAY=TU;UNTIL=20261231", anchor),
        )
    }

    @Test
    fun `provider rule rejects count exclusions and multi-day weekly recurrence`() {
        assertThrows(IllegalArgumentException::class.java) {
            parseCalendarRecurrence("FREQ=DAILY;COUNT=10", anchor)
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseCalendarRecurrence("FREQ=WEEKLY;BYDAY=MO,TU", anchor)
        }
    }

    @Test
    fun `calendar transfer uses merge-only preview and stable source ids dedupe`() {
        val entry = DateEntry(
            id = "stable-provider-id",
            title = "会议",
            note = "系统日历时间：09:00",
            date = anchor,
            kind = DateKind.Schedule,
        )
        val candidate = CalendarImportCandidate(
            sourceId = "1:2",
            calendarId = 1,
            calendarName = "工作",
            entry = entry,
            isAllDay = false,
            originalTime = java.time.LocalTime.of(9, 0),
            warnings = listOf("定时事件按日期级普通日程导入"),
        )
        val load = CalendarCandidateLoad(listOf(candidate), emptyList(), 0)
        val parsed = prepareCalendarTransfer(listOf(candidate), load)
        val first = ImportPlanner.prepare(parsed, emptyList())
        val repeated = ImportPlanner.prepare(parsed, listOf(entry))

        assertEquals(1, first.preview.counts.added)
        assertTrue(!first.preview.canReplace)
        assertEquals(1, repeated.preview.counts.duplicates)
    }

    @Test
    fun `timed provider event maps time zone note and reminder explicitly`() {
        val candidate = CalendarEventRecord(
            eventId = 8,
            calendarId = 2,
            title = "评审",
            description = "方案",
            startMillis = java.time.Instant.parse("2026-08-25T01:30:00Z").toEpochMilli(),
            allDay = false,
            eventTimeZone = "Asia/Shanghai",
            rrule = null,
            rdate = null,
            exdate = null,
            location = "会议室",
        ).toCandidate("工作", listOf(15), ZoneId.of("UTC"))

        assertEquals(anchor, candidate.entry.date)
        assertEquals(LocalTime.of(9, 30), candidate.originalTime)
        assertEquals(EntryReminder(candidate.entry.reminders.single().id, 0, LocalTime.of(9, 15)), candidate.entry.reminders.single())
        assertEquals(LocalTime.of(9, 30), candidate.entry.eventTime)
        assertEquals("Asia/Shanghai", candidate.entry.eventTimeZone)
        assertEquals(ExternalIdentity(EXTERNAL_SOURCE_CALENDAR, "2:8"), candidate.entry.externalIdentity)
        assertTrue(candidate.entry.note.contains("地点：会议室"))
        assertTrue(candidate.warnings.none { it.contains("定时事件") })
    }

    @Test
    fun `provider duration and multi day semantics are never silently dropped`() {
        val timed = CalendarEventRecord(
            eventId = 80,
            calendarId = 2,
            title = "长会议",
            description = "",
            startMillis = java.time.Instant.parse("2026-08-25T01:30:00Z").toEpochMilli(),
            endMillis = java.time.Instant.parse("2026-08-25T03:30:00Z").toEpochMilli(),
            allDay = false,
            eventTimeZone = "Asia/Shanghai",
            rrule = null,
            rdate = null,
            exdate = null,
            location = "",
        ).toCandidate("工作", emptyList(), ZoneId.of("UTC"))
        val multiDay = CalendarEventRecord(
            eventId = 81,
            calendarId = 2,
            title = "多日",
            description = "",
            startMillis = java.time.Instant.parse("2026-08-25T00:00:00Z").toEpochMilli(),
            duration = "P3D",
            allDay = true,
            eventTimeZone = "UTC",
            rrule = null,
            rdate = null,
            exdate = null,
            location = "",
        ).toCandidate("工作", emptyList(), ZoneId.of("UTC"))

        assertTrue(timed.warnings.any { it.contains("持续时长") })
        assertTrue(multiDay.warnings.any { it.contains("多日") })
    }

    @Test
    fun `all day reminder minutes map to previous local date time`() {
        val candidate = CalendarEventRecord(
            eventId = 9,
            calendarId = 2,
            title = "全天",
            description = "",
            startMillis = java.time.Instant.parse("2026-08-25T00:00:00Z").toEpochMilli(),
            allDay = true,
            eventTimeZone = "UTC",
            rrule = null,
            rdate = null,
            exdate = null,
            location = "",
        ).toCandidate("工作", listOf(15), ZoneId.of("Asia/Shanghai"))

        assertEquals(anchor, candidate.entry.date)
        assertEquals(1, candidate.entry.reminders.single().daysBefore)
        assertEquals(LocalTime.of(23, 45), candidate.entry.reminders.single().time)
    }
}
