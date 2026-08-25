package com.athena.dates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderScheduleTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `one-time reminder applies arbitrary lead days and time`() {
        val entry = entry(date = LocalDate.of(2026, 9, 10), daysBefore = 2, time = LocalTime.of(8, 30))
        val now = ZonedDateTime.of(2026, 9, 1, 12, 0, 0, 0, zone)

        assertEquals(ZonedDateTime.of(2026, 9, 8, 8, 30, 0, 0, zone), nextReminderAt(entry, now))
    }

    @Test
    fun `multiple reminder instances are scheduled independently`() {
        val occurrence = LocalDate.of(2026, 9, 10)
        val first = EntryReminder("first", 7, LocalTime.of(8, 0))
        val second = EntryReminder("second", 0, LocalTime.of(20, 0))
        val entry = baseEntry(occurrence).copy(reminders = listOf(first, second))
        val schedules = nextReminderSchedules(entry, ZonedDateTime.of(2026, 8, 1, 0, 0, 0, 0, zone))

        assertEquals(listOf("first", "second"), schedules.map { it.reminder.id })
        assertEquals(listOf(occurrence.minusDays(7), occurrence), schedules.map { it.triggerAt.toLocalDate() })
    }

    @Test
    fun `completely duplicate reminder configurations are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            baseEntry(LocalDate.of(2026, 9, 10)).copy(
                reminders = listOf(
                    EntryReminder("one", 3, LocalTime.of(9, 0)),
                    EntryReminder("two", 3, LocalTime.of(9, 0)),
                ),
            )
        }
    }

    @Test
    fun `annual reminder crossing year boundary targets correct occurrence`() {
        val entry = entry(LocalDate.of(2020, 1, 1), repeats = true, daysBefore = 7)
        val now = ZonedDateTime.of(2025, 12, 20, 12, 0, 0, 0, zone)
        val schedule = nextReminderSchedule(entry, now)

        assertEquals(LocalDate.of(2026, 1, 1), schedule?.occurrenceDate)
        assertEquals(ZonedDateTime.of(2025, 12, 25, 9, 0, 0, 0, zone), schedule?.triggerAt)
    }

    @Test
    fun `leap-day annual reminder clamps to February 28`() {
        val entry = entry(LocalDate.of(2024, 2, 29), repeats = true, daysBefore = 3)
        val now = ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, zone)
        val schedule = nextReminderSchedule(entry, now)

        assertEquals(LocalDate.of(2025, 2, 28), schedule?.occurrenceDate)
        assertEquals(ZonedDateTime.of(2025, 2, 25, 9, 0, 0, 0, zone), schedule?.triggerAt)
    }

    @Test
    fun `elapsed one-time or reminder-free entry is not scheduled`() {
        val elapsed = entry(LocalDate.of(2026, 1, 1))
        val now = ZonedDateTime.of(2026, 1, 2, 0, 0, 0, 0, zone)

        assertNull(nextReminderAt(elapsed, now))
        assertNull(nextReminderAt(elapsed.copy(reminders = emptyList()), now.minusYears(1)))
    }

    @Test
    fun `lead-day range is enforced`() {
        assertThrows(IllegalArgumentException::class.java) { EntryReminder(daysBefore = 366) }
    }

    @Test
    fun `occurrence validation rejects stale alarm dates and wrong reminder instance`() {
        val annual = entry(LocalDate.of(2024, 2, 29), repeats = true)
        val reminder = annual.reminders.single()

        assertNull(reminderScheduleForOccurrence(annual, reminder, LocalDate.of(2025, 3, 1), zone))
        assertNull(
            reminderScheduleForOccurrence(
                annual,
                reminder.copy(id = "removed"),
                LocalDate.of(2025, 2, 28),
                zone,
            ),
        )
    }

    @Test
    fun `rebuilding in a new time zone keeps selected local time`() {
        val entry = entry(LocalDate.of(2026, 9, 10), time = LocalTime.of(8, 30))
        val reminder = entry.reminders.single()
        val shanghai = reminderScheduleForOccurrence(entry, reminder, entry.date, ZoneId.of("Asia/Shanghai"))
        val tokyo = reminderScheduleForOccurrence(entry, reminder, entry.date, ZoneId.of("Asia/Tokyo"))

        assertEquals(LocalTime.of(8, 30), shanghai?.triggerAt?.toLocalTime())
        assertEquals(LocalTime.of(8, 30), tokyo?.triggerAt?.toLocalTime())
        assertNotEquals(shanghai?.triggerAt?.toInstant(), tokyo?.triggerAt?.toInstant())
    }

    private fun entry(
        date: LocalDate,
        repeats: Boolean = false,
        daysBefore: Int = 0,
        time: LocalTime = LocalTime.of(9, 0),
    ) = baseEntry(date).copy(
        recurrence = if (repeats) RecurrenceRule(RepeatFrequency.Yearly) else RecurrenceRule(),
        reminders = listOf(EntryReminder("reminder-instance", daysBefore, time)),
    )

    private fun baseEntry(date: LocalDate) = DateEntry(
        id = "entry",
        title = "提醒验证",
        note = "",
        date = date,
        kind = DateKind.Anniversary,
    )
}
