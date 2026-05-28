package org.kashmir.heatguard.ui

import android.Manifest
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import org.kashmir.heatguard.R
import org.kashmir.heatguard.alerts.EscalationManager
import org.kashmir.heatguard.core.SystemClock
import org.kashmir.heatguard.core.escalation.EscalationEngine
import org.kashmir.heatguard.core.escalation.EscalationPlan
import org.kashmir.heatguard.core.model.*
import org.kashmir.heatguard.core.risk.HeatRiskEngine
import org.kashmir.heatguard.data.AppRepository
import org.kashmir.heatguard.core.ring.RingDataSource
import org.kashmir.heatguard.ring.SimulatedRingDataSource
import org.kashmir.heatguard.service.HeatMonitoringService
import org.kashmir.heatguard.weather.ManualWeatherProvider

class MainActivity : AppCompatActivity() {
    private lateinit var repository: AppRepository
    private lateinit var escalationManager: EscalationManager
    private lateinit var ringDataSource: RingDataSource
    private val engine = HeatRiskEngine()
    private val escalationEngine = EscalationEngine(SystemClock)
    private var latestSymptoms: SymptomReport? = null
    private var latestDecision: RiskDecision? = null
    private var latestPlan: EscalationPlan? = null

    private lateinit var statusText: TextView
    private lateinit var actionText: TextView
    private lateinit var detailsText: TextView
    private lateinit var confidenceText: TextView
    private lateinit var freshnessText: TextView
    private lateinit var coolingHeader: TextView
    private lateinit var coolingText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = AppRepository(this)
        escalationManager = EscalationManager(this, repository)
        ringDataSource = SimulatedRingDataSource()
        ensureDefaultBaseline()
        requestRuntimePermissions()
        startHeatMonitoringService()
        buildUi()
        ringDataSource.start {
            assessAndRender()
        }
        assessAndRender()
    }

    override fun onDestroy() {
        ringDataSource.stop()
        super.onDestroy()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 40)
        }
        scroll.addView(root)

        statusText = TextView(this).apply {
            textSize = 36f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 16)
        }
        actionText = TextView(this).apply {
            textSize = 22f
            setPadding(0, 16, 0, 24)
        }
        confidenceText = TextView(this).apply {
            textSize = 16f
            setPadding(8, 8, 8, 8)
        }
        detailsText = TextView(this).apply {
            textSize = 14f
            setPadding(0, 8, 0, 8)
        }
        freshnessText = TextView(this).apply {
            textSize = 13f
            setPadding(0, 4, 0, 16)
        }
        coolingHeader = TextView(this).apply {
            text = getString(R.string.cooling_instructions_header)
            textSize = 16f
            setPadding(0, 16, 0, 4)
            visibility = View.GONE
        }
        coolingText = TextView(this).apply {
            textSize = 14f
            setPadding(16, 0, 0, 16)
            visibility = View.GONE
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 24f
            gravity = Gravity.CENTER
        })
        root.addView(statusText)
        root.addView(actionText)
        root.addView(confidenceText)
        root.addView(detailsText)
        root.addView(freshnessText)
        root.addView(coolingHeader)
        root.addView(coolingText)

        root.addView(button(R.string.i_am_safe) {
            latestSymptoms = SymptomReport(
                timestampMillis = System.currentTimeMillis(),
                responseState = ResponseState.RESPONDED
            )
            assessAndRender()
        })
        root.addView(button(R.string.report_symptoms) { showSymptomDialog() })
        root.addView(button(R.string.manual_emergency) {
            latestSymptoms = SymptomReport(
                timestampMillis = System.currentTimeMillis(),
                responseState = ResponseState.MANUAL_EMERGENCY
            )
            assessAndRender()
            latestDecision?.let { escalationManager.showRiskNotification(it) }
        })
        root.addView(button(R.string.call_contact) { escalationManager.dialEmergencyContact() })
        root.addView(button(R.string.share_alert) { latestDecision?.let { escalationManager.shareEmergencyMessage(it) } })
        root.addView(button(R.string.edit_risk_profile) { startActivity(Intent(this, RiskProfileActivity::class.java)) })
        root.addView(button(R.string.set_contact) { showContactDialog() })
        root.addView(button(R.string.set_weather) { showWeatherDialog() })
        root.addView(button(R.string.volunteer_view) { startActivity(Intent(this, VolunteerActivity::class.java)) })

        root.addView(CheckBox(this).apply {
            text = getString(R.string.simulated_mode_label)
            textSize = 16f
            isChecked = repository.getBoolean("use_simulated_ring", true)
            setOnCheckedChangeListener { _, checked ->
                repository.setBoolean("use_simulated_ring", checked)
                restartService()
            }
        })

        setContentView(scroll)
    }

    private fun button(textRes: Int, onClick: () -> Unit): Button = Button(this).apply {
        text = getString(textRes)
        textSize = 18f
        setAllCaps(false)
        setOnClickListener { onClick() }
    }

    private fun assessAndRender() {
        val now = System.currentTimeMillis()
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = ringDataSource.latestSample(),
                weatherSample = ManualWeatherProvider(repository).latestWeather(now),
                symptomReport = latestSymptoms,
                baseline = repository.getBaseline(),
                riskProfile = repository.getRiskProfile()
            )
        )
        val plan = escalationEngine.plan(decision)
        latestDecision = decision
        latestPlan = plan
        repository.saveLastDecision(decision)
        render(decision, plan)
    }

    private fun render(decision: RiskDecision, plan: EscalationPlan) {
        statusText.text = when (decision.level) {
            RiskLevel.SAFE -> getString(R.string.state_safe)
            RiskLevel.CHECK -> getString(R.string.state_check)
            RiskLevel.ACTION -> getString(R.string.state_action)
            RiskLevel.EMERGENCY -> getString(R.string.state_emergency)
        }
        actionText.text = decision.recommendedAction
        confidenceText.text = getString(R.string.confidence_label, decision.confidence.name)
        confidenceText.setBackgroundColor(
            when (decision.confidence) {
                Confidence.HIGH -> Color.parseColor("#4CAF50")
                Confidence.MEDIUM -> Color.parseColor("#FFEB3B")
                Confidence.LOW -> Color.parseColor("#F44336")
            }
        )

        val heatIndexText = decision.effectiveHeatIndexC?.let { String.format("%.1f°C", it) } ?: getString(R.string.unknown)
        detailsText.text = buildString {
            append(getString(R.string.heat_index_label))
            append(": ")
            append(heatIndexText)
            append('\n')
            append(getString(R.string.reasons_label))
            append(": ")
            append(decision.reasonCodes.joinToString())
        }

        val weatherAge = decision.weatherDataAgeMinutes
        val ringAge = decision.ringDataAgeMinutes
        val isStale = (weatherAge != null && weatherAge > 15) || (ringAge != null && ringAge > 15)
        freshnessText.text = getString(
            R.string.data_freshness_label,
            weatherAge?.toString() ?: getString(R.string.unknown),
            ringAge?.toString() ?: getString(R.string.unknown)
        )
        freshnessText.setTextColor(if (isStale) Color.parseColor("#FFA000") else Color.DKGRAY)

        val showCooling = decision.level.rank >= RiskLevel.ACTION.rank && plan.coolingInstructions.isNotEmpty()
        val coolingVisibility = if (showCooling) View.VISIBLE else View.GONE
        coolingHeader.visibility = coolingVisibility
        coolingText.visibility = coolingVisibility
        if (showCooling) {
            coolingText.text = plan.coolingInstructions.joinToString("\n") { "- $it" }
        }

        if (decision.level.rank >= RiskLevel.ACTION.rank) {
            escalationManager.showRiskNotification(decision)
        }
    }

    private fun showSymptomDialog() {
        val labels = arrayOf(
            getString(R.string.symptom_thirst),
            getString(R.string.symptom_headache),
            getString(R.string.symptom_nausea),
            getString(R.string.symptom_dizziness),
            getString(R.string.symptom_weakness),
            getString(R.string.symptom_heavy_sweating),
            getString(R.string.symptom_confusion),
            getString(R.string.symptom_slurred_speech),
            getString(R.string.symptom_fainting),
            getString(R.string.symptom_seizure),
            getString(R.string.symptom_cannot_stand)
        )
        val checked = BooleanArray(labels.size)
        AlertDialog.Builder(this)
            .setTitle(R.string.report_symptoms)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.save) { _, _ ->
                latestSymptoms = SymptomReport(
                    timestampMillis = System.currentTimeMillis(),
                    responseState = ResponseState.RESPONDED,
                    thirst = checked[0],
                    headache = checked[1],
                    nausea = checked[2],
                    dizziness = checked[3],
                    weakness = checked[4],
                    heavySweating = checked[5],
                    confusion = checked[6],
                    slurredSpeech = checked[7],
                    fainting = checked[8],
                    seizure = checked[9],
                    cannotStand = checked[10]
                )
                assessAndRender()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showContactDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 0)
        }
        val name = EditText(this).apply {
            hint = getString(R.string.contact_name)
            setText(repository.getEmergencyContactName())
        }
        val phone = EditText(this).apply {
            hint = getString(R.string.contact_phone)
            setText(repository.getEmergencyContactPhone())
        }
        layout.addView(name)
        layout.addView(phone)
        AlertDialog.Builder(this)
            .setTitle(R.string.set_contact)
            .setView(layout)
            .setPositiveButton(R.string.save) { _, _ -> repository.setEmergencyContact(name.text.toString(), phone.text.toString()) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showWeatherDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 0)
        }
        val existing = repository.getLastManualWeather(System.currentTimeMillis())
        val temp = EditText(this).apply {
            hint = getString(R.string.temperature_c)
            setText(existing.airTemperatureC?.toString() ?: "36.0")
        }
        val rh = EditText(this).apply {
            hint = getString(R.string.relative_humidity)
            setText(existing.relativeHumidityPercent?.toString() ?: "45.0")
        }
        val sun = CheckBox(this).apply {
            text = getString(R.string.direct_sun)
            isChecked = existing.directSun
        }
        val warning = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                OfficialHeatWarning.values().map { it.name }
            )
            setSelection(OfficialHeatWarning.values().indexOf(existing.officialWarning))
        }
        layout.addView(temp)
        layout.addView(rh)
        layout.addView(sun)
        layout.addView(warning)
        AlertDialog.Builder(this)
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

    private fun requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= 33) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 77)
    }

    private fun startHeatMonitoringService() {
        val intent = Intent(this, HeatMonitoringService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun restartService() {
        val intent = Intent(this, HeatMonitoringService::class.java)
        stopService(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }
}
