package org.kashmir.heatguard.weather

import org.kashmir.heatguard.core.model.WeatherSample
import org.kashmir.heatguard.core.weather.WeatherProvider
import org.kashmir.heatguard.data.AppRepository

class CachedWeatherProvider(
    private val delegate: WeatherProvider,
    private val repository: AppRepository,
    private val maxCacheAgeMs: Long = 2 * 60 * 60 * 1_000L
) : WeatherProvider {

    override fun latestWeather(nowMillis: Long): WeatherSample? {
        return try {
            val sample = delegate.latestWeather(nowMillis)
            if (sample != null) {
                repository.setCachedApiWeather(sample)
            }
            sample
        } catch (_: Exception) {
            val cached = repository.getCachedApiWeather() ?: return null
            if (nowMillis - cached.timestampMillis <= maxCacheAgeMs) cached else null
        }
    }
}
