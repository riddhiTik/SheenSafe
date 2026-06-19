package org.kashmir.heatguard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.kashmir.heatguard.core.escalation.EscalationAction
import org.kashmir.heatguard.core.escalation.EscalationEngine
import org.kashmir.heatguard.core.fakes.FakeClock
import org.kashmir.heatguard.core.fakes.FakeRingDataSource
import org.kashmir.heatguard.core.fakes.FakeWeatherProvider
import org.kashmir.heatguard.core.model.*
import org.kashmir.heatguard.core.pipeline.MonitoringPipeline
import org.kashmir.heatguard.core.risk.HeatRiskEngine

/**
 * Tests how the risk assessment pipeline reacts to changes in user data over time.
 *
 * Each test simulates a real monitoring scenario: data arrives, time passes, data
 * changes, and the system must produce the correct updated decision.
 */
class UserDataChangeTest {

    private val clock = FakeClock()
    private val ring = FakeRingDataSource()
    private val weather = FakeWeatherProvider()
    private val escalation = EscalationEngine(clock)

    private val baseline = Baseline(
        typicalRestingHeartRateBpm = 72,
        typicalSkinTemperatureC = 34.1,
        skinTemperatureStdDevC = 0.5
    )

    private val pipeline = MonitoringPipeline(
        clock = clock,
        ringDataSource = ring,
        weatherProvider = weather,
        engine = HeatRiskEngine(),
        escalationEngine = escalation,
        baselineProvider = { baseline },
        riskProfileProvider = { RiskProfile(ageBand = AgeBand.ADULT, daysAcclimatized = 10) }
    )

    // ------------------------------------------------------------------ heart rate changes

    @Test
    fun normalHrAtRest_isSafe() {
        weather.set(mildWeather())
        ring.emit(sample(hr = 72, temp = 34.1, steps = 5))

        val result = pipeline.checkNow()

        assertEquals(RiskLevel.SAFE, result.decision.level)
        assertFalse(result.decision.reasonCodes.contains(ReasonCode.HEART_RATE_HIGH_AT_REST))
    }

    @Test
    fun hrJumpsAbove115AtRest_triggersCheck() {
        weather.set(mildWeather())
        ring.emit(sample(hr = 72, temp = 34.1, steps = 5))
        assertEquals(RiskLevel.SAFE, pipeline.checkNow().decision.level)

        // 5 minutes later: HR spikes to 118 bpm, user is still at rest
        clock.advanceMinutes(5)
        ring.emit(sample(hr = 118, temp = 34.1, steps = 10, offsetMinutes = 5))

        val result = pipeline.checkNow()

        assertEquals(RiskLevel.CHECK, result.decision.level)
        assertTrue(result.decision.reasonCodes.contains(ReasonCode.HEART_RATE_HIGH_AT_REST))
    }

    @Test
    fun hrDropsBackToNormal_riskReturnsSafe() {
        weather.set(mildWeather())

        // Elevated HR at rest
        ring.emit(sample(hr = 120, temp = 34.1, steps = 8))
        assertEquals(RiskLevel.CHECK, pipeline.checkNow().decision.level)

        // 5 minutes later: HR normalises
        clock.advanceMinutes(5)
        ring.emit(sample(hr = 74, temp = 34.1, steps = 12, offsetMinutes = 5))

        val result = pipeline.checkNow()

        assertEquals(RiskLevel.SAFE, result.decision.level)
        assertFalse(result.decision.reasonCodes.contains(ReasonCode.HEART_RATE_HIGH_AT_REST))
    }

    @Test
    fun highHrWhileActive_isNotFlaggedAsRiskAtRest() {
        weather.set(mildWeather())
        // Steps = 200 → user is clearly active, not at rest
        ring.emit(sample(hr = 145, temp = 34.1, steps = 200))

        val result = pipeline.checkNow()

        assertFalse(result.decision.reasonCodes.contains(ReasonCode.HEART_RATE_HIGH_AT_REST))
    }

    // ------------------------------------------------------------------ skin temperature changes

