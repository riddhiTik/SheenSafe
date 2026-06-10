package org.kashmir.heatguard.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kashmir.heatguard.BuildConfig
import org.kashmir.heatguard.R
import java.net.HttpURLConnection
import java.net.URL

/**
 * Admin authentication gate. Uses raw HTTP to the Supabase Auth REST endpoint so the
 * regular user's Supabase SDK session is never replaced. The access token is discarded
 * after the admin_users table check — nothing is persisted.
 */
class AdminLoginActivity : AppCompatActivity() {

    private var passwordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_login)

        val etEmail = findViewById<EditText>(R.id.etAdminEmail)
        val etPassword = findViewById<EditText>(R.id.etAdminPassword)
        val btnLogin = findViewById<MaterialButton>(R.id.btnAdminLogin)
        val btnToggle = findViewById<ImageButton>(R.id.btnToggleAdminPassword)
        val tvError = findViewById<TextView>(R.id.tvAdminError)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        btnToggle.setOnClickListener {
            passwordVisible = !passwordVisible
            etPassword.inputType = if (passwordVisible)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            etPassword.setSelection(etPassword.text.length)
            btnToggle.setImageResource(
                if (passwordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility
            )
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()
            tvError.visibility = View.GONE

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showError(tvError, getString(R.string.login_error_invalid_email))
                return@setOnClickListener
            }
            if (password.isBlank()) {
                showError(tvError, getString(R.string.login_error_short_password))
                return@setOnClickListener
            }

            setLoading(true, btnLogin, progressBar)
            attemptAdminLogin(email, password, tvError, btnLogin, progressBar)
        }
    }

    private fun attemptAdminLogin(
        email: String,
        password: String,
        tvError: TextView,
        btnLogin: MaterialButton,
        progressBar: ProgressBar
    ) {
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) {
            setLoading(false, btnLogin, progressBar)
            showError(tvError, getString(R.string.login_error_not_configured))
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Step 1: authenticate via Supabase Auth REST — no SDK session is touched
                val authBody = """{"email":"$email","password":"$password"}"""
                val authUrl = "${BuildConfig.SUPABASE_URL}/auth/v1/token?grant_type=password"
                val authConn = (URL(authUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    doOutput = true
                    outputStream.use { it.write(authBody.toByteArray()) }
                }

                if (authConn.responseCode != 200) {
                    withContext(Dispatchers.Main) {
                        setLoading(false, btnLogin, progressBar)
                        showError(tvError, getString(R.string.login_error_failed))
                    }
                    return@launch
                }

                val authJson = authConn.inputStream.bufferedReader().readText()
                val accessToken = extractJsonString(authJson, "access_token")
                val userId = extractJsonString(authJson, "id")

                if (accessToken == null || userId == null) {
                    withContext(Dispatchers.Main) {
                        setLoading(false, btnLogin, progressBar)
                        showError(tvError, getString(R.string.login_error_failed))
                    }
                    return@launch
                }

                // Step 2: check admin_users table using the temporary access token
                val adminUrl = "${BuildConfig.SUPABASE_URL}/rest/v1/admin_users?user_id=eq.$userId&select=user_id&limit=1"
                val adminConn = (URL(adminUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    connectTimeout = 10_000
                    readTimeout = 10_000
                }

                val adminJson = adminConn.inputStream.bufferedReader().readText().trim()
                val isAdmin = adminJson != "[]" && adminJson.isNotBlank()

                withContext(Dispatchers.Main) {
                    setLoading(false, btnLogin, progressBar)
                    if (isAdmin) {
                        startActivity(Intent(this@AdminLoginActivity, AdminActivity::class.java))
                    } else {
                        showError(tvError, getString(R.string.admin_login_denied))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoading(false, btnLogin, progressBar)
                    showError(tvError, getString(R.string.login_error_failed))
                }
            }
        }
    }

    /** Extracts the first occurrence of "key":"value" from a JSON string without a library. */
    private fun extractJsonString(json: String, key: String): String? {
        val pattern = Regex(""""$key"\s*:\s*"([^"]+)"""")
        return pattern.find(json)?.groupValues?.getOrNull(1)
    }

    private fun showError(tvError: TextView, message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }

    private fun setLoading(loading: Boolean, btnLogin: MaterialButton, progressBar: ProgressBar) {
        btnLogin.isEnabled = !loading
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }
}
