package com.tk.toolkit.tv.launcher.weather

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import com.tk.toolkit.tv.ui.theme.Motion
import java.util.Calendar
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The home view's weather wallpaper: the sky over the TV right now, with clouds drifting on the
 * actual wind and rain, snow or lightning when they're happening. It fades in and out with the
 * home view like the image wallpapers, reports the weather for the caption under the clock, and
 * says when it covers the screen so the backdrop under it can skip drawing.
 *
 * The sky and clouds are rendered once (on background threads) whenever the weather or the light
 * changes noticeably; each frame only slides those images along and draws the falling drops. It
 * redraws at about half the display's rate, which is plenty for clouds and still reads as rain,
 * and stops completely when the home view isn't showing.
 */
@Composable
fun WeatherLayer(
    visible: Boolean,
    onCaption: (String?) -> Unit,
    opaque: MutableState<Boolean>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember { WeatherRepository(context.applicationContext) }
    var report by remember { mutableStateOf(repository.cached()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var art by remember { mutableStateOf<SceneArt?>(null) }
    var previous by remember { mutableStateOf<SceneArt?>(null) }
    val crossfade = remember { Animatable(1f) }
    var time by remember { mutableFloatStateOf(0f) }
    val shown = animateFloatAsState(
        if (visible && art != null) 1f else 0f,
        tween(Motion.WallpaperRevealMillis, easing = LinearOutSlowInEasing),
        label = "weather",
    )
    val animating by remember { derivedStateOf { shown.value > 0f } }
    val reportCaption by rememberUpdatedState(onCaption)

    // The weather, every 15 minutes (every minute until the first report arrives).
    LaunchedEffect(Unit) {
        while (true) {
            repository.refresh()?.let { report = it }
            delay(if (report == null) 60_000L else 15 * 60_000L)
        }
    }
    // The light changes through the day even when the weather doesn't.
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            now = System.currentTimeMillis()
        }
    }
    // Rounded, so the sky is only re-rendered when it would look different.
    val scene = sceneFor(report, now).settled()
    val caption = if (WeatherPreview.active) {
        "Preview  \u00b7  ${scene.condition.label}  \u00b7  ${WeatherPreview.time.label}"
    } else {
        report?.caption(now)
    }
    LaunchedEffect(caption) { reportCaption(caption) }

    LaunchedEffect(scene, size) {
        if (size.width <= 0 || size.height <= 0) return@LaunchedEffect
        val w = size.width
        val h = size.height
        // The sky first, so something shows at once; the clouds follow as they're ready.
        val sky = withContext(Dispatchers.Default) { bitmapOf(renderSky(scene, w, h), w, h) }
        val next = SceneArt(scene, sky)
        previous = art
        art = next
        crossfade.snapTo(0f)
        crossfade.animateTo(1f, tween(Motion.WallpaperCrossfadeMillis, easing = LinearOutSlowInEasing))
        previous = null
        next.clouds = coroutineScope {
            cloudLayers(scene).map { spec ->
                async(Dispatchers.Default) { CloudArt(spec, bitmapOf(renderClouds(spec, scene), spec.width, spec.height)) }
            }.awaitAll()
        }
        next.cloudFade.animateTo(1f, tween(Motion.WallpaperCrossfadeMillis, easing = LinearOutSlowInEasing))
    }

    // The clock for the drift and the falling drops: every other frame, only while on screen.
    LaunchedEffect(animating) {
        if (!animating) return@LaunchedEffect
        var last = -1L
        while (true) {
            withFrameNanos { }
            withFrameNanos { t ->
                val dt = if (last < 0) 0f else ((t - last) / 1e9f).coerceAtMost(0.1f)
                last = t
                time += dt
                art?.precipitation?.step(dt, time, size.width.toFloat(), size.height.toFloat())
            }
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { shown.value >= 1f && art != null && crossfade.value >= 1f }.collect { opaque.value = it }
    }
    DisposableEffect(Unit) {
        onDispose {
            opaque.value = false
            reportCaption(null)
        }
    }

    val starPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; color = android.graphics.Color.WHITE } }
    val dropPaint = remember { Paint() }
    val starGroups = remember(size) { starGroups(size) }
    // A soft shade behind the clock, eased to nothing over the top 55% of the screen. Its end has to
    // be set to that height: by default a gradient spans the whole screen and would be cut off midway.
    val legibility = remember(size) {
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.32f),
            0.35f to Color.Black.copy(alpha = 0.2f),
            0.7f to Color.Black.copy(alpha = 0.06f),
            1f to Color.Transparent,
            startY = 0f,
            endY = size.height * ShadeHeight,
        )
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .graphicsLayer { alpha = shown.value }
            .drawBehind {
                val t = time
                val f = crossfade.value
                previous?.let { draw(it, t, 1f, starGroups, starPaint, dropPaint) }
                art?.let { draw(it, t, if (previous == null) 1f else f, starGroups, starPaint, dropPaint) }
                drawRect(legibility, size = Size(this.size.width, this.size.height * ShadeHeight))
            },
    )
}

/** How far down the shade behind the clock reaches. */
private const val ShadeHeight = 0.55f

/** What's been rendered for one scene. */
private class SceneArt(val scene: Scene, val sky: ImageBitmap) {
    var clouds by mutableStateOf<List<CloudArt>>(emptyList())
    val cloudFade = Animatable(0f)
    val precipitation = Precipitation(scene).takeIf { it.depths.isNotEmpty() }
    val lightning = if (scene.condition == Condition.Thunderstorm) Lightning() else null
    val starAlpha = starVisibility(scene)
}

