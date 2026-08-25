package com.athena.dates

import android.content.ContentResolver
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

data class DeviceCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val colorArgb: Int,
)

data class CalendarImportCandidate(
    val sourceId: String,
    val calendarId: Long,
    val calendarName: String,
    val entry: DateEntry,
    val isAllDay: Boolean,
    val originalTime: LocalTime?,
    val warnings: List<String>,
)

data class CalendarCandidateLoad(
    val candidates: List<CalendarImportCandidate>,
    val issues: List<ImportIssue>,
    val skipped: Int,
)

internal class CalendarProviderReader(
    private val resolver: ContentResolver,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun calendars(): List<DeviceCalendar> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
        )
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.VISIBLE}=1",
            null,
            "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} COLLATE NOCASE",
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        DeviceCalendar(
                            id = cursor.getLong(0),
                            displayName = cursor.getString(1)?.takeIf(String::isNotBlank) ?: "未命名日历",
                            accountName = cursor.getString(2).orEmpty(),
                            colorArgb = cursor.getInt(3),
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    suspend fun candidates(calendarIds: Set<Long>): CalendarCandidateLoad = withContext(Dispatchers.IO) {
        require(calendarIds.isNotEmpty()) { "请至少选择一个系统日历" }
        val calendarsById = calendars().associateBy(DeviceCalendar::id)
        val issues = mutableListOf<ImportIssue>()
        var skipped = 0
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.RDATE,
            CalendarContract.Events.EXDATE,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.HAS_ATTENDEE_DATA,
        )
        val placeholders = calendarIds.joinToString(",") { "?" }
        val loaded = mutableListOf<CalendarEventRecord>()
        resolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            "${CalendarContract.Events.CALENDAR_ID} IN ($placeholders) AND ${CalendarContract.Events.DELETED}=0",
            calendarIds.map(Long::toString).toTypedArray(),
            "${CalendarContract.Events.DTSTART}, ${CalendarContract.Events._ID}",
        )?.use { cursor ->
            var truncated = false
            while (cursor.moveToNext()) {
                if (loaded.size >= MAX_CALENDAR_EVENT_COUNT) {
                    truncated = true
                    break
                }
                loaded += CalendarEventRecord(
                    eventId = cursor.getLong(0),
                    calendarId = cursor.getLong(1),
                    title = cursor.getString(2).orEmpty(),
                    description = cursor.getString(3).orEmpty(),
                    startMillis = cursor.getLong(4),
                    endMillis = cursor.getLong(5).takeUnless { cursor.isNull(5) },
                    duration = cursor.getString(6),
                    allDay = cursor.getInt(7) != 0,
                    eventTimeZone = cursor.getString(8),
                    rrule = cursor.getString(9),
                    rdate = cursor.getString(10),
                    exdate = cursor.getString(11),
                    location = cursor.getString(12).orEmpty(),
                    hasAttendeeData = cursor.getInt(13) != 0,
                )
            }
            if (truncated) {
                skipped++
                issues += ImportIssue(IssueSeverity.Warning, "系统日历事件超过 $MAX_CALENDAR_EVENT_COUNT 条，仅加载前 $MAX_CALENDAR_EVENT_COUNT 条")
            }
        }
        val reminderLoad = loadReminders(loaded.map(CalendarEventRecord::eventId).toSet())
        val candidates = loaded.mapNotNull { raw ->
            runCatching {
                raw.toCandidate(
                    calendarName = calendarsById[raw.calendarId]?.displayName ?: "系统日历",
                    reminderMinutes = reminderLoad.minutesByEvent[raw.eventId].orEmpty(),
                    unsupportedReminderCount = reminderLoad.unsupportedByEvent[raw.eventId] ?: 0,
                    zoneId = zoneId,
                )
            }.fold(
                onSuccess = { candidate ->
                    candidate
                },
                onFailure = { error ->
                    skipped++
                    issues += ImportIssue(
                        IssueSeverity.Warning,
                        "系统事件 ${raw.eventId} 未导入：${error.message ?: "字段无法表示"}",
                    )
                    null
                },
            )
        }
        CalendarCandidateLoad(candidates, issues, skipped)
    }

    private fun loadReminders(eventIds: Set<Long>): ProviderReminderLoad {
        if (eventIds.isEmpty()) return ProviderReminderLoad(emptyMap(), emptyMap())
        val result = mutableMapOf<Long, MutableList<Int>>()
        val unsupported = mutableMapOf<Long, Int>()
        eventIds.chunked(500).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            resolver.query(
                CalendarContract.Reminders.CONTENT_URI,
                arrayOf(
                    CalendarContract.Reminders.EVENT_ID,
                    CalendarContract.Reminders.MINUTES,
                    CalendarContract.Reminders.METHOD,
                ),
                "${CalendarContract.Reminders.EVENT_ID} IN ($placeholders)",
                chunk.map(Long::toString).toTypedArray(),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val method = cursor.getInt(2)
                    if (method == CalendarContract.Reminders.METHOD_ALERT ||
                        method == CalendarContract.Reminders.METHOD_DEFAULT
                    ) {
                        result.getOrPut(cursor.getLong(0), ::mutableListOf) += cursor.getInt(1)
                    } else {
                        val eventId = cursor.getLong(0)
                        unsupported[eventId] = (unsupported[eventId] ?: 0) + 1
                    }
                }
            }
        }
        return ProviderReminderLoad(result, unsupported)
    }
}

