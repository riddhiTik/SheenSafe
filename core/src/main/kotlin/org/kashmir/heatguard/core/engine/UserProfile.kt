package org.kashmir.heatguard.core.engine

data class UserProfile(
    val age: Int,
    val isPregnant: Boolean = false,
    val chronicConditions: Set<String> = emptySet(),
    val heatSensitiveMedication: Boolean = false,
    val recentlyRelocatedFromCoolerClimate: Boolean = false,
    val outdoorWorkerOrLongOutdoorExposure: Boolean = false,
    val limitedWaterOrShadeAccess: Boolean = false,
    val bmi: Double? = null
)
