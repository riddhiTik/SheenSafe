package org.kashmir.heatguard.core.model

data class SensorSample(
    val timestampMillis: Long,
    val heartRateBpm: Int? = null,
    // Kept for backward compatibility with existing readers (Dashboard, Supabase column).
    // Always equal to smoothedSkinTemperatureC when that is non-null. New code should prefer
    // rawSkinTemperatureC / smoothedSkinTemperatureC directly, since this field's meaning
    // silently changed from "raw" to "smoothed" when skin-temp smoothing was introduced.
    val skinTemperatureC: Double? = null,
    val rawSkinTemperatureC: Double? = null,
    val smoothedSkinTemperatureC: Double? = null,
    // Watch's own on-device ambient reading (e.g. SkinTemperatureSet.AMBIENT_TEMPERATURE),
    // distinct from WeatherSample.airTemperatureC which comes from a weather API.
    val onDeviceAmbientTemperatureC: Double? = null,
    val stepsSinceLastSample: Int? = null,
    val spo2Percent: Int? = null,
    val batteryPercent: Int? = null,
    val connected: Boolean = true
)
