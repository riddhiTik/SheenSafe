package org.kashmir.heatguard.core.model

data class SensorSample(
    val timestampMillis: Long,
    val heartRateBpm: Int? = null,
    val skinTemperatureC: Double? = null,
    val stepsSinceLastSample: Int? = null,
    val spo2Percent: Int? = null,
    val batteryPercent: Int? = null,
    val connected: Boolean = true
)
