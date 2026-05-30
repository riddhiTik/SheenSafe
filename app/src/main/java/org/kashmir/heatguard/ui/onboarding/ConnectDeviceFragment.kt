package org.kashmir.heatguard.ui.onboarding

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import org.kashmir.heatguard.R

class ConnectDeviceFragment : Fragment(R.layout.fragment_connect_device) {

    private val animators = mutableListOf<ObjectAnimator>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvStepLabel = view.findViewById<TextView>(R.id.tvStepLabel)
        val outerRing = view.findViewById<View>(R.id.outerRing)
        val middleRing = view.findViewById<View>(R.id.middleRing)
        val scanDot = view.findViewById<View>(R.id.scanDot)
        val btnConnect = view.findViewById<MaterialButton>(R.id.btnConnect)
        val btnBack = view.findViewById<ImageButton>(R.id.btnBack)

        tvStepLabel.text = getString(R.string.onboarding_step_label, 3)

        startPulseAnimation(outerRing, 0L)
        startPulseAnimation(middleRing, 500L)
        startOrbitAnimation(scanDot)

        btnConnect.setOnClickListener {
            (activity as OnboardingActivity).onDeviceConnected()
        }

        btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun startPulseAnimation(view: View, startDelay: Long) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.15f, 1f).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
            this.startDelay = startDelay
            interpolator = LinearInterpolator()
        }
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.15f, 1f).apply {
            duration = 2000
            repeatCount = ObjectAnimator.INFINITE
            this.startDelay = startDelay
            interpolator = LinearInterpolator()
        }
        animators.add(scaleX)
        animators.add(scaleY)
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            start()
        }
    }

    private fun startOrbitAnimation(dot: View) {
        val rotation = ObjectAnimator.ofFloat(dot.parent as View, "rotation", 0f, 360f).apply {
            duration = 6000
            repeatCount = ObjectAnimator.INFINITE
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
