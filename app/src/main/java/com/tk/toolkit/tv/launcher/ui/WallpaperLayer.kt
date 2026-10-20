package com.tk.toolkit.tv.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import com.tk.toolkit.tv.launcher.data.Wallpaper
import com.tk.toolkit.tv.launcher.data.WallpaperRepository
import com.tk.toolkit.tv.launcher.data.WallpaperSource
import com.tk.toolkit.tv.ui.theme.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The home view's wallpaper: images from [source], crossfading every few minutes, drifting with a
 * slow pan and zoom while [visible]. It fades out when focus moves to the tiles, and reports what's
 * showing through [onShowing] (for the caption) and whether it fully covers the screen through
 * [opaque] (so the backdrop under it can skip drawing).
 *
 * The drift only moves this layer's transform, so it costs the GPU one image draw a frame and
 * never redraws the tiles; it stops entirely when the wallpaper isn't visible.
 */
@Composable
fun WallpaperLayer(
    source: WallpaperSource,
    visible: Boolean,
    onShowing: (Wallpaper?) -> Unit,
    opaque: MutableState<Boolean>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember { WallpaperRepository(context.applicationContext) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var current by remember { mutableStateOf<ImageBitmap?>(null) }
    var previous by remember { mutableStateOf<ImageBitmap?>(null) }
    val fade = remember { Animatable(1f) }
    val drift = remember { Animatable(0f) }
    val shown = animateFloatAsState(
        if (visible && current != null) 1f else 0f,
        tween(Motion.WallpaperRevealMillis, easing = LinearOutSlowInEasing),
        label = "wallpaper",
    )
    val reportShowing by rememberUpdatedState(onShowing)

    // Cycle through the source's images. Restarts when the source or screen size changes.
    LaunchedEffect(source, size) {
        if (size.width <= 0 || size.height <= 0) return@LaunchedEffect
        val list = withContext(Dispatchers.IO) { repository.list(source) }
        if (list.isEmpty()) {
            current = null
            previous = null
            reportShowing(null)
            return@LaunchedEffect
        }
        var index = 0
        var failures = 0
        while (failures < list.size) {
            val wallpaper = list[index % list.size]
            index++
            val image = withContext(Dispatchers.IO) { repository.load(wallpaper, size.width, size.height) }
            if (image == null) {
                failures++
                delay(1_000)
                continue
            }
            failures = 0
            previous = current
            current = image
            reportShowing(wallpaper)
            fade.snapTo(0f)
            fade.animateTo(1f, tween(Motion.WallpaperCrossfadeMillis, easing = LinearOutSlowInEasing))
            previous = null
            delay(Motion.WallpaperRotateMillis)
        }
        reportShowing(null)
    }

    // Slow pan and zoom, back and forth, only while it's on screen.
    LaunchedEffect(visible) {
        while (visible) {
            drift.animateTo(if (drift.value < 0.5f) 1f else 0f, tween(Motion.WallpaperDriftMillis, easing = LinearEasing))
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { shown.value >= 1f && fade.value >= 1f && current != null }.collect { opaque.value = it }
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .graphicsLayer {
                alpha = shown.value
                // Always slightly enlarged, so panning never shows an edge.
                val d = drift.value
                scaleX = 1.04f + 0.04f * d
                scaleY = scaleX
                translationX = (d - 0.5f) * 0.02f * this.size.width
            }
            .drawBehind {
                val dst = IntSize(this.size.width.toInt(), this.size.height.toInt())
                val f = fade.value
                previous?.let { if (f < 1f) drawImage(it, dstSize = dst, filterQuality = FilterQuality.Low) }
                current?.let { drawImage(it, dstSize = dst, alpha = f, filterQuality = FilterQuality.Low) }
            },
    )
}
