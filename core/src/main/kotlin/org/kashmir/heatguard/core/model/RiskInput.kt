package org.kashmir.heatguard.core.model

data class RiskInput(
    val nowMillis: Long,
    val sensorSample: SensorSample? = null,
    val weatherSample: WeatherSample? = null,
    val symptomReport: SymptomReport? = null,
    val baseline: Baseline = Baseline(),
    val riskProfile: RiskProfile = RiskProfile()
)
