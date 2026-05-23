package org.kashmir.heatguard.core.integration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.kashmir.heatguard.core.fakes.FakeClock
import org.kashmir.heatguard.core.fakes.FakeWeatherProvider
import org.kashmir.heatguard.core.model.WeatherSample
import org.kashmir.heatguard.core.weather.WeatherProvider

/**
 * Tests the fallback chain pattern without the Android-specific FallbackWeatherProvider.
 * The same logic is tested here using a simple ordered-provider helper so it can run on JVM.
 */
class WeatherFallbackScenarioTest {
    private val clock = FakeClock()

    private fun makeFallback(providers: List<WeatherProvider>): WeatherProvider {
        return WeatherProvider { nowMillis ->
            for (provider in providers) {
                try {
                    val sample = provider.latestWeather(nowMillis)
                    if (sample != null) return@WeatherProvider sample
                } catch (_: Exception) { /* try next */ }
            }
            null
        }
    }

    @Test
    fun apiAvailableReturnsApiSample() {
        val apiSample = WeatherSample(clock.nowMillis(), airTemperatureC = 38.0, relativeHumidityPercent = 55.0, source = "api")
        val api = FakeWeatherProvider(apiSample)
        val manual = FakeWeatherProvider(WeatherSample(clock.nowMillis(), airTemperatureC = 30.0, source = "manual"))
        val result = makeFallback(listOf(api, manual)).latestWeather(clock.nowMillis())
        assertEquals("api", result?.source)
        assertEquals(1, api.callCount)
        assertEquals(0, manual.callCount) // manual not consulted
    }

    @Test
    fun apiThrowsReturnsCachedSample() {
        val throwingApi = WeatherProvider { throw RuntimeException("no network") }
        val cached = FakeWeatherProvider(WeatherSample(clock.nowMillis(), airTemperatureC = 32.0, source = "cached"))
        val result = makeFallback(listOf(throwingApi, cached)).latestWeather(clock.nowMillis())
        assertNotNull(result)
        assertEquals("cached", result?.source)
    }

    @Test
    fun apiAndCacheUnavailableReturnManual() {
        val throwingApi = WeatherProvider { throw RuntimeException("no network") }
        val emptyCache = FakeWeatherProvider(null)
        val manual = FakeWeatherProvider(WeatherSample(clock.nowMillis(), airTemperatureC = 28.0, source = "manual"))
        val result = makeFallback(listOf(throwingApi, emptyCache, manual)).latestWeather(clock.nowMillis())
        assertEquals("manual", result?.source)
    }

    @Test
    fun allFailReturnsNull() {
        val throw1 = WeatherProvider { throw RuntimeException("fail 1") }
        val throw2 = WeatherProvider { throw RuntimeException("fail 2") }
        val result = makeFallback(listOf(throw1, throw2)).latestWeather(clock.nowMillis())
        assertNull(result)
    }

    @Test
    fun emptyProviderListReturnsNull() {
        val result = makeFallback(emptyList()).latestWeather(clock.nowMillis())
        assertNull(result)
    }
}
