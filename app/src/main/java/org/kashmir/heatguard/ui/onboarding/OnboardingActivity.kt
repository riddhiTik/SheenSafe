package org.kashmir.heatguard.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import org.kashmir.heatguard.R
import org.kashmir.heatguard.data.AppRepository
import org.kashmir.heatguard.ui.MainActivity

class OnboardingActivity : AppCompatActivity() {

    private lateinit var repository: AppRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = AppRepository(this)

        if (repository.isOnboardingComplete()) {
            launchMain()
            return
        }

        setContentView(R.layout.activity_onboarding)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.onboardingContainer, LanguageSelectionFragment())
                .commit()
        }
    }

    fun onLanguageSelected(languageCode: String) {
        repository.setLanguage(languageCode)
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
        launchMain()
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
}
