package com.athena.dates

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

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
    fun daoRoundTripsRecurrenceMultipleRemindersLunarAndTags() = runBlocking {
        val dao = inMemoryDatabase().dateEntryDao()
        val lunar = LunarDateSpec(2026, 1, 1)
        val tags = listOf(DateTag("family", "家人", DEFAULT_TAG_COLORS[0]))
        val original = DateEntry(
            id = "complete",
            title = "春节",
            note = "团圆",
            date = lunar.toSolarDate(),
            eventTime = LocalTime.of(18, 30),
            eventTimeZone = "Asia/Shanghai",
            kind = DateKind.Anniversary,
            calendarSystem = DateCalendarSystem.ChineseLunar,
            lunarDate = lunar,
            recurrence = RecurrenceRule(RepeatFrequency.Yearly, endDate = LocalDate.of(2100, 12, 31)),
            reminders = listOf(
                EntryReminder("week", 7, LocalTime.of(8, 0)),
                EntryReminder("day", 0, LocalTime.of(18, 30)),
            ),
            tags = tags,
            externalIdentity = ExternalIdentity(EXTERNAL_SOURCE_CALENDAR, "7:42"),
        )

        dao.upsertEntry(original.toDetails())

        assertEquals(
            original.copy(reminders = original.reminders.sortedWith(compareBy(EntryReminder::daysBefore, EntryReminder::time))),
            dao.getById(original.id)?.toDateEntry(),
        )
        assertEquals(tags, dao.observeTags().first().map(DateTagEntity::toDateTag))
        assertEquals(1, dao.deleteById(original.id))
        assertNull(dao.getById(original.id))
    }

    @Test
    fun migrationFromV4PreservesLegacyRuleArchiveAndSingleReminderDelivery() = runBlocking {
        migrationHelper.createDatabase(DATABASE_NAME, 4).apply {
            execSQL(
                "INSERT INTO date_entries VALUES " +
                    "('legacy', '旧纪念日', '备注', '2024-02-29', 'anniversary', 1, 1, 7, '08:30', '2026-02-28', 0, 0)",
            )
            execSQL(
                "INSERT INTO date_entries VALUES " +
                    "('archived', '旧归档', '', '2020-01-01', 'countdown', 0, 0, 0, '09:00', NULL, 1, 0)",
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            DATABASE_NAME,
            5,
            true,
            AthenaDatabase.MIGRATION_4_5,
        )
        migrated.query("SELECT * FROM date_entries WHERE id='legacy'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("gregorian", cursor.string("calendarSystem"))
            assertEquals("yearly", cursor.string("repeatFrequency"))
            assertEquals(1, cursor.int("repeatInterval"))
            assertFalse(cursor.int("isArchived") != 0)
        }
        migrated.query("SELECT * FROM entry_reminders WHERE entryId='legacy'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("legacy:legacy-reminder", cursor.string("id"))
            assertEquals(7, cursor.int("daysBefore"))
            assertEquals("08:30", cursor.string("time"))
            assertEquals("2026-02-28", cursor.string("lastNotifiedReminderKey"))
        }
        migrated.query("SELECT isArchived FROM date_entries WHERE id='archived'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.getInt(0) != 0)
        }
        migrated.close()
    }

    @Test
    fun migrationFromV1ThroughV5PreservesEntryAndAddsNormalizedDefaults() {
        migrationHelper.createDatabase(DATABASE_NAME, 1).apply {
            execSQL("INSERT INTO date_entries VALUES ('v1', '早期数据', '', '2026-08-25', 'countdown', 0)")
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            DATABASE_NAME,
            5,
            true,
            AthenaDatabase.MIGRATION_1_2,
            AthenaDatabase.MIGRATION_2_3,
            AthenaDatabase.MIGRATION_3_4,
            AthenaDatabase.MIGRATION_4_5,
        )
        migrated.query("SELECT * FROM date_entries WHERE id='v1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("none", cursor.string("repeatFrequency"))
            assertEquals("gregorian", cursor.string("calendarSystem"))
        }
        migrated.query("SELECT COUNT(*) FROM entry_reminders").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }

    @Test
    fun migrationFromV5ToV6AddsExternalTimeAndPersistentSnoozeWithoutChangingEntries() {
        migrationHelper.createDatabase(DATABASE_NAME, 5).apply {
            execSQL(
                "INSERT INTO date_entries VALUES " +
                    "('v5', '旧数据', '', '2026-08-25', 'schedule', 'gregorian', NULL, NULL, NULL, 0, 'none', 1, NULL, 0, 0)",
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            DATABASE_NAME,
            6,
            true,
            AthenaDatabase.MIGRATION_5_6,
        )
        migrated.query("SELECT * FROM date_entries WHERE id='v5'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("eventTime")))
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("externalSource")))
        }
        migrated.execSQL(
            "INSERT INTO reminder_snoozes VALUES ('v5', 'reminder', '2026-08-25', 123456)",
        )
        migrated.query("SELECT triggerAtEpochMillis FROM reminder_snoozes").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(123456L, cursor.getLong(0))
        }
        migrated.close()
    }

    @Test
    fun snoozeUpsertDeduplicatesAndEntryEditRemovesOnlyOrphans() = runBlocking {
        val dao = inMemoryDatabase().dateEntryDao()
        val entry = standardEntry("snooze").copy(
            reminders = listOf(EntryReminder("keep"), EntryReminder("remove", 1)),
        )
        dao.upsertEntry(entry.toDetails())
        dao.upsertSnooze(ReminderSnoozeEntity(entry.id, "keep", "2026-08-25", 100))
        dao.upsertSnooze(ReminderSnoozeEntity(entry.id, "keep", "2026-08-25", 200))
        dao.upsertSnooze(ReminderSnoozeEntity(entry.id, "remove", "2026-08-25", 300))

        assertEquals(2, dao.getAllSnoozes().size)
        assertEquals(200L, dao.getSnooze(entry.id, "keep", "2026-08-25")?.triggerAtEpochMillis)
        dao.upsertEntry(entry.copy(reminders = listOf(entry.reminders.first())).toDetails())

        assertEquals(listOf("keep"), dao.getAllSnoozes().map(ReminderSnoozeEntity::reminderId))
        dao.deleteById(entry.id)
        assertTrue(dao.getAllSnoozes().isEmpty())
    }

    @Test
    fun deliveryDedupeIsScopedToReminderInstanceAndSurvivesEntryEdit() = runBlocking {
        val dao = inMemoryDatabase().dateEntryDao()
        val entry = standardEntry("dedupe").copy(
            reminders = listOf(
                EntryReminder("first", 1, LocalTime.of(9, 0)),
                EntryReminder("second", 1, LocalTime.of(10, 0)),
            ),
        )
        dao.upsertEntry(entry.toDetails())

        suspend fun claim(id: String, time: String) = dao.claimReminderDelivery(
            entryId = entry.id,
            reminderId = id,
            daysBefore = 1,
            time = time,
            deliveryKey = "2026-08-25",
        )

        assertEquals(1, claim("first", "09:00"))
        assertEquals(0, claim("first", "09:00"))
        assertEquals(1, claim("second", "10:00"))
        dao.upsertEntry(entry.copy(title = "编辑后").toDetails())
        assertEquals(0, claim("first", "09:00"))
        assertEquals(1, dao.releaseReminderDelivery(entry.id, "first", "2026-08-25"))
        assertEquals(1, claim("first", "09:00"))
    }

    @Test
    fun archiveOnlyTargetsExpiredOneTimeCountdowns() = runBlocking {
        val dao = inMemoryDatabase().dateEntryDao()
        listOf(
            standardEntry("expired").copy(kind = DateKind.Countdown, date = LocalDate.of(2026, 8, 23)),
            standardEntry("weekly").copy(
                kind = DateKind.Countdown,
                date = LocalDate.of(2026, 8, 1),
                recurrence = RecurrenceRule(RepeatFrequency.Weekly),
            ),
            standardEntry("future").copy(kind = DateKind.Countdown, date = LocalDate.of(2026, 8, 26)),
        ).forEach { dao.upsertEntry(it.toDetails()) }

        assertEquals(1, dao.archiveExpiredCountdowns("2026-08-25"))
        assertTrue(checkNotNull(dao.getById("expired")).entry.isArchived)
        assertFalse(checkNotNull(dao.getById("weekly")).entry.isArchived)
    }

    @Test
    fun failedReplaceRollsBackEntriesTagsAndReminders() = runBlocking {
        val dao = inMemoryDatabase().dateEntryDao()
        val original = standardEntry("original")
        dao.upsertEntry(original.toDetails())
        val collision = standardEntry("collision").toDetails()
        val duplicateReminder = ReminderEntity("duplicate", "collision", 0, "09:00")
        val invalidData = DateDataEntities(
            entries = listOf(collision.copy(reminders = listOf(duplicateReminder, duplicateReminder))),
            tags = emptyList(),
        )

        val failure = runCatching { dao.applyImport(invalidData, replace = true) }

        assertTrue(failure.isFailure)
        assertEquals(listOf(original), dao.getAll().mapNotNull(DateEntryWithDetails::toDateEntry))
    }

    private fun inMemoryDatabase(): AthenaDatabase = Room.inMemoryDatabaseBuilder(context, AthenaDatabase::class.java)
        .allowMainThreadQueries()
        .build()
        .also { database = it }

    private fun standardEntry(id: String) = DateEntry(
        id = id,
        title = "标题",
        note = "备注",
        date = LocalDate.of(2026, 8, 25),
        kind = DateKind.Anniversary,
    )

    private fun android.database.Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column))
    private fun android.database.Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))

    private companion object {
        const val DATABASE_NAME = "athena-migration-test"
    }
}
