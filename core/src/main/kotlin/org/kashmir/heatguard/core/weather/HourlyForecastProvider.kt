package org.kashmir.heatguard.core.weather

import org.kashmir.heatguard.core.model.HourlyForecastPoint

fun interface HourlyForecastProvider {
    fun hourlyForecast(nowMillis: Long): List<HourlyForecastPoint>
}
