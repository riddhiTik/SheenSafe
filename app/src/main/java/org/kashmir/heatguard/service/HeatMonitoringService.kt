package org.kashmir.heatguard.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import org.kashmir.heatguard.alerts.EscalationManager
import org.kashmir.heatguard.alerts.NotificationHelper
import org.kashmir.heatguard.core.SystemClock
import org.kashmir.heatguard.core.escalation.EscalationEngine
import org.kashmir.heatguard.core.model.AgeBand
import org.kashmir.heatguard.core.pipeline.MonitoringPipeline
import org.kashmir.heatguard.core.ring.RingDataSource
import org.kashmir.heatguard.core.risk.HeatRiskEngine
import org.kashmir.heatguard.data.AppRepository
import org.kashmir.heatguard.ring.GadgetbridgeRingDataSource
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
    private lateinit var executor: ScheduledExecutorService
    private lateinit var repository: AppRepository
    private lateinit var ringDataSource: RingDataSource
    private lateinit var escalationManager: EscalationManager
    private lateinit var pipeline: MonitoringPipeline
    private val escalationEngine = EscalationEngine(SystemClock)
    private val engine = HeatRiskEngine()

    override fun onCreate() {
        super.onCreate()
        repository = AppRepository(this)
        escalationManager = EscalationManager(this, repository)
        ringDataSource = buildRingDataSource()
        ringDataSource.start { /* latest sample retained by source */ }
        pipeline = buildPipeline()
        startForeground(1, monitoringNotification())
        executor = Executors.newSingleThreadScheduledExecutor()
        executor.scheduleAtFixedRate({ assessOnce() }, 0L, 5L, TimeUnit.MINUTES)
        promptForProfileIfNeeded()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        ringDataSource.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun assessOnce() {
        try {
            val result = pipeline.checkNow()
            repository.saveLastDecision(result.decision)
            mainHandler.post { escalationManager.execute(result.plan, result.decision) }
        } catch (_: Exception) {
            // Do not crash the service on individual assessment failures
        }
    }

    private fun buildRingDataSource(): RingDataSource {
        return if (repository.getBoolean("use_simulated_ring", true)) {
            SimulatedRingDataSource()
        } else {
            GadgetbridgeRingDataSource(this)
        }
    }

    private fun buildPipeline(): MonitoringPipeline {
        // Default coordinates: Srinagar, Kashmir
        // TODO: make configurable via location settings
        val apiProvider = ApiWeatherProvider(latitude = 34.0837, longitude = 74.7973)
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
            riskProfileProvider = { repository.getRiskProfile() },
            symptomProvider = { null } // symptoms managed by MainActivity
        )
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
