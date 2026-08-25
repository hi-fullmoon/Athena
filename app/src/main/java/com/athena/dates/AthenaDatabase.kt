package com.athena.dates

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "date_entries")
internal data class DateEntryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val note: String,
    val date: String,
    val eventTime: String? = null,
    val eventTimeZone: String? = null,
    val kind: String,
    val calendarSystem: String = DateCalendarSystem.Gregorian.storageKey,
    val lunarYear: Int? = null,
    val lunarMonth: Int? = null,
    val lunarDay: Int? = null,
    val lunarLeapMonth: Boolean = false,
    val repeatFrequency: String = RepeatFrequency.None.storageKey,
    val repeatInterval: Int = 1,
    val repeatEndDate: String? = null,
    val isArchived: Boolean = false,
    val keepVisibleWhenExpired: Boolean = false,
    val externalSource: String? = null,
    val externalKey: String? = null,
)

@Entity(
    tableName = "reminder_snoozes",
    primaryKeys = ["entryId", "reminderId", "occurrenceDate"],
    foreignKeys = [
        ForeignKey(
            entity = DateEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entryId")],
)
internal data class ReminderSnoozeEntity(
    val entryId: String,
    val reminderId: String,
    val occurrenceDate: String,
    val triggerAtEpochMillis: Long,
)

@Entity(
    tableName = "entry_reminders",
    primaryKeys = ["id", "entryId"],
    foreignKeys = [
        ForeignKey(
            entity = DateEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entryId")],
)
internal data class ReminderEntity(
    val id: String,
    val entryId: String,
    val daysBefore: Int,
    val time: String,
    val lastNotifiedReminderKey: String? = null,
)

@Entity(
    tableName = "date_tags",
    indices = [Index(value = ["name"], unique = true)],
)
internal data class DateTagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorArgb: Int,
)

@Entity(
    tableName = "date_entry_tags",
    primaryKeys = ["entryId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = DateEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DateTagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId")],
)
internal data class DateEntryTagCrossRef(
    val entryId: String,
    val tagId: String,
)

internal data class DateEntryWithDetails(
    @Embedded val entry: DateEntryEntity,
    @Relation(parentColumn = "id", entityColumn = "entryId")
    val reminders: List<ReminderEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = DateEntryTagCrossRef::class,
            parentColumn = "entryId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<DateTagEntity>,
)

internal data class DateDataEntities(
    val entries: List<DateEntryWithDetails>,
    val tags: List<DateTagEntity>,
)

@Dao
internal interface DateEntryDao {
    @Transaction
    @Query("SELECT * FROM date_entries ORDER BY date, id")
    fun observeAll(): Flow<List<DateEntryWithDetails>>

    @Transaction
    @Query("SELECT * FROM date_entries WHERE id = :id")
    suspend fun getById(id: String): DateEntryWithDetails?

    @Transaction
    @Query("SELECT * FROM date_entries ORDER BY date, id")
    suspend fun getAll(): List<DateEntryWithDetails>

    @Query("SELECT * FROM date_tags ORDER BY name COLLATE NOCASE, id")
    fun observeTags(): Flow<List<DateTagEntity>>

    @Query("SELECT * FROM date_tags ORDER BY name COLLATE NOCASE, id")
    suspend fun getAllTags(): List<DateTagEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntry(entry: DateEntryEntity): Long

    @Update
    suspend fun updateEntry(entry: DateEntryEntity): Int

    @Upsert
    suspend fun upsertEntryRow(entry: DateEntryEntity)

    @Upsert
    suspend fun upsertTags(tags: List<DateTagEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReminders(reminders: List<ReminderEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTagRefs(refs: List<DateEntryTagCrossRef>)

    @Query("SELECT * FROM entry_reminders WHERE entryId = :entryId")
    suspend fun getReminders(entryId: String): List<ReminderEntity>

    @Query("DELETE FROM entry_reminders WHERE entryId = :entryId")
    suspend fun deleteReminders(entryId: String)

    @Query("DELETE FROM date_entry_tags WHERE entryId = :entryId")
    suspend fun deleteTagRefs(entryId: String)

    @Upsert
    suspend fun upsertSnooze(snooze: ReminderSnoozeEntity)

    @Query("SELECT * FROM reminder_snoozes ORDER BY triggerAtEpochMillis")
    suspend fun getAllSnoozes(): List<ReminderSnoozeEntity>

    @Query("SELECT * FROM reminder_snoozes WHERE entryId = :entryId AND reminderId = :reminderId AND occurrenceDate = :occurrenceDate")
    suspend fun getSnooze(entryId: String, reminderId: String, occurrenceDate: String): ReminderSnoozeEntity?

    @Query("DELETE FROM reminder_snoozes WHERE entryId = :entryId AND reminderId = :reminderId AND occurrenceDate = :occurrenceDate")
    suspend fun deleteSnooze(entryId: String, reminderId: String, occurrenceDate: String): Int

    @Query("DELETE FROM reminder_snoozes WHERE entryId = :entryId AND reminderId NOT IN (:reminderIds)")
    suspend fun deleteOrphanSnoozes(entryId: String, reminderIds: List<String>): Int

    @Query("DELETE FROM reminder_snoozes WHERE entryId = :entryId")
    suspend fun deleteAllSnoozesForEntry(entryId: String): Int

    @Transaction
    suspend fun upsertEntry(details: DateEntryWithDetails, preserveReminderDelivery: Boolean = true) {
        val deliveryById = if (preserveReminderDelivery) {
            getReminders(details.entry.id).associate { it.id to it.lastNotifiedReminderKey }
        } else {
            emptyMap()
        }
        upsertEntryRow(details.entry)
        if (details.tags.isNotEmpty()) upsertTags(details.tags)
        deleteReminders(details.entry.id)
        deleteTagRefs(details.entry.id)
        if (details.reminders.isNotEmpty()) {
            insertReminders(
                details.reminders.map { reminder ->
                    reminder.copy(lastNotifiedReminderKey = deliveryById[reminder.id])
                },
            )
        }
        if (details.tags.isNotEmpty()) {
            insertTagRefs(details.tags.map { DateEntryTagCrossRef(details.entry.id, it.id) })
        }
        if (details.reminders.isEmpty()) {
            deleteAllSnoozesForEntry(details.entry.id)
        } else {
            deleteOrphanSnoozes(details.entry.id, details.reminders.map(ReminderEntity::id))
        }
    }

    @Query("DELETE FROM date_entries WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM date_entries")
    suspend fun deleteAllEntries()

    @Query("DELETE FROM date_tags")
    suspend fun deleteAllTags()

    @Query("DELETE FROM date_tags WHERE id = :id")
    suspend fun deleteTagById(id: String): Int

    @Query(
        """
        UPDATE date_entries
        SET isArchived = 1
        WHERE kind = 'countdown'
          AND repeatFrequency = 'none'
          AND date < :today
          AND isArchived = 0
          AND keepVisibleWhenExpired = 0
        """,
    )
    suspend fun archiveExpiredCountdowns(today: String): Int

    @Query(
        """
        UPDATE date_entries
        SET isArchived = 0, keepVisibleWhenExpired = 1
        WHERE id = :id AND isArchived = 1
        """,
    )
    suspend fun restoreArchived(id: String): Int

    @Transaction
    suspend fun applyImport(
        data: DateDataEntities,
        replace: Boolean,
        archiveBeforeDate: String? = null,
    ) {
        if (replace) {
            deleteAllEntries()
            deleteAllTags()
        }
        if (data.tags.isNotEmpty()) upsertTags(data.tags)
        data.entries.forEach { upsertEntry(it, preserveReminderDelivery = !replace) }
        if (archiveBeforeDate != null) archiveExpiredCountdowns(archiveBeforeDate)
    }

    @Query(
        """
        UPDATE entry_reminders
        SET lastNotifiedReminderKey = :deliveryKey
        WHERE id = :reminderId
          AND entryId = :entryId
          AND daysBefore = :daysBefore
          AND time = :time
          AND (lastNotifiedReminderKey IS NULL OR lastNotifiedReminderKey != :deliveryKey)
        """,
    )
    suspend fun claimReminderDelivery(
        entryId: String,
        reminderId: String,
        daysBefore: Int,
        time: String,
        deliveryKey: String,
    ): Int

    @Query(
        """
        UPDATE entry_reminders
        SET lastNotifiedReminderKey = NULL
        WHERE id = :reminderId AND entryId = :entryId AND lastNotifiedReminderKey = :deliveryKey
        """,
    )
    suspend fun releaseReminderDelivery(entryId: String, reminderId: String, deliveryKey: String): Int
}

@Database(
    entities = [
        DateEntryEntity::class,
        ReminderEntity::class,
        ReminderSnoozeEntity::class,
        DateTagEntity::class,
        DateEntryTagCrossRef::class,
    ],
    version = 6,
    exportSchema = true,
)
internal abstract class AthenaDatabase : RoomDatabase() {
    abstract fun dateEntryDao(): DateEntryDao

    companion object {
        @Volatile
        private var instance: AthenaDatabase? = null

        fun getInstance(context: Context): AthenaDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AthenaDatabase::class.java,
                "athena.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build().also { instance = it }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE date_entries ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE date_entries ADD COLUMN reminderDaysBefore INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE date_entries ADD COLUMN reminderTime TEXT NOT NULL DEFAULT '09:00'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE date_entries
                    SET reminderDaysBefore = CASE
                        WHEN reminderDaysBefore <= 0 THEN 0
                        WHEN reminderDaysBefore <= 2 THEN 1
                        WHEN reminderDaysBefore <= 5 THEN 3
                        ELSE 7
                    END
                    """.trimIndent(),
                )
                db.execSQL("ALTER TABLE date_entries ADD COLUMN lastNotifiedReminderKey TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE date_entries ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE date_entries ADD COLUMN keepVisibleWhenExpired INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE date_entries RENAME TO date_entries_legacy")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS date_entries (
                        id TEXT NOT NULL,
                        title TEXT NOT NULL,
                        note TEXT NOT NULL,
                        date TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        calendarSystem TEXT NOT NULL,
                        lunarYear INTEGER,
                        lunarMonth INTEGER,
                        lunarDay INTEGER,
                        lunarLeapMonth INTEGER NOT NULL,
                        repeatFrequency TEXT NOT NULL,
                        repeatInterval INTEGER NOT NULL,
                        repeatEndDate TEXT,
                        isArchived INTEGER NOT NULL,
                        keepVisibleWhenExpired INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO date_entries (
                        id, title, note, date, kind, calendarSystem,
                        lunarLeapMonth, repeatFrequency, repeatInterval, isArchived, keepVisibleWhenExpired
                    )
                    SELECT id, title, note, date, kind, 'gregorian',
                        0, CASE WHEN repeatsYearly = 1 THEN 'yearly' ELSE 'none' END,
                        1, isArchived, keepVisibleWhenExpired
                    FROM date_entries_legacy
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS entry_reminders (
                        id TEXT NOT NULL,
                        entryId TEXT NOT NULL,
                        daysBefore INTEGER NOT NULL,
                        time TEXT NOT NULL,
                        lastNotifiedReminderKey TEXT,
                        PRIMARY KEY(id, entryId),
                        FOREIGN KEY(entryId) REFERENCES date_entries(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_entry_reminders_entryId ON entry_reminders(entryId)")
                db.execSQL(
                    """
                    INSERT INTO entry_reminders (id, entryId, daysBefore, time, lastNotifiedReminderKey)
                    SELECT id || ':legacy-reminder', id, reminderDaysBefore, reminderTime, lastNotifiedReminderKey
                    FROM date_entries_legacy
                    WHERE reminderEnabled = 1
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS date_tags (
                        id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        colorArgb INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_date_tags_name ON date_tags(name)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS date_entry_tags (
                        entryId TEXT NOT NULL,
                        tagId TEXT NOT NULL,
                        PRIMARY KEY(entryId, tagId),
                        FOREIGN KEY(entryId) REFERENCES date_entries(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(tagId) REFERENCES date_tags(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_date_entry_tags_tagId ON date_entry_tags(tagId)")
                db.execSQL("DROP TABLE date_entries_legacy")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE date_entries ADD COLUMN eventTime TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE date_entries ADD COLUMN eventTimeZone TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE date_entries ADD COLUMN externalSource TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE date_entries ADD COLUMN externalKey TEXT DEFAULT NULL")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS reminder_snoozes (
                        entryId TEXT NOT NULL,
                        reminderId TEXT NOT NULL,
                        occurrenceDate TEXT NOT NULL,
                        triggerAtEpochMillis INTEGER NOT NULL,
                        PRIMARY KEY(entryId, reminderId, occurrenceDate),
                        FOREIGN KEY(entryId) REFERENCES date_entries(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reminder_snoozes_entryId ON reminder_snoozes(entryId)")
            }
        }
    }
}
