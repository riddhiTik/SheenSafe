package org.kashmir.heatguard.ui

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import org.kashmir.heatguard.R
import org.kashmir.heatguard.data.AppRepository
import org.kashmir.heatguard.data.SupabaseClientProvider
import org.kashmir.heatguard.data.SupabaseRepository

class ContactsFragment : Fragment(R.layout.fragment_contacts) {

    private lateinit var repository: AppRepository

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = AppRepository(requireContext())
        refreshContacts(view)
    }

    private fun refreshContacts(view: View) {
        val contact1Card = view.findViewById<MaterialCardView>(R.id.contact1Card)
        val contact2Card = view.findViewById<MaterialCardView>(R.id.contact2Card)
        val emptyState = view.findViewById<TextView>(R.id.tvContactsEmpty)
        val fab = view.findViewById<FloatingActionButton>(R.id.fabAddContact)

        val name1 = repository.getEmergencyContactName()
        val phone1 = repository.getEmergencyContactPhone()
        val name2 = repository.getSecondContactName()
        val phone2 = repository.getSecondContactPhone()

        val hasContact1 = name1.isNotBlank()
        val hasContact2 = name2.isNotBlank()

        contact1Card.visibility = if (hasContact1) View.VISIBLE else View.GONE
        contact2Card.visibility = if (hasContact2) View.VISIBLE else View.GONE
        emptyState.visibility = if (!hasContact1 && !hasContact2) View.VISIBLE else View.GONE
        fab.visibility = if (hasContact1 && hasContact2) View.GONE else View.VISIBLE

        val primaryIndex = repository.getPrimaryContactIndex()
        val star1 = view.findViewById<ImageView>(R.id.ivContact1Star)
        val star2 = view.findViewById<ImageView>(R.id.ivContact2Star)
        val delete1 = view.findViewById<ImageView>(R.id.ivContact1Delete)
        val delete2 = view.findViewById<ImageView>(R.id.ivContact2Delete)

        if (hasContact1) {
            view.findViewById<TextView>(R.id.tvContact1Initials).text = name1.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            view.findViewById<TextView>(R.id.tvContact1Name).text = name1
            view.findViewById<TextView>(R.id.tvContact1Phone).text = phone1.ifBlank { getString(R.string.metric_no_data) }
            contact1Card.setOnClickListener { showContactDialog(view, slot = 1) }
            applyStarState(star1, primaryIndex == 1)
            star1.setOnClickListener {
                repository.setPrimaryContactIndex(1)
                refreshContacts(view)
            }
            delete1.setOnClickListener { confirmDeleteContact(view, slot = 1, name = name1) }
        }

        if (hasContact2) {
            view.findViewById<TextView>(R.id.tvContact2Initials).text = name2.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            view.findViewById<TextView>(R.id.tvContact2Name).text = name2
            view.findViewById<TextView>(R.id.tvContact2Phone).text = phone2.ifBlank { getString(R.string.metric_no_data) }
            contact2Card.setOnClickListener { showContactDialog(view, slot = 2) }
            applyStarState(star2, primaryIndex == 2)
            star2.setOnClickListener {
                repository.setPrimaryContactIndex(2)
                refreshContacts(view)
            }
            delete2.setOnClickListener { confirmDeleteContact(view, slot = 2, name = name2) }
        }

        fab.setOnClickListener {
            showContactDialog(view, slot = if (!hasContact1) 1 else 2)
        }
    }

    private fun applyStarState(star: ImageView, isPrimary: Boolean) {
        val ctx = requireContext()
        if (isPrimary) {
            star.setImageResource(R.drawable.ic_star_filled)
            star.imageTintList = ContextCompat.getColorStateList(ctx, R.color.warning_color)
        } else {
            star.setImageResource(R.drawable.ic_star_outline)
            star.imageTintList = ContextCompat.getColorStateList(ctx, R.color.muted)
        }
    }

    private fun confirmDeleteContact(view: View, slot: Int, name: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Remove $name?")
            .setMessage("This contact will no longer receive emergency notifications.")
            .setPositiveButton("Remove") { _, _ ->
                if (slot == 1) repository.setEmergencyContact("", "")
                else repository.setSecondContact("", "")
                if (repository.getPrimaryContactIndex() == slot) {
                    val otherSlot = if (slot == 1) 2 else 1
                    val otherHasContact = if (otherSlot == 1) repository.getEmergencyContactName().isNotBlank()
                                         else repository.getSecondContactName().isNotBlank()
                    repository.setPrimaryContactIndex(if (otherHasContact) otherSlot else 1)
                }
                refreshContacts(view)
                syncContactsToSupabase()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun syncContactsToSupabase() {
        val client = SupabaseClientProvider.client ?: return
        val uid = repository.getSupabaseUserId().ifBlank { return }
        viewLifecycleOwner.lifecycleScope.launch {
            SupabaseRepository(client).syncUser(
                deviceId = uid,
                name = repository.getUserName().ifBlank { null },
                age = repository.getUserAge().takeIf { it > 0 },
                gender = repository.getUserGender().ifBlank { null },
                language = repository.getLanguage(),
                contact1Name = repository.getEmergencyContactName().ifBlank { null },
                contact1Phone = repository.getEmergencyContactPhone().ifBlank { null },
                contact2Name = repository.getSecondContactName().ifBlank { null },
                contact2Phone = repository.getSecondContactPhone().ifBlank { null }
            )
        }
    }

    private fun showContactDialog(view: View, slot: Int) {
        val ctx = requireContext()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        val existingName = if (slot == 1) repository.getEmergencyContactName() else repository.getSecondContactName()
        val existingPhone = if (slot == 1) repository.getEmergencyContactPhone() else repository.getSecondContactPhone()

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

        val title = if (existingName.isNotBlank()) "Edit Contact" else getString(R.string.set_contact)
        AlertDialog.Builder(ctx)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton(R.string.save) { _, _ ->
                val n = name.text.toString().trim()
                val p = phone.text.toString().trim()
                if (slot == 1) repository.setEmergencyContact(n, p)
                else repository.setSecondContact(n, p)
                refreshContacts(view)
                syncContactsToSupabase()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
