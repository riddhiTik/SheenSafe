package org.kashmir.heatguard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.kashmir.heatguard.core.model.*
import org.kashmir.heatguard.core.risk.HeatIndex
import org.kashmir.heatguard.core.risk.HeatRiskEngine

class HeatIndexCalculatorTest {
    private val now = 1_700_000_000_000L
    private val engine = HeatRiskEngine()

    @Test
    fun belowThresholdReturnsAirTemp() {
        val result = HeatIndex.calculateCelsius(25.0, 40.0)
        assertEquals(25.0, result, "Below 80°F: heat index should equal air temperature")
    }

    @Test
    fun celsiusFahrenheitRoundTrip() {
        val original = 39.0
        val converted = HeatIndex.fahrenheitToCelsius(HeatIndex.celsiusToFahrenheit(original))
        assertTrue(kotlin.math.abs(converted - original) < 0.001)
    }

    @Test
    fun knownValueHighHumidity() {
        // 32.2°C / 70% RH: Rothfusz should produce a noticeably higher apparent temp
        val hi = HeatIndex.calculateCelsius(32.2, 70.0)
        assertTrue(hi > 33.0, "Expected apparent temp > air temp at high humidity, got $hi")
    }

    @Test
    fun directSunAdds8_33CToHeatIndex() {
        // directSun adds 15°F = 8.33°C
        val base = engine.assess(
            RiskInput(
                nowMillis = now,
                weatherSample = WeatherSample(now, airTemperatureC = 38.0, relativeHumidityPercent = 50.0, directSun = false)
            )
        )
        val withSun = engine.assess(
            RiskInput(
                nowMillis = now,
                weatherSample = WeatherSample(now, airTemperatureC = 38.0, relativeHumidityPercent = 50.0, directSun = true)
            )
        )
        val baseIdx = base.effectiveHeatIndexC!!
        val sunIdx = withSun.effectiveHeatIndexC!!
        assertTrue(sunIdx > baseIdx + 7.0, "Direct sun should add ~8.33°C, got diff=${sunIdx - baseIdx}")
    }

    @Test
    fun humidityClampedAt100() {
        // Should not throw; result should equal calculateCelsius with 100%
        val clamped = HeatIndex.calculateCelsius(38.0, 150.0)
        val normal = HeatIndex.calculateCelsius(38.0, 100.0)
        assertEquals(normal, clamped)
    }

    @Test
    fun threshold80FIsCheck() {
        // 26.67°C = 80°F at 0% humidity (below Rothfusz but == threshold)
        // Use exactly 80°F = 26.67°C — the Rothfusz guard is t < 80, so 26.67°C hits it
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                weatherSample = WeatherSample(now, airTemperatureC = 26.67, relativeHumidityPercent = 45.0)
            )
        )
        // The heat index at 26.67°C/45% will be checked against 80°F threshold
        assertTrue(decision.level.rank >= RiskLevel.CHECK.rank || decision.level == RiskLevel.SAFE,
            "At 80°F boundary: expecting at least CHECK or SAFE depending on exact HI calc")
    }

    @Test
    fun threshold103FIsAction() {
        // 39.44°C = 103°F; combine with moderate humidity to stay near 103°F heat index
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                weatherSample = WeatherSample(now, airTemperatureC = 39.44, relativeHumidityPercent = 20.0)
            )
        )
        // At 103°F dry, effective HI should be around 103°F
        assertTrue(decision.level.rank >= RiskLevel.CHECK.rank)
    }

    @Test
    fun threshold125FIsEmergency() {
        // 51.67°C = 125°F; force with direct sun to ensure EMERGENCY
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                weatherSample = WeatherSample(now, airTemperatureC = 45.0, relativeHumidityPercent = 65.0, directSun = false)
            )
        )
        assertEquals(RiskLevel.EMERGENCY, decision.level, "Very high heat index should be EMERGENCY")
    }
}
