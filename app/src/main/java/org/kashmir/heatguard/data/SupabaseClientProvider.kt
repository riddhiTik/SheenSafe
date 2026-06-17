package org.kashmir.heatguard.data

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import org.kashmir.heatguard.BuildConfig

object SupabaseClientProvider {
    @Volatile
    private var testClientOverrideSet = false

    @Volatile
    private var testClientOverride: SupabaseClient? = null

    private val configuredClient by lazy {
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) {
            null
        } else {
            createSupabaseClient(
                supabaseUrl = BuildConfig.SUPABASE_URL,
                supabaseKey = BuildConfig.SUPABASE_ANON_KEY
            ) {
                install(Postgrest)
                install(Auth)
            }
        }
    }

    val client: SupabaseClient?
        get() = if (testClientOverrideSet) testClientOverride else configuredClient

    fun setClientForTesting(client: SupabaseClient?) {
        testClientOverride = client
        testClientOverrideSet = true
    }

    fun clearClientForTesting() {
        testClientOverride = null
        testClientOverrideSet = false
    }
}
