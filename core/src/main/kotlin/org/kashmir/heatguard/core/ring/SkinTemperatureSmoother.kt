package org.kashmir.heatguard.core.ring

/**
 * Exponential moving average smoother for skin temperature, applied after RingOutlierFilter
 * has already rejected spikes. Stateful: holds a running smoothed value across calls.
 *
 * Intended to run continuously as readings arrive (roughly once per second from the watch),
 * not once per risk-assessment cycle — smoothing only has an effect across a stream of values.
 */
class SkinTemperatureSmoother(
    private val smoothingFactor: Double = 0.2
) {
    init {
        require(smoothingFactor > 0.0 && smoothingFactor <= 1.0) {
            "smoothingFactor must be in (0, 1], got $smoothingFactor"
        }
    }

    private var smoothed: Double? = null

    /**
     * Feeds one outlier-rejected raw value through the EMA. Passing null leaves the running
     * smoothed value unchanged (a rejected/missing reading should not move the average).
     */
    fun smooth(rawValueC: Double?): Double? {
        if (rawValueC == null) return smoothed

        val previous = smoothed
        smoothed = if (previous == null) {
            rawValueC
        } else {
            smoothingFactor * rawValueC + (1 - smoothingFactor) * previous
        }
        return smoothed
    }

    fun reset() {
        smoothed = null
    }
}
