package org.kashmir.heatguard.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import org.kashmir.heatguard.R
import org.kashmir.heatguard.data.AppRepository
import org.kashmir.heatguard.data.SupabaseClientProvider
import org.kashmir.heatguard.data.SupabaseRepository

class ContactsFragment : Fragment(R.layout.fragment_contacts) {

    private lateinit var repository: AppRepository

    /**
     * Slots 1-2 are the sync-backed "primary" contacts; 3-4 are local-only "secondary" contacts.
     * [isCallPrimary] is independent of that: it marks the single contact (any slot) the user has
     * starred to receive a phone call on EMERGENCY escalation. Every other filled contact gets an SMS.
     */
    private data class Slot(
        val index: Int,
        val name: String,
        val phone: String,
        val relation: String,
        val isPrimary: Boolean,
        val isCallPrimary: Boolean,
        val avatarColorRes: Int
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = AppRepository(requireContext())
        refreshContacts(view)
    }

    private fun slots(): List<Slot> {
        val primaryIndex = repository.getPrimaryContactIndex()
        return listOf(
            Slot(1, repository.getEmergencyContactName(), repository.getEmergencyContactPhone(), repository.getContact1Relation(), true, primaryIndex == 1, R.color.warning_color),
            Slot(2, repository.getSecondContactName(), repository.getSecondContactPhone(), repository.getContact2Relation(), true, primaryIndex == 2, R.color.logo_blue),
            Slot(3, repository.getThirdContactName(), repository.getThirdContactPhone(), repository.getThirdContactRelation(), false, primaryIndex == 3, R.color.live_dot_green),
            Slot(4, repository.getFourthContactName(), repository.getFourthContactPhone(), repository.getFourthContactRelation(), false, primaryIndex == 4, R.color.avatar_yellow)
        )
    }

    private fun cardIdFor(slot: Int) = when (slot) {
        1 -> R.id.contact1Card
        2 -> R.id.contact2Card
        3 -> R.id.contact3Card
        else -> R.id.contact4Card
    }

    private fun refreshContacts(view: View) {
        val allSlots = slots()
        val filled = allSlots.filter { it.name.isNotBlank() }

        view.findViewById<TextView>(R.id.tvAlertCount).text =
            getString(R.string.contacts_alert_count, filled.size)

        view.findViewById<TextView>(R.id.tvInfoBanner).text = buildSpannedString {
            append(getString(R.string.contacts_info_banner_prefix))
            color(ContextCompat.getColor(requireContext(), R.color.critical_color)) {
                append(getString(R.string.contacts_info_banner_critical))
            }
        }

        view.findViewById<TextView>(R.id.tvPrimaryLabel).visibility =
            if (allSlots.filter { it.isPrimary }.any { it.name.isNotBlank() }) View.VISIBLE else View.GONE
        view.findViewById<TextView>(R.id.tvSecondaryLabel).visibility =
            if (allSlots.filter { !it.isPrimary }.any { it.name.isNotBlank() }) View.VISIBLE else View.GONE

        for (slot in allSlots) {
            bindCard(view, slot)
        }

        view.findViewById<TextView>(R.id.tvContactsEmpty).visibility =
            if (filled.isEmpty()) View.VISIBLE else View.GONE

        view.findViewById<LinearLayout>(R.id.btnAddContact).apply {
            visibility = if (filled.size < allSlots.size) View.VISIBLE else View.GONE
            setOnClickListener {
                val nextEmpty = allSlots.firstOrNull { it.name.isBlank() }
                if (nextEmpty == null) {
                    Toast.makeText(requireContext(), R.string.contacts_max_reached, Toast.LENGTH_SHORT).show()
                } else {
                    showContactDialog(view, nextEmpty.index)
                }
            }
        }
    }

    private fun bindCard(view: View, slot: Slot) {
        val card = view.findViewById<MaterialCardView>(cardIdFor(slot.index))
        val hasContact = slot.name.isNotBlank()
        card.visibility = if (hasContact) View.VISIBLE else View.GONE
        if (!hasContact) return

        card.findViewById<TextView>(R.id.tvContactInitials).apply {
            text = slot.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            backgroundTintList = ContextCompat.getColorStateList(requireContext(), slot.avatarColorRes)
        }
        card.findViewById<TextView>(R.id.tvContactName).text = slot.name
        card.findViewById<TextView>(R.id.tvContactDetail).text = listOf(slot.relation, slot.phone)
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(getString(R.string.metric_no_data)) }
            .joinToString(" • ")

