package com.athena.dates

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
fun EditorSheet(
    existingEntry: DateEntry?,
    today: LocalDate,
    availableTags: List<DateTag> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (DateEntry) -> Unit,
) {
    val initialDate = existingEntry?.date ?: today.plusDays(1)
    val initialLunar = existingEntry?.lunarDate ?: runCatching { initialDate.toLunarDateSpec() }.getOrNull()
    var title by rememberSaveable(existingEntry?.id) { mutableStateOf(existingEntry?.title.orEmpty()) }
    var note by rememberSaveable(existingEntry?.id) { mutableStateOf(existingEntry?.note.orEmpty()) }
    var kindName by rememberSaveable(existingEntry?.id) {
        mutableStateOf((existingEntry?.kind ?: DateKind.Anniversary).name)
    }
    var calendarName by rememberSaveable(existingEntry?.id) {
        mutableStateOf((existingEntry?.calendarSystem ?: DateCalendarSystem.Gregorian).name)
    }
    var targetDateIso by rememberSaveable(existingEntry?.id) { mutableStateOf(initialDate.toString()) }
    var lunarYearText by rememberSaveable(existingEntry?.id) { mutableStateOf(initialLunar?.year?.toString().orEmpty()) }
    var lunarMonth by rememberSaveable(existingEntry?.id) { mutableIntStateOf(initialLunar?.month ?: 1) }
    var lunarDayText by rememberSaveable(existingEntry?.id) { mutableStateOf((initialLunar?.day ?: 1).toString()) }
    var lunarLeap by rememberSaveable(existingEntry?.id) { mutableStateOf(initialLunar?.isLeapMonth ?: false) }
    var repeatName by rememberSaveable(existingEntry?.id) {
        mutableStateOf((existingEntry?.recurrence?.frequency ?: RepeatFrequency.Yearly).name)
    }
    var repeatIntervalText by rememberSaveable(existingEntry?.id) {
        mutableStateOf((existingEntry?.recurrence?.interval ?: 1).toString())
    }
    var repeatEndIso by rememberSaveable(existingEntry?.id) {
        mutableStateOf(existingEntry?.recurrence?.endDate?.toString().orEmpty())
    }
    var reminderState by rememberSaveable(existingEntry?.id) {
        mutableStateOf(encodeReminders(existingEntry?.reminders.orEmpty()))
    }
    var selectedTagIds by rememberSaveable(existingEntry?.id) {
        mutableStateOf(existingEntry?.tags.orEmpty().joinToString(",", transform = DateTag::id))
    }
    var pendingTagState by rememberSaveable(existingEntry?.id) { mutableStateOf("") }
    var newTagName by rememberSaveable(existingEntry?.id) { mutableStateOf("") }
    var newTagColorIndex by rememberSaveable(existingEntry?.id) { mutableIntStateOf(0) }
    var datePickerMode by rememberSaveable { mutableStateOf<DatePickerMode?>(null) }
    var reminderDialogOpen by rememberSaveable { mutableStateOf(false) }

    val kind = DateKind.entries.firstOrNull { it.name == kindName } ?: DateKind.Anniversary
    val calendarSystem = DateCalendarSystem.entries.firstOrNull { it.name == calendarName }
        ?: DateCalendarSystem.Gregorian
    val repeatFrequency = RepeatFrequency.entries.firstOrNull { it.name == repeatName } ?: RepeatFrequency.None
    val repeatInterval = repeatIntervalText.toIntOrNull()
    val targetDate = runCatching { LocalDate.parse(targetDateIso) }.getOrDefault(initialDate)
    val lunarSpec = runCatching {
        LunarDateSpec(
            year = lunarYearText.toInt(),
            month = lunarMonth,
            day = lunarDayText.toInt(),
            isLeapMonth = lunarLeap,
        ).takeIf(LunarDateSpec::isValidLunarDate)
    }.getOrNull()
    val canonicalDate = if (calendarSystem == DateCalendarSystem.ChineseLunar) {
        lunarSpec?.let { runCatching { it.toSolarDate() }.getOrNull() }
    } else {
        targetDate
    }
    val repeatEnd = repeatEndIso.takeIf(String::isNotBlank)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val reminders = remember(reminderState) { decodeReminders(reminderState) }
    val pendingTags = remember(pendingTagState) { decodePendingTags(pendingTagState) }
    val selectedIds = remember(selectedTagIds) { selectedTagIds.split(',').filter(String::isNotBlank).toSet() }
    val allTags = remember(availableTags, existingEntry?.tags, pendingTags) {
        (availableTags + existingEntry?.tags.orEmpty() + pendingTags).distinctBy(DateTag::id)
    }
    val selectedTags = allTags.filter { it.id in selectedIds }
    val recurrenceValid = repeatFrequency == RepeatFrequency.None ||
        (repeatInterval in 1..99 && (repeatEnd == null || canonicalDate?.let { !repeatEnd.isBefore(it) } == true))
    val lunarValid = calendarSystem == DateCalendarSystem.Gregorian || lunarSpec != null
    val canSave = title.isNotBlank() && canonicalDate != null && lunarValid && recurrenceValid
    val focus = LocalFocusManager.current
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .22f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
                .padding(horizontal = 20.dp).padding(bottom = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (existingEntry == null) "新建日子" else "编辑日子",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (existingEntry == null) "把值得记住的时刻放进日历" else "更新日期、重复与提醒",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 1.dp,
                ) {
                    IconButton(onDismiss) { Icon(Icons.Outlined.Close, "关闭") }
                }
            }
            Spacer(Modifier.height(16.dp))
            SectionTitle("类型")
            DateKindSelector(kind) {
                kindName = it.name
                if (existingEntry == null) {
                    repeatName = if (it == DateKind.Anniversary) RepeatFrequency.Yearly.name else RepeatFrequency.None.name
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                title, { title = it.take(40) }, Modifier.fillMaxWidth(),
                label = { Text("名称") }, singleLine = true, colors = fieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions { focus.clearFocus() },
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                note, { note = it.take(500) }, Modifier.fillMaxWidth(),
                label = { Text("备注（可选）") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Notes, null) },
                maxLines = 4,
                colors = fieldColors,
            )
            Spacer(Modifier.height(18.dp))
            SectionTitle("日期")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateCalendarSystem.entries.forEach { system ->
                    FilterChip(
                        selected = calendarSystem == system,
                        onClick = {
                            if (system == DateCalendarSystem.ChineseLunar) {
                                runCatching { targetDate.toLunarDateSpec() }.getOrNull()?.let { converted ->
                                    lunarYearText = converted.year.toString()
                                    lunarMonth = converted.month
                                    lunarDayText = converted.day.toString()
                                    lunarLeap = converted.isLeapMonth
                                }
                            } else {
                                lunarSpec?.let { targetDateIso = it.toSolarDate().toString() }
                            }
                            calendarName = system.name
                            if (system == DateCalendarSystem.ChineseLunar) {
                                repeatName = if (repeatFrequency == RepeatFrequency.None) {
                                    RepeatFrequency.None.name
                                } else {
                                    RepeatFrequency.Yearly.name
                                }
                            }
                        },
                        label = { Text(system.label) },
                    )
                }
            }
            if (calendarSystem == DateCalendarSystem.Gregorian) {
                DateSelectionCard(
                    targetDate.format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日 · EEEE", Locale.CHINA)),
                ) { datePickerMode = DatePickerMode.Anchor }
                Text(
                    "农历 ${targetDate.lunarDisplayLabel() ?: "超出支持范围"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 5.dp),
                )
            } else {
                LunarDateEditor(
                    yearText = lunarYearText,
                    month = lunarMonth,
                    dayText = lunarDayText,
                    leap = lunarLeap,
                    onYear = { lunarYearText = it.filter(Char::isDigit).take(4) },
                    onMonth = { month, leap -> lunarMonth = month; lunarLeap = leap },
                    onDay = { lunarDayText = it.filter(Char::isDigit).take(2) },
                )
                if (canonicalDate != null) {
                    Text(
                        "对应公历 ${canonicalDate.format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日"))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "请输入 $LUNAR_MIN_YEAR–$LUNAR_MAX_YEAR 内存在的农历日期；闰月只在对应年份可选。",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            SectionTitle("重复")
            RecurrenceEditor(
                calendarSystem = calendarSystem,
                frequency = repeatFrequency,
                intervalText = repeatIntervalText,
                endDate = repeatEnd,
                onFrequency = { repeatName = it.name; if (it == RepeatFrequency.None) repeatEndIso = "" },
                onInterval = { repeatIntervalText = it.filter(Char::isDigit).take(2) },
                onToggleEnd = { enabled -> repeatEndIso = if (enabled) (canonicalDate ?: today).plusYears(1).toString() else "" },
                onPickEnd = { datePickerMode = DatePickerMode.RepeatEnd },
            )
            if (!recurrenceValid) {
                Text("间隔需为 1–99，结束日期不能早于首次日期。", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(18.dp))
            ReminderListEditor(
                reminders = reminders,
                onAdd = { reminderDialogOpen = true },
                onDelete = { id -> reminderState = encodeReminders(reminders.filterNot { it.id == id }) },
            )
            Spacer(Modifier.height(18.dp))
            TagEditor(
                tags = allTags,
                selectedIds = selectedIds,
                newName = newTagName,
                colorIndex = newTagColorIndex,
                onToggle = { id ->
                    selectedTagIds = (if (id in selectedIds) selectedIds - id else selectedIds + id).joinToString(",")
                },
                onNewName = { newTagName = it.take(30) },
                onColor = { newTagColorIndex = it },
                onCreate = {
                    val normalized = newTagName.trim()
                    val existingTag = allTags.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
                    if (existingTag != null) {
                        selectedTagIds = (selectedIds + existingTag.id).joinToString(",")
                    } else if (normalized.isNotEmpty()) {
                        val tag = DateTag(name = normalized, colorArgb = DEFAULT_TAG_COLORS[newTagColorIndex])
                        pendingTagState = encodePendingTags(pendingTags + tag)
                        selectedTagIds = (selectedIds + tag.id).joinToString(",")
                    }
                    newTagName = ""
                },
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    val recurrence = RecurrenceRule(
                        frequency = repeatFrequency,
                        interval = if (repeatFrequency == RepeatFrequency.None) 1 else checkNotNull(repeatInterval),
                        endDate = if (repeatFrequency == RepeatFrequency.None) null else repeatEnd,
                    )
                    onSave(
                        DateEntry(
                            id = existingEntry?.id ?: UUID.randomUUID().toString(),
                            title = title.trim(),
                            note = note.trim(),
                            date = checkNotNull(canonicalDate),
                            eventTime = existingEntry?.eventTime,
                            eventTimeZone = existingEntry?.eventTimeZone,
                            kind = kind,
                            calendarSystem = calendarSystem,
                            lunarDate = lunarSpec.takeIf { calendarSystem == DateCalendarSystem.ChineseLunar },
                            recurrence = recurrence,
                            reminders = reminders,
                            tags = selectedTags,
                            isArchived = existingEntry?.isArchived ?: false,
                            keepVisibleWhenExpired = existingEntry?.keepVisibleWhenExpired ?: false,
                            externalIdentity = existingEntry?.externalIdentity,
                        ),
                    )
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Outlined.Check, null)
                Spacer(Modifier.width(8.dp))
                Text("保存")
            }
        }
    }

    datePickerMode?.let { mode ->
        val initial = when (mode) {
            DatePickerMode.Anchor -> targetDate
            DatePickerMode.RepeatEnd -> repeatEnd ?: (canonicalDate ?: today).plusYears(1)
        }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { datePickerMode = null },
            confirmButton = {
                Button({
                    state.selectedDateMillis?.let {
                        val selected = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        if (mode == DatePickerMode.Anchor) targetDateIso = selected else repeatEndIso = selected
                    }
                    datePickerMode = null
                }) { Text("确定") }
            },
            dismissButton = { OutlinedButton({ datePickerMode = null }) { Text("取消") } },
        ) { DatePicker(state) }
    }

    if (reminderDialogOpen) {
        AddReminderDialog(
            onDismiss = { reminderDialogOpen = false },
            onAdd = { reminder ->
                if (reminders.none { it.daysBefore == reminder.daysBefore && it.time == reminder.time }) {
                    reminderState = encodeReminders(reminders + reminder)
                    reminderDialogOpen = false
                }
            },
            existing = reminders,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun DateKindSelector(selected: DateKind, onSelected: (DateKind) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DateKind.entries.forEach { kind ->
            val active = kind == selected
            Surface(
                Modifier.weight(1f).height(52.dp).selectable(active, role = Role.RadioButton) { onSelected(kind) },
                shape = RoundedCornerShape(17.dp),
                color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = if (active) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .5f)) else null,
            ) {
                Row(
                    Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        when (kind) {
                            DateKind.Anniversary -> Icons.Outlined.FavoriteBorder
                            DateKind.Countdown -> Icons.Outlined.Timer
                            DateKind.Schedule -> Icons.Outlined.CalendarToday
                        },
                        null,
                        Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(kind.label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun DateSelectionCard(label: String, onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .42f),
        tonalElevation = 1.dp,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CalendarToday, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(label, Modifier.weight(1f))
            Icon(Icons.Outlined.ChevronRight, "选择日期")
        }
    }
}

@Composable
private fun LunarDateEditor(
    yearText: String,
    month: Int,
    dayText: String,
    leap: Boolean,
    onYear: (String) -> Unit,
    onMonth: (Int, Boolean) -> Unit,
    onDay: (String) -> Unit,
) {
    val year = yearText.toIntOrNull()
    val months = year?.takeIf { it in LUNAR_SUPPORTED_YEARS }?.let(::lunarMonthsInYear).orEmpty()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            yearText, onYear, Modifier.weight(1f), label = { Text("农历年") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            dayText, onDay, Modifier.weight(1f), label = { Text("日（1–30）") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        months.forEach { option ->
            FilterChip(
                selected = month == option.month && leap == option.isLeapMonth,
                onClick = { onMonth(option.month, option.isLeapMonth) },
                label = { Text(option.label) },
            )
        }
    }
}

@Composable
private fun RecurrenceEditor(
    calendarSystem: DateCalendarSystem,
    frequency: RepeatFrequency,
    intervalText: String,
    endDate: LocalDate?,
    onFrequency: (RepeatFrequency) -> Unit,
    onInterval: (String) -> Unit,
    onToggleEnd: (Boolean) -> Unit,
    onPickEnd: () -> Unit,
) {
    val choices = if (calendarSystem == DateCalendarSystem.ChineseLunar) {
        listOf(RepeatFrequency.None, RepeatFrequency.Yearly)
    } else {
        listOf(RepeatFrequency.None, RepeatFrequency.Weekly, RepeatFrequency.Monthly, RepeatFrequency.Yearly)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { choice ->
            FilterChip(
                selected = choice == frequency,
                onClick = { onFrequency(choice) },
                label = { Text(choice.displayChoice()) },
            )
        }
    }
    if (frequency != RepeatFrequency.None) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            intervalText,
            onInterval,
            Modifier.fillMaxWidth(),
            label = { Text("重复间隔（每几${frequency.label}）") },
            supportingText = { Text("1 表示每${frequency.label}，可输入 1–99") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("设置结束日期", Modifier.weight(1f))
            Switch(endDate != null, { onToggleEnd(it) })
        }
        if (endDate != null) DateSelectionCard("重复至 $endDate", onPickEnd)
    }
}

@Composable
private fun ReminderListEditor(
    reminders: List<EntryReminder>,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SectionTitle("提醒（${reminders.size}/$MAX_ENTRY_REMINDERS）")
        Spacer(Modifier.weight(1f))
        IconButton(onAdd, enabled = reminders.size < MAX_ENTRY_REMINDERS) {
            Icon(Icons.Outlined.Add, "添加提醒")
        }
    }
    if (reminders.isEmpty()) Text("未设置提醒", color = MaterialTheme.colorScheme.onSurfaceVariant)
    reminders.sortedWith(compareByDescending<EntryReminder> { it.daysBefore }.thenBy { it.time }).forEach { reminder ->
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f),
            shape = RoundedCornerShape(15.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        ) {
            Row(Modifier.padding(start = 14.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Notifications, null)
                Spacer(Modifier.width(10.dp))
                Text(
                    "${if (reminder.daysBefore == 0) "当天" else "提前 ${reminder.daysBefore} 天"} · ${reminder.time}",
                    Modifier.weight(1f),
                )
                IconButton({ onDelete(reminder.id) }) { Icon(Icons.Outlined.DeleteOutline, "删除这条提醒") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddReminderDialog(
    existing: List<EntryReminder>,
    onDismiss: () -> Unit,
    onAdd: (EntryReminder) -> Unit,
) {
    var daysText by rememberSaveable { mutableStateOf("0") }
    val state = rememberTimePickerState(initialHour = 9, initialMinute = 0, is24Hour = true)
    val days = daysText.toIntOrNull()
    val validDays = days != null && days in REMINDER_DAYS_RANGE
    val duplicate = validDays && existing.any { it.daysBefore == days && it.time == LocalTime.of(state.hour, state.minute) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.AccessTime, null) },
        title = { Text("添加提醒") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTextField(
                    daysText,
                    { daysText = it.filter(Char::isDigit).take(3) },
                    label = { Text("提前天数（0–365）") },
                    supportingText = { Text(if (duplicate) "已有完全相同的提醒" else "0 表示当天") },
                    isError = !validDays || duplicate,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                TimePicker(state)
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(EntryReminder(daysBefore = checkNotNull(days), time = LocalTime.of(state.hour, state.minute))) },
                enabled = validDays && !duplicate,
            ) { Text("添加") }
        },
        dismissButton = { OutlinedButton(onDismiss) { Text("取消") } },
    )
}

@Composable
private fun TagEditor(
    tags: List<DateTag>,
    selectedIds: Set<String>,
    newName: String,
    colorIndex: Int,
    onToggle: (String) -> Unit,
    onNewName: (String) -> Unit,
    onColor: (Int) -> Unit,
    onCreate: () -> Unit,
) {
    SectionTitle("标签与颜色")
    if (tags.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag ->
                FilterChip(
                    selected = tag.id in selectedIds,
                    onClick = { onToggle(tag.id) },
                    leadingIcon = {
                        Box(
                            Modifier.size(14.dp).clip(CircleShape).background(Color(tag.colorArgb))
                                .border(1.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
                        )
                    },
                    label = { Text(tag.name) },
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            newName,
            onNewName,
            Modifier.weight(1f),
            label = { Text("新标签") },
            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, null) },
            singleLine = true,
        )
        IconButton(onCreate, enabled = newName.isNotBlank()) { Icon(Icons.Outlined.Add, "创建并选择标签") }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DEFAULT_TAG_COLORS.forEachIndexed { index, color ->
            FilterChip(
                selected = colorIndex == index,
                onClick = { onColor(index) },
                label = {
                    Box(
                        Modifier.size(20.dp).clip(CircleShape).background(Color(color))
                            .border(1.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
                    )
                },
            )
        }
    }
}

private enum class DatePickerMode { Anchor, RepeatEnd }

private fun RepeatFrequency.displayChoice(): String = when (this) {
    RepeatFrequency.None -> "不重复"
    RepeatFrequency.Daily -> "每天"
    RepeatFrequency.Weekly -> "每周"
    RepeatFrequency.Monthly -> "每月"
    RepeatFrequency.Yearly -> "每年"
}

private fun encodeReminders(reminders: List<EntryReminder>): String = reminders.joinToString(";") {
    val id = java.util.Base64.getUrlEncoder().withoutPadding()
        .encodeToString(it.id.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
    "$id,${it.daysBefore},${it.time}"
}

private fun decodeReminders(value: String): List<EntryReminder> = if (value.isBlank()) {
    emptyList()
} else {
    value.split(';').mapNotNull { encoded ->
        val fields = encoded.split(',')
        runCatching {
            val id = String(
                java.util.Base64.getUrlDecoder().decode(fields[0]),
                java.nio.charset.StandardCharsets.UTF_8,
            )
            EntryReminder(id, fields[1].toInt(), LocalTime.parse(fields[2]))
        }.getOrNull()
    }
}

private fun encodePendingTags(tags: List<DateTag>): String = tags.joinToString(";") { tag ->
    val name = java.util.Base64.getUrlEncoder().withoutPadding()
        .encodeToString(tag.name.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
    "${tag.id},${tag.colorArgb},$name"
}

private fun decodePendingTags(value: String): List<DateTag> = if (value.isBlank()) {
    emptyList()
} else {
    value.split(';').mapNotNull { encoded ->
        runCatching {
            val fields = encoded.split(',', limit = 3)
            val name = String(
                java.util.Base64.getUrlDecoder().decode(fields[2]),
                java.nio.charset.StandardCharsets.UTF_8,
            )
            DateTag(fields[0], name, fields[1].toInt())
        }.getOrNull()
    }
}
