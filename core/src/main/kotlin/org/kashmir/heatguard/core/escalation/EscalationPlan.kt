package org.kashmir.heatguard.core.escalation

data class EscalationPlan(
    val actions: List<EscalationAction>,
    val notificationTitle: String,
    val notificationBody: String,
    val coolingInstructions: List<String>,
    val suppressedByAntiSpam: Boolean = false
)