    @Test
    fun skinTempRisesOneCelsiusAboveBaseline_triggersCheck() {
        weather.set(mildWeather())
        ring.emit(sample(hr = 72, temp = 34.1, steps = 5))
        assertEquals(RiskLevel.SAFE, pipeline.checkNow().decision.level)

        clock.advanceMinutes(5)
        // Baseline is 34.1°C. A rise of 1.0°C puts delta at exactly 1.0 → SKIN_TEMPERATURE_RISING
        ring.emit(sample(hr = 73, temp = 35.1, steps = 5, offsetMinutes = 5))

        val result = pipeline.checkNow()

        assertEquals(RiskLevel.CHECK, result.decision.level)
        assertTrue(result.decision.reasonCodes.contains(ReasonCode.SKIN_TEMPERATURE_RISING))
    }

    @Test
    fun skinTempRisesBy1_8C_highFlag() {
        weather.set(mildWeather())
        // 34.1 + 1.9 = 36.0°C → delta 1.9°C ≥ 1.8°C threshold (avoids IEEE 754 issue)
        ring.emit(sample(hr = 72, temp = 36.0, steps = 5))

        val result = pipeline.checkNow()

        assertEquals(RiskLevel.CHECK, result.decision.level)
        assertTrue(result.decision.reasonCodes.contains(ReasonCode.SKIN_TEMPERATURE_HIGH_RELATIVE_TO_BASELINE))
    }

    @Test
    fun skinTempNormalisesAfterBeingHigh_riskDrops() {
        weather.set(mildWeather())
        ring.emit(sample(hr = 72, temp = 36.0, steps = 5))
        assertEquals(RiskLevel.CHECK, pipeline.checkNow().decision.level)

        clock.advanceMinutes(5)
        ring.emit(sample(hr = 72, temp = 34.3, steps = 5, offsetMinutes = 5))

        val result = pipeline.checkNow()

        assertEquals(RiskLevel.SAFE, result.decision.level)
    }

    // ------------------------------------------------------------------ combined physiological strain

    @Test
    fun highHrPlusTempRisingInWarmWeather_isAction() {
        // 30°C / 55% RH → heat index ~93°F → CHECK level for environment
        weather.set(WeatherSample(clock.nowMillis(), airTemperatureC = 30.0, relativeHumidityPercent = 55.0))
        // HR high at rest AND skin temp above baseline → combined strain
        ring.emit(sample(hr = 118, temp = 35.2, steps = 10))

        val result = pipeline.checkNow()

        assertTrue(result.decision.level.rank >= RiskLevel.ACTION.rank)
        assertTrue(result.decision.reasonCodes.contains(ReasonCode.COMBINED_PHYSIOLOGY_STRAIN))
    }

    @Test
    fun highHrAloneInCoolWeather_isCheckNotAction() {
        // Mild weather → environment at SAFE level
        weather.set(mildWeather())
        ring.emit(sample(hr = 120, temp = 34.1, steps = 5))

        val result = pipeline.checkNow()

        // Combined strain requires BOTH elevated HR and elevated temp;
        // HR alone in cool weather stays at CHECK, not ACTION
        assertEquals(RiskLevel.CHECK, result.decision.level)
        assertFalse(result.decision.reasonCodes.contains(ReasonCode.COMBINED_PHYSIOLOGY_STRAIN))
    }

    // ------------------------------------------------------------------ watch connection changes

    @Test
    fun watchDisconnects_riskStillReflectsHotWeather() {
        // 39°C / 60% RH → heat index ~109°F → ACTION from environment alone
        weather.set(WeatherSample(clock.nowMillis(), airTemperatureC = 39.0, relativeHumidityPercent = 60.0))
        ring.emit(sample(hr = 80, temp = 34.2, steps = 5))
        assertTrue(pipeline.checkNow().decision.level.rank >= RiskLevel.CHECK.rank)

        // Watch disconnects
        clock.advanceMinutes(5)
        ring.disconnect()

        val result = pipeline.checkNow()

        // Disconnect → RING_DATA_STALE, but hot weather still drives risk up
        assertTrue(result.decision.reasonCodes.contains(ReasonCode.RING_DATA_STALE))
        assertTrue(result.decision.level.rank >= RiskLevel.CHECK.rank)
    }