internal fun prepareCalendarTransfer(
    selected: List<CalendarImportCandidate>,
    load: CalendarCandidateLoad,
): ParsedTransfer = ParsedTransfer(
    format = TransferFormat.Calendar,
    entries = selected.map(CalendarImportCandidate::entry),
    issues = load.issues + selected.flatMap { candidate ->
        candidate.warnings.map { warning -> ImportIssue(IssueSeverity.Warning, "“${candidate.entry.title}”：$warning") }
    },
    skipped = load.skipped,
)

internal data class CalendarEventRecord(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val description: String,
    val startMillis: Long,
    val endMillis: Long? = null,
    val duration: String? = null,
    val allDay: Boolean,
    val eventTimeZone: String?,
    val rrule: String?,
    val rdate: String?,
    val exdate: String?,
    val location: String,
    val hasAttendeeData: Boolean = false,
)

internal fun CalendarEventRecord.toCandidate(
    calendarName: String,
    reminderMinutes: List<Int>,
    zoneId: ZoneId,
    unsupportedReminderCount: Int = 0,
): CalendarImportCandidate {
    require(title.isNotBlank()) { "标题为空" }
    require(rdate.isNullOrBlank()) { "RDATE 额外发生日期无法无损表示" }
    require(exdate.isNullOrBlank()) { "EXDATE 排除日期无法无损表示" }
    val eventZone = eventTimeZone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: zoneId
    val start = Instant.ofEpochMilli(startMillis)
    val date = if (allDay) start.atZone(ZoneOffset.UTC).toLocalDate() else start.atZone(eventZone).toLocalDate()
    val time = if (allDay) null else start.atZone(eventZone).toLocalTime().withSecond(0).withNano(0)
    val recurrence = parseCalendarRecurrence(rrule, date)
    val warnings = buildList {
        if (location.isNotBlank()) add("地点已写入备注")
        if (hasAttendeeData) add("参与者和邀请响应不是 Athena 日期字段，未导入")
        if (!allDay && (endMillis != null || !duration.isNullOrBlank())) {
            add("Athena 不保存结束时间或持续时长；重新导出到系统日历时会使用 1 小时")
        }
        if (allDay && ((endMillis != null && endMillis - startMillis != MILLIS_PER_DAY) ||
                (!duration.isNullOrBlank() && duration != "P1D"))
        ) {
            add("多日全天事件只能保存开始日期；结束日期未导入")
        }
        if (reminderMinutes.any { it < 0 || it > REMINDER_DAYS_RANGE.last * 1_440 }) {
            add("超出 0–365 天范围的提醒未导入")
        }
        if (reminderMinutes.distinct().size > MAX_ENTRY_REMINDERS) {
            add("提醒超过 $MAX_ENTRY_REMINDERS 条，仅导入前 $MAX_ENTRY_REMINDERS 条")
        }
        if (unsupportedReminderCount > 0) add("$unsupportedReminderCount 条非通知型系统提醒无法表示，未导入")
    }
    val note = buildList {
        if (description.isNotBlank()) add(description.trim())
        if (location.isNotBlank()) add("地点：${location.trim()}")
        add("来源：系统日历 / $calendarName")
    }.joinToString("\n").take(MAX_IMPORTED_NOTE_LENGTH)
    val reminders = reminderMinutes.distinct().sorted().mapNotNull { minutes ->
        if (minutes !in 0..(REMINDER_DAYS_RANGE.last * 1_440)) return@mapNotNull null
        val trigger = if (allDay) {
            date.atStartOfDay().minusMinutes(minutes.toLong())
        } else {
            date.atTime(checkNotNull(time)).minusMinutes(minutes.toLong())
        }
        val daysBefore = java.time.temporal.ChronoUnit.DAYS.between(trigger.toLocalDate(), date).toInt()
        EntryReminder(
            id = stableCalendarId("$calendarId:$eventId:reminder:$minutes"),
            daysBefore = daysBefore,
            time = trigger.toLocalTime(),
        )
    }.distinctBy { it.daysBefore to it.time }.take(MAX_ENTRY_REMINDERS)
    val sourceId = "$calendarId:$eventId"
    return CalendarImportCandidate(
        sourceId = sourceId,
        calendarId = calendarId,
        calendarName = calendarName,
        entry = DateEntry(
            id = stableCalendarId(sourceId),
            title = title.trim().take(200),
            note = note,
            date = date,
            eventTime = time,
            eventTimeZone = eventZone.id.takeIf { time != null },
            kind = DateKind.Schedule,
            recurrence = recurrence,
            reminders = reminders,
            externalIdentity = ExternalIdentity(EXTERNAL_SOURCE_CALENDAR, sourceId),
        ),
        isAllDay = allDay,
        originalTime = time,
        warnings = warnings,
    )
}

