package org.kashmir.heatguard.ui.onboarding

import android.Manifest
import android.animation.ObjectAnimator
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import org.kashmir.heatguard.R

class NotificationPermissionFragment : Fragment(R.layout.fragment_notification_permission) {

    private val notificationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        (activity as OnboardingActivity).onNotificationPermissionDone()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvStepLabel = view.findViewById<TextView>(R.id.tvStepLabel)
        val ivBell = view.findViewById<ImageView>(R.id.ivBell)
        val btnEnable = view.findViewById<MaterialButton>(R.id.btnEnableNotifications)
        val tvNotNow = view.findViewById<TextView>(R.id.tvNotNow)
        val btnBack = view.findViewById<ImageButton>(R.id.btnBack)

        tvStepLabel.text = getString(R.string.onboarding_step_label, 5)

        startBellAnimation(ivBell)

        btnEnable.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                (activity as OnboardingActivity).onNotificationPermissionDone()
            }
        }

        tvNotNow.setOnClickListener {
            (activity as OnboardingActivity).onNotificationPermissionDone()
        }

        btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun startBellAnimation(bell: ImageView) {
        val ring = {
            ObjectAnimator.ofFloat(bell, "rotation", 0f, 15f).apply {
                duration = 150
                interpolator = DecelerateInterpolator()
            }.also { it.start() }

            bell.postDelayed({
                ObjectAnimator.ofFloat(bell, "rotation", 15f, -15f).apply {
                    duration = 150
                    interpolator = DecelerateInterpolator()
                }.also { it.start() }
            }, 150)

            bell.postDelayed({
                ObjectAnimator.ofFloat(bell, "rotation", -15f, 0f).apply {
                    duration = 150
                    interpolator = DecelerateInterpolator()
                }.also { it.start() }
            }, 300)
        }

        ring()
        bell.postDelayed({
            if (isAdded) ring()
        }, 3000)
        bell.postDelayed({
            if (isAdded) ring()
        }, 6000)
    }
}
