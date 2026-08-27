package com.athena.dates

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class SearchRotationUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun searchTextSurvivesActivityRecreation() {
        composeRule.onNode(hasSetTextAction()).performTextInput("旅行 备注")
        composeRule.onNode(hasSetTextAction()).assertTextContains("旅行 备注")

        composeRule.activityRule.scenario.recreate()

        composeRule.onNode(hasSetTextAction()).assertTextContains("旅行 备注")
    }

    @Test
    fun settingsKeepsAppearanceDataReminderArchiveAndWidgetDiscoverable() {
        composeRule.onNodeWithContentDescription("设置").performClick()

        composeRule.onNodeWithText("外观").assertIsDisplayed()
        listOf("提醒状态", "已归档的倒数日", "数据管理", "桌面小组件").forEach { destination ->
            composeRule.onNodeWithText(destination).assertExists()
        }
    }
}

class ArchivedEntriesUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun archivedRowOffersRestoreAndDeleteActions() {
        val entry = DateEntry(
            id = "archived",
            title = "过去的倒数日",
            note = "归档备注",
            date = LocalDate.of(2020, 1, 1),
            kind = DateKind.Countdown,
            isArchived = true,
        )
        val actions = mutableListOf<String>()
        composeRule.setContent {
            AthenaTheme(AthenaPalette.Violet) {
                ArchivedEntriesSheet(
                    entries = listOf(entry),
                    onDismiss = {},
                    onRestore = { actions += "restore:${it.id}" },
                    onDelete = { actions += "delete:${it.id}" },
                )
            }
        }

        composeRule.onNodeWithText("恢复").performClick()
        composeRule.onNodeWithText("删除").performClick()

        assertEquals(listOf("restore:archived", "delete:archived"), actions)
    }
}
