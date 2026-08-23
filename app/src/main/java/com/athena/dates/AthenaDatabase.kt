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
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "date_entries")
internal data class DateEntryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val note: String,
    val date: String,
    val kind: String,
    val repeatsYearly: Boolean,
)

@Dao
internal interface DateEntryDao {
    @Query("SELECT * FROM date_entries ORDER BY date, id")
    fun observeAll(): Flow<List<DateEntryEntity>>

    @Upsert
    suspend fun upsert(entry: DateEntryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLegacyEntries(entries: List<DateEntryEntity>)

    @Query("DELETE FROM date_entries WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Database(
    entities = [DateEntryEntity::class],
    version = 1,
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
            ).build().also { instance = it }
        }
    }
}
