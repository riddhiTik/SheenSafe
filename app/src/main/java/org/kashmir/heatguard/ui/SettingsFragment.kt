package org.kashmir.heatguard.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial
import org.kashmir.heatguard.R
import org.kashmir.heatguard.data.AppRepository

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
    }

    private fun bindProfile(view: View) {
        view.findViewById<TextView>(R.id.tvProfileName).text =
            repository.getUserName().ifBlank { "—" }

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
        }
    }
}
