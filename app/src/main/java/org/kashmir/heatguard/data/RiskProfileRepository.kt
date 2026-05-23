package org.kashmir.heatguard.data

import org.kashmir.heatguard.core.model.RiskProfile

interface RiskProfileRepository {
    fun getRiskProfile(): RiskProfile
    fun setRiskProfile(profile: RiskProfile)
}
