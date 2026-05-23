package org.kashmir.heatguard.ring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class GadgetbridgeRingDataSourceContractTest {
    private val ts = 1_700_000_000_000L

    @Test
    fun latestSampleIsNullBeforeStart() {
        val source = FakeGadgetbridgeRingDataSource()
        assertNull(source.latestSample())
    }

    @Test
    fun startCallsListenerWhenSampleInjected() {
        val source = FakeGadgetbridgeRingDataSource()
        var callCount = 0
        source.start { callCount++ }
        source.injectRawSample(timestampMillis = ts, heartRateBpm = 72, skinTemperatureC = 34.5)
        assertEquals(1, callCount)
    }

    @Test
    fun latestSampleUpdatesAfterInject() {
        val source = FakeGadgetbridgeRingDataSource()
        source.start { }
        source.injectRawSample(timestampMillis = ts, heartRateBpm = 80, skinTemperatureC = 35.0)
        val latest = source.latestSample()
        assertNotNull(latest)
        assertEquals(80, latest!!.heartRateBpm)
        assertEquals(35.0, latest.skinTemperatureC)
    }

    @Test
    fun stopPreventsNewSamples() {
        val source = FakeGadgetbridgeRingDataSource()
        var callCount = 0
        source.start { callCount++ }
        source.injectRawSample(timestampMillis = ts, heartRateBpm = 72)
        source.stop()
        source.injectRawSample(timestampMillis = ts + 1000L, heartRateBpm = 75)
        assertEquals(1, callCount)
    }

    @Test
    fun outlierFilterRejectsHrSpike() {
        val source = FakeGadgetbridgeRingDataSource()
        source.start { }
        // Seed the window with five normal values
        repeat(5) { i ->
            source.injectRawSample(timestampMillis = ts + i * 1000L, heartRateBpm = 72)
        }
        // Inject a spike that is > 40 bpm away from the median of 72
        source.injectRawSample(timestampMillis = ts + 6_000L, heartRateBpm = 200)
        // The spike should be rejected; latestSample HR should not be 200
        val latest = source.latestSample()
        assertNotNull(latest)
        assertNull(latest!!.heartRateBpm, "HR spike should have been filtered to null")
    }

    @Test
    fun outlierFilterAcceptsNormalHrChange() {
        val source = FakeGadgetbridgeRingDataSource()
        source.start { }
        repeat(5) { i ->
            source.injectRawSample(timestampMillis = ts + i * 1000L, heartRateBpm = 72)
        }
        // A 10 bpm increase is within tolerance
        source.injectRawSample(timestampMillis = ts + 6_000L, heartRateBpm = 82)
        val latest = source.latestSample()
        assertNotNull(latest)
        assertEquals(82, latest!!.heartRateBpm)
    }
}
