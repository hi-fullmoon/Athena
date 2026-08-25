package com.athena.dates

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Locale
import java.util.UUID

enum class TransferFormat { Json, Ics, Calendar, Contacts }

enum class ImportMode { Merge, Replace }

enum class IssueSeverity { Warning, Error }

data class ImportIssue(
    val severity: IssueSeverity,
    val message: String,
)

data class ImportCounts(
    val added: Int = 0,
    val updated: Int = 0,
    val duplicates: Int = 0,
    val skipped: Int = 0,
    val errors: Int = 0,
)

data class ImportPreview(
    val format: TransferFormat,
    val counts: ImportCounts,
    val issues: List<ImportIssue>,
    val canApply: Boolean,
    val canReplace: Boolean,
    val restoresSettings: Boolean,
)

internal data class BackupSnapshot(
    val entries: List<DateEntry>,
    val paletteName: String?,
    val exportedAt: Instant,
    val appVersion: String,
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
    val tags: List<DateTag> = entries.flatMap(DateEntry::tags).distinctBy(DateTag::id),
)

internal data class ParsedTransfer(
    val format: TransferFormat,
    val entries: List<DateEntry>,
    val tags: List<DateTag> = entries.flatMap(DateEntry::tags).distinctBy(DateTag::id),
    val paletteName: String? = null,
    val themeMode: ThemeMode? = null,
    val dynamicColor: Boolean? = null,
    val restoresSettings: Boolean = false,
    val issues: List<ImportIssue> = emptyList(),
    val skipped: Int = 0,
    val fatal: Boolean = false,
)

internal data class PreparedImport(
    val preview: ImportPreview,
    val mergeEntries: List<DateEntry>,
    val replacementEntries: List<DateEntry>,
    val mergeTags: List<DateTag>,
    val replacementTags: List<DateTag>,
    val paletteName: String?,
    val themeMode: ThemeMode?,
    val dynamicColor: Boolean?,
) {
    val format: TransferFormat get() = preview.format
}

internal object JsonBackupCodec {
    private val json = Json { prettyPrint = true }

    fun encode(snapshot: BackupSnapshot): String = json.encodeToString(
        JsonElement.serializer(),
        buildJsonObject {
            put("schema", BACKUP_SCHEMA)
            put("version", BACKUP_VERSION)
            put("exportedAt", snapshot.exportedAt.toString())
            putJsonObject("app") {
                put("package", "com.athena.dates")
                put("version", snapshot.appVersion)
            }
            putJsonObject("settings") {
                put("palette", snapshot.paletteName?.let(::JsonPrimitive) ?: JsonNull)
                put("themeMode", snapshot.themeMode.name)
                put("dynamicColor", snapshot.dynamicColor)
            }
            put("tags", buildJsonArray {
                snapshot.tags.sortedWith(compareBy({ it.name.lowercase(Locale.ROOT) }, DateTag::id)).forEach { tag ->
                    add(buildJsonObject {
                        put("id", tag.id)
                        put("name", tag.name)
                        put("colorArgb", tag.colorArgb)
                    })
                }
            })
            put("entries", buildJsonArray {
                snapshot.entries.sortedWith(compareBy(DateEntry::date, DateEntry::id)).forEach { entry ->
                    add(encodeEntry(entry))
                }
            })
        },
    )

    fun parse(raw: String): ParsedTransfer {
        val issues = mutableListOf<ImportIssue>()
        var skipped = 0
        return try {
            val root = json.parseToJsonElement(raw).asObject("备份根节点")
            val version = root.requiredInt("version")
            root.requireOnly(if (version >= 3) ROOT_FIELDS_V3 else ROOT_FIELDS_V1_V2, "备份根节点")
            require(root.requiredString("schema") == BACKUP_SCHEMA) { "不是 Athena 备份文件" }
            require(version in MIN_BACKUP_VERSION..BACKUP_VERSION) {
                "不支持的备份版本；当前支持版本 $MIN_BACKUP_VERSION..$BACKUP_VERSION"
            }
            Instant.parse(root.requiredString("exportedAt"))
            root.requiredObject("app").also { app ->
                app.requireOnly(APP_FIELDS, "app")
                require(app.requiredString("package") == "com.athena.dates") { "备份来源应用不匹配" }
                app.requiredString("version")
            }
            val settings = root.requiredObject("settings").also {
                it.requireOnly(if (version == 1) SETTINGS_FIELDS_V1 else SETTINGS_FIELDS_V2, "settings")
            }
            val palette = settings["palette"].nullableString("settings.palette")
            require(palette == null || palette in PALETTE_NAMES) { "settings.palette 不是可识别的主题" }
            val themeMode = if (version >= 2) {
                settings.requiredString("themeMode").let { stored ->
                    ThemeMode.entries.firstOrNull { it.name == stored } ?: error("settings.themeMode 无法识别")
                }
            } else {
                null
            }
            val dynamicColor = if (version >= 2) settings.requiredBoolean("dynamicColor") else null

            val tags = if (version >= 3) parseTags(root.requiredArray("tags")) else emptyList()
            val tagsById = tags.associateBy(DateTag::id)
            val array = root.requiredArray("entries")
            require(array.size <= MAX_ENTRY_COUNT) { "备份记录超过 $MAX_ENTRY_COUNT 条限制" }
            val entries = buildList {
                array.forEachIndexed { index, element ->
                    runCatching {
                        if (version >= 3) parseEntryCurrent(element, index + 1, tagsById, version) else parseLegacyEntry(element, index + 1, version)
                    }.onSuccess(::add).onFailure {
                        skipped++
                        issues += ImportIssue(IssueSeverity.Error, "第 ${index + 1} 条记录：${it.safeMessage()}")
                    }
                }
            }
            ParsedTransfer(
                format = TransferFormat.Json,
                entries = entries,
                tags = tags,
                paletteName = palette,
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                restoresSettings = true,
                issues = issues,
                skipped = skipped,
                fatal = issues.any { it.severity == IssueSeverity.Error },
            )
        } catch (failure: Exception) {
            ParsedTransfer(
                format = TransferFormat.Json,
                entries = emptyList(),
                issues = listOf(ImportIssue(IssueSeverity.Error, failure.safeMessage())),
                skipped = skipped,
                fatal = true,
            )
        }
    }

