package org.kashmir.heatguard.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.kashmir.heatguard.core.model.*
import org.kashmir.heatguard.core.risk.HeatRiskEngine

class DecisionReasonTest {
    private val now = 1_700_000_000_000L
    private val engine = HeatRiskEngine()

    @Test
    fun safeConditionsHaveNormalCode() {
        val d = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = SensorSample(now, heartRateBpm = 75, skinTemperatureC = 34.0, stepsSinceLastSample = 10),
                weatherSample = WeatherSample(now, airTemperatureC = 22.0, relativeHumidityPercent = 40.0),
                baseline = Baseline(typicalRestingHeartRateBpm = 75, typicalSkinTemperatureC = 34.0)
            )
        )
        assertTrue(d.reasonCodes.contains(ReasonCode.NORMAL))
    }

    @Test
    fun normalCodeAbsentWhenOtherReasonsPresent() {
        val d = engine.assess(
            RiskInput(
                nowMillis = now,
                weatherSample = WeatherSample(now, airTemperatureC = 22.0, relativeHumidityPercent = 40.0,
                    officialWarning = OfficialHeatWarning.WATCH)
            )
        )
        assertFalse(d.reasonCodes.contains(ReasonCode.NORMAL))
        assertTrue(d.reasonCodes.contains(ReasonCode.OFFICIAL_HEAT_WATCH))
    }

    @Test
    fun multipleCausesAllPresent() {
        val staleRing = now - 30 * 60_000L
        val d = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = SensorSample(staleRing, heartRateBpm = 120, skinTemperatureC = 37.0, stepsSinceLastSample = 0),
                weatherSample = WeatherSample(now, airTemperatureC = 38.0, relativeHumidityPercent = 55.0),
                baseline = Baseline(typicalRestingHeartRateBpm = 70, typicalSkinTemperatureC = 34.0)
            )
        )
        assertTrue(d.reasonCodes.contains(ReasonCode.RING_DATA_STALE))
        assertTrue(d.reasonCodes.contains(ReasonCode.HIGH_HEAT_INDEX))
        // Physiology skipped because ring is stale
    }

    @Test
    fun directSunAddsDirectSunCode() {
        val d = engine.assess(
            RiskInput(
                nowMillis = now,
                weatherSample = WeatherSample(now, airTemperatureC = 36.0, relativeHumidityPercent = 50.0, directSun = true)
            )
        )
        assertTrue(d.reasonCodes.contains(ReasonCode.DIRECT_SUN_ADJUSTMENT))
    }

    @Test
    fun batteryLowAddsCode() {
        val d = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = SensorSample(now, heartRateBpm = 80, batteryPercent = 10)
            )
        )
        assertTrue(d.reasonCodes.contains(ReasonCode.BATTERY_LOW))
    }

    @Test
    fun reasonCodesIterateStably() {
        val d = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = SensorSample(now, heartRateBpm = 80, batteryPercent = 10),
                weatherSample = WeatherSample(now, airTemperatureC = 22.0, relativeHumidityPercent = 40.0,
                    officialWarning = OfficialHeatWarning.WATCH)
            )
        )
        val list1 = d.reasonCodes.toList()
        val list2 = d.reasonCodes.toList()
        assertTrue(list1 == list2, "Reason codes should iterate in stable order")
    }
}
