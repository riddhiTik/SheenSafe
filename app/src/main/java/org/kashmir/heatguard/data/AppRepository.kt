package org.kashmir.heatguard.data

import android.content.Context
import android.content.SharedPreferences
import org.kashmir.heatguard.core.model.*

// TODO: [PRIVACY-HARDENING] Replace SharedPreferences with EncryptedSharedPreferences once
// androidx is enabled. Set android.useAndroidX=true in gradle.properties, add
// implementation("androidx.security:security-crypto:1.1.0-alpha06") to app/build.gradle.kts,
// then replace context.getSharedPreferences(...) with EncryptedSharedPreferences.create(...).
class AppRepository(context: Context) : BaselineRepository, RiskProfileRepository, ContactRepository, WeatherRepository {
    private val prefs: SharedPreferences = context.getSharedPreferences("heatguard", Context.MODE_PRIVATE)

    override fun getEmergencyContactName(): String = prefs.getString(KEY_CONTACT_NAME, "") ?: ""
    override fun getEmergencyContactPhone(): String = prefs.getString(KEY_CONTACT_PHONE, "") ?: ""

    override fun setEmergencyContact(name: String, phone: String) {
        prefs.edit()
            .putString(KEY_CONTACT_NAME, name.trim())
            .putString(KEY_CONTACT_PHONE, phone.trim())
            .apply()
    }

    override fun getBaseline(): Baseline = Baseline(
        typicalRestingHeartRateBpm = prefs.getIntOrNull(KEY_BASELINE_HR),
        typicalSkinTemperatureC = prefs.getDoubleOrNull(KEY_BASELINE_TEMP),
        skinTemperatureStdDevC = prefs.getDoubleOrNull(KEY_BASELINE_TEMP_STD)
    )

    override fun setBaseline(baseline: Baseline) {
        prefs.edit()
            .putIntOrRemove(KEY_BASELINE_HR, baseline.typicalRestingHeartRateBpm)
            .putDoubleOrRemove(KEY_BASELINE_TEMP, baseline.typicalSkinTemperatureC)
            .putDoubleOrRemove(KEY_BASELINE_TEMP_STD, baseline.skinTemperatureStdDevC)
            .apply()
    }

    override fun getRiskProfile(): RiskProfile = RiskProfile(
        ageBand = AgeBand.valueOf(prefs.getString(KEY_AGE_BAND, AgeBand.UNKNOWN.name) ?: AgeBand.UNKNOWN.name),
        pregnant = prefs.getBoolean(KEY_PREGNANT, false),
        chronicIllness = prefs.getBoolean(KEY_CHRONIC, false),
        outdoorWorkOrTravel = prefs.getBoolean(KEY_OUTDOOR, false),
        fastingOrFluidRestriction = prefs.getBoolean(KEY_FASTING, false),
        priorHeatIllness = prefs.getBoolean(KEY_PRIOR_HEAT_ILLNESS, false),
        limitedCoolingAccess = prefs.getBoolean(KEY_LIMITED_COOLING, false),
        daysAcclimatized = prefs.getInt(KEY_DAYS_ACCLIMATIZED, 0)
    )

    override fun setRiskProfile(profile: RiskProfile) {
        prefs.edit()
            .putString(KEY_AGE_BAND, profile.ageBand.name)
            .putBoolean(KEY_PREGNANT, profile.pregnant)
            .putBoolean(KEY_CHRONIC, profile.chronicIllness)
            .putBoolean(KEY_OUTDOOR, profile.outdoorWorkOrTravel)
            .putBoolean(KEY_FASTING, profile.fastingOrFluidRestriction)
            .putBoolean(KEY_PRIOR_HEAT_ILLNESS, profile.priorHeatIllness)
            .putBoolean(KEY_LIMITED_COOLING, profile.limitedCoolingAccess)
            .putInt(KEY_DAYS_ACCLIMATIZED, profile.daysAcclimatized)
            .apply()
    }

    fun saveLastDecision(decision: RiskDecision) {
        prefs.edit()
            .putString(KEY_LAST_LEVEL, decision.level.name)
            .putString(KEY_LAST_ACTION, decision.recommendedAction)
            .putLong(KEY_LAST_DECISION_TIME, System.currentTimeMillis())
            .apply()
    }

    override fun getLastManualWeather(nowMillis: Long): WeatherSample {
        val temp = prefs.getDoubleOrNull(KEY_WEATHER_TEMP) ?: 36.0
        val rh = prefs.getDoubleOrNull(KEY_WEATHER_RH) ?: 45.0
        val timestamp = prefs.getLong(KEY_WEATHER_TIME, nowMillis)
        return WeatherSample(
            timestampMillis = timestamp,
            airTemperatureC = temp,
            relativeHumidityPercent = rh,
            directSun = prefs.getBoolean(KEY_WEATHER_DIRECT_SUN, false),
            officialWarning = OfficialHeatWarning.valueOf(
                prefs.getString(KEY_WEATHER_WARNING, OfficialHeatWarning.NONE.name) ?: OfficialHeatWarning.NONE.name
            ),
            source = "manual"
        )
    }

    override fun setManualWeather(tempC: Double, relativeHumidityPercent: Double, directSun: Boolean, warning: OfficialHeatWarning) {
        prefs.edit()
            .putDouble(KEY_WEATHER_TEMP, tempC)
            .putDouble(KEY_WEATHER_RH, relativeHumidityPercent)
            .putBoolean(KEY_WEATHER_DIRECT_SUN, directSun)
            .putString(KEY_WEATHER_WARNING, warning.name)
            .putLong(KEY_WEATHER_TIME, System.currentTimeMillis())
            .apply()
    }

