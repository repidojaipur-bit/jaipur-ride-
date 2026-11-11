package com.drivepartner.app

import android.app.Application
import com.drivepartner.app.core.di.AppContainer
import com.drivepartner.app.core.di.DefaultAppContainer
import com.drivepartner.app.core.notifications.DrivePartnerNotificationBridge

/**
 * DrivePartnerApp - Base application class owning the application dependency container.
 */
class DrivePartnerApp : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        DrivePartnerNotificationBridge.ensureNotificationChannels(this)
        appContainer = DefaultAppContainer(this)
    }
}
