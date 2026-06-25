package org.kashmir.heatguard.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.google.android.gms.wearable.Wearable
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/**
 * Foreground service that owns the Samsung Health Sensor SDK connection lifecycle.
 *
 * Prerequisites:
 *  - Samsung Health Sensor SDK .aar is in wear/libs/
 *  - SDK Debug Mode enabled on Watch6: Samsung Health → About → SDK Debug Mode
 *
 * Data flow:
 *  Samsung Health Sensor SDK callbacks → dispatchReading()
 *      → Wearable MessageClient → path /sheensafe-data/watch-reading
 *      → SheenSafeWearListenerService (phone)
 */
class WatchSensorService : Service() {

    companion object {
        private const val TAG = "WatchSensorService"
        const val MSG_PATH = "/sheensafe-data/watch-reading"
        private const val CHANNEL_ID = "sheensafe_sensor"
        private const val NOTIF_ID = 1
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Held before connectService() is called; used inside onConnectionSuccess()
    private var healthTrackingService: HealthTrackingService? = null
    private var hrTracker: HealthTracker? = null
    private var skinTempTracker: HealthTracker? = null

    @Volatile private var latestHr: Int? = null
    // TODO: skin temperature — latestTemp feeds dispatchReading() via startSkinTempTracker()
    @Volatile private var latestTemp: Float? = null

    private val connectionListener = object : ConnectionListener {
        override fun onConnectionSuccess() {
            Log.i(TAG, "HealthTrackingService connected")
            startHrTracker()
        }

        override fun onConnectionEnded() {
            Log.w(TAG, "HealthTrackingService connection ended")
        }

        override fun onConnectionFailed(e: HealthTrackerException) {
            Log.e(TAG, "Connection failed (code ${e.errorCode}): ${e.message}")
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildForegroundNotification())

        val service = HealthTrackingService(connectionListener, applicationContext)
        healthTrackingService = service
        service.connectService()
        Log.i(TAG, "WatchSensorService started, connecting to HealthTrackingService")
    }

    override fun onDestroy() {
        hrTracker?.unsetEventListener()
        skinTempTracker?.unsetEventListener()
        healthTrackingService?.disconnectService()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ------------------------------------------------------------------ Milestone D: HR tracker

    private fun startHrTracker() {
        val service = healthTrackingService ?: return
        try {
            val tracker = service.getHealthTracker(HealthTrackerType.HEART_RATE_CONTINUOUS)
            hrTracker = tracker
            tracker.setEventListener(object : HealthTracker.TrackerEventListener {
                override fun onDataReceived(list: List<DataPoint>) {
                    list.lastOrNull()?.let { dp ->
                        val hr: Int = dp.getValue(ValueKey.HeartRateSet.HEART_RATE) ?: return@let
                        val status: Int = dp.getValue(ValueKey.HeartRateSet.HEART_RATE_STATUS) ?: 0
                        // Status 0 = no contact / initializing; any other status has a reading
                        if (status != 0 && hr > 0) {
                            Log.d(TAG, "HR=$hr status=$status")
                            latestHr = hr
                            dispatchReading()
                        }
                    }
                }

                override fun onFlushCompleted() = Unit

                override fun onError(e: HealthTracker.TrackerError) {
                    Log.e(TAG, "HR tracker error: $e")
                }
            })
            Log.i(TAG, "HR tracker started")
            // TODO: skin temperature — remove this call if the Watch variant lacks a skin temp sensor
            startSkinTempTracker(service)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start HR tracker: ${e.message}")
        }
    }

    // ------------------------------------------------------------------ Milestone E: Skin temp tracker

    // TODO: skin temperature — validate OBJECT_TEMPERATURE field against Samsung SDK changelog before pilot
    private fun startSkinTempTracker(service: HealthTrackingService) {
        try {
            val tracker = service.getHealthTracker(HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS)
            skinTempTracker = tracker
            tracker.setEventListener(object : HealthTracker.TrackerEventListener {
                override fun onDataReceived(list: List<DataPoint>) {
                    list.lastOrNull()?.let { dp ->
                        val temp: Float = dp.getValue(ValueKey.SkinTemperatureSet.OBJECT_TEMPERATURE)
                            ?: return@let
                        if (temp > 0f) {
                            Log.d(TAG, "SkinTemp=$temp°C")
                            latestTemp = temp
                            dispatchReading()
                        }
                    }
                }

                override fun onFlushCompleted() = Unit

                override fun onError(e: HealthTracker.TrackerError) {
                    Log.w(TAG, "Skin temp tracker error: $e")
                }
            })
            Log.i(TAG, "Skin temp tracker started")
        } catch (e: Exception) {
            // SKIN_TEMPERATURE_CONTINUOUS is unavailable on some Watch variants
            Log.w(TAG, "Skin temp tracker unavailable: ${e.message}")
        }
    }

    // ------------------------------------------------------------------ Message dispatch (Milestone F)

    private fun dispatchReading() {
        val hr = latestHr
        val temp = latestTemp
        if (hr == null && temp == null) return

        val json = JSONObject().apply {
            if (hr != null) put("heartRateBpm", hr) else put("heartRateBpm", JSONObject.NULL)
            if (temp != null) put("skinTempC", temp.toDouble()) else put("skinTempC", JSONObject.NULL)
            put("timestampMillis", System.currentTimeMillis())
        }.toString()

        scope.launch {
            try {
                val nodes = Wearable.getNodeClient(this@WatchSensorService).connectedNodes.await()
                nodes.forEach { node ->
                    Wearable.getMessageClient(this@WatchSensorService)
                        .sendMessage(node.id, MSG_PATH, json.toByteArray(Charsets.UTF_8))
                        .await()
                    Log.d(TAG, "Sent to ${node.displayName}: $json")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send watch reading: ${e.message}")
            }
        }
    }

    private fun buildForegroundNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "SheenSafe Sensor", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("SheenSafe active")
            .setContentText("Collecting heat risk data")
            .build()
    }
}
