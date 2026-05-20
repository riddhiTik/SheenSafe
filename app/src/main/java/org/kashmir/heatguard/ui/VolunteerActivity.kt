package org.kashmir.heatguard.ui

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.kashmir.heatguard.R

class VolunteerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 40)
        }
        scroll.addView(root)
        root.addView(TextView(this).apply {
            text = getString(R.string.volunteer_view)
            textSize = 26f
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.volunteer_stub)
            textSize = 18f
            setPadding(0, 24, 0, 24)
        })
        val demoRows = listOf(
            "Zone A / User 001 — CHECK — ring 4 min ago",
            "Zone A / User 002 — SAFE — ring 2 min ago",
            "Zone B / User 003 — ACTION — no response",
            "Zone B / User 004 — NO SIGNAL — battery low"
        )
        demoRows.forEach { row ->
            root.addView(TextView(this).apply {
                text = row
                textSize = 18f
                setPadding(0, 16, 0, 16)
            })
        }
        setContentView(scroll)
    }
}
