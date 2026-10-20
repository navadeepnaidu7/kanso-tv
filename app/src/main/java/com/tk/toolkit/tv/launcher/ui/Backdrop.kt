package com.tk.toolkit.tv.launcher.ui

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import com.tk.toolkit.tv.launcher.data.LauncherApp
import com.tk.toolkit.tv.ui.theme.Motion
import com.tk.toolkit.tv.ui.theme.ToolkitColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.withContext

/**
 * The home screen's background: the focused app's artwork, heavily blurred and toned down,
 * crossfading as focus moves, with a vignette and legibility scrims. [dimmed] darkens it while
 * browsing All Apps.
 *
 * TV GPUs are slow at live blur and full-screen gradients, so the blur is done once per app on
 * the CPU into a tiny image the GPU just stretches, scrims included. Per frame that's one plain
 * image draw (two while crossfading), in its own layer.
 */
@Composable
fun Backdrop(
    focusedApp: State<LauncherApp?>,
    dimmed: State<Boolean>,
    modifier: Modifier = Modifier,
    /** True while something opaque (the wallpaper) covers it, so it can skip drawing. */
    covered: () -> Boolean = { false },
) {
    var current by remember { mutableStateOf<ImageBitmap?>(null) }
    var previous by remember { mutableStateOf<ImageBitmap?>(null) }
    val fade = remember { Animatable(1f) }
    val cache = remember { HashMap<String, ImageBitmap>() }
    val dim = animateFloatAsState(if (dimmed.value) 0.5f else 0f, Motion.fade, label = "dim")

    LaunchedEffect(Unit) {
        snapshotFlow { focusedApp.value }
            .distinctUntilChangedBy { it?.packageName }
            .collectLatest { app ->
                if (app == null) return@collectLatest
                delay(Motion.BackdropSettleMillis)
                val key = "${app.packageName}:${app.lastUpdateTime}"
                val art = cache[key] ?: withContext(Dispatchers.Default) { blurredArt(app) }.also { cache[key] = it }
                if (art === current) return@collectLatest
                // Interrupted mid-fade: keep fading from whichever image is mostly showing.
                if (previous == null || fade.value >= 0.5f) previous = current
                current = art
                fade.snapTo(0f)
                fade.animateTo(1f, tween(Motion.BackdropMillis, easing = LinearOutSlowInEasing))
                previous = null
            }
    }

    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer()
            .drawBehind {
                if (covered()) return@drawBehind
                val dst = IntSize(this.size.width.toInt(), this.size.height.toInt())
                val cur = current
                val prev = previous
                val f = fade.value
                // Each full-screen draw costs the GPU milliseconds; only draw what shows.
                if (cur == null || (prev == null && f < 1f)) drawRect(ToolkitColors.Background)
                if (prev != null && f < 1f) {
                    drawImage(prev, dstSize = dst, filterQuality = FilterQuality.Low)
                }
                if (cur != null) {
                    drawImage(cur, dstSize = dst, alpha = f, filterQuality = FilterQuality.Low)
                }
                // A plain translucent fill: a color filter on the art did the same but cost ~10ms a frame.
                if (dim.value > 0f) drawRect(Color.Black.copy(alpha = dim.value))
            },
    )
}

private const val ART_W = 160
private const val ART_H = 90

/** The app's banner (or icon on its color), shrunk, toned down and box-blurred into a soft wash. */
private fun blurredArt(app: LauncherApp): ImageBitmap {
    val bitmap = Bitmap.createBitmap(ART_W, ART_H, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    // Richer color, less light: artwork becomes a backdrop, not a billboard.
    val mood = ColorMatrix().apply {
        setSaturation(1.7f)
        postConcat(ColorMatrix().apply { setScale(0.4f, 0.4f, 0.4f, 1f) })
    }
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(mood)
    }
    val banner = app.banner
    if (banner != null) {
        canvas.drawBitmap(banner.asAndroidBitmap(), null, Rect(0, 0, ART_W, ART_H), paint)
    } else {
        canvas.drawRect(0f, 0f, ART_W.toFloat(), ART_H.toFloat(), Paint(paint).apply { color = app.accent.toArgb() })
        val s = ART_H * 0.8f
        canvas.drawBitmap(
            app.icon.asAndroidBitmap(),
            null,
            RectF((ART_W - s) / 2f, (ART_H - s) / 2f, (ART_W + s) / 2f, (ART_H + s) / 2f),
            paint,
        )
    }
    val pixels = IntArray(ART_W * ART_H)
    bitmap.getPixels(pixels, 0, ART_W, 0, 0, ART_W, ART_H)
    // Three box passes each way approximate a wide Gaussian.
    repeat(3) {
        boxBlur(pixels, ART_W, ART_H, radius = 9, horizontal = true)
        boxBlur(pixels, ART_W, ART_H, radius = 9, horizontal = false)
    }
    bitmap.setPixels(pixels, 0, ART_W, 0, 0, ART_W, ART_H)
    paintScrims(canvas)
    return bitmap.asImageBitmap()
}

/** One box-blur pass along rows or columns, clamping at the edges. Opaque pixels only. */
private fun boxBlur(px: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
    val lines = if (horizontal) h else w
    val length = if (horizontal) w else h
    val r = IntArray(length)
    val g = IntArray(length)
    val b = IntArray(length)
    val window = radius * 2 + 1
    for (line in 0 until lines) {
        fun index(i: Int) = if (horizontal) line * w + i else i * w + line
        for (i in 0 until length) {
            val c = px[index(i)]
            r[i] = (c shr 16) and 0xFF
            g[i] = (c shr 8) and 0xFF
            b[i] = c and 0xFF
        }
        var sr = 0
        var sg = 0
        var sb = 0
        for (k in -radius..radius) {
            val j = k.coerceIn(0, length - 1)
            sr += r[j]; sg += g[j]; sb += b[j]
        }
        for (i in 0 until length) {
            px[index(i)] = (0xFF shl 24) or ((sr / window) shl 16) or ((sg / window) shl 8) or (sb / window)
            val out = (i - radius).coerceIn(0, length - 1)
            val inn = (i + radius + 1).coerceIn(0, length - 1)
            sr += r[inn] - r[out]; sg += g[inn] - g[out]; sb += b[inn] - b[out]
        }
    }
}

/**
 * Vignette, a top scrim for the clock and a bottom scrim for the dock. Soft gradients survive
 * being stretched, so they're painted into the small art rather than a screen-sized overlay,
 * which saves the GPU a full-screen pass every frame.
 */
private fun paintScrims(canvas: android.graphics.Canvas) {
    val w = ART_W.toFloat()
    val h = ART_H.toFloat()
    fun shade(alpha: Float) = android.graphics.Color.argb((alpha * 255).toInt(), 0, 0, 0)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = RadialGradient(
        w * 0.5f, h * 0.4f, w * 0.75f,
        intArrayOf(0, 0, shade(0.5f)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, w, h, paint)
    paint.shader = LinearGradient(0f, 0f, 0f, h * 0.22f, shade(0.3f), 0, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, w, h, paint)
    paint.shader = LinearGradient(0f, h * 0.45f, 0f, h, 0, shade(0.6f), Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, w, h, paint)
}
