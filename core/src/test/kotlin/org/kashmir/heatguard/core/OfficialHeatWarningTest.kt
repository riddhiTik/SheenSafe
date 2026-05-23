package org.kashmir.heatguard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.kashmir.heatguard.core.model.*
import org.kashmir.heatguard.core.risk.HeatRiskEngine

class OfficialHeatWarningTest {
    private val now = 1_700_000_000_000L
    private val engine = HeatRiskEngine()
    private val coolWeather = WeatherSample(now, airTemperatureC = 22.0, relativeHumidityPercent = 40.0)

    private fun withWarning(warning: OfficialHeatWarning) = engine.assess(
        RiskInput(nowMillis = now, weatherSample = coolWeather.copy(officialWarning = warning))
    )

    @Test
    fun watchProducesCheck() {
        val d = withWarning(OfficialHeatWarning.WATCH)
        assertEquals(RiskLevel.CHECK, d.level)
        assertTrue(d.reasonCodes.contains(ReasonCode.OFFICIAL_HEAT_WATCH))
    }

    @Test
    fun warningProducesAction() {
        val d = withWarning(OfficialHeatWarning.WARNING)
        assertEquals(RiskLevel.ACTION, d.level)
        assertTrue(d.reasonCodes.contains(ReasonCode.OFFICIAL_HEAT_WARNING))
    }

    @Test
    fun extremeProducesEmergency() {
        val d = withWarning(OfficialHeatWarning.EXTREME)
        assertEquals(RiskLevel.EMERGENCY, d.level)
        assertTrue(d.reasonCodes.contains(ReasonCode.OFFICIAL_EXTREME_WARNING))
    }

    @Test
    fun noneHasNoEffect() {
        val d = withWarning(OfficialHeatWarning.NONE)
        assertEquals(RiskLevel.SAFE, d.level)
        assertFalse(d.reasonCodes.contains(ReasonCode.OFFICIAL_HEAT_WATCH))
    }

    @Test
    fun warningBeatsMildEnvCheck() {
        // cool air temp (would be SAFE) but WARNING → ACTION
        val d = engine.assess(
            RiskInput(
                nowMillis = now,
                weatherSample = WeatherSample(now, airTemperatureC = 22.0, relativeHumidityPercent = 40.0,
                    officialWarning = OfficialHeatWarning.WARNING)
            )
        )
        assertEquals(RiskLevel.ACTION, d.level)
    }

    @Test
    fun extremeBeatsEverything() {
        val d = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = SensorSample(now, heartRateBpm = 70, skinTemperatureC = 34.0, stepsSinceLastSample = 10),
                weatherSample = WeatherSample(now, airTemperatureC = 22.0, relativeHumidityPercent = 40.0,
                    officialWarning = OfficialHeatWarning.EXTREME),
                baseline = Baseline(typicalRestingHeartRateBpm = 70, typicalSkinTemperatureC = 34.0)
            )
        )
        assertEquals(RiskLevel.EMERGENCY, d.level)
    }
}
