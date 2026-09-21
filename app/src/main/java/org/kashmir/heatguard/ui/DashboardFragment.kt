package org.kashmir.heatguard.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import org.kashmir.heatguard.data.NotificationHistoryEntry
import org.kashmir.heatguard.ring.SimulatedRingDataSource
import org.kashmir.heatguard.weather.ApiHourlyForecastProvider
import org.kashmir.heatguard.weather.CachedHourlyForecastProvider
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
    private var lastDecision: RiskDecision? = null

    private val bodyTempHistory = ArrayDeque<Float>()
    private val heartRateHistory = ArrayDeque<Float>()
    private val humidityHistory = ArrayDeque<Float>()
    private val outsideTempHistory = ArrayDeque<Float>()

    private lateinit var btnEmergency: MaterialButton
    private lateinit var heroCard: MaterialCardView
    private lateinit var heroContent: LinearLayout
    private lateinit var tvRiskBadge: TextView
    private lateinit var tvHeroHeading: TextView
    private lateinit var tvHeroSubtitle: TextView
    private lateinit var heatIndexThumb: View
    private lateinit var tvCareBody: TextView
    private lateinit var tvBodyTemp: TextView
    private lateinit var tvBodyTempUnit: TextView
    private lateinit var tvHeartRate: TextView
    private lateinit var tvHumidity: TextView
    private lateinit var tvOutsideTemp: TextView
    private lateinit var tvOutsideTempUnit: TextView
    private lateinit var tvWatchStatus: TextView
    private lateinit var ivBodyTempTrend: ImageView
    private lateinit var ivHeartRateTrend: ImageView
    private lateinit var ivHumidityTrend: ImageView
    private lateinit var ivOutsideTempTrend: ImageView
    private lateinit var sparkBodyTemp: SparklineView
    private lateinit var sparkHeartRate: SparklineView
    private lateinit var sparkHumidity: SparklineView
    private lateinit var sparkOutsideTemp: SparklineView
    private lateinit var forecastRow: LinearLayout
    private lateinit var forecastSlots: List<View>
    private lateinit var tvNotificationBadge: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = AppRepository(requireContext())
        escalationManager = EscalationManager(requireContext(), repository)

        btnEmergency = view.findViewById(R.id.btnEmergency)
        heroCard = view.findViewById(R.id.heroCard)
        heroContent = view.findViewById(R.id.heroContent)
        tvRiskBadge = view.findViewById(R.id.tvRiskBadge)
        tvHeroHeading = view.findViewById(R.id.tvHeroHeading)
        tvHeroSubtitle = view.findViewById(R.id.tvHeroSubtitle)
        heatIndexThumb = view.findViewById(R.id.heatIndexThumb)
        tvCareBody = view.findViewById(R.id.tvCareBody)
        tvBodyTemp = view.findViewById(R.id.tvBodyTemp)
        tvBodyTempUnit = view.findViewById(R.id.tvBodyTempUnit)
        tvHeartRate = view.findViewById(R.id.tvHeartRate)
        tvHumidity = view.findViewById(R.id.tvHumidity)
        tvOutsideTemp = view.findViewById(R.id.tvOutsideTemp)
        tvOutsideTempUnit = view.findViewById(R.id.tvOutsideTempUnit)
        tvWatchStatus = view.findViewById(R.id.tvWatchStatus)
        ivBodyTempTrend = view.findViewById(R.id.ivBodyTempTrend)
        ivHeartRateTrend = view.findViewById(R.id.ivHeartRateTrend)
        ivHumidityTrend = view.findViewById(R.id.ivHumidityTrend)
        ivOutsideTempTrend = view.findViewById(R.id.ivOutsideTempTrend)
        sparkBodyTemp = view.findViewById(R.id.sparkBodyTemp)
        sparkHeartRate = view.findViewById(R.id.sparkHeartRate)
        sparkHumidity = view.findViewById(R.id.sparkHumidity)
        sparkOutsideTemp = view.findViewById(R.id.sparkOutsideTemp)
        forecastRow = view.findViewById(R.id.forecastRow)
        forecastSlots = listOf(
            view.findViewById(R.id.forecast0),
            view.findViewById(R.id.forecast1),
            view.findViewById(R.id.forecast2),
            view.findViewById(R.id.forecast3),
            view.findViewById(R.id.forecast4)
        )
        tvNotificationBadge = view.findViewById(R.id.tvNotificationBadge)

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

        view.findViewById<android.widget.ImageButton>(R.id.btnNotifications).setOnClickListener {
            showNotificationHistory()
        }
        refreshNotificationBadge()

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
        loadHourlyForecast()
    }

    override fun onResume() {
        super.onResume()
        val phones = pendingTextPhones
        pendingTextPhones = emptyList()
        phones.forEach { escalationManager.openSmsForContact(it) }

        (requireActivity().application as HeatGuardApplication).inAppAlertListener = { decision ->
            if (isAdded) showInAppAlert(decision)
        }
        refreshNotificationBadge()
    }

    override fun onPause() {
        (requireActivity().application as HeatGuardApplication).inAppAlertListener = null
        super.onPause()
    }

    override fun onDestroyView() {
        refreshHandler.removeCallbacks(refreshRunnable)
        if (ownsDataSource) ringDataSource.stop()
        super.onDestroyView()
    }

    /** Routine risk update while this screen is open — shown in-app only, never as a system notification. */
    private fun showInAppAlert(decision: RiskDecision) {
        val root = view ?: return
        Snackbar.make(root, getString(R.string.heat_alert_title, decision.level.name) + ": " + decision.recommendedAction, Snackbar.LENGTH_LONG).show()
        refreshNotificationBadge()
    }

    private fun refreshNotificationBadge() {
        val unread = repository.getNotificationHistory().count { it.timestampMillis > repository.getLastNotificationsViewedAt() }
        tvNotificationBadge.visibility = if (unread > 0) View.VISIBLE else View.GONE
        tvNotificationBadge.text = if (unread > 9) "9+" else unread.toString()
    }

    private fun showNotificationHistory() {
        val entries = repository.getNotificationHistory()
        repository.markNotificationsViewed()
        refreshNotificationBadge()

        if (entries.isEmpty()) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.notification_history_title)
                .setMessage(R.string.notification_history_empty)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }

        val formatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        val rows = entries.map { entry: NotificationHistoryEntry ->
            "${formatter.format(Date(entry.timestampMillis))} — ${entry.level.name}\n${entry.actionText}"
        }
        val listView = ListView(requireContext()).apply {
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, rows)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.notification_history_title)
            .setView(listView)
            .setPositiveButton(R.string.ok, null)
            .show()
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
        lastDecision = decision
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
                heroContent.setBackgroundResource(R.drawable.bg_hero_gradient_safe)
                tvRiskBadge.text = "SAFE"
                tvHeroHeading.text = getString(R.string.status_safe_heading)
                tvHeroSubtitle.text = getString(R.string.status_safe_sub)
            }
            RiskLevel.CHECK -> {
                heroContent.setBackgroundResource(R.drawable.bg_hero_gradient_check)
                tvRiskBadge.text = "CHECK IN"
                tvHeroHeading.text = getString(R.string.status_check_heading)
                tvHeroSubtitle.text = getString(R.string.status_check_sub)
            }
            RiskLevel.ACTION -> {
                heroContent.setBackgroundResource(R.drawable.bg_hero_gradient_action)
                tvRiskBadge.text = "ACTION NEEDED"
                tvHeroHeading.text = getString(R.string.status_action_heading)
                tvHeroSubtitle.text = getString(R.string.status_action_sub)
            }
            RiskLevel.EMERGENCY -> {
                heroContent.setBackgroundResource(R.drawable.bg_hero_gradient_emergency)
                tvRiskBadge.text = "CRITICAL RISK"
                tvHeroHeading.text = getString(R.string.status_emergency_heading)
                tvHeroSubtitle.text = getString(R.string.status_emergency_sub)
            }
        }

        // Heat index slider — map effective heat index (°F) onto the 70–130°F gradient track.
        val heatIndexF = decision.effectiveHeatIndexC?.let { it * 9.0 / 5.0 + 32.0 }
        heroCard.post {
            val trackWidth = heroContent.width
            if (trackWidth > 0) {
                val fraction = heatIndexF?.let { ((it - 70.0) / (130.0 - 70.0)).coerceIn(0.0, 1.0) } ?: 0.0
                val thumbWidth = heatIndexThumb.width.takeIf { it > 0 } ?: heatIndexThumb.layoutParams.width
                val maxTranslation = (trackWidth - thumbWidth - heroContent.paddingLeft - heroContent.paddingRight).coerceAtLeast(0)
                heatIndexThumb.translationX = (fraction * maxTranslation).toFloat()
            }
        }

        // Care message
        tvCareBody.text = decision.recommendedAction

        // Vitals
        val isCelsius = unit == "C"
        val unitLabel = if (isCelsius) getString(R.string.unit_celsius) else "°F"
        tvBodyTempUnit.text = unitLabel
        tvOutsideTempUnit.text = unitLabel

        val bodyTempC = sample?.skinTemperatureC
        tvBodyTemp.text = bodyTempC?.let { formatTemp(it, unit) } ?: getString(R.string.metric_no_data)
        updateTrend(bodyTempHistory, bodyTempC?.let { formatTemp(it, unit).toFloatOrNull() }, sparkBodyTemp, ivBodyTempTrend, R.color.vital_temp_accent)

        val heartRate = sample?.heartRateBpm
        tvHeartRate.text = heartRate?.toString() ?: getString(R.string.metric_no_data)
        updateTrend(heartRateHistory, heartRate?.toFloat(), sparkHeartRate, ivHeartRateTrend, R.color.vital_heart_accent)

        val humidity = weather.relativeHumidityPercent
        tvHumidity.text = humidity?.let { "%.0f".format(it) } ?: getString(R.string.metric_no_data)
        updateTrend(humidityHistory, humidity?.toFloat(), sparkHumidity, ivHumidityTrend, R.color.vital_humidity_accent)

        val outsideTempC = weather.airTemperatureC
        tvOutsideTemp.text = outsideTempC?.let { formatTemp(it, unit) } ?: getString(R.string.metric_no_data)
        updateTrend(outsideTempHistory, outsideTempC?.let { formatTemp(it, unit).toFloatOrNull() }, sparkOutsideTemp, ivOutsideTempTrend, R.color.vital_outside_accent)

        // Notifications are handled by HeatMonitoringService with proper cooldowns.
        // Firing them here caused a notification every 5 seconds.

        // Watch connection status
        val watch6Enabled = repository.getUseWatch6()
        val isConfigured = watch6Enabled || repository.getRingDeviceMac() != null
        val s = ringDataSource.latestSample()
        val ageSeconds = if (s != null) (System.currentTimeMillis() - s.timestampMillis) / 1000L else null

        tvWatchStatus.text = when {
            !isConfigured -> "Watch: not configured"
            s == null -> if (watch6Enabled) "Watch: waiting…" else "Watch: connecting…"
            s.connected == false -> "Watch: disconnected"
            ageSeconds != null && ageSeconds < 30 -> {
                val hr = s.heartRateBpm
                if (hr != null) "Watch: live — $hr bpm" else "Watch: connected"
            }
            else -> "Watch: no recent data"
        }
    }

    /** Appends the latest value to a rolling history buffer, then updates its sparkline + trend arrow. */
    private fun updateTrend(
        history: ArrayDeque<Float>,
        latest: Float?,
        sparkline: SparklineView,
        trendIcon: ImageView,
        accentColorRes: Int
    ) {
        if (latest != null) {
            history.addLast(latest)
            while (history.size > 12) history.removeFirst()
        }
        val accent = ContextCompat.getColor(requireContext(), accentColorRes)
        sparkline.setData(history.toList(), accent)

        val direction = if (history.size >= 2) history.last() - history[history.size - 2] else 0f
        trendIcon.setImageResource(
            when {
                direction > 0.05f -> R.drawable.ic_trending_up
                direction < -0.05f -> R.drawable.ic_trending_down
                else -> R.drawable.ic_trending_flat
            }
        )
        trendIcon.imageTintList = ContextCompat.getColorStateList(requireContext(), accentColorRes)
    }

    private fun formatTemp(celsius: Double, unit: String): String {
        return if (unit == "C") "%.1f".format(celsius)
        else "%.1f".format(celsius * 9.0 / 5.0 + 32.0)
    }

    private fun loadHourlyForecast() {
        val lat = repository.getLastLatitude() ?: 34.0837
        val lon = repository.getLastLongitude() ?: 74.7973
        viewLifecycleOwner.lifecycleScope.launch {
            val points = withContext(Dispatchers.IO) {
                runCatching {
                    CachedHourlyForecastProvider(ApiHourlyForecastProvider(lat, lon), repository)
                        .hourlyForecast(System.currentTimeMillis())
                }.getOrDefault(emptyList())
            }
            if (!isAdded || points.isEmpty()) return@launch

            val unit = repository.getTempUnit()
            val hourFormat = SimpleDateFormat("h a", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("Asia/Kolkata")
            }

            forecastSlots.forEachIndexed { index, slotView ->
                val point = points.getOrNull(index)
                if (point == null) {
                    slotView.visibility = View.GONE
                    return@forEachIndexed
                }
                slotView.visibility = View.VISIBLE
                slotView.findViewById<TextView>(R.id.tvForecastHour).text = hourFormat.format(Date(point.timestampMillis))
                slotView.findViewById<TextView>(R.id.tvForecastTemp).text = formatTemp(point.airTemperatureC, unit) + if (unit == "C") "°" else "°F"

                val fahrenheit = point.airTemperatureC * 9.0 / 5.0 + 32.0
                val dotColorRes = when {
                    fahrenheit > 103 -> R.color.heat_index_red
                    fahrenheit > 90 -> R.color.heat_index_orange
                    fahrenheit > 80 -> R.color.heat_index_yellow
                    else -> R.color.heat_index_green
                }
                slotView.findViewById<View>(R.id.viewForecastDot).backgroundTintList =
                    ContextCompat.getColorStateList(requireContext(), dotColorRes)
            }
            forecastRow.visibility = View.VISIBLE
        }
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
