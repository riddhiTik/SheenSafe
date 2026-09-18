package org.kashmir.heatguard.ui

import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import org.kashmir.heatguard.R
import org.kashmir.heatguard.alerts.EscalationManager
import org.kashmir.heatguard.data.AppRepository

class EmergencyAlertActivity : AppCompatActivity() {

    private lateinit var repository: AppRepository
    private lateinit var escalationManager: EscalationManager
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Wake the device and show over the lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        setContentView(R.layout.activity_emergency_alert)

        repository = AppRepository(this)
        escalationManager = EscalationManager(this, repository)

        val countdownText = findViewById<TextView>(R.id.countdown_text)
        val slideSeekbar = findViewById<SeekBar>(R.id.slide_seekbar)
        val slideProgress = findViewById<View>(R.id.slide_progress)
        val callButton = findViewById<MaterialButton>(R.id.call_contacts_btn)
        val warningIconBg = findViewById<FrameLayout>(R.id.warning_icon_bg)

        warningIconBg.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse_scale))
        startCountdown(countdownText)
        wireSlider(slideSeekbar, slideProgress)

        callButton.setOnClickListener {
            countDownTimer?.cancel()
            escalationManager.dialEmergencyContact()
            finish()
        }
    }

    private fun startCountdown(countdownText: TextView) {
        countDownTimer = object : CountDownTimer(10_000L, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                countdownText.text = ((millisUntilFinished + 999L) / 1000L).toString()
            }

            override fun onFinish() {
                countdownText.text = "0"
                executeEscalation()
            }
        }.start()
    }

    private fun wireSlider(seekbar: SeekBar, progressView: View) {
        seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val fraction = progress / 100f
                val availableWidth = sb.width - sb.paddingStart - sb.paddingEnd
                val lp = progressView.layoutParams as FrameLayout.LayoutParams
                lp.width = (fraction * availableWidth).toInt()
                progressView.layoutParams = lp
            }

            override fun onStartTrackingTouch(sb: SeekBar) {}

            override fun onStopTrackingTouch(sb: SeekBar) {
                if (sb.progress >= 99) {
                    cancelAndSuppress()
                } else {
                    sb.progress = 0
                    val lp = progressView.layoutParams as FrameLayout.LayoutParams
                    lp.width = 0
                    progressView.layoutParams = lp
                }
            }
        })
    }

    private fun cancelAndSuppress() {
        countDownTimer?.cancel()
        val suppressUntil = System.currentTimeMillis() + 15 * 60 * 1_000L
        repository.setEmergencyAlertSuppressedUntil(suppressUntil)
        finish()
    }

    private fun executeEscalation() {
        escalationManager.dialEmergencyContact()
        escalationManager.smsNonPrimaryContacts()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}
