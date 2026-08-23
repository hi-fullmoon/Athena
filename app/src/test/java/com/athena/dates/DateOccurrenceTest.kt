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
        repeatsYearly = repeats,
    )
}
