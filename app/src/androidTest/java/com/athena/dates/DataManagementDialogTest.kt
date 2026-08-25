package com.athena.dates

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DataManagementDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun previewExposesCountsAndBothExplicitRestoreChoices() {
        var replacementRequested = false
        composeRule.setContent {
            AthenaTheme(AthenaPalette.Violet) {
                DataManagementDialog(
                    state = DataTransferState.Preview(
                        ImportPreview(
                            format = TransferFormat.Json,
                            counts = ImportCounts(added = 2, updated = 1, duplicates = 3, skipped = 4, errors = 0),
                            issues = emptyList(),
                            canApply = true,
                            canReplace = true,
                            restoresSettings = true,
                        ),
                    ),
                    onDismiss = {},
                    onExportJson = {},
                    onImportJson = {},
                    onExportIcs = {},
                    onImportIcs = {},
                    onMerge = {},
                    onRequestReplace = { replacementRequested = true },
                    onReset = {},
                )
            }
        }

        composeRule.onNodeWithText("新增 2 · 更新 1 · 重复 3 · 跳过 4 · 错误 0").assertIsDisplayed()
        composeRule.onNodeWithText("安全合并").assertIsDisplayed()
        composeRule.onNodeWithText("全量替换…").performClick()
        assertTrue(replacementRequested)
    }

    @Test
    fun replaceConfirmationStatesDestructiveEffectAndRequiresExplicitAction() {
        composeRule.setContent {
            AthenaTheme(AthenaPalette.Violet) {
                ReplaceRestoreConfirmation(onDismiss = {}, onConfirm = {})
            }
        }

        composeRule.onNodeWithText("确认全量替换？").assertIsDisplayed()
        composeRule.onNodeWithText("确认全量替换").assertIsDisplayed()
        composeRule.onNodeWithText("取消").assertIsDisplayed()
    }
}
