package com.tk.toolkit.tv.launcher.weather

import android.content.Context
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** The weather where the TV is, as of [fetchedAt]. Temperatures are in the unit [fahrenheit] says. */
data class WeatherReport(
    val place: String?,
    val temperature: Float,
    val high: Float,
    val low: Float,
    /** WMO weather code. */
    val code: Int,
    /** Percent. */
    val cloudCover: Int,
    val windKmh: Float,
    val windFromDegrees: Float,
    val precipitationMm: Float,
    val sunrise: Long,
    val sunset: Long,
    val fahrenheit: Boolean,
    val fetchedAt: Long,
) {
    /** The scene at [now], with today's sunrise and sunset carried over to whichever day [now] is. */
    fun sceneAt(now: Long): Scene {
        val day = 24 * 60 * 60 * 1000L
        val noon = (sunrise + sunset) / 2
        val shift = Math.round((now - noon).toDouble() / day) * day
        return Scene.of(code, cloudCover, now, sunrise + shift, sunset + shift, windKmh, windFromDegrees, precipitationMm)
    }

    /** For under the clock: "Partly cloudy, 24°  ·  H 29°  L 19°  ·  Bengaluru". */
    fun caption(now: Long): String {
        val parts = mutableListOf("${sceneAt(now).condition.label}, ${temperature.roundToInt()}°")
        parts += "H ${high.roundToInt()}°  L ${low.roundToInt()}°"
        place?.let { parts += it }
        return parts.joinToString("  ·  ")
    }

    internal fun toJson() = JSONObject()
        .put("place", place).put("temperature", temperature.toDouble()).put("high", high.toDouble())
        .put("low", low.toDouble()).put("code", code).put("cloudCover", cloudCover).put("wind", windKmh.toDouble())
        .put("windFrom", windFromDegrees.toDouble()).put("precipitation", precipitationMm.toDouble())
        .put("sunrise", sunrise).put("sunset", sunset).put("fahrenheit", fahrenheit).put("fetchedAt", fetchedAt)

    internal companion object {
        fun fromJson(o: JSONObject) = WeatherReport(
            place = o.optString("place").takeIf { it.isNotEmpty() && it != "null" },
            temperature = o.getDouble("temperature").toFloat(),
            high = o.getDouble("high").toFloat(),
            low = o.getDouble("low").toFloat(),
            code = o.getInt("code"),
            cloudCover = o.getInt("cloudCover"),
            windKmh = o.getDouble("wind").toFloat(),
            windFromDegrees = o.getDouble("windFrom").toFloat(),
            precipitationMm = o.getDouble("precipitation").toFloat(),
            sunrise = o.getLong("sunrise"),
            sunset = o.getLong("sunset"),
            fahrenheit = o.getBoolean("fahrenheit"),
            fetchedAt = o.getLong("fetchedAt"),
        )
    }
}

/**
 * Gets the weather from Open-Meteo (free, no key) for where the TV is, which it works out from its
 * internet address (ipwho.is, or ipapi.co as a fallback; also free, no key). Both are cached:
 * the place for half a day, the last report so the scene is right the moment the launcher opens.
 */
class WeatherRepository(context: Context) {
    private val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)

    /** The last report, if it's recent enough to still describe the sky. */
    fun cached(): WeatherReport? = runCatching {
        prefs.getString(KEY_REPORT, null)?.let { WeatherReport.fromJson(JSONObject(it)) }
            ?.takeIf { System.currentTimeMillis() - it.fetchedAt < STALE_AFTER }
    }.getOrNull()

    /** Fetches the current weather; null when offline or the services fail. */
    suspend fun refresh(): WeatherReport? = withContext(Dispatchers.IO) {
        runCatching {
            val place = place() ?: return@runCatching null
            fetch(place).also { prefs.edit().putString(KEY_REPORT, it.toJson().toString()).apply() }
        }.onFailure { Log.w(TAG, "Couldn't get the weather", it) }.getOrNull()
    }

    private class Place(val latitude: Double, val longitude: Double, val name: String?, val country: String?)

    private fun place(): Place? {
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_PLACE_TIME, 0) < PLACE_FOR && prefs.contains(KEY_COUNTRY)) {
            return saved()
        }
        val found = runCatching {
            val o = JSONObject(get("https://ipwho.is/"))
            check(o.optBoolean("success", false))
            Place(o.getDouble("latitude"), o.getDouble("longitude"), o.optString("city").ifEmpty { null }, o.optString("country_code").ifEmpty { null })
        }.recoverCatching {
            val o = JSONObject(get("https://ipapi.co/json/"))
            Place(o.getDouble("latitude"), o.getDouble("longitude"), o.optString("city").ifEmpty { null }, o.optString("country_code").ifEmpty { null })
        }.onFailure { Log.w(TAG, "Couldn't find where the TV is", it) }.getOrNull()
        if (found != null) {
            prefs.edit()
                .putString(KEY_LAT, found.latitude.toString())
                .putString(KEY_LON, found.longitude.toString())
                .putString(KEY_PLACE, found.name)
                .putString(KEY_COUNTRY, found.country)
                .putLong(KEY_PLACE_TIME, now)
                .apply()
            return found
        }
        // Somewhere we found before is better than nowhere.
        return if (prefs.contains(KEY_LAT)) saved() else null
    }

    private fun saved() = Place(
        prefs.getString(KEY_LAT, null)!!.toDouble(),
        prefs.getString(KEY_LON, null)!!.toDouble(),
        prefs.getString(KEY_PLACE, null),
        prefs.getString(KEY_COUNTRY, null),
    )

    private fun fetch(place: Place): WeatherReport {
        // The country the TV is in, not the one its settings say (TVs are often set to the US).
        val fahrenheit = (place.country ?: Locale.getDefault().country) in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${place.latitude}&longitude=${place.longitude}" +
            "&current=temperature_2m,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,precipitation" +
            "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset" +
            "&timezone=auto&timeformat=unixtime&forecast_days=1" +
            (if (fahrenheit) "&temperature_unit=fahrenheit" else "")
        val o = JSONObject(get(url))
        val current = o.getJSONObject("current")
        val daily = o.getJSONObject("daily")
        return WeatherReport(
            place = place.name,
            temperature = current.getDouble("temperature_2m").toFloat(),
            high = daily.getJSONArray("temperature_2m_max").getDouble(0).toFloat(),
            low = daily.getJSONArray("temperature_2m_min").getDouble(0).toFloat(),
            code = current.getInt("weather_code"),
            cloudCover = current.optInt("cloud_cover", 50),
            windKmh = current.optDouble("wind_speed_10m", 8.0).toFloat(),
            windFromDegrees = current.optDouble("wind_direction_10m", 270.0).toFloat(),
            precipitationMm = current.optDouble("precipitation", 0.0).toFloat(),
            sunrise = daily.getJSONArray("sunrise").getLong(0) * 1000,
            sunset = daily.getJSONArray("sunset").getLong(0) * 1000,
            fahrenheit = fahrenheit,
            fetchedAt = System.currentTimeMillis(),
        )
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("User-Agent", "Toolkit-TV-Launcher")
        try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode} from $url" }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "Weather"
        const val KEY_REPORT = "report"
        const val KEY_LAT = "lat"
        const val KEY_LON = "lon"
        const val KEY_PLACE = "place"
        const val KEY_COUNTRY = "country"
        const val KEY_PLACE_TIME = "placeTime"
        const val PLACE_FOR = 12 * 60 * 60 * 1000L
        const val STALE_AFTER = 3 * 60 * 60 * 1000L
    }
}
