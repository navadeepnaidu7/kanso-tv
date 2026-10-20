package com.navadeep.kanso.tv.launcher.weather

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * The weather wallpaper's look, as plain math with no Android types, so the same code can be
 * rendered to images on a desktop to check how each kind of weather looks.
 *
 * A scene is a sky (a gradient for the time of day and the weather, with the sun or moon's glow
 * baked in) under up to four cloud layers. Each cloud layer is a wide image that wraps around
 * horizontally, so it can drift across the screen forever. Rain, snow, stars and lightning are
 * drawn live on top (see Particles.kt).
 */

/** What kind of weather it is, broadly: it decides the clouds, the sky's grey and what falls. */
enum class Condition(val label: String) {
    Clear("Clear"),
    MostlyClear("Mostly clear"),
    PartlyCloudy("Partly cloudy"),
    MostlyCloudy("Mostly cloudy"),
    Overcast("Overcast"),
    Fog("Fog"),
    Drizzle("Drizzle"),
    Rain("Rain"),
    HeavyRain("Heavy rain"),
    Snow("Snow"),
    Thunderstorm("Thunderstorm"),
    ;

    val isWet get() = this == Drizzle || this == Rain || this == HeavyRain || this == Thunderstorm
}

/** Everything the scene's look depends on. */
data class Scene(
    val condition: Condition,
    /** Share of the sky under cloud, 0 to 1. */
    val cloudCover: Float,
    /** How high the sun is: 1 at midday, 0 at sunrise and sunset, -1 in the middle of the night. */
    val sunHeight: Float,
    /** Where the sun (or by night the moon) is across its arc, 0 rising to 1 setting. */
    val arc: Float,
    /** True before midday: twilight is a dawn, not a dusk. */
    val morning: Boolean,
    /** Wind along the screen, in km/h; positive blows to the right. */
    val windKmh: Float,
    /** How hard rain or snow is falling, 0 to 1. */
    val intensity: Float,
) {
    /** 0 by night, 0.5 at sunrise/sunset, 1 by day; everything's colors blend along this. */
    val light: Float get() = ((sunHeight + 0.12f) / 0.47f).coerceIn(0f, 1f)

    companion object {
        /**
         * The scene at [now] (epoch millis) for [code] (a WMO weather code, as Open-Meteo reports
         * it), [cloudCover] in percent, today's [sunrise] and [sunset], wind and precipitation.
         */
        fun of(
            code: Int,
            cloudCover: Int,
            now: Long,
            sunrise: Long,
            sunset: Long,
            windKmh: Float,
            windFromDegrees: Float,
            precipitationMm: Float,
        ): Scene {
            val day = 24 * 60 * 60 * 1000L
            val (height, arc) = when {
                now in sunrise..sunset -> {
                    val f = (now - sunrise).toFloat() / (sunset - sunrise)
                    sin(PI * f).toFloat() to f
                }
                now > sunset -> {
                    val f = (now - sunset).toFloat() / (sunrise + day - sunset)
                    -sin(PI * f).toFloat() to f
                }
                else -> {
                    val f = (now - (sunset - day)).toFloat() / (sunrise - (sunset - day))
                    -sin(PI * f).toFloat() to f
                }
            }
            val noon = (sunrise + sunset) / 2
            val cover = cloudCover / 100f
            val condition = conditionOf(code, cover)
            // Wind "from" the west blows east, to the right.
            val along = -sin(windFromDegrees * PI / 180.0).toFloat()
            val intensity = when (condition) {
                Condition.Drizzle -> 0.25f
                Condition.HeavyRain, Condition.Thunderstorm -> 1f
                Condition.Rain, Condition.Snow -> (0.45f + precipitationMm / 6f).coerceAtMost(0.9f)
                else -> 0f
            }
            return Scene(
                condition = condition,
                cloudCover = coverFor(condition, cover),
                sunHeight = height,
                arc = arc.coerceIn(0f, 1f),
                morning = now < noon && now > sunrise - day / 4,
                windKmh = windKmh * (if (along == 0f) 1f else along),
                intensity = intensity,
            )
        }

        private fun conditionOf(code: Int, cover: Float) = when (code) {
            0, 1, 2, 3 -> when {
                cover < 0.12f -> Condition.Clear
                cover < 0.35f -> Condition.MostlyClear
                cover < 0.62f -> Condition.PartlyCloudy
                cover < 0.88f -> Condition.MostlyCloudy
                else -> Condition.Overcast
            }
            45, 48 -> Condition.Fog
            51, 53, 55, 56, 57 -> Condition.Drizzle
            65, 67, 82 -> Condition.HeavyRain
            61, 63, 66, 80, 81 -> Condition.Rain
            71, 73, 75, 77, 85, 86 -> Condition.Snow
            95, 96, 99 -> Condition.Thunderstorm
            else -> Condition.PartlyCloudy
        }

        /** The reported cover, kept within what the condition looks like. */
        private fun coverFor(condition: Condition, cover: Float) = when (condition) {
            Condition.Clear -> cover.coerceIn(0f, 0.12f)
            Condition.MostlyClear -> cover.coerceIn(0.12f, 0.35f)
            Condition.PartlyCloudy -> cover.coerceIn(0.35f, 0.62f)
            Condition.MostlyCloudy -> cover.coerceIn(0.62f, 0.88f)
            Condition.Fog -> cover.coerceIn(0.5f, 1f)
            else -> cover.coerceIn(0.88f, 1f)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Colors

private fun argb(r: Float, g: Float, b: Float, a: Float = 1f): Int =
    ((a.coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 24) or
        ((r.coerceIn(0f, 255f) + 0.5f).toInt() shl 16) or
        ((g.coerceIn(0f, 255f) + 0.5f).toInt() shl 8) or
        (b.coerceIn(0f, 255f) + 0.5f).toInt()

/** An RGB color as floats 0–255, for blending. */
internal class Rgb(val r: Float, val g: Float, val b: Float) {
    constructor(hex: Int) : this(((hex shr 16) and 0xFF).toFloat(), ((hex shr 8) and 0xFF).toFloat(), (hex and 0xFF).toFloat())

    fun mix(o: Rgb, t: Float) = Rgb(r + (o.r - r) * t, g + (o.g - g) * t, b + (o.b - b) * t)
    fun times(f: Float) = Rgb(r * f, g * f, b * f)
}

/** A sky gradient: zenith, middle and horizon. */
internal class SkyStops(val top: Rgb, val mid: Rgb, val horizon: Rgb) {
    constructor(top: Int, mid: Int, horizon: Int) : this(Rgb(top), Rgb(mid), Rgb(horizon))

    fun mix(o: SkyStops, t: Float) = SkyStops(top.mix(o.top, t), mid.mix(o.mid, t), horizon.mix(o.horizon, t))
    fun times(f: Float) = SkyStops(top.times(f), mid.times(f), horizon.times(f))
}

private val ClearDay = SkyStops(0x1C57AB, 0x3E85D0, 0x9FCBF0)
private val Dawn = SkyStops(0x27356A, 0x8D7BAA, 0xF6B98E)
private val Dusk = SkyStops(0x1B2453, 0x7B4E88, 0xF28B5C)
private val ClearNight = SkyStops(0x02050D, 0x081226, 0x172548)

private val OvercastDay = SkyStops(0x66727F, 0x8D98A4, 0xB3BBC3)
private val RainDay = SkyStops(0x3B4652, 0x55626E, 0x75818B)
private val StormDay = SkyStops(0x1D222F, 0x313848, 0x4A5161)
private val SnowDay = SkyStops(0x8492A3, 0xA9B5C2, 0xD3DAE1)
private val FogDay = SkyStops(0x97A0A8, 0xB3BAC0, 0xCCD1D5)

/** Blends night → twilight → day along [light]. */
private fun <T> alongLight(light: Float, night: T, twilight: T, day: T, mix: T.(T, Float) -> T): T =
    if (light < 0.5f) night.mix(twilight, light / 0.5f) else twilight.mix(day, (light - 0.5f) / 0.5f)

private fun greyStops(c: Condition): SkyStops? = when (c) {
    Condition.Overcast, Condition.MostlyCloudy -> OvercastDay
    Condition.Drizzle, Condition.Rain, Condition.HeavyRain -> RainDay
    Condition.Thunderstorm -> StormDay
    Condition.Snow -> SnowDay
    Condition.Fog -> FogDay
    else -> null
}

/** How far the sky is greyed out from clear blue. */
private fun greyness(s: Scene) = when (s.condition) {
    Condition.Clear, Condition.MostlyClear -> 0f
    Condition.PartlyCloudy -> 0.12f
    Condition.MostlyCloudy -> 0.45f
    else -> 1f
}

internal fun skyStops(s: Scene): SkyStops {
    val twilight = if (s.morning) Dawn else Dusk
    val clear = alongLight(s.light, ClearNight, twilight, ClearDay) { o, t -> mix(o, t) }
    val grey = greyStops(s.condition) ?: return clear
    // Grey skies by night are nearly black with a little blue; at twilight they pick up some warmth.
    val greyNight = grey.times(0.13f).mix(ClearNight, 0.35f)
    val greyTwilight = grey.times(0.6f).mix(twilight, 0.3f)
    val g = alongLight(s.light, greyNight, greyTwilight, grey) { o, t -> mix(o, t) }
    return clear.mix(g, greyness(s))
}

/** Cloud colors: where light catches them, and their shaded undersides. */
internal class CloudColors(val lit: Rgb, val shade: Rgb)

internal fun cloudColors(s: Scene, heavy: Boolean): CloudColors {
    val warm = if (s.morning) Rgb(0xFFCFA8) else Rgb(0xFFAE80)
    val (dayLit, dayShade) = when (s.condition) {
        Condition.Thunderstorm -> Rgb(0x5E6776) to Rgb(0x222936)
        Condition.Drizzle, Condition.Rain, Condition.HeavyRain -> Rgb(0x939EA9) to Rgb(0x4A5560)
        Condition.Snow -> Rgb(0xE6EAEF) to Rgb(0xA4AEB9)
        Condition.Fog -> Rgb(0xDADFE3) to Rgb(0xAEB6BD)
        Condition.Overcast -> Rgb(0xC9D0D7) to Rgb(0x7D8893)
        else -> if (heavy) Rgb(0xF2F5F8) to Rgb(0x7E8B9B) else Rgb(0xFFFFFF) to Rgb(0x8796A8)
    }
    val twilightLit = dayLit.times(0.75f).mix(warm, 0.55f)
    val twilightShade = dayShade.times(0.55f).mix(Rgb(0x5E5679), 0.4f)
    // By night, clouds are faintly moonlit from above and almost black below.
    val nightLit = dayLit.times(0.24f).mix(Rgb(0x56637A), 0.35f)
    val nightShade = dayShade.times(0.12f).mix(Rgb(0x10151E), 0.5f)
    return CloudColors(
        alongLight(s.light, nightLit, twilightLit, dayLit) { o, t -> mix(o, t) },
        alongLight(s.light, nightShade, twilightShade, dayShade) { o, t -> mix(o, t) },
    )
}

// ---------------------------------------------------------------------------------------------
// The sky

/** Where the sun or moon sits on screen, as fractions of the width and height. */
internal fun lightPosition(s: Scene): Pair<Float, Float> {
    val x = 0.1f + 0.8f * s.arc
    val y = 0.92f - 0.78f * sin(PI * s.arc).toFloat()
    return x to y
}

/**
 * The sky as ARGB pixels: the gradient, the glow around the sun (or the moon and its halo by
 * night), dithered so the long gentle gradients don't band.
 */
fun renderSky(s: Scene, w: Int, h: Int, seed: Int = 7): IntArray {
    val stops = skyStops(s)
    val px = IntArray(w * h)
    val rnd = Random(seed)
    val (lx, ly) = lightPosition(s)
    val sunX = lx * w
    val sunY = ly * h
    val clearness = 1f - greyness(s)
    val byDay = s.light >= 0.5f
    // The sun's glow is warmer and wider low in the sky.
    val low = 1f - (s.sunHeight.coerceIn(0f, 1f))
    val glow = if (byDay || s.light > 0.3f) {
        if (s.morning) Rgb(0xFFE2B8) else Rgb(0xFFD3A0)
    } else {
        Rgb(0xBFD3FF)
    }
    val glowColor = Rgb(0xFFF6E4).mix(glow, if (byDay) low else 1f)
    val sunVisible = byDay || s.light > 0.3f
    // Clouded skies still brighten where the sun is behind them, just more broadly.
    val glowStrength = if (sunVisible) (0.25f + 0.75f * clearness) * (0.55f + 0.45f * low) else 0.55f * clearness
    val wideR = (if (sunVisible) 0.55f + 0.25f * low else 0.32f) * h
    val coreR = (if (sunVisible) 0.07f else 0.035f) * h
    val discR = if (sunVisible) 0.022f * h else 0.026f * h
    val moonOrSun = clearness > 0.6f

    // The control point that makes the curve pass through the middle stop.
    val ctrl = Rgb(
        2f * stops.mid.r - (stops.top.r + stops.horizon.r) / 2f,
        2f * stops.mid.g - (stops.top.g + stops.horizon.g) / 2f,
        2f * stops.mid.b - (stops.top.b + stops.horizon.b) / 2f,
    )
    for (y in 0 until h) {
        val v = y / (h - 1f)
        // One smooth curve through the three stops (the middle a bit below halfway). Easing into and
        // out of the middle stop separately would flatten there and show as a line across the sky.
        val t = v.toDouble().pow(MidCurve).toFloat()
        val a = (1f - t) * (1f - t)
        val b = 2f * (1f - t) * t
        val c = t * t
        val base = Rgb(
            stops.top.r * a + ctrl.r * b + stops.horizon.r * c,
            stops.top.g * a + ctrl.g * b + stops.horizon.g * c,
            stops.top.b * a + ctrl.b * b + stops.horizon.b * c,
        )
        for (x in 0 until w) {
            var r = base.r
            var g = base.g
            var b = base.b
            val dx = x - sunX
            val dy = (y - sunY) * 1.15f
            val d2 = dx * dx + dy * dy
            if (glowStrength > 0f) {
                val wide = exp(-d2 / (wideR * wideR)) * 0.55f
                val core = exp(-d2 / (coreR * coreR)) * 0.6f
                val k = (wide + core) * glowStrength
                r += (glowColor.r - r) * k
                g += (glowColor.g - g) * k
                b += (glowColor.b - b) * k
            }
            if (moonOrSun) {
                val d = sqrt(d2)
                if (d < discR + 1.5f) {
                    // A soft-edged disc; the moon is a cool white, the sun nearly pure light.
                    val edge = ((discR + 1.5f - d) / 3f).coerceIn(0f, 1f)
                    // The moon is a touch darker toward its rim, with a few faint grey seas.
                    val disc = if (sunVisible) {
                        Rgb(0xFFFDF4)
                    } else {
                        val rim = (d / discR).coerceIn(0f, 1f)
                        val sea = moonSeas(dx / discR, dy / 1.15f / discR)
                        Rgb(0xF6F8FC).mix(Rgb(0xB9C0CC), 0.25f * rim * rim + 0.3f * sea)
                    }
                    val a = edge * clearness
                    r += (disc.r - r) * a
                    g += (disc.g - g) * a
                    b += (disc.b - b) * a
                }
            }
            // Triangular dither, about one step either way.
            val n = rnd.nextFloat() - rnd.nextFloat()
            px[y * w + x] = argb(r + n, g + n, b + n)
        }
    }
    return px
}

/** Puts the curve's halfway point (the middle stop) at 55% of the way down. */
private val MidCurve = kotlin.math.ln(0.5) / kotlin.math.ln(0.55)

/** Faint darker patches across the moon's face, in -1..1 coordinates on its disc. */
private fun moonSeas(x: Float, y: Float): Float {
    fun blob(cx: Float, cy: Float, r: Float) = exp(-((x - cx) * (x - cx) + (y - cy) * (y - cy)) / (r * r))
    return (blob(-0.3f, -0.25f, 0.32f) + blob(0.15f, -0.1f, 0.25f) + 0.8f * blob(-0.1f, 0.35f, 0.22f) + 0.6f * blob(0.4f, 0.3f, 0.2f)).coerceAtMost(1f)
}

private fun smooth(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

private fun smoothstep(a: Float, b: Float, x: Float) = smooth((x - a) / (b - a))

// ---------------------------------------------------------------------------------------------
// Clouds

/** One cloud layer: its image's size and look, and where and how fast it drifts on screen. */
data class CloudLayerSpec(
    val seed: Int,
    val width: Int,
    val height: Int,
    /** Noise cells across the image: fewer means bigger clouds. */
    val cells: Int,
    /** Vertical squash: below 1, clouds are wider than they are tall. */
    val stretch: Float,
    /** Share of the layer that's cloud. */
    val coverage: Float,
    /** The band of the screen the layer fills, as fractions of the height (may run past the edges). */
    val top: Float,
    val bottom: Float,
    /** Drift speed in pixels a second on a 1080-line screen, before wind. */
    val speed: Float,
    val opacity: Float,
    /** Ragged, low-contrast fog rather than billowing cloud. */
    val fog: Boolean = false,
    /** Darker, heavier cloud (overcast ceilings and rain clouds). */
    val heavy: Boolean = false,
    /** Fades out toward the top or bottom of the band; clouds read as farther away up high. */
    val profile: Profile = Profile.Middle,
) {
    enum class Profile { Middle, Ceiling, Ground }
}

/** The cloud layers for [s], far to near. */
fun cloudLayers(s: Scene): List<CloudLayerSpec> {
    val c = s.cloudCover
    val layers = mutableListOf<CloudLayerSpec>()
    val heavyWeather = s.condition.isWet || s.condition == Condition.Snow || s.condition == Condition.Overcast
    if (s.condition == Condition.Fog) {
        layers += CloudLayerSpec(11, 1280, 300, 3, 0.35f, 0.75f, -0.05f, 0.55f, 4f, 0.55f, profile = CloudLayerSpec.Profile.Ceiling)
        layers += CloudLayerSpec(12, 1280, 320, 3, 0.28f, 0.9f, 0.35f, 1.05f, 7f, 0.6f, fog = true, profile = CloudLayerSpec.Profile.Ground)
        layers += CloudLayerSpec(13, 1280, 320, 2, 0.25f, 0.95f, 0.55f, 1.1f, 12f, 0.65f, fog = true, profile = CloudLayerSpec.Profile.Ground)
        return layers
    }
    if (c < 0.03f) return layers
    // High, thin, slow streaks.
    layers += CloudLayerSpec(21, 1280, 280, 7, 0.32f, (c * 0.55f).coerceAtMost(0.5f), -0.02f, 0.42f, 3f, if (heavyWeather) 0.5f else 0.42f)
    if (heavyWeather) {
        // A ceiling of cloud over the whole sky, then lumpier, darker cloud and ragged scud below it.
        layers += CloudLayerSpec(31, 1280, 380, 3, 0.5f, 0.97f, -0.1f, 0.88f, 5f, 1f, heavy = true, profile = CloudLayerSpec.Profile.Ceiling)
        layers += CloudLayerSpec(41, 1280, 360, 6, 0.6f, 0.6f, 0.0f, 0.78f, 8f, 0.9f, heavy = true)
        layers += CloudLayerSpec(51, 1280, 400, 5, 0.55f, 0.42f, 0.3f, 1.05f, 14f, 0.85f, heavy = true)
        return layers
    }
    if (c >= 0.1f) {
        layers += CloudLayerSpec(41, 1280, 340, 7, 0.62f, c * 0.8f, 0.05f, 0.62f, 7f, 0.92f)
    }
    if (c >= 0.3f) {
        layers += CloudLayerSpec(51, 1280, 400, 5, 0.6f, c * 0.9f, 0.16f, 0.9f, 13f, 1f)
    }
    return layers
}

/** How fast [spec] drifts with the scene's wind, in pixels a second at 1080 lines; negative is leftward. */
fun drift(spec: CloudLayerSpec, s: Scene): Float {
    val factor = 0.6f + kotlin.math.abs(s.windKmh) / 15f
    return spec.speed * factor * (if (s.windKmh < 0f) -1f else 1f)
}

/**
 * Renders a cloud layer as ARGB pixels (unpremultiplied), wrapping seamlessly left to right.
 *
 * The shapes are large, warped noise for the cloud masses with billowing detail eroding their
 * edges, cut to the layer's coverage. Each pixel is then lit the way real clouds are: by how much
 * cloud lies between it and the sun, so thin edges facing the light glow and thick cores and
 * undersides fall into shade.
 */
fun renderClouds(spec: CloudLayerSpec, s: Scene): IntArray {
    val w = spec.width
    val h = spec.height
    val noise = Noise(spec.seed)
    val cellsY = spec.cells * h.toFloat() / w / spec.stretch
    val puffCellsY = spec.cells * 3 * h.toFloat() / w
    val field = FloatArray(w * h)
    // Heavy cloud is a softer, lumpier sheet; fair-weather cloud billows.
    val detail = when {
        spec.fog -> 0.08f
        spec.heavy -> 0.22f
        else -> 0.42f
    }
    for (y in 0 until h) {
        val v = y / h.toFloat()
        val envelope = when (spec.profile) {
            // Thickest in the middle of the band, thinning to nothing at its edges.
            CloudLayerSpec.Profile.Middle -> smoothstep(0f, 0.3f, v) * smoothstep(1f, 0.6f, v)
            // Solid along the top, breaking up below.
            CloudLayerSpec.Profile.Ceiling -> smoothstep(1f, 0.3f, v)
            CloudLayerSpec.Profile.Ground -> smoothstep(0f, 0.5f, v)
        }
        for (x in 0 until w) {
            val u = x / w.toFloat()
            // Warping the coordinates turns plain noise into rolling, billowing shapes.
            val wu = noise.fbm(u, v + 3.7f, spec.cells, cellsY, 2) * 0.22f / spec.cells
            val wv = noise.fbm(u, v + 9.1f, spec.cells, cellsY, 2) * 0.18f / cellsY
            val mass = noise.fbm(u + wu, v + wv, spec.cells, cellsY, 3)
            // The puffs are round (not squashed like the masses), so edges look like cauliflower, not brush strokes.
            val puffs = if (spec.fog) 0.5f else noise.turbulence(u + wu * 0.5f, v, spec.cells * 3, puffCellsY, 4)
            field[y * w + x] = mass * 0.5f + 0.5f + (puffs - 0.5f) * detail - (1f - envelope) * 1.2f
        }
    }

    // The threshold that leaves [coverage] of the band as cloud, from the in-band (upper) values.
    val sample = FloatArray(4096) { field[(it * 7919L % field.size).toInt()] }.also { it.sort() }
    val upper = sample.copyOfRange(sample.size / 4, sample.size)
    val mean = upper.average().toFloat()
    var variance = 0f
    for (x in upper) variance += (x - mean) * (x - mean)
    val std = sqrt(variance / upper.size).coerceAtLeast(0.01f)
    val threshold = upper[((1f - spec.coverage.coerceIn(0.01f, 0.99f)) * (upper.size - 1)).toInt()]
    val spread = std * (if (spec.fog) 3f else 1.5f)

    // How much cloud there is at each pixel, 0 (sky) to 1 (thick).
    val thick = FloatArray(w * h)
    for (i in field.indices) thick[i] = smoothstep(threshold - std * 0.2f, threshold + spread, field[i])

    val colors = cloudColors(s, spec.heavy)
    // Toward the light: the sun's side, and steeper the higher it is.
    val (sunU, _) = lightPosition(s)
    val side = ((sunU - 0.5f) * 2f).coerceIn(-1f, 1f) * (0.3f + 0.7f * (1f - s.sunHeight.coerceIn(0f, 1f)))
    val len = sqrt(side * side + 1f)
    val step = h * 0.028f
    val sx = side / len * step
    val sy = -1f / len * step
    val steps = 8
    val absorb = if (spec.heavy) 0.42f else 0.6f

    val px = IntArray(w * h)
    for (y in 0 until h) {
        val v = y / h.toFloat()
        for (x in 0 until w) {
            val i = y * w + x
            val t = thick[i]
            if (t <= 0.003f) continue
            // Cloud between here and the light.
            var depth = 0f
            for (j in 1..steps) {
                val yy = (y + sy * j).toInt()
                if (yy < 0) break
                val xx = (((x + sx * j).toInt() % w) + w) % w
                depth += thick[yy.coerceAtMost(h - 1) * w + xx]
            }
            val sun = exp(-depth * absorb)
            // Some light always reaches clouds from the rest of the sky, a little more up high.
            val sky = 0.32f + 0.18f * (1f - v)
            var light = (sky * 0.6f + sun * 0.85f).coerceIn(0f, 1f)
            if (spec.heavy) light = 0.25f + 0.62f * light
            if (spec.fog) light = 0.72f + 0.28f * sun
            val c = colors.shade.mix(colors.lit, light)
            // Thin cloud is see-through; thick cloud is opaque.
            // Faded out at the band's edges, so a layer never ends in a straight line.
            val edges = smoothstep(0f, 0.1f, v) * smoothstep(1f, 0.88f, v)
            val alpha = (1f - exp(-t * 3.2f)) / 0.959f * spec.opacity * edges
            px[i] = argb(c.r, c.g, c.b, alpha)
        }
    }
    return px
}

/** Gradient noise that repeats every [period] cells left to right. */
internal class Noise(seed: Int) {
    private val perm = IntArray(512)
    private val gx = FloatArray(16)
    private val gy = FloatArray(16)

    init {
        val p = (0 until 256).toMutableList()
        p.shuffle(Random(seed))
        for (i in 0 until 512) perm[i] = p[i and 255]
        for (i in 0 until 16) {
            val a = (i / 16.0 * 2 * PI)
            gx[i] = cos(a).toFloat()
            gy[i] = sin(a).toFloat()
        }
    }

    fun noise(x: Float, y: Float, period: Int): Float {
        val x0 = floor(x)
        val y0 = floor(y)
        val fx = x - x0
        val fy = y - y0
        val xi = ((x0.toInt() % period) + period) % period
        val xi1 = (xi + 1) % period
        val yi = y0.toInt() and 255
        val yi1 = (yi + 1) and 255
        fun dot(ix: Int, iy: Int, dx: Float, dy: Float): Float {
            val g = perm[perm[ix and 255] + iy] and 15
            return gx[g] * dx + gy[g] * dy
        }
        val u = fade(fx)
        val v = fade(fy)
        val a = lerp(dot(xi, yi, fx, fy), dot(xi1, yi, fx - 1f, fy), u)
        val b = lerp(dot(xi, yi1, fx, fy - 1f), dot(xi1, yi1, fx - 1f, fy - 1f), u)
        return lerp(a, b, v) * 1.41f
    }

    /** Fractal noise over [octaves], about -1 to 1; [u] runs 0–1 across one horizontal repeat. */
    fun fbm(u: Float, v: Float, cellsX: Int, cellsY: Float, octaves: Int): Float {
        var sum = 0f
        var amp = 0.5f
        var norm = 0f
        var f = 1
        for (o in 0 until octaves) {
            val period = cellsX * f
            sum += noise(u * period, v * cellsY * f + o * 31.7f, period) * amp
            norm += amp
            amp *= 0.5f
            f *= 2
        }
        return sum / norm * 1.6f
    }

    /** Billowing noise: folded octaves, about 0 to 1, with rounded puffs and creases between them. */
    fun turbulence(u: Float, v: Float, cellsX: Int, cellsY: Float, octaves: Int): Float {
        var sum = 0f
        var amp = 0.5f
        var norm = 0f
        var f = 1
        for (o in 0 until octaves) {
            val period = cellsX * f
            sum += kotlin.math.abs(noise(u * period, v * cellsY * f + o * 17.9f, period)) * amp
            norm += amp
            amp *= 0.5f
            f *= 2
        }
        return (sum / norm * 2.2f).coerceIn(0f, 1f)
    }

    private fun fade(t: Float) = t * t * t * (t * (t * 6f - 15f) + 10f)
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}

// ---------------------------------------------------------------------------------------------
// Stars

/** Stars as x, y (fractions of the screen), size in px at 1080 lines, brightness, twinkle phase. */
fun stars(count: Int = 260, seed: Int = 3): FloatArray {
    val r = Random(seed)
    val out = FloatArray(count * 5)
    for (i in 0 until count) {
        out[i * 5] = r.nextFloat()
        // Denser and brighter overhead, fading toward the horizon's haze.
        out[i * 5 + 1] = (r.nextFloat() * r.nextFloat() * 0.85f)
        out[i * 5 + 2] = if (r.nextFloat() < 0.12f) 2.4f else 1.4f + r.nextFloat() * 0.6f
        out[i * 5 + 3] = 0.25f + r.nextFloat() * r.nextFloat() * 0.75f
        out[i * 5 + 4] = r.nextFloat() * 6.28f
    }
    return out
}

/** How visible the stars are: only on clear-ish nights. */
fun starVisibility(s: Scene): Float = (1f - s.light / 0.45f).coerceIn(0f, 1f) * (1f - s.cloudCover * 1.1f).coerceIn(0f, 1f)
