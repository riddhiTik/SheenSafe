package org.kashmir.heatguard.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.location.LocationManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.kashmir.heatguard.alerts.EscalationManager
import org.kashmir.heatguard.alerts.NotificationHelper
import org.kashmir.heatguard.core.SystemClock
import org.kashmir.heatguard.core.escalation.EscalationEngine
import org.kashmir.heatguard.core.model.AgeBand
import org.kashmir.heatguard.core.pipeline.MonitoringPipeline
import org.kashmir.heatguard.core.ring.RingDataSource
import org.kashmir.heatguard.core.risk.HeatRiskEngine
import org.kashmir.heatguard.data.AppRepository
import org.kashmir.heatguard.data.SupabaseClientProvider
import org.kashmir.heatguard.data.SupabaseRepository
import com.google.android.gms.wearable.Wearable
import org.kashmir.heatguard.HeatGuardApplication
import org.kashmir.heatguard.ring.S5RingDataSource
import org.kashmir.heatguard.ring.SimulatedRingDataSource
import org.kashmir.heatguard.weather.ApiWeatherProvider
import org.kashmir.heatguard.weather.CachedWeatherProvider
import org.kashmir.heatguard.weather.FallbackWeatherProvider
import org.kashmir.heatguard.weather.ManualWeatherProvider
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class HeatMonitoringService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var executor: ScheduledExecutorService
    private lateinit var repository: AppRepository
    private lateinit var supabaseRepo: SupabaseRepository
    private lateinit var ringDataSource: RingDataSource
    private lateinit var escalationManager: EscalationManager
    private lateinit var pipeline: MonitoringPipeline
    private val escalationEngine = EscalationEngine(SystemClock)
    private val engine = HeatRiskEngine()

    override fun onCreate() {
        super.onCreate()
        repository = AppRepository(this)
        supabaseRepo = SupabaseRepository(SupabaseClientProvider.client)
        escalationManager = EscalationManager(this, repository)
        ringDataSource = buildRingDataSource()
        (application as HeatGuardApplication).activeRingDataSource = ringDataSource
        ringDataSource.start { /* latest sample retained by source */ }
        pipeline = buildPipeline()
        startForeground(1, monitoringNotification())
        executor = Executors.newSingleThreadScheduledExecutor()
        executor.scheduleAtFixedRate({ assessOnce() }, 0L, 5L, TimeUnit.MINUTES)
        if (repository.getUseWatch6()) startWatchCollection()
        promptForProfileIfNeeded()
        syncUserToCloud()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        ringDataSource.stop()
        (application as HeatGuardApplication).activeRingDataSource = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun assessOnce() {
        try {
            val result = pipeline.checkNow()
            repository.saveLastDecision(result.decision)
            mainHandler.post { escalationManager.execute(result.plan, result.decision) }

            val uid = repository.getSupabaseUserId().ifBlank { repository.getOrCreateDeviceId() }
            val ambientTempC = result.weatherSample?.airTemperatureC?.toFloat()
            serviceScope.launch {
                supabaseRepo.logDecision(uid, result.decision, ambientTempC)
                ringDataSource.latestSample()?.let { supabaseRepo.logSample(uid, it) }
            }
        } catch (_: Exception) {
            // Do not crash the service on individual assessment failures
        }
    }

    private fun syncUserToCloud() {
        serviceScope.launch {
            val uid = repository.getSupabaseUserId().ifBlank { repository.getOrCreateDeviceId() }
            supabaseRepo.syncUser(
                deviceId = uid,
                name = repository.getUserName().ifBlank { null },
                age = repository.getUserAge().takeIf { it > 0 },
                gender = repository.getUserGender().ifBlank { null },
                language = repository.getLanguage(),
                contact1Name = repository.getEmergencyContactName().ifBlank { null },
                contact1Phone = repository.getEmergencyContactPhone().ifBlank { null },
                contact2Name = repository.getSecondContactName().ifBlank { null },
                contact2Phone = repository.getSecondContactPhone().ifBlank { null }
            )
            supabaseRepo.syncRiskProfile(uid, repository.getRiskProfile())
        }
    }

    /**
     * Sends /sheensafe/start to all connected Wear OS nodes. The watch's
     * WatchCommandService receives this and starts sensor collection (or shows
     * the one-time BODY_SENSORS permission prompt if not yet granted).
     * Fires and forgets — failures are silent since the next service restart retries.
     */
    private fun startWatchCollection() {
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                val payload = ByteArray(0)
                nodes.forEach { node ->
                    Wearable.getMessageClient(this)
                        .sendMessage(node.id, "/sheensafe/start", payload)
                }
            }
    }

    private fun buildRingDataSource(): RingDataSource {
        if (repository.getUseWatch6()) {
            return (application as HeatGuardApplication).wearDataSource
        }
        val mac = repository.getRingDeviceMac()
        return if (mac != null) {
            S5RingDataSource(this, mac)
        } else {
            SimulatedRingDataSource()
        }
    }

    private fun buildPipeline(): MonitoringPipeline {
        val (lat, lon) = getCoordinates()
        val apiProvider = ApiWeatherProvider(latitude = lat, longitude = lon)
        val weatherProvider = FallbackWeatherProvider(
            listOf(
                CachedWeatherProvider(apiProvider, repository),
                ManualWeatherProvider(repository)
            )
        )
        return MonitoringPipeline(
            clock = SystemClock,
            ringDataSource = ringDataSource,
            weatherProvider = weatherProvider,
            engine = engine,
            escalationEngine = escalationEngine,
            baselineProvider = { repository.getBaseline() },
            riskProfileProvider = { repository.getRiskProfile() }
        )
    }

    @Suppress("MissingPermission")
    private fun getCoordinates(): Pair<Double, Double> {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val location = try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: SecurityException) { null }
        return if (location != null) {
            repository.setLastKnownCoords(location.latitude, location.longitude)
            Pair(location.latitude, location.longitude)
        } else {
            val lat = repository.getLastLatitude() ?: 34.0837
            val lon = repository.getLastLongitude() ?: 74.7973
            Pair(lat, lon)
        }
    }

    private fun promptForProfileIfNeeded() {
        val profile = repository.getRiskProfile()
        if (profile.ageBand == AgeBand.UNKNOWN) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            val notification = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                Notification.Builder(this, NotificationHelper.CHANNEL_MONITORING)
            } else {
                Notification.Builder(this)
            }
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Set up your risk profile")
                .setContentText("Tap to add your age and health details for more accurate alerts")
                .setAutoCancel(true)
                .build()
            manager.notify(300, notification)
        }
    }

    private fun monitoringNotification(): Notification {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Notification.Builder(this, NotificationHelper.CHANNEL_MONITORING)
        } else {
            Notification.Builder(this)
        }
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("HeatGuard active")
            .setContentText("Monitoring heat risk")
            .build()
    }
}
