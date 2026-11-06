package com.example.roombooking.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.roombooking.MainActivity
import com.example.roombooking.R

object NotificationHelper {
    const val CHANNEL_UPDATES = "booking_updates"
    const val CHANNEL_REMINDERS = "booking_reminders"
    const val EXTRA_BOOKING_ID = "booking_id"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATES, "สถานะการจอง", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "คำขอใหม่ ผลการอนุมัติ และการยกเลิก"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "เตือนก่อนใช้ห้อง", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "แจ้งเตือนก่อนถึงเวลาใช้ห้องที่อนุมัติแล้ว"
            },
        )
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked by canNotify()
    fun show(context: Context, notificationId: Int, channel: String, title: String, text: String, bookingId: Long?) {
        if (!canNotify(context)) return
        // Tapping the notification opens the booking detail screen (deep link via intent extra)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (bookingId != null) putExtra(EXTRA_BOOKING_ID, bookingId)
        }
        val pending = PendingIntent.getActivity(
            context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
