package org.kashmir.heatguard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.kashmir.heatguard.core.escalation.EscalationAction
import org.kashmir.heatguard.core.escalation.EscalationEngine
import org.kashmir.heatguard.core.fakes.FakeClock
import org.kashmir.heatguard.core.model.*

class EscalationEngineTest {
    private val clock = FakeClock()

    private fun makeDecision(level: RiskLevel) = RiskDecision(
        level = level,
        confidence = Confidence.HIGH,
        reasonCodes = setOf(ReasonCode.NORMAL),
        recommendedAction = "test"
    )

    @Test
    fun checkLevelProducesNotify() {
        val engine = EscalationEngine(clock)
        val plan = engine.plan(makeDecision(RiskLevel.CHECK))
        assertTrue(plan.actions.contains(EscalationAction.NOTIFY_USER))
        assertFalse(plan.actions.contains(EscalationAction.ALERT_VOLUNTEER))
    }

    @Test
    fun actionLevelIncludesAlertVolunteer() {
        val engine = EscalationEngine(clock)
        val plan = engine.plan(makeDecision(RiskLevel.ACTION))
        assertTrue(plan.actions.contains(EscalationAction.NOTIFY_USER))
        assertTrue(plan.actions.contains(EscalationAction.ALERT_VOLUNTEER))
    }

    @Test
    fun emergencyHasAllFourActions() {
        val engine = EscalationEngine(clock)
        val plan = engine.plan(makeDecision(RiskLevel.EMERGENCY))
        assertEquals(4, plan.actions.size)
        assertTrue(plan.actions.contains(EscalationAction.SUGGEST_DIAL_CONTACT))
        assertTrue(plan.actions.contains(EscalationAction.LOG_EMERGENCY_EVENT))
    }

    @Test
    fun safeProducesEmptyActions() {
        val engine = EscalationEngine(clock)
        val plan = engine.plan(makeDecision(RiskLevel.SAFE))
        assertTrue(plan.actions.isEmpty())
    }

    @Test
    fun cooldownPreventsRepeatNotify() {
        val engine = EscalationEngine(clock, notifyAgainAfterMinutes = 30)
        engine.plan(makeDecision(RiskLevel.CHECK)) // first fire
        clock.advanceMinutes(29)
        val second = engine.plan(makeDecision(RiskLevel.CHECK))
        assertTrue(second.suppressedByAntiSpam)
        assertFalse(second.actions.contains(EscalationAction.NOTIFY_USER))
    }

    @Test
    fun cooldownExpiresAfterWindow() {
        val engine = EscalationEngine(clock, notifyAgainAfterMinutes = 30)
        engine.plan(makeDecision(RiskLevel.CHECK))
        clock.advanceMinutes(31)
        val second = engine.plan(makeDecision(RiskLevel.CHECK))
        assertTrue(second.actions.contains(EscalationAction.NOTIFY_USER))
    }

    @Test
    fun emergencyBypassesCooldown() {
        val engine = EscalationEngine(clock, notifyAgainAfterMinutes = 30)
        engine.plan(makeDecision(RiskLevel.EMERGENCY)) // fires all
        clock.advanceMinutes(1) // well within cooldown
        val second = engine.plan(makeDecision(RiskLevel.EMERGENCY))
        assertTrue(second.actions.contains(EscalationAction.NOTIFY_USER))
        assertFalse(second.suppressedByAntiSpam)
    }

    @Test
    fun resetCooldownsAllowsImmediateRetrigger() {
        val engine = EscalationEngine(clock, notifyAgainAfterMinutes = 30)
        engine.plan(makeDecision(RiskLevel.CHECK))
        engine.resetCooldowns()
        val second = engine.plan(makeDecision(RiskLevel.CHECK))
        assertTrue(second.actions.contains(EscalationAction.NOTIFY_USER))
    }

    @Test
    fun coolingInstructionsMatchLevel() {
        val engine = EscalationEngine(clock)
        val checkPlan = engine.plan(makeDecision(RiskLevel.CHECK))
        assertTrue(checkPlan.coolingInstructions.isNotEmpty())

        engine.resetCooldowns()
        val emergencyPlan = engine.plan(makeDecision(RiskLevel.EMERGENCY))
        assertTrue(emergencyPlan.coolingInstructions.size > checkPlan.coolingInstructions.size)
    }
}
