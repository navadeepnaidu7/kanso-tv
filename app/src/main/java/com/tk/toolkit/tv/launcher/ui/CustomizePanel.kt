package com.tk.toolkit.tv.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.tk.toolkit.tv.launcher.data.SortMode
import com.tk.toolkit.tv.launcher.data.WallpaperSource

/** Home screen settings: the home view's wallpaper and how All Apps is sorted. Picks apply live. */
@Composable
fun CustomizePanel(
    wallpaper: WallpaperSource,
    sort: SortMode,
    onDismiss: () -> Unit,
    onWallpaper: (WallpaperSource) -> Unit,
    onSort: (SortMode) -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocusWhenReady() }

    SidePanel(onDismiss = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            PanelTitle("Customize")
            PanelSection("Wallpaper")
            WallpaperSource.entries.forEach { source ->
                val isCurrent = source == wallpaper
                PanelItem(
                    source.title,
                    { onWallpaper(source) },
                    if (isCurrent) Modifier.focusRequester(first) else Modifier,
                    icon = source.icon(),
                    subtitle = source.description,
                    selected = isCurrent,
                )
            }
            PanelSection("Sort All Apps")
            SortMode.entries.forEach { mode ->
                PanelItem(
                    mode.title,
                    { onSort(mode) },
                    icon = mode.icon(),
                    subtitle = mode.description,
                    selected = mode == sort,
                )
            }
        }
    }
}

private fun WallpaperSource.icon() = when (this) {
    WallpaperSource.Art -> Icons.Outlined.Palette
    WallpaperSource.Earth -> Icons.Outlined.Public
    WallpaperSource.Mixed -> Icons.Outlined.Shuffle
    WallpaperSource.Mine -> Icons.Outlined.PhotoLibrary
    WallpaperSource.Off -> Icons.Outlined.BlurOn
}

private fun SortMode.icon() = when (this) {
    SortMode.Suggested -> Icons.Outlined.AutoAwesome
    SortMode.Name -> Icons.Outlined.SortByAlpha
    SortMode.Recent -> Icons.Outlined.History
    SortMode.Custom -> Icons.Outlined.DragIndicator
}
