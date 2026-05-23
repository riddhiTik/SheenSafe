package org.kashmir.heatguard.ui

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import org.kashmir.heatguard.R
import org.kashmir.heatguard.core.model.AgeBand
import org.kashmir.heatguard.core.model.RiskProfile
import org.kashmir.heatguard.data.AppRepository

class RiskProfileActivity : Activity() {
    private lateinit var repository: AppRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = AppRepository(this)
        buildUi()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 40)
        }
        scroll.addView(root)

        val profile = repository.getRiskProfile()

        root.addView(TextView(this).apply {
            text = getString(R.string.risk_profile_title)
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        })

        root.addView(TextView(this).apply {
            text = getString(R.string.age_band_label)
            textSize = 16f
        })
        val ageBandSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@RiskProfileActivity,
                android.R.layout.simple_spinner_item,
                AgeBand.values().map { it.name }
            )
            setSelection(AgeBand.values().indexOf(profile.ageBand))
        }
        root.addView(ageBandSpinner)

        val pregnantBox = checkBox(getString(R.string.pregnant_label), profile.pregnant, root)
        val chronicBox = checkBox(getString(R.string.chronic_illness_label), profile.chronicIllness, root)
        val outdoorBox = checkBox(getString(R.string.outdoor_work_label), profile.outdoorWorkOrTravel, root)
        val fastingBox = checkBox(getString(R.string.fasting_label), profile.fastingOrFluidRestriction, root)
        val priorHeatBox = checkBox(getString(R.string.prior_heat_illness_label), profile.priorHeatIllness, root)
        val limitedCoolingBox = checkBox(getString(R.string.limited_cooling_label), profile.limitedCoolingAccess, root)

        val daysLabel = TextView(this).apply {
            text = getString(R.string.days_acclimatized_label, profile.daysAcclimatized)
            textSize = 16f
            setPadding(0, 16, 0, 4)
        }
        root.addView(daysLabel)
        val daysSeekBar = SeekBar(this).apply {
            max = 14
            progress = profile.daysAcclimatized
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                    daysLabel.text = getString(R.string.days_acclimatized_label, value)
                }
                override fun onStartTrackingTouch(bar: SeekBar) {}
                override fun onStopTrackingTouch(bar: SeekBar) {}
            })
        }
        root.addView(daysSeekBar)

        root.addView(Button(this).apply {
            text = getString(R.string.save)
            textSize = 18f
            setAllCaps(false)
            setPadding(0, 24, 0, 0)
            setOnClickListener {
                repository.setRiskProfile(
                    RiskProfile(
                        ageBand = AgeBand.values()[ageBandSpinner.selectedItemPosition],
                        pregnant = pregnantBox.isChecked,
                        chronicIllness = chronicBox.isChecked,
                        outdoorWorkOrTravel = outdoorBox.isChecked,
                        fastingOrFluidRestriction = fastingBox.isChecked,
                        priorHeatIllness = priorHeatBox.isChecked,
                        limitedCoolingAccess = limitedCoolingBox.isChecked,
                        daysAcclimatized = daysSeekBar.progress
                    )
                )
                finish()
            }
        })

        setContentView(scroll)
    }

    private fun checkBox(label: String, checked: Boolean, parent: LinearLayout): CheckBox {
        return CheckBox(this).apply {
            text = label
            textSize = 16f
            isChecked = checked
            parent.addView(this)
        }
    }
}
