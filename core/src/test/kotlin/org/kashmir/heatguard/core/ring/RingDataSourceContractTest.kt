package org.kashmir.heatguard.core.ring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import org.kashmir.heatguard.core.model.SensorSample

abstract class RingDataSourceContractTest {
    abstract fun createSource(): RingDataSource
    abstract fun emitSample(source: RingDataSource, sample: SensorSample)

    private val sample = SensorSample(
        timestampMillis = 1_700_000_000_000L,
        heartRateBpm = 72,
        skinTemperatureC = 34.5
    )

    @Test
    fun latestSampleIsNullBeforeStart() {
        val source = createSource()
        assertNull(source.latestSample())
    }

    @Test
    fun startCallsListenerWhenSampleEmitted() {
        val source = createSource()
        var received: SensorSample? = null
        source.start { received = it }
        emitSample(source, sample)
        assertNotNull(received)
        assertEquals(sample.heartRateBpm, received!!.heartRateBpm)
    }

    @Test
    fun latestSampleUpdatesAfterEmit() {
        val source = createSource()
        source.start { }
        emitSample(source, sample)
        val latest = source.latestSample()
        assertNotNull(latest)
        assertEquals(sample.heartRateBpm, latest!!.heartRateBpm)
        assertEquals(sample.skinTemperatureC, latest.skinTemperatureC)
    }

    @Test
    fun stopPreventsNewSamples() {
        val source = createSource()
        var callCount = 0
        source.start { callCount++ }
        emitSample(source, sample)
        source.stop()
        emitSample(source, sample.copy(timestampMillis = sample.timestampMillis + 1000L))
        assertEquals(1, callCount)
    }
}
