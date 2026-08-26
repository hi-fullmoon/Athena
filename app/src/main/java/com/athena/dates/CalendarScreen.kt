package com.athena.dates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
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
) {
    val selectedEntries = entries.filter { it.occursOn(selectedDate) }
    val allUpcoming = entries.mapNotNull { entry -> entry.nextOccurrence(today)?.let { DateOccurrence(entry, it) } }
    val eventDates = (1..month.lengthOfMonth()).map(month::atDay).filterTo(mutableSetOf()) { date -> entries.any { it.occursOn(date) } }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
    ) {
        item {
            MonthCalendar(month, selectedDate, today, eventDates, onMonthChange, onDateSelected)
            Spacer(Modifier.height(18.dp))
            SelectedDateHeader(selectedDate, today, selectedEntries.size)
            Spacer(Modifier.height(10.dp))
        }
        if (selectedEntries.isEmpty()) {
            item {
                Surface(
                    Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .42f),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(
                        "这一天还没有安排",
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        items(selectedEntries, key = { "selected-${it.id}" }) { entry ->
            DateEntryCard(entry, selectedDate, today, { onEdit(entry) }, { onDelete(entry) })
            Spacer(Modifier.height(8.dp))
        }
        item {
            Spacer(Modifier.height(20.dp))
            SectionHeader("接下来", if (allUpcoming.isEmpty()) "暂无事项" else "${allUpcoming.size} 项")
            Spacer(Modifier.height(10.dp))
        }
        if (allUpcoming.isEmpty()) {
            item {
                Surface(
                    Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .42f),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(
                        "未来没有待办，给自己留一点期待吧",
                        Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
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
    onMonthChange: (YearMonth) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(start = 6.dp)) {
                    Text(month.format(DateTimeFormatter.ofPattern("yyyy 年 M 月", Locale.CHINA)), style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (eventDates.isEmpty()) "本月暂无记录" else "本月 ${eventDates.size} 个记录日",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (month != YearMonth.from(today)) {
                    Surface(
                        onClick = {
                            onMonthChange(YearMonth.from(today))
                            onDateSelected(today)
                        },
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics { contentDescription = "回到今天" },
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(50),
                    ) {
                        Text("今天", Modifier.padding(horizontal = 10.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.size(4.dp))
                }
                IconButton({ onMonthChange(month.minusMonths(1)) }) { Icon(Icons.Outlined.ChevronLeft, "上个月") }
                IconButton({ onMonthChange(month.plusMonths(1)) }) { Icon(Icons.Outlined.ChevronRight, "下个月") }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("日", "一", "二", "三", "四", "五", "六").forEachIndexed { index, label ->
                    Text(
                        label,
                        Modifier.weight(1f).padding(bottom = 4.dp),
                        textAlign = TextAlign.Center,
                        color = if (index == 0 || index == 6) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            val offset = month.atDay(1).dayOfWeek.sundayIndex()
            val weekCount = (offset + month.lengthOfMonth() + 6) / 7
            val fontScale = LocalDensity.current.fontScale
            val cellHeight = maxOf(48f, 21f * fontScale + 12f).dp
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
    val background = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val foreground = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier.height(cellHeight)
            .selectable(isSelected, role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = date.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA)) + if (hasEvent) "，有事项" else "，无事项"
            }
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(date.dayOfMonth.toString(), color = foreground, fontSize = 12.sp, lineHeight = 13.sp, fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium)
            Text(date.lunarCalendarCellLabel().orEmpty(), color = foreground.copy(alpha = .76f), fontSize = 7.sp, lineHeight = 8.sp, maxLines = 1)
            Box(Modifier.padding(top = 1.dp).size(if (hasEvent) 3.dp else 0.dp).clip(CircleShape).background(if (isSelected) foreground else MaterialTheme.colorScheme.primary))
        }
    }
}

@Composable
private fun SelectedDateHeader(date: LocalDate, today: LocalDate, count: Int) {
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val dateLabel = (if (date == today) "今天 · " else "") +
        date.format(DateTimeFormatter.ofPattern("M 月 d 日 EEEE", Locale.CHINA))
    val countLabel = if (count == 0) "无事项" else "$count 项"
    Surface(
        Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .32f),
        shape = RoundedCornerShape(18.dp),
    ) {
        if (largeText) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    DateHeaderIcon()
                    Spacer(Modifier.weight(1f))
                    Text(countLabel, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Text(dateLabel, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.titleMedium)
                Text("农历 ${date.lunarDisplayLabel() ?: "—"}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                DateHeaderIcon()
                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                    Text(dateLabel, style = MaterialTheme.typography.titleMedium)
                    Text("农历 ${date.lunarDisplayLabel() ?: "—"}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Text(countLabel, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DateHeaderIcon() {
    Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape) {
        Icon(
            Icons.Outlined.CalendarToday,
            null,
            Modifier.padding(8.dp).size(18.dp),
            tint = MaterialTheme.colorScheme.onPrimary,
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

private fun DayOfWeek.sundayIndex() = if (this == DayOfWeek.SUNDAY) 0 else value