    private fun encodeEntry(entry: DateEntry): JsonObject = buildJsonObject {
        put("id", entry.id)
        put("title", entry.title)
        put("note", entry.note)
        put("date", entry.date.toString())
        put("eventTime", entry.eventTime?.toString()?.let(::JsonPrimitive) ?: JsonNull)
        put("eventTimeZone", entry.eventTimeZone?.let(::JsonPrimitive) ?: JsonNull)
        put("externalSource", entry.externalIdentity?.source?.let(::JsonPrimitive) ?: JsonNull)
        put("externalKey", entry.externalIdentity?.key?.let(::JsonPrimitive) ?: JsonNull)
        put("kind", entry.kind.storageKey)
        putJsonObject("calendar") {
            put("system", entry.calendarSystem.storageKey)
            put("lunarYear", entry.lunarDate?.year?.let(::JsonPrimitive) ?: JsonNull)
            put("lunarMonth", entry.lunarDate?.month?.let(::JsonPrimitive) ?: JsonNull)
            put("lunarDay", entry.lunarDate?.day?.let(::JsonPrimitive) ?: JsonNull)
            put("lunarLeapMonth", entry.lunarDate?.isLeapMonth ?: false)
        }
        putJsonObject("recurrence") {
            put("frequency", entry.recurrence.frequency.storageKey)
            put("interval", entry.recurrence.interval)
            put("endDate", entry.recurrence.endDate?.toString()?.let(::JsonPrimitive) ?: JsonNull)
        }
        put("reminders", buildJsonArray {
            entry.reminders.sortedWith(compareBy(EntryReminder::daysBefore, EntryReminder::time, EntryReminder::id))
                .forEach { reminder ->
                    add(buildJsonObject {
                        put("id", reminder.id)
                        put("daysBefore", reminder.daysBefore)
                        put("time", reminder.time.toString())
                    })
                }
        })
        put("tagIds", buildJsonArray { entry.tags.map(DateTag::id).sorted().forEach { add(JsonPrimitive(it)) } })
        put("isArchived", entry.isArchived)
        put("keepVisibleWhenExpired", entry.keepVisibleWhenExpired)
    }

    private fun parseTags(array: JsonArray): List<DateTag> {
        require(array.size <= MAX_TAG_COUNT) { "标签超过 $MAX_TAG_COUNT 条限制" }
        val seenIds = mutableSetOf<String>()
        val seenNames = mutableSetOf<String>()
        return array.mapIndexed { index, element ->
            val item = element.asObject("第 ${index + 1} 个标签").also {
                it.requireOnly(TAG_FIELDS, "第 ${index + 1} 个标签")
            }
            val tag = DateTag(
                id = item.requiredString("id"),
                name = item.requiredString("name").trim(),
                colorArgb = item.requiredInt("colorArgb"),
            )
            require(tag.id.length <= 200 && seenIds.add(tag.id)) { "标签 ID 重复或过长" }
            require(seenNames.add(normalizeTitle(tag.name))) { "标签名称重复：${tag.name}" }
            tag
        }
    }

    private fun parseEntryCurrent(
        element: JsonElement,
        position: Int,
        tagsById: Map<String, DateTag>,
        version: Int,
    ): DateEntry {
        val item = element.asObject("第 $position 条记录").also {
            it.requireOnly(if (version >= 4) ENTRY_FIELDS_V4 else ENTRY_FIELDS_V3, "第 $position 条记录")
        }
        val id = item.requiredString("id")
        val title = item.requiredString("title")
        val note = item.requiredString("note")
        validateEntryStrings(id, title, note)
        val date = LocalDate.parse(item.requiredString("date"))
        val calendar = item.requiredObject("calendar").also { it.requireOnly(CALENDAR_FIELDS, "calendar") }
        val calendarSystem = DateCalendarSystem.fromStored(calendar.requiredString("system"))
            ?: error("calendar.system 无法识别")
        val lunar = when (calendarSystem) {
            DateCalendarSystem.Gregorian -> {
                require(
                    calendar["lunarYear"] == JsonNull && calendar["lunarMonth"] == JsonNull &&
                        calendar["lunarDay"] == JsonNull && !calendar.requiredBoolean("lunarLeapMonth"),
                ) { "公历日期不能包含农历字段" }
                null
            }
            DateCalendarSystem.ChineseLunar -> LunarDateSpec(
                year = calendar.requiredNullableInt("lunarYear") ?: error("农历缺少年份"),
                month = calendar.requiredNullableInt("lunarMonth") ?: error("农历缺少月份"),
                day = calendar.requiredNullableInt("lunarDay") ?: error("农历缺少日"),
                isLeapMonth = calendar.requiredBoolean("lunarLeapMonth"),
            ).also { require(it.isValidLunarDate() && it.toSolarDate() == date) { "农历与公历锚点不一致" } }
        }
        val recurrenceNode = item.requiredObject("recurrence").also { it.requireOnly(RECURRENCE_FIELDS, "recurrence") }
        val recurrence = RecurrenceRule(
            frequency = RepeatFrequency.fromStored(recurrenceNode.requiredString("frequency"))
                ?: error("recurrence.frequency 无法识别"),
            interval = recurrenceNode.requiredInt("interval"),
            endDate = recurrenceNode["endDate"].nullableString("recurrence.endDate")?.let(LocalDate::parse),
        )
        val reminders = item.requiredArray("reminders").also {
            require(it.size <= MAX_ENTRY_REMINDERS) { "单条日期提醒过多" }
        }.mapIndexed { index, reminderNode ->
            val reminder = reminderNode.asObject("提醒 ${index + 1}").also {
                it.requireOnly(REMINDER_V3_FIELDS, "提醒 ${index + 1}")
            }
            EntryReminder(
                id = reminder.requiredString("id"),
                daysBefore = reminder.requiredInt("daysBefore"),
                time = LocalTime.parse(reminder.requiredString("time")),
            )
        }
        val tags = item.requiredArray("tagIds").map { node ->
            val tagId = node.jsonPrimitive.takeIf(JsonPrimitive::isString)?.contentOrNull ?: error("tagIds 必须是字符串数组")
            tagsById[tagId] ?: error("引用了不存在的标签 $tagId")
        }
        return DateEntry(
            id = id,
            title = title,
            note = note,
            date = date,
            eventTime = if (version >= 4) item["eventTime"].nullableString("eventTime")?.let(LocalTime::parse) else null,
            eventTimeZone = if (version >= 4) item["eventTimeZone"].nullableString("eventTimeZone") else null,
            kind = DateKind.fromStored(item.requiredString("kind")) ?: error("kind 无法识别"),
            calendarSystem = calendarSystem,
            lunarDate = lunar,
            recurrence = recurrence,
            reminders = reminders,
            tags = tags,
            isArchived = item.requiredBoolean("isArchived"),
            keepVisibleWhenExpired = item.requiredBoolean("keepVisibleWhenExpired"),
            externalIdentity = if (version >= 4) {
                val source = item["externalSource"].nullableString("externalSource")
                val key = item["externalKey"].nullableString("externalKey")
                require((source == null) == (key == null)) { "外部来源身份字段必须同时存在或同时为空" }
                if (source != null) ExternalIdentity(source, checkNotNull(key)) else null
            } else {
                null
            },
        ).also(::validateArchiveState)
    }

