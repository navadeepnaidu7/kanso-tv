package com.tk.toolkit.tv.launcher.weather

import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Rain or snow, in three depths: far drops are small, faint and slow, near ones big and fast,
 * which gives the fall its depth. Positions are kept as fractions of the screen; [step] moves
 * them and writes each depth's drawing coordinates (in pixels) into [Depth.out]: line segments
 * (x0, y0, x1, y1) for rain, points (x, y) for snow.
 */
class Precipitation(scene: Scene, seed: Int = 5) {
    val snow = scene.condition == Condition.Snow
    private val drizzle = scene.condition == Condition.Drizzle
    private val wind = scene.windKmh
    private val random = Random(seed)

    class Depth(
        val count: Int,
        /** Fall speed, in pixels a second at 1080 lines. */
        val speed: Float,
        /** Streak length (rain) or flake diameter (snow), in pixels at 1080 lines. */
        val size: Float,
        val strokeWidth: Float,
        val alpha: Float,
        /** Side-to-side drift of snowflakes, in pixels at 1080 lines. */
        val sway: Float,
    ) {
        internal val x = FloatArray(count)
        internal val y = FloatArray(count)
        internal val phase = FloatArray(count)
        internal val jitter = FloatArray(count)
        val out = FloatArray(count * 4)

        /** How many floats of [out] are in use. */
        var used = 0
            internal set
    }

    val depths: List<Depth> = run {
        val amount = scene.intensity.coerceIn(0f, 1f)
        if (snow) {
            val n = (70 + 260 * amount).toInt()
            listOf(
                Depth((n * 0.5f).toInt(), 38f, 2.6f, 2.6f, 0.55f, 9f),
                Depth((n * 0.32f).toInt(), 62f, 4.2f, 4.2f, 0.75f, 15f),
                Depth((n * 0.18f).toInt(), 96f, 6.5f, 6.5f, 0.9f, 24f),
            )
        } else if (amount > 0f) {
            val n = (80 + 320 * amount).toInt()
            val len = if (drizzle) 0.5f else 1f
            val fast = if (drizzle) 0.65f else 1f
            listOf(
                Depth((n * 0.5f).toInt(), 1300f * fast, 18f * len, 1.1f, 0.2f, 0f),
                Depth((n * 0.32f).toInt(), 1850f * fast, 30f * len, 1.4f, 0.3f, 0f),
                Depth((n * 0.18f).toInt(), 2500f * fast, 46f * len, 2f, 0.4f, 0f),
            )
        } else {
            emptyList()
        }
    }

    init {
        for (d in depths) {
            for (i in 0 until d.count) {
                d.x[i] = random.nextFloat()
                d.y[i] = random.nextFloat()
                d.phase[i] = random.nextFloat() * 6.28f
                d.jitter[i] = 0.8f + random.nextFloat() * 0.4f
            }
        }
    }

    /** Moves everything on by [dt] seconds and fills each depth's [Depth.out] for a [w]×[h] screen. */
    fun step(dt: Float, time: Float, w: Float, h: Float) {
        val scale = h / 1080f
        // The wind slants rain and carries snow sideways (as a share of the fall speed).
        val slant = (wind / 40f).coerceIn(-0.45f, 0.45f)
        for (d in depths) {
            val fall = d.speed * scale / h
            var o = 0
            for (i in 0 until d.count) {
                val v = fall * d.jitter[i]
                d.y[i] += v * dt
                d.x[i] += v * slant * (h / w) * dt
                if (d.y[i] > 1.05f) {
                    d.y[i] -= 1.1f
                    d.x[i] = random.nextFloat()
                }
                if (d.x[i] < -0.05f) d.x[i] += 1.1f else if (d.x[i] > 1.05f) d.x[i] -= 1.1f
                val px = d.x[i] * w
                val py = d.y[i] * h
                if (snow) {
                    d.out[o++] = px + sin(time * 0.9f * d.jitter[i] + d.phase[i]) * d.sway * scale
                    d.out[o++] = py
                } else {
                    val len = d.size * scale * d.jitter[i]
                    d.out[o++] = px
                    d.out[o++] = py
                    d.out[o++] = px + slant * len
                    d.out[o++] = py + len
                }
            }
            d.used = o
        }
    }
}

/** Thunderstorm lightning: a bright double flicker every few seconds, as a 0–1 brightness. */
class Lightning(seed: Int = 9) {
    private val random = Random(seed)
    private var strike = 2.5f
    private var strength = 1f

    fun brightness(time: Float): Float {
        if (time > strike + 1.5f) {
            strike = time + 4f + random.nextFloat() * 9f
            strength = 0.55f + random.nextFloat() * 0.45f
        }
        val d = time - strike
        if (d < 0f) return 0f
        val first = exp(-d / 0.07f) * 0.75f
        val second = if (d > 0.17f) exp(-(d - 0.17f) / 0.12f) * 0.55f else 0f
        return (first + second).coerceAtMost(1f) * strength
    }
}
