package com.athena.dates

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
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
    val kind: String,
    val repeatsYearly: Boolean,
    val reminderEnabled: Boolean,
    val reminderDaysBefore: Int,
    val reminderTime: String,
)

@Dao
internal interface DateEntryDao {
    @Query("SELECT * FROM date_entries ORDER BY date, id")
    fun observeAll(): Flow<List<DateEntryEntity>>

    @Query("SELECT * FROM date_entries WHERE id = :id")
    suspend fun getById(id: String): DateEntryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: DateEntryEntity): Long

    @Update
    suspend fun update(entry: DateEntryEntity): Int

    @Upsert
    suspend fun upsert(entry: DateEntryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLegacyEntries(entries: List<DateEntryEntity>)

    @Query("DELETE FROM date_entries WHERE id = :id")
    suspend fun deleteById(id: String): Int
}

@Database(
    entities = [DateEntryEntity::class],
    version = 2,
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
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE date_entries ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE date_entries ADD COLUMN reminderDaysBefore INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE date_entries ADD COLUMN reminderTime TEXT NOT NULL DEFAULT '09:00'")
            }
        }
    }
}
