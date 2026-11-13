package org.kashmir.heatguard.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.kashmir.heatguard.R

/** Shown by Health Connect when the user asks how SheenSafe uses their data. */
class HealthPrivacyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        setContentView(TextView(this).apply {
            setPadding(pad, pad, pad, pad)
            textSize = 16f
            setText(R.string.health_connect_rationale)
        })
    }
}