internal fun parseCalendarRecurrence(value: String?, anchor: LocalDate): RecurrenceRule {
    if (value.isNullOrBlank()) return RecurrenceRule()
    val parts = value.split(';').associate { segment ->
        val keyValue = segment.split('=', limit = 2)
        require(keyValue.size == 2) { "重复规则格式无效" }
        keyValue[0].uppercase(Locale.ROOT) to keyValue[1]
    }
    val allowed = setOf("FREQ", "INTERVAL", "UNTIL", "WKST", "BYDAY", "BYMONTHDAY", "BYMONTH")
    require((parts.keys - allowed).isEmpty()) { "重复规则包含不支持的字段 ${(parts.keys - allowed).joinToString()}" }
    val frequency = when (parts["FREQ"]?.uppercase(Locale.ROOT)) {
        "DAILY" -> RepeatFrequency.Daily
        "WEEKLY" -> RepeatFrequency.Weekly
        "MONTHLY" -> RepeatFrequency.Monthly
        "YEARLY" -> RepeatFrequency.Yearly
        else -> error("重复频率无法表示")
    }
    val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1
    parts["BYDAY"]?.let { require(it == anchor.dayOfWeek.shortRRuleName()) { "每周多日或偏移星期规则无法表示" } }
    parts["BYMONTHDAY"]?.let { require(it.toIntOrNull() == anchor.dayOfMonth) { "指定月日规则无法无损表示" } }
    parts["BYMONTH"]?.let { require(it.toIntOrNull() == anchor.monthValue) { "指定月份规则无法无损表示" } }
    val end = parts["UNTIL"]?.let(::parseCalendarUntil)
    return RecurrenceRule(frequency, interval, end)
}

private fun parseCalendarUntil(value: String): LocalDate = when {
    value.matches(Regex("\\d{8}")) -> LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE)
    value.matches(Regex("\\d{8}T\\d{6}Z")) -> java.time.OffsetDateTime.parse(
        value,
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX"),
    ).toLocalDate()
    else -> error("重复结束时间格式无法表示")
}

private fun java.time.DayOfWeek.shortRRuleName(): String = name.take(2)

internal fun stableCalendarId(value: String): String = UUID.nameUUIDFromBytes(
    "athena-calendar:$value".toByteArray(StandardCharsets.UTF_8),
).toString()

private const val MAX_CALENDAR_EVENT_COUNT = 2_000
private const val MAX_IMPORTED_NOTE_LENGTH = 4_000
internal const val EXTERNAL_SOURCE_CALENDAR = "android_calendar"
private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L

private data class ProviderReminderLoad(
    val minutesByEvent: Map<Long, List<Int>>,
    val unsupportedByEvent: Map<Long, Int>,
)
