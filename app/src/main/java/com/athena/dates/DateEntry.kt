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

data class DateEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val note: String,
    val date: LocalDate,
    val kind: DateKind,
    val repeatsYearly: Boolean = false,
    val reminderEnabled: Boolean = false,
    val reminderDaysBefore: Int = 0,
    val reminderTime: LocalTime = LocalTime.of(9, 0),
) {
    init {
        require(reminderDaysBefore in 0..365) { "Reminder lead time must be between 0 and 365 days" }
    }
}
