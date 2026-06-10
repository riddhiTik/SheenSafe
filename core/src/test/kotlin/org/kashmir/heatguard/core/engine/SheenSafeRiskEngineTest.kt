package org.kashmir.heatguard.core.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SheenSafeRiskEngineTest {

    // ------------------------------------------------------------------ TC1: Low Risk

    @Test
    fun tc1_lowRisk_allScoresZeroExceptWeather() {
        val result = SheenSafeRiskEngine.assessRisk(
            weather = WeatherInput(
                ambientTempC = 27.0,
                relativeHumidityPercent = 45.0,
                heatIndexC = 28.0,
                dewPointC = 16.0,
                forecastMaxTempC = 30.0
            ),
            wearable = WearableInput(
                heartRateBpm = 82,
                restingHeartRateBpm = 70,
                skinTempC = 33.8,
                baselineSkinTempC = 33.7
            ),
            profile = UserProfile(age = 24),
            symptoms = SymptomInput()
        )

        assertEquals(2, result.componentScores.weatherScore, "Weather score")
        assertEquals(0, result.componentScores.heartRateScore, "HR score")
        assertEquals(0, result.componentScores.coreTemperatureScore, "Core temp score")
        assertEquals(0, result.componentScores.vulnerabilityScore, "Vulnerability score")
        assertEquals(0, result.componentScores.symptomScore, "Symptom score")
        assertEquals(2, result.totalScore, "Total score")
        assertEquals(SheenSafeRiskLevel.LOW, result.riskLevel)
        assertEquals(false, result.shouldNotifyEmergencyContacts)
        assertEquals(false, result.shouldRequireUserCheckIn)
        assertEquals(false, result.shouldVibrateWatch)
    }

    // ------------------------------------------------------------------ TC2: Moderate Risk

    @Test
    fun tc2_moderateRisk_expectedComponentScores() {
        val result = SheenSafeRiskEngine.assessRisk(
            weather = WeatherInput(
                ambientTempC = 35.0,
                relativeHumidityPercent = 50.0,
                heatIndexC = 36.0,
                dewPointC = 24.0,
                forecastMaxTempC = 35.0 // exactly 35 — NOT > 35, so no bonus
            ),
            wearable = WearableInput(
                heartRateBpm = 105,
                restingHeartRateBpm = 75,
                skinTempC = 34.7,
                baselineSkinTempC = 34.0
            ),
            profile = UserProfile(
                age = 45,
                outdoorWorkerOrLongOutdoorExposure = true
            ),
            symptoms = SymptomInput()
        )

        // heat index 36 → 4, dew point 24 > 23 → +1, forecastMax 35 NOT > 35 → no bonus
        assertEquals(5, result.componentScores.weatherScore, "Weather score")

        // HRmax = 208 − 0.7*45 = 176.5; hrPct = 105/176.5*100 = 59.5% < 60 → base 0
        // delta = 30 bpm (in 20..35) → +1; total HR score = 1
        assertEquals(1, result.componentScores.heartRateScore, "HR score")

        // estimatedCore = 34.7 + 0.7665*(35−34.7) = 34.93 → < 37.5 → coreScore=0
        // delta = 0.7°C (0.5..1.0) → trendScore = 2; max(0,2)=2; HR<4 no combo
        assertEquals(2, result.componentScores.coreTemperatureScore, "Core temp score")

        // outdoor worker → +2; age 45 < 60 → no age bonus
        assertEquals(2, result.componentScores.vulnerabilityScore, "Vulnerability score")
        assertEquals(0, result.componentScores.symptomScore, "Symptom score")

        // total = 5+1+2+2+0 = 10 → LOW (0–10)
        assertEquals(10, result.totalScore)
        assertEquals(SheenSafeRiskLevel.LOW, result.riskLevel)
        assertEquals(false, result.shouldNotifyEmergencyContacts)
    }

    // ------------------------------------------------------------------ TC3: High Risk

    @Test
    fun tc3_highRisk_vulnerableRelocatedUser() {
        val result = SheenSafeRiskEngine.assessRisk(
            weather = WeatherInput(
                ambientTempC = 38.0,
                relativeHumidityPercent = 45.0,
                heatIndexC = 42.0,
                dewPointC = 25.0,
                forecastMaxTempC = 39.0
            ),
            wearable = WearableInput(
                heartRateBpm = 128,
                restingHeartRateBpm = 76,
                skinTempC = 35.3,
                baselineSkinTempC = 34.0
            ),
            profile = UserProfile(
                age = 67,
                chronicConditions = setOf("diabetes"),
                recentlyRelocatedFromCoolerClimate = true
            ),
            symptoms = SymptomInput()
        )

        // 42°C → 6, dew 25 > 23 → +1, forecastMax 39 > 35 → +1 → total 8
        assertEquals(8, result.componentScores.weatherScore, "Weather score")

        // HRmax = 208 − 0.7*67 = 161.1; hrPct = 128/161.1 = 79.45% → base 2
        // delta = 52 > 35 → +2; no activityLevel specified → total 4
        assertEquals(4, result.componentScores.heartRateScore, "HR score")

        // estimatedCore = 35.3 + 0.7665*(38−35.3) = 37.37 → < 37.5 → coreScore=0
        // delta = 1.3°C (1.0..1.5) → trendScore=4; max(0,4)=4
        // rising (1.3≥0.5) AND hrScore 4 ≥ 4 → +2; total = 6
        assertEquals(6, result.componentScores.coreTemperatureScore, "Core temp score")

        // age 67 (60–74) → +2, diabetes → +2, relocation → +2 → total 6
        assertEquals(6, result.componentScores.vulnerabilityScore, "Vulnerability score")
        assertEquals(0, result.componentScores.symptomScore, "Symptom score")

        assertEquals(24, result.totalScore, "Total score")
        assertEquals(SheenSafeRiskLevel.HIGH, result.riskLevel)
        assertEquals(true, result.shouldVibrateWatch)
        assertEquals(true, result.shouldRequireUserCheckIn)
        assertEquals(false, result.shouldNotifyEmergencyContacts)
    }

    // ------------------------------------------------------------------ TC4: Critical Risk

    @Test
    fun tc4_criticalRisk_missedCheckInAndModerateSymptoms() {
        val result = SheenSafeRiskEngine.assessRisk(
            weather = WeatherInput(
                ambientTempC = 40.0,
                relativeHumidityPercent = 45.0,
                heatIndexC = 45.0,
                dewPointC = 26.0,
                forecastMaxTempC = 40.0
            ),
            wearable = WearableInput(
                heartRateBpm = 145,
                restingHeartRateBpm = 78,
                skinTempC = 36.0,
                baselineSkinTempC = 34.2
            ),
            profile = UserProfile(
                age = 72,
                chronicConditions = setOf("heart_disease"),
                outdoorWorkerOrLongOutdoorExposure = true
            ),
            symptoms = SymptomInput(
                moderateSymptoms = true,
                missedSafetyCheckIn = true
            )
        )

        // 45°C → 6, dew 26 > 23 → +1, forecast 40 > 35 → +1 → 8
        assertEquals(8, result.componentScores.weatherScore, "Weather score")

        // HRmax = 208 − 0.7*72 = 157.6; hrPct = 145/157.6 = 92.0% → base 6
        // delta = 67 > 35 → +2; total = 8 (capped)
        assertEquals(8, result.componentScores.heartRateScore, "HR score")

        // estimatedCore = 36.0 + 0.7665*(40−36) = 39.07 → score 6
        // delta = 1.8 ≥ 1.5 → trendScore=6; max(6,6)=6
        // rising (1.8≥0.5) AND hrScore 8 ≥ 4 → +2; total = 8
        assertEquals(8, result.componentScores.coreTemperatureScore, "Core temp score")

        // age 72 (60–74) → +2, heart_disease → +2, outdoor → +2 → 6
        assertEquals(6, result.componentScores.vulnerabilityScore, "Vulnerability score")

        // moderate +2, missedCheckIn +4 → 6 (capped at 6)
        assertEquals(6, result.componentScores.symptomScore, "Symptom score")

        // total = 8+8+8+6+6 = 36 → CRITICAL by score alone
        assertEquals(36, result.totalScore)
        assertEquals(SheenSafeRiskLevel.CRITICAL, result.riskLevel)
        assertEquals(true, result.shouldVibrateWatch)
        assertEquals(true, result.shouldRequireUserCheckIn)
        assertEquals(true, result.shouldNotifyEmergencyContacts)
    }

    // ------------------------------------------------------------------ TC5: Emergency Button

    @Test
    fun tc5_emergencyButtonForcesCritical_regardlessOfScore() {
        val result = SheenSafeRiskEngine.assessRisk(
            weather = WeatherInput(
                ambientTempC = 27.0,
                relativeHumidityPercent = 45.0,
                heatIndexC = 28.0,
                dewPointC = 16.0
            ),
            wearable = WearableInput(
                heartRateBpm = 75,
                restingHeartRateBpm = 70,
                skinTempC = 33.5,
                baselineSkinTempC = 33.5
            ),
            profile = UserProfile(age = 24),
            symptoms = SymptomInput(emergencyButtonPressed = true)
        )

        assertEquals(SheenSafeRiskLevel.CRITICAL, result.riskLevel)
        assertEquals(true, result.shouldNotifyEmergencyContacts)
        assertTrue(result.totalScore < 40, "Score should remain calculated normally (not forced to 40)")
        assertTrue(
            result.explanationReasons.any { "Emergency button pressed" in it },
            "Explanation must mention emergency button"
        )
    }

    // ------------------------------------------------------------------ TC6: Missing Wearable Data

    @Test
    fun tc6_missingWearableData_warningsAndModerateRisk() {
        val result = SheenSafeRiskEngine.assessRisk(
            weather = WeatherInput(
                ambientTempC = 40.0,
                relativeHumidityPercent = 55.0,
                heatIndexC = 43.0,
                dewPointC = 25.0,
                forecastMaxTempC = 39.0
            ),
            wearable = WearableInput(), // no HR, no skin temp
            profile = UserProfile(
                age = 65,
                recentlyRelocatedFromCoolerClimate = true
            ),
            symptoms = SymptomInput()
        )

        // 43°C → 6, dew 25 > 23 → +1, forecast 39 > 35 → +1 → 8
        assertEquals(8, result.componentScores.weatherScore, "Weather score")
        assertEquals(0, result.componentScores.heartRateScore, "HR score (missing)")
        assertEquals(0, result.componentScores.coreTemperatureScore, "Core temp score (missing)")

        // age 65 (60–74) → +2, relocation → +2 → 4
        assertEquals(4, result.componentScores.vulnerabilityScore, "Vulnerability score")
        assertEquals(0, result.componentScores.symptomScore)

        // total = 8+0+0+4+0 = 12 → MODERATE
        assertEquals(12, result.totalScore)
        assertEquals(SheenSafeRiskLevel.MODERATE, result.riskLevel)

        assertTrue(
            result.explanationReasons.any { "Heart rate" in it && "unavailable" in it },
            "Should warn about missing heart rate"
        )
        assertTrue(
            result.explanationReasons.any { "Skin temperature" in it && "unavailable" in it },
            "Should warn about missing skin temperature"
        )
    }

    // ------------------------------------------------------------------ TC7: Heat Index Safety Override

    @Test
    fun tc7_heatIndexOverride_lowScoreEscalatedToHigh() {
        // heatIndexC = 52 is provided directly. ambientTempC = 38, skinTempC = 35.5 stable.
        // estimatedCore = 35.5 + 0.7665*(38−35.5) = 37.4°C → < 37.5, no critical override.
        val result = SheenSafeRiskEngine.assessRisk(
            weather = WeatherInput(
                ambientTempC = 38.0,
                relativeHumidityPercent = 60.0,
                heatIndexC = 52.0,
                dewPointC = 27.0,
                forecastMaxTempC = 42.0
            ),
            wearable = WearableInput(
                heartRateBpm = 75,
                restingHeartRateBpm = 70,
                skinTempC = 35.5,
                baselineSkinTempC = 35.5 // no delta → trendScore = 0
            ),
            profile = UserProfile(age = 24),
            symptoms = SymptomInput()
        )

        // heatIndexC 52 > 51 → base 8; dew 27 > 23 → +1; forecast 42 > 35 → +1 → 10 (capped)
        assertEquals(10, result.componentScores.weatherScore, "Weather score")
        assertEquals(0, result.componentScores.heartRateScore, "HR score")
        assertEquals(0, result.componentScores.coreTemperatureScore, "Core temp score")
        assertEquals(0, result.componentScores.vulnerabilityScore)
        assertEquals(0, result.componentScores.symptomScore)

        // total = 10 → classifyRisk → LOW; but heatIndexC > 51 override → HIGH
        assertEquals(10, result.totalScore)
        assertEquals(SheenSafeRiskLevel.HIGH, result.riskLevel, "Override must escalate to HIGH")
        assertEquals(true, result.shouldRequireUserCheckIn)
        assertEquals(true, result.shouldVibrateWatch)
        assertEquals(false, result.shouldNotifyEmergencyContacts)

        assertTrue(
            result.explanationReasons.any { "Extreme heat index" in it || "51" in it },
            "Should mention heat index override"
        )
    }

    // ------------------------------------------------------------------ Helper function tests

    @Test
    fun calculateHeatIndex_returnsReasonableValue() {
        // 35°C at 60% RH should give a heat index well above ambient.
        val hi = SheenSafeRiskEngine.calculateHeatIndexC(35.0, 60.0)
        assertTrue(hi > 35.0, "Heat index should be higher than ambient in humid conditions")
    }

    @Test
    fun calculateDewPoint_returnsReasonableValue() {
        val dew = SheenSafeRiskEngine.calculateDewPointC(30.0, 80.0)
        assertTrue(dew > 20.0 && dew < 30.0, "Dew point should be below ambient but above 20°C")
    }

    @Test
    fun classifyRisk_boundaries() {
        assertEquals(SheenSafeRiskLevel.LOW, SheenSafeRiskEngine.classifyRisk(0))
        assertEquals(SheenSafeRiskLevel.LOW, SheenSafeRiskEngine.classifyRisk(10))
        assertEquals(SheenSafeRiskLevel.MODERATE, SheenSafeRiskEngine.classifyRisk(11))
        assertEquals(SheenSafeRiskLevel.MODERATE, SheenSafeRiskEngine.classifyRisk(20))
        assertEquals(SheenSafeRiskLevel.HIGH, SheenSafeRiskEngine.classifyRisk(21))
        assertEquals(SheenSafeRiskLevel.HIGH, SheenSafeRiskEngine.classifyRisk(30))
        assertEquals(SheenSafeRiskLevel.CRITICAL, SheenSafeRiskEngine.classifyRisk(31))
        assertEquals(SheenSafeRiskLevel.CRITICAL, SheenSafeRiskEngine.classifyRisk(40))
    }

    @Test
    fun estimateCoreTemp_hotAmbientIncreasesEstimate() {
        // Ambient 42°C, skin 36°C → estimated core should be above skin temp.
        val core = SheenSafeRiskEngine.estimateCoreTempC(36.0, 42.0)
        assertTrue(core > 36.0, "Hot ambient should increase estimated heat strain above skin temp")
    }

    @Test
    fun estimateCoreTemp_coolAmbientDecreasesEstimate() {
        // Ambient 22°C, skin 33.5°C → estimated core should be below skin temp.
        val core = SheenSafeRiskEngine.estimateCoreTempC(33.5, 22.0)
        assertTrue(core < 33.5, "Cool ambient should decrease estimated heat strain below skin temp")
    }
}
