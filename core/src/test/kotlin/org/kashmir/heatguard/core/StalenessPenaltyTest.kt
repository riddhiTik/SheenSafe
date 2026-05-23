package org.kashmir.heatguard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.kashmir.heatguard.core.model.*
import org.kashmir.heatguard.core.risk.HeatRiskEngine

class StalenessPenaltyTest {
    private val now = 1_700_000_000_000L
    private val engine = HeatRiskEngine()

    @Test
    fun staleRingSampleIsDetected() {
        val staleTimestamp = now - 16 * 60_000L
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = SensorSample(staleTimestamp, heartRateBpm = 80, skinTemperatureC = 34.0),
                weatherSample = WeatherSample(now, airTemperatureC = 24.0, relativeHumidityPercent = 40.0)
            )
        )
        assertTrue(decision.reasonCodes.contains(ReasonCode.RING_DATA_STALE))
    }

    @Test
    fun freshRingSampleNotStale() {
        val freshTimestamp = now - 14 * 60_000L
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = SensorSample(freshTimestamp, heartRateBpm = 80, skinTemperatureC = 34.0),
                weatherSample = WeatherSample(now, airTemperatureC = 24.0, relativeHumidityPercent = 40.0)
            )
        )
        assertFalse(decision.reasonCodes.contains(ReasonCode.RING_DATA_STALE))
    }

    @Test
    fun staleWeatherIsDetected() {
        val staleWeather = now - 61 * 60_000L
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                weatherSample = WeatherSample(staleWeather, airTemperatureC = 24.0, relativeHumidityPercent = 40.0)
            )
        )
        assertTrue(decision.reasonCodes.contains(ReasonCode.WEATHER_DATA_STALE))
    }

    @Test
    fun nullWeatherSampleAddsMissingCode() {
        val decision = engine.assess(RiskInput(nowMillis = now, weatherSample = null))
        assertTrue(decision.reasonCodes.contains(ReasonCode.WEATHER_DATA_MISSING))
    }

    @Test
    fun nullRingSampleAddsMissingCode() {
        val decision = engine.assess(RiskInput(nowMillis = now, sensorSample = null))
        assertTrue(decision.reasonCodes.contains(ReasonCode.RING_DATA_MISSING))
    }

    @Test
    fun disconnectedRingIsStale() {
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = SensorSample(now, heartRateBpm = 80, skinTemperatureC = 34.0, connected = false),
                weatherSample = WeatherSample(now, airTemperatureC = 24.0, relativeHumidityPercent = 40.0)
            )
        )
        assertTrue(decision.reasonCodes.contains(ReasonCode.RING_DATA_STALE))
    }

    @Test
    fun staleRingWithHotWeatherStillWarns() {
        val staleTimestamp = now - 30 * 60_000L
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = SensorSample(staleTimestamp, heartRateBpm = 80, skinTemperatureC = 34.0),
                weatherSample = WeatherSample(now, airTemperatureC = 38.0, relativeHumidityPercent = 50.0)
            )
        )
        assertTrue(decision.level.rank >= RiskLevel.CHECK.rank)
        assertTrue(decision.reasonCodes.contains(ReasonCode.RING_DATA_STALE))
    }

    @Test
    fun lowBatteryAddsBatteryLowCode() {
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = SensorSample(now, heartRateBpm = 80, batteryPercent = 15)
            )
        )
        assertTrue(decision.reasonCodes.contains(ReasonCode.BATTERY_LOW))
    }

    @Test
    fun confidenceIsLowWithBothDataMissing() {
        val decision = engine.assess(RiskInput(nowMillis = now))
        assertEquals(Confidence.LOW, decision.confidence)
    }

    @Test
    fun confidenceIsHighWithBothDataFresh() {
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = SensorSample(now, heartRateBpm = 78, skinTemperatureC = 34.0),
                weatherSample = WeatherSample(now, airTemperatureC = 24.0, relativeHumidityPercent = 40.0)
            )
        )
        assertEquals(Confidence.HIGH, decision.confidence)
    }
}
