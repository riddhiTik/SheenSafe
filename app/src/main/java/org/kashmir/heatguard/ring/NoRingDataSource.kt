package org.kashmir.heatguard.ring

import org.kashmir.heatguard.core.model.SensorSample
import org.kashmir.heatguard.core.ring.RingDataSource
import org.kashmir.heatguard.core.ring.RingSampleListener

/**
 * Used when no wearable is configured. It never produces data, so the UI shows "no data"
 * instead of made-up vitals.
 */
class NoRingDataSource : RingDataSource {
    override fun start(listener: RingSampleListener) = Unit
    override fun stop() = Unit
    override fun latestSample(): SensorSample? = null
}
