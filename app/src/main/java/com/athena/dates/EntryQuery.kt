package com.athena.dates

import java.time.LocalDate
import java.util.Locale

enum class EntryStatusFilter(val label: String) {
    All("全部状态"),
    Active("有效"),
    Expired("已过期"),
}

enum class BooleanEntryFilter(val label: String) {
    All("全部"),
    Yes("是"),
    No("否"),
}

enum class EntrySort(val label: String) {
    NextOccurrence("下次发生日期"),
    Name("名称"),
}

data class EntryQuery(
    val search: String = "",
    val kinds: Set<DateKind> = DateKind.entries.toSet(),
    val status: EntryStatusFilter = EntryStatusFilter.All,
    val yearlyRepeat: BooleanEntryFilter = BooleanEntryFilter.All,
    val reminder: BooleanEntryFilter = BooleanEntryFilter.All,
    val tagIds: Set<String> = emptySet(),
    val sort: EntrySort = EntrySort.NextOccurrence,
) {
    val activeFilterCount: Int
        get() = listOf(
            kinds != DateKind.entries.toSet(),
            status != EntryStatusFilter.All,
            yearlyRepeat != BooleanEntryFilter.All,
            reminder != BooleanEntryFilter.All,
            tagIds.isNotEmpty(),
        ).count { it }
}

fun filterAndSortEntries(
    entries: List<DateEntry>,
    reference: LocalDate,
    query: EntryQuery,
    includeArchived: Boolean = false,
): List<DateEntry> {
    val searchTerms = query.search.trim().lowercase(Locale.ROOT)
        .split(Regex("\\s+"))
        .filter(String::isNotEmpty)
    val filtered = entries.asSequence()
        .filter { includeArchived || !it.isArchived }
        .filter { it.kind in query.kinds }
        .filter { entry ->
            val text = "${entry.title}\n${entry.note}".lowercase(Locale.ROOT)
            searchTerms.all(text::contains)
        }
        .filter { entry ->
            when (query.status) {
                EntryStatusFilter.All -> true
                EntryStatusFilter.Active -> !entry.isExpired(reference)
                EntryStatusFilter.Expired -> entry.isExpired(reference)
            }
        }
        .filter { entry ->
            when (query.yearlyRepeat) {
                BooleanEntryFilter.All -> true
                BooleanEntryFilter.Yes -> entry.recurrence.frequency == RepeatFrequency.Yearly
                BooleanEntryFilter.No -> entry.recurrence.frequency != RepeatFrequency.Yearly
            }
        }
        .filter { entry ->
            when (query.reminder) {
                BooleanEntryFilter.All -> true
                BooleanEntryFilter.Yes -> entry.reminderEnabled
                BooleanEntryFilter.No -> !entry.reminderEnabled
            }
        }
        .filter { entry -> query.tagIds.isEmpty() || entry.tags.any { it.id in query.tagIds } }
        .toList()

    return when (query.sort) {
        EntrySort.NextOccurrence -> filtered.sortedWith(
            compareBy<DateEntry> { it.nextOccurrence(reference) == null }
                .thenBy { it.nextOccurrence(reference) ?: it.date }
                .thenBy { it.title.lowercase(Locale.ROOT) }
                .thenBy(DateEntry::id),
        )
        EntrySort.Name -> filtered.sortedWith(
            compareBy<DateEntry> { it.title.trim().lowercase(Locale.ROOT) }
                .thenBy { it.nextOccurrence(reference) ?: it.date }
                .thenBy(DateEntry::id),
        )
    }
}
