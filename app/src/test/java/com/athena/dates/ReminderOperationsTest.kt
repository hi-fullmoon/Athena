package com.athena.dates

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReminderOperationsTest {
    @Test
    fun `save persists before scheduling`() = runBlocking {
        val events = mutableListOf<String>()
        val entry = entry("save")
        val repository = FakeRepository(events)
        val scheduler = FakeScheduler(events)

        saveEntryAndUpdateReminder(repository, scheduler, entry)

        assertEquals(listOf("upsert:save", "schedule:save"), events)
    }

    @Test
    fun `delete removes data before cancelling alarm`() = runBlocking {
        val events = mutableListOf<String>()
        val repository = FakeRepository(events)
        val scheduler = FakeScheduler(events)

        deleteEntryAndCancelReminder(repository, scheduler, "delete")

        assertEquals(listOf("delete:delete", "cancel:delete"), events)
    }

    @Test
    fun `saving with reminder switched off cancels the existing alarm`() = runBlocking {
        val events = mutableListOf<String>()
        val entry = entry("disabled").copy(reminders = emptyList())
        val repository = FakeRepository(events)
        val scheduler = FakeScheduler(events)

        saveEntryAndUpdateReminder(repository, scheduler, entry)

        assertEquals(listOf("upsert:disabled", "cancel:disabled"), events)
    }

    @Test
    fun `rebuild schedules every persisted entry`() = runBlocking {
        val events = mutableListOf<String>()
        val repository = FakeRepository(events, listOf(entry("one"), entry("two")))
        val scheduler = FakeScheduler(events)

        rebuildReminders(repository, scheduler)

        assertEquals(listOf("schedule:one", "schedule:two"), events)
    }

    private fun entry(id: String) = DateEntry(
        id = id,
        title = id,
        note = "",
        date = LocalDate.of(2026, 9, 10),
        kind = DateKind.Anniversary,
        reminders = listOf(EntryReminder()),
    )

    private class FakeRepository(
        private val events: MutableList<String>,
        initialEntries: List<DateEntry> = emptyList(),
    ) : DateEntryRepository {
        override val entries: Flow<List<DateEntry>> = flowOf(initialEntries)
        override val tags: Flow<List<DateTag>> = flowOf(emptyList())

        override suspend fun upsert(entry: DateEntry) {
            events += "upsert:${entry.id}"
        }

        override suspend fun delete(id: String) {
            events += "delete:$id"
        }

        override suspend fun archiveExpired(reference: LocalDate): Int = 0

        override suspend fun restoreArchived(id: String): Boolean = false
    }

    private class FakeScheduler(private val events: MutableList<String>) : ReminderScheduler {
        override fun schedule(entry: DateEntry) {
            events += "schedule:${entry.id}"
        }

        override fun cancel(entryId: String) {
            events += "cancel:$entryId"
        }
    }
}
