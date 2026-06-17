package org.kashmir.heatguard.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import org.kashmir.heatguard.R
import org.kashmir.heatguard.data.SupabaseClientProvider
import org.kashmir.heatguard.data.SupabaseRepository
import java.time.Instant

class AdminActivity : AppCompatActivity() {

    private lateinit var repo: SupabaseRepository
    private lateinit var otherUsersAdapter: OtherUsersAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        repo = SupabaseRepository(SupabaseClientProvider.client)

        val progressBar = findViewById<View>(R.id.progressBar)
        val tvEmpty = findViewById<TextView>(R.id.tvEmpty)

        otherUsersAdapter = OtherUsersAdapter { summary ->
            startActivity(
                Intent(this, AdminUserDetailActivity::class.java)
                    .putExtra("device_id", summary.user.device_id)
            )
        }
        val rvOtherUsers = findViewById<RecyclerView>(R.id.rvOtherUsers)
        rvOtherUsers.layoutManager = LinearLayoutManager(this)
        rvOtherUsers.adapter = otherUsersAdapter

        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val users = repo.getAllUsers()
            val decisionsById = repo.getRecentDecisions(200)
                .groupBy { it.device_id }
                .mapValues { (_, v) -> v.first() }
            val samplesById = repo.getRecentSamples(200)
                .groupBy { it.device_id }
                .mapValues { (_, v) -> v.first() }

            val summaries = users.map { user ->
                AdminUserSummary(
                    user = user,
                    latestDecision = decisionsById[user.device_id],
                    latestSample = samplesById[user.device_id]
                )
            }

            progressBar.visibility = View.GONE

            val totalCount = summaries.size
            val safeCount = summaries.count {
                it.latestDecision?.risk_level?.toRiskCategory() == RiskCategory.SAFE
            }
            val highRiskCount = summaries.count {
                it.latestDecision?.risk_level?.toRiskCategory() == RiskCategory.HIGH_RISK
            }

            findViewById<TextView>(R.id.tvTotalUsers).text = totalCount.toString()
            findViewById<TextView>(R.id.tvSafeCount).text = safeCount.toString()
            findViewById<TextView>(R.id.tvHighRiskCount).text = highRiskCount.toString()

            val priorityUser = summaries
                .filter { it.latestDecision?.risk_level?.toRiskCategory() == RiskCategory.HIGH_RISK }
                .firstOrNull()

            if (priorityUser != null) {
                showPriorityAlert(priorityUser)
            }

