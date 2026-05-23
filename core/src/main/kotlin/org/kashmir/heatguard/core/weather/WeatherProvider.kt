package org.kashmir.heatguard.core.weather

import org.kashmir.heatguard.core.model.WeatherSample

fun interface WeatherProvider {
    fun latestWeather(nowMillis: Long): WeatherSample?
}
