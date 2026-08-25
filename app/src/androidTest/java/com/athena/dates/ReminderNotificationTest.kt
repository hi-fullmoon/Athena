package com.athena.dates

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderNotificationTest {
    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
    }

    @After
    fun tearDown() {
        notificationManager.cancelAll()
        runBlocking { AthenaDatabase.getInstance(context).dateEntryDao().deleteById(ENTRY_ID) }
    }

    @Test
    fun reminderBroadcastPostsLocalNotification() {
        val entry = DateEntry(
            id = ENTRY_ID,
            title = "本地通知端到端验证",
            note = "",
            date = LocalDate.now().minusDays(1),
            kind = DateKind.Anniversary,
            reminders = listOf(EntryReminder(REMINDER_ID, time = LocalTime.NOON)),
        )
        val reminder = entry.reminders.single()
        val schedule = checkNotNull(reminderScheduleForOccurrence(entry, reminder, entry.date, ZoneId.systemDefault()))
        runBlocking { AthenaDatabase.getInstance(context).dateEntryDao().upsertEntry(entry.toDetails()) }

        context.sendBroadcast(reminderIntent(context, entry.id, reminder.id, schedule))

        val posted = (1..20).firstNotNullOfOrNull {
            notificationManager.activeNotifications
                .firstOrNull { notification -> notification.tag == reminderNotificationTag(entry.id, reminder.id) }
                .also { notification -> if (notification == null) Thread.sleep(100) }
        }

        assertNotNull(posted)
        assertEquals("本地通知端到端验证", posted!!.notification.extras.getString("android.title"))
        assertEquals(listOf("查看", "稍后 1 小时"), posted.notification.actions.map { it.title.toString() })
        posted.notification.actions[1].actionIntent.send()
        val snooze = (1..20).firstNotNullOfOrNull {
            runBlocking {
                AthenaDatabase.getInstance(context).dateEntryDao()
                    .getSnooze(entry.id, reminder.id, schedule.occurrenceDate.toString())
            }.also { value -> if (value == null) Thread.sleep(100) }
        }
        assertNotNull(snooze)

        notificationManager.cancel(reminderNotificationTag(entry.id, reminder.id), 0)
        context.sendBroadcast(reminderIntent(context, entry.id, reminder.id, schedule))
        Thread.sleep(500)
        assertNull(
            notificationManager.activeNotifications
                .firstOrNull { notification -> notification.tag == reminderNotificationTag(entry.id, reminder.id) },
        )
    }

    private companion object {
        const val ENTRY_ID = "e2e-notification"
        const val REMINDER_ID = "e2e-reminder"
    }
}
