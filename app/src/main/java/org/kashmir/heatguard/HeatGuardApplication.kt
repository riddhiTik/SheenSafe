package org.kashmir.heatguard

import android.app.Application
import org.kashmir.heatguard.alerts.NotificationHelper

class HeatGuardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }
}
