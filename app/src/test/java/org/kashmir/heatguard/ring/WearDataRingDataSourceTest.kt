package org.kashmir.heatguard.ring

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.kashmir.heatguard.core.model.SensorSample

/**
 * Tests how WearDataRingDataSource reacts to incoming watch readings.
 *
 * WearDataRingDataSource is the sink that SheenSafeWearListenerService pushes
 * watch data into, and that DashboardFragment/HeatMonitoringService reads from.
 * These tests verify the data handoff is correct under all conditions.
 */
class WearDataRingDataSourceTest {

    private val source = WearDataRingDataSource()

    @Test
    fun latestSample_isNullBeforeAnyReading() {
        assertNull(source.latestSample())
    }

    @Test
    fun latestSample_isNullAfterStartButBeforeFirstReading() {
        source.start { }
        assertNull(source.latestSample())
    }

    @Test
    fun onWatchReading_updatesLatestSample() {
        val sample = watchSample(hr = 75, temp = 34.5)
        source.onWatchReading(sample)

        assertEquals(75, source.latestSample()?.heartRateBpm)
        // First EMA reading initializes to the raw value exactly.
        assertEquals(34.5, source.latestSample()?.smoothedSkinTemperatureC)
    }

    @Test
    fun onWatchReading_beforeStart_doesNotCallListener() {
        var listenerCalled = false
        source.start { listenerCalled = true }
        source.stop() // stop before any reading

        source.onWatchReading(watchSample(hr = 80, temp = 34.0))

        assertFalse(listenerCalled)
    }

    @Test
    fun onWatchReading_afterStart_callsListener() {
        var received: SensorSample? = null
        source.start { received = it }

        val sample = watchSample(hr = 82, temp = 35.1)
        source.onWatchReading(sample)

        assertEquals(82, received?.heartRateBpm)
    }

    @Test
    fun onWatchReading_afterStop_doesNotCallListener() {
        var callCount = 0
        source.start { callCount++ }
        source.stop()

        source.onWatchReading(watchSample(hr = 90, temp = 34.0))

        assertEquals(0, callCount)
    }

    @Test
    fun multipleReadings_latestSampleReflectsMostRecent() {
        source.start { }
        source.onWatchReading(watchSample(hr = 70, temp = 34.0, timestamp = 1_000L))
        source.onWatchReading(watchSample(hr = 88, temp = 35.0, timestamp = 2_000L))
        source.onWatchReading(watchSample(hr = 95, temp = 35.3, timestamp = 3_000L))

        assertEquals(95, source.latestSample()?.heartRateBpm)
        // EMA-smoothed: value moves toward 35.3 but is not exactly 35.3.
        // Check raw field to confirm the most recent input reached the sample.
        assertEquals(35.3, source.latestSample()?.rawSkinTemperatureC)
        assertEquals(3_000L, source.latestSample()?.timestampMillis)
    }

    @Test
    fun disconnectedSample_setsConnectedFalse() {
        source.start { }
        source.onWatchReading(SensorSample(
            timestampMillis = System.currentTimeMillis(),
            connected = false
        ))

        assertFalse(source.latestSample()?.connected ?: true)
    }

    @Test
    fun reconnectedAfterDisconnect_latestSampleIsConnected() {
        source.start { }
        source.onWatchReading(SensorSample(timestampMillis = 1_000L, connected = false))
        assertFalse(source.latestSample()?.connected ?: true)

        source.onWatchReading(watchSample(hr = 74, temp = 34.2, timestamp = 2_000L))
        assertTrue(source.latestSample()?.connected ?: false)
    }

    @Test
    fun sampleWithNullHr_doesNotCrash_andStoresNullHr() {
        source.start { }
        source.onWatchReading(SensorSample(
            timestampMillis = System.currentTimeMillis(),
            heartRateBpm = null,
            rawSkinTemperatureC = 34.8,
            connected = true
        ))

        assertNull(source.latestSample()?.heartRateBpm)
        // First EMA reading initializes to the raw value exactly.
        assertEquals(34.8, source.latestSample()?.smoothedSkinTemperatureC)
    }

    @Test
    fun restartingSource_doesNotLoseLatestSample() {
        source.start { }
        source.onWatchReading(watchSample(hr = 78, temp = 34.5))
        source.stop()

        // Restart
        source.start { }
        // latestSample() is still the last-received reading
        assertEquals(78, source.latestSample()?.heartRateBpm)
    }

    // ------------------------------------------------------------------ helpers

    private fun watchSample(
        hr: Int,
        temp: Double,
        timestamp: Long = System.currentTimeMillis()
    ) = SensorSample(
        timestampMillis = timestamp,
        heartRateBpm = hr,
        rawSkinTemperatureC = temp,  // onWatchReading() reads rawSkinTemperatureC, not skinTemperatureC
        connected = true
    )
}
