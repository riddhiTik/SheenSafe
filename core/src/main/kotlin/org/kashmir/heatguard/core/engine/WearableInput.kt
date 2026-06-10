package org.kashmir.heatguard.core.engine

data class WearableInput(
    val heartRateBpm: Int? = null,
    val restingHeartRateBpm: Int? = null,
    val skinTempC: Double? = null,
    val baselineSkinTempC: Double? = null,
    val activityLevel: ActivityLevel? = null,
    val gsrMegaOhms: Double? = null
)
