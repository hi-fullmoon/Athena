package com.athena.dates

import android.content.ContentResolver
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.UUID

data class ContactBirthdayCandidate(
    val sourceId: String,
    val displayName: String,
    val birthdayLabel: String,
    val entry: DateEntry,
)

data class ContactBirthdayLoad(
    val candidates: List<ContactBirthdayCandidate>,
    val issues: List<ImportIssue>,
    val skipped: Int,
)

internal class ContactsBirthdayReader(private val resolver: ContentResolver) {
    suspend fun candidates(): ContactBirthdayLoad = withContext(Dispatchers.IO) {
        val loaded = mutableListOf<ContactBirthdayCandidate>()
        val issues = mutableListOf<ImportIssue>()
        var skipped = 0
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Event.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Event.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Event.START_DATE,
        )
        val selection =
            "${ContactsContract.Data.MIMETYPE}=? AND ${ContactsContract.CommonDataKinds.Event.TYPE}=?"
        val args = arrayOf(
            ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString(),
        )
        resolver.query(
            ContactsContract.Data.CONTENT_URI,
            projection,
            selection,
            args,
            "${ContactsContract.CommonDataKinds.Event.DISPLAY_NAME_PRIMARY} COLLATE NOCASE",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (loaded.size >= MAX_CONTACT_BIRTHDAYS) {
                    issues += ImportIssue(IssueSeverity.Warning, "联系人生日超过 $MAX_CONTACT_BIRTHDAYS 条，仅加载前 $MAX_CONTACT_BIRTHDAYS 条")
                    skipped++
                    break
                }
                runCatching {
                    val lookupKey = cursor.getString(0)?.takeIf(String::isNotBlank) ?: error("缺少稳定 lookup key")
                    val name = cursor.getString(1)?.trim()?.takeIf(String::isNotBlank) ?: error("姓名为空")
                    val rawBirthday = cursor.getString(2)?.trim()?.takeIf(String::isNotBlank) ?: error("生日为空")
                    toCandidate(lookupKey, name, rawBirthday)
                }.onSuccess(loaded::add).onFailure { error ->
                    skipped++
                    issues += ImportIssue(IssueSeverity.Warning, "一条联系人生日已跳过：${error.message ?: "日期无法识别"}")
                }
            }
        }
        ContactBirthdayLoad(
            candidates = loaded.distinctBy(ContactBirthdayCandidate::sourceId),
            issues = issues,
            skipped = skipped + (loaded.size - loaded.distinctBy(ContactBirthdayCandidate::sourceId).size),
        )
    }
}

internal fun contactBirthdayCandidate(lookupKey: String, name: String, rawBirthday: String): ContactBirthdayCandidate =
    toCandidate(lookupKey, name, rawBirthday)

private fun toCandidate(lookupKey: String, name: String, rawBirthday: String): ContactBirthdayCandidate {
    val parsed = parseContactBirthday(rawBirthday)
    val sourceId = "$lookupKey:birthday"
    val entry = DateEntry(
        id = UUID.nameUUIDFromBytes("athena-contact:$sourceId".toByteArray(StandardCharsets.UTF_8)).toString(),
        title = "$name 的生日".take(200),
        note = "来源：系统联系人（仅姓名与生日）",
        date = parsed.date,
        kind = DateKind.Anniversary,
        recurrence = RecurrenceRule(RepeatFrequency.Yearly),
        externalIdentity = ExternalIdentity(EXTERNAL_SOURCE_CONTACT_BIRTHDAY, sourceId),
    )
    return ContactBirthdayCandidate(sourceId, name, parsed.label, entry)
}

private fun parseContactBirthday(raw: String): ParsedContactBirthday {
    val normalized = raw.trim()
    if (normalized.matches(Regex("--\\d{2}-\\d{2}"))) {
        val month = normalized.substring(2, 4).toInt()
        val day = normalized.substring(5, 7).toInt()
        val anchorYear = if (month == 2 && day == 29) 2000 else 2001
        return ParsedContactBirthday(LocalDate.of(anchorYear, month, day), normalized)
    }
    return try {
        val date = LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE)
        ParsedContactBirthday(date, date.toString())
    } catch (_: DateTimeParseException) {
        error("生日格式不受支持：$raw")
    }
}

private data class ParsedContactBirthday(val date: LocalDate, val label: String)

internal const val EXTERNAL_SOURCE_CONTACT_BIRTHDAY = "android_contact_birthday"
private const val MAX_CONTACT_BIRTHDAYS = 2_000
