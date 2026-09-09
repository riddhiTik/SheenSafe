package org.kashmir.heatguard.core.model

data class HourlyForecastPoint(
    val timestampMillis: Long,
    val airTemperatureC: Double
)
