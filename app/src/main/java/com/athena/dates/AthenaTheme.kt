package com.athena.dates

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class AthenaPalette(val label: String, val primary: Color, val soft: Color) {
    Violet("雾紫", Color(0xFF6757D9), Color(0xFFF0EEFF)),
    Sage("鼠尾草", Color(0xFF3D725F), Color(0xFFE9F5EE)),
    Amber("晨曦橙", Color(0xFFA9502E), Color(0xFFFFF0E9)),
    Ocean("深海蓝", Color(0xFF2F73A9), Color(0xFFEAF4FB)),
    Rose("桃粉", Color(0xFFA74466), Color(0xFFFCECF1)),
}

@Composable
fun AthenaTheme(palette: AthenaPalette, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = palette.primary,
            onPrimary = Color.White,
            primaryContainer = palette.soft,
            onPrimaryContainer = Color(0xFF252536),
            secondary = palette.primary,
            background = Color(0xFFFFFBFF),
            surface = Color(0xFFFFFBFF),
            surfaceVariant = Color(0xFFF3F1F7),
            onSurface = Color(0xFF20212A),
            onSurfaceVariant = Color(0xFF727384),
        ),
        content = content,
    )
}
