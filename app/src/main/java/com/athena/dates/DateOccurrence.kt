package com.athena.dates

import java.time.LocalDate
import java.time.MonthDay

data class DateOccurrence(
    val entry: DateEntry,
    val date: LocalDate,
)

/** Returns the next occurrence on or after [reference], or null for a finished one-time entry. */
fun DateEntry.nextOccurrence(reference: LocalDate): LocalDate? {
    if (!repeatsYearly) return date.takeUnless { it.isBefore(reference) }

    val firstCandidateYear = maxOf(reference.year, date.year)
    val firstCandidate = occurrenceInYear(firstCandidateYear)
    return if (firstCandidate.isBefore(reference) || firstCandidate.isBefore(date)) {
        occurrenceInYear(firstCandidateYear + 1)
    } else {
        firstCandidate
    }
}

/** Whether this entry should appear on [candidate], including annual repeats. */
fun DateEntry.occursOn(candidate: LocalDate): Boolean {
    if (candidate.isBefore(date)) return false
    return if (repeatsYearly) occurrenceInYear(candidate.year) == candidate else date == candidate
}

fun DateEntry.occurrenceInYear(year: Int): LocalDate = MonthDay.from(date).atYear(year)

