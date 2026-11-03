package com.pulse.player.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB388FF),
    onPrimary = Color(0xFF1B0B3A),
    primaryContainer = Color(0xFF3A2470),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFF3DDCC0),
    onSecondary = Color(0xFF00382F),
    background = Color(0xFF0B0B12),
    onBackground = Color(0xFFECECF6),
    surface = Color(0xFF12121C),
    onSurface = Color(0xFFECECF6),
    surfaceVariant = Color(0xFF1C1C2A),
    onSurfaceVariant = Color(0xFFA9A9C0),
    surfaceContainer = Color(0xFF16161F),
    surfaceContainerLow = Color(0xFF12121A),
    surfaceContainerHigh = Color(0xFF1C1C28),
    surfaceContainerHighest = Color(0xFF232333),
    outline = Color(0xFF3A3A50),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF6A2BD9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF22005D),
    secondary = Color(0xFF007A68),
    onSecondary = Color.White,
    background = Color(0xFFF8F6FD),
    onBackground = Color(0xFF1B1B24),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1B24),
    surfaceVariant = Color(0xFFEAE6F4),
    onSurfaceVariant = Color(0xFF55536A),
    surfaceContainer = Color(0xFFF1EEF8),
    surfaceContainerLow = Color(0xFFF5F2FB),
    surfaceContainerHigh = Color(0xFFEBE7F5),
    surfaceContainerHighest = Color(0xFFE4E0F0),
    outline = Color(0xFFC9C5D8),
)

@Composable
fun PulseTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = Typography(),
        content = content,
    )
}
