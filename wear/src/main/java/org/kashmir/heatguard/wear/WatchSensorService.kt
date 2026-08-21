package org.kashmir.heatguard.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.BatteryManager
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
 *  Samsung Health Sensor SDK callbacks → HeartRateCollector / SkinTemperatureCollector
 *      → dispatchReading()
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

    private var healthTrackingService: HealthTrackingService? = null
    private var hrCollector: HeartRateCollector? = null
    private var skinTempCollector: SkinTemperatureCollector? = null

    @Volatile private var latestHr: Int? = null
    @Volatile private var latestTemp: Float? = null
    @Volatile private var latestAmbientTemp: Float? = null

    private val connectionListener = object : ConnectionListener {
        override fun onConnectionSuccess() {
            Log.i(TAG, "HealthTrackingService connected")
            val service = healthTrackingService ?: return

            val supported: List<HealthTrackerType> = try {
                service.getTrackingCapability().supportHealthTrackerTypes
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query tracker capabilities: ${e.message}")
                emptyList()
            }
            Log.i(TAG, "Supported tracker types: $supported")

            hrCollector = HeartRateCollector(service, supported) { hr ->
                latestHr = hr
                dispatchReading()
            }.also { it.start() }

            skinTempCollector = SkinTemperatureCollector(service, supported) { temp, ambient ->
                latestTemp = temp
                latestAmbientTemp = ambient
                dispatchReading()
            }.also { it.start() }
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
        hrCollector?.stop()
        skinTempCollector?.stop()
        healthTrackingService?.disconnectService()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dispatchReading() {
        val hr = latestHr
        val temp = latestTemp
        val ambient = latestAmbientTemp
        if (hr == null && temp == null) return

        val batteryMgr = getSystemService(BATTERY_SERVICE) as BatteryManager
        val batteryPct = batteryMgr.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val json = JSONObject().apply {
            if (hr != null) put("heartRateBpm", hr) else put("heartRateBpm", JSONObject.NULL)
            if (temp != null) put("skinTempC", temp.toDouble()) else put("skinTempC", JSONObject.NULL)
            if (ambient != null) put("onDeviceAmbientTempC", ambient.toDouble()) else put("onDeviceAmbientTempC", JSONObject.NULL)
            put("batteryPercent", batteryPct)
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

    // ------------------------------------------------------------------ Collectors

    private inner class HeartRateCollector(
        private val service: HealthTrackingService,
        private val supportedTypes: List<HealthTrackerType>,
        private val onReading: (hr: Int) -> Unit
    ) {
        private val collectorTag = "HRCollector"
        private var tracker: HealthTracker? = null

        fun start() {
            if (!supportedTypes.contains(HealthTrackerType.HEART_RATE_CONTINUOUS)) {
                Log.w(TAG, "$collectorTag: HEART_RATE_CONTINUOUS not supported on this device")
                return
            }
            try {
                val t = service.getHealthTracker(HealthTrackerType.HEART_RATE_CONTINUOUS)
                tracker = t
                t.setEventListener(object : HealthTracker.TrackerEventListener {
                    override fun onDataReceived(list: List<DataPoint>) {
                        list.lastOrNull()?.let { dp ->
                            val hr: Int = dp.getValue(ValueKey.HeartRateSet.HEART_RATE) ?: return@let
                            val status: Int = dp.getValue(ValueKey.HeartRateSet.HEART_RATE_STATUS) ?: 0
                            if (status != 0 && hr > 0) {
                                Log.d(TAG, "$collectorTag: hr=$hr status=$status")
                                onReading(hr)
                            }
                        }
                    }
                    override fun onFlushCompleted() = Unit
                    override fun onError(e: HealthTracker.TrackerError) {
                        Log.e(TAG, "$collectorTag error: $e")
                    }
                })
                Log.i(TAG, "$collectorTag: tracker started")
            } catch (e: Exception) {
                Log.e(TAG, "$collectorTag: failed to start tracker: ${e.message}")
            }
        }

        fun stop() {
            tracker?.unsetEventListener()
            tracker = null
        }
    }

    private inner class SkinTemperatureCollector(
        private val service: HealthTrackingService,
        private val supportedTypes: List<HealthTrackerType>,
        private val onReading: (temp: Float, ambient: Float?) -> Unit
    ) {
        private val collectorTag = "SkinTempCollector"
        private var tracker: HealthTracker? = null
        private var flushJob: Job? = null

        fun start() {
            if (!supportedTypes.contains(HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS)) {
                Log.w(TAG, "$collectorTag: SKIN_TEMPERATURE_CONTINUOUS not supported on this device")
                return
            }
            try {
                val t = service.getHealthTracker(HealthTrackerType.SKIN_TEMPERATURE_CONTINUOUS)
                tracker = t
                t.setEventListener(object : HealthTracker.TrackerEventListener {
                    override fun onDataReceived(list: List<DataPoint>) {
                        // Log and process EVERY datapoint — not just the last — so live logs
                        // can be used to determine real STATUS semantics after the fact.
                        list.forEach { dp ->
                            val obj: Float? = dp.getValue(ValueKey.SkinTemperatureSet.OBJECT_TEMPERATURE)
                            val amb: Float? = dp.getValue(ValueKey.SkinTemperatureSet.AMBIENT_TEMPERATURE)
                            val status: Int = dp.getValue(ValueKey.SkinTemperatureSet.STATUS) ?: 0
                            val accepted = obj != null && isStatusAcceptable(status)
                            Log.d(TAG, "$collectorTag: object=$obj ambient=$amb status=$status accepted=$accepted")
                            if (accepted) {
                                onReading(obj!!, amb)
                            }
                        }
                    }
                    override fun onFlushCompleted() = Unit
                    override fun onError(e: HealthTracker.TrackerError) {
                        Log.w(TAG, "$collectorTag error: $e")
                    }
                })
                Log.i(TAG, "$collectorTag: tracker started")
                // Periodically flush buffered data so the Dashboard updates more frequently
                // than the tracker's natural ~8-minute internal batch interval. flush() asks
                // the SDK to deliver whatever it has accumulated immediately without waiting.
                flushJob = scope.launch {
                    while (true) {
                        delay(60_000L)
                        val flushed = tracker?.flush() ?: false
                        Log.d(TAG, "$collectorTag: flush() returned $flushed")
                    }
                }
            } catch (e: Exception) {
                // SKIN_TEMPERATURE_CONTINUOUS is unavailable on some Watch variants
                Log.w(TAG, "$collectorTag: tracker unavailable: ${e.message}")
            }
        }

        fun stop() {
            flushJob?.cancel()
            flushJob = null
            tracker?.unsetEventListener()
            tracker = null
        }

        // STATUS semantics for SkinTemperatureSet are still not officially documented by
        // Samsung, but live Galaxy Watch6 logs (2026-06-30, confirmed snug fit, HR reporting
        // normally at the same time) showed every datapoint at status=0 with physiologically
        // plausible object/ambient values (high-20s C) -- i.e. status=0 is the NORMAL status
        // for this sensor, the opposite of HeartRateSet's "0 = no contact" convention. No
        // non-zero status has been observed yet, so this remains provisional: revisit if a
        // future session logs a different status value alongside garbage/missing readings.
        private fun isStatusAcceptable(status: Int): Boolean = status == 0
    }

    // ------------------------------------------------------------------ Notification

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