    private fun parseLegacyEntry(element: JsonElement, position: Int, version: Int): DateEntry {
        val item = element.asObject("第 $position 条记录")
        item.requireOnly(if (version == 1) ENTRY_FIELDS_V1 else ENTRY_FIELDS_V2, "第 $position 条记录")
        val id = item.requiredString("id")
        val title = item.requiredString("title")
        val note = item.requiredString("note")
        validateEntryStrings(id, title, note)
        val reminderNode = item.requiredObject("reminder").also {
            it.requireOnly(REMINDER_LEGACY_FIELDS, "第 $position 条记录.reminder")
        }
        val reminders = if (reminderNode.requiredBoolean("enabled")) {
            listOf(
                EntryReminder(
                    id = "$id:legacy-reminder",
                    daysBefore = reminderNode.requiredInt("daysBefore"),
                    time = LocalTime.parse(reminderNode.requiredString("time")),
                ),
            )
        } else {
            emptyList()
        }
        return DateEntry(
            id = id,
            title = title,
            note = note,
            date = LocalDate.parse(item.requiredString("date")),
            kind = DateKind.fromStored(item.requiredString("kind")) ?: error("kind 无法识别"),
            recurrence = if (item.requiredBoolean("repeatsYearly")) {
                RecurrenceRule(RepeatFrequency.Yearly)
            } else {
                RecurrenceRule()
            },
            reminders = reminders,
            isArchived = if (version >= 2) item.requiredBoolean("isArchived") else false,
            keepVisibleWhenExpired = if (version >= 2) item.requiredBoolean("keepVisibleWhenExpired") else false,
        ).also(::validateArchiveState)
    }

    private fun validateEntryStrings(id: String, title: String, note: String) {
        require(id.isNotBlank() && id.length <= 200) { "id 为空或过长" }
        require(title.isNotBlank() && title.length <= 1_000) { "title 为空或过长" }
        require(note.length <= 20_000) { "note 过长" }
    }

    private fun validateArchiveState(entry: DateEntry) {
        require(!entry.isArchived || (entry.kind == DateKind.Countdown && entry.recurrence.frequency == RepeatFrequency.None)) {
            "只有一次性倒数日可以归档"
        }
        require(!entry.keepVisibleWhenExpired || (entry.kind == DateKind.Countdown && entry.recurrence.frequency == RepeatFrequency.None)) {
            "只有一次性倒数日可以保留在默认列表"
        }
    }

    private val ROOT_FIELDS_V1_V2 = setOf("schema", "version", "exportedAt", "app", "settings", "entries")
    private val ROOT_FIELDS_V3 = ROOT_FIELDS_V1_V2 + "tags"
    private val APP_FIELDS = setOf("package", "version")
    private val SETTINGS_FIELDS_V1 = setOf("palette")
    private val SETTINGS_FIELDS_V2 = setOf("palette", "themeMode", "dynamicColor")
    private val ENTRY_FIELDS_V1 = setOf("id", "title", "note", "date", "kind", "repeatsYearly", "reminder")
    private val ENTRY_FIELDS_V2 = ENTRY_FIELDS_V1 + setOf("isArchived", "keepVisibleWhenExpired")
    private val ENTRY_FIELDS_V3 = setOf(
        "id", "title", "note", "date", "kind", "calendar", "recurrence", "reminders", "tagIds",
        "isArchived", "keepVisibleWhenExpired",
    )
    private val ENTRY_FIELDS_V4 = ENTRY_FIELDS_V3 + setOf("eventTime", "eventTimeZone", "externalSource", "externalKey")
    private val CALENDAR_FIELDS = setOf("system", "lunarYear", "lunarMonth", "lunarDay", "lunarLeapMonth")
    private val RECURRENCE_FIELDS = setOf("frequency", "interval", "endDate")
    private val REMINDER_LEGACY_FIELDS = setOf("enabled", "daysBefore", "time")
    private val REMINDER_V3_FIELDS = setOf("id", "daysBefore", "time")
    private val TAG_FIELDS = setOf("id", "name", "colorArgb")
    private val PALETTE_NAMES = setOf("Violet", "Sage", "Amber", "Ocean", "Rose")
}

