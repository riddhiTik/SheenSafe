package org.kashmir.heatguard.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import org.kashmir.heatguard.HeatGuardApplication
import org.kashmir.heatguard.R
import org.kashmir.heatguard.core.model.OfficialHeatWarning
import org.kashmir.heatguard.data.AppRepository
import org.kashmir.heatguard.data.SupabaseClientProvider
import org.kashmir.heatguard.data.SupabaseRepository
import org.kashmir.heatguard.service.HeatMonitoringService
import org.kashmir.heatguard.ui.onboarding.OnboardingActivity

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private lateinit var repository: AppRepository

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = AppRepository(requireContext())

        bindProfile(view)
        bindAlertToggles(view)
        bindPermissions(view)
        bindTempToggle(view)
        bindActions(view)
        bindWatch6Toggle(view)
        bindWatchStatus(view)
        bindThresholdSlider(view)
        bindLocalWeather(view)
    }

    override fun onResume() {
        super.onResume()
        if (view != null) bindWatchStatus(requireView())
    }

    private fun bindProfile(view: View) {
        val tvProfileName = view.findViewById<TextView>(R.id.tvProfileName)
        tvProfileName.text = repository.getUserName().ifBlank { "—" }
        // Long-press launches the admin dashboard (developer/clinician access)
        tvProfileName.setOnLongClickListener {
            startActivity(Intent(requireContext(), AdminLoginActivity::class.java))
            true
        }

        view.findViewById<TextView>(R.id.tvProfileAge).text =
            repository.getUserAge().takeIf { it > 0 }?.toString() ?: "—"

        view.findViewById<TextView>(R.id.tvProfileGender).text = when (repository.getUserGender()) {
            "MALE"   -> "Male"
            "FEMALE" -> "Female"
            "OTHER"  -> "Other"
            else     -> "—"
        }
    }

    private fun bindAlertToggles(view: View) {
        val switchHeat = view.findViewById<SwitchMaterial>(R.id.switchHeatRisk)
        val switchHydration = view.findViewById<SwitchMaterial>(R.id.switchHydration)
        val switchEmergency = view.findViewById<SwitchMaterial>(R.id.switchEmergency)

        switchHeat.isChecked = repository.getAlertHeatRisk()
        switchHydration.isChecked = repository.getAlertHydration()
        switchEmergency.isChecked = repository.getAlertEmergency()

        switchHeat.setOnCheckedChangeListener { _, on -> repository.setAlertHeatRisk(on) }
        switchHydration.setOnCheckedChangeListener { _, on -> repository.setAlertHydration(on) }
        switchEmergency.setOnCheckedChangeListener { _, on -> repository.setAlertEmergency(on) }
    }

    private fun bindPermissions(view: View) {
        val ctx = requireContext()
        val locGranted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val notifEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        else NotificationManagerCompat.from(ctx).areNotificationsEnabled()

        applyChip(view.findViewById(R.id.tvLocationStatus), locGranted)
        applyChip(view.findViewById(R.id.tvNotifStatus), notifEnabled)
    }

    private fun applyChip(tv: TextView, enabled: Boolean) {
        tv.text = if (enabled) getString(R.string.settings_permission_enabled)
        else getString(R.string.settings_permission_disabled)
        tv.setTextColor(
            if (enabled) ContextCompat.getColor(requireContext(), R.color.primary_dark)
            else ContextCompat.getColor(requireContext(), R.color.error_color)
        )
        tv.background = ContextCompat.getDrawable(
            requireContext(),
            if (enabled) R.drawable.bg_permission_chip_enabled else R.drawable.bg_permission_chip_disabled
        )
    }

    private fun bindTempToggle(view: View) {
        val btnC = view.findViewById<TextView>(R.id.btnTempC)
        val btnF = view.findViewById<TextView>(R.id.btnTempF)

        fun updateToggle(unit: String) {
            val activeColor = ContextCompat.getColor(requireContext(), R.color.background_dark)
            val mutedColor = ContextCompat.getColor(requireContext(), R.color.muted)
            btnC.setTextColor(if (unit == "C") activeColor else mutedColor)
            btnF.setTextColor(if (unit == "F") activeColor else mutedColor)
            btnC.setBackgroundResource(if (unit == "C") R.drawable.bg_temp_toggle_active else 0)
            btnF.setBackgroundResource(if (unit == "F") R.drawable.bg_temp_toggle_active else 0)
        }

        updateToggle(repository.getTempUnit())

        btnC.setOnClickListener { repository.setTempUnit("C"); updateToggle("C") }
        btnF.setOnClickListener { repository.setTempUnit("F"); updateToggle("F") }
    }

    private fun bindActions(view: View) {
        view.findViewById<TextView>(R.id.btnEditProfile).setOnClickListener {
            startActivity(Intent(requireContext(), RiskProfileActivity::class.java))
        }

        view.findViewById<TextView>(R.id.btnReconnectWatch).setOnClickListener {
            repository.setDeviceConnected(true)
            Toast.makeText(requireContext(), getString(R.string.settings_reconnect_toast), Toast.LENGTH_SHORT).show()
            bindWatchStatus(view)
        }

        view.findViewById<TextView>(R.id.btnSignOut).setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                SupabaseClientProvider.client?.let { client ->
                    SupabaseRepository(client).signOut()
                }
                // Wipe all local user data so the next person to log in on this device
                // starts completely fresh and does not see the previous user's profile.
                repository.deleteAllData()
                startActivity(
                    Intent(requireContext(), OnboardingActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
            }
        }
    }

    private fun bindWatchStatus(view: View) {
        val tvBattery = view.findViewById<TextView>(R.id.tvWatchBattery)
        val fill = view.findViewById<View>(R.id.vBatteryFill)
        val sample = (requireActivity().application as HeatGuardApplication).activeRingDataSource?.latestSample()
        val batteryPercent = sample?.batteryPercent

        if (sample == null || batteryPercent == null) {
            tvBattery.text = getString(R.string.settings_watch_not_connected)
            fill.post { fill.layoutParams = fill.layoutParams.apply { width = 0 }; fill.requestLayout() }
            return
        }

        val ageSeconds = (System.currentTimeMillis() - sample.timestampMillis) / 1000L
        val agoLabel = when {
            ageSeconds < 60 -> "${ageSeconds}s"
            ageSeconds < 3600 -> "${ageSeconds / 60}m"
            else -> "${ageSeconds / 3600}h"
        }
        tvBattery.text = getString(R.string.settings_watch_sync_status, batteryPercent, agoLabel)

        val track = fill.parent as View
        track.post {
            val trackWidth = track.width
            if (trackWidth > 0) {
                val fraction = (batteryPercent / 100f).coerceIn(0f, 1f)
                fill.layoutParams = fill.layoutParams.apply { width = (trackWidth * fraction).toInt() }
                fill.requestLayout()
            }
        }
    }

    private fun bindThresholdSlider(view: View) {
        val touchArea = view.findViewById<View>(R.id.thresholdSliderTouchArea)
        val thumb = view.findViewById<View>(R.id.thresholdThumb)
        val tvValue = view.findViewById<TextView>(R.id.tvThresholdValue)
        val minF = 70
        val maxF = 130

        fun applyValue(value: Int) {
            tvValue.text = getString(R.string.settings_threshold_value, value)
            val trackWidth = touchArea.width
            val thumbWidth = thumb.width.takeIf { it > 0 } ?: thumb.layoutParams.width
            if (trackWidth > 0) {
                val fraction = ((value - minF).toFloat() / (maxF - minF)).coerceIn(0f, 1f)
                thumb.translationX = fraction * (trackWidth - thumbWidth)
            }
        }

        touchArea.post { applyValue(repository.getCustomAlertThresholdF()) }

        touchArea.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val thumbWidth = thumb.width.takeIf { it > 0 } ?: thumb.layoutParams.width
                    val usableWidth = (v.width - thumbWidth).coerceAtLeast(1)
                    val fraction = ((event.x - thumbWidth / 2f) / usableWidth).coerceIn(0f, 1f)
                    val value = (minF + fraction * (maxF - minF)).toInt()
                    applyValue(value)
                    repository.setCustomAlertThresholdF(value)
                    true
                }
                else -> false
            }
        }
    }

    private fun bindLocalWeather(view: View) {
        view.findViewById<TextView>(R.id.btnSetLocalWeather).setOnClickListener {
            showWeatherDialog()
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
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun bindWatch6Toggle(view: View) {
        val switch = view.findViewById<SwitchMaterial>(R.id.switchWatch6)
        switch.isChecked = repository.getUseWatch6()
        switch.setOnCheckedChangeListener { _, on ->
            Log.i("SettingsFragment", "Watch6 toggle changed to $on — restarting HeatMonitoringService")
            repository.setUseWatch6(on)
            // HeatMonitoringService reads getUseWatch6() only in onCreate(), so we must
            // restart it for the new ring source selection to take effect.
            val ctx = requireContext()
            ctx.stopService(Intent(ctx, HeatMonitoringService::class.java))
            val intent = Intent(ctx, HeatMonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        }
    }
}
