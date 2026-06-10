package org.kashmir.heatguard.core.integration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.kashmir.heatguard.core.escalation.EscalationEngine
import org.kashmir.heatguard.core.fakes.FakeClock
import org.kashmir.heatguard.core.fakes.FakeRingDataSource
import org.kashmir.heatguard.core.fakes.FakeWeatherProvider
import org.kashmir.heatguard.core.model.*
import org.kashmir.heatguard.core.pipeline.MonitoringPipeline
import org.kashmir.heatguard.core.ring.RingOutlierFilter
import org.kashmir.heatguard.core.risk.HeatRiskEngine

/**
 * Validates the complete pipeline with data shapes that the BrilliantHouse S5 watch emits:
 * HR 40–200 bpm, skin temp 32–42 °C, steps integer, SpO2 85–100, battery 0–100.
 * SpO2 and battery are stored in SensorSample but not read by HeatRiskEngine — they
 * pass through transparently and don't affect the risk level.
 */
class S5DataPipelineTest {

    private val clock = FakeClock()
    private val ring = FakeRingDataSource()
    private val weather = FakeWeatherProvider()
    private val engine = HeatRiskEngine()
    private val escalation = EscalationEngine(clock)

    // Baseline representative of a typical adult user at rest
    private val pipeline = MonitoringPipeline(
        clock = clock,
        ringDataSource = ring,
        weatherProvider = weather,
        engine = engine,
        escalationEngine = escalation,
        baselineProvider = {
            Baseline(
                typicalRestingHeartRateBpm = 72,
                typicalSkinTemperatureC = 34.1,
                skinTemperatureStdDevC = 0.5
            )
        },
        riskProfileProvider = { RiskProfile(ageBand = AgeBand.ADULT, daysAcclimatized = 10) }
    )

    // Scenario 1: Normal S5 readings + mild weather → SAFE
    @Test
    fun s5_normalReadings_safeConditions_isSafe() {
        weather.set(WeatherSample(clock.nowMillis(), airTemperatureC = 25.0, relativeHumidityPercent = 40.0))
        ring.emit(s5Sample(hr = 72, temp = 34.3, steps = 20, spo2 = 98, battery = 85))

        val result = pipeline.checkNow()

        assertEquals(RiskLevel.SAFE, result.decision.level)
        assertTrue(result.plan.actions.isEmpty())
    }

    // Scenario 2: Elevated S5 HR + high skin temp + official watch warning → ACTION
    @Test
    fun s5_highHrAndTemp_officialWatch_isAction() {
        weather.set(
            WeatherSample(
                timestampMillis = clock.nowMillis(),
                airTemperatureC = 28.0,
                relativeHumidityPercent = 55.0,
                officialWarning = OfficialHeatWarning.WATCH
            )
        )
        ring.emit(s5Sample(hr = 120, temp = 37.5, steps = 5, spo2 = 96, battery = 70))

        val result = pipeline.checkNow()

        assertTrue(result.decision.level.rank >= RiskLevel.ACTION.rank)
        assertTrue(result.decision.reasonCodes.contains(ReasonCode.COMBINED_PHYSIOLOGY_STRAIN))
    }

    // Scenario 3: S5 disconnects (ring becomes stale) → RING_DATA_STALE reason preserved
    @Test
    fun s5_ringDisconnects_staleReasonCode_isFlagged() {
        val twentyMinutesAgo = clock.nowMillis() - 20 * 60_000L
        ring.emit(SensorSample(
            timestampMillis = twentyMinutesAgo,
            heartRateBpm = 72,
            skinTemperatureC = 34.3,
            connected = false
        ))
        weather.set(WeatherSample(
            timestampMillis = clock.nowMillis(),
            airTemperatureC = 30.0,
            relativeHumidityPercent = 50.0,
            officialWarning = OfficialHeatWarning.WATCH
        ))

        val result = pipeline.checkNow()

        assertTrue(result.decision.reasonCodes.contains(ReasonCode.RING_DATA_STALE))
        assertTrue(result.decision.level.rank >= RiskLevel.CHECK.rank)
    }

    // Scenario 4: SpO2 and battery from S5 pass through without affecting risk level
    @Test
    fun s5_spo2AndBattery_dontAffectRiskLevel() {
        weather.set(WeatherSample(clock.nowMillis(), airTemperatureC = 25.0, relativeHumidityPercent = 40.0))

        ring.emit(s5Sample(hr = 72, temp = 34.3, steps = 20, spo2 = 95, battery = 10))
        val lowBatteryResult = pipeline.checkNow()

        clock.advanceMinutes(35)
        weather.set(WeatherSample(clock.nowMillis(), airTemperatureC = 25.0, relativeHumidityPercent = 40.0))
        ring.emit(s5Sample(hr = 72, temp = 34.3, steps = 20, spo2 = 95, battery = 90))
        val normalBatteryResult = pipeline.checkNow()

        assertEquals(normalBatteryResult.decision.level, lowBatteryResult.decision.level)
        assertTrue(lowBatteryResult.decision.reasonCodes.contains(ReasonCode.BATTERY_LOW))
    }

    // Scenario 5: S5 HR spike (220 bpm) is caught by the outlier filter
    @Test
    fun s5_hrSpike_isRejectedByOutlierFilter() {
        val filter = RingOutlierFilter(hrMaxDelta = 40)
        val normalHr = 75

        repeat(5) { filter.filter(normalHr, 34.0) }

        val spiked = filter.filter(220, 34.0)
        assertNull(spiked.heartRateBpm, "S5 HR spike of 220 bpm should be rejected")

        val recovered = filter.filter(normalHr + 5, 34.0)
        assertNotNull(recovered.heartRateBpm, "Normal HR after spike should be accepted")
    }

    @Test
    fun s5_tempSpike_isRejectedByOutlierFilter() {
        val filter = RingOutlierFilter(tempMaxDeltaC = 3.0)
        val normalTemp = 36.5

        repeat(5) { filter.filter(75, normalTemp) }

        val spiked = filter.filter(75, 45.0)
        assertNull(spiked.skinTemperatureC, "S5 temp spike to 45°C should be rejected")
    }

    private fun s5Sample(
        hr: Int,
        temp: Double,
        steps: Int = 0,
        spo2: Int = 98,
        battery: Int = 75
    ) = SensorSample(
        timestampMillis = clock.nowMillis(),
        heartRateBpm = hr,
        skinTemperatureC = temp,
        stepsSinceLastSample = steps,
        spo2Percent = spo2,
        batteryPercent = battery,
        connected = true
    )
}
