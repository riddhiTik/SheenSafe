package org.kashmir.heatguard.weather

import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONObject
import org.kashmir.heatguard.core.model.HourlyForecastPoint
import org.kashmir.heatguard.core.weather.HourlyForecastProvider

/**
 * Kashmir-specific: Open-Meteo's `timezone=auto` returns local wall-clock times for the
 * given coordinates, which for this app's deployment area is always Asia/Kolkata.
 */
class ApiHourlyForecastProvider(
    private val latitude: Double,
    private val longitude: Double,
    private val timeoutMs: Int = 8_000,
    private val zoneId: ZoneId = ZoneId.of("Asia/Kolkata")
) : HourlyForecastProvider {

    override fun hourlyForecast(nowMillis: Long): List<HourlyForecastPoint> {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude&longitude=$longitude" +
                "&hourly=temperature_2m&timezone=auto&forecast_days=2"
        )
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.requestMethod = "GET"
            val response = conn.inputStream.bufferedReader().readText()
            val hourly = JSONObject(response).getJSONObject("hourly")
            val times = hourly.getJSONArray("time")
            val temps = hourly.getJSONArray("temperature_2m")
            val points = ArrayList<HourlyForecastPoint>(times.length())
            for (i in 0 until times.length()) {
                val localDateTime = LocalDateTime.parse(times.getString(i), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                val millis = localDateTime.atZone(zoneId).toInstant().toEpochMilli()
                points.add(HourlyForecastPoint(timestampMillis = millis, airTemperatureC = temps.getDouble(i)))
            }
            return points.filter { it.timestampMillis >= nowMillis }.take(5)
        } finally {
            conn.disconnect()
        }
    }
}
