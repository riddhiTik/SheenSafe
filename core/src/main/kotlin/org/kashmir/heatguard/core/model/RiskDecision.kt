package org.kashmir.heatguard.core.model

data class RiskDecision(
    val level: RiskLevel,
    val confidence: Confidence,
    val reasonCodes: Set<ReasonCode>,
    val recommendedAction: String,
    val effectiveHeatIndexC: Double? = null,
    val ringDataAgeMinutes: Long? = null,
    val weatherDataAgeMinutes: Long? = null
)
