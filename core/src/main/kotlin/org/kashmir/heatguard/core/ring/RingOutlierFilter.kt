package org.kashmir.heatguard.core.ring

import kotlin.math.abs

class RingOutlierFilter(
    private val windowSize: Int = 5,
    private val hrMaxDelta: Int = 40,
    private val tempMaxDeltaC: Double = 3.0
) {
    private val recentHr = ArrayDeque<Int>()
    private val recentTemp = ArrayDeque<Double>()

    fun filter(hr: Int?, temp: Double?): FilteredSample {
        val cleanHr = if (hr != null && recentHr.size >= 3 && isOutlierInt(hr, recentHr, hrMaxDelta)) {
            null
        } else {
            hr?.also { pushInt(recentHr, it) }
        }

        val cleanTemp = if (temp != null && recentTemp.size >= 3 && isOutlierDouble(temp, recentTemp, tempMaxDeltaC)) {
            null
        } else {
            temp?.also { pushDouble(recentTemp, it) }
        }

        return FilteredSample(cleanHr, cleanTemp)
    }

    fun reset() {
        recentHr.clear()
        recentTemp.clear()
    }

    private fun isOutlierInt(value: Int, window: ArrayDeque<Int>, maxDelta: Int): Boolean {
        val median = medianInt(window)
        return abs(value - median) > maxDelta
    }

    private fun isOutlierDouble(value: Double, window: ArrayDeque<Double>, maxDelta: Double): Boolean {
        val median = medianDouble(window)
        return abs(value - median) > maxDelta
    }

    private fun medianInt(window: ArrayDeque<Int>): Int {
        val sorted = window.sorted()
        return sorted[sorted.size / 2]
    }

    private fun medianDouble(window: ArrayDeque<Double>): Double {
        val sorted = window.sorted()
        return sorted[sorted.size / 2]
    }

    private fun pushInt(window: ArrayDeque<Int>, value: Int) {
        if (window.size >= windowSize) window.removeFirst()
        window.addLast(value)
    }

    private fun pushDouble(window: ArrayDeque<Double>, value: Double) {
        if (window.size >= windowSize) window.removeFirst()
        window.addLast(value)
    }

    data class FilteredSample(val heartRateBpm: Int?, val skinTemperatureC: Double?)
}
