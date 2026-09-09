package org.kashmir.heatguard.data

import org.kashmir.heatguard.core.model.HourlyForecastPoint

interface HourlyForecastRepository {
    fun getCachedHourlyForecast(): List<HourlyForecastPoint>
    fun setCachedHourlyForecast(nowMillis: Long, points: List<HourlyForecastPoint>)
}
