package com.athena.dates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class MainSection(val label: String, val icon: ImageVector) {
    Calendar("日历", Icons.Outlined.CalendarToday),
    Anniversary("纪念日", Icons.Outlined.FavoriteBorder),
    Countdown("倒数日", Icons.Outlined.Timer),
}

@Composable
fun rememberCurrentDate(): LocalDate {
    var currentDate by remember { mutableStateOf(LocalDate.now()) }
    val context = LocalContext.current
    if (!LocalInspectionMode.current) {
        DisposableEffect(context) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) { currentDate = LocalDate.now() }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            onDispose { context.unregisterReceiver(receiver) }
        }
        LaunchedEffect(Unit) {
            while (true) {
                val now = ZonedDateTime.now()
                delay(Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(now.zone)).toMillis().coerceAtLeast(1_000) + 250)
                currentDate = LocalDate.now()
            }
        }
    }
    return currentDate
}

@Composable
fun AthenaBottomBar(selected: MainSection, onSelected: (MainSection) -> Unit) {
    Surface(shadowElevation = 10.dp) {
        Row(
            Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            MainSection.entries.forEach { section ->
                val active = section == selected
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).selectable(
                        selected = active,
                        role = Role.Tab,
                        onClick = { onSelected(section) },
                    ).padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(section.icon, null, tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(section.label, style = MaterialTheme.typography.labelSmall, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
fun CountdownCard(entry: DateEntry, displayDate: LocalDate, today: LocalDate, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 13.dp, end = 4.dp, bottom = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (entry.note.isNotBlank()) Text(entry.note, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Text(
                    buildString {
                        append(displayDate.format(DateTimeFormatter.ofPattern(if (displayDate.year == today.year) "M 月 d 日" else "yyyy 年 M 月 d 日", Locale.CHINA)))
                        entry.lunarDate?.let { append(" · 农历 ").append(it.displayLabel(includeYear = false)) }
                        if (entry.recurrence.isRepeating) append(" · ").append(entry.recurrence.displayLabel())
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(relativeDayLabel(displayDate, today), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            EntryActions(entry, displayDate, today, onEdit, onDelete)
        }
    }
}

@Composable
fun DateEntryCard(entry: DateEntry, occurrenceDate: LocalDate, today: LocalDate, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.size(45.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(occurrenceDate.dayOfMonth.toString(), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Text(occurrenceDate.format(DateTimeFormatter.ofPattern("M月", Locale.CHINA)), fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.title, fontWeight = FontWeight.Medium)
                Text(
                    buildString {
                        append(entry.kind.label)
                        entry.eventTime?.let { append(" · ").append(it.format(DateTimeFormatter.ofPattern("HH:mm"))) }
                        append(" · ").append(entry.note.ifBlank { "当天记录" })
                    },
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append("农历 ").append(occurrenceDate.lunarDisplayLabel() ?: "—")
                        if (entry.recurrence.isRepeating) append(" · ").append(entry.recurrence.displayLabel())
                        if (entry.reminders.isNotEmpty()) append(" · ").append(entry.reminders.size).append(" 条提醒")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        entry.tags.take(3).forEach { tag ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(10.dp).clip(CircleShape).background(Color(tag.colorArgb))
                                        .border(1.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(tag.name, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            Text(relativeDayLabel(occurrenceDate, today), style = MaterialTheme.typography.labelSmall)
            EntryActions(entry, occurrenceDate, today, onEdit, onDelete)
        }
    }
}

@Composable
fun EntryActions(
    entry: DateEntry,
    occurrenceDate: LocalDate,
    today: LocalDate,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dark = isSystemInDarkTheme()
    fun share(includeNote: Boolean) {
        open = false
        scope.launch { ShareCardManager(context).share(entry, occurrenceDate, today, includeNote, dark) }
    }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Outlined.MoreVert, "更多操作") }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("编辑") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { open = false; onEdit() })
            DropdownMenuItem(text = { Text("分享卡片") }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = { share(true) })
            if (entry.note.isNotBlank()) {
                DropdownMenuItem(text = { Text("分享卡片（不含备注）") }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = { share(false) })
            }
            DropdownMenuItem(text = { Text("删除") }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) }, onClick = { open = false; onDelete() })
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Text("$message\n点击右上角 + 添加一个吧", Modifier.padding(20.dp))
    }
}

fun relativeDayLabel(date: LocalDate, reference: LocalDate): String {
    val days = ChronoUnit.DAYS.between(reference, date)
    return when {
        days == 0L -> "今天"
        days > 0L -> "$days 天后"
        else -> "已过去 ${-days} 天"
    }
}
