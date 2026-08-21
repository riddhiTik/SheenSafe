package org.kashmir.heatguard.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.kashmir.heatguard.HeatGuardApplication
import org.kashmir.heatguard.R
import org.kashmir.heatguard.alerts.EscalationManager
import org.kashmir.heatguard.core.SystemClock
import org.kashmir.heatguard.core.escalation.EscalationEngine
import org.kashmir.heatguard.core.escalation.EscalationPlan
import org.kashmir.heatguard.core.model.*
import org.kashmir.heatguard.core.risk.HeatRiskEngine
import org.kashmir.heatguard.core.ring.RingDataSource
import org.kashmir.heatguard.data.AppRepository
import org.kashmir.heatguard.ring.SimulatedRingDataSource
import org.kashmir.heatguard.weather.ManualWeatherProvider

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private lateinit var repository: AppRepository
    private lateinit var escalationManager: EscalationManager
    private lateinit var ringDataSource: RingDataSource
    private val engine = HeatRiskEngine()
    private val escalationEngine = EscalationEngine(SystemClock)
    private var ownsDataSource = false
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable: Runnable = object : Runnable {
        override fun run() {
            assessAndRender()
            refreshHandler.postDelayed(this, 5_000L)
        }
    }

    private var pendingTextPhones: List<String> = emptyList()

    private lateinit var btnEmergency: MaterialButton
    private lateinit var heroCard: MaterialCardView
    private lateinit var ivHeroIcon: android.widget.ImageView
    private lateinit var tvHeroHeading: TextView
    private lateinit var tvHeroSubtitle: TextView
    private lateinit var tvCareBody: TextView
    private lateinit var tvBodyTemp: TextView
    private lateinit var tvBodyTempUnit: TextView
    private lateinit var tvHeartRate: TextView
    private lateinit var tvHumidity: TextView
    private lateinit var tvOutsideTemp: TextView
    private lateinit var tvOutsideTempUnit: TextView
    private lateinit var tvWatchStatus: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = AppRepository(requireContext())
        escalationManager = EscalationManager(requireContext(), repository)

        btnEmergency = view.findViewById(R.id.btnEmergency)
        heroCard = view.findViewById(R.id.heroCard)
        ivHeroIcon = view.findViewById(R.id.ivHeroIcon)
        tvHeroHeading = view.findViewById(R.id.tvHeroHeading)
        tvHeroSubtitle = view.findViewById(R.id.tvHeroSubtitle)
        tvCareBody = view.findViewById(R.id.tvCareBody)
        tvBodyTemp = view.findViewById(R.id.tvBodyTemp)
        tvBodyTempUnit = view.findViewById(R.id.tvBodyTempUnit)
        tvHeartRate = view.findViewById(R.id.tvHeartRate)
        tvHumidity = view.findViewById(R.id.tvHumidity)
        tvOutsideTemp = view.findViewById(R.id.tvOutsideTemp)
        tvOutsideTempUnit = view.findViewById(R.id.tvOutsideTempUnit)
        tvWatchStatus = view.findViewById(R.id.tvWatchStatus)

        // ringDataSource setup must come after the findViewById block above —
        // SimulatedRingDataSource.start() invokes its callback synchronously, which calls
        // render() and crashes with UninitializedPropertyAccessException if the views
        // (heroCard etc.) aren't bound yet.
        val shared = (requireActivity().application as HeatGuardApplication).activeRingDataSource
        if (shared != null) {
            // Service is running with a real watch — share its data source.
            // We poll instead of calling start() to avoid overwriting the service's listener.
            ringDataSource = shared
            ownsDataSource = false
            refreshHandler.post(refreshRunnable)
        } else {
            // No service/watch — use simulated data driven by listener callbacks.
            ringDataSource = SimulatedRingDataSource()
            ownsDataSource = true
            ringDataSource.start { assessAndRender() }
        }

        btnEmergency.setOnClickListener {
            val primaryPhone = repository.getPrimaryPhone()
            val nonPrimary = repository.getNonPrimaryPhones()

            if (primaryPhone.isBlank() && nonPrimary.isEmpty()) {
                AlertDialog.Builder(requireContext())
                    .setTitle("No Emergency Contacts")
                    .setMessage("Add emergency contacts in the Contacts tab first.")
                    .setPositiveButton("OK", null)
                    .show()
                return@setOnClickListener
            }

            pendingTextPhones = nonPrimary
            if (primaryPhone.isNotBlank()) {
                escalationManager.dialEmergencyContact()
            } else {
                nonPrimary.firstOrNull()?.let { escalationManager.openSmsForContact(it) }
                pendingTextPhones = nonPrimary.drop(1)
            }
        }

        ensureDefaultBaseline()
        assessAndRender()
    }

    override fun onResume() {
        super.onResume()
        val phones = pendingTextPhones
        pendingTextPhones = emptyList()
        phones.forEach { escalationManager.openSmsForContact(it) }
    }

    override fun onDestroyView() {
        refreshHandler.removeCallbacks(refreshRunnable)
        if (ownsDataSource) ringDataSource.stop()
        super.onDestroyView()
    }

    private fun bestAvailableWeather(now: Long): WeatherSample {
        val apiCached = repository.getCachedApiWeather()
        val manual = ManualWeatherProvider(repository).latestWeather(now)
        return if (apiCached != null) {
            apiCached.copy(directSun = manual.directSun, officialWarning = manual.officialWarning)
        } else {
            manual
        }
    }

    private fun subtitleFor(decision: RiskDecision): String {
        val r = decision.reasonCodes
        return when {
            ReasonCode.COMBINED_PHYSIOLOGY_STRAIN in r                 -> "Elevated heart rate and skin temperature detected"
            ReasonCode.HEART_RATE_HIGH_AT_REST in r                    -> "Heart rate elevated at rest"
            ReasonCode.SKIN_TEMPERATURE_HIGH_RELATIVE_TO_BASELINE in r -> "Skin temperature above your baseline"
            ReasonCode.SKIN_TEMPERATURE_RISING in r                    -> "Skin temperature rising"
            ReasonCode.ESTIMATED_CORE_TEMP_ELEVATED in r               -> "Estimated body temperature elevated"
            ReasonCode.EXTREME_HEAT_INDEX in r                         -> "Extreme heat index — dangerous outdoor conditions"
            ReasonCode.HIGH_HEAT_INDEX in r                            -> "Heat index is dangerously high"
            ReasonCode.LOW_HEAT_INDEX in r                             -> "Heat index is elevated"
            ReasonCode.OFFICIAL_EXTREME_WARNING in r                   -> "Official extreme heat warning in effect"
            ReasonCode.OFFICIAL_HEAT_WARNING in r                      -> "Official heat warning in effect"
            ReasonCode.OFFICIAL_HEAT_WATCH in r                        -> "Official heat watch in effect"
            ReasonCode.RING_DATA_STALE in r || ReasonCode.WEATHER_DATA_STALE in r -> "Some monitoring data is outdated"
            ReasonCode.RING_DATA_MISSING in r                          -> "No wearable data yet — connect your watch"
            ReasonCode.WEATHER_DATA_MISSING in r                       -> "No weather data yet — fetching…"
            else -> when (decision.level) {
                RiskLevel.SAFE      -> "All vitals and conditions normal"
                RiskLevel.CHECK     -> "Monitor your symptoms carefully"
                RiskLevel.ACTION    -> "Move to a cool area immediately"
                RiskLevel.EMERGENCY -> "Seek help immediately"
            }
        }
    }

    private fun assessAndRender() {
        val now = System.currentTimeMillis()
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = ringDataSource.latestSample(),
                weatherSample = bestAvailableWeather(now),
                baseline = repository.getBaseline(),
                riskProfile = repository.getRiskProfile()
            )
        )
        val plan = escalationEngine.plan(decision)
        repository.saveLastDecision(decision)
        if (isAdded) render(decision, plan)
    }

    private fun render(decision: RiskDecision, plan: EscalationPlan) {
        val unit = repository.getTempUnit()
        val now = System.currentTimeMillis()
        val sample = ringDataSource.latestSample()
        val weather = bestAvailableWeather(now)

        // Hero card
        val subtitle = subtitleFor(decision)
        when (decision.level) {
            RiskLevel.SAFE -> {
                heroCard.setCardBackgroundColor(Color.parseColor("#1A19E661"))
                heroCard.strokeColor = Color.parseColor("#3319E661")
                ivHeroIcon.setImageResource(R.drawable.ic_favorite)
                ivHeroIcon.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.primary)
                tvHeroHeading.text = getString(R.string.status_safe_heading)
                tvHeroSubtitle.text = subtitle
            }
            RiskLevel.CHECK -> {
                heroCard.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.risk_check_bg))
                heroCard.strokeColor = Color.parseColor("#4DF4A261")
                ivHeroIcon.setImageResource(R.drawable.ic_warning_filled)
                ivHeroIcon.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.warning_color)
                tvHeroHeading.text = getString(R.string.status_check_heading)
                tvHeroSubtitle.text = subtitle
            }
            RiskLevel.ACTION -> {
                heroCard.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.risk_action_bg))
                heroCard.strokeColor = Color.parseColor("#4DE07A5F")
                ivHeroIcon.setImageResource(R.drawable.ic_warning_filled)
                ivHeroIcon.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.critical_color)
                tvHeroHeading.text = getString(R.string.status_action_heading)
                tvHeroSubtitle.text = subtitle
            }
            RiskLevel.EMERGENCY -> {
                heroCard.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.risk_emergency_bg))
                heroCard.strokeColor = ContextCompat.getColor(requireContext(), R.color.critical_color)
                ivHeroIcon.setImageResource(R.drawable.ic_warning_filled)
                ivHeroIcon.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.critical_color)
                tvHeroHeading.text = getString(R.string.status_emergency_heading)
                tvHeroSubtitle.text = subtitle
            }
        }

        // Care message
        tvCareBody.text = decision.recommendedAction

        // Vitals
        val isCelsius = unit == "C"
        val unitLabel = if (isCelsius) getString(R.string.unit_celsius) else "°F"
        tvBodyTempUnit.text = unitLabel
        tvOutsideTempUnit.text = unitLabel

        tvBodyTemp.text = sample?.skinTemperatureC?.let { formatTemp(it, unit) } ?: getString(R.string.metric_no_data)
        tvHeartRate.text = sample?.heartRateBpm?.toString() ?: getString(R.string.metric_no_data)
        tvHumidity.text = weather.relativeHumidityPercent?.let { "%.0f".format(it) } ?: getString(R.string.metric_no_data)
        tvOutsideTemp.text = weather.airTemperatureC?.let { formatTemp(it, unit) } ?: getString(R.string.metric_no_data)

        // Notifications are handled by HeatMonitoringService with proper cooldowns.
        // Firing them here caused a notification every 5 seconds.

        // Watch connection status
        val watch6Enabled = repository.getUseWatch6()
        val isConfigured = watch6Enabled || repository.getRingDeviceMac() != null
        val s = ringDataSource.latestSample()
        val ageSeconds = if (s != null) (System.currentTimeMillis() - s.timestampMillis) / 1000L else null

        tvWatchStatus.text = when {
            !isConfigured -> "Watch: not configured"
            s == null -> if (watch6Enabled) "Watch: waiting for Galaxy Watch…" else "Watch: connecting…"
            s.connected == false -> "Watch: disconnected"
            ageSeconds != null && ageSeconds < 30 -> {
                val hr = s.heartRateBpm
                if (hr != null) "Watch: live — HR $hr bpm (${ageSeconds}s ago)"
                else "Watch: connected — waiting for HR…"
            }
            else -> "Watch: connected — no recent data (${ageSeconds}s ago)"
        }
        tvWatchStatus.setTextColor(ContextCompat.getColor(
            requireContext(),
            if (s != null && s.connected && ageSeconds != null && ageSeconds < 30) R.color.primary_dark
            else R.color.muted
        ))
    }

    private fun formatTemp(celsius: Double, unit: String): String {
        return if (unit == "C") "%.1f".format(celsius)
        else "%.1f".format(celsius * 9.0 / 5.0 + 32.0)
    }

    private fun ensureDefaultBaseline() {
        val baseline = repository.getBaseline()
        if (baseline.typicalRestingHeartRateBpm == null || baseline.typicalSkinTemperatureC == null) {
            repository.setBaseline(
                Baseline(
                    typicalRestingHeartRateBpm = baseline.typicalRestingHeartRateBpm ?: 80,
                    typicalSkinTemperatureC = baseline.typicalSkinTemperatureC ?: 34.2,
                    skinTemperatureStdDevC = baseline.skinTemperatureStdDevC ?: 0.5
                )
            )
        }
    }

}
