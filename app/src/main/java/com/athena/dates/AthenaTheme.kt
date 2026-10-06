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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AthenaPalette(val label: String, val primary: Color, val soft: Color, val darkPrimary: Color) {
    // Keep the stored name compatible with existing preferences and backups.
    Violet("雾青", Color(0xFF355E5B), Color(0xFFDFECE8), Color(0xFFB4D3CC)),
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
    val customColors = if (darkTheme) athenaDarkColorScheme(palette) else athenaLightColorScheme(palette)
    val colors = if (dynamic) {
        val systemColors = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        val accents = listOf(systemColors.primary, systemColors.primaryContainer,
            systemColors.secondary, systemColors.secondaryContainer,
            systemColors.tertiary, systemColors.tertiaryContainer, systemColors.inversePrimary)
        if (accents.any { color ->
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(color.toArgb(), hsv)
                hsv[0] in 245f..330f && hsv[1] > 0.08f
            }) customColors else systemColors
    } else {
        customColors
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
        headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif, fontSize = 35.sp, lineHeight = 44.sp, fontWeight = FontWeight.Medium),
        headlineSmall = base.headlineSmall.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif, fontSize = 23.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium),
        titleMedium = base.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 24.sp),
        bodySmall = base.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp),
        labelLarge = base.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
    )
}

private val athenaShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = AthenaShapes.inner,
    medium = AthenaShapes.inner,
    large = AthenaShapes.card,
    extraLarge = AthenaShapes.dialog,
)

internal object AthenaShapes {
    val card = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
    val inner = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)
    val dialog = androidx.compose.foundation.shape.RoundedCornerShape(30.dp)
}

internal fun athenaLightColorScheme(palette: AthenaPalette) = lightColorScheme(
    primary = palette.primary,
    onPrimary = Color.White,
    primaryContainer = palette.soft,
    onPrimaryContainer = Color(0xFF24231F),
    secondary = palette.primary,
    onSecondary = Color.White,
    secondaryContainer = palette.soft,
    onSecondaryContainer = Color(0xFF24231F),
    tertiary = Color(0xFF765B3E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF0E5D6),
    onTertiaryContainer = Color(0xFF3D372E),
    inversePrimary = palette.darkPrimary,
    surfaceTint = palette.primary,
    background = Color(0xFFF8F7F2),
    onBackground = Color(0xFF20231F),
    surface = Color.White,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE8EBE8),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F4EF),
    surfaceContainer = Color(0xFFF2F5F0),
    surfaceContainerHigh = Color(0xFFECF1EB),
    surfaceContainerHighest = Color(0xFFE4EBE4),
    surfaceVariant = Color(0xFFF0F4F0),
    onSurface = Color(0xFF20231F),
    onSurfaceVariant = Color(0xFF68756D),
    outline = Color(0xFF77746E),
    outlineVariant = Color(0xFFDADDD6),
)

internal fun athenaDarkColorScheme(palette: AthenaPalette) = darkColorScheme(
    primary = palette.darkPrimary,
    onPrimary = Color(0xFF20201E),
    primaryContainer = palette.primary,
    onPrimaryContainer = Color(0xFFF4F3EF),
    secondary = palette.darkPrimary,
    onSecondary = Color(0xFF20201E),
    secondaryContainer = palette.primary,
    onSecondaryContainer = Color(0xFFF4F3EF),
    tertiary = Color(0xFFD9BA98),
    onTertiary = Color(0xFF3D372E),
    tertiaryContainer = Color(0xFF3D372E),
    onTertiaryContainer = Color(0xFFF0E5D6),
    inversePrimary = palette.primary,
    surfaceTint = palette.darkPrimary,
    background = Color(0xFF121614),
    onBackground = Color(0xFFE8E9E4),
    surface = Color(0xFF1B211E),
    surfaceBright = Color(0xFF39423C),
    surfaceDim = Color(0xFF121614),
    surfaceContainerLowest = Color(0xFF101512),
    surfaceContainerLow = Color(0xFF1B211E),
    surfaceContainer = Color(0xFF202723),
    surfaceContainerHigh = Color(0xFF272E29),
    surfaceContainerHighest = Color(0xFF313933),
    surfaceVariant = Color(0xFF282D29),
    onSurface = Color(0xFFE8E9E4),
    onSurfaceVariant = Color(0xFFB8BDB7),
    outline = Color(0xFF918F87),
    outlineVariant = Color(0xFF363C37),
)
