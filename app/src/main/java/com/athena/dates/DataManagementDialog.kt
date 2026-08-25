package com.athena.dates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun DataManagementDialog(
    state: DataTransferState,
    onDismiss: () -> Unit,
    onExportJson: () -> Unit,
    onImportJson: () -> Unit,
    onExportIcs: () -> Unit,
    onImportIcs: () -> Unit,
    onImportCalendar: () -> Unit = {},
    onExportCalendar: () -> Unit = {},
    onImportContacts: () -> Unit = {},
    onMerge: () -> Unit,
    onRequestReplace: () -> Unit,
    onReset: () -> Unit,
) {
    val busy = state is DataTransferState.Working
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Outlined.DataObject, "数据管理") },
        title = { Text("数据管理") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (state) {
                    DataTransferState.Idle -> DataManagementActions(
                        onExportJson = onExportJson,
                        onImportJson = onImportJson,
                        onExportIcs = onExportIcs,
                        onImportIcs = onImportIcs,
                        onImportCalendar = onImportCalendar,
                        onExportCalendar = onExportCalendar,
                        onImportContacts = onImportContacts,
                    )
                    is DataTransferState.Working -> Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(state.message)
                    }
                    is DataTransferState.Preview -> ImportPreviewContent(state.preview)
                    is DataTransferState.Finished -> {
                        Text(state.message, fontWeight = FontWeight.SemiBold)
                        state.counts?.let { ImportCountsContent(it) }
                        ImportIssuesContent(state.issues)
                    }
                    is DataTransferState.Failed -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                            Text(state.message, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is DataTransferState.Preview -> if (state.preview.canApply) {
                    Button(onMerge) { Text("安全合并") }
                } else {
                    Button(onReset) { Text("重新选择") }
                }
                is DataTransferState.Finished, is DataTransferState.Failed -> Button(onReset) { Text("返回数据管理") }
                else -> Unit
            }
        },
        dismissButton = {
            when {
                state is DataTransferState.Preview && state.preview.canReplace -> {
                    OutlinedButton(onRequestReplace) { Text("全量替换…") }
                }
                !busy -> OutlinedButton(onDismiss) { Text("关闭") }
            }
        },
    )
}

@Composable
private fun DataManagementActions(
    onExportJson: () -> Unit,
    onImportJson: () -> Unit,
    onExportIcs: () -> Unit,
    onImportIcs: () -> Unit,
    onImportCalendar: () -> Unit,
    onExportCalendar: () -> Unit,
    onImportContacts: () -> Unit,
) {
    Text("完整备份", fontWeight = FontWeight.SemiBold)
    Text("版本化 JSON 会保存全部日期、归档状态、提醒配置以及显示模式、动态配色和主题。")
    ActionButton("导出 JSON 完整备份", true, onExportJson)
    ActionButton("从 JSON 恢复", false, onImportJson)
    Spacer(Modifier.height(4.dp))
    HorizontalDivider()
    Spacer(Modifier.height(4.dp))
    Text("日历交换", fontWeight = FontWeight.SemiBold)
    Text("ICS 支持全天/定时日期、备注、重复规则、多提醒和标签扩展。")
    ActionButton("导出 ICS 日历", true, onExportIcs, calendar = true)
    ActionButton("导入 ICS 日历", false, onImportIcs, calendar = true)
    ActionButton("从系统日历一次性导入", false, onImportCalendar, calendar = true)
    ActionButton("导出所选日期到系统日历", true, onExportCalendar, calendar = true)
    ActionButton("从联系人导入生日", false, onImportContacts, calendar = true)
    Text(
        "系统日历和联系人只在你进入功能、授权并明确选择后读取或写入；不后台同步。",
        style = MaterialTheme.typography.bodySmall,
    )
    Text(
        "文件只通过 Android 系统选择器读取或保存，Athena 不申请文件存储权限。",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun ActionButton(label: String, export: Boolean, onClick: () -> Unit, calendar: Boolean = false) {
    OutlinedButton(onClick, Modifier.fillMaxWidth()) {
        Icon(
            if (calendar) Icons.Outlined.CalendarMonth else if (export) Icons.Outlined.FileUpload else Icons.Outlined.FileDownload,
            contentDescription = null,
        )
        Text(label)
    }
}

@Composable
private fun ImportPreviewContent(preview: ImportPreview) {
    Text(
        if (preview.canApply) "导入预览已完成" else "文件未通过严格校验",
        fontWeight = FontWeight.SemiBold,
    )
    ImportCountsContent(preview.counts)
    Text(
        if (preview.format == TransferFormat.Json) {
            "默认“安全合并”。全量替换会先要求再次确认。"
        } else {
            "ICS 仅支持安全合并；不支持的事件已跳过并列在下方。"
        },
        style = MaterialTheme.typography.bodySmall,
    )
    if (preview.restoresSettings) {
        Text("此备份包含应用主题设置。", style = MaterialTheme.typography.bodySmall)
    }
    ImportIssuesContent(preview.issues)
}

@Composable
private fun ImportIssuesContent(issues: List<ImportIssue>) {
    if (issues.isNotEmpty()) {
        HorizontalDivider()
        issues.take(MAX_VISIBLE_ISSUES).forEach { issue ->
            Text(
                text = "${if (issue.severity == IssueSeverity.Error) "错误" else "提示"}：${issue.message}",
                color = if (issue.severity == IssueSeverity.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (issues.size > MAX_VISIBLE_ISSUES) {
            Text("另有 ${issues.size - MAX_VISIBLE_ISSUES} 条提示", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ImportCountsContent(counts: ImportCounts) {
    Text(
        "新增 ${counts.added} · 更新 ${counts.updated} · 重复 ${counts.duplicates} · " +
            "跳过 ${counts.skipped} · 错误 ${counts.errors}",
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun ReplaceRestoreConfirmation(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("确认全量替换？") },
        text = { Text("当前全部日期会被备份文件中的内容替换。数据库操作是事务化的，但成功后不能撤销；建议先导出当前完整备份。") },
        confirmButton = { Button(onConfirm) { Text("确认全量替换") } },
        dismissButton = { OutlinedButton(onDismiss) { Text("取消") } },
    )
}

private const val MAX_VISIBLE_ISSUES = 8
