package com.athena.dates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EntryQueryTest {
    private val today = LocalDate.of(2026, 8, 24)

    @Test
    fun `search matches title and note using every term`() {
        val entries = listOf(
            entry("a", "产品 发布", note = "北京"),
            entry("b", "北京出差", note = "产品评审"),
            entry("c", "个人安排", note = "上海"),
        )

        val result = filterAndSortEntries(entries, today, EntryQuery(search = "产品 北京"))

        assertEquals(listOf("a", "b"), result.map(DateEntry::id))
    }

    @Test
    fun `filters type expiration repeat reminder and archived independently`() {
        val activeRepeated = entry("repeat", "年度", kind = DateKind.Countdown, date = today.minusYears(1))
            .copy(
                recurrence = RecurrenceRule(RepeatFrequency.Yearly),
                reminders = listOf(EntryReminder()),
            )
        val expired = entry("expired", "过期", kind = DateKind.Countdown, date = today.minusDays(1))
        val archived = expired.copy(id = "archived", isArchived = true)
        val schedule = entry("schedule", "日程", kind = DateKind.Schedule, date = today.plusDays(2))

        val result = filterAndSortEntries(
            listOf(activeRepeated, expired, archived, schedule),
            today,
            EntryQuery(
                kinds = setOf(DateKind.Countdown),
                status = EntryStatusFilter.Active,
                yearlyRepeat = BooleanEntryFilter.Yes,
                reminder = BooleanEntryFilter.Yes,
            ),
        )

        assertEquals(listOf(activeRepeated), result)
        assertFalse(result.any(DateEntry::isArchived))
    }

    @Test
    fun `sort remains deterministic for more than one hundred entries`() {
        val entries = (0 until 250).map { index ->
            entry(
                id = index.toString(),
                title = "名称 ${250 - index}",
                date = today.plusDays((index % 37).toLong()),
            )
        }

        val byDate = filterAndSortEntries(entries, today, EntryQuery(sort = EntrySort.NextOccurrence))
        val byName = filterAndSortEntries(entries, today, EntryQuery(sort = EntrySort.Name))

        assertEquals(250, byDate.size)
        assertTrue(byDate.zipWithNext().all { (a, b) ->
            checkNotNull(a.nextOccurrence(today)) <= checkNotNull(b.nextOccurrence(today))
        })
        assertEquals(byName, filterAndSortEntries(entries, today, EntryQuery(sort = EntrySort.Name)))
    }

    @Test
    fun `tag filter matches any selected reusable tag`() {
        val family = DateTag("family", "家人", DEFAULT_TAG_COLORS[0])
        val work = DateTag("work", "工作", DEFAULT_TAG_COLORS[1])
        val entries = listOf(
            entry("a", "家宴").copy(tags = listOf(family)),
            entry("b", "评审").copy(tags = listOf(work)),
            entry("c", "无标签"),
        )

        assertEquals(
            listOf("a", "b"),
            filterAndSortEntries(entries, today, EntryQuery(tagIds = setOf("family", "work"))).map(DateEntry::id),
        )
    }

    @Test
    fun `only expired one time countdown is auto archivable and restore exemption is respected`() {
        assertTrue(entry("past", "过去", DateKind.Countdown, today.minusDays(1)).shouldAutoArchive(today))
        assertFalse(
            entry("annual", "年度", DateKind.Countdown, today.minusYears(1))
                .copy(recurrence = RecurrenceRule(RepeatFrequency.Yearly)).shouldAutoArchive(today),
        )
        assertFalse(entry("schedule", "日程", DateKind.Schedule, today.minusDays(1)).shouldAutoArchive(today))
        assertFalse(entry("restored", "恢复", DateKind.Countdown, today.minusDays(1)).copy(keepVisibleWhenExpired = true).shouldAutoArchive(today))
    }

    private fun entry(
        id: String,
        title: String,
        kind: DateKind = DateKind.Anniversary,
        date: LocalDate = today.plusDays(1),
        note: String = "",
    ) = DateEntry(id = id, title = title, note = note, date = date, kind = kind)
}
