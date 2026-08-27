package com.athena.dates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlin.math.absoluteValue

enum class MainSection(val label: String, val icon: ImageVector) {
    Calendar("日历", Icons.Outlined.CalendarToday),
    Anniversary("纪念日", Icons.Outlined.FavoriteBorder),
    Countdown("倒数日", Icons.Outlined.Timer),
}

@Composable
fun AthenaHeader(section: MainSection, today: LocalDate, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding()
            .padding(start = 20.dp, top = 8.dp, end = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(section.label, style = MaterialTheme.typography.headlineMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "ATHENA",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    "  ·  ${today.format(DateTimeFormatter.ofPattern("M 月 d 日 EEEE", Locale.CHINA))}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.Settings, "设置", Modifier.size(21.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, shadowElevation = 0.dp) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().selectableGroup()
                    .padding(horizontal = 16.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                MainSection.entries.forEach { section ->
                    val active = section == selected
                    Column(
                        Modifier.weight(1f).heightIn(min = 54.dp).selectable(
                            selected = active,
                            role = Role.Tab,
                            onClick = { onSelected(section) },
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Surface(
                            color = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(
                                section.icon,
                                null,
                                Modifier.padding(horizontal = 10.dp, vertical = 4.dp).size(21.dp),
                                tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            section.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CountdownCard(entry: DateEntry, displayDate: LocalDate, today: LocalDate, onEdit: () -> Unit, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RelativeDayPanel(displayDate, today)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        append(displayDate.format(DateTimeFormatter.ofPattern(if (displayDate.year == today.year) "M 月 d 日" else "yyyy 年 M 月 d 日", Locale.CHINA)))
                        entry.lunarDate?.let { append(" · 农历 ").append(it.displayLabel(includeYear = false)) }
                        if (entry.recurrence.isRepeating) append(" · ").append(entry.recurrence.displayLabel())
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.note.isNotBlank()) {
                    Text(
                        entry.note,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            EntryActions(entry, displayDate, today, onEdit, onDelete)
        }
    }
}

@Composable
private fun RelativeDayPanel(date: LocalDate, today: LocalDate) {
    val days = ChronoUnit.DAYS.between(today, date)
    Surface(
        modifier = Modifier.widthIn(min = 58.dp).heightIn(min = 62.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            Modifier.padding(horizontal = 4.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (days == 0L) {
                Text("今天", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.titleMedium)
            } else {
                Text(
                    days.absoluteValue.toString(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                )
                Text(if (days > 0) "天后" else "天前", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun DateEntryCard(
    entry: DateEntry,
    occurrenceDate: LocalDate,
    today: LocalDate,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    showDateContext: Boolean = true,
) {
    val largeText = LocalDensity.current.fontScale >= 1.5f
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showDateContext) {
                Column(
                    Modifier.width(48.dp).heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer).padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        occurrenceDate.dayOfMonth.toString(),
                        fontSize = 20.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        occurrenceDate.format(DateTimeFormatter.ofPattern("M 月", Locale.CHINA)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        if (showDateContext && largeText) {
                            append(compactRelativeDayLabel(occurrenceDate, today))
                            append(" · ")
                            append(
                                occurrenceDate.format(
                                    DateTimeFormatter.ofPattern(
                                        if (occurrenceDate.year == today.year) "M 月 d 日" else "yyyy 年 M 月 d 日",
                                        Locale.CHINA,
                                    ),
                                ),
                            )
                            append(" · ")
                        }
                        append(entry.kind.label)
                        entry.eventTime?.let { append(" · ").append(it.format(DateTimeFormatter.ofPattern("HH:mm"))) }
                        if (entry.recurrence.isRepeating) append(" · ").append(entry.recurrence.displayLabel())
                        if (entry.reminders.isNotEmpty()) append(" · ").append(entry.reminders.size).append(" 条提醒")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        buildString {
                            append("农历 ").append(occurrenceDate.lunarDisplayLabel() ?: "—")
                            if (entry.note.isNotBlank()) append(" · ").append(entry.note)
                        },
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (entry.tags.isNotEmpty()) {
                        val tag = entry.tags.first()
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier.size(8.dp).clip(CircleShape).background(Color(tag.colorArgb))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (entry.tags.size == 1) tag.name else "${tag.name} +${entry.tags.size - 1}",
                            Modifier.widthIn(max = 72.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (showDateContext && !largeText) {
                Text(
                    compactRelativeDayLabel(occurrenceDate, today),
                    Modifier.widthIn(max = 68.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                )
            }
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
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    fun share(includeNote: Boolean) {
        open = false
        scope.launch { ShareCardManager(context).share(entry, occurrenceDate, today, includeNote, dark) }
    }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.MoreVert, "更多操作", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
fun EmptyState(
    message: String,
    detail: String = "轻触右下角的加号，记下第一个重要日子",
) {
    Surface(Modifier.fillMaxWidth(), color = Color.Transparent) {
        Column(
            Modifier.padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(
                    Icons.Outlined.CalendarToday,
                    null,
                    Modifier.padding(12.dp).size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(message, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

fun relativeDayLabel(date: LocalDate, reference: LocalDate): String {
    val days = ChronoUnit.DAYS.between(reference, date)
    return when {
        days == 0L -> "今天"
        days > 0L -> "$days 天后"
        else -> "${-days} 天前"
    }
}

private fun compactRelativeDayLabel(date: LocalDate, reference: LocalDate): String {
    val days = ChronoUnit.DAYS.between(reference, date)
    return when {
        days == 0L -> "今天"
        days > 0L -> "${days}天后"
        else -> "${-days}天前"
    }
}
