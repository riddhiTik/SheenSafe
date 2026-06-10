package org.kashmir.heatguard.core.engine

import kotlin.math.ln

/**
 * SheenSafe heat-risk engine — pure Kotlin, no Android dependencies.
 *
 * Produces a 0–40 additive risk score split across five components, then applies
 * safety overrides that can raise the final risk level independently of the numeric total.
 *
 * This is decision-support software, not a medical diagnostic tool. The estimated
 * core temperature is a physiological heat-strain signal for internal scoring only
 * and must never be presented as a medically accurate core temperature reading.
 */
object SheenSafeRiskEngine {

    // Wrist/hand sensor placement coefficient for estimated heat strain.
    private const val ALPHA = 0.7665

    private const val MAX_WEATHER_SCORE = 10
    private const val MAX_HR_SCORE = 8
    private const val MAX_CORE_TEMP_SCORE = 8
    private const val MAX_VULNERABILITY_SCORE = 8
    private const val MAX_SYMPTOM_SCORE = 6
    private const val MAX_TOTAL_SCORE = 40

    // ---------------------------------------------------------------------- Public API

    fun assessRisk(
        weather: WeatherInput,
        wearable: WearableInput = WearableInput(),
        profile: UserProfile,
        symptoms: SymptomInput = SymptomInput()
    ): RiskAssessment {
        val reasons = mutableListOf<String>()

        val heatIndexC = weather.heatIndexC
            ?: calculateHeatIndexC(weather.ambientTempC, weather.relativeHumidityPercent)
        val dewPointC = weather.dewPointC
            ?: calculateDewPointC(weather.ambientTempC, weather.relativeHumidityPercent)
        val estimatedCoreTempC = wearable.skinTempC?.let {
            estimateCoreTempC(it, weather.ambientTempC)
        }

        val weatherScore = scoreWeather(heatIndexC, dewPointC, weather.forecastMaxTempC, weather.isHeatwave, reasons)
        val heartRateScore = scoreHeartRate(wearable, profile.age, reasons)
        val coreTemperatureScore = scoreCoreTemperature(wearable, estimatedCoreTempC, heartRateScore, reasons)
        val vulnerabilityScore = scoreVulnerability(profile, reasons)
        val symptomScore = scoreSymptoms(symptoms, reasons)

        val rawTotal = (weatherScore + heartRateScore + coreTemperatureScore + vulnerabilityScore + symptomScore)
            .coerceAtMost(MAX_TOTAL_SCORE)

        var riskLevel = classifyRisk(rawTotal)
        riskLevel = applySafetyOverrides(
            riskLevel, heatIndexC, estimatedCoreTempC,
            heartRateScore, coreTemperatureScore, symptoms, reasons
        )

        return RiskAssessment(
            totalScore = rawTotal,
            riskLevel = riskLevel,
            componentScores = RiskComponentScores(
                weatherScore = weatherScore,
                heartRateScore = heartRateScore,
                coreTemperatureScore = coreTemperatureScore,
                vulnerabilityScore = vulnerabilityScore,
                symptomScore = symptomScore
            ),
            estimatedCoreTempC = estimatedCoreTempC,
            heatIndexC = heatIndexC,
            dewPointC = dewPointC,
            explanationReasons = reasons.toList(),
            recommendedAction = recommendedActionFor(riskLevel),
            shouldNotifyEmergencyContacts = riskLevel == SheenSafeRiskLevel.CRITICAL,
            shouldRequireUserCheckIn = riskLevel >= SheenSafeRiskLevel.HIGH,
            shouldVibrateWatch = riskLevel >= SheenSafeRiskLevel.HIGH
        )
    }

    // ---------------------------------------------------------------------- Weather

