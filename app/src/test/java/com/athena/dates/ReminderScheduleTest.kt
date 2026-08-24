package com.athena.dates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderScheduleTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `one-time reminder applies lead days and time`() {
        val entry = entry(date = LocalDate.of(2026, 9, 10), daysBefore = 3, time = LocalTime.of(8, 30))
        val now = ZonedDateTime.of(2026, 9, 1, 12, 0, 0, 0, zone)

        assertEquals(
            ZonedDateTime.of(2026, 9, 7, 8, 30, 0, 0, zone),
            nextReminderAt(entry, now),
        )
    }

    @Test
    fun `expired annual reminder advances to next occurrence`() {
        val entry = entry(date = LocalDate.of(2020, 2, 29), repeats = true, daysBefore = 1)
        val now = ZonedDateTime.of(2025, 2, 28, 10, 0, 0, 0, zone)

        assertEquals(
            ZonedDateTime.of(2026, 2, 27, 9, 0, 0, 0, zone),
            nextReminderAt(entry, now),
        )
    }

    @Test
    fun `disabled or elapsed one-time reminder is not scheduled`() {
        val elapsed = entry(date = LocalDate.of(2026, 1, 1))
        val now = ZonedDateTime.of(2026, 1, 2, 0, 0, 0, 0, zone)

        assertNull(nextReminderAt(elapsed, now))
        assertNull(nextReminderAt(elapsed.copy(reminderEnabled = false), now.minusYears(1)))
    }

    private fun entry(
        date: LocalDate,
        repeats: Boolean = false,
        daysBefore: Int = 0,
        time: LocalTime = LocalTime.of(9, 0),
    ) = DateEntry(
        id = "reminder",
        title = "提醒验证",
        note = "",
        date = date,
        kind = DateKind.Anniversary,
        repeatsYearly = repeats,
        reminderEnabled = true,
        reminderDaysBefore = daysBefore,
        reminderTime = time,
    )
}
