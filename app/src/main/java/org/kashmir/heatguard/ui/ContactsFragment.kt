package org.kashmir.heatguard.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.kashmir.heatguard.R
import org.kashmir.heatguard.data.AppRepository

class ContactsFragment : Fragment(R.layout.fragment_contacts) {

    private lateinit var repository: AppRepository

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = AppRepository(requireContext())
        refreshContacts(view)

        view.findViewById<FloatingActionButton>(R.id.fabAddContact).setOnClickListener {
            showAddContactDialog(view)
        }
    }

    private fun refreshContacts(view: View) {
        val contact1Card = view.findViewById<MaterialCardView>(R.id.contact1Card)
        val contact2Card = view.findViewById<MaterialCardView>(R.id.contact2Card)
        val emptyState = view.findViewById<TextView>(R.id.tvContactsEmpty)

        val name1 = repository.getEmergencyContactName()
        val phone1 = repository.getEmergencyContactPhone()
        val name2 = repository.getSecondContactName()
        val phone2 = repository.getSecondContactPhone()

        val hasContact1 = name1.isNotBlank()
        val hasContact2 = name2.isNotBlank()

        contact1Card.visibility = if (hasContact1) View.VISIBLE else View.GONE
        contact2Card.visibility = if (hasContact2) View.VISIBLE else View.GONE
        emptyState.visibility = if (!hasContact1 && !hasContact2) View.VISIBLE else View.GONE

        if (hasContact1) {
            view.findViewById<TextView>(R.id.tvContact1Initials).text = name1.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            view.findViewById<TextView>(R.id.tvContact1Name).text = name1
            view.findViewById<TextView>(R.id.tvContact1Phone).text = phone1.ifBlank { getString(R.string.metric_no_data) }
            contact1Card.setOnClickListener { dialContact(phone1) }
        }

        if (hasContact2) {
            view.findViewById<TextView>(R.id.tvContact2Initials).text = name2.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            view.findViewById<TextView>(R.id.tvContact2Name).text = name2
            view.findViewById<TextView>(R.id.tvContact2Phone).text = phone2.ifBlank { getString(R.string.metric_no_data) }
            contact2Card.setOnClickListener { dialContact(phone2) }
        }
    }

    private fun dialContact(phone: String) {
        if (phone.isBlank()) return
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
    }

    private fun showAddContactDialog(view: View) {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val existingName = repository.getEmergencyContactName()
        val existingPhone = repository.getEmergencyContactPhone()

        val name = EditText(ctx).apply {
            hint = getString(R.string.onboarding_contact_name_placeholder)
            setText(existingName)
        }
        val phone = EditText(ctx).apply {
            hint = getString(R.string.onboarding_contact_phone_placeholder)
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setText(existingPhone)
        }
        layout.addView(name)
        layout.addView(phone)

        AlertDialog.Builder(ctx)
            .setTitle(R.string.set_contact)
            .setView(layout)
            .setPositiveButton(R.string.save) { _, _ ->
                repository.setEmergencyContact(name.text.toString(), phone.text.toString())
                refreshContacts(view)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
