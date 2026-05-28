package org.kashmir.heatguard.ui.onboarding

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.kashmir.heatguard.R

class LanguageSelectionFragment : Fragment(R.layout.fragment_language_selection) {

    private var selectedLanguageCode: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val cardEnglish = view.findViewById<MaterialCardView>(R.id.cardEnglish)
        val cardHindi = view.findViewById<MaterialCardView>(R.id.cardHindi)
        val cardKashmiri = view.findViewById<MaterialCardView>(R.id.cardKashmiri)
        val cardUrdu = view.findViewById<MaterialCardView>(R.id.cardUrdu)
        val btnBegin = view.findViewById<MaterialButton>(R.id.btnBeginJourney)

        val checkEnglish = view.findViewById<ImageView>(R.id.checkEnglish)
        val checkHindi = view.findViewById<ImageView>(R.id.checkHindi)
        val checkKashmiri = view.findViewById<ImageView>(R.id.checkKashmiri)
        val checkUrdu = view.findViewById<ImageView>(R.id.checkUrdu)

        data class LanguageOption(
            val card: MaterialCardView,
            val check: ImageView,
            val code: String
        )

        val options = listOf(
            LanguageOption(cardEnglish, checkEnglish, "en"),
            LanguageOption(cardHindi, checkHindi, "hi"),
            LanguageOption(cardKashmiri, checkKashmiri, "ks"),
            LanguageOption(cardUrdu, checkUrdu, "ur")
        )

        val colorDefault = ContextCompat.getColor(requireContext(), R.color.card_stroke_default)
        val colorSelected = ContextCompat.getColor(requireContext(), R.color.primary)
        val bgDefault = ContextCompat.getColor(requireContext(), R.color.white)
        val bgSelected = ContextCompat.getColor(requireContext(), R.color.card_selected_bg)

        fun selectOption(chosen: LanguageOption) {
            options.forEach { opt ->
                opt.card.strokeColor = colorDefault
                opt.card.setCardBackgroundColor(bgDefault)
                opt.check.visibility = View.INVISIBLE
            }
            chosen.card.strokeColor = colorSelected
            chosen.card.setCardBackgroundColor(bgSelected)
            chosen.check.visibility = View.VISIBLE
            selectedLanguageCode = chosen.code
            btnBegin.isEnabled = true
        }

        options.forEach { opt ->
            opt.card.setOnClickListener { selectOption(opt) }
        }

        btnBegin.setOnClickListener {
            val code = selectedLanguageCode ?: return@setOnClickListener
            (activity as? OnboardingActivity)?.onLanguageSelected(code)
        }
    }
}
