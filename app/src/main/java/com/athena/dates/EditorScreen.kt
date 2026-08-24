package com.athena.dates

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorSheet(existingEntry: DateEntry?, today: LocalDate, onDismiss: () -> Unit, onSave: (DateEntry) -> Unit) {
    var title by rememberSaveable(existingEntry?.id) { mutableStateOf(existingEntry?.title.orEmpty()) }
    var note by rememberSaveable(existingEntry?.id) { mutableStateOf(existingEntry?.note.orEmpty()) }
    var kindName by rememberSaveable(existingEntry?.id) { mutableStateOf((existingEntry?.kind ?: DateKind.Anniversary).name) }
    var repeats by rememberSaveable(existingEntry?.id) { mutableStateOf(existingEntry?.repeatsYearly ?: true) }
    var reminderEnabled by rememberSaveable(existingEntry?.id) { mutableStateOf(existingEntry?.reminderEnabled ?: false) }
    var reminderDays by rememberSaveable(existingEntry?.id) { mutableStateOf((existingEntry?.reminderDaysBefore ?: 0).toString()) }
    var reminderTime by rememberSaveable(existingEntry?.id) { mutableStateOf(existingEntry?.reminderTime?.toString() ?: "09:00") }
    var targetDateIso by rememberSaveable(existingEntry?.id) { mutableStateOf((existingEntry?.date ?: today.plusDays(1)).toString()) }
    var datePickerOpen by rememberSaveable { mutableStateOf(false) }
    val kind = DateKind.entries.firstOrNull { it.name == kindName } ?: DateKind.Anniversary
    val targetDate = runCatching { LocalDate.parse(targetDateIso) }.getOrDefault(today.plusDays(1))
    val focus = LocalFocusManager.current
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .2f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .35f),
        unfocusedBorderColor = Color.Transparent,
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), dragHandle = null) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (existingEntry == null) "添加重要日子" else "编辑重要日子", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onDismiss) { Icon(Icons.Outlined.Close, "关闭") }
            }
            Text("类型", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            DateKindSelector(kind) { kindName = it.name; repeats = it == DateKind.Anniversary }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(title, { title = it.take(40) }, Modifier.fillMaxWidth(), label = { Text("名称") }, singleLine = true, colors = fieldColors, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions { focus.clearFocus() })
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(note, { note = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("备注（可选）") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Notes, null) }, maxLines = 3, colors = fieldColors)
            Spacer(Modifier.height(14.dp))
            Text("日期", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            DateSelectionCard(targetDate) { datePickerOpen = true }
            if (kind == DateKind.Anniversary) {
                Spacer(Modifier.height(10.dp))
                RepeatSetting(repeats) { repeats = it }
            }
            Spacer(Modifier.height(10.dp))
            ReminderSetting(
                enabled = reminderEnabled,
                daysBefore = reminderDays,
                time = reminderTime,
                onEnabledChange = { reminderEnabled = it },
                onDaysChange = { value -> reminderDays = value.filter(Char::isDigit).take(3) },
                onTimeChange = { reminderTime = it.take(5) },
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    onSave(
                        DateEntry(
                            id = existingEntry?.id ?: UUID.randomUUID().toString(),
                            title = title.trim(),
                            note = note.trim(),
                            date = targetDate,
                            kind = kind,
                            repeatsYearly = repeats,
                            reminderEnabled = reminderEnabled,
                            reminderDaysBefore = reminderDays.toIntOrNull() ?: 0,
                            reminderTime = runCatching { LocalTime.parse(reminderTime) }.getOrDefault(LocalTime.of(9, 0)),
                        ),
                    )
                },
                enabled = title.isNotBlank() && (reminderDays.toIntOrNull() ?: 0) in 0..365 && runCatching { LocalTime.parse(reminderTime) }.isSuccess,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Icon(Icons.Outlined.Check, null); Spacer(Modifier.width(8.dp)); Text("保存") }
        }
    }
    if (datePickerOpen) {
        val state = rememberDatePickerState(initialSelectedDateMillis = targetDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = { Button({ state.selectedDateMillis?.let { targetDateIso = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }; datePickerOpen = false }) { Text("确定") } },
            dismissButton = { OutlinedButton({ datePickerOpen = false }) { Text("取消") } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun DateKindSelector(selected: DateKind, onSelected: (DateKind) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DateKind.entries.forEach { kind ->
            val active = kind == selected
            Surface(
                Modifier.weight(1f).height(52.dp).selectable(active, role = Role.RadioButton) { onSelected(kind) },
                shape = RoundedCornerShape(14.dp),
                color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else Color.Transparent),
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(when (kind) { DateKind.Anniversary -> Icons.Outlined.FavoriteBorder; DateKind.Countdown -> Icons.Outlined.Timer; DateKind.Schedule -> Icons.Outlined.CalendarToday }, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp)); Text(kind.label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun DateSelectionCard(date: LocalDate, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onClick), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CalendarToday, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(date.format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日 · EEEE", Locale.CHINA)), Modifier.weight(1f))
            Icon(Icons.Outlined.ChevronRight, "选择日期")
        }
    }
}

@Composable
private fun RepeatSetting(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Surface(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.EventRepeat, null); Spacer(Modifier.width(12.dp)); Text("每年重复", Modifier.weight(1f)); Switch(checked, null)
        }
    }
}

@Composable
private fun ReminderSetting(
    enabled: Boolean,
    daysBefore: String,
    time: String,
    onEnabledChange: (Boolean) -> Unit,
    onDaysChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("本地提醒", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Switch(enabled, onEnabledChange)
            }
            if (enabled) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = daysBefore,
                        onValueChange = onDaysChange,
                        modifier = Modifier.weight(1f),
                        label = { Text("提前天数") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = time,
                        onValueChange = onTimeChange,
                        modifier = Modifier.weight(1f),
                        label = { Text("时间 HH:mm") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
        }
    }
}
