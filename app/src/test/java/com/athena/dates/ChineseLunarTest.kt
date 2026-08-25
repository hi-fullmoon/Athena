package com.athena.dates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChineseLunarTest {
    @Test
    fun `2026 spring festival converts in both directions`() {
        val lunarNewYear = LunarDateSpec(2026, 1, 1)

        assertEquals(LocalDate.of(2026, 2, 17), lunarNewYear.toSolarDate())
        assertEquals(lunarNewYear, LocalDate.of(2026, 2, 17).toLunarDateSpec())
    }

    @Test
    fun `leap month is distinct and only selectable in years that contain it`() {
        val leapJune = LunarDateSpec(2025, 6, 1, isLeapMonth = true)

        assertTrue(leapJune.isValidLunarDate())
        assertEquals(leapJune, leapJune.toSolarDate().toLunarDateSpec())
        assertFalse(LunarDateSpec(2026, 6, 1, isLeapMonth = true).isValidLunarDate())
    }

    @Test
    fun `lunar annual recurrence crosses Gregorian year boundary`() {
        val anchor = LunarDateSpec(2025, 12, 29)
        val entry = DateEntry(
            title = "除夕",
            note = "",
            date = anchor.toSolarDate(),
            kind = DateKind.Anniversary,
            calendarSystem = DateCalendarSystem.ChineseLunar,
            lunarDate = anchor,
            recurrence = RecurrenceRule(RepeatFrequency.Yearly),
        )

        assertEquals(LocalDate.of(2026, 2, 16), entry.nextOccurrence(LocalDate.of(2026, 1, 1)))
        assertEquals(LocalDate.of(2027, 2, 5), entry.nextOccurrence(LocalDate.of(2026, 2, 17)))
    }

    @Test
    fun `annual leap month skips years without selected leap month`() {
        val anchor = LunarDateSpec(2025, 6, 1, isLeapMonth = true)
        val entry = DateEntry(
            title = "闰六月",
            note = "",
            date = anchor.toSolarDate(),
            kind = DateKind.Anniversary,
            calendarSystem = DateCalendarSystem.ChineseLunar,
            lunarDate = anchor,
            recurrence = RecurrenceRule(RepeatFrequency.Yearly),
        )

        val next = entry.nextOccurrence(anchor.toSolarDate().plusDays(1))
        assertTrue(next == null || checkNotNull(next.toLunarDateSpec()).isLeapMonth)
        assertTrue(next == null || next.toLunarDateSpec().month == 6)
    }

    @Test
    fun `lunar day thirty clamps when target month has only twenty nine days`() {
        val pair = (LUNAR_MIN_YEAR until LUNAR_MAX_YEAR).firstNotNullOf { year ->
            lunarMonthsInYear(year).firstNotNullOfOrNull { month ->
                val next = lunarMonthsInYear(year + 1).firstOrNull { it.signedMonth == month.signedMonth }
                if (!month.isLeapMonth && month.dayCount == 30 && next?.dayCount == 29) year to month.month else null
            }
        }
        val anchor = LunarDateSpec(pair.first, pair.second, 30)
        val entry = DateEntry(
            title = "月末",
            note = "",
            date = anchor.toSolarDate(),
            kind = DateKind.Anniversary,
            calendarSystem = DateCalendarSystem.ChineseLunar,
            lunarDate = anchor,
            recurrence = RecurrenceRule(RepeatFrequency.Yearly),
        )

        val occurrence = checkNotNull(entry.nextOccurrence(anchor.toSolarDate().plusDays(1)))
        assertEquals(pair.first + 1, occurrence.toLunarDateSpec().year)
        assertEquals(29, occurrence.toLunarDateSpec().day)
    }

    @Test
    fun `supported range is enforced and recurrence stops at upper boundary`() {
        val upper = LunarDateSpec(LUNAR_MAX_YEAR, 1, 1)
        val entry = DateEntry(
            title = "边界",
            note = "",
            date = upper.toSolarDate(),
            kind = DateKind.Anniversary,
            calendarSystem = DateCalendarSystem.ChineseLunar,
            lunarDate = upper,
            recurrence = RecurrenceRule(RepeatFrequency.Yearly),
        )

        assertEquals(upper.toSolarDate(), entry.nextOccurrence(upper.toSolarDate()))
        assertNull(entry.nextOccurrence(upper.toSolarDate().plusDays(1)))
    }
}
