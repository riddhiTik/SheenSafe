package org.kashmir.heatguard.core.model

data class SymptomReport(
    val timestampMillis: Long,
    val responseState: ResponseState = ResponseState.NOT_ASKED,
    val thirst: Boolean = false,
    val headache: Boolean = false,
    val nausea: Boolean = false,
    val dizziness: Boolean = false,
    val weakness: Boolean = false,
    val heavySweating: Boolean = false,
    val confusion: Boolean = false,
    val slurredSpeech: Boolean = false,
    val fainting: Boolean = false,
    val seizure: Boolean = false,
    val cannotStand: Boolean = false,
    val stoppedSweatingWhileHot: Boolean = false
) {
    val hasMildSymptoms: Boolean
        get() = thirst

    val hasModerateSymptoms: Boolean
        get() = headache || nausea || dizziness || weakness || heavySweating

    val hasSevereSymptoms: Boolean
        get() = confusion || slurredSpeech || fainting || seizure || cannotStand || stoppedSweatingWhileHot ||
            responseState == ResponseState.MANUAL_EMERGENCY
}
