package org.kashmir.heatguard.core.model

data class RiskProfile(
    val ageBand: AgeBand = AgeBand.UNKNOWN,
    val pregnant: Boolean = false,
    val chronicIllness: Boolean = false,
    val outdoorWorkOrTravel: Boolean = false,
    val fastingOrFluidRestriction: Boolean = false,
    val priorHeatIllness: Boolean = false,
    val limitedCoolingAccess: Boolean = false,
    val daysAcclimatized: Int = 0
) {
    val hasHigherVulnerability: Boolean
        get() = ageBand == AgeBand.CHILD ||
            ageBand == AgeBand.OLDER_ADULT ||
            pregnant ||
            chronicIllness ||
            fastingOrFluidRestriction ||
            priorHeatIllness ||
            limitedCoolingAccess ||
            daysAcclimatized < 5
}
