package org.kashmir.heatguard.ring

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONException
import org.json.JSONObject
import org.kashmir.heatguard.HeatGuardApplication
import org.kashmir.heatguard.core.model.SensorSample

/**
 * Receives watch readings sent by WatchSensorService on the Galaxy Watch6.
 *
 * Expected message path: /sheensafe-data/watch-reading
 * Expected payload (JSON):
 *   { "heartRateBpm": 95, "skinTempC": 34.8, "timestampMillis": 1710000000000 }
 *
 * Null fields are encoded as JSON null and decoded back to Kotlin null correctly.
 *
 * Uses a distinct path prefix (/sheensafe-data) from the watch command path
 * (/sheensafe-cmd, see WatchCommandService) so the phone and watch listener
 * registrations never overlap under the same signing certificate.
 */
class SheenSafeWearListenerService : WearableListenerService() {

    companion object {
        private const val TAG = "WearListener"
        private const val MSG_PATH = "/sheensafe-data/watch-reading"
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != MSG_PATH) return

        val json = try {
            JSONObject(String(event.data, Charsets.UTF_8))
        } catch (e: JSONException) {
            Log.e(TAG, "Malformed watch message: ${e.message}")
            return
        }

        val sample = SensorSample(
            timestampMillis  = json.optLong("timestampMillis", System.currentTimeMillis()),
            heartRateBpm     = json.optIntOrNull("heartRateBpm"),
            skinTemperatureC = json.optDoubleOrNull("skinTempC"),
            connected        = true
        )

        Log.d(TAG, "Received watch reading: hr=${sample.heartRateBpm} temp=${sample.skinTemperatureC}")
        (application as HeatGuardApplication).wearDataSource.onWatchReading(sample)
    }

    // Optional enhancement (Milestone J): push a disconnected sample immediately so the
    // risk engine marks data stale on the next 5-min tick rather than waiting 15 minutes.
    override fun onPeerDisconnected(peer: Node) {
        Log.i(TAG, "Watch disconnected: ${peer.displayName}")
        val disconnected = SensorSample(
            timestampMillis = System.currentTimeMillis(),
            connected = false
        )
        (application as HeatGuardApplication).wearDataSource.onWatchReading(disconnected)
    }

    // JSONObject.optInt() returns 0 for missing/null keys; we need null semantics
    // so that a missing heart rate doesn't register as 0 bpm in the risk engine.
    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (isNull(key) || !has(key)) null else optInt(key)

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (isNull(key) || !has(key)) null else optDouble(key)
}