    override fun getCachedApiWeather(): WeatherSample? {
        if (!prefs.contains(KEY_API_WEATHER_TIME)) return null
        val time = prefs.getLong(KEY_API_WEATHER_TIME, 0L)
        val temp = prefs.getDoubleOrNull(KEY_API_WEATHER_TEMP) ?: return null
        val rh = prefs.getDoubleOrNull(KEY_API_WEATHER_RH) ?: return null
        return WeatherSample(
            timestampMillis = time,
            airTemperatureC = temp,
            relativeHumidityPercent = rh,
            source = "open-meteo-cached"
        )
    }

    override fun setCachedApiWeather(sample: WeatherSample) {
        prefs.edit()
            .putDouble(KEY_API_WEATHER_TEMP, sample.airTemperatureC ?: return)
            .putDouble(KEY_API_WEATHER_RH, sample.relativeHumidityPercent ?: return)
            .putLong(KEY_API_WEATHER_TIME, sample.timestampMillis)
            .apply()
    }

    fun isOnboardingComplete(): Boolean = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingComplete() {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
    }

    fun getLanguage(): String = prefs.getString(KEY_LANGUAGE, "en") ?: "en"

    fun setLanguage(code: String) {
        prefs.edit().putString(KEY_LANGUAGE, code).apply()
    }

    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "") ?: ""
    fun setUserName(name: String) { prefs.edit().putString(KEY_USER_NAME, name.trim()).apply() }

    fun getUserAge(): Int = prefs.getInt(KEY_USER_AGE, 0)
    fun setUserAge(age: Int) {
        val band = when {
            age < 18 -> AgeBand.CHILD
            age >= 60 -> AgeBand.OLDER_ADULT
            age > 0  -> AgeBand.ADULT
            else     -> AgeBand.UNKNOWN
        }
        prefs.edit()
            .putInt(KEY_USER_AGE, age)
            .putString(KEY_AGE_BAND, band.name)
            .apply()
    }

    fun getUserGender(): String = prefs.getString(KEY_USER_GENDER, "") ?: ""
    fun setUserGender(gender: String) { prefs.edit().putString(KEY_USER_GENDER, gender).apply() }

    fun isDeviceConnected(): Boolean = prefs.getBoolean(KEY_DEVICE_CONNECTED, false)
    fun setDeviceConnected(connected: Boolean) {
        prefs.edit()
            .putBoolean(KEY_DEVICE_CONNECTED, connected)
            .putBoolean("use_simulated_ring", true)
            .apply()
    }

    fun getSecondContactName(): String = prefs.getString(KEY_CONTACT_2_NAME, "") ?: ""
    fun getSecondContactPhone(): String = prefs.getString(KEY_CONTACT_2_PHONE, "") ?: ""
    fun setSecondContact(name: String, phone: String) {
        prefs.edit()
            .putString(KEY_CONTACT_2_NAME, name.trim())
            .putString(KEY_CONTACT_2_PHONE, phone.trim())
            .apply()
    }

    fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

    fun setBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun deleteAllData() {
        prefs.edit().clear().apply()
    }

    private fun SharedPreferences.getIntOrNull(key: String): Int? = if (contains(key)) getInt(key, 0) else null

    private fun SharedPreferences.getDoubleOrNull(key: String): Double? =
        if (contains(key)) Double.fromBits(getLong(key, 0L)) else null

    private fun SharedPreferences.Editor.putDouble(key: String, value: Double): SharedPreferences.Editor =
        putLong(key, value.toBits())

    private fun SharedPreferences.Editor.putDoubleOrRemove(key: String, value: Double?): SharedPreferences.Editor =
        if (value == null) remove(key) else putDouble(key, value)

    private fun SharedPreferences.Editor.putIntOrRemove(key: String, value: Int?): SharedPreferences.Editor =
        if (value == null) remove(key) else putInt(key, value)

    companion object {
        private const val KEY_CONTACT_NAME = "contact_name"
        private const val KEY_CONTACT_PHONE = "contact_phone"
        private const val KEY_BASELINE_HR = "baseline_hr"
        private const val KEY_BASELINE_TEMP = "baseline_temp"
        private const val KEY_BASELINE_TEMP_STD = "baseline_temp_std"
        private const val KEY_AGE_BAND = "age_band"
        private const val KEY_PREGNANT = "pregnant"
        private const val KEY_CHRONIC = "chronic"
        private const val KEY_OUTDOOR = "outdoor"
        private const val KEY_FASTING = "fasting"
        private const val KEY_PRIOR_HEAT_ILLNESS = "prior_heat_illness"
        private const val KEY_LIMITED_COOLING = "limited_cooling"
        private const val KEY_DAYS_ACCLIMATIZED = "days_acclimatized"
        private const val KEY_LAST_LEVEL = "last_level"
        private const val KEY_LAST_ACTION = "last_action"
        private const val KEY_LAST_DECISION_TIME = "last_decision_time"
        private const val KEY_WEATHER_TEMP = "weather_temp"
        private const val KEY_WEATHER_RH = "weather_rh"
        private const val KEY_WEATHER_DIRECT_SUN = "weather_direct_sun"
        private const val KEY_WEATHER_WARNING = "weather_warning"
        private const val KEY_WEATHER_TIME = "weather_time"
        private const val KEY_API_WEATHER_TEMP = "api_weather_temp"
        private const val KEY_API_WEATHER_RH = "api_weather_rh"
        private const val KEY_API_WEATHER_TIME = "api_weather_time"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_AGE = "user_age"
        private const val KEY_USER_GENDER = "user_gender"
        private const val KEY_DEVICE_CONNECTED = "device_connected"
        private const val KEY_CONTACT_2_NAME = "contact_2_name"
        private const val KEY_CONTACT_2_PHONE = "contact_2_phone"
    }
}
