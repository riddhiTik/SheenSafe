package org.kashmir.heatguard.weather

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.kashmir.heatguard.core.model.OfficialHeatWarning
import org.kashmir.heatguard.core.model.WeatherSample
import org.kashmir.heatguard.core.weather.WeatherProvider
import org.kashmir.heatguard.data.WeatherRepository

class WeatherProviderChainTest {

    private val baseTime = 1_700_000_000_000L

    private fun sampleAt(time: Long) = WeatherSample(
        timestampMillis = time,
        airTemperatureC = 30.0,
        relativeHumidityPercent = 50.0,
        source = "test"
    )

    // ------------------------------------------------------------------ FallbackWeatherProvider

    @Test
    fun fallback_firstProviderSucceeds_returnsFirstResult() {
        val primary = WeatherProvider { sampleAt(baseTime) }
        val secondary = WeatherProvider { throw IllegalStateException("should not be called") }
        val fallback = FallbackWeatherProvider(listOf(primary, secondary))
        assertNotNull(fallback.latestWeather(baseTime))
    }

    @Test
    fun fallback_firstProviderReturnsNull_usesSecond() {
        val secondary = sampleAt(baseTime)
        val fallback = FallbackWeatherProvider(listOf(
            WeatherProvider { null },
            WeatherProvider { secondary }
        ))
        assertEquals(secondary, fallback.latestWeather(baseTime))
    }

    @Test
    fun fallback_firstProviderThrows_usesSecond() {
        val secondary = sampleAt(baseTime)
        val fallback = FallbackWeatherProvider(listOf(
            WeatherProvider { error("network down") },
            WeatherProvider { secondary }
        ))
        assertEquals(secondary, fallback.latestWeather(baseTime))
    }

    @Test
    fun fallback_allProvidersReturnNull_returnsNull() {
        val fallback = FallbackWeatherProvider(listOf(
            WeatherProvider { null },
            WeatherProvider { null }
        ))
        assertNull(fallback.latestWeather(baseTime))
    }

    @Test
    fun fallback_allProvidersThrow_returnsNull() {
        val fallback = FallbackWeatherProvider(listOf(
            WeatherProvider { error("primary down") },
            WeatherProvider { error("secondary down") }
        ))
        assertNull(fallback.latestWeather(baseTime))
    }

    // ------------------------------------------------------------------ CachedWeatherProvider

    @Test
    fun cached_delegateSucceeds_storesInCacheAndReturns() {
        val repo = FakeWeatherRepository()
        val fresh = sampleAt(baseTime)
        val cached = CachedWeatherProvider(WeatherProvider { fresh }, repo)

        val result = cached.latestWeather(baseTime)

        assertEquals(fresh, result)
        assertNotNull(repo.getCachedApiWeather())
    }

    @Test
    fun cached_delegateThrows_withinTtl_returnsCachedSample() {
        val repo = FakeWeatherRepository()
        val storedSample = sampleAt(baseTime)
        repo.setCachedApiWeather(storedSample)

        val oneHourLater = baseTime + 60 * 60 * 1000L
        val cached = CachedWeatherProvider(
            WeatherProvider { error("network down") },
            repo,
            maxCacheAgeMs = 2 * 60 * 60 * 1000L
        )

        val result = cached.latestWeather(oneHourLater)
        assertEquals(storedSample, result)
    }

    @Test
    fun cached_delegateThrows_cacheExpired_returnsNull() {
        val repo = FakeWeatherRepository()
        repo.setCachedApiWeather(sampleAt(baseTime))

        val threeHoursLater = baseTime + 3 * 60 * 60 * 1000L
        val cached = CachedWeatherProvider(
            WeatherProvider { error("network down") },
            repo,
            maxCacheAgeMs = 2 * 60 * 60 * 1000L
        )

        assertNull(cached.latestWeather(threeHoursLater))
    }

    @Test
    fun cached_delegateThrows_noCacheStored_returnsNull() {
        val repo = FakeWeatherRepository()
        val cached = CachedWeatherProvider(
            WeatherProvider { error("network down") },
            repo
        )
        assertNull(cached.latestWeather(baseTime))
    }

    // ------------------------------------------------------------------ ManualWeatherProvider

    @Test
    fun manual_noWeatherSet_returnsDefaultSample() {
        val repo = FakeWeatherRepository()
        val provider = ManualWeatherProvider(repo)
        val result = provider.latestWeather(baseTime)
        assertNotNull(result)
    }

    @Test
    fun manual_weatherSet_returnsStoredValues() {
        val repo = FakeWeatherRepository()
        repo.setManualWeather(42.0, 80.0, true, OfficialHeatWarning.WARNING)
        val provider = ManualWeatherProvider(repo)
        val result = provider.latestWeather(baseTime)
        assertEquals(42.0, result.airTemperatureC)
        assertEquals(80.0, result.relativeHumidityPercent)
        assertEquals(true, result.directSun)
        assertEquals(OfficialHeatWarning.WARNING, result.officialWarning)
    }

    // ------------------------------------------------------------------ FakeWeatherRepository

    private class FakeWeatherRepository : WeatherRepository {
        private var manual: WeatherSample? = null
        private var cached: WeatherSample? = null

        override fun getLastManualWeather(nowMillis: Long): WeatherSample {
            return manual ?: WeatherSample(
                timestampMillis = nowMillis,
                airTemperatureC = 36.0,
                relativeHumidityPercent = 45.0,
                source = "manual"
            )
        }

        override fun setManualWeather(
            tempC: Double,
            relativeHumidityPercent: Double,
            directSun: Boolean,
            warning: OfficialHeatWarning
        ) {
            manual = WeatherSample(
                timestampMillis = System.currentTimeMillis(),
                airTemperatureC = tempC,
                relativeHumidityPercent = relativeHumidityPercent,
                directSun = directSun,
                officialWarning = warning,
                source = "manual"
            )
        }

        override fun getCachedApiWeather(): WeatherSample? = cached

        override fun setCachedApiWeather(sample: WeatherSample) {
            cached = sample
        }
    }
}
