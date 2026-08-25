package com.athena.dates

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class DateOccurrence(
    val entry: DateEntry,
    val date: LocalDate,
)

/** Returns the next occurrence on or after [reference], or null after the rule has ended. */
fun DateEntry.nextOccurrence(reference: LocalDate): LocalDate? {
    val earliest = maxOf(reference, date)
    val candidate = when (recurrence.frequency) {
        RepeatFrequency.None -> date.takeUnless { it.isBefore(earliest) }
        RepeatFrequency.Daily -> nextDayBasedOccurrence(earliest, recurrence.interval)
        RepeatFrequency.Weekly -> nextDayBasedOccurrence(earliest, recurrence.interval * 7)
        RepeatFrequency.Monthly -> nextMonthlyOccurrence(earliest, recurrence.interval)
        RepeatFrequency.Yearly -> if (calendarSystem == DateCalendarSystem.ChineseLunar) {
            nextLunarYearlyOccurrence(earliest)
        } else {
            nextGregorianYearlyOccurrence(earliest, recurrence.interval)
        }
    }
    return candidate?.takeIf { recurrence.endDate == null || !it.isAfter(recurrence.endDate) }
}

/** Whether this entry should appear on [candidate], including custom repeats. */
fun DateEntry.occursOn(candidate: LocalDate): Boolean =
    !candidate.isBefore(date) && nextOccurrence(candidate) == candidate

fun DateEntry.occurrenceInYear(year: Int): LocalDate = when (calendarSystem) {
    DateCalendarSystem.Gregorian -> gregorianOccurrenceInYear(year)
    DateCalendarSystem.ChineseLunar -> lunarOccurrenceInYear(checkNotNull(lunarDate), year)
        ?: error("农历 $year 年不存在该闰月")
}

private fun DateEntry.nextDayBasedOccurrence(earliest: LocalDate, intervalDays: Int): LocalDate {
    val elapsed = ChronoUnit.DAYS.between(date, earliest).coerceAtLeast(0)
    val steps = ceilDiv(elapsed, intervalDays.toLong())
    return date.plusDays(steps * intervalDays)
}

private fun DateEntry.nextMonthlyOccurrence(earliest: LocalDate, intervalMonths: Int): LocalDate {
    val anchorMonth = YearMonth.from(date)
    val earliestMonth = YearMonth.from(earliest)
    val elapsed = ChronoUnit.MONTHS.between(anchorMonth, earliestMonth).coerceAtLeast(0)
    var steps = ceilDiv(elapsed, intervalMonths.toLong())
    var month = anchorMonth.plusMonths(steps * intervalMonths)
    var candidate = month.atDay(minOf(date.dayOfMonth, month.lengthOfMonth()))
    if (candidate.isBefore(earliest)) {
        steps++
        month = anchorMonth.plusMonths(steps * intervalMonths)
        candidate = month.atDay(minOf(date.dayOfMonth, month.lengthOfMonth()))
    }
    return candidate
}

private fun DateEntry.nextGregorianYearlyOccurrence(earliest: LocalDate, intervalYears: Int): LocalDate {
    val elapsed = (earliest.year - date.year).coerceAtLeast(0)
    var steps = ceilDiv(elapsed.toLong(), intervalYears.toLong())
    var candidate = gregorianOccurrenceInYear(date.year + (steps * intervalYears).toInt())
    if (candidate.isBefore(earliest)) {
        steps++
        candidate = gregorianOccurrenceInYear(date.year + (steps * intervalYears).toInt())
    }
    return candidate
}

private fun DateEntry.gregorianOccurrenceInYear(year: Int): LocalDate =
    if (date.monthValue == 2 && date.dayOfMonth == 29 && !java.time.Year.isLeap(year.toLong())) {
        LocalDate.of(year, 2, 28)
    } else {
        LocalDate.of(year, date.monthValue, date.dayOfMonth)
    }

private fun DateEntry.nextLunarYearlyOccurrence(earliest: LocalDate): LocalDate? {
    val anchor = checkNotNull(lunarDate)
    val earliestLunarYear = runCatching { earliest.toLunarDateSpec().year }.getOrElse {
        if (earliest.isBefore(date)) anchor.year else LUNAR_MAX_YEAR + 1
    }
    var year = maxOf(anchor.year, earliestLunarYear)
    val remainder = (year - anchor.year) % recurrence.interval
    if (remainder != 0) year += recurrence.interval - remainder
    while (year <= LUNAR_MAX_YEAR) {
        val candidate = lunarOccurrenceInYear(anchor, year)
        if (candidate != null && !candidate.isBefore(earliest) && !candidate.isBefore(date)) return candidate
        year += recurrence.interval
    }
    return null
}

private fun ceilDiv(value: Long, divisor: Long): Long = if (value <= 0) 0 else (value + divisor - 1) / divisor
