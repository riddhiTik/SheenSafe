package org.kashmir.heatguard.wear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Receives commands from the phone app via the Wearable Data Layer.
 *
 * Wear OS keeps this service alive as a system component — it wakes up in response
 * to incoming messages even when nothing else is running on the watch. This is what
 * allows the phone to control the watch-side sensor collection without the user
 * ever opening an app on the watch.
 *
 * Handled paths:
 *   /sheensafe/start  — start sensor collection (grants permission first if needed)
 *   /sheensafe/stop   — stop sensor collection
 */
class WatchCommandService : WearableListenerService() {

    companion object {
        private const val TAG = "WatchCommandService"
        const val PATH_START = "/sheensafe/start"
        const val PATH_STOP = "/sheensafe/stop"
    }

    override fun onMessageReceived(event: MessageEvent) {
        Log.d(TAG, "Message received: ${event.path}")
        when (event.path) {
            PATH_START -> handleStart()
            PATH_STOP  -> handleStop()
        }
    }

    private fun handleStart() {
        if (hasBodySensorsPermission()) {
            startSensorService()
        } else {
            // Launch the one-shot permission activity. After the user grants Body Sensors,
            // PermissionActivity starts WatchSensorService and finishes itself.
            val intent = Intent(this, PermissionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    private fun handleStop() {
        stopService(Intent(this, WatchSensorService::class.java))
        Log.i(TAG, "WatchSensorService stopped by phone command")
    }

    private fun startSensorService() {
        startForegroundService(Intent(this, WatchSensorService::class.java))
        Log.i(TAG, "WatchSensorService started by phone command")
    }

    private fun hasBodySensorsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) ==
            PackageManager.PERMISSION_GRANTED
}
