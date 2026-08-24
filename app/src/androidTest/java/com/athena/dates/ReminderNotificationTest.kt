package com.athena.dates

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    }

    @Test
    fun reminderBroadcastPostsLocalNotification() {
        context.sendBroadcast(
            Intent(context, ReminderReceiver::class.java)
                .putExtra(EXTRA_ID, "e2e-notification")
                .putExtra(EXTRA_TITLE, "本地通知端到端验证"),
        )

        val posted = (1..20).firstNotNullOfOrNull {
            notificationManager.activeNotifications
                .firstOrNull { notification -> notification.tag == REMINDER_NOTIFICATION_TAG }
                .also { notification -> if (notification == null) Thread.sleep(100) }
        }

        assertNotNull(posted)
        assertEquals("本地通知端到端验证", posted!!.notification.extras.getString("android.title"))
    }
}