internal object IcsCodec {
    fun encode(entries: List<DateEntry>, now: Instant): String = buildList {
        add("BEGIN:VCALENDAR")
        add("VERSION:2.0")
        add("PRODID:-//Athena//Date Backup//ZH-CN")
        add("CALSCALE:GREGORIAN")
        entries.sortedWith(compareBy(DateEntry::date, DateEntry::id)).forEach { entry ->
            add("BEGIN:VEVENT")
            add("UID:${escapeIcsText(entry.id)}@athena.local")
            add("DTSTAMP:${ICS_TIMESTAMP.format(now)}")
            if (entry.eventTime == null) {
                add("DTSTART;VALUE=DATE:${ICS_DATE.format(entry.date)}")
            } else {
                val zone = entry.eventTimeZone ?: "UTC"
                add("DTSTART;TZID=$zone:${ICS_LOCAL_DATE_TIME.format(entry.date.atTime(entry.eventTime))}")
            }
            add("SUMMARY:${escapeIcsText(entry.title)}")
            if (entry.note.isNotEmpty()) add("DESCRIPTION:${escapeIcsText(entry.note)}")
            add("X-ATHENA-ID:${escapeIcsText(entry.id)}")
            add("X-ATHENA-KIND:${entry.kind.storageKey}")
            entry.externalIdentity?.let { identity ->
                add("X-ATHENA-EXTERNAL:${encodeExtension(identity.source, identity.key)}")
            }
            add("X-ATHENA-CALENDAR-SYSTEM:${entry.calendarSystem.storageKey}")
            entry.lunarDate?.let { lunar ->
                add("X-ATHENA-LUNAR-DATE:${encodeExtension(lunar.year.toString(), lunar.month.toString(), lunar.day.toString(), lunar.isLeapMonth.toString())}")
            }
            add("X-ATHENA-RECURRENCE:${encodeRecurrenceExtension(entry.recurrence)}")
            if (entry.calendarSystem == DateCalendarSystem.Gregorian && entry.recurrence.isRepeating) {
                add("RRULE:${formatRRule(entry.recurrence)}")
            }
            if (entry.tags.isNotEmpty()) {
                add("CATEGORIES:${entry.tags.joinToString(",") { escapeIcsText(it.name) }}")
                entry.tags.forEach { tag ->
                    add("X-ATHENA-TAG:${encodeExtension(tag.id, tag.colorArgb.toString(), tag.name)}")
                }
            }
            entry.reminders.forEach { reminder ->
                add("X-ATHENA-REMINDER:${encodeExtension(reminder.id, reminder.daysBefore.toString(), reminder.time.toString())}")
                add("BEGIN:VALARM")
                add("ACTION:DISPLAY")
                add("DESCRIPTION:${escapeIcsText(entry.title)}")
                val eventMinute = entry.eventTime?.toSecondOfDay()?.div(60) ?: 0
                add("TRIGGER:${formatDuration(reminder.time.toSecondOfDay() / 60 - eventMinute - reminder.daysBefore * 1_440)}")
                add("END:VALARM")
            }
            add("END:VEVENT")
        }
        add("END:VCALENDAR")
    }.joinToString(separator = "\r\n", postfix = "\r\n", transform = ::foldIcsLine)

    fun parse(raw: String): ParsedTransfer {
        val unfolded = unfoldIcsLines(raw)
        if (unfolded.none { it.equals("BEGIN:VCALENDAR", ignoreCase = true) } ||
            unfolded.none { it.equals("END:VCALENDAR", ignoreCase = true) }
        ) {
            return ParsedTransfer(
                format = TransferFormat.Ics,
                entries = emptyList(),
                issues = listOf(ImportIssue(IssueSeverity.Error, "不是完整的 VCALENDAR 文件")),
                fatal = true,
            )
        }
        val eventBlocks = mutableListOf<List<String>>()
        var current: MutableList<String>? = null
        unfolded.forEach { line ->
            when {
                line.equals("BEGIN:VEVENT", true) && current == null -> current = mutableListOf()
                line.equals("END:VEVENT", true) && current != null -> {
                    eventBlocks += checkNotNull(current)
                    current = null
                }
                current != null -> checkNotNull(current).add(line)
            }
        }
        if (current != null) {
            return ParsedTransfer(
                format = TransferFormat.Ics,
                entries = emptyList(),
                issues = listOf(ImportIssue(IssueSeverity.Error, "VEVENT 未正确结束")),
                fatal = true,
            )
        }
        val issues = mutableListOf<ImportIssue>()
        var skipped = 0
        val entries = buildList {
            eventBlocks.forEachIndexed { index, lines ->
                val label = "第 ${index + 1} 个事件"
                runCatching { parseEvent(lines, label, issues) }
                    .onSuccess(::add)
                    .onFailure {
                        skipped++
                        issues += ImportIssue(IssueSeverity.Warning, "$label 已跳过：${it.safeMessage()}")
                    }
            }
        }
        return ParsedTransfer(
            format = TransferFormat.Ics,
            entries = entries,
            tags = entries.flatMap(DateEntry::tags).distinctBy(DateTag::id),
            issues = issues,
            skipped = skipped,
        )
    }

