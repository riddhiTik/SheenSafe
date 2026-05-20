package org.kashmir.heatguard.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import org.kashmir.heatguard.alerts.EscalationManager
import org.kashmir.heatguard.alerts.NotificationHelper
import org.kashmir.heatguard.core.model.RiskInput
import org.kashmir.heatguard.core.model.RiskLevel
import org.kashmir.heatguard.core.risk.HeatRiskEngine
import org.kashmir.heatguard.data.AppRepository
import org.kashmir.heatguard.ring.RingDataSource
import org.kashmir.heatguard.ring.SimulatedRingDataSource
import org.kashmir.heatguard.weather.ManualWeatherProvider

class HeatMonitoringService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var repository: AppRepository
    private lateinit var ringDataSource: RingDataSource
    private lateinit var escalationManager: EscalationManager
    private val engine = HeatRiskEngine()

    private val assessmentTick = object : Runnable {
        override fun run() {
            assessOnce()
            handler.postDelayed(this, 5 * 60_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = AppRepository(this)
        escalationManager = EscalationManager(this, repository)
        ringDataSource = SimulatedRingDataSource()
        ringDataSource.start { /* latest sample is retained by the source */ }
        startForeground(1, monitoringNotification())
        handler.post(assessmentTick)
    }

    override fun onDestroy() {
        handler.removeCallbacks(assessmentTick)
        ringDataSource.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun assessOnce() {
        val now = System.currentTimeMillis()
        val weather = ManualWeatherProvider(repository).latestWeather(now)
        val decision = engine.assess(
            RiskInput(
                nowMillis = now,
                sensorSample = ringDataSource.latestSample(),
                weatherSample = weather,
                baseline = repository.getBaseline(),
                riskProfile = repository.getRiskProfile()
            )
        )
        repository.saveLastDecision(decision)
        if (decision.level.rank >= RiskLevel.CHECK.rank) {
            escalationManager.showRiskNotification(decision)
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
