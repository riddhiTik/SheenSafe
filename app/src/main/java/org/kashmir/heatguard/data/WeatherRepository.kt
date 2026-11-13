package org.kashmir.heatguard.data

import org.kashmir.heatguard.core.model.OfficialHeatWarning
import org.kashmir.heatguard.core.model.WeatherSample

interface WeatherRepository {
    fun getLastManualWeather(nowMillis: Long): WeatherSample
    /** True only once the user has actually entered weather; otherwise getLastManualWeather returns placeholder defaults. */
    fun hasManualWeather(): Boolean = true
    fun setManualWeather(tempC: Double, relativeHumidityPercent: Double, directSun: Boolean, warning: OfficialHeatWarning)
    fun getCachedApiWeather(): WeatherSample?
    fun setCachedApiWeather(sample: WeatherSample)
}
