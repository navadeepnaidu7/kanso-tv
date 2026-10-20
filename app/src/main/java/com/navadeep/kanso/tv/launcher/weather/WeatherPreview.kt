package com.navadeep.kanso.tv.launcher.weather

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Trying the weather wallpaper in other weather or at another time of day. Not saved: the
 * launcher always comes back to the live sky when it restarts.
 */
object WeatherPreview {
    /** The weather to show instead of the real weather; null for the real thing. */
    var condition by mutableStateOf<Condition?>(null)
    var time by mutableStateOf(PreviewTime.Now)

    val active: Boolean get() = condition != null || time != PreviewTime.Now
}

/** Times of day to preview, placed around the real sunrise and sunset. */
enum class PreviewTime(val label: String) {
    Now("Now"),
    Dawn("Dawn"),
    Morning("Morning"),
    Midday("Midday"),
    Afternoon("Afternoon"),
    Sunset("Sunset"),
    Dusk("Dusk"),
    Night("Night"),
    ;

    /** The moment this time of day falls on, for a day with the given [sunrise] and [sunset]. */
    fun at(now: Long, sunrise: Long, sunset: Long): Long {
        val minute = 60_000L
        val length = sunset - sunrise
        return when (this) {
            Now -> now
            Dawn -> sunrise + 12 * minute
            Morning -> sunrise + length / 5
            Midday -> sunrise + length / 2
            Afternoon -> sunrise + length * 4 / 5
            Sunset -> sunset - 10 * minute
            Dusk -> sunset + 22 * minute
            Night -> sunset + (24 * 60 * minute - length) / 2
        }
    }
}

/** A WMO code and cloud cover (percent) that produce [this] condition. */
internal fun Condition.sample(): Pair<Int, Int> = when (this) {
    Condition.Clear -> 0 to 4
    Condition.MostlyClear -> 1 to 25
    Condition.PartlyCloudy -> 2 to 50
    Condition.MostlyCloudy -> 3 to 78
    Condition.Overcast -> 3 to 98
    Condition.Fog -> 45 to 90
    Condition.Drizzle -> 53 to 95
    Condition.Rain -> 63 to 100
    Condition.HeavyRain -> 65 to 100
    Condition.Snow -> 73 to 100
    Condition.Thunderstorm -> 95 to 100
}
