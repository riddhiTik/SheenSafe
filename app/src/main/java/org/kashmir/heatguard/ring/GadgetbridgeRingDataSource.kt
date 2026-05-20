package org.kashmir.heatguard.ring

import android.content.Context
import org.kashmir.heatguard.core.model.SensorSample

/**
 * Adapter seam for the Gadgetbridge fork.
 *
 * The MVP app is deliberately written against RingDataSource instead of directly against Gadgetbridge internals.
 * After forking Gadgetbridge, wire this class to the COLMI / QRing device coordinator and the synced sample store.
 * Keep this adapter thin so upstream Gadgetbridge protocol fixes can still be merged.
 */
class GadgetbridgeRingDataSource(
    private val context: Context
) : RingDataSource {
    private var latest: SensorSample? = null

    override fun start(listener: RingSampleListener) {
        // TODO: Connect to Gadgetbridge's device service and sample database in the fork.
        // Expected output per sync event:
        // SensorSample(timestamp, heartRateBpm, skinTemperatureC, stepsSinceLastSample, spo2Percent, batteryPercent, connected)
        // Do not perform medical interpretation here. Only normalize raw device data.
    }

    override fun stop() {
        // TODO: Unregister Gadgetbridge listeners / receivers.
    }

    override fun latestSample(): SensorSample? = latest
}
