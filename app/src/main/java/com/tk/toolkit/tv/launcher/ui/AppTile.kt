package com.tk.toolkit.tv.launcher.ui

import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import com.tk.toolkit.tv.launcher.data.LauncherApp
import com.tk.toolkit.tv.ui.theme.ToolkitColors

@Composable
fun AppTile(
    app: LauncherApp,
    index: Int,
    isMoving: Boolean,
    dimmed: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoveBy: (Int) -> Unit,
    onDrop: () -> Unit,
    modifier: Modifier = Modifier,
    /** When set, Up/Down move a moving tile by this many places (a grid row) instead of being held. */
    columns: Int? = null,
    /** Where Up goes from this tile, when the default search would pick the wrong thing. */
    upTarget: FocusRequester? = null,
) {
    val bringIntoView = remember { BringIntoViewRequester() }
    val view = LocalView.current

    // Keep the tile being moved focused and on screen as it changes position.
    LaunchedEffect(isMoving, index) {
        if (isMoving) {
            runCatching { focusRequester.requestFocus() }
            bringIntoView.bringIntoView()
        }
    }

    FocusTile(
        label = app.label,
        focusRequester = focusRequester,
        onFocused = onFocused,
        onClick = onClick,
        onLongClick = onLongClick,
        isMoving = isMoving,
        dimmed = dimmed,
        modifier = modifier.moveKeys(
            isMoving,
            onMoveBy = { delta ->
                UiSounds.moved(view, delta, columns)
                onMoveBy(delta)
            },
            onDrop = {
                UiSounds.click(view)
                onDrop()
            },
            columns = columns,
        ),
        tileModifier = Modifier
            .bringIntoViewRequester(bringIntoView)
            .focusProperties { upTarget?.let { up = it } },
    ) {
        if (app.banner != null) {
            Image(
                app.banner,
                contentDescription = app.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            IconTileContent(app)
        }
        if (isMoving) MoveOverlay()
    }
}

/** Tile for apps without a TV banner: icon over a gradient tinted with the icon's color. */
@Composable
private fun IconTileContent(app: LauncherApp) {
    val base = lerp(app.accent, ToolkitColors.Surface, 0.55f)
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(base, lerp(base, Color.Black, 0.5f)))),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            app.icon,
            contentDescription = app.label,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
    }
}

/**
 * While [isMoving], left/right move the tile one place, up/down one row of [columns] (or are held
 * in a single row), and OK drops it.
 */
internal fun Modifier.moveKeys(isMoving: Boolean, onMoveBy: (Int) -> Unit, onDrop: () -> Unit, columns: Int? = null) =
    onPreviewKeyEvent { event ->
        if (!isMoving) return@onPreviewKeyEvent false
        val down = event.type == KeyEventType.KeyDown
        when (event.nativeKeyEvent.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { if (down) onMoveBy(-1); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { if (down) onMoveBy(1); true }
            KeyEvent.KEYCODE_DPAD_UP -> { if (down && columns != null) onMoveBy(-columns); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { if (down && columns != null) onMoveBy(columns); true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (event.type == KeyEventType.KeyUp) onDrop()
                true
            }
            else -> false
        }
    }

@Composable
internal fun MoveOverlay() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.OpenWith, null, tint = Color.White, modifier = Modifier.size(32.dp))
    }
}
