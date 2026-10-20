package com.tk.toolkit.tv.launcher.weather

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.sin
import org.junit.Test

/**
 * Renders each kind of weather to build/weather-previews, composited the way the TV draws it, so
 * the look can be checked without a TV. Run: ./gradlew :app:testDebugUnitTest --tests '*WeatherPreview*'
 */
class WeatherPreviewTest {

    private val w = 1920
    private val h = 1080
    private val hour = 60 * 60 * 1000L
    private val sunrise = 6 * hour
    private val sunset = 18 * hour

    private fun scene(code: Int, cover: Int, hourOfDay: Float, wind: Float = 12f, rain: Float = 0f) = Scene.of(
        code = code,
        cloudCover = cover,
        now = (hourOfDay * hour).toLong(),
        sunrise = sunrise,
        sunset = sunset,
        windKmh = wind,
        windFromDegrees = 270f,
        precipitationMm = rain,
    )

    @Test
    fun renderPreviews() {
        val out = File(System.getProperty("weather.previews") ?: "build/weather-previews").apply { mkdirs() }
        val only = System.getProperty("weather.only")
        val scenes = linkedMapOf(
            "01-clear-day" to scene(0, 3, 11f),
            "02-mostly-clear-morning" to scene(1, 25, 8.5f),
            "03-partly-cloudy-day" to scene(2, 50, 13f),
            "04-mostly-cloudy-afternoon" to scene(3, 78, 15.5f),
            "05-overcast-day" to scene(3, 98, 12f),
            "06-rain-day" to scene(63, 100, 14f, rain = 2f),
            "07-thunderstorm-evening" to scene(95, 100, 19f, wind = 30f, rain = 6f),
            "08-snow-day" to scene(73, 100, 12f),
            "09-fog-morning" to scene(45, 90, 7.5f),
            "10-clear-night" to scene(0, 2, 23f),
            "11-partly-cloudy-night" to scene(2, 45, 1.5f),
            "12-clear-sunset" to scene(0, 8, 17.75f),
            "13-partly-cloudy-sunset" to scene(2, 45, 17.6f),
            "14-partly-cloudy-dawn" to scene(1, 35, 6.3f),
            "15-drizzle-day" to scene(53, 95, 10f),
            "16-rain-night" to scene(63, 100, 22f, rain = 2f),
        )
        for ((name, s) in scenes) {
            if (only != null && !name.contains(only)) continue
            val started = System.nanoTime()
            ImageIO.write(composite(s, time = 20f), "png", File(out, "$name.png"))
            println("$name: ${s.condition} cover=${s.cloudCover} light=${s.light} (${(System.nanoTime() - started) / 1_000_000} ms)")
        }
    }

    /** The scene as the TV draws it at [time] seconds: sky, cloud layers drifting, stars, rain or snow. */
    private fun composite(s: Scene, time: Float): BufferedImage {
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, w, h, renderSky(s, w, h), 0, w)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val visibleStars = starVisibility(s)
        if (visibleStars > 0f) {
            val st = stars()
            for (i in 0 until st.size / 5) {
                val a = st[i * 5 + 3] * visibleStars * (0.75f + 0.25f * sin(time * 1.3f + st[i * 5 + 4]))
                g.color = Color(1f, 1f, 1f, a.coerceIn(0f, 1f))
                val r = st[i * 5 + 2] / 2f
                g.fill(Ellipse2D.Float(st[i * 5] * w - r, st[i * 5 + 1] * h - r, r * 2, r * 2))
            }
        }

        for (spec in cloudLayers(s)) {
            val layer = BufferedImage(spec.width, spec.height, BufferedImage.TYPE_INT_ARGB)
            layer.setRGB(0, 0, spec.width, spec.height, renderClouds(spec, s), 0, spec.width)
            val bandTop = spec.top * h
            val bandH = (spec.bottom - spec.top) * h
            val scaledW = spec.width * bandH / spec.height
            var x = -((drift(spec, s) * time * h / 1080f) % scaledW + scaledW) % scaledW
            while (x < w) {
                g.drawImage(layer, x.toInt(), bandTop.toInt(), scaledW.toInt() + 1, bandH.toInt(), null)
                x += scaledW
            }
        }

        val fall = Precipitation(s)
        // Let it fall for a while so drops aren't all at their starting spots.
        repeat(40) { fall.step(1 / 25f, it / 25f, w.toFloat(), h.toFloat()) }
        for (d in fall.depths) {
            g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, d.alpha)
            g.color = if (fall.snow) Color.WHITE else Color(0.82f, 0.88f, 0.95f)
            g.stroke = BasicStroke(d.strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            var i = 0
            while (i < d.used) {
                if (fall.snow) {
                    val r = d.size / 2f
                    g.fill(Ellipse2D.Float(d.out[i] - r, d.out[i + 1] - r, r * 2, r * 2))
                    i += 2
                } else {
                    g.draw(Line2D.Float(d.out[i], d.out[i + 1], d.out[i + 2], d.out[i + 3]))
                    i += 4
                }
            }
        }
        g.dispose()
        return image
    }
}
