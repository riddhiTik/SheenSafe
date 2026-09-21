package org.kashmir.heatguard.data

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.kashmir.heatguard.core.model.*

// TODO: [PRIVACY-HARDENING] Replace SharedPreferences with EncryptedSharedPreferences once
// androidx is enabled. Set android.useAndroidX=true in gradle.properties, add
// implementation("androidx.security:security-crypto:1.1.0-alpha06") to app/build.gradle.kts,
// then replace context.getSharedPreferences(...) with EncryptedSharedPreferences.create(...).
class AppRepository(context: Context) : BaselineRepository, RiskProfileRepository, ContactRepository, WeatherRepository, HourlyForecastRepository {
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

    fun setOnboardingComplete(done: Boolean = true) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, done).apply()
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

    // Relations for the two primary (sync-backed) contact slots.
    fun getContact1Relation(): String = prefs.getString(KEY_CONTACT_1_RELATION, "") ?: ""
    fun setContact1Relation(relation: String) { prefs.edit().putString(KEY_CONTACT_1_RELATION, relation.trim()).apply() }
    fun getContact2Relation(): String = prefs.getString(KEY_CONTACT_2_RELATION, "") ?: ""
    fun setContact2Relation(relation: String) { prefs.edit().putString(KEY_CONTACT_2_RELATION, relation.trim()).apply() }

    // Secondary contacts (local-only, not synced to Supabase — its schema has only two contact slots).
    fun getThirdContactName(): String = prefs.getString(KEY_CONTACT_3_NAME, "") ?: ""
    fun getThirdContactPhone(): String = prefs.getString(KEY_CONTACT_3_PHONE, "") ?: ""
    fun getThirdContactRelation(): String = prefs.getString(KEY_CONTACT_3_RELATION, "") ?: ""
    fun setThirdContact(name: String, phone: String, relation: String) {
        prefs.edit()
            .putString(KEY_CONTACT_3_NAME, name.trim())
            .putString(KEY_CONTACT_3_PHONE, phone.trim())
            .putString(KEY_CONTACT_3_RELATION, relation.trim())
            .apply()
    }

    fun getFourthContactName(): String = prefs.getString(KEY_CONTACT_4_NAME, "") ?: ""
    fun getFourthContactPhone(): String = prefs.getString(KEY_CONTACT_4_PHONE, "") ?: ""
    fun getFourthContactRelation(): String = prefs.getString(KEY_CONTACT_4_RELATION, "") ?: ""
    fun setFourthContact(name: String, phone: String, relation: String) {
        prefs.edit()
            .putString(KEY_CONTACT_4_NAME, name.trim())
            .putString(KEY_CONTACT_4_PHONE, phone.trim())
            .putString(KEY_CONTACT_4_RELATION, relation.trim())
            .apply()
    }

    fun getPrimaryContactIndex(): Int = prefs.getInt(KEY_PRIMARY_CONTACT_INDEX, 1)
    fun setPrimaryContactIndex(index: Int) {
        prefs.edit().putInt(KEY_PRIMARY_CONTACT_INDEX, index).apply()
    }

    private fun getContactPhone(index: Int): String = when (index) {
        1 -> getEmergencyContactPhone()
        2 -> getSecondContactPhone()
        3 -> getThirdContactPhone()
        else -> getFourthContactPhone()
    }

    fun getPrimaryPhone(): String = getContactPhone(getPrimaryContactIndex())

    fun getNonPrimaryPhones(): List<String> {
        val primaryIndex = getPrimaryContactIndex()
        return (1..4)
            .filter { it != primaryIndex }
            .map { getContactPhone(it) }
            .filter { it.isNotBlank() }
    }

    /**
     * User-facing "notify me above X°F" preference shown in Settings. This is independent of
     * the clinical RiskLevel thresholds in HeatRiskEngine (which are fixed and require
     * clinician review) — it only drives the informational slider UI for now.
     */
    fun getCustomAlertThresholdF(): Int = prefs.getInt(KEY_CUSTOM_ALERT_THRESHOLD_F, 101)
    fun setCustomAlertThresholdF(value: Int) {
        prefs.edit().putInt(KEY_CUSTOM_ALERT_THRESHOLD_F, value).apply()
    }

    fun getAlertHeatRisk(): Boolean = prefs.getBoolean(KEY_ALERT_HEAT_RISK, true)
    fun setAlertHeatRisk(on: Boolean) { prefs.edit().putBoolean(KEY_ALERT_HEAT_RISK, on).apply() }
    fun getAlertHydration(): Boolean = prefs.getBoolean(KEY_ALERT_HYDRATION, true)
    fun setAlertHydration(on: Boolean) { prefs.edit().putBoolean(KEY_ALERT_HYDRATION, on).apply() }
    fun getAlertEmergency(): Boolean = prefs.getBoolean(KEY_ALERT_EMERGENCY, true)
    fun setAlertEmergency(on: Boolean) { prefs.edit().putBoolean(KEY_ALERT_EMERGENCY, on).apply() }
    fun getTempUnit(): String = prefs.getString(KEY_TEMP_UNIT, "C") ?: "C"
    fun setTempUnit(unit: String) { prefs.edit().putString(KEY_TEMP_UNIT, unit).apply() }

    fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

    fun setBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun getOrCreateDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (existing != null) return existing
        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
        return newId
    }

    fun getRingDeviceMac(): String? = prefs.getString(KEY_RING_MAC, null)

    fun setRingDeviceMac(mac: String) {
        prefs.edit().putString(KEY_RING_MAC, mac).apply()
    }

    fun getSupabaseUserId(): String = prefs.getString(KEY_SUPABASE_USER_ID, "") ?: ""

    fun setSupabaseUserId(id: String) {
        prefs.edit().putString(KEY_SUPABASE_USER_ID, id).apply()
    }

    fun getLastLatitude(): Double? = prefs.getDoubleOrNull(KEY_LAST_LAT)
    fun getLastLongitude(): Double? = prefs.getDoubleOrNull(KEY_LAST_LON)
    fun setLastKnownCoords(lat: Double, lon: Double) {
        prefs.edit()
            .putDouble(KEY_LAST_LAT, lat)
            .putDouble(KEY_LAST_LON, lon)
            .apply()
    }

    override fun getCachedHourlyForecast(): List<HourlyForecastPoint> {
        val json = prefs.getString(KEY_HOURLY_FORECAST, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                HourlyForecastPoint(
                    timestampMillis = obj.getLong("t"),
                    airTemperatureC = obj.getDouble("c")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun setCachedHourlyForecast(nowMillis: Long, points: List<HourlyForecastPoint>) {
        val array = JSONArray()
        points.forEach { point ->
            array.put(JSONObject().put("t", point.timestampMillis).put("c", point.airTemperatureC))
        }
        prefs.edit()
            .putString(KEY_HOURLY_FORECAST, array.toString())
            .putLong(KEY_HOURLY_FORECAST_FETCHED_AT, nowMillis)
            .apply()
    }

    fun getHourlyForecastFetchedAt(): Long = prefs.getLong(KEY_HOURLY_FORECAST_FETCHED_AT, 0L)

    fun getUseWatch6(): Boolean = prefs.getBoolean(KEY_USE_WATCH6, false)

    fun setUseWatch6(on: Boolean) {
        prefs.edit().putBoolean(KEY_USE_WATCH6, on).apply()
    }

    fun setEmergencyAlertSuppressedUntil(millis: Long) {
        prefs.edit().putLong(KEY_EMERGENCY_ALERT_SUPPRESSED_UNTIL, millis).apply()
    }

    fun isEmergencyAlertSuppressed(nowMillis: Long): Boolean =
        nowMillis < prefs.getLong(KEY_EMERGENCY_ALERT_SUPPRESSED_UNTIL, 0L)

    fun deleteAllData() {
        prefs.edit().clear().apply()
    }

    /** Appends one entry per risk check (every tick of the monitoring pipeline), newest first, capped at [MAX_NOTIFICATION_HISTORY]. */
    fun addNotificationHistoryEntry(level: RiskLevel, actionText: String, timestampMillis: Long = System.currentTimeMillis()) {
        val array = JSONArray(prefs.getString(KEY_NOTIFICATION_HISTORY, null) ?: "[]")
        val entry = JSONObject()
            .put("t", timestampMillis)
            .put("level", level.name)
            .put("action", actionText)
        val updated = JSONArray()
        updated.put(entry)
        for (i in 0 until minOf(array.length(), MAX_NOTIFICATION_HISTORY - 1)) {
            updated.put(array.getJSONObject(i))
        }
        prefs.edit().putString(KEY_NOTIFICATION_HISTORY, updated.toString()).apply()
    }

    fun getNotificationHistory(): List<NotificationHistoryEntry> {
        val json = prefs.getString(KEY_NOTIFICATION_HISTORY, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                NotificationHistoryEntry(
                    timestampMillis = obj.getLong("t"),
                    level = RiskLevel.valueOf(obj.getString("level")),
                    actionText = obj.getString("action")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getLastNotificationsViewedAt(): Long = prefs.getLong(KEY_NOTIFICATIONS_VIEWED_AT, 0L)

    fun markNotificationsViewed(atMillis: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_NOTIFICATIONS_VIEWED_AT, atMillis).apply()
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
        private const val KEY_CONTACT_1_RELATION = "contact_1_relation"
        private const val KEY_CONTACT_2_RELATION = "contact_2_relation"
        private const val KEY_CONTACT_3_NAME = "contact_3_name"
        private const val KEY_CONTACT_3_PHONE = "contact_3_phone"
        private const val KEY_CONTACT_3_RELATION = "contact_3_relation"
        private const val KEY_CONTACT_4_NAME = "contact_4_name"
        private const val KEY_CONTACT_4_PHONE = "contact_4_phone"
        private const val KEY_CONTACT_4_RELATION = "contact_4_relation"
        private const val KEY_CUSTOM_ALERT_THRESHOLD_F = "custom_alert_threshold_f"
        private const val KEY_ALERT_HEAT_RISK = "alert_heat_risk"
        private const val KEY_ALERT_HYDRATION = "alert_hydration"
        private const val KEY_ALERT_EMERGENCY = "alert_emergency"
        private const val KEY_TEMP_UNIT = "temp_unit"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_RING_MAC = "ring_device_mac"
        private const val KEY_SUPABASE_USER_ID = "supabase_user_id"
        private const val KEY_LAST_LAT = "last_lat"
        private const val KEY_LAST_LON = "last_lon"
        private const val KEY_PRIMARY_CONTACT_INDEX = "primary_contact_index"
        private const val KEY_EMERGENCY_ALERT_SUPPRESSED_UNTIL = "emergency_alert_suppressed_until"
        private const val KEY_USE_WATCH6 = "use_watch6"
        private const val KEY_HOURLY_FORECAST = "hourly_forecast_json"
        private const val KEY_HOURLY_FORECAST_FETCHED_AT = "hourly_forecast_fetched_at"
        private const val KEY_NOTIFICATION_HISTORY = "notification_history_json"
        private const val KEY_NOTIFICATIONS_VIEWED_AT = "notifications_viewed_at"
        private const val MAX_NOTIFICATION_HISTORY = 50
    }
}

data class NotificationHistoryEntry(
    val timestampMillis: Long,
    val level: RiskLevel,
    val actionText: String
)
