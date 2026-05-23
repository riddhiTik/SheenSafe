package org.kashmir.heatguard.ring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import org.kashmir.heatguard.core.model.SensorSample
import org.kashmir.heatguard.core.ring.RingDataSource
import org.kashmir.heatguard.core.ring.RingOutlierFilter
import org.kashmir.heatguard.core.ring.RingSampleListener

/**
 * Adapter seam for the Gadgetbridge fork (COLMI / QRing).
 *
 * The app is written against RingDataSource, not Gadgetbridge internals. After forking
 * Gadgetbridge, wire this class to the device coordinator and emit via onRawSample().
 * Keep this adapter thin so upstream Gadgetbridge protocol fixes can still be merged.
 *
 * Integration points are marked [Gadgetbridge-fork].
 */
class GadgetbridgeRingDataSource(
    private val context: Context,
    private val outlierFilter: RingOutlierFilter = RingOutlierFilter()
) : RingDataSource {

    private var latest: SensorSample? = null
    private var listener: RingSampleListener? = null
    private var broadcastReceiver: BroadcastReceiver? = null

    override fun start(listener: RingSampleListener) {
        this.listener = listener
        outlierFilter.reset()

        // TODO: [Gadgetbridge-fork] Register a BroadcastReceiver for the device coordinator
        // sample broadcast. Replace SAMPLE_ACTION with the actual action from the fork.
        // The coordinator should broadcast after each sync, carrying the raw sample extras
        // defined in GadgetbridgeSampleContract (create in the fork).
        //
        // Example (uncomment in fork):
        //   val filter = IntentFilter(SAMPLE_ACTION)
        //   val receiver = object : BroadcastReceiver() {
        //       override fun onReceive(ctx: Context, intent: Intent) {
        //           onRawSample(
        //               timestampMillis = intent.getLongExtra("timestamp", 0L),
        //               heartRateBpm = intent.getIntOrNull("hr"),
        //               skinTemperatureC = intent.getDoubleOrNull("temp"),
        //               stepsSinceLastSample = intent.getIntOrNull("steps"),
        //               spo2Percent = intent.getIntOrNull("spo2"),
        //               batteryPercent = intent.getIntOrNull("battery"),
        //               connected = intent.getBooleanExtra("connected", false)
        //           )
        //       }
        //   }
        //   context.registerReceiver(receiver, filter)
        //   broadcastReceiver = receiver
    }

    override fun stop() {
        // TODO: [Gadgetbridge-fork] Unregister the broadcast receiver.
        //   broadcastReceiver?.let { context.unregisterReceiver(it) }
        //   broadcastReceiver = null
        listener = null
    }

    override fun latestSample(): SensorSample? = latest

    /**
     * Called by the BroadcastReceiver once the Gadgetbridge fork is wired, or by
     * FakeGadgetbridgeRingDataSource in tests. Applies outlier filtering then stores
     * and forwards the cleaned sample.
     */
    internal fun onRawSample(
        timestampMillis: Long,
        heartRateBpm: Int?,
        skinTemperatureC: Double?,
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

    companion object {
        // TODO: [Gadgetbridge-fork] Replace with actual action from the fork's coordinator
        const val SAMPLE_ACTION = "org.kashmir.heatguard.RING_SAMPLE"
    }
}