private class CloudArt(val spec: CloudLayerSpec, val image: ImageBitmap)

private fun DrawScope.draw(
    art: SceneArt,
    time: Float,
    alpha: Float,
    starGroups: List<FloatArray>,
    starPaint: Paint,
    dropPaint: Paint,
) {
    val w = size.width
    val h = size.height
    val scale = h / 1080f
    drawImage(art.sky, dstSize = IntSize(w.toInt(), h.toInt()), alpha = alpha, filterQuality = FilterQuality.Low)

    if (art.starAlpha > 0f) {
        drawIntoCanvas { canvas ->
            // Groups twinkling out of step; one draw call each.
            starGroups.forEachIndexed { group, points ->
                val twinkle = 0.65f + 0.35f * sin(time * (0.9f + group * 0.35f) + group * 2.1f)
                val (brightness, width) = StarGroups[group]
                starPaint.alpha = (255 * art.starAlpha * alpha * twinkle * brightness).roundToInt().coerceIn(0, 255)
                starPaint.strokeWidth = width * scale
                canvas.nativeCanvas.drawPoints(points, starPaint)
            }
        }
    }

    val cloudAlpha = art.cloudFade.value * alpha
    if (cloudAlpha > 0f) {
        for (cloud in art.clouds) {
            val spec = cloud.spec
            val top = spec.top * h
            val bandH = (spec.bottom - spec.top) * h
            val scaledW = spec.width * bandH / spec.height
            val dst = IntSize(scaledW.toInt() + 2, bandH.toInt())
            var x = -(((drift(spec, art.scene) * time * scale) % scaledW) + scaledW) % scaledW
            while (x < w) {
                translate(x, top) {
                    drawImage(cloud.image, dstSize = dst, alpha = cloudAlpha, filterQuality = FilterQuality.Low)
                }
                x += scaledW
            }
        }
    }

    art.precipitation?.let { fall ->
        drawIntoCanvas { canvas ->
            fall.depths.forEachIndexed { index, d ->
                dropPaint.color = if (fall.snow) android.graphics.Color.WHITE else android.graphics.Color.rgb(210, 225, 242)
                // Smoothing hundreds of moving streaks costs the TV more than the whole sky, and at this
                // size and speed it doesn't show; only bigger, nearer snowflakes are drawn smooth and round.
                val smooth = fall.snow && index > 0
                dropPaint.isAntiAlias = smooth
                dropPaint.strokeCap = if (fall.snow) Paint.Cap.ROUND else Paint.Cap.BUTT
                dropPaint.alpha = (255 * d.alpha * alpha).roundToInt().coerceIn(0, 255)
                dropPaint.strokeWidth = d.strokeWidth * scale
                if (fall.snow) canvas.nativeCanvas.drawPoints(d.out, 0, d.used, dropPaint)
                else canvas.nativeCanvas.drawLines(d.out, 0, d.used, dropPaint)
            }
        }
    }

    art.lightning?.brightness(time)?.takeIf { it > 0.01f }?.let { b ->
        drawRect(Color(0xFFDCE6FF), alpha = b * 0.5f * alpha, topLeft = Offset.Zero, size = size)
    }
}

/** Star groups by brightness and size: a few bright ones, more middling, many faint. */
private val StarGroups = listOf(1f to 2.6f, 0.75f to 1.9f, 0.45f to 1.4f)

/** The star field's points in pixels, split into [StarGroups] by brightness. */
private fun starGroups(size: IntSize): List<FloatArray> {
    val field = stars()
    val groups = List(StarGroups.size) { ArrayList<Float>() }
    for (i in 0 until field.size / 5) {
        val b = field[i * 5 + 3]
        val g = when {
            b > 0.75f -> 0
            b > 0.45f -> 1
            else -> 2
        }
        groups[g] += field[i * 5] * size.width
        groups[g] += field[i * 5 + 1] * size.height
    }
    return groups.map { it.toFloatArray() }
}

private fun bitmapOf(pixels: IntArray, w: Int, h: Int): ImageBitmap =
    Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()

/** The scene with its values rounded, so tiny changes don't re-render the sky. */
private fun Scene.settled() = copy(
    cloudCover = (cloudCover * 20).roundToInt() / 20f,
    sunHeight = (sunHeight * 25).roundToInt() / 25f,
    arc = (arc * 25).roundToInt() / 25f,
    windKmh = (windKmh / 5).roundToInt() * 5f,
    intensity = (intensity * 10).roundToInt() / 10f,
)

/**
 * The scene to show at [now]: the real weather, or what [WeatherPreview] asks for. Without a
 * report yet, a fair sky with the sun up from 6 to 6.
 */
private fun sceneFor(report: WeatherReport?, now: Long): Scene {
    val hour = 60 * 60 * 1000L
    val (sunrise, sunset) = if (report != null) {
        val day = 24 * hour
        val shift = Math.round((now - (report.sunrise + report.sunset) / 2).toDouble() / day) * day
        report.sunrise + shift to report.sunset + shift
    } else {
        val midnight = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        midnight + 6 * hour to midnight + 18 * hour
    }
    val at = WeatherPreview.time.at(now, sunrise, sunset)
    val previewed = WeatherPreview.condition
    return when {
        previewed != null -> {
            val (code, cover) = previewed.sample()
            val wet = previewed.isWet || previewed == Condition.Snow
            Scene.of(code, cover, at, sunrise, sunset, report?.windKmh ?: 14f, report?.windFromDegrees ?: 270f, if (wet) 2f else 0f)
        }
        report != null -> report.sceneAt(at)
        else -> Scene.of(2, 35, at, sunrise, sunset, 8f, 270f, 0f)
    }
}
