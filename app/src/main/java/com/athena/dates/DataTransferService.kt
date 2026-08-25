package com.athena.dates

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Clock
import java.time.Instant

internal data class ImportCompletion(
    val mode: ImportMode,
    val counts: ImportCounts,
    val finalEntryCount: Int,
    val restoredAppearance: AppearanceSettings?,
    val restoredSettings: Boolean,
    val issues: List<ImportIssue>,
)

internal interface DataTransferStore {
    suspend fun snapshot(): DateDataSnapshot
    suspend fun apply(data: DateDataSnapshot, replace: Boolean)
}

internal class RoomDataTransferStore(context: Context) : DataTransferStore {
    private val repository = RoomDateEntryRepository(context)

    override suspend fun snapshot(): DateDataSnapshot = repository.snapshotData()

    override suspend fun apply(data: DateDataSnapshot, replace: Boolean) {
        repository.applyDataImport(data, replace, archiveReference = java.time.LocalDate.now())
    }
}

internal class DataTransferService(
    context: Context,
    private val store: DataTransferStore = RoomDataTransferStore(context),
    private val settingsRepository: SettingsRepository = SettingsRepository(context),
    private val reminderScheduler: ReminderScheduler = AndroidReminderScheduler(context),
    private val contentResolver: ContentResolver = context.contentResolver,
    private val clock: Clock = Clock.systemUTC(),
    private val calendarReader: CalendarProviderReader = CalendarProviderReader(context.contentResolver),
    private val contactsReader: ContactsBirthdayReader = ContactsBirthdayReader(context.contentResolver),
    private val calendarWriter: CalendarProviderWriter = CalendarProviderWriter(
        context.contentResolver,
        context.packageName,
    ),
) {
    suspend fun exportJson(uri: Uri) = withContext(Dispatchers.IO) {
        val appearance = settingsRepository.loadAppearance()
        val data = loadData()
        val snapshot = BackupSnapshot(
            entries = data.entries,
            paletteName = appearance.paletteName,
            exportedAt = Instant.now(clock),
            appVersion = BuildConfig.VERSION_NAME,
            themeMode = appearance.themeMode,
            dynamicColor = appearance.dynamicColor,
            tags = data.tags,
        )
        writeDocument(uri, JsonBackupCodec.encode(snapshot))
    }

    suspend fun exportIcs(uri: Uri) = withContext(Dispatchers.IO) {
        writeDocument(uri, IcsCodec.encode(loadData().entries, Instant.now(clock)))
    }

    suspend fun preview(uri: Uri, format: TransferFormat): PreparedImport = withContext(Dispatchers.IO) {
        val raw = readDocument(uri)
        val parsed = when (format) {
            TransferFormat.Json -> JsonBackupCodec.parse(raw)
            TransferFormat.Ics -> IcsCodec.parse(raw)
            TransferFormat.Calendar, TransferFormat.Contacts -> error("系统数据导入不读取文档")
        }
        ImportPlanner.prepare(parsed, loadData())
    }

    suspend fun loadDeviceCalendars(): List<DeviceCalendar> = calendarReader.calendars()

    suspend fun loadCalendarCandidates(calendarIds: Set<Long>): CalendarCandidateLoad =
        calendarReader.candidates(calendarIds)

    suspend fun prepareCalendarImport(
        selected: List<CalendarImportCandidate>,
        load: CalendarCandidateLoad,
    ): PreparedImport = withContext(Dispatchers.IO) {
        require(selected.isNotEmpty()) { "请至少选择一个事件" }
        ImportPlanner.prepare(prepareCalendarTransfer(selected, load), loadData())
    }

    suspend fun loadContactBirthdays(): ContactBirthdayLoad = contactsReader.candidates()

    suspend fun prepareContactImport(
        selected: List<ContactBirthdayCandidate>,
        load: ContactBirthdayLoad,
    ): PreparedImport = withContext(Dispatchers.IO) {
        require(selected.isNotEmpty()) { "请至少选择一个联系人生日" }
        ImportPlanner.prepare(
            ParsedTransfer(
                format = TransferFormat.Contacts,
                entries = selected.map(ContactBirthdayCandidate::entry),
                issues = load.issues,
                skipped = load.skipped,
            ),
            loadData(),
        )
    }

    suspend fun loadWritableCalendars(): List<WritableDeviceCalendar> = calendarWriter.writableCalendars()

    suspend fun previewCalendarExport(target: WritableDeviceCalendar, entryIds: Set<String>): CalendarExportPreview =
        withContext(Dispatchers.IO) {
            calendarWriter.preview(target, loadData().entries.filter { it.id in entryIds && !it.isArchived })
        }

    suspend fun applyCalendarExport(preview: CalendarExportPreview): CalendarExportCompletion =
        calendarWriter.apply(preview)

    suspend fun apply(prepared: PreparedImport, mode: ImportMode): ImportCompletion = withContext(Dispatchers.IO) {
        require(prepared.preview.canApply) { "导入预览包含错误，不能恢复" }
        require(mode != ImportMode.Replace || prepared.preview.canReplace) { "该文件不支持全量替换" }

        val before = loadData()
        val dataToWrite = when (mode) {
            ImportMode.Merge -> DateDataSnapshot(prepared.mergeEntries, prepared.mergeTags)
            ImportMode.Replace -> DateDataSnapshot(prepared.replacementEntries, prepared.replacementTags)
        }
        store.apply(dataToWrite, replace = mode == ImportMode.Replace)
        val currentAppearance = settingsRepository.loadAppearance()
        val restoredAppearance = currentAppearance.copy(
            paletteName = prepared.paletteName,
            themeMode = prepared.themeMode ?: currentAppearance.themeMode,
            dynamicColor = prepared.dynamicColor ?: currentAppearance.dynamicColor,
        )
        val settingsRestored = !prepared.preview.restoresSettings ||
            settingsRepository.restoreAppearance(restoredAppearance)

        val after = loadData()
        val afterIds = after.entries.mapTo(mutableSetOf(), DateEntry::id)
        var reminderFailures = 0
        before.entries.asSequence().map(DateEntry::id).filterNot(afterIds::contains).forEach { id ->
            runCatching { reminderScheduler.cancel(id) }.onFailure { reminderFailures++ }
        }
        after.entries.forEach { entry ->
            runCatching { reminderScheduler.schedule(entry) }.onFailure { reminderFailures++ }
        }
        val completionIssues = buildList {
            if (!settingsRestored) {
                add(ImportIssue(IssueSeverity.Warning, "日期已恢复，但应用主题设置写入失败"))
            }
            if (reminderFailures > 0) {
                add(
                    ImportIssue(
                        IssueSeverity.Warning,
                        "数据已恢复，但有 $reminderFailures 个提醒未能立即重建；重新打开 Athena 会再次尝试",
                    ),
                )
            }
        }
        ImportCompletion(
            mode = mode,
            counts = prepared.preview.counts,
            finalEntryCount = after.entries.size,
            restoredAppearance = restoredAppearance.takeIf { prepared.preview.restoresSettings && settingsRestored },
            restoredSettings = prepared.preview.restoresSettings && settingsRestored,
            issues = completionIssues,
        )
    }

    private suspend fun loadData(): DateDataSnapshot = store.snapshot()

    private fun writeDocument(uri: Uri, text: String) {
        val output = contentResolver.openOutputStream(uri, "wt") ?: throw IOException("无法打开所选文件")
        output.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
    }

    private fun readDocument(uri: Uri): String {
        val input = contentResolver.openInputStream(uri) ?: throw IOException("无法读取所选文件")
        return input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_IMPORT_BYTES) { "文件超过 10 MiB 安全限制" }
                output.write(buffer, 0, count)
            }
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(output.toByteArray()))
                .toString()
        }
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 10 * 1024 * 1024
    }
}
