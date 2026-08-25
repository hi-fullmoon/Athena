package com.athena.dates

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class DateCapabilitiesUiTest {
    @get:Rule
    val composeRule = createComposeRule()

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
