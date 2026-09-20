package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AutoMirrorColorScheme = darkColorScheme(
    primary = AutoCyanPrimary,
    onPrimary = Color(0xFF003830),
    primaryContainer = Color(0xFF005045),
    onPrimaryContainer = Color(0xFF70FCE5),
    secondary = AutoSkySecondary,
    onSecondary = Color(0xFF00344F),
    secondaryContainer = Color(0xFF004C72),
    onSecondaryContainer = Color(0xFFC8E6FF),
    tertiary = AutoAmber,
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF643F00),
    onTertiaryContainer = Color(0xFFFFDDB3),
    background = AutoCarbon,
    onBackground = AutoTextPrimary,
    surface = AutoSurface,
    onSurface = AutoTextPrimary,
    surfaceVariant = AutoSurfaceElevated,
    onSurfaceVariant = AutoTextSecondary,
    outline = AutoBorder,
    error = AutoRedAlert,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AutoMirrorColorScheme,
        typography = Typography,
        content = content
    )
}
