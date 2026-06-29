package org.kashmir.heatguard.core.risk

import kotlin.math.abs

enum class AmbientTempSource { ON_DEVICE_SENSOR, WEATHER_API, NONE }

enum class CoreTempInvalidReason {
    MISSING_SKIN_TEMP, MISSING_AMBIENT, OUT_OF_PLAUSIBLE_RANGE, AMBIENT_SOURCE_DISCREPANCY
}

data class CoreTempEstimateResult(
    val valueC: Double?,
    val valid: Boolean,
    val source: AmbientTempSource,
    val invalidReason: CoreTempInvalidReason?
)

/**
 * Estimates a heat-strain core-temperature signal from skin temperature and ambient temperature.
 * This is a decision-support estimate only — never a medical core-temperature reading.
 *
 * Tcore = Tskin + alpha * (Tskin - Tambient), alpha = 0.7665 for wrist/hand placement.
 *
 * This linear formula can produce physiologically implausible results when ambient temperature
 * approaches or exceeds skin temperature (exactly the hot-ambient conditions this app monitors
 * for). The formula is applied EXACTLY as specified, with NO clamping: an out-of-range result is
 * reported as invalid with a reason, never coerced into a plausible-looking number.
 */
object CoreTemperatureEstimator {
    const val ALPHA_WRIST_HAND = 0.7665
    private const val PLAUSIBLE_MIN_C = 35.0
    private const val PLAUSIBLE_MAX_C = 42.0

    // Tunable: how far apart on-device vs. weather ambient can be before we distrust both.
    private const val AMBIENT_DISCREPANCY_THRESHOLD_C = 5.0

    fun estimate(
        skinTempC: Double?,
        onDeviceAmbientC: Double?,
        weatherAmbientC: Double?,
        alpha: Double = ALPHA_WRIST_HAND
    ): CoreTempEstimateResult {
        if (skinTempC == null) {
            return CoreTempEstimateResult(null, false, AmbientTempSource.NONE, CoreTempInvalidReason.MISSING_SKIN_TEMP)
        }

        if (hasAmbientDiscrepancy(onDeviceAmbientC, weatherAmbientC)) {
            return CoreTempEstimateResult(null, false, AmbientTempSource.NONE, CoreTempInvalidReason.AMBIENT_SOURCE_DISCREPANCY)
        }

        val (ambientC, source) = resolveAmbient(onDeviceAmbientC, weatherAmbientC)
            ?: return CoreTempEstimateResult(null, false, AmbientTempSource.NONE, CoreTempInvalidReason.MISSING_AMBIENT)

        val raw = skinTempC + alpha * (skinTempC - ambientC)
        return if (raw in PLAUSIBLE_MIN_C..PLAUSIBLE_MAX_C) {
            CoreTempEstimateResult(raw, true, source, null)
        } else {
            CoreTempEstimateResult(null, false, source, CoreTempInvalidReason.OUT_OF_PLAUSIBLE_RANGE)
        }
    }

    private fun hasAmbientDiscrepancy(onDeviceC: Double?, weatherC: Double?): Boolean =
        onDeviceC != null && weatherC != null && abs(onDeviceC - weatherC) > AMBIENT_DISCREPANCY_THRESHOLD_C

    private fun resolveAmbient(onDeviceC: Double?, weatherC: Double?): Pair<Double, AmbientTempSource>? = when {
        onDeviceC != null -> onDeviceC to AmbientTempSource.ON_DEVICE_SENSOR
        weatherC != null -> weatherC to AmbientTempSource.WEATHER_API
        else -> null
    }
}
