package org.kashmir.heatguard.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable
import org.kashmir.heatguard.core.model.AgeBand
import org.kashmir.heatguard.core.model.RiskDecision
import org.kashmir.heatguard.core.model.RiskProfile
import org.kashmir.heatguard.core.model.SensorSample

class SupabaseRepository(private val client: SupabaseClient?) {

    // ------------------------------------------------------------------ auth

    suspend fun signUpWithEmail(email: String, password: String): Result<String> = runCatching {
        val c = client ?: throw IllegalStateException("Supabase client not configured")
        c.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        c.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("Sign up succeeded but no session was created. Check if email confirmation is enabled in your Supabase project.")
    }

    suspend fun signInWithEmail(email: String, password: String): Result<String> = runCatching {
        val c = client ?: throw IllegalStateException("Supabase client not configured")
        c.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        c.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("Sign in completed but session could not be established.")
    }

    suspend fun signOut(): Result<Unit> = runCatching {
        val c = client ?: return@runCatching
        c.auth.signOut()
    }

    fun currentUserId(): String? = client?.auth?.currentUserOrNull()?.id

    // ------------------------------------------------------------------ profile fetch

    suspend fun fetchUserProfile(userId: String): UserRow? = runCatching {
        val c = client ?: return@runCatching null
        c.from("users").select {
            filter { eq("device_id", userId) }
            limit(1)
        }.decodeList<UserRow>().firstOrNull()
    }.getOrNull()

    suspend fun fetchRiskProfile(userId: String): RiskProfile? = runCatching {
        val c = client ?: return@runCatching null
        val row = c.from("risk_profiles").select {
            filter { eq("device_id", userId) }
            limit(1)
        }.decodeList<RiskProfileRow>().firstOrNull() ?: return@runCatching null
        RiskProfile(
            ageBand = runCatching { AgeBand.valueOf(row.age_band) }.getOrDefault(AgeBand.UNKNOWN),
            pregnant = row.pregnant,
            chronicIllness = row.chronic_illness,
            outdoorWorkOrTravel = row.outdoor_work,
            fastingOrFluidRestriction = row.fasting,
            priorHeatIllness = row.prior_heat_illness,
            limitedCoolingAccess = row.limited_cooling,
            daysAcclimatized = row.days_acclimatized
        )
    }.getOrNull()

    // ------------------------------------------------------------------ sync / upsert

    suspend fun syncUser(
        deviceId: String,
        name: String?,
        age: Int?,
        gender: String?,
        language: String,
        contact1Name: String? = null,
        contact1Phone: String? = null,
        contact2Name: String? = null,
        contact2Phone: String? = null
    ) = runCatching {
        val c = client ?: return@runCatching
        c.from("users").upsert(listOf(
            UserRow(
                device_id = deviceId,
                name = name,
                age = age,
                gender = gender,
                language = language,
                contact_1_name = contact1Name,
                contact_1_phone = contact1Phone,
                contact_2_name = contact2Name,
                contact_2_phone = contact2Phone
            )
        ))
    }

    suspend fun syncRiskProfile(deviceId: String, profile: RiskProfile) = runCatching {
        val c = client ?: return@runCatching
        c.from("risk_profiles").upsert(listOf(
            RiskProfileRow(
                device_id = deviceId,
                age_band = profile.ageBand.name,
                pregnant = profile.pregnant,
                chronic_illness = profile.chronicIllness,
                outdoor_work = profile.outdoorWorkOrTravel,
                fasting = profile.fastingOrFluidRestriction,
                prior_heat_illness = profile.priorHeatIllness,
                limited_cooling = profile.limitedCoolingAccess,
                days_acclimatized = profile.daysAcclimatized
            )
        ))
    }

    suspend fun logDecision(
        deviceId: String,
        decision: RiskDecision,
        ambientTempC: Float? = null
    ) = runCatching {
        val c = client ?: return@runCatching
        c.from("risk_decisions").insert(listOf(
            RiskDecisionRow(
                device_id = deviceId,
                risk_level = decision.level.name,
                confidence = decision.confidence.name,
                reason_codes = decision.reasonCodes.map { it.name },
                recommended_action = decision.recommendedAction,
                effective_heat_index_c = decision.effectiveHeatIndexC?.toFloat(),
                ambient_temp_c = ambientTempC,
                ring_data_age_minutes = decision.ringDataAgeMinutes?.toInt(),
                weather_data_age_minutes = decision.weatherDataAgeMinutes?.toInt()
            )
        ))
    }

