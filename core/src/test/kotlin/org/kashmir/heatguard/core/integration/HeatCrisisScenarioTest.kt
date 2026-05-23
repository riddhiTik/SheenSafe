package org.kashmir.heatguard.core.integration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.kashmir.heatguard.core.escalation.EscalationAction
import org.kashmir.heatguard.core.escalation.EscalationEngine
import org.kashmir.heatguard.core.fakes.FakeClock
import org.kashmir.heatguard.core.fakes.FakeRingDataSource
import org.kashmir.heatguard.core.fakes.FakeWeatherProvider
import org.kashmir.heatguard.core.model.*
import org.kashmir.heatguard.core.pipeline.MonitoringPipeline
import org.kashmir.heatguard.core.risk.HeatRiskEngine

class HeatCrisisScenarioTest {
    private val clock = FakeClock()
    private val ring = FakeRingDataSource()
    private val weather = FakeWeatherProvider()
    private val engine = HeatRiskEngine()
    private val escalation = EscalationEngine(clock)
    private var symptomState: SymptomReport? = null

    private val pipeline = MonitoringPipeline(
        clock = clock,
        ringDataSource = ring,
        weatherProvider = weather,
        engine = engine,
        escalationEngine = escalation,
        baselineProvider = {
            Baseline(typicalRestingHeartRateBpm = 72, typicalSkinTemperatureC = 34.1, skinTemperatureStdDevC = 0.5)
        },
        riskProfileProvider = { RiskProfile(ageBand = AgeBand.ADULT, daysAcclimatized = 10) },
        symptomProvider = { symptomState }
    )

    // Scenario 1: Normal ring + normal weather → SAFE
    @Test
    fun scenario1_safeConditions() {
        weather.set(WeatherSample(clock.nowMillis(), airTemperatureC = 22.0, relativeHumidityPercent = 40.0))
        ring.emit(SensorSample(clock.nowMillis(), heartRateBpm = 72, skinTemperatureC = 34.1, stepsSinceLastSample = 15))
        val result = pipeline.checkNow()
        assertEquals(RiskLevel.SAFE, result.decision.level)
        assertTrue(result.plan.actions.isEmpty())
    }

    // Scenario 2: Rising HR + skin temp → CHECK with reason codes
    @Test
    fun scenario2_risingPhysiologyIsCheck() {
        weather.set(WeatherSample(clock.nowMillis(), airTemperatureC = 27.0, relativeHumidityPercent = 50.0))
        ring.emit(SensorSample(clock.nowMillis(), heartRateBpm = 95, skinTemperatureC = 35.2, stepsSinceLastSample = 5))
        val result = pipeline.checkNow()
        assertTrue(result.decision.level.rank >= RiskLevel.CHECK.rank)
        assertTrue(result.decision.reasonCodes.contains(ReasonCode.SKIN_TEMPERATURE_RISING))
    }

    // Scenario 3: NO_RESPONSE symptom + ACTION-level environment → EMERGENCY
    @Test
    fun scenario3_noResponseWithHotEnvIsEmergency() {
        weather.set(WeatherSample(clock.nowMillis(), airTemperatureC = 42.0, relativeHumidityPercent = 55.0))
        symptomState = SymptomReport(clock.nowMillis(), responseState = ResponseState.NO_RESPONSE)
        val result = pipeline.checkNow()
        assertEquals(RiskLevel.EMERGENCY, result.decision.level)
        assertTrue(result.decision.reasonCodes.contains(ReasonCode.NO_RESPONSE_TO_CHECKIN))
    }

    // Scenario 4: Stale ring + hot weather → at least CHECK with RING_DATA_STALE
    @Test
    fun scenario4_staleRingHotWeatherNotSilentlySafe() {
        val staleTime = clock.nowMillis() - 30 * 60_000L
        ring.emit(SensorSample(staleTime, heartRateBpm = 80, skinTemperatureC = 34.0))
        weather.set(WeatherSample(clock.nowMillis(), airTemperatureC = 40.0, relativeHumidityPercent = 60.0))
        val result = pipeline.checkNow()
        assertTrue(result.decision.level.rank >= RiskLevel.CHECK.rank)
        assertTrue(result.decision.reasonCodes.contains(ReasonCode.RING_DATA_STALE))
    }

    // Scenario 5: MANUAL_EMERGENCY → EMERGENCY, confidence HIGH
    @Test
    fun scenario5_manualEmergencyOverridesEverything() {
        weather.set(WeatherSample(clock.nowMillis(), airTemperatureC = 22.0, relativeHumidityPercent = 40.0))
        ring.emit(SensorSample(clock.nowMillis(), heartRateBpm = 72, skinTemperatureC = 34.1))
        symptomState = SymptomReport(clock.nowMillis(), responseState = ResponseState.MANUAL_EMERGENCY)
        val result = pipeline.checkNow()
        assertEquals(RiskLevel.EMERGENCY, result.decision.level)
        assertEquals(Confidence.HIGH, result.decision.confidence)
        assertTrue(result.plan.actions.contains(EscalationAction.SUGGEST_DIAL_CONTACT))
    }

    // Scenario 6: Anti-spam suppresses second CHECK notification within cooldown
    @Test
    fun scenario6_antiSpamSuppressesRepeatNotification() {
        weather.set(WeatherSample(clock.nowMillis(), airTemperatureC = 28.0, relativeHumidityPercent = 55.0))
        val first = pipeline.checkNow()
        assertTrue(first.plan.actions.contains(EscalationAction.NOTIFY_USER))

        clock.advanceMinutes(10) // within 30-min cooldown
        weather.set(WeatherSample(clock.nowMillis(), airTemperatureC = 28.0, relativeHumidityPercent = 55.0))
        val second = pipeline.checkNow()
        assertTrue(second.plan.suppressedByAntiSpam)
        assertFalse(second.plan.actions.contains(EscalationAction.NOTIFY_USER))
    }
}
