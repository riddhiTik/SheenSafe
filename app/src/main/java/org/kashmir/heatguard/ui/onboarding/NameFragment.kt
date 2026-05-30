package org.kashmir.heatguard.ui.onboarding

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import org.kashmir.heatguard.R

class NameFragment : Fragment(R.layout.fragment_name) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etName = view.findViewById<EditText>(R.id.etName)
        val btnContinue = view.findViewById<MaterialButton>(R.id.btnContinue)
        val tvSkip = view.findViewById<TextView>(R.id.tvSkip)
        val tvStepLabel = view.findViewById<TextView>(R.id.tvStepLabel)
        val btnBack = view.findViewById<ImageButton>(R.id.btnBack)

        tvStepLabel.text = getString(R.string.onboarding_step_label, 1)

        btnContinue.alpha = 0.5f
        btnContinue.isEnabled = false

        etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val hasText = s?.trim()?.isNotEmpty() == true
                btnContinue.isEnabled = hasText
                btnContinue.alpha = if (hasText) 1.0f else 0.5f
            }
        })

        btnContinue.setOnClickListener {
            (activity as OnboardingActivity).onNameCompleted(etName.text.toString())
        }

        tvSkip.setOnClickListener {
            (activity as OnboardingActivity).onNameCompleted(null)
        }

        btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }
}
