package org.kashmir.heatguard.core.model

import org.kashmir.heatguard.core.risk.AmbientTempSource
import org.kashmir.heatguard.core.risk.CoreTempInvalidReason

data class RiskDecision(
    val level: RiskLevel,
    val confidence: Confidence,
    val reasonCodes: Set<ReasonCode>,
    val recommendedAction: String,
    val effectiveHeatIndexC: Double? = null,
    val ringDataAgeMinutes: Long? = null,
    val weatherDataAgeMinutes: Long? = null,
    // Decision-support heat-strain estimate only — never a medical core-temperature reading.
    // See CoreTemperatureEstimator. estimatedCoreTempC is non-null only when coreTempEstimateValid
    // is true; check validity/source/invalidReason before using the value for anything.
    val estimatedCoreTempC: Double? = null,
    val coreTempEstimateValid: Boolean = false,
    val coreTempEstimateInvalidReason: CoreTempInvalidReason? = null,
    val estimatedCoreTempSource: AmbientTempSource? = null
)
