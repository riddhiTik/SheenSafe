package org.kashmir.heatguard.core.engine

data class WeatherInput(
    val ambientTempC: Double,
    val relativeHumidityPercent: Double,
    val heatIndexC: Double? = null,
    val dewPointC: Double? = null,
    val forecastMaxTempC: Double? = null,
    val isHeatwave: Boolean = false
)
