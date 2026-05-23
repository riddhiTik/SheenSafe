package org.kashmir.heatguard.weather

import org.kashmir.heatguard.core.model.WeatherSample
import org.kashmir.heatguard.core.weather.WeatherProvider

class FallbackWeatherProvider(
    private val providers: List<WeatherProvider>
) : WeatherProvider {

    override fun latestWeather(nowMillis: Long): WeatherSample? {
        for (provider in providers) {
            try {
                val sample = provider.latestWeather(nowMillis)
                if (sample != null) return sample
            } catch (_: Exception) {
                // Try the next provider
            }
        }
        return null
    }
}
