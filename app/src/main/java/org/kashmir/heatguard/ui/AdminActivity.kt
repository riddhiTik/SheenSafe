package org.kashmir.heatguard.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch
import org.kashmir.heatguard.R
import org.kashmir.heatguard.data.SupabaseClientProvider
import org.kashmir.heatguard.data.SupabaseRepository

class AdminActivity : AppCompatActivity() {

    private lateinit var supabaseRepo: SupabaseRepository
    private lateinit var adapter: DecisionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        supabaseRepo = SupabaseRepository(SupabaseClientProvider.client)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val rvDecisions = findViewById<RecyclerView>(R.id.rvDecisions)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val tvEmpty = findViewById<TextView>(R.id.tvEmpty)

        adapter = DecisionAdapter()
        rvDecisions.layoutManager = LinearLayoutManager(this)
        rvDecisions.adapter = adapter

        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val decisions = supabaseRepo.getRecentDecisions(limit = 100)
            progressBar.visibility = View.GONE
            if (decisions.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
            } else {
                adapter.setData(decisions)
            }
        }
    }

    // ------------------------------------------------------------------ adapter

    private class DecisionAdapter : RecyclerView.Adapter<DecisionAdapter.VH>() {

        private val items = mutableListOf<SupabaseRepository.RiskDecisionRow>()

        fun setData(data: List<SupabaseRepository.RiskDecisionRow>) {
            items.clear()
            items.addAll(data)
            notifyDataSetChanged()
        }

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvRiskLevel: TextView = itemView.findViewById(R.id.tvRiskLevel)
            val tvConfidence: TextView = itemView.findViewById(R.id.tvConfidence)
            val tvReasonCodes: TextView = itemView.findViewById(R.id.tvReasonCodes)
            val tvDeviceId: TextView = itemView.findViewById(R.id.tvDeviceId)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_decision, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val row = items[position]
            holder.tvRiskLevel.text = row.risk_level
            holder.tvConfidence.text = row.confidence
            holder.tvReasonCodes.text = row.reason_codes.joinToString(", ")
            holder.tvDeviceId.text = "device: ${row.device_id.take(8)}…"
        }

        override fun getItemCount() = items.size
    }
}
