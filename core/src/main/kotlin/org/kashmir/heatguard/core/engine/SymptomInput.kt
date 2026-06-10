package org.kashmir.heatguard.core.engine

data class SymptomInput(
    val mildSymptoms: Boolean = false,
    val moderateSymptoms: Boolean = false,
    val severeSymptoms: Boolean = false,
    val missedSafetyCheckIn: Boolean = false,
    val emergencyButtonPressed: Boolean = false
)
