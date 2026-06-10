package org.kashmir.heatguard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

import org.kashmir.heatguard.core.model.*
import org.kashmir.heatguard.core.risk.HeatRiskEngine

class MissingDataTest {
    private val now = 1_700_000_000_000L
    private val engine = HeatRiskEngine()

    @Test
    fun noWeatherNoRingDefaultsToSafe() {
        val d = engine.assess(RiskInput(nowMillis = now))
        assertEquals(RiskLevel.SAFE, d.level)
        assertEquals(Confidence.LOW, d.confidence)
    }

    @Test
    fun nullWeatherAddsMissingCode() {
        val d = engine.assess(RiskInput(nowMillis = now, weatherSample = null))
        assertTrue(d.reasonCodes.contains(ReasonCode.WEATHER_DATA_MISSING))
    }

    @Test
    fun nullRingAddsMissingCode() {
        val d = engine.assess(RiskInput(nowMillis = now, sensorSample = null))
        assertTrue(d.reasonCodes.contains(ReasonCode.RING_DATA_MISSING))
    }

    @Test
    fun baselineAllNullDoesNotCrash() {
        val d = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = SensorSample(now, heartRateBpm = 80, skinTemperatureC = 34.0),
                weatherSample = WeatherSample(now, airTemperatureC = 24.0, relativeHumidityPercent = 40.0),
                baseline = Baseline()
            )
        )
        // No crash; physiology checks with no baseline should not produce false flags
        assertFalse(d.reasonCodes.contains(ReasonCode.SKIN_TEMPERATURE_RISING))
    }

    @Test
    fun partialBaselineTempPresentHrAbsent() {
        val d = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = SensorSample(now, heartRateBpm = 120, skinTemperatureC = 35.2, stepsSinceLastSample = 0),
                weatherSample = WeatherSample(now, airTemperatureC = 24.0, relativeHumidityPercent = 40.0),
                baseline = Baseline(typicalSkinTemperatureC = 34.1, typicalRestingHeartRateBpm = null)
            )
        )
        // Skin temp delta = 1.1°C → rising flag; HR: no baseline but 120 >= 115 → still flagged
        assertTrue(d.reasonCodes.contains(ReasonCode.SKIN_TEMPERATURE_RISING))
    }

    @Test
    fun ringHrNullDoesNotCrash() {
        val d = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = SensorSample(now, heartRateBpm = null, skinTemperatureC = 34.0),
                weatherSample = WeatherSample(now, airTemperatureC = 24.0, relativeHumidityPercent = 40.0)
            )
        )
        assertFalse(d.reasonCodes.contains(ReasonCode.HEART_RATE_HIGH_AT_REST))
    }

    @Test
    fun ringSkinTempNullDoesNotCrash() {
        val d = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = SensorSample(now, heartRateBpm = 80, skinTemperatureC = null),
                weatherSample = WeatherSample(now, airTemperatureC = 24.0, relativeHumidityPercent = 40.0)
            )
        )
        assertFalse(d.reasonCodes.contains(ReasonCode.SKIN_TEMPERATURE_RISING))
    }
}
