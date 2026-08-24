package com.athena.dates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    val allUpcoming = entries.mapNotNull { entry -> entry.nextOccurrence(today)?.let { DateOccurrence(entry, it) } }.sortedBy { it.date }
    val eventDates = (1..month.lengthOfMonth()).map(month::atDay).filterTo(mutableSetOf()) { date -> entries.any { it.occursOn(date) } }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item { MonthCalendar(month, selectedDate, today, eventDates, onMonthChange, onDateSelected) }
        item {
            Spacer(Modifier.height(16.dp))
            SectionTitle(if (selectedDate == today) "今天 · ${selectedDate.monthValue} 月 ${selectedDate.dayOfMonth} 日" else "${selectedDate.monthValue} 月 ${selectedDate.dayOfMonth} 日", if (selectedEntries.isEmpty()) "暂无事项" else "${selectedEntries.size} 项")
            Spacer(Modifier.height(10.dp))
        }
        items(selectedEntries, key = { "selected-${it.id}" }) { entry ->
            DateEntryCard(entry, selectedDate, today, { onEdit(entry) }, { onDelete(entry) })
            Spacer(Modifier.height(10.dp))
        }
        item { Spacer(Modifier.height(16.dp)); SectionTitle("即将到来", if (allUpcoming.isEmpty()) "暂无" else "${allUpcoming.size} 项"); Spacer(Modifier.height(10.dp)) }
        items(allUpcoming.take(3), key = { "upcoming-${it.entry.id}" }) { occurrence ->
            DateEntryCard(occurrence.entry, occurrence.date, today, { onEdit(occurrence.entry) }, { onDelete(occurrence.entry) })
            Spacer(Modifier.height(10.dp))
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
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton({ onMonthChange(month.minusMonths(1)) }) { Icon(Icons.Outlined.ChevronLeft, "上个月") }
            Text(month.format(DateTimeFormatter.ofPattern("yyyy 年 M 月", Locale.CHINA)), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            IconButton({ onMonthChange(month.plusMonths(1)) }) { Icon(Icons.Outlined.ChevronRight, "下个月") }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium) }
        }
        val offset = month.atDay(1).dayOfWeek.sundayIndex()
        repeat(6) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { index ->
                    val number = week * 7 + index - offset + 1
                    if (number in 1..month.lengthOfMonth()) {
                        val date = month.atDay(number)
                        CalendarDay(date, date == today, date == selectedDate, date in eventDates, { onDateSelected(date) }, Modifier.weight(1f))
                    } else Spacer(Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(date: LocalDate, isToday: Boolean, isSelected: Boolean, hasEvent: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val background = when { isSelected -> MaterialTheme.colorScheme.primary; isToday -> MaterialTheme.colorScheme.primaryContainer; else -> Color.Transparent }
    val foreground = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier.aspectRatio(1f).padding(3.dp).clip(CircleShape).background(background).selectable(isSelected, role = Role.RadioButton, onClick = onClick).semantics {
            contentDescription = date.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA)) + if (hasEvent) "，有事项" else "，无事项"
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date.dayOfMonth.toString(), color = foreground, style = MaterialTheme.typography.bodySmall)
            if (hasEvent) Box(Modifier.padding(top = 2.dp).size(4.dp).clip(CircleShape).background(if (isSelected) foreground else MaterialTheme.colorScheme.primary))
        }
    }
}

@Composable
private fun SectionTitle(title: String, detail: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(detail, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
    }
}

private fun DayOfWeek.sundayIndex() = if (this == DayOfWeek.SUNDAY) 0 else value
