package org.kashmir.heatguard.ui.onboarding

import android.Manifest
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import org.kashmir.heatguard.R

class LocationPermissionFragment : Fragment(R.layout.fragment_location_permission) {

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        (activity as OnboardingActivity).onLocationPermissionDone()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvStepLabel = view.findViewById<TextView>(R.id.tvStepLabel)
        val illustrationBg = view.findViewById<View>(R.id.illustrationBg)
        val btnEnable = view.findViewById<MaterialButton>(R.id.btnEnableLocation)
        val tvNotNow = view.findViewById<TextView>(R.id.tvNotNow)
        val btnBack = view.findViewById<ImageButton>(R.id.btnBack)

        tvStepLabel.text = getString(R.string.onboarding_step_label, 4)

        ObjectAnimator.ofFloat(illustrationBg, "scaleX", 1f, 1.08f, 1f).apply {
            duration = 3000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
        ObjectAnimator.ofFloat(illustrationBg, "scaleY", 1f, 1.08f, 1f).apply {
            duration = 3000
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        btnEnable.setOnClickListener {
            locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        tvNotNow.setOnClickListener {
            (activity as OnboardingActivity).onLocationPermissionDone()
        }

        btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }
}
