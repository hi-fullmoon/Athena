package com.athena.dates

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
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
