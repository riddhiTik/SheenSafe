package org.kashmir.heatguard.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = AppRepository(requireContext())
        escalationManager = EscalationManager(requireContext(), repository)

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

        view.findViewById<android.widget.ImageButton>(R.id.btnNotifications).setOnClickListener {
            showWeatherDialog()
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

    private fun assessAndRender() {
        val now = System.currentTimeMillis()
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = ringDataSource.latestSample(),
                weatherSample = ManualWeatherProvider(repository).latestWeather(now),
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
        val weather = ManualWeatherProvider(repository).latestWeather(now)

        // Hero card
        when (decision.level) {
            RiskLevel.SAFE -> {
                heroCard.setCardBackgroundColor(Color.parseColor("#1A19E661"))
                heroCard.strokeColor = Color.parseColor("#3319E661")
                ivHeroIcon.setImageResource(R.drawable.ic_favorite)
                ivHeroIcon.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.primary)
                tvHeroHeading.text = getString(R.string.status_safe_heading)
                tvHeroSubtitle.text = getString(R.string.status_safe_sub)
            }
            RiskLevel.CHECK -> {
                heroCard.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.risk_check_bg))
                heroCard.strokeColor = Color.parseColor("#4DF4A261")
                ivHeroIcon.setImageResource(R.drawable.ic_warning_filled)
                ivHeroIcon.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.warning_color)
                tvHeroHeading.text = getString(R.string.status_check_heading)
                tvHeroSubtitle.text = getString(R.string.status_check_sub)
            }
            RiskLevel.ACTION -> {
                heroCard.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.risk_action_bg))
                heroCard.strokeColor = Color.parseColor("#4DE07A5F")
                ivHeroIcon.setImageResource(R.drawable.ic_warning_filled)
                ivHeroIcon.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.critical_color)
                tvHeroHeading.text = getString(R.string.status_action_heading)
                tvHeroSubtitle.text = getString(R.string.status_action_sub)
            }
            RiskLevel.EMERGENCY -> {
                heroCard.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.risk_emergency_bg))
                heroCard.strokeColor = ContextCompat.getColor(requireContext(), R.color.critical_color)
                ivHeroIcon.setImageResource(R.drawable.ic_warning_filled)
                ivHeroIcon.imageTintList = ContextCompat.getColorStateList(requireContext(), R.color.critical_color)
                tvHeroHeading.text = getString(R.string.status_emergency_heading)
                tvHeroSubtitle.text = getString(R.string.status_emergency_sub)
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

    private fun showWeatherDialog() {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val existing = repository.getLastManualWeather(System.currentTimeMillis())
        val temp = EditText(ctx).apply {
            hint = getString(R.string.temperature_c)
            setText(existing.airTemperatureC?.toString() ?: "36.0")
        }
        val rh = EditText(ctx).apply {
            hint = getString(R.string.relative_humidity)
            setText(existing.relativeHumidityPercent?.toString() ?: "45.0")
        }
        val sun = CheckBox(ctx).apply {
            text = getString(R.string.direct_sun)
            isChecked = existing.directSun
        }
        val warning = Spinner(ctx).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, OfficialHeatWarning.values().map { it.name })
            setSelection(OfficialHeatWarning.values().indexOf(existing.officialWarning))
        }
        layout.addView(temp)
        layout.addView(rh)
        layout.addView(sun)
        layout.addView(warning)
        AlertDialog.Builder(ctx)
            .setTitle(R.string.set_weather)
            .setView(layout)
            .setPositiveButton(R.string.save) { _, _ ->
                repository.setManualWeather(
                    tempC = temp.text.toString().toDoubleOrNull() ?: 36.0,
                    relativeHumidityPercent = rh.text.toString().toDoubleOrNull() ?: 45.0,
                    directSun = sun.isChecked,
                    warning = OfficialHeatWarning.valueOf(warning.selectedItem.toString())
                )
                assessAndRender()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
