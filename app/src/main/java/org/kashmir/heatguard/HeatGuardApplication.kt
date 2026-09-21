package org.kashmir.heatguard

import android.app.Application
import org.kashmir.heatguard.alerts.NotificationHelper
import org.kashmir.heatguard.core.model.RiskDecision
import org.kashmir.heatguard.core.ring.RingDataSource
import org.kashmir.heatguard.ring.WearDataRingDataSource

class HeatGuardApplication : Application() {

    // Eagerly initialized so SheenSafeWearListenerService (a system-managed binder
    // component) can call onWatchReading() before HeatMonitoringService starts.
    val wearDataSource = WearDataRingDataSource()

    // Set by HeatMonitoringService when it starts; cleared when it stops.
    // DashboardFragment reads from this so it shares the same live connection
    // rather than spinning up its own SimulatedRingDataSource.
    var activeRingDataSource: RingDataSource? = null

    // Set by DashboardFragment while it's visible so EscalationManager can show a
    // routine risk update as an in-app banner instead of a system tray notification.
    // Null when no screen is on top, in which case the alert is simply skipped (it's
    // still recorded in notification history, viewable from the bell icon).
    var inAppAlertListener: ((RiskDecision) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }
}
