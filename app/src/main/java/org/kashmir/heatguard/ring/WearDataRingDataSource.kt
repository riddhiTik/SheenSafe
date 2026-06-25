package org.kashmir.heatguard.ring

import org.kashmir.heatguard.core.model.SensorSample
import org.kashmir.heatguard.core.ring.RingDataSource
import org.kashmir.heatguard.core.ring.RingSampleListener

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

    override fun start(listener: RingSampleListener) {
        this.listener = listener
        running = true
    }

    override fun stop() {
        running = false
        listener = null
    }

    override fun latestSample(): SensorSample? = latest

    fun onWatchReading(sample: SensorSample) {
        latest = sample
        if (running) listener?.onSample(sample)
    }
}
