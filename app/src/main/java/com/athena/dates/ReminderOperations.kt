package com.athena.dates

import kotlinx.coroutines.flow.first

internal suspend fun saveEntryAndUpdateReminder(
    repository: DateEntryRepository,
    scheduler: ReminderScheduler,
    entry: DateEntry,
) {
    repository.upsert(entry)
    if (entry.reminderEnabled) scheduler.schedule(entry) else scheduler.cancel(entry.id)
}

internal suspend fun deleteEntryAndCancelReminder(
    repository: DateEntryRepository,
    scheduler: ReminderScheduler,
    entryId: String,
) {
    repository.delete(entryId)
    scheduler.cancel(entryId)
}

internal suspend fun rebuildReminders(
    repository: DateEntryRepository,
    scheduler: ReminderScheduler,
) {
    repository.entries.first().forEach(scheduler::schedule)
}
