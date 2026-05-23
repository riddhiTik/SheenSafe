package org.kashmir.heatguard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.kashmir.heatguard.core.ring.RingOutlierFilter

class RingOutlierFilterTest {
    @Test
    fun normalSequenceNotRejected() {
        val filter = RingOutlierFilter()
        repeat(5) { filter.filter(72 + it, 34.0 + it * 0.1) }
        val result = filter.filter(74, 34.3)
        assertNotNull(result.heartRateBpm)
        assertNotNull(result.skinTemperatureC)
    }

    @Test
    fun heartRateSpikeRejected() {
        val filter = RingOutlierFilter(hrMaxDelta = 40)
        // Build window of [70, 72, 71, 69, 70]
        listOf(70, 72, 71, 69, 70).forEach { filter.filter(it, 34.0) }
        val result = filter.filter(130, 34.0) // spike: |130 - 71| = 59 > 40
        assertNull(result.heartRateBpm, "Heart rate spike should be rejected")
    }

    @Test
    fun recoveryAfterSpikeAccepted() {
        val filter = RingOutlierFilter(hrMaxDelta = 40)
        listOf(70, 72, 71, 69, 70).forEach { filter.filter(it, 34.0) }
        filter.filter(130, 34.0) // spike — rejected, not added to window
        val result = filter.filter(73, 34.0) // recovery value
        assertNotNull(result.heartRateBpm)
    }

    @Test
    fun smallWindowAcceptsAll() {
        // Window size < 3 → no rejection
        val filter = RingOutlierFilter(hrMaxDelta = 5)
        val r1 = filter.filter(70, 34.0)
        val r2 = filter.filter(130, 34.0) // would be spike but window is < 3
        assertNotNull(r1.heartRateBpm)
        assertNotNull(r2.heartRateBpm)
    }

    @Test
    fun skinTempSpikeRejected() {
        val filter = RingOutlierFilter(tempMaxDeltaC = 2.0)
        listOf(34.0, 34.1, 34.0, 33.9, 34.0).forEach { filter.filter(72, it) }
        val result = filter.filter(72, 40.0) // spike: |40.0 - 34.0| = 6.0 > 2.0
        assertNull(result.skinTemperatureC)
    }

    @Test
    fun normalTempVarianceNotRejected() {
        val filter = RingOutlierFilter(tempMaxDeltaC = 2.0)
        listOf(34.0, 34.1, 34.0, 33.9, 34.0).forEach { filter.filter(72, it) }
        val result = filter.filter(72, 34.8) // delta 0.8 < 2.0
        assertNotNull(result.skinTemperatureC)
    }

    @Test
    fun nullInputPassedThrough() {
        val filter = RingOutlierFilter()
        val result = filter.filter(null, null)
        assertNull(result.heartRateBpm)
        assertNull(result.skinTemperatureC)
    }

    @Test
    fun resetClearsWindow() {
        val filter = RingOutlierFilter(hrMaxDelta = 5)
        listOf(70, 72, 71, 69, 70).forEach { filter.filter(it, 34.0) }
        filter.reset()
        // After reset, window is empty → no rejection
        val result = filter.filter(130, 34.0)
        assertNotNull(result.heartRateBpm)
    }
}
