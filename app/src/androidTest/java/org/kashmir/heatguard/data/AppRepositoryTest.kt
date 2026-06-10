package org.kashmir.heatguard.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kashmir.heatguard.core.model.*

@RunWith(AndroidJUnit4::class)
class AppRepositoryTest {

    private lateinit var repo: AppRepository

    @Before
    fun setUp() {
        repo = AppRepository(ApplicationProvider.getApplicationContext())
        repo.deleteAllData()
    }

    @After
    fun tearDown() {
        repo.deleteAllData()
    }

    // ------------------------------------------------------------------ Baseline

    @Test
    fun baseline_roundTrip_preservesDoublesPrecisely() {
        val original = Baseline(
            typicalRestingHeartRateBpm = 68,
            typicalSkinTemperatureC = 36.5,
            skinTemperatureStdDevC = 0.4
        )
        repo.setBaseline(original)
        val loaded = repo.getBaseline()

        assertEquals(68, loaded.typicalRestingHeartRateBpm)
        // Verify Double-as-Long bit encoding preserves exact value (not truncated to float)
        assertEquals(36.5, loaded.typicalSkinTemperatureC)
        assertEquals(0.4, loaded.skinTemperatureStdDevC)
    }

    @Test
    fun baseline_nullFields_roundTrip() {
        repo.setBaseline(Baseline(typicalRestingHeartRateBpm = null, typicalSkinTemperatureC = null))
        val loaded = repo.getBaseline()
        assertNull(loaded.typicalRestingHeartRateBpm)
        assertNull(loaded.typicalSkinTemperatureC)
    }

    // ------------------------------------------------------------------ RiskProfile

    @Test
    fun riskProfile_roundTrip_allFields() {
        val profile = RiskProfile(
            ageBand = AgeBand.OLDER_ADULT,
            pregnant = true,
            chronicIllness = true,
            outdoorWorkOrTravel = true,
            fastingOrFluidRestriction = true,
            priorHeatIllness = true,
            limitedCoolingAccess = true,
            daysAcclimatized = 7
        )
        repo.setRiskProfile(profile)
        val loaded = repo.getRiskProfile()

        assertEquals(AgeBand.OLDER_ADULT, loaded.ageBand)
        assertTrue(loaded.pregnant)
        assertTrue(loaded.chronicIllness)
        assertTrue(loaded.outdoorWorkOrTravel)
        assertTrue(loaded.fastingOrFluidRestriction)
        assertTrue(loaded.priorHeatIllness)
        assertTrue(loaded.limitedCoolingAccess)
        assertEquals(7, loaded.daysAcclimatized)
    }

    @Test
    fun riskProfile_defaultState_hasNoHigherVulnerability() {
        val profile = repo.getRiskProfile()
        assertFalse(profile.hasHigherVulnerability)
    }

    // ------------------------------------------------------------------ Emergency contacts

    @Test
    fun emergencyContact_setAndGet_roundTrip() {
        repo.setEmergencyContact("Alice", "+1-555-0100")
        assertEquals("Alice", repo.getEmergencyContactName())
        assertEquals("+1-555-0100", repo.getEmergencyContactPhone())
    }

    @Test
    fun secondContact_setAndGet_roundTrip() {
        repo.setSecondContact("Bob", "+1-555-0200")
        assertEquals("Bob", repo.getSecondContactName())
        assertEquals("+1-555-0200", repo.getSecondContactPhone())
    }

    // ------------------------------------------------------------------ Weather

    @Test
    fun manualWeather_setAndGet_roundTrip() {
        val nowMillis = System.currentTimeMillis()
        repo.setManualWeather(38.0, 75.0, true, OfficialHeatWarning.WARNING)
        val loaded = repo.getLastManualWeather(nowMillis)

        assertEquals(38.0, loaded.airTemperatureC)
        assertEquals(75.0, loaded.relativeHumidityPercent)
        assertTrue(loaded.directSun)
        assertEquals(OfficialHeatWarning.WARNING, loaded.officialWarning)
    }

    @Test
    fun cachedApiWeather_setAndGet_roundTrip() {
        val nowMillis = System.currentTimeMillis()
        val sample = WeatherSample(
            timestampMillis = nowMillis,
            airTemperatureC = 32.5,
            relativeHumidityPercent = 60.0,
            source = "open-meteo"
        )
        repo.setCachedApiWeather(sample)
        val loaded = repo.getCachedApiWeather()

        assertNotNull(loaded)
        assertEquals(32.5, loaded.airTemperatureC)
        assertEquals(60.0, loaded.relativeHumidityPercent)
        assertEquals(nowMillis, loaded.timestampMillis)
    }

    @Test
    fun cachedApiWeather_notSet_returnsNull() {
        assertNull(repo.getCachedApiWeather())
    }

    // ------------------------------------------------------------------ User info

    @Test
    fun userName_setAndGet() {
        repo.setUserName("Riddhi")
        assertEquals("Riddhi", repo.getUserName())
    }

    @Test
    fun userAge_setsAgeBandCorrectly() {
        repo.setUserAge(65)
        assertEquals(AgeBand.OLDER_ADULT, repo.getRiskProfile().ageBand)

        repo.setUserAge(30)
        assertEquals(AgeBand.ADULT, repo.getRiskProfile().ageBand)

        repo.setUserAge(15)
        assertEquals(AgeBand.CHILD, repo.getRiskProfile().ageBand)
    }

    // ------------------------------------------------------------------ Settings

    @Test
    fun alertToggles_defaultOn_canBeDisabled() {
        assertTrue(repo.getAlertHeatRisk())
        repo.setAlertHeatRisk(false)
        assertFalse(repo.getAlertHeatRisk())
    }

    @Test
    fun tempUnit_defaultC_canBeChangedToF() {
        assertEquals("C", repo.getTempUnit())
        repo.setTempUnit("F")
        assertEquals("F", repo.getTempUnit())
    }

    // ------------------------------------------------------------------ Onboarding

    @Test
    fun onboarding_defaultNotComplete_setComplete() {
        assertFalse(repo.isOnboardingComplete())
        repo.setOnboardingComplete()
        assertTrue(repo.isOnboardingComplete())
    }

    // ------------------------------------------------------------------ deleteAllData

    @Test
    fun deleteAllData_clearsEverything() {
        repo.setUserName("Alice")
        repo.setOnboardingComplete()
        repo.setBaseline(Baseline(typicalRestingHeartRateBpm = 72, typicalSkinTemperatureC = 36.0))

        repo.deleteAllData()

        assertEquals("", repo.getUserName())
        assertFalse(repo.isOnboardingComplete())
        assertNull(repo.getBaseline().typicalRestingHeartRateBpm)
    }
}
