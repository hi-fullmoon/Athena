package com.athena.dates

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

internal class ShareCardManager(private val context: Context) {
    suspend fun share(entry: DateEntry, occurrence: LocalDate, reference: LocalDate, includeNote: Boolean, dark: Boolean) {
        val uri = withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, SHARE_DIRECTORY).also(File::mkdirs)
            cleanup(directory)
            val output = File(directory, "athena-${UUID.randomUUID()}.png")
            FileOutputStream(output).use { stream ->
                renderShareCard(entry, occurrence, reference, includeNote, dark)
                    .compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", output)
        }
        withContext(Dispatchers.Main) {
            val send = Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, entry.title)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, "分享日期卡片"))
        }
    }

    private fun cleanup(directory: File) {
        val files = directory.listFiles()?.sortedByDescending(File::lastModified).orEmpty()
        val cutoff = System.currentTimeMillis() - SHARE_CACHE_MAX_AGE_MILLIS
        files.forEachIndexed { index, file ->
            if (index >= MAX_SHARE_CACHE_FILES || file.lastModified() < cutoff) file.delete()
        }
    }
}

internal fun renderShareCard(
    entry: DateEntry,
    occurrence: LocalDate,
    reference: LocalDate,
    includeNote: Boolean,
    dark: Boolean,
): Bitmap {
    val bitmap = createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val background = if (dark) Color.rgb(28, 27, 32) else Color.rgb(249, 247, 255)
    val surface = if (dark) Color.rgb(48, 46, 55) else Color.WHITE
    val primary = if (dark) Color.rgb(207, 189, 255) else Color.rgb(92, 70, 176)
    val onSurface = if (dark) Color.rgb(242, 238, 247) else Color.rgb(34, 31, 40)
    val secondary = if (dark) Color.rgb(202, 196, 208) else Color.rgb(92, 87, 99)
    canvas.drawColor(background)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = surface
    canvas.drawRoundRect(64f, 64f, CARD_WIDTH - 64f, CARD_HEIGHT - 64f, 48f, 48f, paint)

    var y = 155f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textSize = 42f
    paint.color = primary
    canvas.drawText("ATHENA · 重要日期", 120f, y, paint)
    y += 112f

    paint.textSize = 74f
    paint.color = onSurface
    drawWrappedText(canvas, entry.title, paint, 120f, y, CARD_WIDTH - 240f, 2, 88f).also { y = it + 38f }

    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    paint.textSize = 48f
    paint.color = secondary
    val dateText = occurrence.format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日", Locale.CHINA))
    canvas.drawText(dateText, 120f, y, paint)
    y += 108f

    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textSize = 104f
    paint.color = primary
    canvas.drawText(relativeDayLabel(occurrence, reference), 120f, y, paint)
    y += 105f

    if (includeNote && entry.note.isNotBlank()) {
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 40f
        paint.color = secondary
        drawWrappedText(canvas, entry.note.trim(), paint, 120f, y, CARD_WIDTH - 240f, 5, 58f)
    }

    paint.textSize = 32f
    paint.color = secondary
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    canvas.drawText("由 Athena 在设备本地生成", 120f, CARD_HEIGHT - 125f, paint)
    return bitmap
}

private fun drawWrappedText(
    canvas: Canvas,
    source: String,
    paint: Paint,
    x: Float,
    startY: Float,
    maxWidth: Float,
    maxLines: Int,
    lineHeight: Float,
): Float {
    var remaining = source.replace(Regex("\\s+"), " ").trim()
    var y = startY
    repeat(maxLines) { index ->
        if (remaining.isEmpty()) return y
        var count = paint.breakText(remaining, true, maxWidth, null).coerceAtLeast(1)
        if (count < remaining.length) {
            remaining.lastIndexOf(' ', startIndex = count - 1).takeIf { it > 0 }?.let { count = it }
        }
        var line = remaining.take(count).trim()
        remaining = remaining.drop(count).trimStart()
        if (index == maxLines - 1 && remaining.isNotEmpty()) {
            while (line.isNotEmpty() && paint.measureText("$line…") > maxWidth) line = line.dropLast(1)
            line += "…"
        }
        canvas.drawText(line, x, y, paint)
        y += lineHeight
    }
    return y
}

private const val CARD_WIDTH = 1080
private const val CARD_HEIGHT = 1350
private const val SHARE_DIRECTORY = "share_cards"
private const val MAX_SHARE_CACHE_FILES = 20
private const val SHARE_CACHE_MAX_AGE_MILLIS = 24L * 60L * 60L * 1_000L
