package com.athena.dates

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class UpcomingDatesWidgetRemoteViewsTest {
    @Test
    fun remoteViewsInflateAllRowsAndKeepRefreshTouchTarget() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val items = (1..3).map { index ->
            UpcomingWidgetItem(
                id = "item-$index",
                title = "日期 $index",
                occurrenceDate = LocalDate.of(2030, 1, index),
                relativeLabel = "还有 $index 天",
            )
        }
        lateinit var root: View
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            root = upcomingDatesRemoteViews(context, items).apply(context, FrameLayout(context))
        }

        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_empty).visibility)
        assertEquals("日期 1", root.findViewById<TextView>(R.id.widget_title_1).text.toString())
        assertEquals("还有 3 天", root.findViewById<TextView>(R.id.widget_relative_3).text.toString())
        val refresh = root.findViewById<View>(R.id.widget_refresh)
        assertTrue(refresh.layoutParams.height >= (48 * context.resources.displayMetrics.density).toInt())
    }

    @Test
    fun largeRemoteViewsInflateSixRowsAndWidgetConfigurationIsPerIdAndRemovable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val items = (1..6).map { index ->
            UpcomingWidgetItem("item-$index", "日期 $index", LocalDate.of(2030, 2, index), "$index 天后")
        }
        val store = WidgetConfigurationStore(context)
        val configuration = WidgetConfiguration(
            kinds = setOf(DateKind.Anniversary),
            tagIds = setOf("family"),
            displayCount = 6,
            style = WidgetStyle.Transparent,
        )
        store.save(101, configuration)
        store.save(202, WidgetConfiguration(displayCount = 1))
        lateinit var root: View
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            root = upcomingDatesRemoteViews(context, items, configuration, large = true, widgetId = 101)
                .apply(context, FrameLayout(context))
        }

        assertEquals("日期 6", root.findViewById<TextView>(R.id.widget_title_6).text.toString())
        assertEquals(configuration, store.load(101))
        assertEquals(1, store.load(202).displayCount)
        store.delete(101)
        assertEquals(WidgetConfiguration(), store.load(101))
        store.delete(202)
    }
}
