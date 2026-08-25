package com.athena.dates

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class UpcomingDatesWidgetTest {
    @Test
    fun `widget selects exactly three nearest non archived occurrences`() {
        val today = LocalDate.of(2026, 8, 24)
        val entries = listOf(
            entry("four", "第四", today.plusDays(4)),
            entry("one", "第一", today.plusDays(1)),
            entry("today", "今天", today),
            entry("archived", "已归档", today.plusDays(2)).copy(isArchived = true),
            entry("three", "第三", today.plusDays(3)),
            entry("past", "已过期", today.minusDays(1)),
        )

        val result = upcomingWidgetItems(entries, today)

        assertEquals(listOf("today", "one", "three"), result.map(UpcomingWidgetItem::id))
        assertEquals(listOf("今天", "1 天后", "3 天后"), result.map(UpcomingWidgetItem::relativeLabel))
    }

    @Test
    fun `widget includes next annual leap occurrence but never expired one time rows`() {
        val today = LocalDate.of(2025, 3, 1)
        val annualLeap = entry("leap", "闰日", LocalDate.of(2024, 2, 29))
            .copy(recurrence = RecurrenceRule(RepeatFrequency.Yearly))
        val expired = entry("expired", "过期", today.minusDays(1))

        assertEquals(listOf("leap"), upcomingWidgetItems(listOf(expired, annualLeap), today).map(UpcomingWidgetItem::id))
        assertEquals(LocalDate.of(2026, 2, 28), upcomingWidgetItems(listOf(annualLeap), today).single().occurrenceDate)
    }

    @Test
    fun `widget uses generic monthly occurrence rather than legacy yearly flag`() {
        val today = LocalDate.of(2026, 2, 1)
        val monthly = entry("monthly", "月末", LocalDate.of(2026, 1, 31))
            .copy(recurrence = RecurrenceRule(RepeatFrequency.Monthly))

        assertEquals(LocalDate.of(2026, 2, 28), upcomingWidgetItems(listOf(monthly), today).single().occurrenceDate)
    }

    private fun entry(id: String, title: String, date: LocalDate) = DateEntry(
        id = id,
        title = title,
        note = "",
        date = date,
        kind = DateKind.Countdown,
    )
}
