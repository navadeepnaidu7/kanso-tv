package com.tk.toolkit.tv.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tk.toolkit.tv.ui.theme.ToolkitColors

/*
 * The launcher's panel language, kept quiet: a dark sheet on the right edge, a title, and plain
 * rows of text. A row turns into a white pill when focused (light means "here", as on the tiles);
 * a row that opens a page shows its current value and a small arrow, an option shows a check
 * when it's the chosen one. No icons, no descriptions.
 */

private val PanelShape = RoundedCornerShape(24.dp)
private val RowShape = RoundedCornerShape(14.dp)
private val PanelColor = Color(0xFF0E1118)
private val FocusedText = Color(0xFF0B0C10)
private val FocusedDanger = Color(0xFFC62828)

/** Lets panel content slide the panel out before acting, so every close animates. */
@Stable
class PanelState internal constructor() {
    internal val visibility = MutableTransitionState(false).apply { targetState = true }
    internal var after: (() -> Unit)? = null

    /** Slides the panel out, then removes it and runs [then]. */
    fun close(then: () -> Unit = {}) {
        after = then
        visibility.targetState = false
    }
}

/**
 * A sheet on the right edge. [onDismiss] runs once it has slid out, by Back or [PanelState.close].
 * [onBack] gets Back first (to step back out of a page); returning true keeps the panel open.
 */
@Composable
fun SidePanel(
    onDismiss: () -> Unit,
    width: Dp = 400.dp,
    /** How much the screen behind darkens; low when the point is to see what's behind. */
    dim: Float = 0.45f,
    onBack: () -> Boolean = { false },
    content: @Composable ColumnScope.(PanelState) -> Unit,
) {
    val state = remember { PanelState() }
    val visibility = state.visibility
    LaunchedEffect(visibility.isIdle, visibility.currentState) {
        if (visibility.isIdle && !visibility.currentState && !visibility.targetState) {
            onDismiss()
            state.after?.invoke()
        }
    }

    Dialog(
        onDismissRequest = { if (!onBack()) state.close() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(dim) }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            AnimatedVisibility(
                visibleState = visibility,
                enter = slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 420f)) { it / 2 } +
                    fadeIn(tween(180)),
                exit = slideOutHorizontally(tween(200)) { it / 2 } + fadeOut(tween(160)),
            ) {
                Column(
                    Modifier
                        .padding(20.dp)
                        .width(width)
                        .fillMaxHeight()
                        .clip(PanelShape)
                        .background(PanelColor)
                        .border(1.dp, Color.White.copy(alpha = 0.06f), PanelShape)
                        .ignoreHeldSelectKey()
                        // Panels are their own window, so the activity never sees these keys.
                        .recordKeysForSounds()
                        .padding(horizontal = 12.dp, vertical = 16.dp),
                ) {
                    content(state)
                }
            }
        }
    }
}

/** A panel's (or a page's) title. */
@Composable
fun PanelTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
        color = ToolkitColors.OnSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
    )
}

/** Space between groups of rows. */
@Composable
fun PanelGap() = Spacer(Modifier.height(14.dp))

/**
 * A row. [value] shows the current choice on the right, [opens] adds an arrow for rows that open a
 * page, [selected] a check for the chosen option.
 */
@Composable
fun PanelItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    opens: Boolean = false,
    selected: Boolean = false,
    danger: Boolean = false,
) {
    val rest = if (danger) ToolkitColors.Danger else ToolkitColors.OnSurface
    val focused = if (danger) FocusedDanger else FocusedText
    Surface(
        onClick = withClickSound(onClick),
        modifier = modifier
            .navigationSound()
            .fillMaxWidth()
            .height(52.dp),
        shape = ClickableSurfaceDefaults.shape(RowShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = rest,
            focusedContainerColor = Color.White,
            focusedContentColor = focused,
            pressedContainerColor = Color.White.copy(alpha = 0.85f),
            pressedContentColor = focused,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f, pressedScale = 0.98f),
    ) {
        val color = LocalContentColor.current
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Normal),
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            value?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal),
                    color = color.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
            if (opens) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = color.copy(alpha = 0.45f),
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(20.dp),
                )
            }
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = color.copy(alpha = 0.85f),
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(18.dp),
                )
            }
        }
    }
}
