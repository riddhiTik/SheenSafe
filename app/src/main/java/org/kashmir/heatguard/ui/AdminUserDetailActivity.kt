package org.kashmir.heatguard.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.kashmir.heatguard.R
import org.kashmir.heatguard.data.SupabaseClientProvider
import org.kashmir.heatguard.data.SupabaseRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AdminUserDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_user_detail)

        val deviceId = intent.getStringExtra("device_id") ?: run {
            finish()
            return
        }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val repo = SupabaseRepository(SupabaseClientProvider.client)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        lifecycleScope.launch {
            val user = repo.fetchUserProfile(deviceId)
            val decisions = repo.getDecisionsForUser(deviceId, limit = 5)
            val latestSample = repo.getLatestSampleForUser(deviceId)

            progressBar.visibility = View.GONE

            // Avatar
            val name = user?.name ?: "Unknown"
            val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            val tvAvatar = findViewById<TextView>(R.id.tvDetailAvatar)
            tvAvatar.text = initial
            val avatarColors = intArrayOf(
                Color.parseColor("#006E29"),
                Color.parseColor("#F4A261"),
                Color.parseColor("#7B9E87"),
                Color.parseColor("#A3B1A8")
            )
            val color = avatarColors[Math.abs(deviceId.hashCode()) % avatarColors.size]
            tvAvatar.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }

            // Identity
            findViewById<TextView>(R.id.tvDetailName).text = name
            val agePart = user?.age?.let { "$it y/o" } ?: ""
            val genderPart = user?.gender ?: ""
            val ageGender = listOf(agePart, genderPart).filter { it.isNotBlank() }.joinToString(", ")
            findViewById<TextView>(R.id.tvDetailAgeGender).text = ageGender.ifBlank { "No profile info" }

            // Risk level chip
            val latestDecision = decisions.firstOrNull()
            val riskLevel = latestDecision?.risk_level ?: "UNKNOWN"
            val chipColor = riskChipColor(riskLevel)
            val tvRisk = findViewById<TextView>(R.id.tvDetailRiskLevel)
            tvRisk.text = riskLevel
            tvRisk.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(chipColor)
                cornerRadius = 24f
            }

            // Vitals
            findViewById<TextView>(R.id.tvDetailCoreTemp).text =
                latestSample?.skin_temperature_c?.let { "%.1f°C".format(it) } ?: "--"
            findViewById<TextView>(R.id.tvDetailHeartRate).text =
                latestSample?.heart_rate_bpm?.let { "$it BPM" } ?: "--"

            // Recent decisions
            val layout = findViewById<LinearLayout>(R.id.layoutDecisions)
            if (decisions.isEmpty()) {
                addDecisionRow(layout, "No decisions logged yet", "", "")
            } else {
                decisions.forEach { row ->
                    val timeStr = row.created_at?.let { formatTimestamp(it) } ?: ""
                    val reasons = row.reason_codes.joinToString(", ").ifBlank { "—" }
                    addDecisionRow(layout, row.risk_level, reasons, timeStr)
                }
            }
        }
    }

    private fun addDecisionRow(parent: LinearLayout, level: String, reasons: String, time: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dpToPx(12))
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val chipColor = riskChipColor(level)
        val chip = TextView(this).apply {
            text = level
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(chipColor)
                cornerRadius = 20f
            }
            background = bg
            setPadding(dpToPx(8), dpToPx(3), dpToPx(8), dpToPx(3))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = dpToPx(8) }
        }
        topRow.addView(chip)

        if (time.isNotBlank()) {
            val tvTime = TextView(this).apply {
                text = time
                setTextColor(ContextCompat.getColor(context, R.color.muted))
                textSize = 11f
            }
            topRow.addView(tvTime)
        }
        row.addView(topRow)

        if (reasons.isNotBlank() && reasons != "—") {
            val tvReasons = TextView(this).apply {
                text = reasons
                setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant))
                textSize = 12f
                setPadding(0, dpToPx(3), 0, 0)
            }
            row.addView(tvReasons)
        }

        val divider = View(this).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.outline_variant))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)
            ).also { it.topMargin = dpToPx(8) }
        }
        row.addView(divider)

        parent.addView(row)
    }

    private fun riskChipColor(level: String) = when (level.uppercase()) {
        "SAFE", "LOW"                             -> Color.parseColor("#006E29")
        "CHECK", "MODERATE"                       -> Color.parseColor("#F4A261")
        "ACTION", "HIGH", "CRITICAL", "EMERGENCY" -> Color.parseColor("#BA1A1A")
        else                                      -> Color.parseColor("#A3B1A8")
    }

    private fun formatTimestamp(iso: String): String = try {
        val instant = Instant.parse(iso)
        val formatter = DateTimeFormatter.ofPattern("d MMM, HH:mm")
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    } catch (_: Exception) {
        ""
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()
}
