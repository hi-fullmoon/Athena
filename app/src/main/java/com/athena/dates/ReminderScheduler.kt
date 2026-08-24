package com.athena.dates

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

interface ReminderScheduler {
    fun schedule(entry: DateEntry)
    fun cancel(entryId: String)
}

class AndroidReminderScheduler(private val context: Context) : ReminderScheduler {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(entry: DateEntry) {
        val triggerAt = nextReminderAt(entry, ZonedDateTime.now())
        if (triggerAt == null) {
            cancel(entry.id)
            return
        }
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt.toInstant().toEpochMilli(),
            reminderPendingIntent(context, entry, PendingIntent.FLAG_UPDATE_CURRENT),
        )
    }

    override fun cancel(entryId: String) {
        val intent = Intent(context, ReminderReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            entryId.stableRequestCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }
}

fun nextReminderAt(
    entry: DateEntry,
    now: ZonedDateTime,
    zoneId: ZoneId = now.zone,
): ZonedDateTime? {
    if (!entry.reminderEnabled) return null
    if (!entry.repeatsYearly) {
        val trigger = entry.date.minusDays(entry.reminderDaysBefore.toLong()).atTime(entry.reminderTime).atZone(zoneId)
        return trigger.takeIf { it.isAfter(now) }
    }
    val firstYear = maxOf(entry.date.year, now.year)
    return (firstYear..firstYear + 2).firstNotNullOfOrNull { year ->
        val occurrence = entry.occurrenceInYear(year)
        if (occurrence.isBefore(entry.date)) null
        else occurrence.minusDays(entry.reminderDaysBefore.toLong()).atTime(entry.reminderTime).atZone(zoneId).takeIf { it.isAfter(now) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: context.getString(R.string.app_name)
        createReminderChannel(context)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            val openApp = PendingIntent.getActivity(
                context,
                id.stableRequestCode(),
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText("你记录的重要日子快到了")
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            NotificationManagerCompat.from(context).notify(REMINDER_NOTIFICATION_TAG, id.stableRequestCode(), notification)
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                RoomDateEntryRepository(context.applicationContext).entries.first()
                    .firstOrNull { it.id == id }
                    ?.takeIf { it.repeatsYearly }
                    ?.let(AndroidReminderScheduler(context.applicationContext)::schedule)
            }
            pendingResult.finish()
        }
    }
}

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val scheduler = AndroidReminderScheduler(context.applicationContext)
                RoomDateEntryRepository(context.applicationContext).entries.first().forEach(scheduler::schedule)
            }
            pendingResult.finish()
        }
    }
}

internal fun createReminderChannel(context: Context) {
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(REMINDER_CHANNEL_ID, "重要日期提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "纪念日、倒数日和日程提醒"
        },
    )
}

private fun reminderPendingIntent(context: Context, entry: DateEntry, flags: Int) = PendingIntent.getBroadcast(
    context,
    entry.id.stableRequestCode(),
    Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_ID, entry.id).putExtra(EXTRA_TITLE, entry.title),
    flags or PendingIntent.FLAG_IMMUTABLE,
)

private fun String.stableRequestCode(): Int = hashCode() and Int.MAX_VALUE

internal const val REMINDER_CHANNEL_ID = "date_reminders"
internal const val REMINDER_NOTIFICATION_TAG = "athena-reminder"
internal const val EXTRA_ID = "entry_id"
internal const val EXTRA_TITLE = "entry_title"
