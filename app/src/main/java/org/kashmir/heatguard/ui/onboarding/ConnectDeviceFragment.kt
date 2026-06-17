package org.kashmir.heatguard.ui.onboarding

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import org.kashmir.heatguard.R
import org.kashmir.heatguard.data.AppRepository

class ConnectDeviceFragment : Fragment(R.layout.fragment_connect_device) {

    private val animators = mutableListOf<ObjectAnimator>()
    private val scanHandler = Handler(Looper.getMainLooper())
    private val discoveredDevices = mutableListOf<BleDevice>()
    private var deviceAdapter: DeviceAdapter? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var scanning = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) startBleScan()
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
        val rvDevices   = view.findViewById<RecyclerView>(R.id.rvDevices)

        tvStepLabel.text = getString(R.string.onboarding_step_label, 3)

        startPulseAnimation(outerRing, 0L)
        startPulseAnimation(middleRing, 500L)
        startOrbitAnimation(scanDot)

        val bluetoothManager =
            requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        deviceAdapter = DeviceAdapter { device ->
            stopBleScan()
            val repo = AppRepository(requireContext())
            repo.setRingDeviceMac(device.address)
            repo.setUseWatch6(false)   // clear Wear OS flag if previously set
            (activity as OnboardingActivity).onDeviceConnected()
        }
        rvDevices.layoutManager = LinearLayoutManager(requireContext())
        rvDevices.adapter = deviceAdapter

        btnConnect.text = getString(R.string.onboarding_scan_for_device)
        btnConnect.setOnClickListener {
            if (scanning) stopBleScan() else requestPermissionsAndScan()
        }

        btnSkip.setOnClickListener {
            stopBleScan()
            (activity as OnboardingActivity).onDeviceConnected()
        }

        btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun requestPermissionsAndScan() {
        val needed = buildList {
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN))   add(Manifest.permission.BLUETOOTH_SCAN)
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (needed.isEmpty()) startBleScan() else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun startBleScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        if (scanning) return

        discoveredDevices.clear()
        deviceAdapter?.notifyDataSetChanged()
        scanning = true
        updateScanUi(true)

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(null, settings, scanCallback)
        scanHandler.postDelayed({ stopBleScan() }, 10_000L)
    }

    private fun stopBleScan() {
        if (!scanning) return
        scanning = false
        scanHandler.removeCallbacksAndMessages(null)
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        updateScanUi(false)
    }

    private fun updateScanUi(isScanning: Boolean) {
        val v = view ?: return
        val btnConnect   = v.findViewById<MaterialButton>(R.id.btnConnect)
        val tvScanStatus = v.findViewById<TextView>(R.id.tvScanStatus)
        val rvDevices    = v.findViewById<RecyclerView>(R.id.rvDevices)

        btnConnect.text = if (isScanning) getString(R.string.onboarding_stop_scan)
                          else getString(R.string.onboarding_scan_for_device)

        if (isScanning || discoveredDevices.isNotEmpty()) {
            tvScanStatus.visibility = View.VISIBLE
            rvDevices.visibility    = View.VISIBLE
            tvScanStatus.text = if (isScanning) getString(R.string.onboarding_scanning)
                                else getString(R.string.onboarding_scan_complete, discoveredDevices.size)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = BleDevice(
                name    = result.device.name ?: getString(R.string.onboarding_unknown_device),
                address = result.device.address
            )
            if (discoveredDevices.none { it.address == device.address }) {
                discoveredDevices.add(device)
                deviceAdapter?.notifyItemInserted(discoveredDevices.size - 1)
                view?.findViewById<RecyclerView>(R.id.rvDevices)?.visibility = View.VISIBLE
                updateScanUi(scanning)
            }
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED

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
        stopBleScan()
        super.onDestroyView()
        animators.forEach { it.cancel() }
        animators.clear()
    }

    // ------------------------------------------------------------------ inner types

    data class BleDevice(val name: String, val address: String)

    private inner class DeviceAdapter(
        private val onSelect: (BleDevice) -> Unit
    ) : RecyclerView.Adapter<DeviceAdapter.VH>() {

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvName: TextView    = itemView.findViewById(R.id.tvDeviceName)
            val tvAddress: TextView = itemView.findViewById(R.id.tvDeviceAddress)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_ble_device, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val device = discoveredDevices[position]
            holder.tvName.text    = device.name
            holder.tvAddress.text = device.address
            holder.itemView.setOnClickListener { onSelect(device) }
        }

        override fun getItemCount() = discoveredDevices.size
    }
}
