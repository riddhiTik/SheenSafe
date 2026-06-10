package org.kashmir.heatguard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import org.kashmir.heatguard.core.model.*
import org.kashmir.heatguard.core.risk.HeatRiskEngine

class ConfidenceScoreTest {
    private val now = 1_700_000_000_000L
    private val engine = HeatRiskEngine()

    @Test
    fun bothFreshIsHighConfidence() {
        val d = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = SensorSample(now, heartRateBpm = 78, skinTemperatureC = 34.0),
                weatherSample = WeatherSample(now, airTemperatureC = 24.0, relativeHumidityPercent = 40.0)
            )
        )
        assertEquals(Confidence.HIGH, d.confidence)
    }

    @Test
    fun onlyWeatherFreshIsMediumConfidence() {
        val staleRing = now - 30 * 60_000L
        val d = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = SensorSample(staleRing, heartRateBpm = 78, skinTemperatureC = 34.0),
                weatherSample = WeatherSample(now, airTemperatureC = 24.0, relativeHumidityPercent = 40.0)
            )
        )
        assertEquals(Confidence.MEDIUM, d.confidence)
    }

    @Test
    fun onlyRingFreshIsMediumConfidence() {
        val staleWeather = now - 90 * 60_000L
        val d = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = SensorSample(now, heartRateBpm = 78, skinTemperatureC = 34.0),
                weatherSample = WeatherSample(staleWeather, airTemperatureC = 24.0, relativeHumidityPercent = 40.0)
            )
        )
        assertEquals(Confidence.MEDIUM, d.confidence)
    }

    @Test
    fun bothStaleIsLowConfidence() {
        val stale = now - 90 * 60_000L
        val d = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = SensorSample(stale, heartRateBpm = 78, skinTemperatureC = 34.0),
                weatherSample = WeatherSample(stale, airTemperatureC = 24.0, relativeHumidityPercent = 40.0)
            )
        )
        assertEquals(Confidence.LOW, d.confidence)
    }
}