    @Test
    fun watchDisconnects_freshDataResumes_staleCodeDisappears() {
        weather.set(mildWeather())
        ring.emit(sample(hr = 72, temp = 34.1, steps = 5))
        ring.disconnect()

        val staleResult = pipeline.checkNow()
        assertTrue(staleResult.decision.reasonCodes.contains(ReasonCode.RING_DATA_STALE))

        // Watch reconnects with fresh data
        clock.advanceMinutes(1)
        ring.emit(sample(hr = 73, temp = 34.2, steps = 8, offsetMinutes = 1))

        val freshResult = pipeline.checkNow()

        assertFalse(freshResult.decision.reasonCodes.contains(ReasonCode.RING_DATA_STALE))
        assertEquals(Confidence.HIGH, freshResult.decision.confidence)
    }

    @Test
    fun noWatchDataAtAll_hotWeatherStillTriggersCheck() {
        // 33°C / 65% RH → heat index ~103°F → ACTION from environment
        weather.set(WeatherSample(clock.nowMillis(), airTemperatureC = 33.0, relativeHumidityPercent = 65.0))
        // ring emits nothing — latestSample() returns null

        val result = pipeline.checkNow()

        assertTrue(result.decision.level.rank >= RiskLevel.CHECK.rank)
        assertTrue(result.decision.reasonCodes.contains(ReasonCode.RING_DATA_MISSING))
    }

    // ------------------------------------------------------------------ data staleness

    @Test
    fun ringDataOlderThan15Minutes_markedStale() {
        weather.set(mildWeather())
        // Emit a sample that is 16 minutes old
        val staleTimestamp = clock.nowMillis() - 16 * 60_000L
        ring.emit(SensorSample(
            timestampMillis = staleTimestamp,
            heartRateBpm = 72,
            skinTemperatureC = 34.1,
            connected = true
        ))

        val result = pipeline.checkNow()

        assertTrue(result.decision.reasonCodes.contains(ReasonCode.RING_DATA_STALE))
        assertEquals(Confidence.MEDIUM, result.decision.confidence)
    }

    @Test
    fun ringDataFreshUnder15Minutes_notStale() {
        weather.set(mildWeather())
        ring.emit(sample(hr = 72, temp = 34.1, steps = 5))

        val result = pipeline.checkNow()

        assertFalse(result.decision.reasonCodes.contains(ReasonCode.RING_DATA_STALE))
        assertEquals(Confidence.HIGH, result.decision.confidence)
    }

    // ------------------------------------------------------------------ escalation gating

    @Test
    fun emergencyConditions_produceAllFourActions() {
        // 46°C / 70% RH → heat index ~135°F → EMERGENCY
        weather.set(WeatherSample(clock.nowMillis(), airTemperatureC = 46.0, relativeHumidityPercent = 70.0))
        ring.emit(sample(hr = 130, temp = 36.5, steps = 5))

        val result = pipeline.checkNow()

        assertEquals(RiskLevel.EMERGENCY, result.decision.level)
        assertTrue(result.plan.actions.contains(EscalationAction.NOTIFY_USER))
        assertTrue(result.plan.actions.contains(EscalationAction.ALERT_VOLUNTEER))
        assertTrue(result.plan.actions.contains(EscalationAction.SUGGEST_DIAL_CONTACT))
        assertTrue(result.plan.actions.contains(EscalationAction.LOG_EMERGENCY_EVENT))
    }

    @Test
    fun safeConditions_produceNoEscalationActions() {
        weather.set(mildWeather())
        ring.emit(sample(hr = 72, temp = 34.1, steps = 5))

        val result = pipeline.checkNow()

        assertEquals(RiskLevel.SAFE, result.decision.level)
        assertTrue(result.plan.actions.isEmpty())
    }

