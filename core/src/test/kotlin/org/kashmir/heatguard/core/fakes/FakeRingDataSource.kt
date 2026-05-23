package org.kashmir.heatguard.core.fakes

import org.kashmir.heatguard.core.model.SensorSample
import org.kashmir.heatguard.core.ring.RingDataSource
import org.kashmir.heatguard.core.ring.RingSampleListener

class FakeRingDataSource : RingDataSource {
    private var sample: SensorSample? = null
    private var listener: RingSampleListener? = null
    var started = false

    override fun start(listener: RingSampleListener) {
        this.listener = listener
        started = true
    }

    override fun stop() {
        started = false
        listener = null
    }

    override fun latestSample(): SensorSample? = sample

    fun emit(sample: SensorSample) {
        this.sample = sample
        listener?.onSample(sample)
    }

    fun disconnect() {
        sample = sample?.copy(connected = false)
    }
}
