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
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

interface ReminderScheduler {
    fun schedule(entry: DateEntry)
    fun cancel(entryId: String)
}

data class ReminderSchedule(
    val reminder: EntryReminder,
    val occurrenceDate: LocalDate,
    val triggerAt: ZonedDateTime,
) {
    val deliveryKey: String = occurrenceDate.toString()
}

class AndroidReminderScheduler(private val context: Context) : ReminderScheduler {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    private val registry = appContext.getSharedPreferences(REMINDER_REGISTRY, Context.MODE_PRIVATE)

    override fun schedule(entry: DateEntry) {
        cancelLegacyPendingIntent(entry.id)
        val previousIds = registry.getStringSet(entry.id, emptySet()).orEmpty()
        val schedules = nextReminderSchedules(entry, ZonedDateTime.now()).associateBy { it.reminder.id }
        (previousIds - schedules.keys).forEach { cancelReminder(entry.id, it) }
        schedules.values.forEach { schedule ->
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                schedule.triggerAt.toInstant().toEpochMilli(),
                reminderPendingIntent(appContext, entry.id, schedule, PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }
        registry.edit {
            if (schedules.isEmpty()) remove(entry.id) else putStringSet(entry.id, schedules.keys)
        }
    }

    override fun cancel(entryId: String) {
        registry.getStringSet(entryId, emptySet()).orEmpty().forEach { cancelReminder(entryId, it) }
        registry.edit { remove(entryId) }
        cancelLegacyPendingIntent(entryId)
    }

    private fun cancelReminder(entryId: String, reminderId: String) {
        PendingIntent.getBroadcast(
            appContext,
            0,
            reminderIntent(appContext, entryId, reminderId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let { pendingIntent ->
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun cancelLegacyPendingIntent(entryId: String) {
        PendingIntent.getBroadcast(
            appContext,
            0,
            Intent(appContext, ReminderReceiver::class.java)
                .setAction(ACTION_DELIVER_REMINDER)
                .setData(legacyReminderUri(entryId)),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let { pendingIntent ->
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}

fun nextReminderSchedules(
    entry: DateEntry,
    now: ZonedDateTime,
    zoneId: ZoneId = now.zone,
): List<ReminderSchedule> = entry.reminders.mapNotNull { reminder ->
    nextReminderSchedule(entry, reminder, now, zoneId)
}.sortedBy(ReminderSchedule::triggerAt)

fun nextReminderSchedule(
    entry: DateEntry,
    reminder: EntryReminder,
    now: ZonedDateTime,
    zoneId: ZoneId = now.zone,
): ReminderSchedule? {
    var occurrence = entry.nextOccurrence(now.toLocalDate().plusDays(reminder.daysBefore.toLong())) ?: return null
    repeat(MAX_REMINDER_OCCURRENCE_SEARCH) {
        val schedule = reminderScheduleForOccurrence(entry, reminder, occurrence, zoneId)
        if (schedule != null && schedule.triggerAt.isAfter(now)) return schedule
        occurrence = entry.nextOccurrence(occurrence.plusDays(1)) ?: return null
    }
    return null
}

fun nextReminderSchedule(
    entry: DateEntry,
    now: ZonedDateTime,
    zoneId: ZoneId = now.zone,
): ReminderSchedule? = nextReminderSchedules(entry, now, zoneId).minByOrNull(ReminderSchedule::triggerAt)

fun nextReminderAt(
    entry: DateEntry,
    now: ZonedDateTime,
    zoneId: ZoneId = now.zone,
): ZonedDateTime? = nextReminderSchedule(entry, now, zoneId)?.triggerAt

internal fun reminderScheduleForOccurrence(
    entry: DateEntry,
    reminder: EntryReminder,
    occurrenceDate: LocalDate,
    zoneId: ZoneId,
): ReminderSchedule? {
    if (reminder !in entry.reminders || !entry.occursOn(occurrenceDate)) return null
    return ReminderSchedule(
        reminder = reminder,
        occurrenceDate = occurrenceDate,
        triggerAt = occurrenceDate.minusDays(reminder.daysBefore.toLong()).atTime(reminder.time).atZone(zoneId),
    )
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DELIVER_REMINDER) return
        val entryId = intent.getStringExtra(EXTRA_ID) ?: return
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val occurrenceDate = intent.getStringExtra(EXTRA_OCCURRENCE_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return
        val triggerAtMillis = intent.getLongExtra(EXTRA_TRIGGER_AT_MILLIS, Long.MIN_VALUE)
        if (triggerAtMillis == Long.MIN_VALUE) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val dao = AthenaDatabase.getInstance(appContext).dateEntryDao()
                val entry = dao.getById(entryId)?.toDateEntry() ?: return@launch
                val reminder = entry.reminders.firstOrNull { it.id == reminderId } ?: return@launch
                val schedule = reminderScheduleForOccurrence(entry, reminder, occurrenceDate, ZoneId.systemDefault())
                    ?: return@launch

                if (schedule.triggerAt.toInstant().toEpochMilli() != triggerAtMillis) return@launch
                if (schedule.triggerAt.toInstant().isAfter(Instant.now())) {
                    AndroidReminderScheduler(appContext).schedule(entry)
                    return@launch
                }

                createReminderChannel(appContext)
                if (canPostReminderNotifications(appContext)) {
                    val claimed = dao.claimReminderDelivery(
                        entryId = entry.id,
                        reminderId = reminder.id,
                        daysBefore = reminder.daysBefore,
                        time = reminder.time.toString(),
                        deliveryKey = schedule.deliveryKey,
                    )
                    if (claimed == 1) {
                        val latestEntry = dao.getById(entryId)?.toDateEntry()
                        val latestReminder = latestEntry?.reminders?.firstOrNull { it.id == reminderId }
                        val latestSchedule = if (latestEntry != null && latestReminder != null) {
                            reminderScheduleForOccurrence(latestEntry, latestReminder, occurrenceDate, ZoneId.systemDefault())
                        } else {
                            null
                        }
                        val isStillCurrent = latestSchedule?.triggerAt?.toInstant()?.toEpochMilli() == triggerAtMillis
                        if (!isStillCurrent || !postReminderNotification(appContext, checkNotNull(latestEntry), schedule)) {
                            dao.releaseReminderDelivery(entry.id, reminder.id, schedule.deliveryKey)
                        }
                    }
                }

                val scheduler = AndroidReminderScheduler(appContext)
                dao.getById(entryId)?.toDateEntry()?.let(scheduler::schedule) ?: scheduler.cancel(entryId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> Unit
            else -> return
        }
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val scheduler = AndroidReminderScheduler(appContext)
                val repository = RoomDateEntryRepository(appContext)
                repository.archiveExpired(LocalDate.now())
                repository.entries.first().forEach(scheduler::schedule)
                ReminderSnoozeScheduler(appContext).rebuild()
                runCatching { refreshUpcomingDatesWidget(appContext) }
            } finally {
                pendingResult.finish()
            }
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

internal fun canPostReminderNotifications(context: Context): Boolean {
    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    return context.getSystemService(NotificationManager::class.java)
        .getNotificationChannel(REMINDER_CHANNEL_ID)
        ?.importance
        ?.let { it != NotificationManager.IMPORTANCE_NONE }
        ?: false
}

internal fun postReminderNotification(
    context: Context,
    entry: DateEntry,
    schedule: ReminderSchedule,
    overrideMessage: String? = null,
): Boolean {
    if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }
    val openApp = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_REMINDER)
            .setData(reminderUri(entry.id, schedule.reminder.id)),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val daysUntil = ChronoUnit.DAYS.between(LocalDate.now(schedule.triggerAt.zone), schedule.occurrenceDate)
    val message = overrideMessage ?: when (daysUntil) {
        0L -> "就是今天"
        1L -> "将在明天到来"
        in 2L..Long.MAX_VALUE -> "还有 $daysUntil 天"
        else -> "你记录的重要日子到了"
    }
    val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(entry.title)
        .setContentText(message)
        .setContentIntent(openApp)
        .addAction(R.drawable.ic_launcher_foreground, "查看", openApp)
        .addAction(
            R.drawable.ic_launcher_foreground,
            "稍后 1 小时",
            snoozeRequestPendingIntent(context, entry.id, schedule.reminder.id, schedule.occurrenceDate),
        )
        .setAutoCancel(true)
        .setCategory(NotificationCompat.CATEGORY_EVENT)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .build()
    return try {
        NotificationManagerCompat.from(context).notify(
            reminderNotificationTag(entry.id, schedule.reminder.id),
            0,
            notification,
        )
        true
    } catch (_: SecurityException) {
        false
    }
}

private fun reminderPendingIntent(
    context: Context,
    entryId: String,
    schedule: ReminderSchedule,
    flags: Int,
) = PendingIntent.getBroadcast(
    context,
    0,
    reminderIntent(context, entryId, schedule.reminder.id, schedule),
    flags or PendingIntent.FLAG_IMMUTABLE,
)

internal fun reminderIntent(
    context: Context,
    entryId: String,
    reminderId: String,
    schedule: ReminderSchedule? = null,
) = Intent(context, ReminderReceiver::class.java)
    .setAction(ACTION_DELIVER_REMINDER)
    .setData(reminderUri(entryId, reminderId))
    .putExtra(EXTRA_ID, entryId)
    .putExtra(EXTRA_REMINDER_ID, reminderId)
    .apply {
        schedule?.let {
            putExtra(EXTRA_OCCURRENCE_DATE, it.occurrenceDate.toString())
            putExtra(EXTRA_TRIGGER_AT_MILLIS, it.triggerAt.toInstant().toEpochMilli())
        }
    }

private fun reminderUri(entryId: String, reminderId: String): Uri = Uri.Builder()
    .scheme("athena")
    .authority("reminder")
    .appendPath(entryId)
    .appendPath(reminderId)
    .build()

private fun legacyReminderUri(entryId: String): Uri = Uri.Builder()
    .scheme("athena")
    .authority("reminder")
    .appendPath(entryId)
    .build()

internal fun reminderNotificationTag(entryId: String, reminderId: String) =
    "$REMINDER_NOTIFICATION_TAG_PREFIX:$entryId:$reminderId"

private const val MAX_REMINDER_OCCURRENCE_SEARCH = 10_000
private const val REMINDER_REGISTRY = "scheduled_reminder_instances"
internal const val REMINDER_CHANNEL_ID = "date_reminders"
internal const val REMINDER_NOTIFICATION_TAG_PREFIX = "athena-reminder"
internal const val ACTION_DELIVER_REMINDER = "com.athena.dates.action.DELIVER_REMINDER"
internal const val ACTION_OPEN_REMINDER = "com.athena.dates.action.OPEN_REMINDER"
internal const val EXTRA_ID = "entry_id"
internal const val EXTRA_REMINDER_ID = "reminder_id"
internal const val EXTRA_OCCURRENCE_DATE = "occurrence_date"
internal const val EXTRA_TRIGGER_AT_MILLIS = "trigger_at_millis"

internal class ReminderSnoozeScheduler(private val context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    private val dao = AthenaDatabase.getInstance(appContext).dateEntryDao()

    suspend fun request(entryId: String, reminderId: String, occurrenceDate: LocalDate, nowMillis: Long = System.currentTimeMillis()) {
        val entry = dao.getById(entryId)?.toDateEntry() ?: return
        if (entry.reminders.none { it.id == reminderId } || !entry.occursOn(occurrenceDate)) return
        val snooze = ReminderSnoozeEntity(entryId, reminderId, occurrenceDate.toString(), nowMillis + SNOOZE_DURATION_MILLIS)
        dao.upsertSnooze(snooze)
        schedule(snooze)
    }

    suspend fun rebuild() {
        dao.getAllSnoozes().forEach(::schedule)
    }

    suspend fun deliver(entryId: String, reminderId: String, occurrenceDate: LocalDate, triggerAtMillis: Long) {
        val snooze = dao.getSnooze(entryId, reminderId, occurrenceDate.toString()) ?: return
        if (snooze.triggerAtEpochMillis != triggerAtMillis) return
        if (triggerAtMillis > System.currentTimeMillis()) {
            schedule(snooze)
            return
        }
        val entry = dao.getById(entryId)?.toDateEntry() ?: return
        val reminder = entry.reminders.firstOrNull { it.id == reminderId } ?: return
        if (!entry.occursOn(occurrenceDate)) return
        if (dao.deleteSnooze(entryId, reminderId, occurrenceDate.toString()) != 1) return
        createReminderChannel(appContext)
        if (canPostReminderNotifications(appContext)) {
            postReminderNotification(
                appContext,
                entry,
                ReminderSchedule(reminder, occurrenceDate, ZonedDateTime.now()),
                overrideMessage = "稍后提醒 · ${relativeDayLabel(occurrenceDate, LocalDate.now())}",
            )
        }
    }

    private fun schedule(snooze: ReminderSnoozeEntity) {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            snooze.triggerAtEpochMillis,
            snoozeDeliveryPendingIntent(appContext, snooze, PendingIntent.FLAG_UPDATE_CURRENT),
        )
    }
}

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(ACTION_SNOOZE_REMINDER, ACTION_DELIVER_SNOOZE)) return
        val entryId = intent.getStringExtra(EXTRA_ID) ?: return
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val occurrence = intent.getStringExtra(EXTRA_OCCURRENCE_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val scheduler = ReminderSnoozeScheduler(context.applicationContext)
                if (intent.action == ACTION_SNOOZE_REMINDER) {
                    scheduler.request(entryId, reminderId, occurrence)
                    NotificationManagerCompat.from(context).cancel(reminderNotificationTag(entryId, reminderId), 0)
                } else {
                    val trigger = intent.getLongExtra(EXTRA_TRIGGER_AT_MILLIS, Long.MIN_VALUE)
                    if (trigger != Long.MIN_VALUE) scheduler.deliver(entryId, reminderId, occurrence, trigger)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

internal fun snoozeRequestPendingIntent(
    context: Context,
    entryId: String,
    reminderId: String,
    occurrenceDate: LocalDate,
): PendingIntent = PendingIntent.getBroadcast(
    context,
    0,
    snoozeIntent(context, ACTION_SNOOZE_REMINDER, entryId, reminderId, occurrenceDate),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
)

private fun snoozeDeliveryPendingIntent(
    context: Context,
    snooze: ReminderSnoozeEntity,
    flags: Int,
): PendingIntent = PendingIntent.getBroadcast(
    context,
    0,
    snoozeIntent(
        context,
        ACTION_DELIVER_SNOOZE,
        snooze.entryId,
        snooze.reminderId,
        LocalDate.parse(snooze.occurrenceDate),
    ).putExtra(EXTRA_TRIGGER_AT_MILLIS, snooze.triggerAtEpochMillis),
    flags or PendingIntent.FLAG_IMMUTABLE,
)

private fun snoozeIntent(
    context: Context,
    action: String,
    entryId: String,
    reminderId: String,
    occurrenceDate: LocalDate,
) = Intent(context, ReminderActionReceiver::class.java)
    .setAction(action)
    .setData(
        Uri.Builder().scheme("athena").authority("snooze")
            .appendPath(entryId).appendPath(reminderId).appendPath(occurrenceDate.toString()).build(),
    )
    .putExtra(EXTRA_ID, entryId)
    .putExtra(EXTRA_REMINDER_ID, reminderId)
    .putExtra(EXTRA_OCCURRENCE_DATE, occurrenceDate.toString())

internal const val ACTION_SNOOZE_REMINDER = "com.athena.dates.action.SNOOZE_REMINDER"
internal const val ACTION_DELIVER_SNOOZE = "com.athena.dates.action.DELIVER_SNOOZE"
private const val SNOOZE_DURATION_MILLIS = 60L * 60L * 1_000L
