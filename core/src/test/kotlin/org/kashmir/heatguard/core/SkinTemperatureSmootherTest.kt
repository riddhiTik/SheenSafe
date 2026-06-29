package org.kashmir.heatguard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.kashmir.heatguard.core.ring.SkinTemperatureSmoother

class SkinTemperatureSmootherTest {
    @Test
    fun firstValueIsReturnedAsIs() {
        val smoother = SkinTemperatureSmoother()
        assertEquals(34.0, smoother.smooth(34.0))
    }

    @Test
    fun nullInputLeavesRunningValueUnchanged() {
        val smoother = SkinTemperatureSmoother()
        smoother.smooth(34.0)
        val result = smoother.smooth(null)
        assertEquals(34.0, result)
    }

    @Test
    fun nullInputBeforeAnyValueReturnsNull() {
        val smoother = SkinTemperatureSmoother()
        assertNull(smoother.smooth(null))
    }

    @Test
    fun smoothsTowardNewValueGradually() {
        val smoother = SkinTemperatureSmoother(smoothingFactor = 0.2)
        smoother.smooth(34.0)
        val result = smoother.smooth(36.0)
        // 0.2 * 36.0 + 0.8 * 34.0 = 34.4
        assertEquals(34.4, result!!, 0.0001)
    }

    @Test
    fun reset_clearsRunningValue() {
        val smoother = SkinTemperatureSmoother()
        smoother.smooth(34.0)
        smoother.reset()
        assertEquals(36.0, smoother.smooth(36.0))
    }

    @Test
    fun invalidSmoothingFactorThrows() {
        var threw = false
        try {
            SkinTemperatureSmoother(smoothingFactor = 0.0)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        kotlin.test.assertTrue(threw)
    }
}
