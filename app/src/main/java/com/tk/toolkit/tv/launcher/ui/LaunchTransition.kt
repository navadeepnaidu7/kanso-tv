package com.tk.toolkit.tv.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.tk.toolkit.tv.launcher.data.LauncherApp
import com.tk.toolkit.tv.ui.theme.Dimens
import com.tk.toolkit.tv.ui.theme.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Opening an app: the tile lifts off and glides to the middle of the screen, growing, while a
 * wash of its colors floods out from where it sat. The app then fades in over that. Coming back,
 * the same thing plays in reverse and the tile settles back into its place.
 *
 * It plays in a window of its own over the home screen. The compositor stacks the two, so the
 * home screen doesn't redraw while it plays (redrawing it every frame is what this TV's GPU can't
 * keep up with); each frame is just the wash and the tile, moved by layer transforms.
 */
@Stable
class LaunchTransition internal constructor(private val scope: CoroutineScope) {
    private enum class Phase { Idle, Opening, Away, Returning }

    private var phase by mutableStateOf(Phase.Idle)
    private var job: Job? = null
    private var paused = false

    /** The app opening, or being returned from; null when nothing is showing. */
    internal var app by mutableStateOf<LauncherApp?>(null)
        private set
    internal var wash by mutableStateOf<ImageBitmap?>(null)
        private set

    /** Where the tile was on screen, in root pixels; null to grow from the middle. */
    internal var from: Rect? = null
        private set

    /** 0 is the home screen, 1 the app filling the screen. Read it in layer blocks only. */
    internal val progress = Animatable(0f)

    /** While the tile is on its way out, keys are ignored so focus doesn't move under it. */
    val blocksInput: Boolean get() = phase == Phase.Opening

    /** Plays the opening from [from] and calls [start] partway through. [start] says whether the app launched. */
    fun open(app: LauncherApp, from: Rect?, start: () -> Boolean) {
        if (phase == Phase.Opening || phase == Phase.Away) return
        job?.cancel()
        this.app = app
        this.from = from
        // Usually ready: the backdrop made it when the tile was focused.
        wash = Washes.get(app)
        paused = false
        phase = Phase.Opening
        job = scope.launch {
            val grow = launch { progress.animateTo(1f, Motion.launchGrow) }
            // Handing off before the opening finishes hides the app's start-up behind the motion.
            delay(Motion.LaunchHandoffMillis)
            if (!start()) {
                grow.cancel()
                comeBack()
                return@launch
            }
            phase = Phase.Away
            grow.join()
            // Nothing took over the screen (the launch was blocked, or the app quit at once).
            delay(Motion.LaunchGiveUpMillis)
            if (!paused) comeBack()
        }
    }

    internal fun onPaused() {
        paused = true
    }

    internal fun onResumed() {
        paused = false
        if (phase == Phase.Away) {
            job?.cancel()
            job = scope.launch { comeBack() }
        }
    }

    private suspend fun comeBack() {
        phase = Phase.Returning
        progress.animateTo(0f, Motion.launchReturn)
        phase = Phase.Idle
        app = null
        wash = null
    }
}

@Composable
fun rememberLaunchTransition(): LaunchTransition {
    val scope = rememberCoroutineScope()
    val transition = remember { LaunchTransition(scope) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { transition.onPaused() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { transition.onResumed() }
    return transition
}

/** Draws the opening over the whole screen, in its own window, while one is playing. */
@Composable
fun LaunchOverlay(transition: LaunchTransition) {
    // The window always exists, sized to nothing while idle: creating it on OK costs ~150ms,
    // which would swallow the start of the opening. Not focusable, so keys keep going to the home screen.
    Popup(
        popupPositionProvider = TopLeft,
        properties = PopupProperties(focusable = false, clippingEnabled = false),
    ) {
        val app = transition.app
        if (app != null) Opening(transition, app, transition.wash)
    }
}

private object TopLeft : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ) = IntOffset.Zero
}

@Composable
private fun Opening(transition: LaunchTransition, app: LauncherApp, wash: ImageBitmap?) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val from = transition.from ?: Rect(
            offset = androidx.compose.ui.geometry.Offset(screen.width * 0.42f, screen.height * 0.42f),
            size = Size(screen.width * 0.16f, screen.width * 0.09f),
        )
        val progress = transition.progress

        // The flood: the app's blurred colors, growing from the tile's rectangle to the whole screen.
        if (wash != null) {
            Image(
                wash,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.Low,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val t = progress.value.coerceIn(0f, 1f)
                        val sx = lerp(from.width / size.width, 1f, t)
                        val sy = lerp(from.height / size.height, 1f, t)
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = sx
                        scaleY = sy
                        translationX = lerp(from.left, 0f, t)
                        translationY = lerp(from.top, 0f, t)
                        // Corners in the layer's own (unscaled) units, rounding off to square at full screen.
                        // No clip once it fills the screen; clipping a full-screen layer isn't free.
                        clip = t < 0.995f
                        if (clip) shape = RoundedCornerShape(Dimens.TileCorner.toPx() * (1f - t) / sx)
                    },
            )
        }

        // The tile itself, gliding to the middle and growing.
        val tileW = from.width.toInt().coerceAtLeast(1)
        val tileH = from.height.toInt().coerceAtLeast(1)
        Box(
            Modifier
                .layout { measurable, _ ->
                    val placeable = measurable.measure(Constraints.fixed(tileW, tileH))
                    layout(tileW, tileH) { placeable.place(0, 0) }
                }
                .graphicsLayer {
                    val t = progress.value
                    val grown = screen.width * Motion.LaunchTileWidth / from.width
                    val s = lerp(1f, grown, t)
                    scaleX = s
                    scaleY = s
                    translationX = lerp(from.left, (screen.width - from.width) / 2f, t)
                    translationY = lerp(from.top, (screen.height - from.height) / 2f, t)
                    shape = TileShape
                    clip = true
                    // Grows with the tile; a large soft shadow costs the GPU more than the rest put together.
                    shadowElevation = Dimens.FocusElevation.toPx()
                },
        ) {
            AppArt(app)
        }
    }
}