    private fun scoreWeather(
        heatIndexC: Double,
        dewPointC: Double,
        forecastMaxTempC: Double?,
        isHeatwave: Boolean,
        reasons: MutableList<String>
    ): Int {
        var score = when {
            heatIndexC < 27.0 -> 0
            heatIndexC <= 30.0 -> 2.also { reasons += "Heat index ${fmt(heatIndexC)}°C — warm." }
            heatIndexC <= 40.0 -> 4.also { reasons += "Heat index ${fmt(heatIndexC)}°C — hot." }
            heatIndexC <= 51.0 -> 6.also { reasons += "Heat index ${fmt(heatIndexC)}°C — very hot." }
            else               -> 8.also { reasons += "Heat index ${fmt(heatIndexC)}°C — extreme." }
        }
        if (dewPointC > 23.0) {
            score += 1
            reasons += "High dew point (${fmt(dewPointC)}°C) — oppressive humidity."
        }
        if (forecastMaxTempC != null && forecastMaxTempC > 35.0) {
            score += 1
            reasons += "Forecast maximum ${fmt(forecastMaxTempC)}°C — prolonged heat exposure."
        }
        if (isHeatwave) {
            score += 1
            reasons += "Active heatwave conditions."
        }
        return score.coerceAtMost(MAX_WEATHER_SCORE)
    }

    // ---------------------------------------------------------------------- Heart Rate

    private fun scoreHeartRate(
        wearable: WearableInput,
        age: Int,
        reasons: MutableList<String>
    ): Int {
        val hr = wearable.heartRateBpm ?: run {
            reasons += "Heart rate data unavailable — risk score may be underestimated."
            return 0
        }

        val hrMax = calculateHrMax(age)
        val hrPct = hr / hrMax * 100.0

        var score = when {
            hrPct < 60.0  -> 0
            hrPct < 80.0  -> 2.also { reasons += "Heart rate ${fmt(hrPct)}% of max (moderate effort)." }
            hrPct < 90.0  -> 4.also { reasons += "Heart rate ${fmt(hrPct)}% of max (high effort)." }
            hrPct <= 100.0 -> 6.also { reasons += "Heart rate ${fmt(hrPct)}% of max (near maximum)." }
            else            -> 8.also { reasons += "Heart rate exceeds estimated maximum — severe strain." }
        }

        val resting = wearable.restingHeartRateBpm
        if (resting != null) {
            val delta = hr - resting
            when {
                delta > 35  -> { score += 2; reasons += "Heart rate ${delta} bpm above resting — significantly elevated." }
                delta >= 20 -> { score += 1; reasons += "Heart rate ${delta} bpm above resting." }
            }
            // High HR during low activity is a stronger heat-strain signal.
            val act = wearable.activityLevel
            if ((act == ActivityLevel.RESTING || act == ActivityLevel.LIGHT) && delta > 35) {
                score += 1
                reasons += "Elevated HR during low activity — suggests heat strain rather than exertion."
            }
        }

        return score.coerceAtMost(MAX_HR_SCORE)
    }

    // ---------------------------------------------------------------------- Core Temperature

    private fun scoreCoreTemperature(
        wearable: WearableInput,
        estimatedCoreTempC: Double?,
        heartRateScore: Int,
        reasons: MutableList<String>
    ): Int {
        val skinTempC = wearable.skinTempC ?: run {
            reasons += "Skin temperature unavailable — risk score may be underestimated."
            return 0
        }

        // Score from estimated core temp (heat-strain indicator, not a clinical measurement).
        val coreScore = when {
            estimatedCoreTempC == null || estimatedCoreTempC < 37.5 -> 0
            estimatedCoreTempC < 38.0 -> 2.also { reasons += "Estimated heat strain ${fmt(estimatedCoreTempC)}°C (mildly elevated)." }
            estimatedCoreTempC < 39.0 -> 4.also { reasons += "Estimated heat strain ${fmt(estimatedCoreTempC)}°C (elevated)." }
            estimatedCoreTempC < 40.0 -> 6.also { reasons += "Estimated heat strain ${fmt(estimatedCoreTempC)}°C (high)." }
            else                       -> 8.also { reasons += "Estimated heat strain ${fmt(estimatedCoreTempC)}°C (critical)." }
        }

        // Score from absolute skin temperature rise above personal baseline.
        val trendScore = wearable.baselineSkinTempC?.let { baseline ->
            val delta = skinTempC - baseline
            when {
                delta >= 1.5 -> 6.also { reasons += "Skin temp ${fmt(delta)}°C above baseline (significant rise)." }
                delta >= 1.0 -> 4.also { reasons += "Skin temp ${fmt(delta)}°C above baseline (notable rise)." }
                delta >= 0.5 -> 2.also { reasons += "Skin temp ${fmt(delta)}°C above baseline (mild rise)." }
                else -> 0
            }
        } ?: 0

        // Use whichever signal is stronger.
        var score = maxOf(coreScore, trendScore)

        // Rising skin temperature combined with elevated heart rate indicates multi-system heat strain.
        val rising = wearable.baselineSkinTempC != null &&
            (skinTempC - wearable.baselineSkinTempC) >= 0.5
        if (rising && heartRateScore >= 4) {
            score += 2
            reasons += "Combined skin temperature rise and elevated heart rate — physiological heat strain."
        }

        return score.coerceAtMost(MAX_CORE_TEMP_SCORE)
    }

