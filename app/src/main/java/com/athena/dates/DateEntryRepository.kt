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

interface DateEntryRepository {
    val entries: Flow<List<DateEntry>>
    suspend fun upsert(entry: DateEntry)
    suspend fun delete(id: String)
}

internal class RoomDateEntryRepository(
    context: Context,
    private val dao: DateEntryDao = AthenaDatabase.getInstance(context).dateEntryDao(),
) : DateEntryRepository {
    private val preferences = context.getSharedPreferences("athena_dates", Context.MODE_PRIVATE)
    private val migrationMutex = Mutex()

    override val entries: Flow<List<DateEntry>> = flow {
        migrateLegacyEntriesIfNeeded()
        emitAll(dao.observeAll().map { rows -> rows.mapNotNull(DateEntryEntity::toDateEntry) })
    }.distinctUntilChanged()

    override suspend fun upsert(entry: DateEntry) {
        migrateLegacyEntriesIfNeeded()
        dao.upsert(entry.toEntity())
    }

    override suspend fun delete(id: String) {
        migrateLegacyEntriesIfNeeded()
        dao.deleteById(id)
    }

    private suspend fun migrateLegacyEntriesIfNeeded() = migrationMutex.withLock {
        if (preferences.getBoolean(KEY_ROOM_MIGRATION_COMPLETE, false)) return@withLock
        val raw = preferences.getString(KEY_ENTRIES, null)
        val legacyData = withContext(Dispatchers.IO) { raw?.let(::parseLegacyEntries) }
        legacyData?.entries?.map(DateEntry::toEntity)?.takeIf { it.isNotEmpty() }?.let {
            dao.insertLegacyEntries(it)
        }
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
                        repeatsYearly = item.optBoolean("repeatsYearly"),
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
}

private data class LegacyData(val entries: List<DateEntry>, val needsRecoveryCopy: Boolean)

internal fun DateEntry.toEntity() = DateEntryEntity(
    id = id,
    title = title,
    note = note,
    date = date.toString(),
    kind = kind.storageKey,
    repeatsYearly = repeatsYearly,
)

internal fun DateEntryEntity.toDateEntry(): DateEntry? = runCatching {
    DateEntry(
        id = id,
        title = title,
        note = note,
        date = LocalDate.parse(date),
        kind = DateKind.fromStored(kind) ?: error("Unknown kind"),
        repeatsYearly = repeatsYearly,
    )
}.getOrNull()
