package org.kashmir.heatguard.core.fakes

import org.kashmir.heatguard.core.model.WeatherSample
import org.kashmir.heatguard.core.weather.WeatherProvider

class FakeWeatherProvider(private var sample: WeatherSample? = null) : WeatherProvider {
    var callCount = 0

    override fun latestWeather(nowMillis: Long): WeatherSample? {
        callCount++
        return sample
    }

    fun set(sample: WeatherSample?) { this.sample = sample }
}
