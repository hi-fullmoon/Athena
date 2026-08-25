package com.athena.dates

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

data class DateDataSnapshot(
    val entries: List<DateEntry>,
    val tags: List<DateTag>,
)

interface DateEntryRepository {
    val entries: Flow<List<DateEntry>>
    val tags: Flow<List<DateTag>>
    suspend fun upsert(entry: DateEntry)
    suspend fun delete(id: String)
    suspend fun archiveExpired(reference: LocalDate): Int
    suspend fun restoreArchived(id: String): Boolean
}

internal class RoomDateEntryRepository(
    context: Context,
    private val dao: DateEntryDao = AthenaDatabase.getInstance(context).dateEntryDao(),
    private val widgetRefresher: WidgetRefresher = AndroidWidgetRefresher(context.applicationContext),
) : DateEntryRepository {
    private val preferences = context.getSharedPreferences("athena_dates", Context.MODE_PRIVATE)
    private val migrationMutex = Mutex()

    override val entries: Flow<List<DateEntry>> = flow {
        migrateLegacyEntriesIfNeeded()
        emitAll(dao.observeAll().map { rows -> rows.mapNotNull(DateEntryWithDetails::toDateEntry) })
    }.distinctUntilChanged()

    override val tags: Flow<List<DateTag>> = flow {
        migrateLegacyEntriesIfNeeded()
        emitAll(dao.observeTags().map { rows -> rows.map(DateTagEntity::toDateTag) })
    }.distinctUntilChanged()

    override suspend fun upsert(entry: DateEntry) {
        migrateLegacyEntriesIfNeeded()
        dao.upsertEntry(entry.toDetails())
        refreshWidgetSafely()
    }

    override suspend fun delete(id: String) {
        migrateLegacyEntriesIfNeeded()
        dao.deleteById(id)
        refreshWidgetSafely()
    }

    override suspend fun archiveExpired(reference: LocalDate): Int {
        migrateLegacyEntriesIfNeeded()
        return dao.archiveExpiredCountdowns(reference.toString()).also { changed ->
            if (changed > 0) refreshWidgetSafely()
        }
    }

    override suspend fun restoreArchived(id: String): Boolean {
        migrateLegacyEntriesIfNeeded()
        return (dao.restoreArchived(id) == 1).also { changed ->
            if (changed) refreshWidgetSafely()
        }
    }

    internal suspend fun snapshot(): List<DateEntry> = snapshotData().entries

    internal suspend fun snapshotData(): DateDataSnapshot {
        migrateLegacyEntriesIfNeeded()
        val entries = dao.getAll().map { row ->
            row.toDateEntry() ?: error("数据库记录 ${row.entry.id} 无法解析；为避免不完整备份，操作已停止")
        }
        return DateDataSnapshot(entries, dao.getAllTags().map(DateTagEntity::toDateTag))
    }

    internal suspend fun applyDataImport(
        data: DateDataSnapshot,
        replace: Boolean,
        archiveReference: LocalDate? = null,
    ) {
        migrateLegacyEntriesIfNeeded()
        dao.applyImport(data.toEntities(), replace, archiveReference?.toString())
        refreshWidgetSafely()
    }

    private suspend fun refreshWidgetSafely() {
        runCatching { widgetRefresher.refresh() }
    }

    private suspend fun migrateLegacyEntriesIfNeeded() = migrationMutex.withLock {
        if (preferences.getBoolean(KEY_ROOM_MIGRATION_COMPLETE, false)) return@withLock
        val raw = preferences.getString(KEY_ENTRIES, null)
        val legacyData = withContext(Dispatchers.IO) { raw?.let(::parseLegacyEntries) }
        legacyData?.entries?.takeIf { it.isNotEmpty() }?.forEach { dao.upsertEntry(it.toDetails()) }
        withContext(Dispatchers.IO) {
            preferences.edit {
                if (raw != null && legacyData?.needsRecoveryCopy != false) putString(KEY_RECOVERY, raw)
                remove(KEY_ENTRIES)
                putBoolean(KEY_ROOM_MIGRATION_COMPLETE, true)
            }
        }
    }

    private fun parseLegacyEntries(raw: String): LegacyData {
        val json = runCatching { JSONArray(raw) }.getOrElse {
            return LegacyData(emptyList(), needsRecoveryCopy = true)
        }
        var needsRecoveryCopy = false
        val seenIds = mutableSetOf<String>()
        val loadedEntries = buildList {
            repeat(json.length()) { index ->
                runCatching {
                    val item = json.get(index) as? JSONObject ?: error("Date entry must be an object")
                    require(item.optInt("schemaVersion", 0) in 0..1)
                    val id = item.getString("id").also { require(it.isNotBlank()) }
                    DateEntry(
                        id = id,
                        title = item.getString("title"),
                        note = item.optString("note"),
                        date = LocalDate.parse(item.getString("date")),
                        kind = DateKind.fromStored(item.getString("kind")) ?: error("Unknown kind"),
                        recurrence = if (item.optBoolean("repeatsYearly")) {
                            RecurrenceRule(RepeatFrequency.Yearly)
                        } else {
                            RecurrenceRule()
                        },
                    ).also { require(seenIds.add(id)) }
                }.onSuccess(::add).onFailure { needsRecoveryCopy = true }
            }
        }
        return LegacyData(loadedEntries, needsRecoveryCopy)
    }

    private companion object {
        const val KEY_ENTRIES = "entries"
        const val KEY_RECOVERY = "entries_recovery"
        const val KEY_ROOM_MIGRATION_COMPLETE = "room_migration_complete"
    }
}

class SettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences("athena_dates", Context.MODE_PRIVATE)
    fun loadPaletteName(): String? = preferences.getString("palette", null)
    fun savePaletteName(name: String) = preferences.edit { putString("palette", name) }
    fun loadAppearance(): AppearanceSettings = AppearanceSettings(
        paletteName = loadPaletteName(),
        themeMode = preferences.getString(KEY_THEME_MODE, null)
            ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
            ?: ThemeMode.System,
        dynamicColor = preferences.getBoolean(KEY_DYNAMIC_COLOR, false),
    )

    fun saveAppearance(settings: AppearanceSettings) = preferences.edit {
        if (settings.paletteName == null) remove("palette") else putString("palette", settings.paletteName)
        putString(KEY_THEME_MODE, settings.themeMode.name)
        putBoolean(KEY_DYNAMIC_COLOR, settings.dynamicColor)
    }

    @SuppressLint("ApplySharedPref", "UseKtx")
    fun restoreAppearance(settings: AppearanceSettings): Boolean = preferences.edit().apply {
        if (settings.paletteName == null) remove("palette") else putString("palette", settings.paletteName)
        putString(KEY_THEME_MODE, settings.themeMode.name)
        putBoolean(KEY_DYNAMIC_COLOR, settings.dynamicColor)
    }.commit()

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
    }
}

private data class LegacyData(val entries: List<DateEntry>, val needsRecoveryCopy: Boolean)

internal fun DateEntry.toDetails() = DateEntryWithDetails(
    entry = DateEntryEntity(
        id = id,
        title = title,
        note = note,
        date = date.toString(),
        eventTime = eventTime?.toString(),
        eventTimeZone = eventTimeZone,
        kind = kind.storageKey,
        calendarSystem = calendarSystem.storageKey,
        lunarYear = lunarDate?.year,
        lunarMonth = lunarDate?.month,
        lunarDay = lunarDate?.day,
        lunarLeapMonth = lunarDate?.isLeapMonth ?: false,
        repeatFrequency = recurrence.frequency.storageKey,
        repeatInterval = recurrence.interval,
        repeatEndDate = recurrence.endDate?.toString(),
        isArchived = isArchived,
        keepVisibleWhenExpired = keepVisibleWhenExpired,
        externalSource = externalIdentity?.source,
        externalKey = externalIdentity?.key,
    ),
    reminders = reminders.map { reminder ->
        ReminderEntity(
            id = reminder.id,
            entryId = id,
            daysBefore = reminder.daysBefore,
            time = reminder.time.toString(),
        )
    },
    tags = tags.map(DateTag::toEntity),
)

internal fun DateEntryWithDetails.toDateEntry(): DateEntry? = runCatching {
    val system = DateCalendarSystem.fromStored(entry.calendarSystem) ?: error("Unknown calendar system")
    DateEntry(
        id = entry.id,
        title = entry.title,
        note = entry.note,
        date = LocalDate.parse(entry.date),
        eventTime = entry.eventTime?.let(LocalTime::parse),
        eventTimeZone = entry.eventTimeZone,
        kind = DateKind.fromStored(entry.kind) ?: error("Unknown kind"),
        calendarSystem = system,
        lunarDate = if (system == DateCalendarSystem.ChineseLunar) {
            LunarDateSpec(
                year = checkNotNull(entry.lunarYear),
                month = checkNotNull(entry.lunarMonth),
                day = checkNotNull(entry.lunarDay),
                isLeapMonth = entry.lunarLeapMonth,
            ).also { require(it.isValidLunarDate() && it.toSolarDate() == LocalDate.parse(entry.date)) }
        } else {
            null
        },
        recurrence = RecurrenceRule(
            frequency = RepeatFrequency.fromStored(entry.repeatFrequency) ?: error("Unknown repeat frequency"),
            interval = entry.repeatInterval,
            endDate = entry.repeatEndDate?.let(LocalDate::parse),
        ),
        reminders = reminders.sortedWith(compareBy(ReminderEntity::daysBefore, ReminderEntity::time, ReminderEntity::id))
            .map { reminder -> EntryReminder(reminder.id, reminder.daysBefore, LocalTime.parse(reminder.time)) },
        tags = tags.map(DateTagEntity::toDateTag).sortedBy { it.name.lowercase() },
        isArchived = entry.isArchived,
        keepVisibleWhenExpired = entry.keepVisibleWhenExpired,
        externalIdentity = if (entry.externalSource != null && entry.externalKey != null) {
            ExternalIdentity(entry.externalSource, entry.externalKey)
        } else {
            null
        },
    )
}.getOrNull()

internal fun DateTag.toEntity() = DateTagEntity(id, name, colorArgb)
internal fun DateTagEntity.toDateTag() = DateTag(id, name, colorArgb)

internal fun DateDataSnapshot.toEntities() = DateDataEntities(
    entries = entries.map(DateEntry::toDetails),
    tags = tags.map(DateTag::toEntity),
)
