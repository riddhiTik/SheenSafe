package org.kashmir.heatguard.ring

import org.kashmir.heatguard.core.model.SensorSample
import org.kashmir.heatguard.core.ring.RingDataSource
import org.kashmir.heatguard.core.ring.RingSampleListener
import org.kashmir.heatguard.core.ring.RingOutlierFilter
import org.kashmir.heatguard.core.ring.SkinTemperatureSmoother

/**
 * RingDataSource backed by a Galaxy Watch6 via the Wearable Data Layer.
 *
 * This class is a passive sink: it holds the latest sample and notifies the
 * registered listener. Samples are pushed into it by SheenSafeWearListenerService
 * whenever a message arrives on path /sheensafe-data/watch-reading.
 *
 * Thread safety: onWatchReading() is called from the WearableListenerService binder
 * thread. @Volatile on the shared fields keeps reads from HeatMonitoringService
 * consistent without a full lock on every path.
 */
class WearDataRingDataSource : RingDataSource {

    @Volatile private var latest: SensorSample? = null
    @Volatile private var listener: RingSampleListener? = null
    @Volatile private var running = false

    private val outlierFilter = RingOutlierFilter()
    private val tempSmoother = SkinTemperatureSmoother()

    override fun start(listener: RingSampleListener) {
        this.listener = listener
        running = true
    }

    override fun stop() {
        running = false
        listener = null
        outlierFilter.reset()
        tempSmoother.reset()
    }

    override fun latestSample(): SensorSample? = latest

    fun onWatchReading(sample: SensorSample) {
        val rawSkinTemp = sample.rawSkinTemperatureC
        val filtered = outlierFilter.filter(sample.heartRateBpm, rawSkinTemp)
        val smoothed = tempSmoother.smooth(filtered.skinTemperatureC)

        val processed = sample.copy(
            heartRateBpm = filtered.heartRateBpm,
            smoothedSkinTemperatureC = smoothed,
            skinTemperatureC = smoothed  // compat alias: always mirrors smoothedSkinTemperatureC
        )
        latest = processed
        if (running) listener?.onSample(processed)
    }
}
