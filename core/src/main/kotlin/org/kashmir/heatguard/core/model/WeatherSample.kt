package org.kashmir.heatguard.core.model

data class WeatherSample(
    val timestampMillis: Long,
    val airTemperatureC: Double? = null,
    val relativeHumidityPercent: Double? = null,
    val heatIndexC: Double? = null,
    val directSun: Boolean = false,
    val officialWarning: OfficialHeatWarning = OfficialHeatWarning.NONE,
    val source: String = "manual"
)
