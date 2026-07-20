package com.steady.habittracker.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.steady.habittracker.MainActivity
import com.steady.habittracker.data.ReminderStrength

object NotificationHelper {
    private const val CHANNEL_ID = "steady_reminders"
    private const val CHANNEL_NAME = "Steady Reminders"
    private const val CHANNEL_STRONG = "steady_reminders_strong"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Habit reminders for your routines"
            }
            mgr.createNotificationChannel(ch)
            val strong = NotificationChannel(
                CHANNEL_STRONG,
                "Steady Strong Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Higher-priority habit reminders"
            }
            mgr.createNotificationChannel(strong)
        }
    }

    fun showReminder(
        context: Context,
        title: String,
        text: String,
        groupId: String? = null,
        notificationId: Int = 4242,
        strength: ReminderStrength = ReminderStrength.GENTLE,
        openCapture: Boolean = false,
        ongoing: Boolean = false
    ) {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("open_group", groupId)
            if (openCapture) putExtra("open_capture", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channel = when (strength) {
            ReminderStrength.STRONG -> CHANNEL_STRONG
            else -> CHANNEL_ID
        }
        val priority = when (strength) {
            ReminderStrength.STRONG -> NotificationCompat.PRIORITY_HIGH
            ReminderStrength.NORMAL -> NotificationCompat.PRIORITY_DEFAULT
            ReminderStrength.GENTLE -> NotificationCompat.PRIORITY_LOW
        }

        val notif = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(!ongoing)
            .setOngoing(ongoing)
            .setContentIntent(pi)
            .setPriority(priority)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId, notif)
    }
}
