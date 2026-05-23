package org.kashmir.heatguard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.kashmir.heatguard.core.model.*
import org.kashmir.heatguard.core.risk.HeatRiskEngine

class SymptomOverrideTest {
    private val now = 1_700_000_000_000L
    private val engine = HeatRiskEngine()
    private val safeWeather = WeatherSample(now, airTemperatureC = 22.0, relativeHumidityPercent = 40.0)

    private fun check(symptom: SymptomReport) = engine.assess(
        RiskInput(nowMillis = now, symptomReport = symptom, weatherSample = safeWeather)
    )

    @Test
    fun confusionAloneIsEmergency() {
        val d = check(SymptomReport(now, confusion = true))
        assertEquals(RiskLevel.EMERGENCY, d.level)
        assertTrue(d.reasonCodes.contains(ReasonCode.SEVERE_SYMPTOMS))
    }

    @Test
    fun seizureAloneIsEmergency() {
        val d = check(SymptomReport(now, seizure = true))
        assertEquals(RiskLevel.EMERGENCY, d.level)
    }

    @Test
    fun faintingAloneIsEmergency() {
        val d = check(SymptomReport(now, fainting = true))
        assertEquals(RiskLevel.EMERGENCY, d.level)
    }

    @Test
    fun cannotStandAloneIsEmergency() {
        val d = check(SymptomReport(now, cannotStand = true))
        assertEquals(RiskLevel.EMERGENCY, d.level)
    }

    @Test
    fun stoppedSweatingWhileHotIsEmergency() {
        val d = check(SymptomReport(now, stoppedSweatingWhileHot = true))
        assertEquals(RiskLevel.EMERGENCY, d.level)
    }

    @Test
    fun slurredSpeechIsEmergency() {
        val d = check(SymptomReport(now, slurredSpeech = true))
        assertEquals(RiskLevel.EMERGENCY, d.level)
    }

    @Test
    fun manualEmergencyFlagIsEmergency() {
        val d = check(SymptomReport(now, responseState = ResponseState.MANUAL_EMERGENCY))
        assertEquals(RiskLevel.EMERGENCY, d.level)
        assertTrue(d.reasonCodes.contains(ReasonCode.MANUAL_EMERGENCY))
    }

    @Test
    fun noResponseWithActionEnvIsEmergency() {
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                symptomReport = SymptomReport(now, responseState = ResponseState.NO_RESPONSE),
                weatherSample = WeatherSample(now, airTemperatureC = 42.0, relativeHumidityPercent = 55.0)
            )
        )
        assertEquals(RiskLevel.EMERGENCY, decision.level)
        assertTrue(decision.reasonCodes.contains(ReasonCode.NO_RESPONSE_TO_CHECKIN))
    }

    @Test
    fun noResponseWithCheckEnvIsAction() {
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                symptomReport = SymptomReport(now, responseState = ResponseState.NO_RESPONSE),
                weatherSample = WeatherSample(now, airTemperatureC = 28.0, relativeHumidityPercent = 55.0)
            )
        )
        assertEquals(RiskLevel.ACTION, decision.level)
    }

    @Test
    fun moderateSymptomsAreAction() {
        val d = check(SymptomReport(now, headache = true, dizziness = true))
        assertEquals(RiskLevel.ACTION, d.level)
        assertTrue(d.reasonCodes.contains(ReasonCode.MODERATE_SYMPTOMS))
    }

    @Test
    fun mildThirstIsCheck() {
        val d = check(SymptomReport(now, thirst = true))
        assertEquals(RiskLevel.CHECK, d.level)
        assertTrue(d.reasonCodes.contains(ReasonCode.MILD_SYMPTOMS))
    }
}
