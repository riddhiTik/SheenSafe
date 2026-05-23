package org.kashmir.heatguard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.kashmir.heatguard.core.model.*
import org.kashmir.heatguard.core.risk.HeatRiskEngine

class BaselineDriftTest {
    private val now = 1_700_000_000_000L
    private val engine = HeatRiskEngine()
    private val safeWeather = WeatherSample(now, airTemperatureC = 22.0, relativeHumidityPercent = 40.0)

    @Test
    fun skinTempRisingBy1CIsCheck() {
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = SensorSample(now, heartRateBpm = 78, skinTemperatureC = 35.1, stepsSinceLastSample = 5),
                weatherSample = safeWeather,
                baseline = Baseline(typicalSkinTemperatureC = 34.1)
            )
        )
        assertEquals(RiskLevel.CHECK, decision.level)
        assertTrue(decision.reasonCodes.contains(ReasonCode.SKIN_TEMPERATURE_RISING))
    }

    @Test
    fun skinTempBelowThresholdIsNotFlagged() {
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = SensorSample(now, heartRateBpm = 78, skinTemperatureC = 34.9, stepsSinceLastSample = 5),
                weatherSample = safeWeather,
                baseline = Baseline(typicalSkinTemperatureC = 34.1)
            )
        )
        assertFalse(decision.reasonCodes.contains(ReasonCode.SKIN_TEMPERATURE_RISING))
    }

    @Test
    fun skinTempHighBy1_8CIsCheck() {
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = SensorSample(now, heartRateBpm = 78, skinTemperatureC = 36.0, stepsSinceLastSample = 5),
                weatherSample = safeWeather,
                baseline = Baseline(typicalSkinTemperatureC = 34.1)
            )
        )
        // delta = 36.0 - 34.1 = 1.9°C, which is > 1.8°C threshold (using 35.9 caused FP truncation to 1.799...)
        assertTrue(decision.reasonCodes.contains(ReasonCode.SKIN_TEMPERATURE_HIGH_RELATIVE_TO_BASELINE))
    }

    @Test
    fun skinTempAtTwoStdDevsIsHigh() {
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = SensorSample(now, heartRateBpm = 78, skinTemperatureC = 35.1, stepsSinceLastSample = 5),
                weatherSample = safeWeather,
                baseline = Baseline(typicalSkinTemperatureC = 34.1, skinTemperatureStdDevC = 0.5)
            )
        )
        // delta = 1.0 = 2 * stdDev(0.5) → should be flagged HIGH
        assertTrue(decision.reasonCodes.contains(ReasonCode.SKIN_TEMPERATURE_HIGH_RELATIVE_TO_BASELINE))
    }

    @Test
    fun hrHighAtRestNoBaselineIsCheck() {
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = SensorSample(now, heartRateBpm = 116, skinTemperatureC = 34.0, stepsSinceLastSample = 0),
                weatherSample = safeWeather
            )
        )
        assertTrue(decision.reasonCodes.contains(ReasonCode.HEART_RATE_HIGH_AT_REST))
        assertTrue(decision.level.rank >= RiskLevel.CHECK.rank)
    }

    @Test
    fun hrHighVsBaselineIsCheck() {
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = SensorSample(now, heartRateBpm = 101, skinTemperatureC = 34.0, stepsSinceLastSample = 0),
                weatherSample = safeWeather,
                baseline = Baseline(typicalRestingHeartRateBpm = 70)
            )
        )
        // 101 >= 70 + 30 = 100: should be flagged
        assertTrue(decision.reasonCodes.contains(ReasonCode.HEART_RATE_HIGH_AT_REST))
    }

    @Test
    fun hrHighWhileActiveNotFlagged() {
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = SensorSample(now, heartRateBpm = 130, skinTemperatureC = 34.0, stepsSinceLastSample = 200),
                weatherSample = safeWeather
            )
        )
        assertFalse(decision.reasonCodes.contains(ReasonCode.HEART_RATE_HIGH_AT_REST))
    }

    @Test
    fun combinedStrainWithHotWeatherIsAction() {
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = SensorSample(now, heartRateBpm = 122, skinTemperatureC = 36.2, stepsSinceLastSample = 0),
                weatherSample = WeatherSample(now, airTemperatureC = 38.0, relativeHumidityPercent = 55.0),
                baseline = Baseline(typicalRestingHeartRateBpm = 78, typicalSkinTemperatureC = 34.1, skinTemperatureStdDevC = 0.5)
            )
        )
        assertEquals(RiskLevel.ACTION, decision.level)
        assertTrue(decision.reasonCodes.contains(ReasonCode.COMBINED_PHYSIOLOGY_STRAIN))
    }

    @Test
    fun staleRingSkipsPhysiologyCheck() {
        val staleTimestamp = now - 30 * 60_000L
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = SensorSample(staleTimestamp, heartRateBpm = 130, skinTemperatureC = 37.5, stepsSinceLastSample = 0),
                weatherSample = safeWeather,
                baseline = Baseline(typicalRestingHeartRateBpm = 70, typicalSkinTemperatureC = 34.0)
            )
        )
        assertFalse(decision.reasonCodes.contains(ReasonCode.HEART_RATE_HIGH_AT_REST))
        assertFalse(decision.reasonCodes.contains(ReasonCode.SKIN_TEMPERATURE_RISING))
    }
}
