package org.kashmir.heatguard.ui.onboarding

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import org.kashmir.heatguard.R

class EmergencyContactFragment : Fragment(R.layout.fragment_emergency_contact) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvStepLabel = view.findViewById<TextView>(R.id.tvStepLabel)
        val tvContact1Label = view.findViewById<TextView>(R.id.tvContact1Label)
        val tvContact2Label = view.findViewById<TextView>(R.id.tvContact2Label)
        val etContact1Name = view.findViewById<EditText>(R.id.etContact1Name)
        val etContact1Phone = view.findViewById<EditText>(R.id.etContact1Phone)
        val contact2Section = view.findViewById<LinearLayout>(R.id.contact2Section)
        val etContact2Name = view.findViewById<EditText>(R.id.etContact2Name)
        val etContact2Phone = view.findViewById<EditText>(R.id.etContact2Phone)
        val btnAddContact = view.findViewById<LinearLayout>(R.id.btnAddContact)
        val btnRemoveContact2 = view.findViewById<ImageButton>(R.id.btnRemoveContact2)
        val btnContinue = view.findViewById<MaterialButton>(R.id.btnContinue)
        val tvSkip = view.findViewById<TextView>(R.id.tvSkip)
        val btnBack = view.findViewById<ImageButton>(R.id.btnBack)

        tvStepLabel.text = getString(R.string.onboarding_step_label, 6)
        tvContact1Label.text = getString(R.string.onboarding_contact_section_label, 1)
        tvContact2Label.text = getString(R.string.onboarding_contact_section_label, 2)

        btnAddContact.setOnClickListener {
            contact2Section.visibility = View.VISIBLE
            btnAddContact.visibility = View.GONE
        }

        btnRemoveContact2.setOnClickListener {
            contact2Section.visibility = View.GONE
            btnAddContact.visibility = View.VISIBLE
            etContact2Name.text?.clear()
            etContact2Phone.text?.clear()
        }

        btnContinue.setOnClickListener {
            val name1 = etContact1Name.text?.toString()?.trim()
            val phone1 = etContact1Phone.text?.toString()?.trim()
            val name2 = if (contact2Section.visibility == View.VISIBLE)
                etContact2Name.text?.toString()?.trim() else null
            val phone2 = if (contact2Section.visibility == View.VISIBLE)
                etContact2Phone.text?.toString()?.trim() else null

            (activity as OnboardingActivity).onEmergencyContactDone(name1, phone1, name2, phone2)
        }

        tvSkip.setOnClickListener {
            (activity as OnboardingActivity).onEmergencyContactDone(null, null, null, null)
        }

        btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }
}
