package com.athena.dates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DateOccurrenceTest {
    @Test
    fun `one-time entry has no next occurrence after it passes`() {
        val entry = entry(date = LocalDate.of(2026, 5, 10))

        assertEquals(LocalDate.of(2026, 5, 10), entry.nextOccurrence(LocalDate.of(2026, 5, 10)))
        assertNull(entry.nextOccurrence(LocalDate.of(2026, 5, 11)))
    }

    @Test
    fun `annual entry advances to the next year`() {
        val entry = entry(date = LocalDate.of(2020, 8, 20), repeats = true)

        assertEquals(LocalDate.of(2027, 8, 20), entry.nextOccurrence(LocalDate.of(2026, 8, 21)))
        assertTrue(entry.occursOn(LocalDate.of(2028, 8, 20)))
        assertFalse(entry.occursOn(LocalDate.of(2019, 8, 20)))
    }

    @Test
    fun `leap-day anniversary uses february 28 in non-leap years`() {
        val entry = entry(date = LocalDate.of(2024, 2, 29), repeats = true)

        assertEquals(LocalDate.of(2025, 2, 28), entry.nextOccurrence(LocalDate.of(2025, 1, 1)))
        assertTrue(entry.occursOn(LocalDate.of(2025, 2, 28)))
        assertEquals(LocalDate.of(2028, 2, 29), entry.nextOccurrence(LocalDate.of(2028, 1, 1)))
        assertEquals(LocalDate.of(2026, 2, 28), entry.nextOccurrence(LocalDate.of(2025, 3, 1)))
        assertFalse(entry.occursOn(LocalDate.of(2025, 3, 1)))
    }

    @Test
    fun `annual entry never occurs before its original date`() {
        val entry = entry(date = LocalDate.of(2030, 12, 31), repeats = true)

        assertEquals(LocalDate.of(2030, 12, 31), entry.nextOccurrence(LocalDate.of(2026, 1, 1)))
        assertFalse(entry.occursOn(LocalDate.of(2029, 12, 31)))
    }

    @Test
    fun `year end anniversary rolls across year boundary`() {
        val entry = entry(date = LocalDate.of(2020, 12, 31), repeats = true)

        assertEquals(LocalDate.of(2026, 12, 31), entry.nextOccurrence(LocalDate.of(2026, 12, 31)))
        assertEquals(LocalDate.of(2027, 12, 31), entry.nextOccurrence(LocalDate.of(2027, 1, 1)))
    }

    @Test
    fun `weekly custom interval and end date are inclusive`() {
        val entry = entry(LocalDate.of(2026, 1, 5)).copy(
            recurrence = RecurrenceRule(
                frequency = RepeatFrequency.Weekly,
                interval = 2,
                endDate = LocalDate.of(2026, 2, 2),
            ),
        )

        assertEquals(LocalDate.of(2026, 1, 19), entry.nextOccurrence(LocalDate.of(2026, 1, 6)))
        assertEquals(LocalDate.of(2026, 2, 2), entry.nextOccurrence(LocalDate.of(2026, 2, 2)))
        assertNull(entry.nextOccurrence(LocalDate.of(2026, 2, 3)))
    }

    @Test
    fun `monthly recurrence clamps month end without drifting`() {
        val entry = entry(LocalDate.of(2026, 1, 31)).copy(
            recurrence = RecurrenceRule(RepeatFrequency.Monthly),
        )

        assertEquals(LocalDate.of(2026, 2, 28), entry.nextOccurrence(LocalDate.of(2026, 2, 1)))
        assertEquals(LocalDate.of(2026, 3, 31), entry.nextOccurrence(LocalDate.of(2026, 3, 1)))
    }

    @Test
    fun `custom yearly interval stays anchored to original year`() {
        val entry = entry(LocalDate.of(2024, 2, 29)).copy(
            recurrence = RecurrenceRule(RepeatFrequency.Yearly, interval = 4),
        )

        assertEquals(LocalDate.of(2028, 2, 29), entry.nextOccurrence(LocalDate.of(2025, 1, 1)))
        assertFalse(entry.occursOn(LocalDate.of(2026, 2, 28)))
    }

    @Test
    fun `date kind accepts stable keys and legacy enum names`() {
        assertEquals(DateKind.Anniversary, DateKind.fromStored("anniversary"))
        assertEquals(DateKind.Anniversary, DateKind.fromStored("Anniversary"))
        assertNull(DateKind.fromStored("renamed-kind"))
    }

    private fun entry(date: LocalDate, repeats: Boolean = false) = DateEntry(
        title = "测试",
        note = "",
        date = date,
        kind = DateKind.Anniversary,
        recurrence = if (repeats) RecurrenceRule(RepeatFrequency.Yearly) else RecurrenceRule(),
    )
}
