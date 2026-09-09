package org.kashmir.heatguard.ui.onboarding

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import org.kashmir.heatguard.R
import org.kashmir.heatguard.data.AppRepository
import org.kashmir.heatguard.data.EmailConfirmationPendingException
import org.kashmir.heatguard.data.SupabaseClientProvider
import org.kashmir.heatguard.data.SupabaseRepository

class LoginFragment : Fragment(R.layout.fragment_login) {

    private var isSignUp = true
    private var passwordVisible = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // If a Supabase session already exists, skip the form entirely.
        // currentSessionOrNull() reads from the SDK's in-memory/persisted cache — no network call.
        val existingSession = SupabaseClientProvider.client?.auth?.currentSessionOrNull()
        if (existingSession != null) {
            (activity as OnboardingActivity).onAuthCompleted()
            return
        }

        setupUi(view)
    }

    private fun setupUi(view: View) {
        val etEmail = view.findViewById<EditText>(R.id.etEmail)
        val etPassword = view.findViewById<EditText>(R.id.etPassword)
        val btnPrimary = view.findViewById<MaterialButton>(R.id.btnPrimary)
        val btnTogglePassword = view.findViewById<ImageButton>(R.id.btnTogglePassword)
        val tvAuthSwitchPrompt = view.findViewById<TextView>(R.id.tvAuthSwitchPrompt)
        val tvAuthSwitchLink = view.findViewById<TextView>(R.id.tvAuthSwitchLink)
        val tvPasswordLabel = view.findViewById<TextView>(R.id.tvPasswordLabel)
        val layoutTerms = view.findViewById<View>(R.id.layoutTerms)
        val cbTerms = view.findViewById<CheckBox>(R.id.cbTerms)
        val tvError = view.findViewById<TextView>(R.id.tvError)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)

        btnTogglePassword.setOnClickListener {
            passwordVisible = !passwordVisible
            etPassword.inputType = if (passwordVisible)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            etPassword.setSelection(etPassword.text.length)
            btnTogglePassword.setImageResource(
                if (passwordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility
            )
        }

        tvAuthSwitchLink.setOnClickListener {
            isSignUp = !isSignUp
            applyMode(tvPasswordLabel, layoutTerms, btnPrimary, tvAuthSwitchPrompt, tvAuthSwitchLink)
            tvError.visibility = View.GONE
        }

        btnPrimary.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()
            tvError.visibility = View.GONE

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showError(tvError, getString(R.string.login_error_invalid_email))
                return@setOnClickListener
            }
            if (password.length < 8) {
                showError(tvError, getString(R.string.login_error_short_password))
                return@setOnClickListener
            }
            if (isSignUp && !cbTerms.isChecked) {
                showError(tvError, getString(R.string.login_error_terms))
                return@setOnClickListener
            }

            val client = SupabaseClientProvider.client
            if (client == null) {
                showError(tvError, getString(R.string.login_error_not_configured))
                return@setOnClickListener
            }

            setLoading(true, btnPrimary, progressBar)
            val repo = SupabaseRepository(client)

            viewLifecycleOwner.lifecycleScope.launch {
                val authResult = if (isSignUp) {
                    repo.signUpWithEmail(email, password)
                } else {
                    repo.signInWithEmail(email, password)
                }

                val userId = authResult.getOrElse { e ->
                    setLoading(false, btnPrimary, progressBar)
                    if (e is EmailConfirmationPendingException) {
                        isSignUp = false
                        applyMode(tvPasswordLabel, layoutTerms, btnPrimary, tvAuthSwitchPrompt, tvAuthSwitchLink)
                        showError(tvError, getString(R.string.login_confirm_email_sent))
                    } else {
                        showError(tvError, friendlyAuthError(e.message, isSignUp))
                    }
                    return@launch
                }

                // Auth succeeded — keep spinner visible while restoring profile from Supabase.
                val appRepo = AppRepository(requireContext())
                val storedId = appRepo.getSupabaseUserId()

                // Wipe local data only when a different account takes over this device.
                if (storedId.isNotBlank() && storedId != userId) {
                    appRepo.deleteAllData()
                }
                appRepo.setSupabaseUserId(userId)

                // Fetch the user's profile from Supabase.
                // - Found: returning user — restore local cache and skip onboarding.
                // - Not found: new user — local data stays empty, onboarding runs.
                // - Network error: getOrNull() returns null, treated same as new user.
                val profile = repo.fetchUserProfile(userId)
                if (profile != null) {
                    if (!profile.name.isNullOrBlank()) appRepo.setUserName(profile.name)
                    if (profile.age != null && profile.age > 0) appRepo.setUserAge(profile.age)
                    if (!profile.gender.isNullOrBlank()) appRepo.setUserGender(profile.gender)
                    appRepo.setLanguage(profile.language)
                    if (!profile.contact_1_name.isNullOrBlank()) {
                        appRepo.setEmergencyContact(profile.contact_1_name, profile.contact_1_phone ?: "")
                    }
                    if (!profile.contact_2_name.isNullOrBlank()) {
                        appRepo.setSecondContact(profile.contact_2_name, profile.contact_2_phone ?: "")
                    }
                    val riskProfile = repo.fetchRiskProfile(userId)
                    if (riskProfile != null) appRepo.setRiskProfile(riskProfile)
                    appRepo.setOnboardingComplete(true)
                }

                setLoading(false, btnPrimary, progressBar)
                (activity as OnboardingActivity).onAuthCompleted()
            }
        }
    }

    private fun applyMode(
        tvPasswordLabel: TextView,
        layoutTerms: View,
        btnPrimary: MaterialButton,
        tvAuthSwitchPrompt: TextView,
        tvAuthSwitchLink: TextView
    ) {
        if (isSignUp) {
            tvPasswordLabel.text = getString(R.string.login_password_label_signup)
            layoutTerms.visibility = View.VISIBLE
            btnPrimary.text = getString(R.string.login_create_account)
            tvAuthSwitchPrompt.text = getString(R.string.login_have_account)
            tvAuthSwitchLink.text = getString(R.string.login_link)
        } else {
            tvPasswordLabel.text = getString(R.string.login_password_label_login)
            layoutTerms.visibility = View.GONE
            btnPrimary.text = getString(R.string.login_sign_in)
            tvAuthSwitchPrompt.text = getString(R.string.login_no_account)
            tvAuthSwitchLink.text = getString(R.string.login_sign_up_link)
        }
    }

    private fun showError(tvError: TextView, message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }

    private fun setLoading(loading: Boolean, btnPrimary: MaterialButton, progressBar: ProgressBar) {
        btnPrimary.isEnabled = !loading
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun friendlyAuthError(raw: String?, isSignUp: Boolean): String {
        val msg = raw?.lowercase() ?: ""
        return when {
            isSignUp && ("user_already_exists" in msg || "already registered" in msg || "already been registered" in msg) ->
                "An account with this email already exists. Please log in instead."
            !isSignUp && ("invalid login" in msg || "invalid credentials" in msg || "email not confirmed" in msg) ->
                "Incorrect email or password. Please try again."
            "rate limit" in msg || "too many" in msg ->
                "Too many attempts. Please wait a moment and try again."
            "network" in msg || "connect" in msg || "timeout" in msg ->
                "Connection failed. Please check your internet and try again."
            else -> getString(R.string.login_error_failed)
        }
    }
}
