package org.kashmir.heatguard.core.engine

data class RiskComponentScores(
    val weatherScore: Int,
    val heartRateScore: Int,
    val coreTemperatureScore: Int,
    val vulnerabilityScore: Int,
    val symptomScore: Int
)
