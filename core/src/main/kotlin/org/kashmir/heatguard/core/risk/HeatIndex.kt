package org.kashmir.heatguard.core.risk

import kotlin.math.pow

object HeatIndex {
    fun celsiusToFahrenheit(celsius: Double): Double = celsius * 9.0 / 5.0 + 32.0
    fun fahrenheitToCelsius(fahrenheit: Double): Double = (fahrenheit - 32.0) * 5.0 / 9.0

    /**
     * Rothfusz-style heat index calculation.
     * Returns the apparent temperature in Celsius. For cooler conditions this returns air temperature.
     */
    fun calculateCelsius(airTemperatureC: Double, relativeHumidityPercent: Double): Double {
        val t = celsiusToFahrenheit(airTemperatureC)
        val rh = relativeHumidityPercent.coerceIn(0.0, 100.0)
        if (t < 80.0) return airTemperatureC

        var hi = -42.379 +
            2.04901523 * t +
            10.14333127 * rh -
            0.22475541 * t * rh -
            0.00683783 * t.pow(2.0) -
            0.05481717 * rh.pow(2.0) +
            0.00122874 * t.pow(2.0) * rh +
            0.00085282 * t * rh.pow(2.0) -
            0.00000199 * t.pow(2.0) * rh.pow(2.0)

        if (rh < 13.0 && t in 80.0..112.0) {
            val adjustment = ((13.0 - rh) / 4.0) * kotlin.math.sqrt((17.0 - kotlin.math.abs(t - 95.0)) / 17.0)
            hi -= adjustment
        } else if (rh > 85.0 && t in 80.0..87.0) {
            val adjustment = ((rh - 85.0) / 10.0) * ((87.0 - t) / 5.0)
            hi += adjustment
        }

        return fahrenheitToCelsius(hi)
    }
}
