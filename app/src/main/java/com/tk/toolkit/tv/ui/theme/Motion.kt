package com.tk.toolkit.tv.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Every animation on the home screen, in one place. Springs read as:
 * stiffness = speed (higher is faster), dampingRatio = bounce (1 is none, lower bounces more).
 */
object Motion {
    /** How much a focused tile grows. */
    const val FocusScale = 1.08f

    /** How much a favorite grows while it's being moved. */
    const val MovingScale = 1.14f

    /** How far a tile sinks while OK is held, relative to its focused size. */
    const val PressScale = 0.95f

    /** Focus lift: quick, with a light overshoot. */
    val focus: AnimationSpec<Float> = spring(dampingRatio = 0.75f, stiffness = 700f)

    /** Press feedback: fast and firm. */
    val press: AnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 1600f)

    /**
     * The page gliding between the dock and All Apps, and row to row. No bounce, so it settles
     * cleanly; soft enough that the long dock-to-grid move reads as one continuous glide.
     */
    val verticalScroll: AnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 200f)

    /** The dock gliding sideways when it has more favorites than fit. */
    val horizontalScroll: AnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 380f)

    /** The dock settling down into the home view, and rising back when focus returns to it. */
    val dockSlide: AnimationSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 260f)

    /** Favorites sliding into place while reordering. */
    val reorder: FiniteAnimationSpec<IntOffset> = spring(dampingRatio = 0.85f, stiffness = 500f)

    /** Fades: labels, titles, dimming. */
    val fade: AnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 500f)

    /** An app's tile gliding to the middle and its colors flooding the screen. A whisper of overshoot. */
    val launchGrow: AnimationSpec<Float> = spring(dampingRatio = 0.86f, stiffness = 240f)

    /** Coming back from an app: the tile settling into its place, no bounce. */
    val launchReturn: AnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 300f)

    /** How far into the opening the app is started, so its start-up hides behind the motion. */
    const val LaunchHandoffMillis = 200L

    /** How long to wait for an app to cover the screen before giving up and coming back. */
    const val LaunchGiveUpMillis = 1_500L

    /** How wide the tile grows to while opening, as a share of the screen width. */
    const val LaunchTileWidth = 0.3f

    /** How long the background takes to crossfade to the focused app's artwork. */
    const val BackdropMillis = 550

    /** The wallpaper fading in as the home view opens, and out as focus moves to the tiles. */
    const val WallpaperRevealMillis = 700

    /** One wallpaper dissolving into the next. */
    const val WallpaperCrossfadeMillis = 1_500

    /** How long each wallpaper stays up. */
    const val WallpaperRotateMillis = 180_000L

    /** One sweep of the wallpaper's slow pan and zoom. */
    const val WallpaperDriftMillis = 45_000

    /** Focus has to rest this long before the background changes, so fast scrolling doesn't strobe it. */
    const val BackdropSettleMillis = 90L
}

/** Home screen measurements. */
object Dimens {
    val EdgePadding = 56.dp
    const val Columns = 5
    val TileGap = 24.dp
    val RowGap = 6.dp
    val TileCorner = 14.dp

    /** Room around tiles so a focused (grown, shadowed) tile isn't clipped. */
    val LiftRoom = 18.dp

    /** Space under each tile for the focused app's name. */
    val LabelHeight = 30.dp

    /** Focused tile's shadow. */
    val FocusElevation = 14.dp

    /** How much of the next favorite stays visible at the dock's right edge. */
    val DockPeek = 64.dp

    /**
     * How much of "All Apps" peeks above the bottom edge while on the dock: the title and the top of
     * its first row. That row has to be on screen for Down to land on the tile under the focused one.
     */
    val AllAppsPeek = 84.dp
}
