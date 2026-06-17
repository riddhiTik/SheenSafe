package org.kashmir.heatguard.wear

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Restarts WatchSensorService after the watch reboots. Only starts the service if
 * BODY_SENSORS was already granted — no permission dialogs are shown here, because
 * BroadcastReceivers can't show UI. If the permission was revoked, the phone will
 * need to send /sheensafe/start again to re-trigger the PermissionActivity.
 */
class WatchBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WatchBootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.BODY_SENSORS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            Log.i(TAG, "Boot completed — restarting WatchSensorService")
            context.startForegroundService(Intent(context, WatchSensorService::class.java))
        } else {
            Log.w(TAG, "Boot completed — BODY_SENSORS not granted, skipping service start")
        }
    }
}
