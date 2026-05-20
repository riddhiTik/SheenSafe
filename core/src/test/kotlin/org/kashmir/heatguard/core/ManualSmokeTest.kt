package org.kashmir.heatguard.core

import org.kashmir.heatguard.core.model.*
import org.kashmir.heatguard.core.risk.HeatRiskEngine

fun main() {
    val now = System.currentTimeMillis()
    val decision = HeatRiskEngine().assess(
        RiskInput(
            nowMillis = now,
            weatherSample = WeatherSample(now, airTemperatureC = 39.0, relativeHumidityPercent = 55.0, directSun = true),
            sensorSample = SensorSample(now, heartRateBpm = 126, skinTemperatureC = 36.4, stepsSinceLastSample = 0, batteryPercent = 70),
            baseline = Baseline(typicalRestingHeartRateBpm = 78, typicalSkinTemperatureC = 34.1, skinTemperatureStdDevC = 0.5),
            symptomReport = SymptomReport(now, dizziness = true)
        )
    )
    println(decision)
}
