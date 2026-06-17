package org.kashmir.heatguard.core.pipeline

import org.kashmir.heatguard.core.Clock
import org.kashmir.heatguard.core.escalation.EscalationEngine
import org.kashmir.heatguard.core.escalation.EscalationPlan
import org.kashmir.heatguard.core.model.Baseline
import org.kashmir.heatguard.core.model.RiskDecision
import org.kashmir.heatguard.core.model.RiskInput
import org.kashmir.heatguard.core.model.RiskProfile
import org.kashmir.heatguard.core.model.WeatherSample
import org.kashmir.heatguard.core.ring.RingDataSource
import org.kashmir.heatguard.core.risk.HeatRiskEngine
import org.kashmir.heatguard.core.weather.WeatherProvider

class MonitoringPipeline(
    private val clock: Clock,
    private val ringDataSource: RingDataSource,
    private val weatherProvider: WeatherProvider,
    private val engine: HeatRiskEngine,
    private val escalationEngine: EscalationEngine,
    private val baselineProvider: () -> Baseline,
    private val riskProfileProvider: () -> RiskProfile
) {
    fun checkNow(): PipelineResult {
        val now = clock.nowMillis()
        val input = RiskInput(
            nowMillis = now,
            sensorSample = ringDataSource.latestSample(),
            weatherSample = weatherProvider.latestWeather(now),
            baseline = baselineProvider(),
            riskProfile = riskProfileProvider()
        )
        val decision = engine.assess(input)
        val plan = escalationEngine.plan(decision)
        return PipelineResult(decision, plan, now, input.weatherSample)
    }
}

data class PipelineResult(
    val decision: RiskDecision,
    val plan: EscalationPlan,
    val nowMillis: Long,
    val weatherSample: WeatherSample? = null
)
