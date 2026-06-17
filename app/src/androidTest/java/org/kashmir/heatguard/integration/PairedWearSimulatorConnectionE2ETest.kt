package org.kashmir.heatguard.integration

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.kashmir.heatguard.HeatGuardApplication
import org.kashmir.heatguard.R
import org.kashmir.heatguard.core.model.AgeBand
import org.kashmir.heatguard.core.model.Baseline
import org.kashmir.heatguard.core.model.OfficialHeatWarning
import org.kashmir.heatguard.core.model.RiskProfile
import org.kashmir.heatguard.core.model.SensorSample
import org.kashmir.heatguard.data.AppRepository
import org.kashmir.heatguard.data.SupabaseClientProvider
import org.kashmir.heatguard.service.HeatMonitoringService
import org.kashmir.heatguard.ui.MainActivity

@LargeTest
@RunWith(AndroidJUnit4::class)
class PairedWearSimulatorConnectionE2ETest {

    private lateinit var app: HeatGuardApplication
    private lateinit var context: Context
    private lateinit var repository: AppRepository

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        context = app
        repository = AppRepository(context)

        context.stopService(Intent(context, HeatMonitoringService::class.java))
        app.activeRingDataSource = null
        app.wearDataSource.clearForTesting()
        SupabaseClientProvider.setClientForTesting(null)
        repository.deleteAllData()
        grantPhoneRuntimePermissions()
        configureCompletedWatch6Profile()
    }

    @After
    fun tearDown() {
        sendStopToNearbyWearSimulators()
        context.stopService(Intent(context, HeatMonitoringService::class.java))
        app.activeRingDataSource = null
        app.wearDataSource.clearForTesting()
        SupabaseClientProvider.clearClientForTesting()
        repository.deleteAllData()
    }

    @Test
    fun pairedWearSimulatorApprovesAndStreamsWatchOriginatedReadingToPhoneDashboard() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val nearbyNodes = requireNearbyWearSimulatorNodes()

            waitUntil {
                assertSame(app.wearDataSource, app.activeRingDataSource)
            }

            nearbyNodes.forEach { node -> sendWearMessage(node, WATCH_STOP_PATH) }
            Thread.sleep(WATCH_STOP_DRAIN_MILLIS)
            app.wearDataSource.clearForTesting()

            val commandSentAt = System.currentTimeMillis()
            nearbyNodes.forEach { node -> sendWearMessage(node, WATCH_START_SIMULATED_PATH) }

            val sample = awaitFreshWatchSimulatorSample(commandSentAt)
            assertEquals(EXPECTED_SIMULATED_HEART_RATE, sample.heartRateBpm)
            assertEquals(EXPECTED_SIMULATED_SKIN_TEMP, sample.skinTemperatureC)

            waitUntil(timeoutMillis = 15_000) {
                onView(withId(R.id.tvHeartRate))
                    .check(matches(withText(EXPECTED_SIMULATED_HEART_RATE.toString())))
                onView(withId(R.id.tvBodyTemp))
                    .check(matches(withText("%.1f".format(EXPECTED_SIMULATED_SKIN_TEMP))))
            }
        }
    }

    private fun configureCompletedWatch6Profile() {
        repository.setOnboardingComplete(true)
        repository.setUserName("Wear Simulator E2E")
        repository.setUserAge(67)
        repository.setUserGender("OTHER")
        repository.setEmergencyContact("Responder", "+15550100")
        repository.setBaseline(
            Baseline(
                typicalRestingHeartRateBpm = 76,
                typicalSkinTemperatureC = 34.2,
                skinTemperatureStdDevC = 0.5
            )
        )
        repository.setRiskProfile(
            RiskProfile(
                ageBand = AgeBand.OLDER_ADULT,
                chronicIllness = true,
                limitedCoolingAccess = true,
                daysAcclimatized = 2
            )
        )
        repository.setManualWeather(
            tempC = 34.5,
            relativeHumidityPercent = 52.0,
            directSun = false,
            warning = OfficialHeatWarning.NONE
        )
        repository.setDeviceConnected(true)
        repository.setUseWatch6(true)
    }

    private fun requireNearbyWearSimulatorNodes(): List<Node> {
        val nodes = try {
            Tasks.await(
                Wearable.getNodeClient(context).connectedNodes,
                TASK_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            )
        } catch (error: Exception) {
            throw AssertionError(
                "Wearable Data Layer API is unavailable on this phone test device. Use Android Studio's " +
                    "Wear OS Pairing Assistant to pair a phone emulator/device with a Wear OS emulator/device, " +
                    "then rerun this test. The test intentionally fails instead of spoofing phone-side data.",
                error
            )
        }
        val nearby = nodes.filter { it.isNearby }
        assertTrue(
            nearby.isNotEmpty(),
            "No nearby paired Wear OS simulator/device found. Start the phone emulator and Wear OS " +
                "emulator in Android Studio, pair them through the Wear OS pairing assistant, install " +
                "the :wear debug app on the watch simulator, keep it awake, and approve BODY_SENSORS " +
                "on the watch simulator when prompted. Connected nodes: " +
                nodes.joinToString { node -> "${node.displayName}:${node.id}:nearby=${node.isNearby}" }
        )
        return nearby
    }

    private fun sendWearMessage(node: Node, path: String) {
        Tasks.await(
            Wearable.getMessageClient(context).sendMessage(node.id, path, ByteArray(0)),
            TASK_TIMEOUT_SECONDS,
            TimeUnit.SECONDS
        )
    }

    private fun awaitFreshWatchSimulatorSample(commandSentAtMillis: Long): SensorSample {
        val deadline = System.currentTimeMillis() + WATCH_READING_TIMEOUT_MILLIS
        var latest: SensorSample? = null

        while (System.currentTimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            latest = app.wearDataSource.latestSample()
            val closeToCommand = (latest?.timestampMillis ?: Long.MIN_VALUE) >=
                commandSentAtMillis - WATCH_CLOCK_SKEW_TOLERANCE_MILLIS
            if (latest != null &&
                closeToCommand &&
                latest.connected &&
                latest.heartRateBpm == EXPECTED_SIMULATED_HEART_RATE &&
                latest.skinTemperatureC == EXPECTED_SIMULATED_SKIN_TEMP
            ) {
                return latest
            }
            Thread.sleep(1_000)
        }

        throw AssertionError(
            "No fresh watch-originated simulator reading arrived on /sheensafe/watch-reading within " +
                "${WATCH_READING_TIMEOUT_MILLIS / 1_000}s after /sheensafe/start-simulated. " +
                "This test intentionally does not spoof phone-side data. Confirm the Wear emulator is paired, " +
                "the :wear debug app is installed, and BODY_SENSORS was approved on the watch simulator. " +
                "Last phone-side sample: $latest"
        )
    }

    private fun sendStopToNearbyWearSimulators() {
        runCatching {
            requireNearbyWearSimulatorNodes().forEach { node -> sendWearMessage(node, WATCH_STOP_PATH) }
        }
    }

    private fun grantPhoneRuntimePermissions() {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        permissions.forEach { permission ->
            runCatching {
                instrumentation.uiAutomation.grantRuntimePermission(context.packageName, permission)
            }
        }
    }

    private fun waitUntil(timeoutMillis: Long = 5_000, assertion: () -> Unit) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var lastError: Throwable? = null

        while (System.currentTimeMillis() < deadline) {
            try {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                assertion()
                return
            } catch (error: Throwable) {
                lastError = error
                Thread.sleep(100)
            }
        }

        throw AssertionError("Condition was not met within ${timeoutMillis}ms", lastError)
    }

    private companion object {
        private const val WATCH_START_SIMULATED_PATH = "/sheensafe/start-simulated"
        private const val WATCH_STOP_PATH = "/sheensafe/stop"
        private const val TASK_TIMEOUT_SECONDS = 15L
        private const val WATCH_READING_TIMEOUT_MILLIS = 180_000L
        private const val WATCH_CLOCK_SKEW_TOLERANCE_MILLIS = 10_000L
        private const val WATCH_STOP_DRAIN_MILLIS = 2_000L
        private const val EXPECTED_SIMULATED_HEART_RATE = 101
        private const val EXPECTED_SIMULATED_SKIN_TEMP = 36.0
    }
}
