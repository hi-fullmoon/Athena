package com.athena.dates

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class DateCapabilitiesUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun calendarCellsKeepAccessibleHeightAndGrowWithLargeText() {
        val fontScale = mutableFloatStateOf(1f)
        val dateDescription = "2026年8月25日 星期二，无事项"
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = fontScale.floatValue)) {
                AthenaTheme(AthenaPalette.Violet) {
                    CalendarScreen(
                        entries = emptyList(),
                        today = LocalDate.of(2026, 8, 25),
                        month = YearMonth.of(2026, 8),
                        selectedDate = LocalDate.of(2026, 8, 25),
                        onMonthChange = {},
                        onDateSelected = {},
                        onEdit = {},
                        onDelete = {},
                    )
                }
            }
        }

        val normalHeight = composeRule.onNodeWithContentDescription(dateDescription)
            .fetchSemanticsNode().boundsInRoot.height
        assertTrue("Normal calendar target was only $normalHeight dp", normalHeight >= 48f)

        composeRule.runOnIdle { fontScale.floatValue = 2f }
        val largeTextHeight = composeRule.onNodeWithContentDescription(dateDescription)
            .fetchSemanticsNode().boundsInRoot.height
        assertTrue(
            "Large-text calendar target did not grow: $normalHeight -> $largeTextHeight dp",
            largeTextHeight > normalHeight,
        )
    }

    @Test
    fun searchUsesTheWholeVisibleSurfaceAsItsTouchTarget() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1f)) {
                AthenaTheme(AthenaPalette.Violet) {
                    EntrySearchControls(
                        query = EntryQuery(),
                        onQueryChange = {},
                        onReset = {},
                    )
                }
            }
        }

        val search = composeRule.onNodeWithContentDescription("搜索日子、备注")
        val searchBounds = search.fetchSemanticsNode().boundsInRoot
        assertTrue("Search target was only ${searchBounds.height} dp high", searchBounds.height >= 48f)
        search.performClick().assertIsFocused()
    }

    @Test
    fun monthActionsExposeFullTouchTargets() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1f)) {
                AthenaTheme(AthenaPalette.Violet) {
                    CalendarScreen(
                        entries = emptyList(),
                        today = LocalDate.of(2026, 8, 25),
                        month = YearMonth.of(2026, 7),
                        selectedDate = LocalDate.of(2026, 7, 25),
                        onMonthChange = {},
                        onDateSelected = {},
                        onEdit = {},
                        onDelete = {},
                    )
                }
            }
        }

        listOf("回到今天", "上个月", "下个月").forEach { description ->
            val bounds = composeRule.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot
            assertTrue("$description target was only ${bounds.width}×${bounds.height} dp", bounds.width >= 48f && bounds.height >= 48f)
        }
    }

    @Test
    fun calendarUpcomingListPreservesTheProvidedSortOrder() {
        val today = LocalDate.of(2026, 8, 25)
        val nameFirst = DateEntry(
            id = "name-first",
            title = "Alpha",
            note = "",
            date = today.plusDays(10),
            kind = DateKind.Schedule,
        )
        val nameSecond = DateEntry(
            id = "name-second",
            title = "Zulu",
            note = "",
            date = today.plusDays(1),
            kind = DateKind.Schedule,
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1f)) {
                AthenaTheme(AthenaPalette.Violet) {
                    CalendarScreen(
                        entries = listOf(nameFirst, nameSecond),
                        today = today,
                        month = YearMonth.from(today),
                        selectedDate = today,
                        onMonthChange = {},
                        onDateSelected = {},
                        onEdit = {},
                        onDelete = {},
                    )
                }
            }
        }

        val firstTop = composeRule.onNodeWithText("Alpha").fetchSemanticsNode().boundsInRoot.top
        val secondTop = composeRule.onNodeWithText("Zulu").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Calendar changed the provided order", firstTop < secondTop)
    }

    @Test
    fun editorExposesCalendarRecurrenceMultipleReminderAndTagLayers() {
        composeRule.setContent {
            AthenaTheme(AthenaPalette.Violet) {
                EditorSheet(
                    existingEntry = null,
                    today = LocalDate.of(2026, 8, 25),
                    availableTags = listOf(DateTag("family", "家人", DEFAULT_TAG_COLORS[0])),
                    onDismiss = {},
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithText("中国农历").performClick()
        composeRule.onNodeWithText("农历年").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("每年").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("提醒（0/32）").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("标签与颜色").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("家人").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun calendarImportRequiresExplicitCalendarSelection() {
        var selected: Set<Long> = emptySet()
        composeRule.setContent {
            AthenaTheme(AthenaPalette.Violet) {
                CalendarImportDialog(
                    state = CalendarImportState.Calendars(
                        listOf(DeviceCalendar(42, "工作", "user@example.com", 0)),
                    ),
                    onDismiss = {},
                    onLoadEvents = { selected = it },
                    onPreview = {},
                    onApply = {},
                )
            }
        }

        composeRule.onNodeWithText("选择事件").assertIsNotEnabled()
        composeRule.onNodeWithText("工作").performClick()
        composeRule.onNodeWithText("选择事件").assertIsEnabled().performClick()
        assertEquals(setOf(42L), selected)
    }
}
