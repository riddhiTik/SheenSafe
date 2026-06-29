package org.kashmir.heatguard.wear

import android.app.ActivityManager
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Writes a real "start" command DataItem via DataClient.putDataItem() and lets the
 * actual manifest-registered WatchCommandService.onDataChanged() dispatch handle it —
 * no reflection, no faked events. This is possible (unlike the previous MessageClient-based
 * version of this test, which had to invoke onMessageReceived() directly via reflection)
 * because DataItem writes are visible to local listeners immediately: the local node is
 * always part of the eventually-consistent Data Layer store, unlike MessageClient.sendMessage()
 * which fails with TARGET_NODE_NOT_CONNECTED when targeting the local node.
 *
 * IMPORTANT SCOPE LIMITATION: this test proves the manifest registration and onDataChanged()
 * parsing logic are wired correctly for LOCAL delivery. It does NOT and CANNOT prove
 * cross-device delivery works, because local DataItem writes never touch the cross-device
 * AppKey resolution step that was separately confirmed broken (see HeatMonitoringService /
 * WatchCommandService doc comments). Real verification of the cross-device fix requires a
 * live two-device logcat check showing "WatchCommandService: DataItem command received: start"
 * on the watch in response to a phone-side write.
 *
 * Either WatchSensorService starting (permission already granted) or PermissionActivity
 * launching (permission not yet granted) counts as proof onDataChanged() correctly handled
 * the command — both are valid real-world outcomes.
 */
@RunWith(AndroidJUnit4::class)
class WatchCommandServiceLoopbackTest {

    @Test
    fun localDataItemWrite_start_handlesCommandCorrectly() {
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext

            context.stopService(Intent(context, WatchSensorService::class.java))
            Thread.sleep(500)
            assertTrue("Precondition failed: WatchSensorService already running before test", !isServiceRunning(context))

            val monitor = Instrumentation.ActivityMonitor(PermissionActivity::class.java.name, null, false)
            instrumentation.addMonitor(monitor)

            val request = PutDataMapRequest.create(WatchCommandService.COMMAND_PATH).apply {
                dataMap.putString(WatchCommandService.KEY_COMMAND, WatchCommandService.COMMAND_START)
                dataMap.putLong(WatchCommandService.KEY_TIMESTAMP, System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()

            var sensorServiceStarted = false
            repeat(6) {
                Thread.sleep(500)
                if (isServiceRunning(context)) sensorServiceStarted = true
            }
            val permissionActivityLaunched = monitor.hits > 0
            instrumentation.removeMonitor(monitor)

            assertTrue(
                "WatchCommandService.onDataChanged() handled neither branch for " +
                    "${WatchCommandService.COMMAND_PATH}: WatchSensorService did not start " +
                    "(started=$sensorServiceStarted) and PermissionActivity was not launched " +
                    "(launched=$permissionActivityLaunched)",
                sensorServiceStarted || permissionActivityLaunched
            )

            context.stopService(Intent(context, WatchSensorService::class.java))
        }
    }

    private fun isServiceRunning(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        return am.getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == WatchSensorService::class.java.name }
    }
}
