package org.kashmir.heatguard.weather

import org.kashmir.heatguard.core.weather.WeatherProvider
import org.kashmir.heatguard.data.WeatherRepository
import org.kashmir.heatguard.core.model.WeatherSample

class ManualWeatherProvider(
    private val repository: WeatherRepository
) : WeatherProvider {
    // Null until the user has entered weather: the stored defaults (36 °C / 45 %) are placeholders,
    // not measurements, and must never feed a risk decision or the UI.
    override fun latestWeather(nowMillis: Long): WeatherSample? =
        if (repository.hasManualWeather()) repository.getLastManualWeather(nowMillis) else null
}
