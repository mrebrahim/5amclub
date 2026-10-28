package com.fiveam.club

import android.app.Application
import com.fiveam.club.util.Notifications
import kotlinx.coroutines.launch

class FiveAmApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        container.appScope.launch { container.routines.ensureDefaults() }
    }
}
