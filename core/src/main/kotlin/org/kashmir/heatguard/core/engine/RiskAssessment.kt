package org.kashmir.heatguard.core.engine

data class RiskAssessment(
    val totalScore: Int,
    val riskLevel: SheenSafeRiskLevel,
    val componentScores: RiskComponentScores,
    val estimatedCoreTempC: Double?,
    val heatIndexC: Double,
    val dewPointC: Double,
    val explanationReasons: List<String>,
    val recommendedAction: String,
    val shouldNotifyEmergencyContacts: Boolean,
    val shouldRequireUserCheckIn: Boolean,
    val shouldVibrateWatch: Boolean
)
