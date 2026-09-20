package org.kashmir.heatguard.ui.onboarding

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.gms.wearable.Wearable
import com.google.android.material.button.MaterialButton
import org.kashmir.heatguard.R
import org.kashmir.heatguard.data.AppRepository

class ConnectDeviceFragment : Fragment(R.layout.fragment_connect_device) {

    private val animators = mutableListOf<ObjectAnimator>()
    private var checking = false

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

        btnConnect.setOnClickListener { checkWatchConnection() }

        btnSkip.setOnClickListener {
            (activity as OnboardingActivity).onDeviceConnected()
        }

        btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
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
        animators.forEach { it.cancel() }
        animators.clear()
    }
}
