package org.kashmir.heatguard.data

import android.content.Context
import android.content.SharedPreferences
import org.kashmir.heatguard.core.model.*

class AppRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("heatguard", Context.MODE_PRIVATE)

    fun getEmergencyContactName(): String = prefs.getString(KEY_CONTACT_NAME, "") ?: ""
    fun getEmergencyContactPhone(): String = prefs.getString(KEY_CONTACT_PHONE, "") ?: ""

    fun setEmergencyContact(name: String, phone: String) {
        prefs.edit()
            .putString(KEY_CONTACT_NAME, name.trim())
            .putString(KEY_CONTACT_PHONE, phone.trim())
            .apply()
    }

    fun getBaseline(): Baseline = Baseline(
        typicalRestingHeartRateBpm = prefs.getIntOrNull(KEY_BASELINE_HR),
        typicalSkinTemperatureC = prefs.getDoubleOrNull(KEY_BASELINE_TEMP),
        skinTemperatureStdDevC = prefs.getDoubleOrNull(KEY_BASELINE_TEMP_STD)
    )

    fun setBaseline(baseline: Baseline) {
        prefs.edit()
            .putIntOrRemove(KEY_BASELINE_HR, baseline.typicalRestingHeartRateBpm)
            .putDoubleOrRemove(KEY_BASELINE_TEMP, baseline.typicalSkinTemperatureC)
            .putDoubleOrRemove(KEY_BASELINE_TEMP_STD, baseline.skinTemperatureStdDevC)
            .apply()
    }

    fun getRiskProfile(): RiskProfile = RiskProfile(
        ageBand = AgeBand.valueOf(prefs.getString(KEY_AGE_BAND, AgeBand.UNKNOWN.name) ?: AgeBand.UNKNOWN.name),
        pregnant = prefs.getBoolean(KEY_PREGNANT, false),
        chronicIllness = prefs.getBoolean(KEY_CHRONIC, false),
        outdoorWorkOrTravel = prefs.getBoolean(KEY_OUTDOOR, false),
        fastingOrFluidRestriction = prefs.getBoolean(KEY_FASTING, false),
        priorHeatIllness = prefs.getBoolean(KEY_PRIOR_HEAT_ILLNESS, false),
        limitedCoolingAccess = prefs.getBoolean(KEY_LIMITED_COOLING, false)
    )

    fun setRiskProfile(profile: RiskProfile) {
        prefs.edit()
            .putString(KEY_AGE_BAND, profile.ageBand.name)
            .putBoolean(KEY_PREGNANT, profile.pregnant)
            .putBoolean(KEY_CHRONIC, profile.chronicIllness)
            .putBoolean(KEY_OUTDOOR, profile.outdoorWorkOrTravel)
            .putBoolean(KEY_FASTING, profile.fastingOrFluidRestriction)
            .putBoolean(KEY_PRIOR_HEAT_ILLNESS, profile.priorHeatIllness)
            .putBoolean(KEY_LIMITED_COOLING, profile.limitedCoolingAccess)
            .apply()
    }

    fun saveLastDecision(decision: RiskDecision) {
        prefs.edit()
            .putString(KEY_LAST_LEVEL, decision.level.name)
            .putString(KEY_LAST_ACTION, decision.recommendedAction)
            .putLong(KEY_LAST_DECISION_TIME, System.currentTimeMillis())
            .apply()
    }

    fun getLastManualWeather(nowMillis: Long = System.currentTimeMillis()): WeatherSample {
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

    fun setManualWeather(tempC: Double, relativeHumidityPercent: Double, directSun: Boolean, warning: OfficialHeatWarning) {
        prefs.edit()
            .putDouble(KEY_WEATHER_TEMP, tempC)
            .putDouble(KEY_WEATHER_RH, relativeHumidityPercent)
            .putBoolean(KEY_WEATHER_DIRECT_SUN, directSun)
            .putString(KEY_WEATHER_WARNING, warning.name)
            .putLong(KEY_WEATHER_TIME, System.currentTimeMillis())
            .apply()
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
        private const val KEY_LAST_LEVEL = "last_level"
        private const val KEY_LAST_ACTION = "last_action"
        private const val KEY_LAST_DECISION_TIME = "last_decision_time"
        private const val KEY_WEATHER_TEMP = "weather_temp"
        private const val KEY_WEATHER_RH = "weather_rh"
        private const val KEY_WEATHER_DIRECT_SUN = "weather_direct_sun"
        private const val KEY_WEATHER_WARNING = "weather_warning"
        private const val KEY_WEATHER_TIME = "weather_time"
    }
}
