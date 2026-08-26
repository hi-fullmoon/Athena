package com.athena.dates

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val MainSectionSaver = Saver<MainSection, String>({ it.name }, { MainSection.valueOf(it) })
private val YearMonthSaver = Saver<YearMonth, String>({ it.toString() }, YearMonth::parse)
private val LocalDateSaver = Saver<LocalDate, String>({ it.toString() }, LocalDate::parse)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AthenaApp(
    viewModel: AthenaViewModel,
    launchAction: String? = null,
    onLaunchActionConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    var notificationPermissionExplanation by rememberSaveable { mutableStateOf(false) }
    var notificationSettingsGuidance by rememberSaveable { mutableStateOf(false) }
    var calendarPermissionExplanation by rememberSaveable { mutableStateOf(false) }
    var calendarPermissionDenied by rememberSaveable { mutableStateOf(false) }
    var calendarExportPermissionExplanation by rememberSaveable { mutableStateOf(false) }
    var calendarExportPermissionDenied by rememberSaveable { mutableStateOf(false) }
    var contactsPermissionExplanation by rememberSaveable { mutableStateOf(false) }
    var contactsPermissionDenied by rememberSaveable { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.rescheduleReminders() else notificationSettingsGuidance = true
    }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.startCalendarImport()
        } else {
            calendarPermissionDenied = true
        }
    }
    val calendarExportPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.READ_CALENDAR] == true && result[Manifest.permission.WRITE_CALENDAR] == true) {
            viewModel.startCalendarExport()
        } else {
            calendarExportPermissionDenied = true
        }
    }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startContactsImport() else contactsPermissionDenied = true
    }
    val jsonExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportJson)
    }
    val icsExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        uri?.let(viewModel::exportIcs)
    }
    val jsonImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.previewImport(it, TransferFormat.Json) }
    }
    val icsImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.previewImport(it, TransferFormat.Ics) }
    }
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val entryQuery by viewModel.entryQuery.collectAsStateWithLifecycle()
    val dataTransferState by viewModel.dataTransferState.collectAsStateWithLifecycle()
    val calendarImportState by viewModel.calendarImportState.collectAsStateWithLifecycle()
    val calendarExportState by viewModel.calendarExportState.collectAsStateWithLifecycle()
    val contactsImportState by viewModel.contactsImportState.collectAsStateWithLifecycle()
    val today = rememberCurrentDate()
    val palette = AthenaPalette.entries.firstOrNull { it.name == appearance.paletteName } ?: AthenaPalette.Violet
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (appearance.themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val visibleEntries = remember(entries, today, entryQuery) {
        filterAndSortEntries(entries, today, entryQuery)
    }
    val archivedEntries = remember(entries) { entries.filter(DateEntry::isArchived) }
    var section by rememberSaveable(stateSaver = MainSectionSaver) { mutableStateOf(MainSection.Calendar) }
    var month by rememberSaveable(stateSaver = YearMonthSaver) { mutableStateOf(YearMonth.from(today)) }
    var selectedDate by rememberSaveable(stateSaver = LocalDateSaver) { mutableStateOf(today) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var archiveOpen by rememberSaveable { mutableStateOf(false) }
    var dataManagementOpen by rememberSaveable { mutableStateOf(false) }
    var replaceConfirmationOpen by rememberSaveable { mutableStateOf(false) }
    var calendarImportOpen by rememberSaveable { mutableStateOf(false) }
    var calendarExportOpen by rememberSaveable { mutableStateOf(false) }
    var contactsImportOpen by rememberSaveable { mutableStateOf(false) }
    val editing = editingId?.let { id -> entries.firstOrNull { it.id == id } }
    val deleting = deletingId?.let { id -> entries.firstOrNull { it.id == id } }

    LaunchedEffect(Unit) { createReminderChannel(context) }

    val view = LocalView.current
    SideEffect {
        context.findActivity()?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    fun openEditor(entry: DateEntry? = null) { editingId = entry?.id; editorOpen = true }
    fun guideNotificationAccess() {
        createReminderChannel(context)
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionExplanation = true
        } else if (!canPostReminderNotifications(context)) {
            notificationSettingsGuidance = true
        }
    }
    fun guideCalendarReadAccess() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            calendarImportOpen = true
            viewModel.startCalendarImport()
        } else {
            calendarPermissionExplanation = true
        }
    }
    fun guideCalendarExportAccess() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            calendarExportOpen = true
            viewModel.startCalendarExport()
        } else {
            calendarExportPermissionExplanation = true
        }
    }
    fun guideContactsAccess() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            contactsImportOpen = true
            viewModel.startContactsImport()
        } else {
            contactsPermissionExplanation = true
        }
    }

    LaunchedEffect(launchAction) {
        when (launchAction) {
            ACTION_SHORTCUT_ADD -> openEditor()
            ACTION_SHORTCUT_UPCOMING, ACTION_OPEN_REMINDER -> {
                section = MainSection.Calendar
                viewModel.updateEntryQuery { it.copy(status = EntryStatusFilter.Active, sort = EntrySort.NextOccurrence) }
            }
            ACTION_SHORTCUT_SETTINGS -> settingsOpen = true
            null -> return@LaunchedEffect
        }
        onLaunchActionConsumed()
    }

    AthenaTheme(palette, appearance, darkTheme) {
        val compactAddAction = LocalDensity.current.fontScale >= 1.5f
        Surface(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    AthenaHeader(section, today) { settingsOpen = true }
                },
                bottomBar = { AthenaBottomBar(section) { section = it } },
                floatingActionButton = {
                    if (compactAddAction) {
                        FloatingActionButton(
                            onClick = { openEditor() },
                            shape = RoundedCornerShape(20.dp),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.semantics { contentDescription = "添加重要日子" },
                        ) { Icon(Icons.Outlined.Add, null) }
                    } else {
                        ExtendedFloatingActionButton(
                            onClick = { openEditor() },
                            shape = RoundedCornerShape(20.dp),
                            icon = { Icon(Icons.Outlined.Add, null) },
                            text = { Text("新建") },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.semantics { contentDescription = "添加重要日子" },
                        )
                    }
                },
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    EntrySearchControls(
                        query = entryQuery,
                        availableTags = tags,
                        onQueryChange = viewModel::updateEntryQuery,
                        onReset = viewModel::resetEntryQuery,
                    )
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        when (section) {
                            MainSection.Calendar -> CalendarScreen(visibleEntries, today, month, selectedDate, { newMonth -> month = newMonth; selectedDate = newMonth.atDay(minOf(selectedDate.dayOfMonth, newMonth.lengthOfMonth())) }, { selectedDate = it }, ::openEditor, { deletingId = it.id }, Modifier.fillMaxSize())
                            MainSection.Anniversary -> AnniversaryScreen(visibleEntries, today, ::openEditor, { deletingId = it.id }, Modifier.fillMaxSize())
                            MainSection.Countdown -> CountdownScreen(visibleEntries, today, ::openEditor, { deletingId = it.id }, Modifier.fillMaxSize())
                        }
                    }
                }
            }
            if (editorOpen) EditorSheet(
                existingEntry = editing,
                today = today,
                availableTags = tags,
                onDismiss = { editorOpen = false; editingId = null },
            ) { entry ->
                if (entry.reminderEnabled) guideNotificationAccess()
                viewModel.save(entry)
                val focusDate = entry.nextOccurrence(today) ?: entry.date
                month = YearMonth.from(focusDate); selectedDate = focusDate
                section = when (entry.kind) { DateKind.Anniversary -> MainSection.Anniversary; DateKind.Countdown -> MainSection.Countdown; DateKind.Schedule -> MainSection.Calendar }
                editorOpen = false; editingId = null
            }
            deleting?.let { entry ->
                AlertDialog(
                    onDismissRequest = { deletingId = null },
                    icon = { Icon(Icons.Outlined.DeleteOutline, null) },
                    title = { Text("删除“${entry.title}”？") },
                    text = { Text("删除后无法恢复，这个日期及其重复记录都会消失。") },
                    confirmButton = { Button({ viewModel.delete(entry.id); deletingId = null }) { Text("删除") } },
                    dismissButton = { OutlinedButton({ deletingId = null }) { Text("取消") } },
                )
            }
            if (notificationPermissionExplanation) {
                AlertDialog(
                    onDismissRequest = { notificationPermissionExplanation = false },
                    title = { Text("开启日期提醒通知") },
                    text = { Text("Athena 需要通知权限，才能在你选择的日期和时间发送本地提醒。提醒数据只保存在此设备上。") },
                    confirmButton = {
                        Button({
                            notificationPermissionExplanation = false
                            notificationPermission.launch(POST_NOTIFICATIONS_PERMISSION)
                        }) { Text("继续") }
                    },
                    dismissButton = {
                        OutlinedButton({ notificationPermissionExplanation = false }) { Text("暂不开启") }
                    },
                )
            }
            if (notificationSettingsGuidance) {
                AlertDialog(
                    onDismissRequest = { notificationSettingsGuidance = false },
                    title = { Text("通知尚未开启") },
                    text = { Text("提醒已经保存，但需要在系统设置中允许 Athena 通知后才能显示。") },
                    confirmButton = {
                        Button({
                            notificationSettingsGuidance = false
                            openNotificationSettings(context)
                        }) { Text("前往设置") }
                    },
                    dismissButton = {
                        OutlinedButton({ notificationSettingsGuidance = false }) { Text("稍后") }
                    },
                )
            }
            if (settingsOpen) {
                SettingsSheet(
                    appearance = appearance,
                    reminderCount = entries.count { it.reminderEnabled && !it.isArchived },
                    notificationsAvailable = canPostReminderNotifications(context),
                    archivedCount = archivedEntries.size,
                    onDismiss = { settingsOpen = false },
                    onThemeMode = viewModel::selectThemeMode,
                    onDynamicColor = viewModel::setDynamicColor,
                    onPalette = { viewModel.selectPalette(it.name) },
                    onReminderSettings = {
                        settingsOpen = false
                        if (canPostReminderNotifications(context)) {
                            openNotificationSettings(context)
                        } else {
                            guideNotificationAccess()
                        }
                    },
                    onArchived = {
                        settingsOpen = false
                        archiveOpen = true
                    },
                    onDataManagement = {
                        settingsOpen = false
                        dataManagementOpen = true
                    },
                )
            }
            if (archiveOpen) {
                ArchivedEntriesSheet(
                    entries = archivedEntries,
                    onDismiss = { archiveOpen = false },
                    onRestore = { viewModel.restoreArchived(it.id) },
                    onDelete = { deletingId = it.id },
                )
            }
            if (dataManagementOpen) {
                DataManagementDialog(
                    state = dataTransferState,
                    onDismiss = {
                        dataManagementOpen = false
                        replaceConfirmationOpen = false
                        viewModel.clearDataTransferState()
                    },
                    onExportJson = {
                        jsonExport.launch("athena-backup-${today.format(DateTimeFormatter.ISO_DATE)}.json")
                    },
                    onImportJson = { jsonImport.launch(arrayOf("application/json", "text/json", "text/plain")) },
                    onExportIcs = {
                        icsExport.launch("athena-dates-${today.format(DateTimeFormatter.ISO_DATE)}.ics")
                    },
                    onImportIcs = { icsImport.launch(arrayOf("text/calendar", "application/ics", "text/plain")) },
                    onImportCalendar = {
                        dataManagementOpen = false
                        guideCalendarReadAccess()
                    },
                    onExportCalendar = {
                        dataManagementOpen = false
                        guideCalendarExportAccess()
                    },
                    onImportContacts = {
                        dataManagementOpen = false
                        guideContactsAccess()
                    },
                    onMerge = { viewModel.applyPendingImport(ImportMode.Merge) },
                    onRequestReplace = { replaceConfirmationOpen = true },
                    onReset = viewModel::clearDataTransferState,
                )
            }
            if (replaceConfirmationOpen) {
                ReplaceRestoreConfirmation(
                    onDismiss = { replaceConfirmationOpen = false },
                    onConfirm = {
                        replaceConfirmationOpen = false
                        viewModel.applyPendingImport(ImportMode.Replace)
                    },
                )
            }
            if (calendarPermissionExplanation) {
                AlertDialog(
                    onDismissRequest = { calendarPermissionExplanation = false },
                    title = { Text("允许读取系统日历？") },
                    text = {
                        Text(
                            "Athena 只在这次导入中读取你接下来明确勾选的日历和事件，用于生成预览；" +
                                "不会写入系统日历，也不会后台同步。",
                        )
                    },
                    confirmButton = {
                        Button({
                            calendarPermissionExplanation = false
                            calendarImportOpen = true
                            calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                        }) { Text("继续选择") }
                    },
                    dismissButton = { OutlinedButton({ calendarPermissionExplanation = false }) { Text("取消") } },
                )
            }
            if (calendarPermissionDenied) {
                PermissionDeniedDialog(
                    title = "未获得日历读取权限",
                    message = "没有此权限时 Athena 不会读取任何系统日历数据。你仍可使用 JSON 或 ICS 文件导入，也可稍后在系统设置授权后重试。",
                    onDismiss = { calendarPermissionDenied = false; calendarImportOpen = false },
                    onSettings = { calendarPermissionDenied = false; calendarImportOpen = false; openApplicationSettings(context) },
                )
            }
            if (calendarImportOpen && !calendarPermissionDenied) {
                CalendarImportDialog(
                    state = calendarImportState,
                    onDismiss = {
                        calendarImportOpen = false
                        viewModel.clearCalendarImport()
                    },
                    onLoadEvents = viewModel::loadCalendarEvents,
                    onPreview = viewModel::previewCalendarImport,
                    onApply = viewModel::applyCalendarImport,
                )
            }
            if (calendarExportPermissionExplanation) {
                AlertDialog(
                    onDismissRequest = { calendarExportPermissionExplanation = false },
                    title = { Text("允许访问系统日历？") },
                    text = { Text("Athena 需要读取权限来生成去重预览，需要写入权限把你明确勾选的日期保存到所选日历。不会后台同步。") },
                    confirmButton = {
                        Button({
                            calendarExportPermissionExplanation = false
                            calendarExportOpen = true
                            calendarExportPermission.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                        }) { Text("继续选择") }
                    },
                    dismissButton = { OutlinedButton({ calendarExportPermissionExplanation = false }) { Text("取消") } },
                )
            }
            if (calendarExportPermissionDenied) {
                PermissionDeniedDialog(
                    title = "未获得日历读写权限",
                    message = "没有这些权限时 Athena 不会读取或写入系统日历；其他功能仍可使用。可稍后在系统设置授权后重试。",
                    onDismiss = { calendarExportPermissionDenied = false; calendarExportOpen = false },
                    onSettings = { calendarExportPermissionDenied = false; openApplicationSettings(context) },
                )
            }
            if (calendarExportOpen && !calendarExportPermissionDenied) {
                CalendarExportDialog(
                    state = calendarExportState,
                    entries = entries,
                    onDismiss = { calendarExportOpen = false; viewModel.clearCalendarExport() },
                    onPreview = viewModel::previewCalendarExport,
                    onApply = viewModel::applyCalendarExport,
                )
            }
            if (contactsPermissionExplanation) {
                AlertDialog(
                    onDismissRequest = { contactsPermissionExplanation = false },
                    title = { Text("允许读取联系人生日？") },
                    text = { Text("Athena 只读取联系人姓名、生日和用于稳定去重的 lookup key；不读取电话、消息或其他资料，也不会后台同步。") },
                    confirmButton = {
                        Button({
                            contactsPermissionExplanation = false
                            contactsImportOpen = true
                            contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                        }) { Text("继续选择") }
                    },
                    dismissButton = { OutlinedButton({ contactsPermissionExplanation = false }) { Text("取消") } },
                )
            }
            if (contactsPermissionDenied) {
                PermissionDeniedDialog(
                    title = "未获得联系人读取权限",
                    message = "Athena 未读取任何联系人数据。其他功能仍可使用；可稍后在系统设置授权后重试。",
                    onDismiss = { contactsPermissionDenied = false; contactsImportOpen = false },
                    onSettings = { contactsPermissionDenied = false; openApplicationSettings(context) },
                )
            }
            if (contactsImportOpen && !contactsPermissionDenied) {
                ContactsImportDialog(
                    state = contactsImportState,
                    onDismiss = { contactsImportOpen = false; viewModel.clearContactsImport() },
                    onPreview = viewModel::previewContactsImport,
                    onApply = viewModel::applyContactsImport,
                )
            }
        }
    }
}

private fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    )
}

private fun openApplicationSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
}

@Composable
private fun PermissionDeniedDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { Button(onSettings) { Text("前往系统设置") } },
        dismissButton = { OutlinedButton(onDismiss) { Text("暂不授权") } },
    )
}

private const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
