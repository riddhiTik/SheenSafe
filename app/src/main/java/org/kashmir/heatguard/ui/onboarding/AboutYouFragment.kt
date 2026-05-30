package org.kashmir.heatguard.ui.onboarding

import android.os.Bundle
import android.view.View
import android.view.animation.TranslateAnimation
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import org.kashmir.heatguard.R

class AboutYouFragment : Fragment(R.layout.fragment_about_you) {

    private var selectedGender: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvStepLabel = view.findViewById<TextView>(R.id.tvStepLabel)
        val etAge = view.findViewById<EditText>(R.id.etAge)
        val cardMale = view.findViewById<FrameLayout>(R.id.cardMale)
        val cardFemale = view.findViewById<FrameLayout>(R.id.cardFemale)
        val cardOther = view.findViewById<FrameLayout>(R.id.cardOther)
        val checkMale = view.findViewById<ImageView>(R.id.checkMale)
        val checkFemale = view.findViewById<ImageView>(R.id.checkFemale)
        val checkOther = view.findViewById<ImageView>(R.id.checkOther)
        val btnContinue = view.findViewById<MaterialButton>(R.id.btnContinue)
        val btnBack = view.findViewById<ImageButton>(R.id.btnBack)

        tvStepLabel.text = getString(R.string.onboarding_step_label, 2)

        val cards = listOf(
            Triple(cardMale, checkMale, "MALE"),
            Triple(cardFemale, checkFemale, "FEMALE"),
            Triple(cardOther, checkOther, "OTHER")
        )

        cards.forEach { (card, check, gender) ->
            card.setOnClickListener {
                cards.forEach { (c, ch, _) ->
                    c.setBackgroundResource(R.drawable.bg_gender_card_default)
                    ch.visibility = View.GONE
                }
                card.setBackgroundResource(R.drawable.bg_gender_card_selected)
                check.visibility = View.VISIBLE
                selectedGender = gender
            }
        }

        btnContinue.setOnClickListener {
            val ageText = etAge.text?.toString()?.trim()
            val age = ageText?.toIntOrNull()

            var hasError = false
            if (age == null || age <= 0) {
                shake(etAge)
                hasError = true
            }
            if (selectedGender == null) {
                shake(cardMale)
                hasError = true
            }
            if (!hasError) {
                (activity as OnboardingActivity).onAboutYouCompleted(age, selectedGender)
            }
        }

        btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun shake(view: View) {
        val anim = TranslateAnimation(-16f, 16f, 0f, 0f).apply {
            duration = 200
            repeatCount = 3
            repeatMode = TranslateAnimation.REVERSE
        }
        view.startAnimation(anim)
    }
}
