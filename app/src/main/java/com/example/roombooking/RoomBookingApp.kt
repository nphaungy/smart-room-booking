package com.example.roombooking

import android.app.Application
import com.example.roombooking.notification.NotificationHelper
import com.example.roombooking.notification.WorkScheduler

class RoomBookingApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannels(this)
        WorkScheduler.schedulePolling(this)
    }
}
