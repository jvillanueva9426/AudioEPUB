package com.ferdyan.audioepub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ferdyan.audioepub.model.ReaderThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF312E81),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF3730A3),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF0C4A6E),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF8FAFC)
)

private val SepiaColors = lightColorScheme(
    primary = Color(0xFF8B4513),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8D3A7),
    onPrimaryContainer = Color(0xFF2A1F16),
    secondary = Color(0xFFA0522D),
    onSecondary = Color.White,
    background = Color(0xFFFBF0D9),
    onBackground = Color(0xFF4A3B2C),
    surface = Color(0xFFF4E4C1),
    onSurface = Color(0xFF3E2D1F),
    surfaceVariant = Color(0xFFEAD8B1),
    onSurfaceVariant = Color(0xFF5F4B32)
)

@Composable
fun AudioEpubTheme(
    themeMode: ReaderThemeMode = ReaderThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val colorScheme = when (themeMode) {
        ReaderThemeMode.SEPIA -> SepiaColors
        ReaderThemeMode.LIGHT -> LightColors
        ReaderThemeMode.DARK -> DarkColors
        ReaderThemeMode.SYSTEM -> if (systemInDark) DarkColors else LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
