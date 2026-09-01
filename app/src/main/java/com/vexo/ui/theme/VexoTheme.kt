package com.vexo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VexoDarkScheme = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0A3A6B),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFF8E8E93),
    onSecondary = Color.White,
    surface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFF2C2C2E),
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFF8E8E93),
    background = Color.Black,
    onBackground = Color.White,
    outline = Color(0xFF38383A),
    outlineVariant = Color(0xFF2C2C2E),
    error = Color(0xFFFF453A),
    onError = Color.White,
)

@Composable
fun VexoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VexoDarkScheme,
        content = content,
    )
}
