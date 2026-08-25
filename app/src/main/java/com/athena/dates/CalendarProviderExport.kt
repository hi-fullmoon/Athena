package com.athena.dates

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class WritableDeviceCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String,
)

data class CalendarExportPreview(
    val target: WritableDeviceCalendar,
    val entries: List<DateEntry>,
    val counts: ImportCounts,
    val issues: List<ImportIssue>,
    val canApply: Boolean,
)

data class CalendarExportCompletion(
    val counts: ImportCounts,
    val issues: List<ImportIssue>,
)

internal class CalendarProviderWriter(
    private val resolver: ContentResolver,
    private val packageName: String,
    private val defaultZone: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun writableCalendars(): List<WritableDeviceCalendar> = withContext(Dispatchers.IO) {
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
            ),
            "${CalendarContract.Calendars.VISIBLE}=1 AND ${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=?",
            arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
            "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} COLLATE NOCASE",
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        WritableDeviceCalendar(
                            cursor.getLong(0),
                            cursor.getString(1)?.takeIf(String::isNotBlank) ?: "未命名日历",
                            cursor.getString(2).orEmpty(),
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    suspend fun preview(target: WritableDeviceCalendar, entries: List<DateEntry>): CalendarExportPreview =
        withContext(Dispatchers.IO) {
            require(entries.isNotEmpty()) { "请至少选择一个日期" }
            require(entries.size <= MAX_CALENDAR_EXPORT_COUNT) { "一次最多导出 $MAX_CALENDAR_EXPORT_COUNT 条日期" }
            var added = 0
            var updated = 0
            var duplicates = 0
            val issues = mutableListOf<ImportIssue>()
            entries.forEach { entry ->
                issues += exportWarnings(entry).map { ImportIssue(IssueSeverity.Warning, "“${entry.title}”：$it") }
                val existing = findExistingEvent(target.id, entry)
                when {
                    existing == null -> added++
                    eventMatches(existing, entry) -> duplicates++
                    else -> updated++
                }
            }
            CalendarExportPreview(
                target,
                entries,
                ImportCounts(added = added, updated = updated, duplicates = duplicates),
                issues,
                canApply = true,
            )
        }

    suspend fun apply(preview: CalendarExportPreview): CalendarExportCompletion = withContext(Dispatchers.IO) {
        require(preview.canApply) { "导出预览不可应用" }
        val operations = arrayListOf<ContentProviderOperation>()
        preview.entries.forEach { entry ->
            val existing = findExistingEvent(preview.target.id, entry)
            val eventValues = entry.toCalendarValues(preview.target.id, packageName, defaultZone)
            val eventOperationIndex = operations.size
            if (existing == null) {
                operations += ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
                    .withValues(eventValues)
                    .build()
            } else {
                operations += ContentProviderOperation.newUpdate(
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existing.id),
                ).withValues(eventValues).build()
                operations += ContentProviderOperation.newDelete(CalendarContract.Reminders.CONTENT_URI)
                    .withSelection("${CalendarContract.Reminders.EVENT_ID}=?", arrayOf(existing.id.toString()))
                    .build()
            }
            entry.providerReminderMinutes(defaultZone).forEach { minutes ->
                val builder = ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI)
                    .withValue(CalendarContract.Reminders.MINUTES, minutes)
                    .withValue(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                if (existing == null) {
                    builder.withValueBackReference(CalendarContract.Reminders.EVENT_ID, eventOperationIndex)
                } else {
                    builder.withValue(CalendarContract.Reminders.EVENT_ID, existing.id)
                }
                operations += builder.build()
            }
        }
        if (operations.isNotEmpty()) resolver.applyBatch(CalendarContract.AUTHORITY, operations)
        CalendarExportCompletion(preview.counts, preview.issues)
    }

    private fun findExistingEvent(calendarId: Long, entry: DateEntry): ExistingCalendarEvent? {
        val sourceEventId = entry.externalIdentity
            ?.takeIf { it.source == EXTERNAL_SOURCE_CALENDAR }
            ?.key
            ?.split(':', limit = 2)
            ?.takeIf { parts -> parts.size == 2 && parts[0].toLongOrNull() == calendarId }
            ?.get(1)
            ?.toLongOrNull()
        if (sourceEventId != null) {
            queryExisting(
                "${CalendarContract.Events._ID}=? AND ${CalendarContract.Events.CALENDAR_ID}=? AND ${CalendarContract.Events.DELETED}=0",
                arrayOf(sourceEventId.toString(), calendarId.toString()),
            )?.let { return it }
        }
        return queryExisting(
            "${CalendarContract.Events.CALENDAR_ID}=? AND ${CalendarContract.Events.CUSTOM_APP_PACKAGE}=? AND " +
                "${CalendarContract.Events.CUSTOM_APP_URI}=? AND ${CalendarContract.Events.DELETED}=0",
            arrayOf(calendarId.toString(), packageName, entry.providerIdentityUri()),
        )
    }

    private fun queryExisting(selection: String, args: Array<String>): ExistingCalendarEvent? {
        return resolver.query(
            CalendarContract.Events.CONTENT_URI,
            EVENT_PROJECTION,
            selection,
            args,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) null else ExistingCalendarEvent(
                id = cursor.getLong(0),
                title = cursor.getString(1).orEmpty(),
                description = cursor.getString(2).orEmpty(),
                startMillis = cursor.getLong(3),
                allDay = cursor.getInt(4) != 0,
                timeZone = cursor.getString(5),
                rrule = cursor.getString(6),
            )
        }
    }

    private fun eventMatches(existing: ExistingCalendarEvent, entry: DateEntry): Boolean {
        val values = entry.toCalendarValues(0, packageName, defaultZone)
        return existing.title == values.getAsString(CalendarContract.Events.TITLE) &&
            existing.description == values.getAsString(CalendarContract.Events.DESCRIPTION).orEmpty() &&
            existing.startMillis == values.getAsLong(CalendarContract.Events.DTSTART) &&
            existing.allDay == (values.getAsInteger(CalendarContract.Events.ALL_DAY) != 0) &&
            existing.timeZone == values.getAsString(CalendarContract.Events.EVENT_TIMEZONE) &&
            existing.rrule.orEmpty() == values.getAsString(CalendarContract.Events.RRULE).orEmpty()
    }
}

