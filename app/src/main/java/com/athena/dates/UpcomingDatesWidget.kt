package com.athena.dates

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class UpcomingWidgetItem(
    val id: String,
    val title: String,
    val occurrenceDate: LocalDate,
    val relativeLabel: String,
)

fun upcomingWidgetItems(
    entries: List<DateEntry>,
    reference: LocalDate,
    limit: Int = 3,
    kinds: Set<DateKind> = DateKind.entries.toSet(),
    tagIds: Set<String> = emptySet(),
): List<UpcomingWidgetItem> = entries.asSequence()
    .filterNot(DateEntry::isArchived)
    .filter { it.kind in kinds }
    .filter { tagIds.isEmpty() || it.tags.any { tag -> tag.id in tagIds } }
    .mapNotNull { entry -> entry.nextOccurrence(reference)?.let { entry to it } }
    .sortedWith(compareBy<Pair<DateEntry, LocalDate>> { it.second }.thenBy { it.first.title }.thenBy { it.first.id })
    .take(limit)
    .map { (entry, occurrence) ->
        UpcomingWidgetItem(entry.id, entry.title, occurrence, relativeDayLabel(occurrence, reference))
    }
    .toList()

internal fun interface WidgetRefresher {
    suspend fun refresh()
}

internal class AndroidWidgetRefresher(private val context: Context) : WidgetRefresher {
    override suspend fun refresh() = refreshUpcomingDatesWidget(context)
}

internal suspend fun refreshUpcomingDatesWidget(context: Context, requestedIds: IntArray? = null) {
    val appContext = context.applicationContext
    val manager = AppWidgetManager.getInstance(appContext)
    val ids = requestedIds ?: manager.getAppWidgetIds(ComponentName(appContext, UpcomingDatesWidgetReceiver::class.java))
    if (ids.isEmpty()) return
    val entries = withContext(Dispatchers.IO) { RoomDateEntryRepository(appContext).snapshot() }
    val store = WidgetConfigurationStore(appContext)
    ids.forEach { id ->
        val configuration = store.load(id)
        val items = upcomingWidgetItems(
            entries,
            LocalDate.now(),
            configuration.displayCount,
            configuration.kinds,
            configuration.tagIds,
        )
        val minHeight = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        manager.updateAppWidget(id, upcomingDatesRemoteViews(appContext, items, configuration, minHeight >= 240, id))
    }
}

class UpcomingDatesWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshAsync(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        refreshAsync(context, intArrayOf(appWidgetId))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val store = WidgetConfigurationStore(context.applicationContext)
        appWidgetIds.forEach(store::delete)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH_WIDGET) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            refreshAsync(context, id.takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }?.let { intArrayOf(it) })
        } else {
            super.onReceive(context, intent)
        }
    }

    private fun refreshAsync(context: Context, ids: IntArray? = null) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                refreshUpcomingDatesWidget(context, ids)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

internal fun upcomingDatesRemoteViews(
    context: Context,
    items: List<UpcomingWidgetItem>,
    configuration: WidgetConfiguration = WidgetConfiguration(),
    large: Boolean = false,
    widgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID,
): RemoteViews =
    RemoteViews(context.packageName, if (large) R.layout.upcoming_dates_widget_large else R.layout.upcoming_dates_widget).apply {
        setInt(
            R.id.widget_root,
            "setBackgroundResource",
            if (configuration.style == WidgetStyle.Transparent) R.drawable.widget_background_transparent else R.drawable.widget_background,
        )
        setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context,
                OPEN_APP_REQUEST_CODE,
                Intent(context, MainActivity::class.java).setAction(ACTION_OPEN_WIDGET),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        setOnClickPendingIntent(
            R.id.widget_refresh,
            PendingIntent.getBroadcast(
                context,
                if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) REFRESH_REQUEST_CODE else REFRESH_REQUEST_CODE + widgetId,
                Intent(context, UpcomingDatesWidgetReceiver::class.java)
                    .setAction(ACTION_REFRESH_WIDGET)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )

        setViewVisibility(R.id.widget_empty, if (items.isEmpty()) View.VISIBLE else View.GONE)
        val rows = buildList {
            add(Triple(R.id.widget_row_1, R.id.widget_title_1, R.id.widget_relative_1))
            add(Triple(R.id.widget_row_2, R.id.widget_title_2, R.id.widget_relative_2))
            add(Triple(R.id.widget_row_3, R.id.widget_title_3, R.id.widget_relative_3))
            if (large) {
                add(Triple(R.id.widget_row_4, R.id.widget_title_4, R.id.widget_relative_4))
                add(Triple(R.id.widget_row_5, R.id.widget_title_5, R.id.widget_relative_5))
                add(Triple(R.id.widget_row_6, R.id.widget_title_6, R.id.widget_relative_6))
            }
        }
        rows.forEachIndexed { index, (rowId, titleId, relativeId) ->
            val item = items.getOrNull(index)
            setViewVisibility(rowId, if (item == null) View.GONE else View.VISIBLE)
            if (item != null) {
                setTextViewText(titleId, item.title)
                setTextViewText(relativeId, item.relativeLabel)
            }
        }
    }

private const val ACTION_REFRESH_WIDGET = "com.athena.dates.action.REFRESH_WIDGET"
private const val ACTION_OPEN_WIDGET = "com.athena.dates.action.OPEN_WIDGET"
private const val OPEN_APP_REQUEST_CODE = 20_001
private const val REFRESH_REQUEST_CODE = 20_002