    private fun parseEvent(lines: List<String>, label: String, issues: MutableList<ImportIssue>): DateEntry {
        val alarmBlocks = mutableListOf<List<String>>()
        val eventLines = mutableListOf<IcsProperty>()
        var alarm: MutableList<String>? = null
        lines.forEach { line ->
            when {
                line.equals("BEGIN:VALARM", true) -> {
                    require(alarm == null) { "嵌套 VALARM 不受支持" }
                    alarm = mutableListOf()
                }
                line.equals("END:VALARM", true) -> {
                    require(alarm != null) { "VALARM 结构无效" }
                    alarmBlocks += checkNotNull(alarm)
                    alarm = null
                }
                alarm != null -> checkNotNull(alarm).add(line)
                else -> eventLines += parseProperty(line)
            }
        }
        require(alarm == null) { "VALARM 未正确结束" }

        val byName = eventLines.groupBy(IcsProperty::name)
        val starts = byName["DTSTART"].orEmpty()
        require(starts.size == 1) { "必须且只能包含一个 DTSTART" }
        val start = starts.single()
        val parsedStart = parseIcsStart(start)
        val date = parsedStart.date
        if (parsedStart.floating) {
            issues += ImportIssue(IssueSeverity.Warning, "$label 的 DTSTART 没有时区，按当前设备时区 ${parsedStart.zoneId} 导入")
        }
        val title = byName["SUMMARY"].orEmpty().also { require(it.size <= 1) { "包含多个 SUMMARY" } }
            .singleOrNull()?.value?.let(::unescapeIcsText)?.trim().orEmpty()
        require(title.isNotBlank() && title.length <= 1_000) { "缺少有效 SUMMARY" }
        val note = byName["DESCRIPTION"].orEmpty().also { require(it.size <= 1) { "包含多个 DESCRIPTION" } }
            .singleOrNull()?.value?.let(::unescapeIcsText).orEmpty()
        require(note.length <= 20_000) { "DESCRIPTION 过长" }

        val calendarSystem = byName["X-ATHENA-CALENDAR-SYSTEM"]?.singleOrNull()?.value?.let {
            DateCalendarSystem.fromStored(it) ?: error("X-ATHENA-CALENDAR-SYSTEM 无法识别")
        } ?: DateCalendarSystem.Gregorian
        val lunar = byName["X-ATHENA-LUNAR-DATE"]?.singleOrNull()?.value?.let { encoded ->
            val parts = decodeExtension(encoded, 4)
            LunarDateSpec(parts[0].toInt(), parts[1].toInt(), parts[2].toInt(), parts[3].toBooleanStrict())
                .also { require(it.isValidLunarDate() && it.toSolarDate() == date) { "农历扩展字段与 DTSTART 不一致" } }
        }
        require((calendarSystem == DateCalendarSystem.ChineseLunar) == (lunar != null)) { "农历扩展字段不完整" }

        val recurrence = byName["X-ATHENA-RECURRENCE"]?.singleOrNull()?.value?.let(::decodeRecurrenceExtension)
            ?: parseRRule(byName["RRULE"].orEmpty())
        require(byName["RDATE"].isNullOrEmpty() && byName["EXDATE"].isNullOrEmpty()) {
            "RDATE/EXDATE 无法无损映射"
        }
        val kind = byName["X-ATHENA-KIND"]?.singleOrNull()?.value?.let {
            DateKind.fromStored(it) ?: error("X-ATHENA-KIND 无法识别")
        } ?: DateKind.Schedule
        val customId = byName["X-ATHENA-ID"]?.singleOrNull()?.value?.let(::unescapeIcsText)?.trim()
        val uid = byName["UID"]?.singleOrNull()?.value?.let(::unescapeIcsText)?.trim()
        val id = when {
            !customId.isNullOrBlank() -> customId
            !uid.isNullOrBlank() -> stableIcsId(uid)
            else -> stableIcsId("${normalizeTitle(title)}|$date|${kind.storageKey}|$recurrence")
        }
        require(id.length <= 200) { "标识符过长" }

        val exactReminders = byName["X-ATHENA-REMINDER"].orEmpty().mapIndexed { index, property ->
            runCatching {
                val parts = decodeExtension(property.value, 3)
                EntryReminder(parts[0], parts[1].toInt(), LocalTime.parse(parts[2]))
            }.getOrElse {
                issues += ImportIssue(IssueSeverity.Warning, "$label 的第 ${index + 1} 条 Athena 提醒字段无效，已跳过")
                null
            }
        }.filterNotNull()
        val reminders = if (exactReminders.isNotEmpty()) {
            exactReminders
        } else {
            alarmBlocks.mapIndexedNotNull { index, block ->
                parseAlarm(block, parsedStart.time ?: LocalTime.MIDNIGHT)?.let { (days, time) ->
                    EntryReminder(stableIcsId("$id|alarm|$index|$days|$time"), days, time)
                } ?: run {
                    issues += ImportIssue(IssueSeverity.Warning, "$label 的第 ${index + 1} 条 VALARM 无法映射，已跳过该提醒")
                    null
                }
            }
        }.distinctBy { it.daysBefore to it.time }.also { unique ->
            val sourceSize = if (exactReminders.isNotEmpty()) exactReminders.size else alarmBlocks.size
            if (unique.size < sourceSize) issues += ImportIssue(IssueSeverity.Warning, "$label 的完全重复提醒已合并")
        }

        val exactTags = byName["X-ATHENA-TAG"].orEmpty().mapIndexedNotNull { index, property ->
            runCatching {
                val parts = decodeExtension(property.value, 3)
                DateTag(parts[0], parts[2], parts[1].toInt())
            }.getOrElse {
                issues += ImportIssue(IssueSeverity.Warning, "$label 的第 ${index + 1} 个 Athena 标签无效，已跳过")
                null
            }
        }
        val tags = if (exactTags.isNotEmpty()) {
            exactTags
        } else {
            byName["CATEGORIES"].orEmpty().flatMap { splitIcsTextList(it.value) }.map { name ->
                DateTag(stableIcsId("tag:${normalizeTitle(name)}"), name, defaultTagColor(name))
            }
        }.distinctBy(DateTag::id)

        val supported = SUPPORTED_EVENT_FIELDS
        eventLines.map(IcsProperty::name).distinct().filter { it !in supported && !it.startsWith("X-") }.forEach { field ->
            issues += ImportIssue(IssueSeverity.Warning, "$label 的 $field 未导入")
        }
        eventLines.map(IcsProperty::name).distinct().filter { it.startsWith("X-") && it !in supported }.forEach { field ->
            issues += ImportIssue(IssueSeverity.Warning, "$label 的扩展字段 $field 未导入")
        }

        val externalIdentity = byName["X-ATHENA-EXTERNAL"]?.singleOrNull()?.value?.let { encoded ->
            val parts = decodeExtension(encoded, 2)
            ExternalIdentity(parts[0], parts[1])
        }

        return DateEntry(
            id = id,
            title = title,
            note = note,
            date = date,
            eventTime = parsedStart.time,
            eventTimeZone = parsedStart.zoneId,
            kind = kind,
            calendarSystem = calendarSystem,
            lunarDate = lunar,
            recurrence = recurrence,
            reminders = reminders,
            tags = tags,
            externalIdentity = externalIdentity,
        )
    }

    private fun parseRRule(rules: List<IcsProperty>): RecurrenceRule {
        require(rules.size <= 1) { "包含多个 RRULE" }
        val rule = rules.singleOrNull() ?: return RecurrenceRule()
        val parts = rule.value.split(';').associate { part ->
            val pair = part.split('=', limit = 2)
            require(pair.size == 2) { "RRULE 格式无效" }
            pair[0].uppercase(Locale.ROOT) to pair[1].uppercase(Locale.ROOT)
        }
        require(parts.keys.all { it in setOf("FREQ", "INTERVAL", "UNTIL") }) { "RRULE 包含无法无损映射的规则" }
        val frequency = when (parts["FREQ"]) {
            "DAILY" -> RepeatFrequency.Daily
            "WEEKLY" -> RepeatFrequency.Weekly
            "MONTHLY" -> RepeatFrequency.Monthly
            "YEARLY" -> RepeatFrequency.Yearly
            else -> error("不支持的 RRULE FREQ")
        }
        val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1
        val until = parts["UNTIL"]?.let { value ->
            require(DATE_VALUE.matches(value)) { "仅支持日期型 UNTIL" }
            LocalDate.parse(value, ICS_DATE)
        }
        return RecurrenceRule(frequency, interval, until)
    }

