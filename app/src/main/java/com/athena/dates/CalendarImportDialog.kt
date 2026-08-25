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
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import java.time.format.DateTimeFormatter

@Composable
internal fun CalendarImportDialog(
    state: CalendarImportState,
    onDismiss: () -> Unit,
    onLoadEvents: (Set<Long>) -> Unit,
    onPreview: (Set<String>) -> Unit,
    onApply: () -> Unit,
) {
    var calendarIdsState by rememberSaveable { mutableStateOf("") }
    var eventIdsState by rememberSaveable { mutableStateOf("") }
    val selectedCalendarIds = calendarIdsState.parseLongSet()
    val selectedEventIds = eventIdsState.parseStringSet()
    val busy = state is CalendarImportState.Loading
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Outlined.CalendarMonth, "从系统日历导入") },
        title = { Text("从系统日历导入") },
        text = {
            when (state) {
                CalendarImportState.Idle -> Text("正在准备…")
                is CalendarImportState.Loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text(state.message)
                }
                is CalendarImportState.Calendars -> CalendarSelection(
                    calendars = state.calendars,
                    selected = selectedCalendarIds,
                    onToggle = { id -> calendarIdsState = selectedCalendarIds.toggle(id).joinToString(",") },
                )
                is CalendarImportState.Events -> EventSelection(
                    load = state.load,
                    selected = selectedEventIds,
                    onToggle = { id -> eventIdsState = selectedEventIds.toggle(id).joinToString(",") },
                )
                is CalendarImportState.Preview -> CalendarPreview(state.preview)
                is CalendarImportState.Finished -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.message, fontWeight = FontWeight.SemiBold)
                    CountsText(state.counts)
                    IssueSummary(state.issues)
                }
                is CalendarImportState.Failed -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            when (state) {
                is CalendarImportState.Calendars -> Button(
                    { onLoadEvents(selectedCalendarIds) },
                    enabled = selectedCalendarIds.isNotEmpty(),
                ) { Text("选择事件") }
                is CalendarImportState.Events -> Button(
                    { onPreview(selectedEventIds) },
                    enabled = selectedEventIds.isNotEmpty(),
                ) { Text("生成预览") }
                is CalendarImportState.Preview -> Button(onApply, enabled = state.preview.canApply) { Text("安全合并") }
                is CalendarImportState.Finished, is CalendarImportState.Failed -> Button(onDismiss) { Text("完成") }
                else -> Unit
            }
        },
        dismissButton = { if (!busy) OutlinedButton(onDismiss) { Text("取消") } },
    )
}

@Composable
private fun CalendarSelection(
    calendars: List<DeviceCalendar>,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("仅在本次操作中读取你勾选的可见日历。Athena 不写回、不后台同步。")
        if (calendars.isEmpty()) Text("没有可读取的可见日历。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            items(calendars, key = DeviceCalendar::id) { calendar ->
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { onToggle(calendar.id) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(calendar.id in selected, null)
                    Column(Modifier.weight(1f)) {
                        Text(calendar.displayName, fontWeight = FontWeight.Medium)
                        if (calendar.accountName.isNotBlank()) {
                            Text(calendar.accountName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventSelection(
    load: CalendarCandidateLoad,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("继续选择要导入的具体事件。全天日期和带时区的定时事件都会保留其开始语义。")
        Text("可选 ${load.candidates.size} · 预先跳过 ${load.skipped}", style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.heightIn(max = 390.dp)) {
            items(load.candidates, key = CalendarImportCandidate::sourceId) { candidate ->
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { onToggle(candidate.sourceId) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(candidate.sourceId in selected, null)
                    Column(Modifier.weight(1f)) {
                        Text(candidate.entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${candidate.entry.date.format(DateTimeFormatter.ISO_DATE)} · ${candidate.calendarName}" +
                                if (candidate.isAllDay) " · 全天" else " · ${candidate.originalTime}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (candidate.warnings.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Schedule, null, Modifier.padding(end = 4.dp))
                                Text(candidate.warnings.first(), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarPreview(preview: ImportPreview) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (preview.canApply) "导入预览已完成" else "所选事件无法安全导入", fontWeight = FontWeight.SemiBold)
        CountsText(preview.counts)
        Text("系统日历导入只支持安全合并；稳定来源 ID 与日期语义共同用于识别重复。")
        IssueSummary(preview.issues)
    }
}

@Composable
private fun CountsText(counts: ImportCounts) {
    Text("新增 ${counts.added} · 更新 ${counts.updated} · 重复 ${counts.duplicates} · 跳过 ${counts.skipped} · 错误 ${counts.errors}")
}

@Composable
private fun IssueSummary(issues: List<ImportIssue>) {
    issues.take(6).forEach { issue ->
        Text(
            "${if (issue.severity == IssueSeverity.Error) "错误" else "提示"}：${issue.message}",
            color = if (issue.severity == IssueSeverity.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (issues.size > 6) Text("另有 ${issues.size - 6} 条提示", style = MaterialTheme.typography.bodySmall)
}

private fun String.parseLongSet(): Set<Long> = split(',').mapNotNull(String::toLongOrNull).toSet()
private fun String.parseStringSet(): Set<String> = split(',').filter(String::isNotBlank).toSet()
private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
