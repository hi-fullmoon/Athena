package com.athena.dates

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class AthenaThemeTest {
    @Test
    fun `custom light and dark schemes keep key text contrast accessible`() {
        AthenaPalette.entries.forEach { palette ->
            val light = athenaLightColorScheme(palette)
            val dark = athenaDarkColorScheme(palette)

            assertContrast(light.onSurface, light.surface, 4.5f, "${palette.name} light surface")
            assertContrast(light.onPrimary, light.primary, 4.5f, "${palette.name} light primary")
            assertContrast(light.onPrimaryContainer, light.primaryContainer, 4.5f, "${palette.name} light primary container")
            assertContrast(light.outline, light.surfaceContainerLowest, 3f, "${palette.name} light control outline")
            assertContrast(dark.onSurface, dark.surface, 4.5f, "${palette.name} dark surface")
            assertContrast(dark.onSurfaceVariant, dark.surfaceVariant, 4.5f, "${palette.name} dark variant")
            assertContrast(dark.onPrimary, dark.primary, 4.5f, "${palette.name} dark primary")
            assertContrast(dark.onPrimaryContainer, dark.primaryContainer, 4.5f, "${palette.name} dark primary container")
            assertContrast(dark.outline, dark.surfaceContainerLowest, 3f, "${palette.name} dark control outline")
        }
    }

    private fun assertContrast(foreground: Color, background: Color, minimum: Float, label: String) {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        val ratio = (lighter + .05f) / (darker + .05f)
        assertTrue("$label contrast was $ratio", ratio >= minimum)
    }
}