    suspend fun logSample(deviceId: String, sample: SensorSample) = runCatching {
        val c = client ?: return@runCatching
        val iso = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(sample.timestampMillis))
        c.from("sensor_samples").insert(listOf(
            SensorSampleRow(
                device_id = deviceId,
                heart_rate_bpm = sample.heartRateBpm,
                skin_temperature_c = sample.skinTemperatureC?.toFloat(),
                steps_since_last = sample.stepsSinceLastSample,
                spo2_percent = sample.spo2Percent,
                battery_percent = sample.batteryPercent,
                connected = sample.connected,
                sampled_at = iso
            )
        ))
    }

    // ------------------------------------------------------------------ admin queries

    suspend fun getAllUsers(): List<UserRow> = runCatching {
        val c = client ?: return@runCatching emptyList()
        c.from("users").select {
            order("created_at", order = Order.DESCENDING)
        }.decodeList<UserRow>()
    }.getOrElse { emptyList() }

    suspend fun getRecentDecisions(limit: Int = 50): List<RiskDecisionRow> = runCatching {
        val c = client ?: return@runCatching emptyList()
        c.from("risk_decisions").select {
            order("created_at", order = Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList<RiskDecisionRow>()
    }.getOrElse { emptyList() }

    suspend fun getRecentSamples(limit: Int = 200): List<SensorSampleRow> = runCatching {
        val c = client ?: return@runCatching emptyList()
        c.from("sensor_samples").select {
            order("sampled_at", order = Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList<SensorSampleRow>()
    }.getOrElse { emptyList() }

    suspend fun getLatestSampleForUser(deviceId: String): SensorSampleRow? = runCatching {
        val c = client ?: return@runCatching null
        c.from("sensor_samples").select {
            filter { eq("device_id", deviceId) }
            order("sampled_at", order = Order.DESCENDING)
            limit(1)
        }.decodeList<SensorSampleRow>().firstOrNull()
    }.getOrNull()

    suspend fun getDecisionsForUser(deviceId: String, limit: Int = 50): List<RiskDecisionRow> = runCatching {
        val c = client ?: return@runCatching emptyList()
        c.from("risk_decisions").select {
            filter {
                eq("device_id", deviceId)
            }
            order("created_at", order = Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList<RiskDecisionRow>()
    }.getOrElse { emptyList() }

    suspend fun isAdmin(userId: String): Boolean = runCatching {
        val c = client ?: return@runCatching false
        c.from("admin_users").select {
            filter { eq("user_id", userId) }
            limit(1)
        }.decodeList<AdminUserCheckRow>().isNotEmpty()
    }.getOrDefault(false)

    // ------------------------------------------------------------------ DTOs

    @Serializable
    data class UserRow(
        val device_id: String,
        val name: String? = null,
        val age: Int? = null,
        val gender: String? = null,
        val language: String = "en",
        val contact_1_name: String? = null,
        val contact_1_phone: String? = null,
        val contact_2_name: String? = null,
        val contact_2_phone: String? = null
    )

    @Serializable
    data class RiskProfileRow(
        val device_id: String,
        val age_band: String,
        val pregnant: Boolean,
        val chronic_illness: Boolean,
        val outdoor_work: Boolean,
        val fasting: Boolean,
        val prior_heat_illness: Boolean,
        val limited_cooling: Boolean,
        val days_acclimatized: Int
    )

    @Serializable
    data class RiskDecisionRow(
        val device_id: String,
        val risk_level: String,
        val confidence: String,
        val reason_codes: List<String>,
        val recommended_action: String? = null,
        val effective_heat_index_c: Float? = null,
        val ambient_temp_c: Float? = null,
        val ring_data_age_minutes: Int? = null,
        val weather_data_age_minutes: Int? = null,
        val created_at: String? = null
    )

    @Serializable
    private data class AdminUserCheckRow(val user_id: String)

    @Serializable
    data class SensorSampleRow(
        val device_id: String,
        val heart_rate_bpm: Int? = null,
        val skin_temperature_c: Float? = null,
        val steps_since_last: Int? = null,
        val spo2_percent: Int? = null,
        val battery_percent: Int? = null,
        val connected: Boolean = true,
        val sampled_at: String
    )
}
