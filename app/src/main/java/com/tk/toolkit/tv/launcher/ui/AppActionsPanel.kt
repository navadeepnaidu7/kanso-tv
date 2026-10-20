package com.tk.toolkit.tv.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.tk.toolkit.tv.launcher.data.LauncherApp

/** The long-press menu for an app: its name, then what can be done with it. */
@Composable
fun AppActionsPanel(
    app: LauncherApp,
    isHidden: Boolean,
    isFavorite: Boolean,
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
            PanelTitle(app.label)
            if (isHidden) {
                PanelItem("Unhide", { panel.close(onToggleHidden) }, Modifier.focusRequester(first))
            } else {
                PanelItem("Move", { panel.close(onMove) }, Modifier.focusRequester(first))
                PanelItem(
                    if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                    { panel.close(onToggleFavorite) },
                )
                PanelItem("Hide", { panel.close(onToggleHidden) })
            }
            PanelGap()
            PanelItem("App Info", { panel.close(onAppInfo) })
            PanelItem("Force Stop", { panel.close(onForceStop) })
            if (app.canUninstall) {
                PanelItem(
                    if (app.isUpdatedSystemApp) "Uninstall Updates" else "Uninstall",
                    { panel.close(onUninstall) },
                    danger = true,
                )
            }
        }
    }
}
