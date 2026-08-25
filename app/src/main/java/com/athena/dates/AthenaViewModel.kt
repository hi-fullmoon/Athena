package com.athena.dates

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface DataTransferState {
    data object Idle : DataTransferState
    data class Working(val message: String) : DataTransferState
    data class Preview(val preview: ImportPreview) : DataTransferState
    data class Finished(
        val message: String,
        val counts: ImportCounts? = null,
        val issues: List<ImportIssue> = emptyList(),
    ) : DataTransferState
    data class Failed(val message: String) : DataTransferState
}

sealed interface CalendarImportState {
    data object Idle : CalendarImportState
    data class Loading(val message: String) : CalendarImportState
    data class Calendars(val calendars: List<DeviceCalendar>) : CalendarImportState
    data class Events(val load: CalendarCandidateLoad) : CalendarImportState
    data class Preview(val preview: ImportPreview) : CalendarImportState
    data class Finished(
        val message: String,
        val counts: ImportCounts,
        val issues: List<ImportIssue>,
    ) : CalendarImportState
    data class Failed(val message: String) : CalendarImportState
}

sealed interface CalendarExportState {
    data object Idle : CalendarExportState
    data class Loading(val message: String) : CalendarExportState
    data class Calendars(val calendars: List<WritableDeviceCalendar>) : CalendarExportState
    data class Preview(val preview: CalendarExportPreview) : CalendarExportState
    data class Finished(val completion: CalendarExportCompletion) : CalendarExportState
    data class Failed(val message: String) : CalendarExportState
}

sealed interface ContactsImportState {
    data object Idle : ContactsImportState
    data class Loading(val message: String) : ContactsImportState
    data class Candidates(val load: ContactBirthdayLoad) : ContactsImportState
    data class Preview(val preview: ImportPreview) : ContactsImportState
    data class Finished(val message: String, val counts: ImportCounts, val issues: List<ImportIssue>) : ContactsImportState
    data class Failed(val message: String) : ContactsImportState
}

