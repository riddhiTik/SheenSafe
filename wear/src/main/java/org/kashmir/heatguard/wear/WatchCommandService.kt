package org.kashmir.heatguard.wear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

/**
 * Receives commands from the phone app via the Wearable Data Layer's DataClient
 * (synced DataItem state), not MessageClient. DataItems are persistent and synced
 * by Google Play Services rather than fired-and-forgotten, which is more resilient
 * than point-to-point messages for "desired state" commands like start/stop.
 *
 * Wear OS keeps this service alive as a system component — it wakes up in response
 * to data changes even when nothing else is running on the watch. This is what
 * allows the phone to control the watch-side sensor collection without the user
 * ever opening an app on the watch.
 *
 * Single command path, distinguished by a "command" field in the DataMap (rather than
 * separate start/stop paths) because DataItem identity is keyed by path — one path
 * represents one synced "current command" slot. A "timestamp" field is always included
 * so writing the same command twice in a row still produces a content change and fires
 * onDataChanged() (DataItem change notification is content-diffed).
 */
class WatchCommandService : WearableListenerService() {

    companion object {
        private const val TAG = "WatchCommandService"
        const val COMMAND_PATH = "/sheensafe-cmd/control"
        const val KEY_COMMAND = "command"
        const val KEY_TIMESTAMP = "timestamp"
        const val COMMAND_START = "start"
        const val COMMAND_STOP = "stop"
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        try {
            for (event in dataEvents) {
                if (event.type != DataEvent.TYPE_CHANGED) continue
                val item = event.dataItem
                if (item.uri.path != COMMAND_PATH) continue

                val command = DataMapItem.fromDataItem(item).dataMap.getString(KEY_COMMAND)
                Log.d(TAG, "DataItem command received: $command")
                when (command) {
                    COMMAND_START -> handleStart()
                    COMMAND_STOP -> handleStop()
                }
            }
        } finally {
            dataEvents.release()
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
