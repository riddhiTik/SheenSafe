package org.kashmir.heatguard.core.ring

import org.kashmir.heatguard.core.fakes.FakeRingDataSource
import org.kashmir.heatguard.core.model.SensorSample

class FakeRingDataSourceContractTest : RingDataSourceContractTest() {
    override fun createSource(): RingDataSource = FakeRingDataSource()

    override fun emitSample(source: RingDataSource, sample: SensorSample) {
        (source as FakeRingDataSource).emit(sample)
    }
}