    private fun parseAlarm(lines: List<String>, eventTime: LocalTime): Pair<Int, LocalTime>? {
        val properties = runCatching { lines.map(::parseProperty).groupBy(IcsProperty::name) }.getOrNull() ?: return null
        if (properties["ACTION"]?.singleOrNull()?.value?.uppercase(Locale.ROOT) != "DISPLAY") return null
        val trigger = properties["TRIGGER"]?.singleOrNull() ?: return null
        if (trigger.parameters["VALUE"]?.equals("DATE-TIME", true) == true ||
            trigger.parameters["RELATED"]?.equals("END", true) == true
        ) {
            return null
        }
        val offset = parseDuration(trigger.value) ?: return null
        val baseDate = LocalDate.of(2000, 1, 2)
        val triggerMoment = baseDate.atTime(eventTime).plusMinutes(offset.toLong())
        val days = ChronoUnit.DAYS.between(triggerMoment.toLocalDate(), baseDate).toInt()
        return days.takeIf { it in REMINDER_DAYS_RANGE }?.let { it to triggerMoment.toLocalTime() }
    }

    private val SUPPORTED_EVENT_FIELDS = setOf(
        "UID", "DTSTAMP", "DTSTART", "SUMMARY", "DESCRIPTION", "RRULE", "RDATE", "EXDATE", "CATEGORIES",
        "X-ATHENA-ID", "X-ATHENA-KIND", "X-ATHENA-CALENDAR-SYSTEM", "X-ATHENA-LUNAR-DATE",
        "X-ATHENA-RECURRENCE", "X-ATHENA-REMINDER", "X-ATHENA-TAG",
        "X-ATHENA-EXTERNAL",
    )
}

internal object ImportPlanner {
    fun prepare(parsed: ParsedTransfer, existing: List<DateEntry>): PreparedImport =
        prepare(parsed, DateDataSnapshot(existing, existing.flatMap(DateEntry::tags).distinctBy(DateTag::id)))

    fun prepare(parsed: ParsedTransfer, existing: DateDataSnapshot): PreparedImport {
        val issues = parsed.issues.toMutableList()
        val tagPlan = planTags(parsed.tags, existing.tags, issues)
        val incomingEntries = parsed.entries.map { original ->
            original to original.copy(tags = original.tags.map { tag -> tagPlan.remapped[tag.id] ?: tag })
        }
        var added = 0
        var updated = 0
        var duplicates = 0
        var skipped = parsed.skipped
        val mergeEntries = mutableListOf<DateEntry>()
        val acceptedSource = mutableListOf<DateEntry>()
        val existingById = existing.entries.associateBy(DateEntry::id)
        val existingByIdentity = existing.entries.groupBy(::entryIdentity)
        val seenIncomingIds = mutableMapOf<String, DateEntry>()
        val claimedExistingIds = mutableMapOf<String, DateEntry>()

        incomingEntries.forEach { (original, incoming) ->
            val identity = entryIdentity(incoming)
            val priorIncoming = seenIncomingIds[incoming.id]
            if (priorIncoming != null) {
                if (priorIncoming.sameContent(incoming)) duplicates++ else {
                    skipped++
                    issues += ImportIssue(
                        if (parsed.format == TransferFormat.Json) IssueSeverity.Error else IssueSeverity.Warning,
                        "导入文件内有相同 ID 但内容冲突的记录“${incoming.title}”，已保留第一条",
                    )
                }
                return@forEach
            }
            seenIncomingIds[incoming.id] = incoming
            acceptedSource += original

            val idMatch = existingById[incoming.id]
            val semanticMatches = existingByIdentity[identity].orEmpty()
            val target = idMatch ?: semanticMatches.singleOrNull()
            if (idMatch == null && semanticMatches.size > 1) {
                skipped++
                issues += ImportIssue(IssueSeverity.Warning, "本地存在多条语义相同记录“${incoming.title}”，无法安全选择更新对象")
                return@forEach
            }
            val priorClaim = target?.let { claimedExistingIds[it.id] }
            if (target != null && priorClaim != null) {
                if (priorClaim.sameContent(incoming)) duplicates++ else {
                    skipped++
                    issues += ImportIssue(IssueSeverity.Warning, "多条导入记录会更新同一条本地数据“${incoming.title}”，已跳过后者")
                }
                return@forEach
            }
            if (target != null) claimedExistingIds[target.id] = incoming
            when {
                target == null -> {
                    added++
                    mergeEntries += incoming
                }
                target.sameContent(incoming) -> duplicates++
                else -> {
                    updated++
                    mergeEntries += incoming.copy(id = target.id)
                }
            }
        }

        val errors = issues.count { it.severity == IssueSeverity.Error }
        val canApply = !parsed.fatal && errors == 0
        return PreparedImport(
            preview = ImportPreview(
                format = parsed.format,
                counts = ImportCounts(added, updated, duplicates, skipped, errors),
                issues = issues,
                canApply = canApply,
                canReplace = canApply && parsed.format == TransferFormat.Json,
                restoresSettings = parsed.restoresSettings,
            ),
            mergeEntries = mergeEntries,
            replacementEntries = acceptedSource,
            mergeTags = tagPlan.toUpsert,
            replacementTags = parsed.tags,
            paletteName = parsed.paletteName,
            themeMode = parsed.themeMode,
            dynamicColor = parsed.dynamicColor,
        )
    }

