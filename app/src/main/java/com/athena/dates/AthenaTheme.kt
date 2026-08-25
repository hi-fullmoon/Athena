package com.athena.dates

import android.os.Build
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AthenaPalette(val label: String, val primary: Color, val soft: Color, val darkPrimary: Color) {
    Violet("雾紫", Color(0xFF6757D9), Color(0xFFF0EEFF), Color(0xFFD0C4FF)),
    Sage("鼠尾草", Color(0xFF3D725F), Color(0xFFE9F5EE), Color(0xFFA9D9C3)),
    Amber("晨曦橙", Color(0xFFA9502E), Color(0xFFFFF0E9), Color(0xFFFFC0A6)),
    Ocean("深海蓝", Color(0xFF2F73A9), Color(0xFFEAF4FB), Color(0xFFA9D3F5)),
    Rose("桃粉", Color(0xFFA74466), Color(0xFFFCECF1), Color(0xFFFFB0C8)),
}

enum class ThemeMode(val label: String) {
    System("跟随系统"),
    Light("浅色"),
    Dark("深色"),
}

data class AppearanceSettings(
    val paletteName: String? = null,
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = false,
)

@Composable
fun AthenaTheme(
    palette: AthenaPalette,
    appearance: AppearanceSettings = AppearanceSettings(),
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dynamic = appearance.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colors = when {
        dynamic && darkTheme -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        darkTheme -> athenaDarkColorScheme(palette)
        else -> athenaLightColorScheme(palette)
    }
    MaterialTheme(
        colorScheme = colors,
        typography = athenaTypography,
        shapes = athenaShapes,
        content = content,
    )
}

private val athenaTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val athenaShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

internal fun athenaLightColorScheme(palette: AthenaPalette) = lightColorScheme(
    primary = palette.primary,
    onPrimary = Color.White,
    primaryContainer = palette.soft,
    onPrimaryContainer = Color(0xFF252536),
    secondary = palette.primary,
    background = Color(0xFFFAF8FF),
    surface = Color(0xFFFFFBFF),
    surfaceBright = Color(0xFFFFFBFF),
    surfaceDim = Color(0xFFE1DCE5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F3FA),
    surfaceContainer = Color(0xFFF3EEF6),
    surfaceContainerHigh = Color(0xFFEDE7F1),
    surfaceContainerHighest = Color(0xFFE7E1EB),
    surfaceVariant = Color(0xFFF0EBF4),
    onSurface = Color(0xFF211F24),
    onSurfaceVariant = Color(0xFF6F6973),
    outline = Color(0xFF8A838E),
    outlineVariant = Color(0xFFE0D9E4),
)

internal fun athenaDarkColorScheme(palette: AthenaPalette) = darkColorScheme(
    primary = palette.darkPrimary,
    onPrimary = Color(0xFF282035),
    primaryContainer = palette.primary.copy(alpha = .62f),
    onPrimaryContainer = Color(0xFFFFF8FF),
    secondary = palette.darkPrimary,
    background = Color(0xFF141218),
    surface = Color(0xFF1D1B20),
    surfaceBright = Color(0xFF3B383F),
    surfaceDim = Color(0xFF141218),
    surfaceContainerLowest = Color(0xFF0F0D12),
    surfaceContainerLow = Color(0xFF1A171E),
    surfaceContainer = Color(0xFF201D24),
    surfaceContainerHigh = Color(0xFF2A272F),
    surfaceContainerHighest = Color(0xFF353139),
    surfaceVariant = Color(0xFF302C34),
    onSurface = Color(0xFFF1ECF3),
    onSurfaceVariant = Color(0xFFCBC3CE),
    outline = Color(0xFF968E99),
    outlineVariant = Color(0xFF48434C),
)
