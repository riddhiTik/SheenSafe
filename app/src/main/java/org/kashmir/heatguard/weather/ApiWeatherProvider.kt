package org.kashmir.heatguard.weather

import org.kashmir.heatguard.core.model.WeatherSample
import org.kashmir.heatguard.core.weather.WeatherProvider
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ApiWeatherProvider(
    private val latitude: Double,
    private val longitude: Double,
    private val timeoutMs: Int = 8_000
) : WeatherProvider {

    override fun latestWeather(nowMillis: Long): WeatherSample {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,relative_humidity_2m"
        )
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.requestMethod = "GET"
            val response = conn.inputStream.bufferedReader().readText()
            val current = JSONObject(response).getJSONObject("current")
            val temp = current.getDouble("temperature_2m")
            val rh = current.getDouble("relative_humidity_2m")
            return WeatherSample(
                timestampMillis = nowMillis,
                airTemperatureC = temp,
                relativeHumidityPercent = rh,
                source = "open-meteo"
            )
        } finally {
            conn.disconnect()
        }
    }
}
