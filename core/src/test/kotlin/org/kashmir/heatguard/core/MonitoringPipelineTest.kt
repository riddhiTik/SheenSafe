package org.kashmir.heatguard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.kashmir.heatguard.core.escalation.EscalationEngine
import org.kashmir.heatguard.core.fakes.FakeClock
import org.kashmir.heatguard.core.fakes.FakeRingDataSource
import org.kashmir.heatguard.core.fakes.FakeWeatherProvider
import org.kashmir.heatguard.core.model.*
import org.kashmir.heatguard.core.pipeline.MonitoringPipeline
import org.kashmir.heatguard.core.risk.HeatRiskEngine

class MonitoringPipelineTest {
    private val clock = FakeClock()
    private val ring = FakeRingDataSource()
    private val weather = FakeWeatherProvider()
    private val engine = HeatRiskEngine()
    private val escalation = EscalationEngine(clock)

    private fun makePipeline(
        baseline: Baseline = Baseline(),
        profile: RiskProfile = RiskProfile(ageBand = AgeBand.ADULT, daysAcclimatized = 10)
    ) = MonitoringPipeline(
        clock = clock,
        ringDataSource = ring,
        weatherProvider = weather,
        engine = engine,
        escalationEngine = escalation,
        baselineProvider = { baseline },
        riskProfileProvider = { profile }
    )

    @Test
    fun checkNowReturnsDecisionAndPlan() {
        weather.set(WeatherSample(clock.nowMillis(), airTemperatureC = 22.0, relativeHumidityPercent = 40.0))
        val result = makePipeline().checkNow()
        assertEquals(RiskLevel.SAFE, result.decision.level)
        assertEquals(clock.nowMillis(), result.nowMillis)
    }

    @Test
    fun clockTimePassedToResult() {
        val before = clock.nowMillis()
        val result = makePipeline().checkNow()
        assertEquals(before, result.nowMillis)
    }

    @Test
    fun allProvidersAreCalled() {
        makePipeline().checkNow()
        assertEquals(1, weather.callCount)
    }

    @Test
    fun ringDisconnectReflectedInDecision() {
        ring.emit(SensorSample(clock.nowMillis(), heartRateBpm = 80, skinTemperatureC = 34.0))
        ring.disconnect()
        weather.set(WeatherSample(clock.nowMillis(), airTemperatureC = 38.0, relativeHumidityPercent = 55.0))
        val result = makePipeline().checkNow()
        assertTrue(result.decision.reasonCodes.contains(ReasonCode.RING_DATA_STALE))
    }

    @Test
    fun noWeatherProducesLowConfidence() {
        weather.set(null)
        val result = makePipeline().checkNow()
        assertEquals(Confidence.LOW, result.decision.confidence)
        assertTrue(result.decision.reasonCodes.contains(ReasonCode.WEATHER_DATA_MISSING))
    }

}
