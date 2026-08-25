package com.athena.dates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun CalendarExportDialog(
    state: CalendarExportState,
    entries: List<DateEntry>,
    onDismiss: () -> Unit,
    onPreview: (WritableDeviceCalendar, Set<String>) -> Unit,
    onApply: () -> Unit,
) {
    var targetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedState by rememberSaveable { mutableStateOf("") }
    val selected = selectedState.split(',').filter(String::isNotBlank).toSet()
    val busy = state is CalendarExportState.Loading
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Outlined.CalendarMonth, "导出到系统日历") },
        title = { Text("导出到系统日历") },
        text = {
            when (state) {
                CalendarExportState.Idle -> Text("正在准备…")
                is CalendarExportState.Loading -> BusyRow(state.message)
                is CalendarExportState.Calendars -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("选择目标日历", fontWeight = FontWeight.SemiBold)
                    if (state.calendars.isEmpty()) Text("没有可写入的系统日历。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyColumn(Modifier.heightIn(max = 150.dp)) {
                        items(state.calendars, key = WritableDeviceCalendar::id) { calendar ->
                            Row(
                                Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { targetId = calendar.id }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(targetId == calendar.id, null)
                                Column {
                                    Text(calendar.displayName)
                                    if (calendar.accountName.isNotBlank()) Text(calendar.accountName, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    Text("选择要导出的日期", fontWeight = FontWeight.SemiBold)
                    LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        items(entries.filterNot(DateEntry::isArchived), key = DateEntry::id) { entry ->
                            Row(
                                Modifier.fillMaxWidth().clickable(role = Role.Checkbox) {
                                    selectedState = selected.toggle(entry.id).joinToString(",")
                                }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(entry.id in selected, null)
                                Column(Modifier.weight(1f)) {
                                    Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${entry.date} · ${entry.recurrence.displayLabel()}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    Text("只在确认后写入所选日历；不会后台同步。再次导出会更新稳定匹配项。", style = MaterialTheme.typography.bodySmall)
                }
                is CalendarExportState.Preview -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("导出预览", fontWeight = FontWeight.SemiBold)
                    IntegrationCounts(state.preview.counts)
                    Text("目标：${state.preview.target.displayName}")
                    IntegrationIssues(state.preview.issues)
                }
                is CalendarExportState.Finished -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("系统日历导出完成", fontWeight = FontWeight.SemiBold)
                    IntegrationCounts(state.completion.counts)
                    IntegrationIssues(state.completion.issues)
                }
                is CalendarExportState.Failed -> ErrorRow(state.message)
            }
        },
        confirmButton = {
            when (state) {
                is CalendarExportState.Calendars -> {
                    val target = state.calendars.firstOrNull { it.id == targetId }
                    Button(
                        onClick = { if (target != null) onPreview(target, selected) },
                        enabled = target != null && selected.isNotEmpty(),
                    ) { Text("生成预览") }
                }
                is CalendarExportState.Preview -> Button(onApply) { Text("确认写入") }
                is CalendarExportState.Finished, is CalendarExportState.Failed -> Button(onDismiss) { Text("完成") }
                else -> Unit
            }
        },
        dismissButton = { if (!busy) OutlinedButton(onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun ContactsImportDialog(
    state: ContactsImportState,
    onDismiss: () -> Unit,
    onPreview: (Set<String>) -> Unit,
    onApply: () -> Unit,
) {
    var selectedState by rememberSaveable { mutableStateOf("") }
    val selected = selectedState.split(',').filter(String::isNotBlank).toSet()
    val busy = state is ContactsImportState.Loading
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Outlined.Contacts, "导入联系人生日") },
        title = { Text("导入联系人生日") },
        text = {
            when (state) {
                ContactsImportState.Idle -> Text("正在准备…")
                is ContactsImportState.Loading -> BusyRow(state.message)
                is ContactsImportState.Candidates -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Athena 只读取联系人姓名、生日和用于去重的 lookup key；不读取电话、消息或其他资料。")
                    Text("可选 ${state.load.candidates.size} · 跳过 ${state.load.skipped}", style = MaterialTheme.typography.bodySmall)
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(state.load.candidates, key = ContactBirthdayCandidate::sourceId) { candidate ->
                            Row(
                                Modifier.fillMaxWidth().clickable(role = Role.Checkbox) {
                                    selectedState = selected.toggle(candidate.sourceId).joinToString(",")
                                }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(candidate.sourceId in selected, null)
                                Column(Modifier.weight(1f)) {
                                    Text(candidate.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(candidate.birthdayLabel, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                is ContactsImportState.Preview -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("导入预览", fontWeight = FontWeight.SemiBold)
                    IntegrationCounts(state.preview)
                    IntegrationIssues(state.preview.issues)
                }
                is ContactsImportState.Finished -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.message, fontWeight = FontWeight.SemiBold)
                    IntegrationCounts(state.counts)
                    IntegrationIssues(state.issues)
                }
                is ContactsImportState.Failed -> ErrorRow(state.message)
            }
        },
        confirmButton = {
            when (state) {
                is ContactsImportState.Candidates -> Button({ onPreview(selected) }, enabled = selected.isNotEmpty()) { Text("生成预览") }
                is ContactsImportState.Preview -> Button(onApply, enabled = state.preview.canApply) { Text("安全合并") }
                is ContactsImportState.Finished, is ContactsImportState.Failed -> Button(onDismiss) { Text("完成") }
                else -> Unit
            }
        },
        dismissButton = { if (!busy) OutlinedButton(onDismiss) { Text("取消") } },
    )
}

@Composable
private fun BusyRow(message: String) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    CircularProgressIndicator()
    Text(message)
}

@Composable
private fun ErrorRow(message: String) = Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
    Text(message, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun IntegrationCounts(counts: ImportCounts) {
    Text("新增 ${counts.added} · 更新 ${counts.updated} · 重复 ${counts.duplicates} · 跳过 ${counts.skipped} · 错误 ${counts.errors}")
}

@Composable
private fun IntegrationCounts(preview: ImportPreview) = IntegrationCounts(preview.counts)

@Composable
private fun IntegrationIssues(issues: List<ImportIssue>) {
    issues.take(6).forEach { issue ->
        Text(
            "${if (issue.severity == IssueSeverity.Error) "错误" else "提示"}：${issue.message}",
            color = if (issue.severity == IssueSeverity.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (issues.size > 6) Text("另有 ${issues.size - 6} 条提示", style = MaterialTheme.typography.bodySmall)
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
