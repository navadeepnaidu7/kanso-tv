package com.navadeep.kanso.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

object KansoColors {
    val Background = Color(0xFF05060B)
    val GlowA = Color(0xFF3B3FD8)
    val GlowB = Color(0xFF0E8F8A)
    val GlowC = Color(0xFF8A3FD8)
    val GlowD = Color(0xFF1B4FD8)
    val Surface = Color(0xFF14161D)
    val SurfaceRaised = Color(0xFF1C1F28)
    val OnSurface = Color(0xFFF2F3F7)
    val OnSurfaceMuted = Color(0xFF9097A6)
    val Accent = Color(0xFF8FA8FF)
    val Danger = Color(0xFFFF7A7A)
    val FocusRing = Color(0xFFF5F6FA)
}

@Composable
fun KansoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = KansoColors.Accent,
            onPrimary = Color(0xFF0B1030),
            secondary = KansoColors.GlowB,
            background = KansoColors.Background,
            onBackground = KansoColors.OnSurface,
            surface = KansoColors.Surface,
            onSurface = KansoColors.OnSurface,
            surfaceVariant = KansoColors.SurfaceRaised,
            onSurfaceVariant = KansoColors.OnSurfaceMuted,
            error = KansoColors.Danger,
            border = KansoColors.FocusRing,
        ),
        typography = Typography,
        content = content,
    )
}
