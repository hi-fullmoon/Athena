package com.athena.dates

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalendarScreen(
    entries: List<DateEntry>,
    today: LocalDate,
    month: YearMonth,
    selectedDate: LocalDate,
    onMonthChange: (YearMonth) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onEdit: (DateEntry) -> Unit,
    onDelete: (DateEntry) -> Unit,
    modifier: Modifier = Modifier,
    isFiltering: Boolean = false,
) {
    val selectedEntries = entries.filter { it.occursOn(selectedDate) }
    val allUpcoming = entries.mapNotNull { entry -> entry.nextOccurrence(today)?.let { DateOccurrence(entry, it) } }
        .filter { it.date != selectedDate }
    val eventDates = (1..month.lengthOfMonth()).map(month::atDay).filterTo(mutableSetOf()) { date -> entries.any { it.occursOn(date) } }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
    ) {
        item {
            MonthCalendar(month, selectedDate, today, eventDates, isFiltering, onMonthChange, onDateSelected)
            Spacer(Modifier.height(22.dp))
            SelectedDateHeader(selectedDate, today, selectedEntries.size)
            Spacer(Modifier.height(10.dp))
        }
        if (selectedEntries.isEmpty()) {
            item { QuietEmptyRow(if (isFiltering) "当天没有匹配记录" else "这一天没有重要日子") }
        }
        items(selectedEntries, key = { "selected-${it.id}" }) { entry ->
            DateEntryCard(entry, selectedDate, today, { onEdit(entry) }, { onDelete(entry) }, showDateContext = false)
            Spacer(Modifier.height(8.dp))
        }
        item {
            Spacer(Modifier.height(22.dp))
            SectionHeader(
                "接下来",
                when {
                    allUpcoming.isEmpty() -> "暂无"
                    allUpcoming.size > 4 -> "显示 4 个"
                    else -> "${allUpcoming.size} 个"
                },
            )
            Spacer(Modifier.height(10.dp))
        }
        if (allUpcoming.isEmpty()) {
            item {
                QuietEmptyRow(
                    if (isFiltering) "当前筛选下没有即将到来的日子" else "暂无接下来的重要日子",
                )
            }
        }
        items(allUpcoming.take(4), key = { "upcoming-${it.entry.id}" }) { occurrence ->
            DateEntryCard(occurrence.entry, occurrence.date, today, { onEdit(occurrence.entry) }, { onDelete(occurrence.entry) })
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MonthCalendar(
    month: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    eventDates: Set<LocalDate>,
    isFiltering: Boolean,
    onMonthChange: (YearMonth) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 2.dp)) {
                Text("${month.monthValue} 月", style = MaterialTheme.typography.titleLarge)
                Text(
                    buildString {
                        append(month.year)
                        append("  ·  ")
                        append(
                            if (eventDates.isEmpty()) {
                                if (isFiltering) "本月没有匹配记录" else "本月没有记录"
                            } else {
                                "${eventDates.size} 天有记录"
                            },
                        )
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (month != YearMonth.from(today)) {
                Box(
                    Modifier.size(48.dp).semantics { contentDescription = "回到今天" }.clickable {
                        onMonthChange(YearMonth.from(today))
                        onDateSelected(today)
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(10.dp)) {
                        Text(
                            "今天",
                            Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            IconButton({ onMonthChange(month.minusMonths(1)) }) {
                Icon(Icons.Outlined.ChevronLeft, "上个月", Modifier.size(20.dp))
            }
            IconButton({ onMonthChange(month.plusMonths(1)) }) {
                Icon(Icons.Outlined.ChevronRight, "下个月", Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("日", "一", "二", "三", "四", "五", "六").forEachIndexed { index, label ->
                        Text(
                            label,
                            Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            color = if (index == 0 || index == 6) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                val offset = month.atDay(1).dayOfWeek.sundayIndex()
                val weekCount = (offset + month.lengthOfMonth() + 6) / 7
                val fontScale = LocalDensity.current.fontScale
                val cellHeight = maxOf(48f, 26f * fontScale + 22f).dp
                repeat(weekCount) { week ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { index ->
                            val number = week * 7 + index - offset + 1
                            if (number in 1..month.lengthOfMonth()) {
                                val date = month.atDay(number)
                                CalendarDay(
                                    date = date,
                                    isToday = date == today,
                                    isSelected = date == selectedDate,
                                    hasEvent = date in eventDates,
                                    cellHeight = cellHeight,
                                    onClick = { onDateSelected(date) },
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                Spacer(Modifier.weight(1f).height(cellHeight))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    hasEvent: Boolean,
    cellHeight: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val foreground = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.height(cellHeight)
            .selectable(isSelected, role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = date.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA)) + if (hasEvent) "，有事项" else "，无事项"
            }
            .padding(horizontal = 3.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxSize().clip(shape)
                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                .then(
                    if (isToday && !isSelected) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape)
                    else Modifier,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                color = foreground,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.SemiBold,
            )
            Text(
                date.lunarCalendarCellLabel().orEmpty(),
                color = foreground.copy(alpha = .78f),
                fontSize = 10.sp,
                lineHeight = 12.sp,
                maxLines = 1,
            )
            Box(
                Modifier.padding(top = 2.dp).size(if (hasEvent) 4.dp else 0.dp)
                    .clip(CircleShape).background(if (isSelected) foreground else MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun SelectedDateHeader(date: LocalDate, today: LocalDate, count: Int) {
    val dateLabel = (if (date == today) "今天 · " else "") +
        date.format(DateTimeFormatter.ofPattern("M 月 d 日 EEEE", Locale.CHINA))
    Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
        Text(dateLabel, style = MaterialTheme.typography.titleLarge)
        Text(
            buildString {
                append("农历 ").append(date.lunarDisplayLabel() ?: "—")
                if (count > 0) append("  ·  ").append(count).append(" 个记录")
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SectionHeader(title: String, detail: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun QuietEmptyRow(message: String) {
    Box(Modifier.fillMaxWidth()) {
        Text(
            message,
            Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun DayOfWeek.sundayIndex() = if (this == DayOfWeek.SUNDAY) 0 else value
