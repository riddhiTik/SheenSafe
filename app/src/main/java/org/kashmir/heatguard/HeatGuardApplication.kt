package org.kashmir.heatguard

import android.app.Application
import org.kashmir.heatguard.alerts.NotificationHelper
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

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }
}
