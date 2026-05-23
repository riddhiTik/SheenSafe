package org.kashmir.heatguard.ring

import org.kashmir.heatguard.core.model.SensorSample
import org.kashmir.heatguard.core.ring.RingDataSource
import org.kashmir.heatguard.core.ring.RingOutlierFilter
import org.kashmir.heatguard.core.ring.RingSampleListener

/**
 * JVM-safe test double for GadgetbridgeRingDataSource.
 * Does not extend the real class (which requires Android Context).
 * Replicates the same onRawSample + RingOutlierFilter logic so the
 * contract can be tested on the JVM without instrumentation.
 */
class FakeGadgetbridgeRingDataSource(
    private val outlierFilter: RingOutlierFilter = RingOutlierFilter()
) : RingDataSource {
    private var latest: SensorSample? = null
    private var listener: RingSampleListener? = null
    var started = false

    override fun start(listener: RingSampleListener) {
        this.listener = listener
        started = true
        outlierFilter.reset()
    }

    override fun stop() {
        started = false
        listener = null
    }

    override fun latestSample(): SensorSample? = latest

    fun injectRawSample(
        timestampMillis: Long = System.currentTimeMillis(),
        heartRateBpm: Int? = null,
        skinTemperatureC: Double? = null,
        stepsSinceLastSample: Int? = null,
        spo2Percent: Int? = null,
        batteryPercent: Int? = null,
        connected: Boolean = true
    ) {
        val filtered = outlierFilter.filter(heartRateBpm, skinTemperatureC)
        val sample = SensorSample(
            timestampMillis = timestampMillis,
            heartRateBpm = filtered.heartRateBpm,
            skinTemperatureC = filtered.skinTemperatureC,
            stepsSinceLastSample = stepsSinceLastSample,
            spo2Percent = spo2Percent,
            batteryPercent = batteryPercent,
            connected = connected
        )
        latest = sample
        listener?.onSample(sample)
    }
}
