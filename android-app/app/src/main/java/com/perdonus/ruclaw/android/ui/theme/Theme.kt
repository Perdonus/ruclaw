package com.perdonus.ruclaw.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RuClawDarkScheme = darkColorScheme(
    primary = Color(0xFF72D8C4),
    onPrimary = Color(0xFF08221E),
    secondary = Color(0xFFFFA56D),
    onSecondary = Color(0xFF2B1100),
    tertiary = Color(0xFFFFD47A),
    background = Color(0xFF0E1117),
    onBackground = Color(0xFFF6EFE4),
    surface = Color(0xFF141A22),
    onSurface = Color(0xFFF6EFE4),
    surfaceContainer = Color(0xFF1A2330),
    surfaceContainerHigh = Color(0xFF202B39),
    surfaceContainerHighest = Color(0xFF293647),
    outline = Color(0xFF4A5D70),
)

private val RuClawLightScheme = lightColorScheme(
    primary = Color(0xFF0E7F72),
    onPrimary = Color(0xFFF8FBFA),
    secondary = Color(0xFFC2642A),
    onSecondary = Color(0xFFFFF8F5),
    tertiary = Color(0xFFA67000),
    background = Color(0xFFF6F1E8),
    onBackground = Color(0xFF15181D),
    surface = Color(0xFFFFFBF6),
    onSurface = Color(0xFF15181D),
    surfaceContainer = Color(0xFFF1E7D7),
    surfaceContainerHigh = Color(0xFFE9DDCC),
    surfaceContainerHighest = Color(0xFFDED1BF),
    outline = Color(0xFF8A7C6B),
)

@Composable
fun RuClawTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) RuClawDarkScheme else RuClawLightScheme,
        typography = AppTypography,
        content = content,
    )
}
