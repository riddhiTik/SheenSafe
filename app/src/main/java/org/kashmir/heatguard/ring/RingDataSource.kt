package org.kashmir.heatguard.ring

import org.kashmir.heatguard.core.model.SensorSample

interface RingDataSource {
    fun start(listener: RingSampleListener)
    fun stop()
    fun latestSample(): SensorSample?
}

fun interface RingSampleListener {
    fun onSample(sample: SensorSample)
}
