package com.athena.dates

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AthenaApp() }
    }
}

private enum class AthenaPalette(
    val label: String,
    val primary: Color,
    val soft: Color,
) {
    Violet("雾紫", Color(0xFF6757D9), Color(0xFFF0EEFF)),
    Sage("鼠尾草", Color(0xFF3D725F), Color(0xFFE9F5EE)),
    Amber("晨曦橙", Color(0xFFA9502E), Color(0xFFFFF0E9)),
    Ocean("深海蓝", Color(0xFF2F73A9), Color(0xFFEAF4FB)),
    Rose("桃粉", Color(0xFFA74466), Color(0xFFFCECF1)),
}

private enum class MainSection(val label: String, val icon: ImageVector) {
    Calendar("日历", Icons.Outlined.CalendarToday),
    Anniversary("纪念日", Icons.Outlined.FavoriteBorder),
    Countdown("倒数日", Icons.Outlined.Timer),
}

private val MainSectionSaver = Saver<MainSection, String>(
    save = { it.name },
    restore = { saved -> MainSection.entries.firstOrNull { it.name == saved } ?: MainSection.Calendar },
)

private val YearMonthSaver = Saver<YearMonth, String>(
    save = { it.toString() },
    restore = YearMonth::parse,
)

private val LocalDateSaver = Saver<LocalDate, String>(
    save = { it.toString() },
    restore = LocalDate::parse,
)

private val CalendarAccessibilityFormatter =
    DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA)

@Composable
private fun rememberCurrentDate(): LocalDate {
    var currentDate by remember { mutableStateOf(LocalDate.now()) }
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current

    if (!isPreview) {
        DisposableEffect(context) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    currentDate = LocalDate.now()
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            onDispose { context.unregisterReceiver(receiver) }
        }

        LaunchedEffect(Unit) {
            while (true) {
                val now = ZonedDateTime.now()
                val nextDay = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
                val waitMillis = Duration.between(now, nextDay).toMillis().coerceAtLeast(1_000L)
                delay(waitMillis + 250L)
                currentDate = LocalDate.now()
            }
        }
    }
    return currentDate
}