    // ---------------------------------------------------------------------- Vulnerability

    private fun scoreVulnerability(profile: UserProfile, reasons: MutableList<String>): Int {
        var score = 0

        when {
            profile.age >= 75 -> { score += 3; reasons += "Age ${profile.age} (75+): high heat vulnerability." }
            profile.age >= 60 -> { score += 2; reasons += "Age ${profile.age} (60–74): elevated heat vulnerability." }
        }
        if (profile.isPregnant) {
            score += 3; reasons += "Pregnancy increases heat sensitivity."
        }
        if (profile.chronicConditions.isNotEmpty()) {
            score += 2
            reasons += "Chronic conditions (${profile.chronicConditions.joinToString()}) increase risk."
        }
        if (profile.heatSensitiveMedication) {
            score += 2; reasons += "Heat-sensitive medications impair thermoregulation."
        }
        if (profile.recentlyRelocatedFromCoolerClimate) {
            score += 2; reasons += "Recent relocation from cooler climate — reduced heat acclimatization."
        }
        if (profile.outdoorWorkerOrLongOutdoorExposure) {
            score += 2; reasons += "Prolonged outdoor exposure increases cumulative heat load."
        }
        if (profile.limitedWaterOrShadeAccess) {
            score += 1; reasons += "Limited access to water or shade reduces recovery capacity."
        }
        if (profile.bmi != null && (profile.bmi >= 30.0 || profile.bmi < 18.5)) {
            score += 1; reasons += "BMI ${fmt(profile.bmi)} outside normal range — increased heat sensitivity."
        }

        return score.coerceAtMost(MAX_VULNERABILITY_SCORE)
    }

    // ---------------------------------------------------------------------- Symptoms

    private fun scoreSymptoms(symptoms: SymptomInput, reasons: MutableList<String>): Int {
        // Emergency button forces CRITICAL via safety override; it does not add a numeric score.
        if (symptoms.emergencyButtonPressed) {
            reasons += "Emergency button pressed."
        }

        var score = 0
        if (symptoms.severeSymptoms) {
            score += 4; reasons += "Severe symptoms reported (confusion, fainting, or similar)."
        }
        if (symptoms.missedSafetyCheckIn) {
            score += 4; reasons += "Missed safety check-in."
        }
        if (symptoms.moderateSymptoms) {
            score += 2; reasons += "Moderate symptoms reported (dizziness, nausea, or weakness)."
        }
        if (symptoms.mildSymptoms) {
            score += 1; reasons += "Mild symptoms reported (headache, thirst, or fatigue)."
        }
        return score.coerceAtMost(MAX_SYMPTOM_SCORE)
    }

    // ---------------------------------------------------------------------- Safety Overrides

