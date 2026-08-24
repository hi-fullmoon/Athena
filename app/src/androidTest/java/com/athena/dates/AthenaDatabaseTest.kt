package com.athena.dates

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AthenaDatabaseTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AthenaDatabase::class.java,
    )

    private lateinit var context: Context
    private var database: AthenaDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun daoSupportsCreateReadUpdateAndDelete() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AthenaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { database = it }
        val dao = db.dateEntryDao()
        val original = entity(id = "crud")

        assertEquals(1L, dao.insert(original))
        assertEquals(original, dao.getById("crud"))
        assertEquals(1, dao.update(original.copy(title = "更新后", reminderEnabled = true)))
        assertEquals("更新后", dao.observeAll().first().single().title)
        assertEquals(1, dao.deleteById("crud"))
        assertNull(dao.getById("crud"))
    }

    @Test
    fun migrationFromV1PreservesDataAndSuppliesReminderDefaults() = runBlocking {
        migrationHelper.createDatabase(DATABASE_NAME, 1).apply {
            execSQL("INSERT INTO date_entries VALUES ('legacy', '旧纪念日', '', '2020-08-20', 'anniversary', 1)")
            close()
        }

        val migratedDatabase: SupportSQLiteDatabase = migrationHelper.runMigrationsAndValidate(
            DATABASE_NAME,
            2,
            true,
            AthenaDatabase.MIGRATION_1_2,
        )
        migratedDatabase.query("SELECT * FROM date_entries WHERE id = 'legacy'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("旧纪念日", cursor.getString(cursor.getColumnIndexOrThrow("title")))
            assertFalse(cursor.getInt(cursor.getColumnIndexOrThrow("reminderEnabled")) != 0)
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("reminderDaysBefore")))
            assertEquals("09:00", cursor.getString(cursor.getColumnIndexOrThrow("reminderTime")))
        }
        migratedDatabase.close()
    }

    private fun entity(id: String) = DateEntryEntity(
        id = id,
        title = "标题",
        note = "备注",
        date = "2026-08-24",
        kind = "anniversary",
        repeatsYearly = true,
        reminderEnabled = false,
        reminderDaysBefore = 0,
        reminderTime = "09:00",
    )

    private companion object {
        const val DATABASE_NAME = "migration-test.db"
    }
}
