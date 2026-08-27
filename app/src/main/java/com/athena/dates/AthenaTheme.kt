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
    Violet("雾紫", Color(0xFF584A9E), Color(0xFFEDEAF7), Color(0xFFCBC3F0)),
    Sage("鼠尾草", Color(0xFF3E6855), Color(0xFFE7F0EA), Color(0xFFB5D7C4)),
    Amber("晨曦橙", Color(0xFF8C4A2C), Color(0xFFF7E9E1), Color(0xFFF0B89A)),
    Ocean("深海蓝", Color(0xFF356482), Color(0xFFE6EEF3), Color(0xFFACCEE4)),
    Rose("桃粉", Color(0xFF8C435C), Color(0xFFF5E7EC), Color(0xFFEFB3C5)),
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
        displaySmall = base.displaySmall.copy(fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
        bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
        labelLarge = base.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
    )
}

private val athenaShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

internal fun athenaLightColorScheme(palette: AthenaPalette) = lightColorScheme(
    primary = palette.primary,
    onPrimary = Color.White,
    primaryContainer = palette.soft,
    onPrimaryContainer = Color(0xFF24212C),
    secondary = palette.primary,
    tertiary = Color(0xFF765B3E),
    background = Color(0xFFF7F6F2),
    surface = Color(0xFFFCFBF8),
    surfaceBright = Color(0xFFFFFEFB),
    surfaceDim = Color(0xFFE4E2DC),
    surfaceContainerLowest = Color(0xFFFFFEFB),
    surfaceContainerLow = Color(0xFFFAF9F5),
    surfaceContainer = Color(0xFFF1F0EB),
    surfaceContainerHigh = Color(0xFFEAE8E2),
    surfaceContainerHighest = Color(0xFFE3E1DA),
    surfaceVariant = Color(0xFFF0EEE8),
    onSurface = Color(0xFF1D1D1B),
    onSurfaceVariant = Color(0xFF65635E),
    outline = Color(0xFF77746E),
    outlineVariant = Color(0xFFDEDBD3),
)

internal fun athenaDarkColorScheme(palette: AthenaPalette) = darkColorScheme(
    primary = palette.darkPrimary,
    onPrimary = Color(0xFF282035),
    primaryContainer = palette.primary,
    onPrimaryContainer = Color(0xFFFFFBFF),
    secondary = palette.darkPrimary,
    tertiary = Color(0xFFD9BA98),
    background = Color(0xFF111210),
    surface = Color(0xFF181A17),
    surfaceBright = Color(0xFF363834),
    surfaceDim = Color(0xFF111210),
    surfaceContainerLowest = Color(0xFF0D0E0C),
    surfaceContainerLow = Color(0xFF171916),
    surfaceContainer = Color(0xFF1D1F1C),
    surfaceContainerHigh = Color(0xFF252723),
    surfaceContainerHighest = Color(0xFF2D2F2B),
    surfaceVariant = Color(0xFF292B27),
    onSurface = Color(0xFFF0F0EA),
    onSurfaceVariant = Color(0xFFC5C4BC),
    outline = Color(0xFF918F87),
    outlineVariant = Color(0xFF41433E),
)
