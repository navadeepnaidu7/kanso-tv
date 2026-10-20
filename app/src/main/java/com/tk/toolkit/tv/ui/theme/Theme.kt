package com.tk.toolkit.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

object ToolkitColors {
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
fun ToolkitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = ToolkitColors.Accent,
            onPrimary = Color(0xFF0B1030),
            secondary = ToolkitColors.GlowB,
            background = ToolkitColors.Background,
            onBackground = ToolkitColors.OnSurface,
            surface = ToolkitColors.Surface,
            onSurface = ToolkitColors.OnSurface,
            surfaceVariant = ToolkitColors.SurfaceRaised,
            onSurfaceVariant = ToolkitColors.OnSurfaceMuted,
            error = ToolkitColors.Danger,
            border = ToolkitColors.FocusRing,
        ),
        typography = Typography,
        content = content,
    )
}
