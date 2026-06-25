package org.kashmir.heatguard.wear

import android.app.ActivityManager
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.wearable.MessageEvent
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives WatchCommandService.onMessageReceived() directly with a fake MessageEvent,
 * bypassing the real Wearable Data Layer entirely. This verifies our component's
 * own logic (path routing -> handleStart() -> either WatchSensorService or
 * PermissionActivity, depending on BODY_SENSORS grant state) is correct,
 * independent of whether Google Play Services actually delivers a real cross-device
 * message — that delivery step has been separately confirmed broken on this device
 * (zero WatchSensorService/WatchCommandService process history prior to this test;
 * MessageClient.sendMessage to the local node itself also fails with
 * TARGET_NODE_NOT_CONNECTED, which is expected since the Data Layer only routes to
 * other peer nodes, not self).
 *
 * Either WatchSensorService starting (permission already granted) or PermissionActivity
 * launching (permission not yet granted) counts as proof onMessageReceived() correctly
 * handled the /sheensafe-cmd/start path — both are valid real-world outcomes.
 */
@RunWith(AndroidJUnit4::class)
class WatchCommandServiceLoopbackTest {

    private class FakeMessageEvent(private val path: String) : MessageEvent {
        override fun getRequestId() = 0
        override fun getPath() = path
        override fun getSourceNodeId() = "fake-source-node"
        override fun getData() = ByteArray(0)
    }

    @Test
    fun onMessageReceived_start_handlesMessageCorrectly() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        context.stopService(Intent(context, WatchSensorService::class.java))
        Thread.sleep(500)
        assertTrue("Precondition failed: WatchSensorService already running before test", !isServiceRunning(context))

        val monitor = Instrumentation.ActivityMonitor(PermissionActivity::class.java.name, null, false)
        instrumentation.addMonitor(monitor)

        val service = WatchCommandService()
        val attachBaseContext = ContextWrapper::class.java
            .getDeclaredMethod("attachBaseContext", Context::class.java)
            .apply { isAccessible = true }
        attachBaseContext.invoke(service, context)

        service.onMessageReceived(FakeMessageEvent(WatchCommandService.PATH_START))

        var sensorServiceStarted = false
        repeat(6) {
            Thread.sleep(500)
            if (isServiceRunning(context)) sensorServiceStarted = true
        }
        val permissionActivityLaunched = monitor.hits > 0
        instrumentation.removeMonitor(monitor)

        assertTrue(
            "WatchCommandService.onMessageReceived() handled neither branch for " +
                "${WatchCommandService.PATH_START}: WatchSensorService did not start " +
                "(started=$sensorServiceStarted) and PermissionActivity was not launched " +
                "(launched=$permissionActivityLaunched)",
            sensorServiceStarted || permissionActivityLaunched
        )

        context.stopService(Intent(context, WatchSensorService::class.java))
    }

    private fun isServiceRunning(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        return am.getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == WatchSensorService::class.java.name }
    }
}