    private fun applySafetyOverrides(
        level: SheenSafeRiskLevel,
        heatIndexC: Double,
        estimatedCoreTempC: Double?,
        heartRateScore: Int,
        coreTemperatureScore: Int,
        symptoms: SymptomInput,
        reasons: MutableList<String>
    ): SheenSafeRiskLevel {
        // Emergency button: unconditional CRITICAL.
        if (symptoms.emergencyButtonPressed) {
            reasons += "OVERRIDE: Emergency button — CRITICAL."
            return SheenSafeRiskLevel.CRITICAL
        }

        var result = level

        if (symptoms.severeSymptoms) {
            reasons += "OVERRIDE: Severe symptoms — CRITICAL."
            result = SheenSafeRiskLevel.CRITICAL
        }
        if (symptoms.missedSafetyCheckIn && result >= SheenSafeRiskLevel.HIGH) {
            reasons += "OVERRIDE: Missed check-in during elevated risk — CRITICAL."
            result = SheenSafeRiskLevel.CRITICAL
        }
        if (heatIndexC > 51.0) {
            reasons += "OVERRIDE: Extreme heat index ${fmt(heatIndexC)}°C — minimum HIGH."
            result = maxRisk(result, SheenSafeRiskLevel.HIGH)
        }
        if (heatIndexC >= 41.0 && heartRateScore >= 4) {
            reasons += "OVERRIDE: Dangerous heat-HR combination — minimum HIGH."
            result = maxRisk(result, SheenSafeRiskLevel.HIGH)
        }
        if (coreTemperatureScore >= 6 && heartRateScore >= 4) {
            reasons += "OVERRIDE: High core temperature strain + elevated HR — minimum HIGH."
            result = maxRisk(result, SheenSafeRiskLevel.HIGH)
        }
        if (estimatedCoreTempC != null && estimatedCoreTempC >= 40.0) {
            reasons += "OVERRIDE: Estimated heat strain ${fmt(estimatedCoreTempC)}°C — CRITICAL."
            result = SheenSafeRiskLevel.CRITICAL
        }
        if (estimatedCoreTempC != null && estimatedCoreTempC >= 39.0 && symptoms.moderateSymptoms) {
            reasons += "OVERRIDE: Heat strain ≥39°C with moderate symptoms — CRITICAL."
            result = SheenSafeRiskLevel.CRITICAL
        }

        return result
    }

    private fun maxRisk(a: SheenSafeRiskLevel, b: SheenSafeRiskLevel): SheenSafeRiskLevel =
        if (a.ordinal >= b.ordinal) a else b

    private operator fun SheenSafeRiskLevel.compareTo(other: SheenSafeRiskLevel): Int =
        this.ordinal - other.ordinal

    // ---------------------------------------------------------------------- Public Helpers

    fun classifyRisk(score: Int): SheenSafeRiskLevel = when {
        score <= 10 -> SheenSafeRiskLevel.LOW
        score <= 20 -> SheenSafeRiskLevel.MODERATE
        score <= 30 -> SheenSafeRiskLevel.HIGH
        else        -> SheenSafeRiskLevel.CRITICAL
    }

    /**
     * NOAA Rothfusz heat index formula.
     * Converts Celsius input to Fahrenheit, applies the polynomial, converts result back.
     */
    fun calculateHeatIndexC(tempC: Double, rhPercent: Double): Double {
        val t = tempC * 9.0 / 5.0 + 32.0
        val rh = rhPercent
        val hi = -42.379 +
            2.04901523 * t +
            10.14333127 * rh -
            0.22475541 * t * rh -
            0.00683783 * t * t -
            0.05391553 * rh * rh +
            0.00122874 * t * t * rh +
            0.00085282 * t * rh * rh -
            0.00000199 * t * t * rh * rh
        return (hi - 32.0) * 5.0 / 9.0
    }

    /** Magnus formula dew point approximation. */
    fun calculateDewPointC(tempC: Double, rhPercent: Double): Double {
        val a = 17.27
        val b = 237.7
        val gamma = (a * tempC / (b + tempC)) + ln(rhPercent / 100.0)
        return (b * gamma) / (a - gamma)
    }

    /** Tanaka equation for age-predicted maximum heart rate. */
    fun calculateHrMax(age: Int): Double = 208.0 - 0.7 * age

    /**
     * Estimates physiological heat strain from wrist skin temperature and ambient temperature.
     * Higher ambient relative to skin indicates greater heat stress on the body.
     * This is an internal heat-strain signal — do NOT display as medical core temperature.
     * Present in UI as "estimated heat strain" or "estimated body heat trend."
     */
    fun estimateCoreTempC(skinTempC: Double, ambientTempC: Double): Double =
        skinTempC + ALPHA * (ambientTempC - skinTempC)

    private fun recommendedActionFor(level: SheenSafeRiskLevel): String = when (level) {
        SheenSafeRiskLevel.LOW      -> "Current heat risk is low. Keep hydrated and continue monitoring."
        SheenSafeRiskLevel.MODERATE -> "Heat risk is rising. Move to shade, drink water, and reduce activity."
        SheenSafeRiskLevel.HIGH     -> "High heat risk detected. Stop activity, move to a cooler place, drink water, and confirm you are safe."
        SheenSafeRiskLevel.CRITICAL -> "Critical heat risk detected. Seek immediate help, start cooling, and notify emergency contacts."
    }

    private fun fmt(d: Double): String = "%.1f".format(d)
}