@Composable
private fun AthenaTheme(palette: AthenaPalette, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = palette.primary,
            onPrimary = Color.White,
            primaryContainer = palette.soft,
            onPrimaryContainer = Color(0xFF252536),
            secondary = palette.primary,
            background = Color(0xFFFFFBFF),
            surface = Color(0xFFFFFBFF),
            surfaceVariant = Color(0xFFF3F1F7),
            onSurface = Color(0xFF20212A),
            onSurfaceVariant = Color(0xFF727384),
        ),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AthenaApp() {
    val context = LocalContext.current
    val today = rememberCurrentDate()
    val store = remember { AthenaStore(context.applicationContext) }
    val entries by store.entries.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()
    var activeSection by rememberSaveable(stateSaver = MainSectionSaver) { mutableStateOf(MainSection.Calendar) }
    var palette by remember {
        mutableStateOf(
            store.loadPaletteName()
                ?.let { saved -> AthenaPalette.entries.firstOrNull { it.name == saved } }
                ?: AthenaPalette.Violet,
        )
    }
    var paletteMenuOpen by remember { mutableStateOf(false) }
    var addSheetOpen by rememberSaveable { mutableStateOf(false) }
    var editingEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteCandidateId by rememberSaveable { mutableStateOf<String?>(null) }
    var month by rememberSaveable(stateSaver = YearMonthSaver) { mutableStateOf(YearMonth.from(today)) }
    var selectedDate by rememberSaveable(stateSaver = LocalDateSaver) { mutableStateOf(today) }
    val editingEntry = editingEntryId?.let { id -> entries.firstOrNull { it.id == id } }
    val deleteCandidate = deleteCandidateId?.let { id -> entries.firstOrNull { it.id == id } }

    fun openEditor(entry: DateEntry? = null) {
        paletteMenuOpen = false
        editingEntryId = entry?.id
        addSheetOpen = true
    }

    AthenaTheme(palette) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(activeSection.label, fontWeight = FontWeight.SemiBold)
                        },
                        actions = {
                            IconButton(onClick = { openEditor() }) {
                                Icon(Icons.Outlined.Add, contentDescription = "添加重要日子")
                            }
                            Box {
                                IconButton(onClick = { paletteMenuOpen = true }) {
                                    Icon(Icons.Outlined.MoreVert, contentDescription = "更多设置")
                                }
                                DropdownMenu(
                                    expanded = paletteMenuOpen,
                                    onDismissRequest = { paletteMenuOpen = false },
                                ) {
                                    AthenaPalette.entries.forEach { choice ->
                                        DropdownMenuItem(
                                            text = { Text(choice.label) },
                                            leadingIcon = {
                                                Box(
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .clip(CircleShape)
                                                        .background(choice.primary),
                                                )
                                            },
                                            trailingIcon = {
                                                if (choice == palette) {
                                                    Icon(Icons.Outlined.Check, contentDescription = "当前主题")
                                                }
                                            },
                                            onClick = {
                                                palette = choice
                                                store.savePaletteName(choice.name)
                                                paletteMenuOpen = false
                                            },
                                        )
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                },
                bottomBar = {
                    AthenaBottomBar(selected = activeSection, onSelected = { activeSection = it })
                },
            ) { padding ->
                when (activeSection) {
                    MainSection.Calendar -> CalendarScreen(
                        entries = entries,
                        today = today,
                        month = month,
                        selectedDate = selectedDate,
                        onMonthChange = { newMonth ->
                            month = newMonth
                            selectedDate = newMonth.atDay(minOf(selectedDate.dayOfMonth, newMonth.lengthOfMonth()))
                        },
                        onDateSelected = { selectedDate = it },
                        onEdit = ::openEditor,
                        onDelete = { deleteCandidateId = it.id },
                        modifier = Modifier.padding(padding),
                    )

                    MainSection.Anniversary -> EntryListScreen(
                        emptyMessage = "还没有纪念日",
                        entries = entries.filter { it.kind == DateKind.Anniversary },
                        today = today,
                        onEdit = ::openEditor,
                        onDelete = { deleteCandidateId = it.id },
                        modifier = Modifier.padding(padding),
                    )

                    MainSection.Countdown -> EntryListScreen(
                        emptyMessage = "还没有倒数日",
                        entries = entries.filter { it.kind == DateKind.Countdown },
                        today = today,
                        onEdit = ::openEditor,
                        onDelete = { deleteCandidateId = it.id },
                        modifier = Modifier.padding(padding),
                    )
                }
            }

            if (addSheetOpen) {
                AddDateSheet(
                    existingEntry = editingEntry,
                    today = today,
                    onDismiss = {
                        addSheetOpen = false
                        editingEntryId = null
                    },
                    onSave = { entry ->
                        coroutineScope.launch { store.upsert(entry) }
                        val focusDate = entry.nextOccurrence(today) ?: entry.date
                        month = YearMonth.from(focusDate)
                        selectedDate = focusDate
                        activeSection = when (entry.kind) {
                            DateKind.Anniversary -> MainSection.Anniversary
                            DateKind.Countdown -> MainSection.Countdown
                            DateKind.Schedule -> MainSection.Calendar
                        }
                        addSheetOpen = false
                        editingEntryId = null
                    },
                )
            }

            deleteCandidate?.let { entry ->
                AlertDialog(
                    onDismissRequest = { deleteCandidateId = null },
                    icon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                    title = { Text("删除“${entry.title}”？") },
                    text = { Text("删除后无法恢复，这个日期及其重复记录都会消失。") },
                    confirmButton = {
                        Button(
                            onClick = {
                                coroutineScope.launch { store.delete(entry.id) }
                                deleteCandidateId = null
                            },
                        ) { Text("删除") }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { deleteCandidateId = null }) { Text("取消") }
                    },
                )
            }
        }
    }
}

