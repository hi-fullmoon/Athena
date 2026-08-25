package com.athena.dates

import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

enum class DateKind(val storageKey: String, val label: String) {
    Anniversary("anniversary", "纪念日"),
    Countdown("countdown", "倒数日"),
    Schedule("schedule", "普通日程");

    companion object {
        fun fromStored(value: String): DateKind? = entries.firstOrNull {
            it.storageKey == value || it.name == value
        }
    }
}

enum class DateCalendarSystem(val storageKey: String, val label: String) {
    Gregorian("gregorian", "公历"),
    ChineseLunar("chinese_lunar", "中国农历");

    companion object {
        fun fromStored(value: String): DateCalendarSystem? = entries.firstOrNull {
            it.storageKey == value || it.name == value
        }
    }
}

data class LunarDateSpec(
    val year: Int,
    val month: Int,
    val day: Int,
    val isLeapMonth: Boolean = false,
) {
    init {
        require(year in LUNAR_SUPPORTED_YEARS) { "Lunar year must be in $LUNAR_SUPPORTED_YEARS" }
        require(month in 1..12) { "Lunar month must be in 1..12" }
        require(day in 1..30) { "Lunar day must be in 1..30" }
    }

    val signedMonth: Int get() = if (isLeapMonth) -month else month
}

enum class RepeatFrequency(val storageKey: String, val label: String) {
    None("none", "不重复"),
    Daily("daily", "天"),
    Weekly("weekly", "周"),
    Monthly("monthly", "月"),
    Yearly("yearly", "年");

    companion object {
        fun fromStored(value: String): RepeatFrequency? = entries.firstOrNull {
            it.storageKey == value || it.name == value
        }
    }
}

data class RecurrenceRule(
    val frequency: RepeatFrequency = RepeatFrequency.None,
    val interval: Int = 1,
    val endDate: LocalDate? = null,
) {
    init {
        require(interval in 1..99) { "Repeat interval must be in 1..99" }
        require(frequency != RepeatFrequency.None || interval == 1) {
            "A non-repeating rule cannot have a custom interval"
        }
        require(frequency != RepeatFrequency.None || endDate == null) {
            "A non-repeating rule cannot have an end date"
        }
    }

    val isRepeating: Boolean get() = frequency != RepeatFrequency.None

    fun displayLabel(): String = when {
        frequency == RepeatFrequency.None -> "不重复"
        interval == 1 -> when (frequency) {
            RepeatFrequency.Daily -> "每天"
            RepeatFrequency.Weekly -> "每周"
            RepeatFrequency.Monthly -> "每月"
            RepeatFrequency.Yearly -> "每年"
            RepeatFrequency.None -> "不重复"
        }
        else -> "每 $interval ${frequency.label}"
    } + (endDate?.let { " · 至 $it" } ?: "")
}

data class EntryReminder(
    val id: String = UUID.randomUUID().toString(),
    val daysBefore: Int = 0,
    val time: LocalTime = LocalTime.of(9, 0),
) {
    init {
        require(id.isNotBlank()) { "Reminder id cannot be blank" }
        require(daysBefore in REMINDER_DAYS_RANGE) {
            "Reminder lead time must be in $REMINDER_DAYS_RANGE"
        }
    }
}

data class DateTag(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorArgb: Int,
) {
    init {
        require(id.isNotBlank()) { "Tag id cannot be blank" }
        require(name.isNotBlank() && name.length <= 30) { "Tag name must contain 1..30 characters" }
    }
}

data class ExternalIdentity(
    val source: String,
    val key: String,
) {
    init {
        require(source.isNotBlank() && source.length <= 80) { "External source must contain 1..80 characters" }
        require(key.isNotBlank() && key.length <= 500) { "External key must contain 1..500 characters" }
    }
}

data class DateEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val note: String,
    /** Canonical Gregorian anchor used for ordering and one-time occurrences. */
    val date: LocalDate,
    /** Optional local start time for imported system events; null means an all-day date. */
    val eventTime: LocalTime? = null,
    val eventTimeZone: String? = null,
    val kind: DateKind,
    val calendarSystem: DateCalendarSystem = DateCalendarSystem.Gregorian,
    val lunarDate: LunarDateSpec? = null,
    val recurrence: RecurrenceRule = RecurrenceRule(),
    val reminders: List<EntryReminder> = emptyList(),
    val tags: List<DateTag> = emptyList(),
    val isArchived: Boolean = false,
    val keepVisibleWhenExpired: Boolean = false,
    /** Stable identity used only for explicit, idempotent provider transfers. */
    val externalIdentity: ExternalIdentity? = null,
) {
    init {
        require(id.isNotBlank()) { "Entry id cannot be blank" }
        require((calendarSystem == DateCalendarSystem.ChineseLunar) == (lunarDate != null)) {
            "Chinese-lunar entries must carry a lunar date and Gregorian entries must not"
        }
        require(
            lunarDate == null || (lunarDate.isValidLunarDate() && lunarDate.toSolarDate() == date),
        ) { "The lunar date must exist and match the canonical Gregorian anchor" }
        require(recurrence.endDate == null || !recurrence.endDate.isBefore(date)) {
            "Repeat end date cannot be before the anchor date"
        }
        require(eventTime != null || eventTimeZone == null) { "An event time zone requires an event time" }
        require(eventTimeZone == null || runCatching { java.time.ZoneId.of(eventTimeZone) }.isSuccess) {
            "Event time zone must be a valid ZoneId"
        }
        require(
            calendarSystem != DateCalendarSystem.ChineseLunar ||
                recurrence.frequency in setOf(RepeatFrequency.None, RepeatFrequency.Yearly),
        ) { "Chinese-lunar entries support non-repeating or yearly rules" }
        require(reminders.map(EntryReminder::id).distinct().size == reminders.size) {
            "Reminder ids must be unique within an entry"
        }
        require(reminders.size <= MAX_ENTRY_REMINDERS) {
            "An entry can contain at most $MAX_ENTRY_REMINDERS reminders"
        }
        require(reminders.map { it.daysBefore to it.time }.distinct().size == reminders.size) {
            "Completely duplicate reminders are not allowed"
        }
        require(tags.map(DateTag::id).distinct().size == tags.size) { "Tag ids must be unique within an entry" }
    }

    val repeatsYearly: Boolean
        get() = recurrence.frequency == RepeatFrequency.Yearly && recurrence.interval == 1 && recurrence.endDate == null
    val reminderEnabled: Boolean get() = reminders.isNotEmpty()
    val reminderDaysBefore: Int get() = reminders.firstOrNull()?.daysBefore ?: 0
    val reminderTime: LocalTime get() = reminders.firstOrNull()?.time ?: LocalTime.of(9, 0)
}

fun DateEntry.shouldAutoArchive(reference: LocalDate): Boolean =
    kind == DateKind.Countdown && recurrence.frequency == RepeatFrequency.None && date.isBefore(reference) &&
        !isArchived && !keepVisibleWhenExpired

fun DateEntry.isExpired(reference: LocalDate): Boolean = nextOccurrence(reference) == null

val REMINDER_LEAD_DAYS = listOf(0, 1, 3, 7)
val REMINDER_DAYS_RANGE = 0..365
const val MAX_ENTRY_REMINDERS = 32

val DEFAULT_TAG_COLORS = listOf(
    0xFF6757D9.toInt(),
    0xFF2F73A9.toInt(),
    0xFF3D725F.toInt(),
    0xFFA9502E.toInt(),
    0xFFA74466.toInt(),
    0xFF7B5A32.toInt(),
)
