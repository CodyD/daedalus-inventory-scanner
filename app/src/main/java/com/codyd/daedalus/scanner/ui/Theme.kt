package com.codyd.daedalus.scanner.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Always dark: the camera preview dominates, and it matches the desktop app's default theme. */
private val colors = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    onPrimary = Color(0xFF00242F),
    secondary = Color(0xFF9FB4BF),
    background = Color(0xFF1E1E22),
    surface = Color(0xFF26262B),
    surfaceVariant = Color(0xFF33333A),
    onSurface = Color(0xFFE6E6EA),
    onSurfaceVariant = Color(0xFFB5B5BE),
    error = Color(0xFFFF8A80),
)

@Composable
fun ScannerTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = colors, content = content)
