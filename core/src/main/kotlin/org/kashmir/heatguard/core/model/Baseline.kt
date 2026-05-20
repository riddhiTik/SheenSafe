package org.kashmir.heatguard.core.model

data class Baseline(
    val typicalRestingHeartRateBpm: Int? = null,
    val typicalSkinTemperatureC: Double? = null,
    val skinTemperatureStdDevC: Double? = null
)
