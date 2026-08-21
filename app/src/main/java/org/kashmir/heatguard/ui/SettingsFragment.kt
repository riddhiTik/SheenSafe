package org.kashmir.heatguard.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.Wearable
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.launch
import org.kashmir.heatguard.R
import org.kashmir.heatguard.data.AppRepository
import org.kashmir.heatguard.data.SupabaseClientProvider
import org.kashmir.heatguard.data.SupabaseRepository
import org.kashmir.heatguard.service.HeatMonitoringService
import org.kashmir.heatguard.ui.onboarding.OnboardingActivity

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private lateinit var repository: AppRepository
    private var settingsView: View? = null

    private val locationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { settingsView?.let { bindPermissions(it) } }

    private val notifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { settingsView?.let { bindPermissions(it) } }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settingsView = view
        repository = AppRepository(requireContext())

        bindProfile(view)
        bindAlertToggles(view)
        bindPermissions(view)
        bindTempToggle(view)
        bindActions(view)
        bindWatchCard(view)
    }

    override fun onResume() {
        super.onResume()
        val view = settingsView ?: return
        bindPermissions(view)
        bindWatchCard(view)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        settingsView = null
    }

    private fun bindProfile(view: View) {
        val tvProfileName = view.findViewById<TextView>(R.id.tvProfileName)
        tvProfileName.text = repository.getUserName().ifBlank { "—" }
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
        val switchHeat      = view.findViewById<SwitchMaterial>(R.id.switchHeatRisk)
        val switchHydration = view.findViewById<SwitchMaterial>(R.id.switchHydration)
        val switchEmergency = view.findViewById<SwitchMaterial>(R.id.switchEmergency)

        switchHeat.isChecked      = repository.getAlertHeatRisk()
        switchHydration.isChecked = repository.getAlertHydration()
        switchEmergency.isChecked = repository.getAlertEmergency()

        switchHeat.setOnCheckedChangeListener      { _, on -> repository.setAlertHeatRisk(on) }
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

        view.findViewById<View>(R.id.rowLocation).setOnClickListener {
            if (!locGranted) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                } else {
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", ctx.packageName, null))
                    )
                }
            }
        }

        view.findViewById<View>(R.id.rowNotifications).setOnClickListener {
            if (!notifEnabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    )
                }
            }
        }
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
            val mutedColor  = ContextCompat.getColor(requireContext(), R.color.muted)
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

        view.findViewById<TextView>(R.id.btnSignOut).setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                SupabaseClientProvider.client?.let { client ->
                    SupabaseRepository(client).signOut()
                }
                repository.deleteAllData()
                startActivity(
                    Intent(requireContext(), OnboardingActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
            }
        }
    }

    private fun bindWatchCard(view: View) {
        val useWatch6 = repository.getUseWatch6()

        Wearable.getNodeClient(requireContext()).connectedNodes
            .addOnSuccessListener { nodes ->
                if (!isAdded) return@addOnSuccessListener
                val node      = nodes.firstOrNull()
                val connected = useWatch6 && node != null

                view.findViewById<View>(R.id.viewWatchDot).setBackgroundResource(
                    if (connected) R.drawable.bg_status_dot_green else R.drawable.bg_status_dot_grey
                )
                view.findViewById<TextView>(R.id.tvWatchStatus).apply {
                    text = if (connected) "Connected" else "Not Connected"
                    setTextColor(ContextCompat.getColor(requireContext(),
                        if (connected) R.color.primary_dark else R.color.muted))
                }
                view.findViewById<TextView>(R.id.tvWatchDeviceName).text =
                    node?.displayName ?: if (useWatch6) "Searching…" else "No device paired"

                val battery = repository.getLastBatteryPercent()
                view.findViewById<TextView>(R.id.tvWatchBattery).apply {
                    visibility = if (battery != null && connected) View.VISIBLE else View.GONE
                    text = getString(R.string.settings_watch_battery, battery ?: 0)
                }

                view.findViewById<MaterialButton>(R.id.btnPairWatch).visibility =
                    if (!connected) View.VISIBLE else View.GONE
                view.findViewById<MaterialButton>(R.id.btnUnpairWatch).visibility =
                    if (connected) View.VISIBLE else View.GONE
                view.findViewById<MaterialButton>(R.id.btnChangeDevice).visibility =
                    if (connected) View.VISIBLE else View.GONE
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                view.findViewById<TextView>(R.id.tvWatchDeviceName).text = "Wearable API unavailable"
            }

        view.findViewById<MaterialButton>(R.id.btnPairWatch).setOnClickListener {
            repository.setUseWatch6(true)
            restartMonitoringService()
            val galIntent = requireContext().packageManager
                .getLaunchIntentForPackage("com.samsung.android.app.watchmanager")
            startActivity(galIntent ?: Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            bindWatchCard(view)
        }

        view.findViewById<MaterialButton>(R.id.btnUnpairWatch).setOnClickListener {
            repository.setUseWatch6(false)
            restartMonitoringService()
            bindWatchCard(view)
        }

        view.findViewById<MaterialButton>(R.id.btnChangeDevice).setOnClickListener {
            val galIntent = requireContext().packageManager
                .getLaunchIntentForPackage("com.samsung.android.app.watchmanager")
            startActivity(galIntent ?: Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
    }

    private fun restartMonitoringService() {
        val ctx = requireContext()
        Log.i("SettingsFragment", "Restarting HeatMonitoringService after watch toggle")
        ctx.stopService(Intent(ctx, HeatMonitoringService::class.java))
        val intent = Intent(ctx, HeatMonitoringService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent)
        else ctx.startService(intent)
    }
}