    private fun planTags(incoming: List<DateTag>, existing: List<DateTag>, issues: MutableList<ImportIssue>): TagPlan {
        val existingById = existing.associateBy(DateTag::id)
        val knownByName = existing.associateBy { normalizeTitle(it.name) }.toMutableMap()
        val remapped = mutableMapOf<String, DateTag>()
        val toUpsert = mutableListOf<DateTag>()
        incoming.distinctBy(DateTag::id).forEach { tag ->
            val idMatch = existingById[tag.id]
            val normalizedName = normalizeTitle(tag.name)
            val nameMatch = knownByName[normalizedName]
            when {
                idMatch != null && nameMatch != null && nameMatch.id != idMatch.id -> {
                    remapped[tag.id] = nameMatch
                    issues += ImportIssue(IssueSeverity.Warning, "标签“${tag.name}”名称与本地另一标签重复，已合并为本地标签")
                }
                idMatch != null -> {
                    remapped[tag.id] = tag
                    if (idMatch != tag) {
                        toUpsert += tag
                        knownByName.remove(normalizeTitle(idMatch.name))
                        knownByName[normalizedName] = tag
                    }
                }
                nameMatch != null -> {
                    remapped[tag.id] = nameMatch
                    if (nameMatch.colorArgb != tag.colorArgb) {
                        issues += ImportIssue(IssueSeverity.Warning, "标签“${tag.name}”已存在，保留本地颜色")
                    }
                }
                else -> {
                    remapped[tag.id] = tag
                    toUpsert += tag
                    knownByName[normalizedName] = tag
                }
            }
        }
        return TagPlan(remapped, toUpsert.distinctBy(DateTag::id))
    }

    private data class TagPlan(val remapped: Map<String, DateTag>, val toUpsert: List<DateTag>)
}

internal fun entryIdentity(entry: DateEntry): String = listOf(
    entry.externalIdentity?.let { "external:${it.source}:${it.key}" }.orEmpty(),
    normalizeTitle(entry.title),
    entry.date.toString(),
    entry.eventTime?.toString().orEmpty(),
    entry.eventTimeZone.orEmpty(),
    entry.kind.storageKey,
    entry.calendarSystem.storageKey,
    entry.lunarDate?.toString().orEmpty(),
    entry.recurrence.frequency.storageKey,
    entry.recurrence.interval.toString(),
    entry.recurrence.endDate?.toString().orEmpty(),
).joinToString("|")

private fun normalizeTitle(value: String): String = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

private fun DateEntry.sameContent(other: DateEntry): Boolean =
    title == other.title && note == other.note && date == other.date && kind == other.kind &&
        eventTime == other.eventTime && eventTimeZone == other.eventTimeZone &&
        calendarSystem == other.calendarSystem && lunarDate == other.lunarDate && recurrence == other.recurrence &&
        reminders.sortedWith(REMINDER_COMPARATOR) == other.reminders.sortedWith(REMINDER_COMPARATOR) &&
        tags.map(DateTag::id).sorted() == other.tags.map(DateTag::id).sorted() &&
        isArchived == other.isArchived && keepVisibleWhenExpired == other.keepVisibleWhenExpired &&
        externalIdentity == other.externalIdentity

private data class IcsProperty(val name: String, val parameters: Map<String, String>, val value: String)

private data class ParsedIcsStart(
    val date: LocalDate,
    val time: LocalTime?,
    val zoneId: String?,
    val floating: Boolean = false,
)

private fun parseIcsStart(property: IcsProperty): ParsedIcsStart {
    if (property.parameters["VALUE"]?.equals("DATE", true) == true) {
        require(DATE_VALUE.matches(property.value)) { "全天 DTSTART 日期格式无效" }
        return ParsedIcsStart(LocalDate.parse(property.value, ICS_DATE), null, null)
    }
    require(
        property.parameters["VALUE"].isNullOrBlank() ||
            property.parameters["VALUE"]?.equals("DATE-TIME", true) == true,
    ) { "DTSTART VALUE 类型不受支持" }
    val utc = property.value.endsWith('Z')
    val raw = if (utc) property.value.dropLast(1) else property.value
    require(LOCAL_DATE_TIME_VALUE.matches(raw)) { "定时 DTSTART 格式无效" }
    val local = LocalDateTime.parse(raw, ICS_LOCAL_DATE_TIME)
    val explicitZone = property.parameters["TZID"]
    require(!utc || explicitZone == null) { "UTC DTSTART 不能同时包含 TZID" }
    val zone = when {
        utc -> "UTC"
        explicitZone != null -> ZoneId.of(explicitZone).id
        else -> ZoneId.systemDefault().id
    }
    return ParsedIcsStart(local.toLocalDate(), local.toLocalTime(), zone, floating = !utc && explicitZone == null)
}

private fun parseProperty(line: String): IcsProperty {
    val colon = line.indexOf(':')
    require(colon > 0) { "ICS 属性缺少冒号" }
    val declaration = line.substring(0, colon).split(';')
    val parameters = declaration.drop(1).associate { part ->
        val pair = part.split('=', limit = 2)
        require(pair.size == 2) { "ICS 参数格式无效" }
        pair[0].uppercase(Locale.ROOT) to pair[1].trim('"')
    }
    return IcsProperty(declaration.first().uppercase(Locale.ROOT), parameters, line.substring(colon + 1))
}

private fun unfoldIcsLines(raw: String): List<String> {
    val result = mutableListOf<String>()
    raw.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { line ->
        if ((line.startsWith(' ') || line.startsWith('\t')) && result.isNotEmpty()) result[result.lastIndex] += line.drop(1)
        else if (line.isNotEmpty()) result += line
    }
    return result
}

private fun escapeIcsText(value: String): String = value
    .replace("\\", "\\\\").replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n")
    .replace(",", "\\,").replace(";", "\\;")

private fun unescapeIcsText(value: String): String {
    val output = StringBuilder()
    var index = 0
    while (index < value.length) {
        if (value[index] == '\\' && index + 1 < value.length) {
            output.append(if (value[index + 1] == 'n' || value[index + 1] == 'N') '\n' else value[index + 1])
            index += 2
        } else output.append(value[index++])
    }
    return output.toString()
}

private fun splitIcsTextList(value: String): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var escaped = false
    value.forEach { char ->
        when {
            escaped -> {
                current.append(if (char == 'n' || char == 'N') '\n' else char)
                escaped = false
            }
            char == '\\' -> escaped = true
            char == ',' -> {
                current.toString().trim().takeIf(String::isNotEmpty)?.let(result::add)
                current.clear()
            }
            else -> current.append(char)
        }
    }
    current.toString().trim().takeIf(String::isNotEmpty)?.let(result::add)
    return result
}

