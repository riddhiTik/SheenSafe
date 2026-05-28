package org.kashmir.heatguard.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
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
        // TODO: navigate to next onboarding step (device connection)
        // For now, complete onboarding and go to the main app.
        repository.setOnboardingComplete()
        launchMain()
    }

    private fun launchMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
