package org.kashmir.heatguard.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import org.kashmir.heatguard.R
import org.kashmir.heatguard.data.AppRepository
import org.kashmir.heatguard.data.SupabaseClientProvider
import org.kashmir.heatguard.data.SupabaseRepository
import org.kashmir.heatguard.ui.AdminActivity
import org.kashmir.heatguard.ui.MainActivity

class OnboardingActivity : AppCompatActivity() {

    private lateinit var repository: AppRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = AppRepository(this)

        // currentSessionOrNull() reads from the SDK's in-memory cache — safe to call on main thread.
        val hasSession = SupabaseClientProvider.client?.auth?.currentSessionOrNull() != null

        if (hasSession && repository.isOnboardingComplete()) {
            val userId = repository.getSupabaseUserId()
            val client = SupabaseClientProvider.client
            if (client != null && userId.isNotBlank()) {
                lifecycleScope.launch {
                    if (SupabaseRepository(client).isAdmin(userId)) {
                        launchAdmin()
                    } else {
                        launchMain()
                    }
                }
            } else {
                launchMain()
            }
            return
        }

        setContentView(R.layout.activity_onboarding)

        if (savedInstanceState == null) {
            // LoginFragment handles its own session check and routes accordingly.
            supportFragmentManager.beginTransaction()
                .replace(R.id.onboardingContainer, LoginFragment())
                .commit()
        }
    }

    /** Called by LoginFragment after successful sign-in or sign-up. */
    fun onAuthCompleted() {
        val userId = repository.getSupabaseUserId()
        val client = SupabaseClientProvider.client
        lifecycleScope.launch {
            val adminCheck = client != null && userId.isNotBlank() &&
                SupabaseRepository(client).isAdmin(userId)
            when {
                adminCheck                        -> launchAdmin()
                repository.isOnboardingComplete() -> launchMain()
                else                              -> navigateTo(LanguageSelectionFragment(), addToBackStack = false)
            }
        }
    }

    fun onLanguageSelected(languageCode: String) {
        repository.setLanguage(languageCode)
        // Recreates this and every future Activity with the chosen locale applied;
        // AppLocalesMetadataHolderService (see manifest) persists it across restarts.
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageCode))
        navigateTo(NameFragment(), addToBackStack = false)
    }

    fun onNameCompleted(name: String?) {
        if (!name.isNullOrBlank()) repository.setUserName(name)
        navigateTo(AboutYouFragment())
    }

    fun onAboutYouCompleted(age: Int?, gender: String?) {
        if (age != null && age > 0) repository.setUserAge(age)
        if (!gender.isNullOrBlank()) repository.setUserGender(gender)
        navigateTo(ConnectDeviceFragment())
    }

    fun onDeviceConnected() {
        repository.setDeviceConnected(true)
        navigateTo(LocationPermissionFragment())
    }

    fun onLocationPermissionDone() {
        navigateTo(NotificationPermissionFragment())
    }

    fun onNotificationPermissionDone() {
        navigateTo(EmergencyContactFragment())
    }

    fun onEmergencyContactDone(name: String?, phone: String?, name2: String?, phone2: String?) {
        if (!name.isNullOrBlank()) repository.setEmergencyContact(name, phone ?: "")
        if (!name2.isNullOrBlank()) repository.setSecondContact(name2, phone2 ?: "")
        repository.setOnboardingComplete()
        syncUserToSupabase()
        launchMain()
    }

    private fun syncUserToSupabase() {
        val client = SupabaseClientProvider.client ?: return
        val deviceId = repository.getSupabaseUserId().ifBlank { repository.getOrCreateDeviceId() }
        val repo = SupabaseRepository(client)
        lifecycleScope.launch {
            repo.syncUser(
                deviceId = deviceId,
                name = repository.getUserName().ifBlank { null },
                age = repository.getUserAge().takeIf { it > 0 },
                gender = repository.getUserGender().ifBlank { null },
                language = repository.getLanguage(),
                contact1Name = repository.getEmergencyContactName().ifBlank { null },
                contact1Phone = repository.getEmergencyContactPhone().ifBlank { null },
                contact2Name = repository.getSecondContactName().ifBlank { null },
                contact2Phone = repository.getSecondContactPhone().ifBlank { null }
            )
            repo.syncRiskProfile(deviceId, repository.getRiskProfile())
        }
    }

    private fun navigateTo(fragment: Fragment, addToBackStack: Boolean = true) {
        val tx = supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.slide_in_right, R.anim.slide_out_left,
                R.anim.slide_in_left, R.anim.slide_out_right
            )
            .replace(R.id.onboardingContainer, fragment)
        if (addToBackStack) tx.addToBackStack(null)
        tx.commit()
    }

    private fun launchMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun launchAdmin() {
        startActivity(Intent(this, AdminActivity::class.java))
        finish()
    }
}
