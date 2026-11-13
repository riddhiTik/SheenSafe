package org.kashmir.heatguard.wear

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Invisible one-shot activity whose only job is to show the BODY_SENSORS permission
 * dialog. Once the user taps Allow, it starts WatchSensorService and finishes itself.
 * If denied, it finishes silently — the phone can retry later.
 *
 * This is the only moment the user ever interacts with anything on the watch for
 * SheenSafe. After this, everything is silent background collection.
 */
class PermissionActivity : ComponentActivity() {

    companion object {
        private const val TAG = "PermissionActivity"
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it } || SensorPermissions.anyGranted(this)) {
            Log.i(TAG, "Sensor permission granted ($results) — starting sensor service")
            startForegroundService(Intent(this, WatchSensorService::class.java))
        } else {
            Log.w(TAG, "Sensor permissions denied: $results")
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (SensorPermissions.anyGranted(this)) {
            startForegroundService(Intent(this, WatchSensorService::class.java))
            finish()
        } else {
            permissionLauncher.launch(SensorPermissions.ALL)
        }
    }
}
