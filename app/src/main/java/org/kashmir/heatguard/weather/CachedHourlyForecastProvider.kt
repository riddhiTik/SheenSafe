package org.kashmir.heatguard.weather

import org.kashmir.heatguard.core.model.HourlyForecastPoint
import org.kashmir.heatguard.core.weather.HourlyForecastProvider
import org.kashmir.heatguard.data.AppRepository

class CachedHourlyForecastProvider(
    private val delegate: HourlyForecastProvider,
    private val repository: AppRepository,
    private val maxCacheAgeMs: Long = 2 * 60 * 60 * 1_000L
) : HourlyForecastProvider {

    override fun hourlyForecast(nowMillis: Long): List<HourlyForecastPoint> {
        return try {
            val points = delegate.hourlyForecast(nowMillis)
            if (points.isNotEmpty()) repository.setCachedHourlyForecast(nowMillis, points)
            points
        } catch (_: Exception) {
            if (nowMillis - repository.getHourlyForecastFetchedAt() > maxCacheAgeMs) return emptyList()
            repository.getCachedHourlyForecast().filter { it.timestampMillis >= nowMillis }
        }
    }
}
