package com.tk.toolkit.tv.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.tk.toolkit.tv.launcher.data.LauncherApp
import com.tk.toolkit.tv.ui.theme.ToolkitColors

/** The long-press menu for an app, as a side panel headed by the app's banner. */
@Composable
fun AppActionsPanel(
    app: LauncherApp,
    isHidden: Boolean,
    isFavorite: Boolean,
    /** What Move reorders: the dock or All Apps, depending on where the menu was opened. */
    moveDescription: String,
    onDismiss: () -> Unit,
    onMove: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleHidden: () -> Unit,
    onAppInfo: () -> Unit,
    onForceStop: () -> Unit,
    onUninstall: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocusWhenReady() }

    SidePanel(onDismiss = onDismiss) { panel ->
        Column(Modifier.verticalScroll(rememberScrollState())) {
            // Compact header: a small banner beside the name, so every action fits without scrolling.
            Row(
                Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppBanner(app, Modifier.width(128.dp))
                Column(Modifier.weight(1f)) { PanelTitle(app.label, app.packageName) }
            }
            if (isHidden) {
                PanelItem(
                    "Unhide", { panel.close(onToggleHidden) }, Modifier.focusRequester(first),
                    icon = Icons.Outlined.Visibility, subtitle = "Show it on the home screen again",
                )
            } else {
                PanelItem(
                    "Move", { panel.close(onMove) }, Modifier.focusRequester(first),
                    icon = Icons.Outlined.OpenWith, subtitle = moveDescription,
                )
                if (isFavorite) {
                    PanelItem(
                        "Remove from Favorites", { panel.close(onToggleFavorite) },
                        icon = Icons.Rounded.Star, subtitle = "Take it off the dock",
                    )
                } else {
                    PanelItem(
                        "Add to Favorites", { panel.close(onToggleFavorite) },
                        icon = Icons.Outlined.StarBorder, subtitle = "Pin it to the dock",
                    )
                }
                PanelItem(
                    "Hide", { panel.close(onToggleHidden) },
                    icon = Icons.Outlined.VisibilityOff, subtitle = "Move it to Hidden apps",
                )
            }
            PanelItem(
                "App info", { panel.close(onAppInfo) },
                icon = Icons.Outlined.Info, subtitle = "Storage, permissions and more",
            )
            PanelItem(
                "Force stop", { panel.close(onForceStop) },
                icon = Icons.Outlined.StopCircle, subtitle = "Close the app completely",
            )
            if (app.canUninstall) {
                PanelItem(
                    if (app.isUpdatedSystemApp) "Uninstall updates" else "Uninstall",
                    { panel.close(onUninstall) },
                    icon = Icons.Outlined.Delete,
                    subtitle = if (app.isUpdatedSystemApp) "Restore the factory version" else "Remove it from this TV",
                    danger = true,
                )
            }
        }
    }
}

/** The app's TV banner, or its icon on a wash of its color. */
@Composable
private fun AppBanner(app: LauncherApp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(shape)
            .background(Brush.linearGradient(listOf(lerp(app.accent, ToolkitColors.Surface, 0.4f), lerp(app.accent, Color.Black, 0.6f)))),
        contentAlignment = Alignment.Center,
    ) {
        if (app.banner != null) {
            Image(app.banner, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
        }
    }
}
