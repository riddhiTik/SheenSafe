package org.kashmir.heatguard.weather

import org.kashmir.heatguard.core.model.WeatherSample

interface WeatherProvider {
    fun latestWeather(nowMillis: Long = System.currentTimeMillis()): WeatherSample
}
