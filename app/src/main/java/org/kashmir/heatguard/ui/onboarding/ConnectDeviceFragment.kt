package org.kashmir.heatguard.ui.onboarding

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.wearable.Wearable
import com.google.android.material.button.MaterialButton
import org.kashmir.heatguard.R
import org.kashmir.heatguard.data.AppRepository
import org.kashmir.heatguard.service.HeatMonitoringService

class ConnectDeviceFragment : Fragment(R.layout.fragment_connect_device) {

    private val animators = mutableListOf<ObjectAnimator>()
    private var checking = false

    // BLE scan state — one scan at a time, auto-stopped after SCAN_TIMEOUT_MS.
    private val mainHandler = Handler(Looper.getMainLooper())
    private var scanCallback: ScanCallback? = null
    private var scanDialog: AlertDialog? = null
    private val foundDevices = linkedMapOf<String, String>() // MAC -> display name

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.all { it }) showScanDialog()
            else setUiState(
                buttonText = getString(R.string.onboarding_scan_for_device),
                statusText = getString(R.string.onboarding_bt_permission_denied)
            )
        }

    private val enableBtLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (bluetoothAdapterOrNull()?.isEnabled == true) showScanDialog()
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvStepLabel = view.findViewById<TextView>(R.id.tvStepLabel)
        val outerRing   = view.findViewById<View>(R.id.outerRing)
        val middleRing  = view.findViewById<View>(R.id.middleRing)
        val scanDot     = view.findViewById<View>(R.id.scanDot)
        val btnConnect  = view.findViewById<MaterialButton>(R.id.btnConnect)
        val btnSkip     = view.findViewById<MaterialButton>(R.id.btnSkip)
        val btnBack     = view.findViewById<ImageButton>(R.id.btnBack)

        tvStepLabel.text = getString(R.string.onboarding_step_label, 3)

        startPulseAnimation(outerRing, 0L)
        startPulseAnimation(middleRing, 500L)
        startOrbitAnimation(scanDot)

        btnConnect.setOnClickListener { beginBleConnect() }

        btnSkip.setOnClickListener {
            (activity as OnboardingActivity).onDeviceConnected()
        }

        btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    // ------------------------------------------------------------------ BLE scan / pick

    private fun requiredBlePermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)  // required for BLE scan on API <= 30

    private fun bluetoothAdapterOrNull() =
        (requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private fun beginBleConnect() {
        val ctx = requireContext()
        val missing = requiredBlePermissions().filter {
            ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
            return
        }
        val adapter = bluetoothAdapterOrNull()
        if (adapter == null) {
            setUiState(getString(R.string.onboarding_scan_for_device), getString(R.string.onboarding_watch_error))
            return
        }
        if (!adapter.isEnabled) {
            // ACTION_REQUEST_ENABLE needs no extra permission and shows the system "turn on Bluetooth" prompt.
            enableBtLauncher.launch(Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        showScanDialog()
    }

    @SuppressLint("MissingPermission") // permissions verified in beginBleConnect()
    private fun showScanDialog() {
        val ctx = requireContext()
        val scanner = bluetoothAdapterOrNull()?.bluetoothLeScanner ?: return
        foundDevices.clear()

        val macs = mutableListOf<String>()
        val adapter = ArrayAdapter<String>(ctx, android.R.layout.simple_list_item_1, mutableListOf())
        val list = ListView(ctx).apply { this.adapter = adapter }

        val dialog = AlertDialog.Builder(ctx)
            .setTitle(R.string.onboarding_scanning)
            .setView(list)
            .setNeutralButton(R.string.onboarding_use_wear_os) { _, _ -> checkWatchConnection() }
            .setNegativeButton(R.string.cancel, null)
            .create()
        scanDialog = dialog

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val mac = result.device.address ?: return
                // Unnamed devices are mostly beacons/trackers — skip them to keep the list readable.
                val name = result.scanRecord?.deviceName ?: result.device.name ?: return
                if (foundDevices.put(mac, name) == null) {
                    macs.add(mac)
                    adapter.add("$name\n$mac")
                    dialog.setTitle(getString(R.string.onboarding_scan_complete, macs.size))
                }
            }

            override fun onScanFailed(errorCode: Int) {
                dialog.setTitle(R.string.onboarding_watch_error)
            }
        }
        scanCallback = callback

        list.setOnItemClickListener { _, _, position, _ ->
            val mac = macs[position]
            stopScan()
            dialog.dismiss()
            onDevicePicked(mac)
        }
        dialog.setOnDismissListener { stopScan() }
        dialog.show()

        // Low-latency mode: the user is staring at the dialog waiting for their watch to appear.
        scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
        mainHandler.postDelayed({ stopScan() }, SCAN_TIMEOUT_MS)
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        mainHandler.removeCallbacksAndMessages(null)
        val cb = scanCallback ?: return
        scanCallback = null
        try {
            bluetoothAdapterOrNull()?.bluetoothLeScanner?.stopScan(cb)
        } catch (_: IllegalStateException) {
            // Bluetooth was switched off mid-scan; nothing to stop.
        }
    }

    /** Saves the chosen watch and restarts the monitoring service so it connects over BLE GATT. */
    private fun onDevicePicked(mac: String) {
        val ctx = requireContext().applicationContext
        AppRepository(ctx).apply {
            setRingDeviceMac(mac)
            setUseWatch6(false) // BLE source is chosen only when Watch6/Wear OS mode is off
        }
        ctx.stopService(Intent(ctx, HeatMonitoringService::class.java))
        ContextCompat.startForegroundService(ctx, Intent(ctx, HeatMonitoringService::class.java))
        (activity as OnboardingActivity).onDeviceConnected()
    }

    /**
     * Watch pairing itself happens outside the app, via the watch's own companion app —
     * this only confirms a Wear OS node is reachable over the Data Layer before turning
     * useWatch6 on, so HeatMonitoringService knows to read from wearDataSource instead of a BLE ring.
     */
    private fun checkWatchConnection() {
        if (checking) return
        checking = true
        setUiState(buttonText = getString(R.string.onboarding_stop_scan), statusText = null)

        Wearable.getNodeClient(requireContext()).connectedNodes
            .addOnSuccessListener { nodes ->
                checking = false
                if (nodes.isNotEmpty()) {
                    AppRepository(requireContext()).setUseWatch6(true)
                    (activity as OnboardingActivity).onDeviceConnected()
                } else {
                    setUiState(
                        buttonText = getString(R.string.onboarding_scan_for_device),
                        statusText = getString(R.string.onboarding_watch_not_found)
                    )
                }
            }
            .addOnFailureListener {
                checking = false
                setUiState(
                    buttonText = getString(R.string.onboarding_scan_for_device),
                    statusText = getString(R.string.onboarding_watch_error)
                )
            }
    }

    private fun setUiState(buttonText: String, statusText: String?) {
        val v = view ?: return
        v.findViewById<MaterialButton>(R.id.btnConnect).text = buttonText
        val tvScanStatus = v.findViewById<TextView>(R.id.tvScanStatus)
        tvScanStatus.visibility = if (statusText != null) View.VISIBLE else View.GONE
        tvScanStatus.text = statusText ?: ""
    }

    // ------------------------------------------------------------------ animations

    private fun startPulseAnimation(view: View, startDelay: Long) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.15f, 1f).apply {
            duration = 2000; repeatCount = ObjectAnimator.INFINITE
            this.startDelay = startDelay; interpolator = LinearInterpolator()
        }
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.15f, 1f).apply {
            duration = 2000; repeatCount = ObjectAnimator.INFINITE
            this.startDelay = startDelay; interpolator = LinearInterpolator()
        }
        animators.add(scaleX); animators.add(scaleY)
        AnimatorSet().apply { playTogether(scaleX, scaleY); start() }
    }

    private fun startOrbitAnimation(dot: View) {
        val rotation = ObjectAnimator.ofFloat(dot.parent as View, "rotation", 0f, 360f).apply {
            duration = 6000; repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
        }
        animators.add(rotation)
        rotation.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopScan()
        scanDialog?.dismiss()
        scanDialog = null
        animators.forEach { it.cancel() }
        animators.clear()
    }

    private companion object {
        const val SCAN_TIMEOUT_MS = 20_000L
    }
}
