package com.example.roombooking.notification

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.roombooking.RoomBookingApp
import com.example.roombooking.data.SessionState
import com.example.roombooking.domain.Booking
import com.example.roombooking.domain.BookingRules
import com.example.roombooking.domain.BookingStatus
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * RB08: checks the server for new notifications and shows them as system notifications.
 * Runs every 15 minutes in the background (the WorkManager minimum) and immediately
 * whenever the app comes to the foreground.
 */
class NotificationPollWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as RoomBookingApp).container
        if (container.session.state.value is SessionState.Loading) container.session.restore()
        if (container.session.state.value !is SessionState.LoggedIn) return Result.success()

        val fresh = container.notificationRepository.fetchNew().getOrElse { return Result.retry() }
        fresh.forEach { n ->
            NotificationHelper.show(
                applicationContext, n.id.toInt(), NotificationHelper.CHANNEL_UPDATES, n.title, n.message, n.bookingId,
            )
        }
        // A status changed -> refresh "my bookings", which also (re)schedules reminders
        if (fresh.any { it.type.startsWith("BOOKING_") }) container.bookingRepository.refreshMine()
        return Result.success()
    }
}

/** RB08: "your room booking starts in 30 minutes". */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val bookingId = inputData.getLong(KEY_BOOKING_ID, -1)
        NotificationHelper.show(
            applicationContext,
            notificationId = (1_000_000 + bookingId).toInt(),
            channel = NotificationHelper.CHANNEL_REMINDERS,
            title = inputData.getString(KEY_TITLE) ?: "ใกล้ถึงเวลาใช้ห้อง",
            text = inputData.getString(KEY_TEXT) ?: "",
            bookingId = bookingId.takeIf { it > 0 },
        )
        return Result.success()
    }

    companion object {
        const val KEY_BOOKING_ID = "booking_id"
        const val KEY_TITLE = "title"
        const val KEY_TEXT = "text"
    }
}

class ReminderScheduler(private val context: Context) {
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    /** Schedules a reminder 30 min before each upcoming APPROVED booking; cancels it otherwise. */
    fun sync(bookings: List<Booking>) {
        val wm = WorkManager.getInstance(context)
        val now = OffsetDateTime.now()
        bookings.forEach { b ->
            val name = "reminder_${b.id}"
            val remindAt = b.start.minusMinutes(REMIND_BEFORE_MINUTES)
            if (b.status == BookingStatus.APPROVED && remindAt.isAfter(now)) {
                val start = b.start.atZoneSameInstant(BookingRules.ZONE).format(timeFmt)
                val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(Duration.between(now, remindAt).toMillis(), TimeUnit.MILLISECONDS)
                    .setInputData(
                        workDataOf(
                            ReminderWorker.KEY_BOOKING_ID to b.id,
                            ReminderWorker.KEY_TITLE to "อีก $REMIND_BEFORE_MINUTES นาทีถึงเวลาใช้ห้อง ${b.roomCode}",
                            ReminderWorker.KEY_TEXT to "${b.roomName} เริ่ม $start น. อย่าลืมสแกน QR เช็กอินหน้าห้อง",
                        ),
                    )
                    .build()
                // REPLACE so an edited start time moves the reminder
                wm.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
            } else {
                wm.cancelUniqueWork(name)
            }
        }
    }

    companion object {
        const val REMIND_BEFORE_MINUTES = 30L
    }
}

object WorkScheduler {
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePolling(context: Context) {
        val request = PeriodicWorkRequestBuilder<NotificationPollWorker>(15, TimeUnit.MINUTES)
            .setConstraints(online)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork("notification_poll", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun pollNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<NotificationPollWorker>().setConstraints(online).build()
        WorkManager.getInstance(context).enqueueUniqueWork("notification_poll_now", ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWork()
    }
}