@Composable
private fun AthenaBottomBar(selected: MainSection, onSelected: (MainSection) -> Unit) {
    Surface(shadowElevation = 10.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            MainSection.entries.forEach { section ->
                val isSelected = section == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .selectable(
                            selected = isSelected,
                            role = Role.Tab,
                            onClick = { onSelected(section) },
                        )
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        section.icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        section.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarScreen(
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
    val allUpcoming = entries
        .mapNotNull { entry -> entry.nextOccurrence(today)?.let { DateOccurrence(entry, it) } }
        .sortedBy { it.date }
    val upcoming = allUpcoming.take(3)
    val eventDates = (1..month.lengthOfMonth())
        .map(month::atDay)
        .filterTo(mutableSetOf()) { date -> entries.any { it.occursOn(date) } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
    ) {
        item {
            MonthCalendar(
                month = month,
                selectedDate = selectedDate,
                today = today,
                eventDates = eventDates,
                onMonthChange = onMonthChange,
                onDateSelected = onDateSelected,
            )
            Spacer(Modifier.height(12.dp))
        }
        item {
            SelectedDateHeader(date = selectedDate, entryCount = selectedEntries.size, today = today)
            Spacer(Modifier.height(10.dp))
        }
        items(selectedEntries, key = { "selected-${it.id}" }) { entry ->
            DateEntryCard(
                entry = entry,
                occurrenceDate = selectedDate,
                today = today,
                onEdit = { onEdit(entry) },
                onDelete = { onDelete(entry) },
            )
            Spacer(Modifier.height(10.dp))
        }
        item {
            Spacer(Modifier.height(16.dp))
            SectionTitle(
                title = "即将到来",
                detail = when {
                    allUpcoming.isEmpty() -> "暂无"
                    else -> "${allUpcoming.size} 项"
                },
            )
            Spacer(Modifier.height(10.dp))
        }
        items(upcoming, key = { "upcoming-${it.entry.id}" }) { occurrence ->
            DateEntryCard(
                entry = occurrence.entry,
                occurrenceDate = occurrence.date,
                today = today,
                onEdit = { onEdit(occurrence.entry) },
                onDelete = { onDelete(occurrence.entry) },
            )
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "上个月")
            }
            Text(
                text = month.format(DateTimeFormatter.ofPattern("yyyy 年 M 月", Locale.CHINA)),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "下个月")
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val offset = month.atDay(1).dayOfWeek.sundayIndex()
        val days = month.lengthOfMonth()
        repeat(6) { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { dayIndex ->
                    val number = week * 7 + dayIndex - offset + 1
                    if (number in 1..days) {
                        val date = month.atDay(number)
                        CalendarDay(
                            date = date,
                            isToday = date == today,
                            isSelected = date == selectedDate,
                            hasEvent = eventDates.contains(date),
                            onClick = { onDateSelected(date) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val foreground = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(3.dp)
            .clip(CircleShape)
            .background(background)
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics {
                contentDescription = buildString {
                    append(date.format(CalendarAccessibilityFormatter))
                    append(if (hasEvent) "，有事项" else "，无事项")
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date.dayOfMonth.toString(), color = foreground, style = MaterialTheme.typography.bodySmall)
            if (hasEvent) {
                Box(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) foreground else MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun SelectedDateHeader(date: LocalDate, entryCount: Int, today: LocalDate) {
    val dateLabel = if (date == today) {
        "今天 · ${date.format(DateTimeFormatter.ofPattern("M 月 d 日", Locale.CHINA))}"
    } else {
        date.format(DateTimeFormatter.ofPattern("M 月 d 日 · EEEE", Locale.CHINA))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = dateLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (entryCount == 0) "暂无事项" else "$entryCount 项",
            color = if (entryCount == 0) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun EntryListScreen(
    emptyMessage: String,
    entries: List<DateEntry>,
    today: LocalDate,
    onEdit: (DateEntry) -> Unit,
    onDelete: (DateEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sortedEntries = entries.sortedWith(
        compareBy<DateEntry> { it.nextOccurrence(today) == null }
            .thenBy { it.nextOccurrence(today) ?: it.date },
    )
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
    ) {
        if (entries.isEmpty()) {
            item {
                EmptyState(emptyMessage)
            }
        } else {
            items(sortedEntries, key = { it.id }) { entry ->
                CountdownCard(
                    entry = entry,
                    displayDate = entry.nextOccurrence(today) ?: entry.date,
                    today = today,
                    onEdit = { onEdit(entry) },
                    onDelete = { onDelete(entry) },
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun CountdownCard(
    entry: DateEntry,
    displayDate: LocalDate,
    today: LocalDate,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 13.dp, end = 4.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val datePattern = if (displayDate.year == today.year) "M 月 d 日" else "yyyy 年 M 月 d 日"
                if (entry.note.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = entry.note,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(displayDate.format(DateTimeFormatter.ofPattern(datePattern, Locale.CHINA)))
                        if (entry.repeatsYearly) append(" · 每年")
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(
                text = relativeDayLabel(displayDate, today),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            EntryActions(onEdit = onEdit, onDelete = onDelete)
        }
    }
}

@Composable
private fun DateEntryCard(
    entry: DateEntry,
    occurrenceDate: LocalDate,
    today: LocalDate,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .size(45.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(occurrenceDate.dayOfMonth.toString(), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Text(occurrenceDate.format(DateTimeFormatter.ofPattern("M月", Locale.CHINA)), fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.title, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text("${entry.kind.label} · ${entry.note.ifBlank { "当天记录" }}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(relativeDayLabel(occurrenceDate, today), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            EntryActions(onEdit = onEdit, onDelete = onDelete)
        }
    }
}

@Composable
private fun EntryActions(onEdit: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "更多操作")
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("编辑") },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text("删除") },
                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDateSheet(
    existingEntry: DateEntry?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (DateEntry) -> Unit,
) {
    var title by rememberSaveable(existingEntry?.id) { mutableStateOf(existingEntry?.title.orEmpty()) }
    var note by rememberSaveable(existingEntry?.id) { mutableStateOf(existingEntry?.note.orEmpty()) }
    var noteExpanded by rememberSaveable(existingEntry?.id) {
        mutableStateOf(existingEntry?.note?.isNotBlank() == true)
    }
    var kindName by rememberSaveable(existingEntry?.id) { mutableStateOf((existingEntry?.kind ?: DateKind.Anniversary).name) }
    var repeatsYearly by rememberSaveable(existingEntry?.id) { mutableStateOf(existingEntry?.repeatsYearly ?: true) }
    var targetDateIso by rememberSaveable(existingEntry?.id) { mutableStateOf((existingEntry?.date ?: today.plusDays(1)).toString()) }
    var datePickerOpen by rememberSaveable { mutableStateOf(false) }
    val kind = DateKind.entries.firstOrNull { it.name == kindName } ?: DateKind.Anniversary
    val targetDate = runCatching { LocalDate.parse(targetDateIso) }.getOrDefault(today.plusDays(1))
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = Color.Transparent,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (existingEntry == null) "添加重要日子" else "编辑重要日子",
                    modifier = Modifier.weight(1f),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭")
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("类型", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            DateKindSelector(
                selected = kind,
                onSelected = { option ->
                    kindName = option.name
                    repeatsYearly = option == DateKind.Anniversary
                },
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(40) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("名称") },
                placeholder = { Text("例如：旅行出发") },
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            )
            Spacer(Modifier.height(10.dp))
            if (noteExpanded) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(120) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备注（可选）") },
                    placeholder = { Text("写下一点期待或备注") },
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                )
            } else {
                OutlinedButton(
                    onClick = { noteExpanded = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Notes, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("添加备注")
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("日期", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            DateSelectionCard(date = targetDate, onClick = { datePickerOpen = true })
            if (kind == DateKind.Anniversary) {
                Spacer(Modifier.height(10.dp))
                RepeatSetting(
                    checked = repeatsYearly,
                    onCheckedChange = { repeatsYearly = it },
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (title.trim().isNotEmpty()) {
                        onSave(
                            DateEntry(
                                id = existingEntry?.id ?: java.util.UUID.randomUUID().toString(),
                                title = title.trim(),
                                note = note.trim(),
                                date = targetDate,
                                kind = kind,
                                repeatsYearly = repeatsYearly,
                            ),
                        )
                    }
                },
                enabled = title.trim().isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (existingEntry == null) "保存这个日子" else "保存修改",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }

    if (datePickerOpen) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = targetDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                Button(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        targetDateIso = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    datePickerOpen = false
                }) { Text("确定") }
            },
            dismissButton = { OutlinedButton(onClick = { datePickerOpen = false }) { Text("取消") } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun DateKindSelector(
    selected: DateKind,
    onSelected: (DateKind) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DateKind.entries.forEach { option ->
            val isSelected = option == selected
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelected(option) },
                    ),
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = when (option) {
                            DateKind.Anniversary -> Icons.Outlined.FavoriteBorder
                            DateKind.Countdown -> Icons.Outlined.Timer
                            DateKind.Schedule -> Icons.Outlined.CalendarToday
                        },
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun DateSelectionCard(date: LocalDate, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日 · EEEE", Locale.CHINA)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = "选择日期",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun RepeatSetting(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.EventRepeat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "每年重复",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun SectionTitle(title: String, detail: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(detail, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun EmptyState(message: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "$message\n点击右上角 + 添加一个吧",
            modifier = Modifier.padding(20.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

private fun relativeDayLabel(
    date: LocalDate,
    reference: LocalDate,
    compact: Boolean = true,
): String {
    val days = ChronoUnit.DAYS.between(reference, date)
    return when {
        days == 0L -> if (compact) "今天" else "就是今天"
        days > 0L -> if (compact) "$days 天后" else "$days 天"
        else -> "已过去 ${-days} 天"
    }
}

private fun DayOfWeek.sundayIndex(): Int = if (this == DayOfWeek.SUNDAY) 0 else value

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AthenaPreview() {
    AthenaApp()
}
