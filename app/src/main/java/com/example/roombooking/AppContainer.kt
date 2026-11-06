package com.example.roombooking

import android.content.Context
import com.example.roombooking.data.SessionManager
import com.example.roombooking.data.local.AppDatabase
import com.example.roombooking.data.remote.ApiService
import com.example.roombooking.data.remote.Network
import com.example.roombooking.data.repository.AdminRepository
import com.example.roombooking.data.repository.AuthRepository
import com.example.roombooking.data.repository.BookingRepository
import com.example.roombooking.data.repository.NotificationRepository
import com.example.roombooking.data.repository.RoomRepository
import com.example.roombooking.notification.ReminderScheduler
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency injection: one instance of each dependency for the whole app. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val gson = Gson()
    val session = SessionManager(context, gson, appScope)
    val api: ApiService = Network.createApi(BuildConfig.BASE_URL, session, gson)
    private val db = AppDatabase.build(context)
    private val reminders = ReminderScheduler(context)

    val notificationRepository = NotificationRepository(api, db.notificationDao(), session, gson)
    val authRepository = AuthRepository(api, session, db, notificationRepository, gson)
    val roomRepository = RoomRepository(api, db.roomDao(), gson)
    val bookingRepository = BookingRepository(api, db.bookingDao(), session, reminders, gson)
    val adminRepository = AdminRepository(api, gson)
}
