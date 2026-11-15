package com.moadrive.pleos.safety

import com.moadrive.pleos.navigation.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

interface WeatherProvider {
    suspend fun forecast(center: GeoPoint): AreaForecast
}

/** Development endpoint. Replace with a licensed provider/endpoint for commercial distribution. */
class OpenMeteoWeatherProvider(
    private val endpoint: String = "https://api.open-meteo.com/v1/forecast",
) : WeatherProvider {
    override suspend fun forecast(center: GeoPoint): AreaForecast = withTimeout(20_000L) {
        withContext(Dispatchers.IO) {
            val points = weatherSamplePoints(center)
            val query = "latitude=${points.joinToString(",") { it.point.latitude.toString() }}" +
                "&longitude=${points.joinToString(",") { it.point.longitude.toString() }}" +
                "&current=temperature_2m,weather_code,precipitation,rain,snowfall,wind_speed_10m,visibility,is_day" +
                "&hourly=temperature_2m,weather_code,precipitation_probability,precipitation,snowfall,wind_speed_10m,wind_gusts_10m,visibility" +
                "&forecast_hours=6&wind_speed_unit=ms&timezone=Asia%2FSeoul&timeformat=unixtime"
            val connection = URL("$endpoint?$query").openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("Accept", "application/json")
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Weather HTTP unavailable")
                val response = connection.inputStream.bufferedReader().use { reader ->
                    // Bound malformed or unexpectedly large responses without logging location payloads.
                    val text = StringBuilder()
                    val buffer = CharArray(4_096)
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        if (text.length + count > 1_000_000) throw IOException("Weather response too large")
                        text.append(buffer, 0, count)
                    }
                    text.toString()
                }
                val array = JSONArray(response)
                if (array.length() != points.size) throw IOException("Incomplete weather region response")
                val samples = points.mapIndexed { index, sample ->
                    val item = array.getJSONObject(index)
                    val current = item.optJSONObject("current")?.let(::currentReading)
                    val hourly = item.optJSONObject("hourly")
                    val times = hourly?.optJSONArray("time")
                    val hours = if (hourly == null || times == null) emptyList() else (0 until minOf(times.length(), 6)).mapNotNull { hour ->
                        epochMillis(times.optDouble(hour, Double.NaN))?.let { time ->
                            WeatherReading(time,
                                validWeatherCode(hourly.number("weather_code", hour)),
                                hourly.number("temperature_2m", hour)?.takeIf { it in -100.0..65.0 },
                                hourly.number("precipitation", hour)?.takeIf { it in 0.0..1000.0 },
                                hourly.number("snowfall", hour)?.takeIf { it in 0.0..1000.0 },
                                hourly.number("wind_speed_10m", hour)?.takeIf { it in 0.0..150.0 },
                                hourly.number("wind_gusts_10m", hour)?.takeIf { it in 0.0..150.0 },
                                hourly.number("visibility", hour)?.takeIf { it in 0.0..1_000_000.0 },
                                hourly.number("precipitation_probability", hour)?.takeIf { it in 0.0..100.0 })
                        }
                    }
                    WeatherSample(sample.label, sample.point, current, hours)
                }
                AreaForecast(center, System.currentTimeMillis(), samples)
            } finally { connection.disconnect() }
        }
    }

    private fun currentReading(item: JSONObject): WeatherReading? {
        val time = epochMillis(item.optDouble("time", Double.NaN)) ?: return null
        return WeatherReading(time,
            validWeatherCode(item.number("weather_code")),
            item.number("temperature_2m")?.takeIf { it in -100.0..65.0 },
            item.number("precipitation")?.takeIf { it in 0.0..1000.0 },
            item.number("snowfall")?.takeIf { it in 0.0..1000.0 },
            item.number("wind_speed_10m")?.takeIf { it in 0.0..150.0 },
            null,
            item.number("visibility")?.takeIf { it in 0.0..1_000_000.0 },
            null)
    }
}

private fun JSONObject.number(key: String, index: Int? = null): Double? =
    (if (index == null) optDouble(key, Double.NaN) else optJSONArray(key)?.optDouble(index, Double.NaN))?.takeIf { it.isFinite() }

private fun epochMillis(seconds: Double): Long? = seconds.takeIf { it.isFinite() && it in 946684800.0..4102444800.0 }?.let { (it * 1000).toLong() }

private fun validWeatherCode(value: Double?): Int? = value?.toInt()?.takeIf { value == it.toDouble() && it in setOf(
    0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99) }
