package org.kashmir.heatguard.ring

import android.os.Handler
import android.os.Looper
import org.kashmir.heatguard.core.model.SensorSample
import kotlin.math.roundToInt
import kotlin.random.Random

class SimulatedRingDataSource : RingDataSource {
    private val handler = Handler(Looper.getMainLooper())
    private var listener: RingSampleListener? = null
    private var lastSample: SensorSample? = null
    private var running = false
    private var heatDrift = 0.0

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            heatDrift = (heatDrift + Random.nextDouble(-0.05, 0.12)).coerceIn(0.0, 2.3)
            val sample = SensorSample(
                timestampMillis = System.currentTimeMillis(),
                heartRateBpm = (82 + heatDrift * 18 + Random.nextDouble(-4.0, 4.0)).roundToInt(),
                skinTemperatureC = 34.1 + heatDrift + Random.nextDouble(-0.15, 0.15),
                stepsSinceLastSample = Random.nextInt(0, 35),
                batteryPercent = 73,
                connected = true
            )
            lastSample = sample
            listener?.onSample(sample)
            handler.postDelayed(this, 60_000L)
        }
    }

    override fun start(listener: RingSampleListener) {
        this.listener = listener
        running = true
        tick.run()
    }

    override fun stop() {
        running = false
        handler.removeCallbacks(tick)
        listener = null
    }

    override fun latestSample(): SensorSample? = lastSample
}
