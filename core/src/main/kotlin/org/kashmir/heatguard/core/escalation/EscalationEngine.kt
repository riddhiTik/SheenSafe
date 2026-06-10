package org.kashmir.heatguard.core.escalation

import org.kashmir.heatguard.core.Clock
import org.kashmir.heatguard.core.model.RiskDecision
import org.kashmir.heatguard.core.model.RiskLevel

class EscalationEngine(
    private val clock: Clock,
    private val notifyAgainAfterMinutes: Long = 30
) {
    private val lastFiredAt: MutableMap<EscalationAction, Long> = mutableMapOf()

    fun plan(decision: RiskDecision): EscalationPlan {
        val actions = mutableListOf<EscalationAction>()
        var anySupressed = false
        val isEmergency = decision.level == RiskLevel.EMERGENCY

        fun maybeAdd(action: EscalationAction, cooldownMinutes: Long) {
            if (isEmergency || canFire(action, cooldownMinutes)) {
                actions += action
            } else {
                anySupressed = true
            }
        }

        when (decision.level) {
            RiskLevel.SAFE -> { /* no actions */ }
            RiskLevel.CHECK -> {
                maybeAdd(EscalationAction.NOTIFY_USER, notifyAgainAfterMinutes)
            }
            RiskLevel.ACTION -> {
                maybeAdd(EscalationAction.NOTIFY_USER, notifyAgainAfterMinutes)
                actions += EscalationAction.ALERT_VOLUNTEER
            }
            RiskLevel.EMERGENCY -> {
                actions += EscalationAction.NOTIFY_USER
                actions += EscalationAction.ALERT_VOLUNTEER
                actions += EscalationAction.SUGGEST_DIAL_CONTACT
                actions += EscalationAction.LOG_EMERGENCY_EVENT
            }
        }

        val now = clock.nowMillis()
        actions.forEach { lastFiredAt[it] = now }

        return EscalationPlan(
            actions = actions,
            notificationTitle = "Heat Risk: ${decision.level.name}",
            notificationBody = decision.recommendedAction,
            coolingInstructions = COOLING_INSTRUCTIONS[decision.level] ?: emptyList(),
            suppressedByAntiSpam = anySupressed
        )
    }

    fun resetCooldowns() {
        lastFiredAt.clear()
    }

    private fun canFire(action: EscalationAction, cooldownMinutes: Long): Boolean {
        val last = lastFiredAt[action] ?: return true
        return clock.nowMillis() - last >= cooldownMinutes * 60_000L
    }

    companion object {
        private val COOLING_INSTRUCTIONS: Map<RiskLevel, List<String>> = mapOf(
            RiskLevel.SAFE to listOf("Keep water nearby"),
            RiskLevel.CHECK to listOf(
                "Drink water",
                "Find shade or a cooler area"
            ),
            RiskLevel.ACTION to listOf(
                "Move to shade or a cooler area",
                "Stop activity immediately",
                "Loosen clothing",
                "Tell someone nearby",
                "Drink small sips of water if able"
            ),
            RiskLevel.EMERGENCY to listOf(
                "Move to a cool place immediately",
                "Stop all activity",
                "Loosen or remove extra clothing",
                "Cool with water, wet cloths, or a fan",
                "Contact emergency help",
                "Do not leave the person alone",
                "Give small sips of water only if awake and able to swallow"
            )
        )
    }
}
