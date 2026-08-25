package com.athena.dates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class SystemIntegrationTest {
    @Test
    fun `contact lookup key produces stable annual anniversary including yearless leap birthday`() {
        val first = contactBirthdayCandidate("lookup-42", "阿澄", "--02-29")
        val repeated = contactBirthdayCandidate("lookup-42", "阿澄", "--02-29")

        assertEquals(first.entry.id, repeated.entry.id)
        assertEquals(LocalDate.of(2000, 2, 29), first.entry.date)
        assertEquals(RepeatFrequency.Yearly, first.entry.recurrence.frequency)
        assertEquals(
            ExternalIdentity(EXTERNAL_SOURCE_CONTACT_BIRTHDAY, "lookup-42:birthday"),
            first.entry.externalIdentity,
        )
        assertEquals(1, ImportPlanner.prepare(
            ParsedTransfer(TransferFormat.Contacts, listOf(repeated.entry)),
            listOf(first.entry),
        ).preview.counts.duplicates)
    }

    @Test
    fun `calendar export maps only reminders before event start and preserves supported recurrence`() {
        val entry = DateEntry(
            id = "timed",
            title = "评审",
            note = "",
            date = LocalDate.of(2026, 8, 25),
            eventTime = LocalTime.of(10, 0),
            eventTimeZone = "Asia/Shanghai",
            kind = DateKind.Schedule,
            recurrence = RecurrenceRule(RepeatFrequency.Monthly, interval = 2, endDate = LocalDate.of(2027, 8, 25)),
            reminders = listOf(
                EntryReminder("safe", 0, LocalTime.of(9, 30)),
                EntryReminder("after", 0, LocalTime.of(11, 0)),
                EntryReminder("day", 1, LocalTime.of(10, 0)),
            ),
        )

        assertEquals(listOf(30, 1_440), entry.providerReminderMinutes(ZoneId.of("Asia/Shanghai")))
        assertEquals("FREQ=MONTHLY;INTERVAL=2;UNTIL=20270825", entry.recurrence.toProviderRrule())
        assertTrue(exportWarnings(entry).any { it.contains("1 条") })
        assertTrue(exportWarnings(entry).any { it.contains("1 小时") })
    }

    @Test
    fun `widget filters by kind tag and configured count`() {
        val date = LocalDate.of(2026, 8, 25)
        val family = DateTag("family", "家人", DEFAULT_TAG_COLORS.first())
        val entries = listOf(
            DateEntry("a", "家人纪念", "", date, kind = DateKind.Anniversary, tags = listOf(family)),
            DateEntry("b", "工作纪念", "", date.plusDays(1), kind = DateKind.Anniversary),
            DateEntry("c", "家人倒数", "", date.plusDays(2), kind = DateKind.Countdown, tags = listOf(family)),
        )

        val result = upcomingWidgetItems(entries, date, 6, setOf(DateKind.Anniversary), setOf("family"))

        assertEquals(listOf("a"), result.map(UpcomingWidgetItem::id))
    }
}
