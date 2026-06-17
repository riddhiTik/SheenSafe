package org.kashmir.heatguard.wear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Log.i(TAG, "BODY_SENSORS granted — starting sensor service")
            startForegroundService(Intent(this, WatchSensorService::class.java))
        } else {
            Log.w(TAG, "BODY_SENSORS denied")
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (checkSelfPermission(Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED) {
            startForegroundService(Intent(this, WatchSensorService::class.java))
            finish()
        } else {
            permissionLauncher.launch(Manifest.permission.BODY_SENSORS)
        }
    }
}