    @Test
    fun checkNotification_suppressedIfRepeatedWithinCooldown() {
        // Conditions that produce CHECK
        weather.set(WeatherSample(clock.nowMillis(), airTemperatureC = 28.0, relativeHumidityPercent = 60.0))

        val first = pipeline.checkNow()
        assertTrue(first.plan.actions.contains(EscalationAction.NOTIFY_USER))
        assertFalse(first.plan.suppressedByAntiSpam)

        // Same conditions 10 minutes later — within 30-min cooldown
        clock.advanceMinutes(10)
        weather.set(WeatherSample(clock.nowMillis(), airTemperatureC = 28.0, relativeHumidityPercent = 60.0))

        val second = pipeline.checkNow()
        assertTrue(second.plan.suppressedByAntiSpam)
        assertFalse(second.plan.actions.contains(EscalationAction.NOTIFY_USER))
    }

    @Test
    fun emergencyBypassesCooldown_evenAfterRecentNotification() {
        weather.set(WeatherSample(clock.nowMillis(), airTemperatureC = 28.0, relativeHumidityPercent = 60.0))
        pipeline.checkNow() // first notification fires, starts cooldown

        clock.advanceMinutes(5) // within cooldown window
        // Now conditions escalate to EMERGENCY
        weather.set(WeatherSample(clock.nowMillis(), airTemperatureC = 46.0, relativeHumidityPercent = 70.0))
        ring.emit(sample(hr = 130, temp = 36.5, steps = 5, offsetMinutes = 5))

        val result = pipeline.checkNow()

        assertEquals(RiskLevel.EMERGENCY, result.decision.level)
        assertFalse(result.plan.suppressedByAntiSpam)
        assertTrue(result.plan.actions.contains(EscalationAction.NOTIFY_USER))
    }

    // ------------------------------------------------------------------ multi-tick scenario

    @Test
    fun fullSession_safeToEmergencyOverMultipleTicks() {
        // Tick 1: cool morning, safe
        weather.set(WeatherSample(clock.nowMillis(), airTemperatureC = 22.0, relativeHumidityPercent = 40.0))
        ring.emit(sample(hr = 70, temp = 34.1, steps = 20))
        assertEquals(RiskLevel.SAFE, pipeline.checkNow().decision.level)

        // Tick 2 (+5 min): getting warm, mild HR rise — CHECK
        clock.advanceMinutes(5)
        weather.set(WeatherSample(clock.nowMillis(), airTemperatureC = 30.0, relativeHumidityPercent = 55.0))
        ring.emit(sample(hr = 90, temp = 34.8, steps = 10, offsetMinutes = 5))
        val tick2 = pipeline.checkNow()
        assertTrue(tick2.decision.level.rank >= RiskLevel.CHECK.rank)

        // Tick 3 (+10 min): hot and user shows combined physiological strain — ACTION
        clock.advanceMinutes(5)
        weather.set(WeatherSample(clock.nowMillis(), airTemperatureC = 36.0, relativeHumidityPercent = 60.0))
        ring.emit(sample(hr = 118, temp = 35.5, steps = 8, offsetMinutes = 10))
        val tick3 = pipeline.checkNow()
        assertTrue(tick3.decision.level.rank >= RiskLevel.ACTION.rank)

        // Tick 4 (+15 min): extreme heat, HR very high — EMERGENCY
        clock.advanceMinutes(5)
        weather.set(WeatherSample(clock.nowMillis(), airTemperatureC = 46.0, relativeHumidityPercent = 70.0))
        ring.emit(sample(hr = 140, temp = 37.0, steps = 5, offsetMinutes = 15))
        val tick4 = pipeline.checkNow()
        assertEquals(RiskLevel.EMERGENCY, tick4.decision.level)
    }

    // ------------------------------------------------------------------ helpers

    private fun mildWeather() = WeatherSample(
        timestampMillis = clock.nowMillis(),
        airTemperatureC = 22.0,
        relativeHumidityPercent = 40.0
    )

    private fun sample(
        hr: Int,
        temp: Double,
        steps: Int,
        offsetMinutes: Long = 0
    ) = SensorSample(
        timestampMillis = clock.nowMillis() - offsetMinutes * 60_000L,
        heartRateBpm = hr,
        skinTemperatureC = temp,
        stepsSinceLastSample = steps,
        connected = true
    )
}