        card.findViewById<ImageView>(R.id.ivContactStar).apply {
            visibility = View.VISIBLE
            setImageResource(if (slot.isCallPrimary) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
            imageTintList = ContextCompat.getColorStateList(
                requireContext(),
                if (slot.isCallPrimary) R.color.warning_color else R.color.muted
            )
            contentDescription = if (slot.isCallPrimary) {
                getString(R.string.contacts_primary_contact_label)
            } else {
                getString(R.string.contacts_set_primary_contact)
            }
            setOnClickListener {
                // Only one contact can ever be primary — setting a new one clears the old star.
                repository.setPrimaryContactIndex(slot.index)
                refreshContacts(view)
                if (slot.index <= 2) syncContactsToSupabase()
            }
        }

        card.findViewById<View>(R.id.btnCallContact).setOnClickListener {
            callContact(slot.phone)
        }

        card.setOnClickListener { showContactDialog(view, slot.index) }
    }

    private fun callContact(phone: String) {
        if (phone.isBlank()) return
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}"))
        startActivity(intent)
    }

    private fun getSlotName(index: Int): String = when (index) {
        1 -> repository.getEmergencyContactName()
        2 -> repository.getSecondContactName()
        3 -> repository.getThirdContactName()
        else -> repository.getFourthContactName()
    }

    private fun getSlotPhone(index: Int): String = when (index) {
        1 -> repository.getEmergencyContactPhone()
        2 -> repository.getSecondContactPhone()
        3 -> repository.getThirdContactPhone()
        else -> repository.getFourthContactPhone()
    }

    private fun getSlotRelation(index: Int): String = when (index) {
        1 -> repository.getContact1Relation()
        2 -> repository.getContact2Relation()
        3 -> repository.getThirdContactRelation()
        else -> repository.getFourthContactRelation()
    }

    private fun saveSlot(index: Int, name: String, phone: String, relation: String) {
        when (index) {
            1 -> {
                repository.setEmergencyContact(name, phone)
                repository.setContact1Relation(relation)
            }
            2 -> {
                repository.setSecondContact(name, phone)
                repository.setContact2Relation(relation)
            }
            3 -> repository.setThirdContact(name, phone, relation)
            else -> repository.setFourthContact(name, phone, relation)
        }
    }

    private fun clearSlot(index: Int) {
        saveSlot(index, "", "", "")
        if (repository.getPrimaryContactIndex() == index) {
            val nextPrimary = (1..4).firstOrNull { it != index && getSlotPhone(it).isNotBlank() } ?: 1
            repository.setPrimaryContactIndex(nextPrimary)
        }
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
        val existingName = getSlotName(slot)
        val existingPhone = getSlotPhone(slot)
        val existingRelation = getSlotRelation(slot)

        val name = EditText(ctx).apply {
            hint = getString(R.string.onboarding_contact_name_placeholder)
            setText(existingName)
        }
        val relation = EditText(ctx).apply {
            hint = getString(R.string.contacts_relation_placeholder)
            setText(existingRelation)
        }
        val phone = EditText(ctx).apply {
            hint = getString(R.string.onboarding_contact_phone_placeholder)
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setText(existingPhone)
        }
        layout.addView(name)
        layout.addView(relation)
        layout.addView(phone)

        val title = if (existingName.isNotBlank()) "Edit Contact" else getString(R.string.set_contact)
        val builder = AlertDialog.Builder(ctx)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton(R.string.save) { _, _ ->
                val n = name.text.toString().trim()
                val p = phone.text.toString().trim()
                val r = relation.text.toString().trim()
                saveSlot(slot, n, p, r)
                refreshContacts(view)
                if (slot <= 2) syncContactsToSupabase()
            }
            .setNegativeButton(R.string.cancel, null)

        if (existingName.isNotBlank()) {
            builder.setNeutralButton("Remove") { _, _ ->
                clearSlot(slot)
                refreshContacts(view)
                if (slot <= 2) syncContactsToSupabase()
            }
        }
        builder.show()
    }
}
