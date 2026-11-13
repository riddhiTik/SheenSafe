package org.kashmir.heatguard.ring

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.kashmir.heatguard.core.model.SensorSample
import org.kashmir.heatguard.core.ring.RingDataSource
import org.kashmir.heatguard.core.ring.RingSampleListener
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * RingDataSource backed by Android Health Connect. A wearable's own app writes heart rate into
 * Health Connect; this polls for the newest reading.
 *
 * The sample timestamp is the time the reading was *taken*, not when we read it, so
 * HeatRiskEngine's staleness check treats delayed syncs correctly. Skin temperature is not read:
 * Health Connect 1.1 has no skin-temperature record, so it stays null.
 */
class HealthConnectRingDataSource(private val context: Context) : RingDataSource {

    @Volatile private var latest: SensorSample? = null
    private var scope: CoroutineScope? = null
    private var job: Job? = null

    override fun start(listener: RingSampleListener) {
        stop()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        job = s.launch {
            while (isActive) {
                try {
                    readLatest()?.let { sample ->
                        latest = sample
                        listener.onSample(sample)
                    }
                } catch (e: Exception) {
                    // Permission revoked, Health Connect missing/updating, etc. Keep the last sample.
                    Log.w(TAG, "Health Connect read failed: ${e.message}")
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override fun stop() {
        job?.cancel()
        scope?.cancel()
        job = null
        scope = null
    }

    override fun latestSample(): SensorSample? = latest

    private suspend fun readLatest(): SensorSample? {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) return null
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        if (HealthPermission.getReadPermission(HeartRateRecord::class) !in granted) return null

        val now = Instant.now()
        val response = client.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(now.minus(LOOKBACK_MINUTES, ChronoUnit.MINUTES), now)
            )
        )
        val newest = response.records
            .flatMap { it.samples }
            .maxByOrNull { it.time } ?: return null
        return SensorSample(
            timestampMillis = newest.time.toEpochMilli(),
            heartRateBpm = newest.beatsPerMinute.toInt(),
            connected = true
        )
    }

    companion object {
        private const val TAG = "HealthConnectSource"
        private const val POLL_INTERVAL_MS = 60_000L
        private const val LOOKBACK_MINUTES = 30L
    }
}
