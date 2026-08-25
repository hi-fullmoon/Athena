package com.athena.dates

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareCardFileProviderTest {
    @Test
    fun shareCardRendersLongTextAndFileProviderExposesOnlyConfiguredCachePath() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entry = DateEntry(
            id = "share",
            title = "这是一条很长很长、需要自动换行并保持可读性的日期标题",
            note = "长备注 ".repeat(120),
            date = LocalDate.of(2030, 2, 28),
            kind = DateKind.Anniversary,
        )
        val bitmap = renderShareCard(entry, entry.date, LocalDate.of(2030, 2, 1), includeNote = true, dark = true)
        assertEquals(1080, bitmap.width)
        assertEquals(1350, bitmap.height)

        val directory = File(context.cacheDir, "share_cards").also(File::mkdirs)
        val file = File(directory, "instrumentation-share.png")
        FileOutputStream(file).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)

        assertEquals("content", uri.scheme)
        assertEquals("image/png", context.contentResolver.getType(uri))
        context.contentResolver.openInputStream(uri).use { stream ->
            val decoded = BitmapFactory.decodeStream(stream)
            assertNotNull(decoded)
            assertTrue(decoded.width > 0)
        }
        file.delete()
    }
}
