package org.kashmir.heatguard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.kashmir.heatguard.core.model.*
import org.kashmir.heatguard.core.risk.HeatIndex
import org.kashmir.heatguard.core.risk.HeatRiskEngine

class RiskThresholdTest {
    private val now = 1_700_000_000_000L
    private val engine = HeatRiskEngine()

    private fun weatherAt(heatIndexF: Double): WeatherSample {
        // Back-calculate a temp/humidity that produces the target heat index in Fahrenheit
        // Simple approach: use humidity = 50%, find temp that produces desired HI
        // For testing use direct heatIndexC override via provided field
        val heatIndexC = HeatIndex.fahrenheitToCelsius(heatIndexF)
        return WeatherSample(
            timestampMillis = now,
            airTemperatureC = heatIndexC - 2.0, // approximate; heatIndexC field overrides calc
            relativeHumidityPercent = 50.0,
            heatIndexC = heatIndexC
        )
    }

    @Test
    fun below80FIsSafe() {
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                weatherSample = WeatherSample(now, airTemperatureC = 20.0, relativeHumidityPercent = 40.0)
            )
        )
        assertEquals(RiskLevel.SAFE, decision.level)
    }

    @Test
    fun at80FIsCheck() {
        val decision = engine.assess(RiskInput(nowMillis = now, weatherSample = weatherAt(80.0)))
        assertEquals(RiskLevel.CHECK, decision.level)
        assertTrue(decision.reasonCodes.contains(ReasonCode.LOW_HEAT_INDEX))
    }

    @Test
    fun at102FIsCheck() {
        val decision = engine.assess(RiskInput(nowMillis = now, weatherSample = weatherAt(102.0)))
        assertEquals(RiskLevel.CHECK, decision.level)
    }

    @Test
    fun at103FIsAction() {
        val decision = engine.assess(RiskInput(nowMillis = now, weatherSample = weatherAt(103.0)))
        assertEquals(RiskLevel.ACTION, decision.level)
        assertTrue(decision.reasonCodes.contains(ReasonCode.HIGH_HEAT_INDEX))
    }

    @Test
    fun at124FIsAction() {
        val decision = engine.assess(RiskInput(nowMillis = now, weatherSample = weatherAt(124.0)))
        assertEquals(RiskLevel.ACTION, decision.level)
    }

    @Test
    fun at125FIsEmergency() {
        val decision = engine.assess(RiskInput(nowMillis = now, weatherSample = weatherAt(125.0)))
        assertEquals(RiskLevel.EMERGENCY, decision.level)
        assertTrue(decision.reasonCodes.contains(ReasonCode.EXTREME_HEAT_INDEX))
    }

    @Test
    fun officialWatchProducesCheck() {
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                weatherSample = WeatherSample(now, airTemperatureC = 22.0, relativeHumidityPercent = 40.0,
                    officialWarning = OfficialHeatWarning.WATCH)
            )
        )
        assertEquals(RiskLevel.CHECK, decision.level)
        assertTrue(decision.reasonCodes.contains(ReasonCode.OFFICIAL_HEAT_WATCH))
    }

    @Test
    fun officialWarningProducesAction() {
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                weatherSample = WeatherSample(now, airTemperatureC = 22.0, relativeHumidityPercent = 40.0,
                    officialWarning = OfficialHeatWarning.WARNING)
            )
        )
        assertEquals(RiskLevel.ACTION, decision.level)
        assertTrue(decision.reasonCodes.contains(ReasonCode.OFFICIAL_HEAT_WARNING))
    }

    @Test
    fun officialExtremeProducesEmergency() {
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                weatherSample = WeatherSample(now, airTemperatureC = 22.0, relativeHumidityPercent = 40.0,
                    officialWarning = OfficialHeatWarning.EXTREME)
            )
        )
        assertEquals(RiskLevel.EMERGENCY, decision.level)
        assertTrue(decision.reasonCodes.contains(ReasonCode.OFFICIAL_EXTREME_WARNING))
    }

    @Test
    fun directSunPushesRiskHigher() {
        // borderline temperature that without sun is CHECK; with sun should stay CHECK or higher
        val noSun = engine.assess(
            RiskInput(nowMillis = now,
                weatherSample = WeatherSample(now, airTemperatureC = 34.0, relativeHumidityPercent = 55.0, directSun = false))
        )
        val withSun = engine.assess(
            RiskInput(nowMillis = now,
                weatherSample = WeatherSample(now, airTemperatureC = 34.0, relativeHumidityPercent = 55.0, directSun = true))
        )
        assertTrue(withSun.level.rank >= noSun.level.rank, "Direct sun should not lower risk")
        assertTrue(withSun.reasonCodes.contains(ReasonCode.DIRECT_SUN_ADJUSTMENT))
    }
}
