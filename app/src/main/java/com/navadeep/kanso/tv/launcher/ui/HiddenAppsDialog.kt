package com.navadeep.kanso.tv.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.navadeep.kanso.tv.launcher.data.LauncherApp
import com.navadeep.kanso.tv.ui.theme.Motion

private const val HIDDEN_COLUMNS = 2

/** Side panel listing hidden apps. Click launches; long press opens the app's actions (to unhide). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HiddenAppsPanel(
    apps: List<LauncherApp>,
    onDismiss: () -> Unit,
    onLaunch: (LauncherApp) -> Unit,
    onLongPress: (LauncherApp) -> Unit,
) {
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val lastFocused = remember { arrayOfNulls<String>(1) }
    val scrollSpec = with(LocalDensity.current) {
        remember { edgeScrollSpec(16.dp.toPx(), 16.dp.toPx(), Motion.verticalScroll) }
    }

    // Focus the first app on open, and again if the focused app gets unhidden.
    LaunchedEffect(apps.map { it.packageName }) {
        val keys = apps.map { it.packageName }
        if (keys.isEmpty() || lastFocused[0] in keys) return@LaunchedEffect
        focusRequesters.getOrPut(keys.first()) { FocusRequester() }.requestFocusWhenReady()
    }

    SidePanel(onDismiss = onDismiss, width = 560.dp) { panel ->
        PanelTitle("Hidden Apps")
        CompositionLocalProvider(LocalBringIntoViewSpec provides scrollSpec) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(HIDDEN_COLUMNS),
                // Room for the focused tile's lift.
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                    AppTile(
                        app = app,
                        index = index,
                        isMoving = false,
                        dimmed = false,
                        focusRequester = focusRequesters.getOrPut(app.packageName) { FocusRequester() },
                        onFocused = { lastFocused[0] = app.packageName },
                        onClick = { panel.close { onLaunch(app) } },
                        onLongClick = { onLongPress(app) },
                        onMoveBy = {},
                        onDrop = {},
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}