internal fun DateEntry.toCalendarValues(
    calendarId: Long,
    packageName: String,
    defaultZone: ZoneId,
): ContentValues = ContentValues().apply {
    val allDay = eventTime == null
    val zone = eventTimeZone?.let(ZoneId::of) ?: defaultZone
    val startMillis = if (allDay) {
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    } else {
        date.atTime(checkNotNull(eventTime)).atZone(zone).toInstant().toEpochMilli()
    }
    put(CalendarContract.Events.CALENDAR_ID, calendarId)
    put(CalendarContract.Events.TITLE, title)
    put(CalendarContract.Events.DESCRIPTION, note)
    put(CalendarContract.Events.DTSTART, startMillis)
    put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
    put(CalendarContract.Events.EVENT_TIMEZONE, if (allDay) "UTC" else zone.id)
    put(CalendarContract.Events.DURATION, "P1D".takeIf { allDay } ?: "PT1H")
    putNull(CalendarContract.Events.DTEND)
    recurrence.toProviderRrule()?.let { put(CalendarContract.Events.RRULE, it) }
        ?: putNull(CalendarContract.Events.RRULE)
    put(CalendarContract.Events.CUSTOM_APP_PACKAGE, packageName)
    put(CalendarContract.Events.CUSTOM_APP_URI, providerIdentityUri())
    put(CalendarContract.Events.HAS_ALARM, if (providerReminderMinutes(defaultZone).isEmpty()) 0 else 1)
}

internal fun RecurrenceRule.toProviderRrule(): String? {
    if (!isRepeating) return null
    val frequencyName = frequency.name.uppercase(Locale.ROOT)
    return buildString {
        append("FREQ=").append(frequencyName)
        if (interval != 1) append(";INTERVAL=").append(interval)
        endDate?.let { append(";UNTIL=").append(it.format(DateTimeFormatter.BASIC_ISO_DATE)) }
    }
}

internal fun DateEntry.providerReminderMinutes(defaultZone: ZoneId): List<Int> {
    val eventLocalTime = eventTime ?: java.time.LocalTime.MIDNIGHT
    return reminders.mapNotNull { reminder ->
        val eventStart = LocalDateTime.of(date, eventLocalTime)
        val trigger = LocalDateTime.of(date.minusDays(reminder.daysBefore.toLong()), reminder.time)
        ChronoUnit.MINUTES.between(trigger, eventStart).takeIf { it in 0..MAX_PROVIDER_REMINDER_MINUTES }?.toInt()
    }.distinct().sorted()
}

internal fun exportWarnings(entry: DateEntry): List<String> = buildList {
    val safeReminders = entry.providerReminderMinutes(ZoneId.systemDefault()).size
    if (safeReminders < entry.reminders.size) {
        add("${entry.reminders.size - safeReminders} 条发生在事件开始之后或超出系统范围的提醒未导出")
    }
    if (entry.calendarSystem == DateCalendarSystem.ChineseLunar) {
        add("系统日历不支持 Athena 农历语义，按当前公历锚点与重复规则导出")
    }
    if (entry.tags.isNotEmpty()) add("标签颜色不是系统日历事件字段，未导出")
    if (entry.isArchived) add("归档状态不是系统日历事件字段，未导出")
    if (entry.eventTime != null) add("Athena 不保存结束时间，系统日历中按 1 小时持续时长导出")
}

private fun DateEntry.providerIdentityUri(): String = "athena://date/$id"

private data class ExistingCalendarEvent(
    val id: Long,
    val title: String,
    val description: String,
    val startMillis: Long,
    val allDay: Boolean,
    val timeZone: String?,
    val rrule: String?,
)

private val EVENT_PROJECTION = arrayOf(
    CalendarContract.Events._ID,
    CalendarContract.Events.TITLE,
    CalendarContract.Events.DESCRIPTION,
    CalendarContract.Events.DTSTART,
    CalendarContract.Events.ALL_DAY,
    CalendarContract.Events.EVENT_TIMEZONE,
    CalendarContract.Events.RRULE,
)
private const val MAX_PROVIDER_REMINDER_MINUTES = 365L * 24L * 60L
private const val MAX_CALENDAR_EXPORT_COUNT = 500
