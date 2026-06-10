package org.kashmir.heatguard.weather

import org.kashmir.heatguard.core.weather.WeatherProvider
import org.kashmir.heatguard.data.WeatherRepository
import org.kashmir.heatguard.core.model.WeatherSample

class ManualWeatherProvider(
    private val repository: WeatherRepository
) : WeatherProvider {
    override fun latestWeather(nowMillis: Long): WeatherSample = repository.getLastManualWeather(nowMillis)
}
