package org.kashmir.heatguard.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import org.kashmir.heatguard.R
import org.kashmir.heatguard.core.model.RiskProfile
import org.kashmir.heatguard.data.AppRepository

class RiskProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        val repo = AppRepository(this)
        val profile = repo.getRiskProfile()

        setSupportActionBar(findViewById(R.id.toolbarEditProfile))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val etName = findViewById<TextInputEditText>(R.id.etName)
        val etAge  = findViewById<TextInputEditText>(R.id.etAge)
        etName.setText(repo.getUserName())
        etAge.setText(repo.getUserAge().takeIf { it > 0 }?.toString() ?: "")

        val genderToggle = findViewById<MaterialButtonToggleGroup>(R.id.toggleGender)
        genderToggle.check(when (repo.getUserGender()) {
            "MALE"   -> R.id.btnGenderMale
            "FEMALE" -> R.id.btnGenderFemale
            else     -> R.id.btnGenderOther
        })

        val switchPregnant       = findViewById<SwitchMaterial>(R.id.switchPregnant)
        val switchChronic        = findViewById<SwitchMaterial>(R.id.switchChronic)
        val switchOutdoor        = findViewById<SwitchMaterial>(R.id.switchOutdoor)
        val switchFasting        = findViewById<SwitchMaterial>(R.id.switchFasting)
        val switchPriorHeat      = findViewById<SwitchMaterial>(R.id.switchPriorHeat)
        val switchLimitedCooling = findViewById<SwitchMaterial>(R.id.switchLimitedCooling)
        val sliderDays           = findViewById<Slider>(R.id.sliderDaysAcclimatized)
        val tvDaysLabel          = findViewById<TextView>(R.id.tvDaysLabel)

        switchPregnant.isChecked       = profile.pregnant
        switchChronic.isChecked        = profile.chronicIllness
        switchOutdoor.isChecked        = profile.outdoorWorkOrTravel
        switchFasting.isChecked        = profile.fastingOrFluidRestriction
        switchPriorHeat.isChecked      = profile.priorHeatIllness
        switchLimitedCooling.isChecked = profile.limitedCoolingAccess
        sliderDays.value               = profile.daysAcclimatized.toFloat()
        tvDaysLabel.text               = "Days adapting to heat: ${profile.daysAcclimatized}"

        sliderDays.addOnChangeListener { _, value, _ ->
            tvDaysLabel.text = "Days adapting to heat: ${value.toInt()}"
        }

        findViewById<MaterialButton>(R.id.btnSaveProfile).setOnClickListener {
            val name   = etName.text.toString().trim()
            val age    = etAge.text.toString().toIntOrNull() ?: 0
            val gender = when (genderToggle.checkedButtonId) {
                R.id.btnGenderMale   -> "MALE"
                R.id.btnGenderFemale -> "FEMALE"
                else                 -> "OTHER"
            }
            if (name.isNotBlank()) repo.setUserName(name)
            if (age > 0) repo.setUserAge(age)
            repo.setUserGender(gender)
            repo.setRiskProfile(RiskProfile(
                ageBand                   = repo.getRiskProfile().ageBand,
                pregnant                  = switchPregnant.isChecked,
                chronicIllness            = switchChronic.isChecked,
                outdoorWorkOrTravel       = switchOutdoor.isChecked,
                fastingOrFluidRestriction = switchFasting.isChecked,
                priorHeatIllness          = switchPriorHeat.isChecked,
                limitedCoolingAccess      = switchLimitedCooling.isChecked,
                daysAcclimatized          = sliderDays.value.toInt()
            ))
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
