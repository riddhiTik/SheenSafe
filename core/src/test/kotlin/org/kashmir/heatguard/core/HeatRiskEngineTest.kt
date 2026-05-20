package org.kashmir.heatguard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.kashmir.heatguard.core.model.*
import org.kashmir.heatguard.core.risk.HeatRiskEngine

class HeatRiskEngineTest {
    private val now = 1_700_000_000_000L
    private val engine = HeatRiskEngine()

    @Test
    fun severeSymptomsAreEmergency() {
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                symptomReport = SymptomReport(timestampMillis = now, confusion = true),
                weatherSample = WeatherSample(now, airTemperatureC = 32.0, relativeHumidityPercent = 40.0),
                sensorSample = SensorSample(now, heartRateBpm = 80, skinTemperatureC = 34.0)
            )
        )
        assertEquals(RiskLevel.EMERGENCY, decision.level)
        assertTrue(decision.reasonCodes.contains(ReasonCode.SEVERE_SYMPTOMS))
    }

    @Test
    fun highHeatWithPhysiologyStrainIsAction() {
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                weatherSample = WeatherSample(now, airTemperatureC = 39.0, relativeHumidityPercent = 55.0),
                sensorSample = SensorSample(now, heartRateBpm = 122, skinTemperatureC = 36.2, stepsSinceLastSample = 0),
                baseline = Baseline(typicalRestingHeartRateBpm = 78, typicalSkinTemperatureC = 34.1, skinTemperatureStdDevC = 0.5)
            )
        )
        assertEquals(RiskLevel.ACTION, decision.level)
        assertTrue(decision.reasonCodes.contains(ReasonCode.COMBINED_PHYSIOLOGY_STRAIN))
    }

    @Test
    fun staleRingStillWarnsOnHighWeatherRisk() {
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                weatherSample = WeatherSample(now, airTemperatureC = 38.0, relativeHumidityPercent = 45.0),
                sensorSample = SensorSample(now - 30 * 60_000L, heartRateBpm = 80, skinTemperatureC = 34.0)
            )
        )
        assertTrue(decision.level.rank >= RiskLevel.CHECK.rank)
        assertTrue(decision.reasonCodes.contains(ReasonCode.RING_DATA_STALE))
    }

    @Test
    fun mildConditionsRemainSafe() {
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                weatherSample = WeatherSample(now, airTemperatureC = 24.0, relativeHumidityPercent = 40.0),
                sensorSample = SensorSample(now, heartRateBpm = 76, skinTemperatureC = 34.0, stepsSinceLastSample = 20),
                baseline = Baseline(typicalRestingHeartRateBpm = 75, typicalSkinTemperatureC = 34.0, skinTemperatureStdDevC = 0.5)
            )
        )
        assertEquals(RiskLevel.SAFE, decision.level)
    }
}
