package org.kashmir.heatguard.wear

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Permissions the sensor service needs. A `health` foreground service requires at least one
 * granted sensor permission, and which ones count depends on the Wear OS version: BODY_SENSORS
 * on Android 14/15, while Android 16+ also wants the health.* permissions or ACTIVITY_RECOGNITION.
 * We request all of them and start the service when any one is granted. Permissions the OS
 * doesn't know about are simply reported as denied.
 */
object SensorPermissions {
    const val READ_HEART_RATE = "android.permission.health.READ_HEART_RATE"

    val ALL = arrayOf(
        Manifest.permission.BODY_SENSORS,
        Manifest.permission.ACTIVITY_RECOGNITION,
        READ_HEART_RATE
    )

    fun anyGranted(context: Context): Boolean = ALL.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}