class AthenaViewModel internal constructor(
    private val repository: DateEntryRepository,
    private val settingsRepository: SettingsRepository,
    private val reminderScheduler: ReminderScheduler,
    private val dataTransferService: DataTransferService,
) : ViewModel() {
    val entries: StateFlow<List<DateEntry>> = repository.entries.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    val tags: StateFlow<List<DateTag>> = repository.tags.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    val appearance = MutableStateFlow(settingsRepository.loadAppearance())
    val entryQuery = MutableStateFlow(EntryQuery())
    val dataTransferState = MutableStateFlow<DataTransferState>(DataTransferState.Idle)
    val calendarImportState = MutableStateFlow<CalendarImportState>(CalendarImportState.Idle)
    val calendarExportState = MutableStateFlow<CalendarExportState>(CalendarExportState.Idle)
    val contactsImportState = MutableStateFlow<ContactsImportState>(ContactsImportState.Idle)
    private var pendingImport: PreparedImport? = null
    private var loadedCalendarCandidates: CalendarCandidateLoad? = null
    private var pendingCalendarImport: PreparedImport? = null
    private var pendingCalendarExport: CalendarExportPreview? = null
    private var loadedContactBirthdays: ContactBirthdayLoad? = null
    private var pendingContactImport: PreparedImport? = null

    init {
        rescheduleReminders()
        archiveExpired(LocalDate.now())
    }

    fun save(entry: DateEntry) = viewModelScope.launch {
        val eligibleForArchive = entry.kind == DateKind.Countdown &&
            entry.recurrence.frequency == RepeatFrequency.None &&
            entry.date.isBefore(LocalDate.now())
        val normalized = if (eligibleForArchive) entry else entry.copy(
            isArchived = false,
            keepVisibleWhenExpired = false,
        )
        saveEntryAndUpdateReminder(repository, reminderScheduler, normalized)
        repository.archiveExpired(LocalDate.now())
    }

    fun delete(id: String) = viewModelScope.launch {
        deleteEntryAndCancelReminder(repository, reminderScheduler, id)
    }

    fun rescheduleReminders() = viewModelScope.launch {
        rebuildReminders(repository, reminderScheduler)
    }

    fun selectPalette(name: String) {
        updateAppearance(appearance.value.copy(paletteName = name))
    }

    fun selectThemeMode(mode: ThemeMode) {
        updateAppearance(appearance.value.copy(themeMode = mode))
    }

    fun setDynamicColor(enabled: Boolean) {
        updateAppearance(appearance.value.copy(dynamicColor = enabled))
    }

    fun updateEntryQuery(transform: (EntryQuery) -> EntryQuery) {
        entryQuery.value = transform(entryQuery.value)
    }

    fun resetEntryQuery() {
        entryQuery.value = EntryQuery()
    }

    fun archiveExpired(reference: LocalDate) = viewModelScope.launch {
        repository.archiveExpired(reference)
    }

    fun restoreArchived(id: String) = viewModelScope.launch {
        repository.restoreArchived(id)
    }

    private fun updateAppearance(updated: AppearanceSettings) {
        settingsRepository.saveAppearance(updated)
        appearance.value = updated
    }

    fun exportJson(uri: Uri) = runTransfer("正在创建完整备份…") {
        dataTransferService.exportJson(uri)
        DataTransferState.Finished("JSON 完整备份已保存")
    }

    fun exportIcs(uri: Uri) = runTransfer("正在导出日历…") {
        dataTransferService.exportIcs(uri)
        DataTransferState.Finished("ICS 日历已保存")
    }

    fun previewImport(uri: Uri, format: TransferFormat) = runTransfer("正在严格检查文件…") {
        dataTransferService.preview(uri, format).also { pendingImport = it }
            .let { DataTransferState.Preview(it.preview) }
    }

    fun applyPendingImport(mode: ImportMode) = viewModelScope.launch {
        val prepared = pendingImport ?: return@launch
        dataTransferState.value = DataTransferState.Working(
            if (mode == ImportMode.Replace) "正在全量替换并重建提醒…" else "正在安全合并并重建提醒…",
        )
        runCatching { dataTransferService.apply(prepared, mode) }
            .onSuccess { completion ->
                pendingImport = null
                completion.restoredAppearance?.let { appearance.value = it }
                dataTransferState.value = DataTransferState.Finished(
                    message = if (mode == ImportMode.Replace) {
                        "全量替换完成，共 ${completion.finalEntryCount} 条记录"
                    } else {
                        "安全合并完成，共 ${completion.finalEntryCount} 条记录"
                    },
                    counts = completion.counts,
                    issues = prepared.preview.issues + completion.issues,
                )
            }
            .onFailure { dataTransferState.value = DataTransferState.Failed(it.userMessage()) }
    }

    fun clearDataTransferState() {
        pendingImport = null
        dataTransferState.value = DataTransferState.Idle
    }

    fun startCalendarImport() = viewModelScope.launch {
        calendarImportState.value = CalendarImportState.Loading("正在读取可见的系统日历…")
        calendarImportState.value = runCatching { dataTransferService.loadDeviceCalendars() }
            .fold(
                onSuccess = { CalendarImportState.Calendars(it) },
                onFailure = { CalendarImportState.Failed(it.userMessage()) },
            )
    }

    fun loadCalendarEvents(calendarIds: Set<Long>) = viewModelScope.launch {
        calendarImportState.value = CalendarImportState.Loading("正在读取所选日历的事件…")
        calendarImportState.value = runCatching { dataTransferService.loadCalendarCandidates(calendarIds) }
            .fold(
                onSuccess = { load ->
                    loadedCalendarCandidates = load
                    CalendarImportState.Events(load)
                },
                onFailure = { CalendarImportState.Failed(it.userMessage()) },
            )
    }

    fun previewCalendarImport(sourceIds: Set<String>) = viewModelScope.launch {
        val load = loadedCalendarCandidates ?: return@launch
        calendarImportState.value = CalendarImportState.Loading("正在识别重复并生成预览…")
        calendarImportState.value = runCatching {
            dataTransferService.prepareCalendarImport(
                selected = load.candidates.filter { it.sourceId in sourceIds },
                load = load,
            )
        }.fold(
            onSuccess = { prepared ->
                pendingCalendarImport = prepared
                CalendarImportState.Preview(prepared.preview)
            },
            onFailure = { CalendarImportState.Failed(it.userMessage()) },
        )
    }

    fun applyCalendarImport() = viewModelScope.launch {
        val prepared = pendingCalendarImport ?: return@launch
        calendarImportState.value = CalendarImportState.Loading("正在事务化导入并重建提醒…")
        calendarImportState.value = runCatching { dataTransferService.apply(prepared, ImportMode.Merge) }
            .fold(
                onSuccess = { completion ->
                    pendingCalendarImport = null
                    CalendarImportState.Finished(
                        message = "系统日历导入完成，共 ${completion.finalEntryCount} 条记录",
                        counts = completion.counts,
                        issues = prepared.preview.issues + completion.issues,
                    )
                },
                onFailure = { CalendarImportState.Failed(it.userMessage()) },
            )
    }

    fun clearCalendarImport() {
        loadedCalendarCandidates = null
        pendingCalendarImport = null
        calendarImportState.value = CalendarImportState.Idle
    }

    fun startCalendarExport() = viewModelScope.launch {
        calendarExportState.value = CalendarExportState.Loading("正在读取可写入的系统日历…")
        calendarExportState.value = runCatching { dataTransferService.loadWritableCalendars() }.fold(
            onSuccess = { CalendarExportState.Calendars(it) },
            onFailure = { CalendarExportState.Failed(it.userMessage()) },
        )
    }

    fun previewCalendarExport(target: WritableDeviceCalendar, entryIds: Set<String>) = viewModelScope.launch {
        calendarExportState.value = CalendarExportState.Loading("正在识别已导出条目并生成预览…")
        calendarExportState.value = runCatching { dataTransferService.previewCalendarExport(target, entryIds) }.fold(
            onSuccess = { preview ->
                pendingCalendarExport = preview
                CalendarExportState.Preview(preview)
            },
            onFailure = { CalendarExportState.Failed(it.userMessage()) },
        )
    }

    fun applyCalendarExport() = viewModelScope.launch {
        val preview = pendingCalendarExport ?: return@launch
        calendarExportState.value = CalendarExportState.Loading("正在写入你选择的系统日历…")
        calendarExportState.value = runCatching { dataTransferService.applyCalendarExport(preview) }.fold(
            onSuccess = { completion ->
                pendingCalendarExport = null
                CalendarExportState.Finished(completion)
            },
            onFailure = { CalendarExportState.Failed(it.userMessage()) },
        )
    }

    fun clearCalendarExport() {
        pendingCalendarExport = null
        calendarExportState.value = CalendarExportState.Idle
    }

    fun startContactsImport() = viewModelScope.launch {
        contactsImportState.value = ContactsImportState.Loading("正在读取联系人姓名与生日…")
        contactsImportState.value = runCatching { dataTransferService.loadContactBirthdays() }.fold(
            onSuccess = { load ->
                loadedContactBirthdays = load
                ContactsImportState.Candidates(load)
            },
            onFailure = { ContactsImportState.Failed(it.userMessage()) },
        )
    }

    fun previewContactsImport(sourceIds: Set<String>) = viewModelScope.launch {
        val load = loadedContactBirthdays ?: return@launch
        contactsImportState.value = ContactsImportState.Loading("正在识别重复并生成预览…")
        contactsImportState.value = runCatching {
            dataTransferService.prepareContactImport(load.candidates.filter { it.sourceId in sourceIds }, load)
        }.fold(
            onSuccess = { prepared ->
                pendingContactImport = prepared
                ContactsImportState.Preview(prepared.preview)
            },
            onFailure = { ContactsImportState.Failed(it.userMessage()) },
        )
    }

    fun applyContactsImport() = viewModelScope.launch {
        val prepared = pendingContactImport ?: return@launch
        contactsImportState.value = ContactsImportState.Loading("正在事务化导入并重建提醒…")
        contactsImportState.value = runCatching { dataTransferService.apply(prepared, ImportMode.Merge) }.fold(
            onSuccess = { completion ->
                pendingContactImport = null
                ContactsImportState.Finished(
                    "联系人生日导入完成，共 ${completion.finalEntryCount} 条记录",
                    completion.counts,
                    prepared.preview.issues + completion.issues,
                )
            },
            onFailure = { ContactsImportState.Failed(it.userMessage()) },
        )
    }

    fun clearContactsImport() {
        loadedContactBirthdays = null
        pendingContactImport = null
        contactsImportState.value = ContactsImportState.Idle
    }

    private fun runTransfer(message: String, block: suspend () -> DataTransferState) = viewModelScope.launch {
        pendingImport = null
        dataTransferState.value = DataTransferState.Working(message)
        dataTransferState.value = runCatching { block() }
            .getOrElse { DataTransferState.Failed(it.userMessage()) }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AthenaViewModel(
                repository = RoomDateEntryRepository(context.applicationContext),
                settingsRepository = SettingsRepository(context.applicationContext),
                reminderScheduler = AndroidReminderScheduler(context.applicationContext),
                dataTransferService = DataTransferService(context.applicationContext),
            ) as T
        }
    }
}

private fun Throwable.userMessage(): String = message?.takeIf(String::isNotBlank) ?: "操作失败，请检查文件后重试"
