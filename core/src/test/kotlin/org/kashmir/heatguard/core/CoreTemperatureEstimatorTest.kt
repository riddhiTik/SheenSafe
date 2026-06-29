package org.kashmir.heatguard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.kashmir.heatguard.core.risk.AmbientTempSource
import org.kashmir.heatguard.core.risk.CoreTemperatureEstimator
import org.kashmir.heatguard.core.risk.CoreTempInvalidReason

class CoreTemperatureEstimatorTest {

    @Test
    fun validEstimateWithOnDeviceAmbient() {
        // 34.0 + 0.7665 * (34.0 - 30.0) = 37.066
        val result = CoreTemperatureEstimator.estimate(skinTempC = 34.0, onDeviceAmbientC = 30.0, weatherAmbientC = null)
        assertTrue(result.valid)
        assertEquals(AmbientTempSource.ON_DEVICE_SENSOR, result.source)
        assertNull(result.invalidReason)
        assertEquals(37.066, result.valueC!!, 0.001)
    }

    @Test
    fun missingSkinTempIsInvalid() {
        val result = CoreTemperatureEstimator.estimate(skinTempC = null, onDeviceAmbientC = 30.0, weatherAmbientC = 25.0)
        assertFalse(result.valid)
        assertEquals(CoreTempInvalidReason.MISSING_SKIN_TEMP, result.invalidReason)
        assertNull(result.valueC)
    }

    @Test
    fun missingBothAmbientSourcesIsInvalid() {
        val result = CoreTemperatureEstimator.estimate(skinTempC = 34.0, onDeviceAmbientC = null, weatherAmbientC = null)
        assertFalse(result.valid)
        assertEquals(CoreTempInvalidReason.MISSING_AMBIENT, result.invalidReason)
        assertEquals(AmbientTempSource.NONE, result.source)
    }

    @Test
    fun weatherOnlyFallsBackToWeatherSource() {
        val result = CoreTemperatureEstimator.estimate(skinTempC = 34.0, onDeviceAmbientC = null, weatherAmbientC = 30.0)
        assertTrue(result.valid)
        assertEquals(AmbientTempSource.WEATHER_API, result.source)
    }

    @Test
    fun bothPresentAndAgreeingPrefersOnDevice() {
        val result = CoreTemperatureEstimator.estimate(skinTempC = 34.0, onDeviceAmbientC = 29.0, weatherAmbientC = 30.0)
        assertTrue(result.valid)
        assertEquals(AmbientTempSource.ON_DEVICE_SENSOR, result.source)
    }

    @Test
    fun bothPresentAndDisagreeingBeyondThresholdIsInvalid() {
        // 11 degree discrepancy, threshold is 5.0
        val result = CoreTemperatureEstimator.estimate(skinTempC = 34.0, onDeviceAmbientC = 19.0, weatherAmbientC = 30.0)
        assertFalse(result.valid)
        assertEquals(CoreTempInvalidReason.AMBIENT_SOURCE_DISCREPANCY, result.invalidReason)
        assertNull(result.valueC)
    }

    @Test
    fun bothPresentAndDisagreeingExactlyAtThresholdIsValid() {
        // exactly 5.0 degree discrepancy, threshold is inclusive (<=)
        val result = CoreTemperatureEstimator.estimate(skinTempC = 34.0, onDeviceAmbientC = 25.0, weatherAmbientC = 30.0)
        assertTrue(result.valid)
    }

    @Test
    fun resultBelowPlausibleRangeIsInvalid() {
        // Hot-ambient case: 34.0 + 0.7665 * (34.0 - 38.0) = 30.934 -> below 35.0 plausible floor
        val result = CoreTemperatureEstimator.estimate(skinTempC = 34.0, onDeviceAmbientC = 38.0, weatherAmbientC = null)
        assertFalse(result.valid)
        assertEquals(CoreTempInvalidReason.OUT_OF_PLAUSIBLE_RANGE, result.invalidReason)
        assertNull(result.valueC)
    }

    @Test
    fun resultAbovePlausibleRangeIsInvalid() {
        // Cold-ambient case: 34.0 + 0.7665 * (34.0 - 20.0) = 44.731 -> above 42.0 plausible ceiling
        val result = CoreTemperatureEstimator.estimate(skinTempC = 34.0, onDeviceAmbientC = 20.0, weatherAmbientC = null)
        assertFalse(result.valid)
        assertEquals(CoreTempInvalidReason.OUT_OF_PLAUSIBLE_RANGE, result.invalidReason)
        assertNull(result.valueC)
    }

    @Test
    fun noClampingEverOccurs_outOfRangeValueIsNullNotCoerced() {
        val result = CoreTemperatureEstimator.estimate(skinTempC = 34.0, onDeviceAmbientC = 38.0, weatherAmbientC = null)
        // Explicitly verify the contract: never a clamped/coerced number, always null when invalid.
        assertNull(result.valueC)
        assertFalse(result.valid)
    }

    @Test
    fun customAlphaIsRespected() {
        val resultDefault = CoreTemperatureEstimator.estimate(skinTempC = 34.0, onDeviceAmbientC = 30.0, weatherAmbientC = null)
        val resultCustom = CoreTemperatureEstimator.estimate(skinTempC = 34.0, onDeviceAmbientC = 30.0, weatherAmbientC = null, alpha = 0.5)
        assertTrue(resultDefault.valueC != resultCustom.valueC)
    }
}
