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
    private var refreshThresholdLabel: (() -> Unit)? = null

    private companion object {
        const val WATCH_FRESH_SECONDS = 60L
        const val WATCH_REFRESH_MS = 3_000L
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = AppRepository(requireContext())

        bindProfile(view)
        bindAlertToggles(view)
        bindPermissions(view)
        bindTempToggle(view)
        bindActions(view)
        bindWatch6Toggle(view)
        bindLanguage(view)
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

        btnC.setOnClickListener { repository.setTempUnit("C"); updateToggle("C"); refreshThresholdLabel?.invoke() }
        btnF.setOnClickListener { repository.setTempUnit("F"); updateToggle("F"); refreshThresholdLabel?.invoke() }
    }

    private fun bindActions(view: View) {
        view.findViewById<TextView>(R.id.btnEditProfile).setOnClickListener {
            startActivity(Intent(requireContext(), RiskProfileActivity::class.java))
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

        view.findViewById<LinearLayout>(R.id.btnDeleteAccount).setOnClickListener {
            confirmAndDeleteAccount()
        }
    }

    private fun confirmAndDeleteAccount() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_account_dialog_title)
            .setMessage(R.string.delete_account_dialog_message)
            .setPositiveButton(R.string.delete_account_confirm) { _, _ -> deleteAccount() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteAccount() {
        val ctx = requireContext().applicationContext
        val deviceId = repository.getSupabaseUserId().ifBlank { repository.getOrCreateDeviceId() }

        viewLifecycleOwner.lifecycleScope.launch {
            var failed = false
            SupabaseClientProvider.client?.let { client ->
                val repo = SupabaseRepository(client)
                repo.deleteAllUserData(deviceId)
                failed = repo.deleteAuthAccount().isFailure
                repo.signOut()
            }

            // Local data is wiped regardless of remote failures — the person asked for their
            // data gone from this device, and they can retry the remote deletion via support.
            repository.deleteAllData()

            if (failed && isAdded) {
                android.widget.Toast.makeText(ctx, R.string.delete_account_error, android.widget.Toast.LENGTH_LONG).show()
            }

            if (isAdded) {
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
        val source = (requireActivity().application as HeatGuardApplication).activeRingDataSource
        val sample = source?.latestSample()

        val ageSeconds = sample?.let { (System.currentTimeMillis() - it.timestampMillis) / 1000L }
        // Connected = a fresh sample that isn't flagged disconnected. Battery is optional:
        // the Galaxy Watch6 stream doesn't report it, so don't require it.
        val isConnected = sample != null && sample.connected && ageSeconds != null && ageSeconds < WATCH_FRESH_SECONDS

        if (!isConnected) {
            tvBattery.text = getString(R.string.settings_watch_not_connected)
            setFillFraction(fill, 0f)
            return
        }

        val agoLabel = when {
            ageSeconds!! < 60 -> "${ageSeconds}s"
            else -> "${ageSeconds / 60}m"
        }
        val batteryPercent = sample!!.batteryPercent
        tvBattery.text = if (batteryPercent != null)
            getString(R.string.settings_watch_sync_status, batteryPercent, agoLabel)
        else
            getString(R.string.settings_watch_connected_status, agoLabel)
        // Bar shows battery when known; otherwise full to indicate an active link.
        setFillFraction(fill, (batteryPercent ?: 100) / 100f)
    }

    private fun setFillFraction(fill: View, fraction: Float) {
        val track = fill.parent as View
        track.post {
            val trackWidth = track.width
            if (trackWidth > 0) {
                fill.layoutParams = fill.layoutParams.apply { width = (trackWidth * fraction.coerceIn(0f, 1f)).toInt() }
                fill.requestLayout()
            }
        }
    }

    private val watchStatusTicker = object : Runnable {
        override fun run() {
            view?.let { bindWatchStatus(it) }
            view?.postDelayed(this, WATCH_REFRESH_MS)
        }
    }

    override fun onStart() {
        super.onStart()
        view?.post(watchStatusTicker)
    }

    override fun onStop() {
        view?.removeCallbacks(watchStatusTicker)
        super.onStop()
    }

    private fun bindThresholdSlider(view: View) {
        val touchArea = view.findViewById<View>(R.id.thresholdSliderTouchArea)
        val thumb = view.findViewById<View>(R.id.thresholdThumb)
        val tvValue = view.findViewById<TextView>(R.id.tvThresholdValue)
        val minF = 70
        val maxF = 130

        fun applyValue(value: Int) {
            // value is always stored/dragged in °F; only the displayed label respects the
            // Temperature Units toggle (see bindTempToggle, which calls refreshThresholdLabel).
            val unit = repository.getTempUnit()
            val displayValue = if (unit == "C") Math.round((value - 32) * 5.0 / 9.0).toInt() else value
            val unitLabel = if (unit == "C") getString(R.string.unit_celsius) else "°F"
            tvValue.text = "$displayValue$unitLabel"
            val trackWidth = touchArea.width
            val thumbWidth = thumb.width.takeIf { it > 0 } ?: thumb.layoutParams.width
            if (trackWidth > 0) {
                val fraction = ((value - minF).toFloat() / (maxF - minF)).coerceIn(0f, 1f)
                thumb.translationX = fraction * (trackWidth - thumbWidth)
            }
        }

        touchArea.post { applyValue(repository.getCustomAlertThresholdF()) }
        refreshThresholdLabel = { applyValue(repository.getCustomAlertThresholdF()) }

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

    private fun bindLanguage(view: View) {
        val languages = listOf("en" to "English", "hi" to "हिन्दी", "ks" to "کٲشُر", "ur" to "اردو")
        val tvValue = view.findViewById<TextView>(R.id.tvLanguageValue)
        fun refresh() {
            tvValue.text = languages.firstOrNull { it.first == repository.getLanguage() }?.second ?: "English"
        }
        refresh()
        view.findViewById<View>(R.id.rowLanguage).setOnClickListener {
            val current = languages.indexOfFirst { it.first == repository.getLanguage() }.coerceAtLeast(0)
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_language_dialog_title)
                .setSingleChoiceItems(languages.map { it.second }.toTypedArray(), current) { dialog, which ->
                    dialog.dismiss()
                    val code = languages[which].first
                    if (code != repository.getLanguage()) {
                        repository.setLanguage(code)
                        // Recreates the activity with the new locale; persisted by AppLocalesMetadataHolderService.
                        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                            androidx.core.os.LocaleListCompat.forLanguageTags(code)
                        )
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun bindWatch6Toggle(view: View) {
        val tvTitle = view.findViewById<TextView>(R.id.tvWatchTitle)

        fun refreshTitle() {
            val name = repository.getSelectedWatchName()
            tvTitle.text = if (name != null) "$name  ›" else getString(R.string.settings_watch_title)
        }

        // Stop first so the old watch gets its "stop" command (the service targets the
        // currently stored watch in onDestroy), then start against the newly stored one.
        fun restartService(afterStop: () -> Unit = {}) {
            val ctx = requireContext()
            ctx.stopService(Intent(ctx, HeatMonitoringService::class.java))
            afterStop()
            val intent = Intent(ctx, HeatMonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent) else ctx.startService(intent)
        }

        fun chooseWatch(onPicked: (() -> Unit)? = null, onFailed: (() -> Unit)? = null) {
            WatchPicker.pick(requireContext(), repository) { result ->
                if (!isAdded) return@pick
                when (result) {
                    is WatchPicker.Result.Picked -> {
                        refreshTitle()
                        onPicked?.invoke()
                    }
                    WatchPicker.Result.NoneFound -> {
                        Toast.makeText(requireContext(), R.string.onboarding_watch_not_found, Toast.LENGTH_LONG).show()
                        onFailed?.invoke()
                    }
                    is WatchPicker.Result.Error -> {
                        Toast.makeText(requireContext(), getString(if (result.unavailable) R.string.onboarding_watch_unavailable else R.string.onboarding_watch_error), Toast.LENGTH_LONG).show()
                        onFailed?.invoke()
                    }
                    WatchPicker.Result.Cancelled -> onFailed?.invoke()
                }
            }
        }

        refreshTitle()
        // Tap the watch card to (re)select the paired watch.
        (tvTitle.parent as View).setOnClickListener {
            val ctx = requireContext()
            ctx.stopService(Intent(ctx, HeatMonitoringService::class.java))   // stops the old watch
            chooseWatch(
                onPicked = { repository.setUseWatch6(true); restartService() },
                onFailed = { restartService() }
            )
        }
    }
}