            val otherUsers = summaries.filter {
                it.latestDecision?.risk_level?.toRiskCategory() != RiskCategory.HIGH_RISK
            }
            if (otherUsers.isEmpty() && priorityUser == null) {
                tvEmpty.visibility = View.VISIBLE
            } else {
                otherUsersAdapter.setData(otherUsers)
            }
        }
    }

    private fun showPriorityAlert(summary: AdminUserSummary) {
        val container = findViewById<View>(R.id.priorityAlertContainer)
        val sectionHeader = findViewById<View>(R.id.sectionPriority)
        container.visibility = View.VISIBLE
        sectionHeader.visibility = View.VISIBLE

        val name = summary.user.name ?: "Unknown"
        val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

        val tvAvatar = container.findViewById<TextView>(R.id.tvPriorityAvatar)
        tvAvatar.text = initial
        setCircleBackground(tvAvatar, summary.user.device_id)

        container.findViewById<TextView>(R.id.tvPriorityName).text = "$name - High Risk"

        val sample = summary.latestSample
        val decision = summary.latestDecision

        container.findViewById<TextView>(R.id.tvPriorityCoreTemp).text =
            sample?.skin_temperature_c?.let { "%.1f°C".format(it) } ?: "--"
        container.findViewById<TextView>(R.id.tvPriorityHeartRate).text =
            sample?.heart_rate_bpm?.let { "$it BPM" } ?: "--"
        container.findViewById<TextView>(R.id.tvPriorityOutsideTemp).text =
            decision?.ambient_temp_c?.let { "%.1f°C".format(it) } ?: "--"

        container.findViewById<View>(R.id.btnContactNow).setOnClickListener {
            val phone = summary.user.contact_1_phone
            if (!phone.isNullOrBlank()) {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
            } else {
                Toast.makeText(this, "No emergency contact on file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ------------------------------------------------------------------ model

    data class AdminUserSummary(
        val user: SupabaseRepository.UserRow,
        val latestDecision: SupabaseRepository.RiskDecisionRow?,
        val latestSample: SupabaseRepository.SensorSampleRow?
    )

    enum class RiskCategory { SAFE, CHECKIN, HIGH_RISK, UNKNOWN }

    private fun String.toRiskCategory() = when (uppercase()) {
        "SAFE", "LOW"                             -> RiskCategory.SAFE
        "CHECK", "MODERATE"                       -> RiskCategory.CHECKIN
        "ACTION", "HIGH", "CRITICAL", "EMERGENCY" -> RiskCategory.HIGH_RISK
        else                                      -> RiskCategory.UNKNOWN
    }

    // ------------------------------------------------------------------ avatar

    private val avatarColors = intArrayOf(
        Color.parseColor("#006E29"), // primary_dark
        Color.parseColor("#F4A261"), // warning_color
        Color.parseColor("#7B9E87"), // sage
        Color.parseColor("#A3B1A8")  // muted
    )

    private fun setCircleBackground(view: TextView, deviceId: String) {
        val color = avatarColors[Math.abs(deviceId.hashCode()) % avatarColors.size]
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        view.background = drawable
    }

    // ------------------------------------------------------------------ adapter

    inner class OtherUsersAdapter(
        private val onChevronClick: (AdminUserSummary) -> Unit
    ) : RecyclerView.Adapter<OtherUsersAdapter.VH>() {

        private val items = mutableListOf<AdminUserSummary>()

        fun setData(data: List<AdminUserSummary>) {
            items.clear()
            items.addAll(data)
            notifyDataSetChanged()
        }

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvInitials: TextView = itemView.findViewById(R.id.tvUserInitials)
            val viewDot: View = itemView.findViewById(R.id.viewStatusDot)
            val tvName: TextView = itemView.findViewById(R.id.tvUserName)
            val tvSync: TextView = itemView.findViewById(R.id.tvSyncTime)
            val btnChevron: FrameLayout = itemView.findViewById(R.id.btnChevron)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_other_user, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val s = items[position]
            val name = s.user.name ?: "Unknown"
            val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

            holder.tvInitials.text = initial
            setCircleBackground(holder.tvInitials, s.user.device_id)

            holder.tvName.text = name

            val dotColor = when (s.latestDecision?.risk_level?.toRiskCategory()) {
                RiskCategory.SAFE    -> ContextCompat.getColor(holder.itemView.context, R.color.primary_dark)
                RiskCategory.CHECKIN -> ContextCompat.getColor(holder.itemView.context, R.color.warning_color)
                else                 -> ContextCompat.getColor(holder.itemView.context, R.color.muted)
            }
            val dotDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(dotColor)
                setStroke(3, Color.WHITE)
            }
            holder.viewDot.background = dotDrawable

            holder.tvSync.text = formatSyncTime(s.latestSample?.sampled_at)

            holder.btnChevron.setOnClickListener { onChevronClick(s) }
            holder.itemView.setOnClickListener { onChevronClick(s) }
        }

        override fun getItemCount() = items.size

        private fun formatSyncTime(sampledAt: String?): String {
            if (sampledAt.isNullOrBlank()) return "No sync"
            return try {
                val instant = Instant.parse(sampledAt)
                val diffMs = System.currentTimeMillis() - instant.toEpochMilli()
                val diffMin = diffMs / 60_000
                when {
                    diffMs < 60_000    -> "Just now"
                    diffMin < 60       -> "Sync: ${diffMin}m ago"
                    diffMin < 1440     -> "Sync: ${diffMin / 60}h ago"
                    else               -> "No sync"
                }
            } catch (_: Exception) {
                "No sync"
            }
        }
    }
}
