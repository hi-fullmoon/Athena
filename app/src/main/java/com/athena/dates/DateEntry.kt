package com.athena.dates

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
)

class AthenaStore(context: Context) {
    private val preferences = context.getSharedPreferences("athena_dates", Context.MODE_PRIVATE)
    private val dateEntryDao = AthenaDatabase.getInstance(context).dateEntryDao()
    private val migrationMutex = Mutex()

    val entries: Flow<List<DateEntry>> = flow {
        migrateLegacyEntriesIfNeeded()
        emitAll(
            dateEntryDao.observeAll().map { entities ->
                entities.mapNotNull(DateEntryEntity::toDateEntry)
            },
        )
    }.distinctUntilChanged()

    suspend fun upsert(entry: DateEntry) {
        migrateLegacyEntriesIfNeeded()
        dateEntryDao.upsert(entry.toEntity())
    }

    suspend fun delete(id: String) {
        migrateLegacyEntriesIfNeeded()
        dateEntryDao.deleteById(id)
    }

    fun loadPaletteName(): String? = preferences.getString("palette", null)

    fun savePaletteName(name: String) {
        preferences.edit { putString("palette", name) }
    }

    private suspend fun migrateLegacyEntriesIfNeeded() = migrationMutex.withLock {
        if (preferences.getBoolean(KEY_ROOM_MIGRATION_COMPLETE, false)) return@withLock

        val raw = preferences.getString(KEY_ENTRIES, null)
        val legacyData = withContext(Dispatchers.IO) { raw?.let(::parseLegacyEntries) }
        legacyData?.entries
            ?.map(DateEntry::toEntity)
            ?.takeIf { it.isNotEmpty() }
            ?.let { dateEntryDao.insertLegacyEntries(it) }

        withContext(Dispatchers.IO) {
            preferences.edit {
                if (raw != null && legacyData?.needsRecoveryCopy != false) {
                    putString(KEY_RECOVERY, raw)
                }
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
                val rawItem = json.get(index)
                runCatching {
                    val item = rawItem as? JSONObject ?: error("Date entry must be an object")
                    val schemaVersion = item.optInt("schemaVersion", 0)
                    require(schemaVersion in 0..CURRENT_SCHEMA_VERSION) { "Unsupported schema version" }
                    val id = item.getString("id").also {
                        require(it.isNotBlank()) { "Missing id" }
                    }
                    DateEntry(
                        id = id,
                        title = item.getString("title"),
                        note = item.optString("note"),
                        date = LocalDate.parse(item.getString("date")),
                        kind = DateKind.fromStored(item.getString("kind"))
                            ?: error("Unknown date kind"),
                        repeatsYearly = item.optBoolean("repeatsYearly"),
                    ).also { require(seenIds.add(id)) { "Duplicate id" } }
                }.onSuccess(::add).onFailure { needsRecoveryCopy = true }
            }
        }
        return LegacyData(loadedEntries, needsRecoveryCopy)
    }

    private companion object {
        const val KEY_ENTRIES = "entries"
        const val KEY_RECOVERY = "entries_recovery"
        const val KEY_ROOM_MIGRATION_COMPLETE = "room_migration_complete"
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

private data class LegacyData(
    val entries: List<DateEntry>,
    val needsRecoveryCopy: Boolean,
)

private fun DateEntry.toEntity() = DateEntryEntity(
    id = id,
    title = title,
    note = note,
    date = date.toString(),
    kind = kind.storageKey,
    repeatsYearly = repeatsYearly,
)

private fun DateEntryEntity.toDateEntry(): DateEntry? = runCatching {
    DateEntry(
        id = id,
        title = title,
        note = note,
        date = LocalDate.parse(date),
        kind = DateKind.fromStored(kind) ?: error("Unknown date kind"),
        repeatsYearly = repeatsYearly,
    )
}.getOrNull()
