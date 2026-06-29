package org.kashmir.heatguard.core.risk

import org.kashmir.heatguard.core.model.*
import kotlin.math.abs

class HeatRiskEngine(
    private val ringStaleAfterMinutes: Long = 15,
    private val weatherStaleAfterMinutes: Long = 60,
    private val lowBatteryThresholdPercent: Int = 20
) {
    companion object {
        // TODO: flip to true once live Galaxy Watch6 skin-temp + STATUS data has been reviewed
        // and the formula/threshold bands below have been validated against real readings.
        // While false, estimatedCoreTempC is still computed, returned in RiskDecision, persisted,
        // and displayed -- it simply does not influence RiskLevel yet.
        private const val CORE_TEMP_SCORING_ENABLED = false

        private const val CORE_TEMP_CHECK_THRESHOLD_C = 38.0
        private const val CORE_TEMP_ACTION_THRESHOLD_C = 39.0
        private const val CORE_TEMP_EMERGENCY_THRESHOLD_C = 40.0
    }

    fun assess(input: RiskInput): RiskDecision {
        val reasons = linkedSetOf<ReasonCode>()

        val weatherAgeMinutes = input.weatherSample?.let { ageMinutes(input.nowMillis, it.timestampMillis) }
        val ringAgeMinutes = input.sensorSample?.let { ageMinutes(input.nowMillis, it.timestampMillis) }
        val weatherStale = weatherAgeMinutes == null || weatherAgeMinutes > weatherStaleAfterMinutes
        val ringStale = ringAgeMinutes == null || ringAgeMinutes > ringStaleAfterMinutes || input.sensorSample.connected == false

        if (input.weatherSample == null) reasons += ReasonCode.WEATHER_DATA_MISSING
        if (input.sensorSample == null) reasons += ReasonCode.RING_DATA_MISSING
        if (weatherAgeMinutes != null && weatherStale) reasons += ReasonCode.WEATHER_DATA_STALE
        if (ringAgeMinutes != null && ringStale) reasons += ReasonCode.RING_DATA_STALE
        if (input.sensorSample?.batteryPercent != null && input.sensorSample.batteryPercent <= lowBatteryThresholdPercent) {
            reasons += ReasonCode.BATTERY_LOW
        }

        val envAssessment = assessEnvironment(input.weatherSample, reasons)
        val physiologyAssessment = assessPhysiology(input, envAssessment.level, ringStale, reasons)
        val profileAssessment = assessProfile(input.riskProfile, envAssessment.level, reasons)

        val coreTempEstimate = CoreTemperatureEstimator.estimate(
            skinTempC = input.sensorSample?.smoothedSkinTemperatureC,
            onDeviceAmbientC = input.sensorSample?.onDeviceAmbientTemperatureC,
            weatherAmbientC = input.weatherSample?.airTemperatureC
        )
        val coreTempAssessment = assessCoreTempEstimate(coreTempEstimate, reasons)

        var level = RiskLevel.maxOf(
            envAssessment.level,
            physiologyAssessment,
            profileAssessment,
            coreTempAssessment
        )

        if (ringStale && envAssessment.level.rank >= RiskLevel.CHECK.rank) {
            level = max(level, RiskLevel.CHECK)
        }

        if (weatherStale && physiologyAssessment.rank >= RiskLevel.CHECK.rank) {
            level = max(level, RiskLevel.CHECK)
        }

        val confidence = confidenceFor(
            level = level,
            weatherStale = weatherStale,
            ringStale = ringStale
        )

        if (reasons.isEmpty()) reasons += ReasonCode.NORMAL

        return RiskDecision(
            level = level,
            confidence = confidence,
            reasonCodes = reasons,
            recommendedAction = actionFor(level),
            effectiveHeatIndexC = envAssessment.effectiveHeatIndexC,
            ringDataAgeMinutes = ringAgeMinutes,
            weatherDataAgeMinutes = weatherAgeMinutes,
            estimatedCoreTempC = coreTempEstimate.valueC,
            coreTempEstimateValid = coreTempEstimate.valid,
            coreTempEstimateInvalidReason = coreTempEstimate.invalidReason,
            estimatedCoreTempSource = coreTempEstimate.source
        )
    }

    /**
     * Feature-gated: while CORE_TEMP_SCORING_ENABLED is false, the estimate is still computed
     * and returned in RiskDecision (for display/persistence), but never contributes to RiskLevel.
     */
    private fun assessCoreTempEstimate(
        estimate: CoreTempEstimateResult,
        reasons: MutableSet<ReasonCode>
    ): RiskLevel {
        if (!CORE_TEMP_SCORING_ENABLED) return RiskLevel.SAFE
        val value = estimate.valueC ?: return RiskLevel.SAFE
        if (!estimate.valid) return RiskLevel.SAFE

        return when {
            value >= CORE_TEMP_EMERGENCY_THRESHOLD_C -> {
                reasons += ReasonCode.ESTIMATED_CORE_TEMP_ELEVATED
                RiskLevel.EMERGENCY
            }
            value >= CORE_TEMP_ACTION_THRESHOLD_C -> {
                reasons += ReasonCode.ESTIMATED_CORE_TEMP_ELEVATED
                RiskLevel.ACTION
            }
            value >= CORE_TEMP_CHECK_THRESHOLD_C -> {
                reasons += ReasonCode.ESTIMATED_CORE_TEMP_ELEVATED
                RiskLevel.CHECK
            }
            else -> RiskLevel.SAFE
        }
    }

    private fun assessEnvironment(weather: WeatherSample?, reasons: MutableSet<ReasonCode>): EnvironmentAssessment {
        if (weather == null) return EnvironmentAssessment(RiskLevel.SAFE, null)

        var level = RiskLevel.SAFE
        val heatIndexC = weather.heatIndexC ?: run {
            val t = weather.airTemperatureC
            val rh = weather.relativeHumidityPercent
            if (t != null && rh != null) HeatIndex.calculateCelsius(t, rh) else null
        }
        val effectiveHeatIndexC = heatIndexC?.let {
            if (weather.directSun) {
                reasons += ReasonCode.DIRECT_SUN_ADJUSTMENT
                HeatIndex.fahrenheitToCelsius(HeatIndex.celsiusToFahrenheit(it) + 15.0)
            } else it
        }
        val effectiveHeatIndexF = effectiveHeatIndexC?.let(HeatIndex::celsiusToFahrenheit)

        if (effectiveHeatIndexF != null) {
            level = when {
                effectiveHeatIndexF >= 125.0 -> {
                    reasons += ReasonCode.EXTREME_HEAT_INDEX
                    RiskLevel.EMERGENCY
                }
                effectiveHeatIndexF >= 103.0 -> {
                    reasons += ReasonCode.HIGH_HEAT_INDEX
                    RiskLevel.ACTION
                }
                effectiveHeatIndexF >= 90.0 -> {
                    reasons += ReasonCode.HIGH_HEAT_INDEX
                    RiskLevel.CHECK
                }
                effectiveHeatIndexF >= 80.0 -> {
                    reasons += ReasonCode.LOW_HEAT_INDEX
                    RiskLevel.CHECK
                }
                else -> RiskLevel.SAFE
            }
        }

        when (weather.officialWarning) {
            OfficialHeatWarning.NONE -> Unit
            OfficialHeatWarning.WATCH -> {
                reasons += ReasonCode.OFFICIAL_HEAT_WATCH
                level = max(level, RiskLevel.CHECK)
            }
            OfficialHeatWarning.WARNING -> {
                reasons += ReasonCode.OFFICIAL_HEAT_WARNING
                level = max(level, RiskLevel.ACTION)
            }
            OfficialHeatWarning.EXTREME -> {
                reasons += ReasonCode.OFFICIAL_EXTREME_WARNING
                level = max(level, RiskLevel.EMERGENCY)
            }
        }

        return EnvironmentAssessment(level, effectiveHeatIndexC)
    }

    private fun assessPhysiology(
        input: RiskInput,
        environmentLevel: RiskLevel,
        ringStale: Boolean,
        reasons: MutableSet<ReasonCode>
    ): RiskLevel {
        val sample = input.sensorSample ?: return RiskLevel.SAFE
        if (ringStale) return RiskLevel.SAFE

        var level = RiskLevel.SAFE
        val temp = sample.skinTemperatureC
        val baselineTemp = input.baseline.typicalSkinTemperatureC
        val tempStdDev = input.baseline.skinTemperatureStdDevC?.takeIf { it > 0.0 }

        val temperatureDelta = if (temp != null && baselineTemp != null) temp - baselineTemp else null
        val tempRising = temperatureDelta != null && temperatureDelta >= 1.0
        val tempHigh = when {
            temperatureDelta == null -> false
            temperatureDelta >= 1.8 -> true
            tempStdDev != null && temperatureDelta >= 2.0 * tempStdDev -> true
            else -> false
        }

        if (tempRising) {
            reasons += ReasonCode.SKIN_TEMPERATURE_RISING
            level = max(level, RiskLevel.CHECK)
        }
        if (tempHigh) {
            reasons += ReasonCode.SKIN_TEMPERATURE_HIGH_RELATIVE_TO_BASELINE
            level = max(level, RiskLevel.CHECK)
        }

        val hr = sample.heartRateBpm
        val baselineHr = input.baseline.typicalRestingHeartRateBpm
        val steps = sample.stepsSinceLastSample ?: 0
        val apparentlyResting = steps <= 50
        val hrHighAtRest = hr != null && apparentlyResting && (
            hr >= 115 || (baselineHr != null && hr >= baselineHr + 30)
        )

        if (hrHighAtRest) {
            reasons += ReasonCode.HEART_RATE_HIGH_AT_REST
            level = max(level, RiskLevel.CHECK)
        }

        val combinedStrain = (tempRising || tempHigh) && hrHighAtRest
        if (combinedStrain) {
            reasons += ReasonCode.COMBINED_PHYSIOLOGY_STRAIN
            level = if (environmentLevel.rank >= RiskLevel.CHECK.rank) RiskLevel.ACTION else RiskLevel.CHECK
        }

        if (combinedStrain && environmentLevel.rank >= RiskLevel.ACTION.rank) {
            level = RiskLevel.ACTION
        }

        return level
    }

    private fun assessProfile(
        profile: RiskProfile,
        environmentLevel: RiskLevel,
        reasons: MutableSet<ReasonCode>
    ): RiskLevel {
        if (!profile.hasHigherVulnerability) return RiskLevel.SAFE
        if (environmentLevel.rank >= RiskLevel.CHECK.rank) {
            reasons += ReasonCode.RISK_PROFILE_HIGHER_VULNERABILITY
            return RiskLevel.CHECK
        }
        return RiskLevel.SAFE
    }

    private fun confidenceFor(
        level: RiskLevel,
        weatherStale: Boolean,
        ringStale: Boolean
    ): Confidence {
        if (!weatherStale && !ringStale) return Confidence.HIGH
        if (!weatherStale || !ringStale) return Confidence.MEDIUM
        return Confidence.LOW
    }

    private fun actionFor(level: RiskLevel): String = when (level) {
        RiskLevel.SAFE -> "Continue monitoring. Keep water nearby."
        RiskLevel.CHECK -> "Drink water, find shade, and answer the symptom check."
        RiskLevel.ACTION -> "Cool down now. Move to shade, stop activity, loosen clothing, and tell someone nearby."
        RiskLevel.EMERGENCY -> "Get help now. Move to a cool place, cool the person with water or wet cloths, and contact emergency support."
    }

    private fun ageMinutes(nowMillis: Long, thenMillis: Long): Long = ((nowMillis - thenMillis).coerceAtLeast(0L)) / 60_000L

    private fun max(a: RiskLevel, b: RiskLevel): RiskLevel = if (a.rank >= b.rank) a else b

    private data class EnvironmentAssessment(
        val level: RiskLevel,
        val effectiveHeatIndexC: Double?
    )
}