private fun foldIcsLine(line: String): String {
    if (line.toByteArray(StandardCharsets.UTF_8).size <= 75) return line
    val output = StringBuilder()
    var bytesOnLine = 0
    var index = 0
    while (index < line.length) {
        val codePoint = line.codePointAt(index)
        val text = String(Character.toChars(codePoint))
        val bytes = text.toByteArray(StandardCharsets.UTF_8).size
        val limit = if (bytesOnLine == 0 && output.isEmpty()) 75 else 74
        if (bytesOnLine > 0 && bytesOnLine + bytes > limit) {
            output.append("\r\n ")
            bytesOnLine = 0
        }
        output.append(text)
        bytesOnLine += bytes
        index += Character.charCount(codePoint)
    }
    return output.toString()
}

private fun formatRRule(rule: RecurrenceRule): String = buildList {
    add("FREQ=${rule.frequency.name.uppercase(Locale.ROOT)}")
    if (rule.interval != 1) add("INTERVAL=${rule.interval}")
    rule.endDate?.let { add("UNTIL=${ICS_DATE.format(it)}") }
}.joinToString(";")

private fun formatDuration(totalMinutes: Int): String {
    val sign = if (totalMinutes < 0) "-" else ""
    var remaining = kotlin.math.abs(totalMinutes)
    val days = remaining / 1_440
    remaining %= 1_440
    val hours = remaining / 60
    val minutes = remaining % 60
    return buildString {
        append(sign).append('P')
        if (days > 0) append(days).append('D')
        if (hours > 0 || minutes > 0 || days == 0) {
            append('T')
            if (hours > 0) append(hours).append('H')
            if (minutes > 0 || hours == 0) append(minutes).append('M')
        }
    }
}

private fun parseDuration(value: String): Int? {
    val match = DURATION_VALUE.matchEntire(value.uppercase(Locale.ROOT)) ?: return null
    val days = match.groupValues[2].toIntOrNull() ?: 0
    val hours = match.groupValues[3].toIntOrNull() ?: 0
    val minutes = match.groupValues[4].toIntOrNull() ?: 0
    if (hours > 23 || minutes > 59 || days !in REMINDER_DAYS_RANGE) return null
    val magnitude = days * 1_440 + hours * 60 + minutes
    return if (match.groupValues[1] == "-") -magnitude else magnitude
}

private fun encodeRecurrenceExtension(rule: RecurrenceRule): String = encodeExtension(
    rule.frequency.storageKey,
    rule.interval.toString(),
    rule.endDate?.toString().orEmpty(),
)

private fun decodeRecurrenceExtension(value: String): RecurrenceRule {
    val parts = decodeExtension(value, 3)
    return RecurrenceRule(
        RepeatFrequency.fromStored(parts[0]) ?: error("重复频率无法识别"),
        parts[1].toInt(),
        parts[2].takeIf(String::isNotEmpty)?.let(LocalDate::parse),
    )
}

private fun encodeExtension(vararg parts: String): String = Base64.getUrlEncoder().withoutPadding()
    .encodeToString(parts.joinToString("\u0000").toByteArray(StandardCharsets.UTF_8))

private fun decodeExtension(value: String, expectedParts: Int): List<String> {
    val decoded = String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split('\u0000')
    require(decoded.size == expectedParts) { "Athena 扩展字段格式无效" }
    return decoded
}

private fun stableIcsId(value: String): String = UUID.nameUUIDFromBytes(
    "athena-ics:$value".toByteArray(StandardCharsets.UTF_8),
).toString()

private fun defaultTagColor(name: String): Int = DEFAULT_TAG_COLORS[
    (normalizeTitle(name).hashCode() and Int.MAX_VALUE) % DEFAULT_TAG_COLORS.size
]

private fun JsonElement.asObject(label: String): JsonObject = this as? JsonObject ?: error("$label 必须是对象")

private fun JsonObject.requireOnly(allowed: Set<String>, label: String) {
    val unknown = keys - allowed
    require(unknown.isEmpty()) { "$label 包含未知字段：${unknown.sorted().joinToString()}" }
}

private fun JsonObject.requiredString(name: String): String =
    this[name]?.jsonPrimitive?.takeIf(JsonPrimitive::isString)?.contentOrNull ?: error("$name 必须是字符串")

private fun JsonObject.requiredInt(name: String): Int =
    this[name]?.jsonPrimitive?.takeUnless(JsonPrimitive::isString)?.intOrNull ?: error("$name 必须是整数")

private fun JsonObject.requiredNullableInt(name: String): Int? = when (val value = this[name]) {
    null -> error("缺少 $name")
    JsonNull -> null
    else -> value.jsonPrimitive.takeUnless(JsonPrimitive::isString)?.intOrNull ?: error("$name 必须是整数或 null")
}

private fun JsonObject.requiredBoolean(name: String): Boolean =
    this[name]?.jsonPrimitive?.takeUnless(JsonPrimitive::isString)?.booleanOrNull ?: error("$name 必须是布尔值")

private fun JsonObject.requiredObject(name: String): JsonObject = this[name]?.jsonObject ?: error("$name 必须是对象")
private fun JsonObject.requiredArray(name: String): JsonArray = this[name]?.jsonArray ?: error("$name 必须是数组")

private fun JsonElement?.nullableString(label: String): String? = when (this) {
    null -> error("缺少 $label")
    JsonNull -> null
    is JsonPrimitive -> takeIf(JsonPrimitive::isString)?.contentOrNull ?: error("$label 必须是字符串或 null")
    else -> error("$label 必须是字符串或 null")
}

private fun Throwable.safeMessage(): String = message?.takeIf(String::isNotBlank) ?: "文件格式无效"

private val ICS_DATE = DateTimeFormatter.BASIC_ISO_DATE
private val ICS_LOCAL_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
private val REMINDER_COMPARATOR = compareBy(EntryReminder::daysBefore, EntryReminder::time, EntryReminder::id)
private val ICS_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(java.time.ZoneOffset.UTC)
private val DATE_VALUE = Regex("\\d{8}")
private val LOCAL_DATE_TIME_VALUE = Regex("\\d{8}T\\d{6}")
private val DURATION_VALUE = Regex("^([+-])?P(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?)?$")
private const val BACKUP_SCHEMA = "athena-backup"
private const val MIN_BACKUP_VERSION = 1
internal const val BACKUP_VERSION = 4
internal const val MAX_ENTRY_COUNT = 10_000
internal const val MAX_TAG_COUNT = 1_000
