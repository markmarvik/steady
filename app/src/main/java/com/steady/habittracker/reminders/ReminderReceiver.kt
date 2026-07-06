package com.steady.habittracker.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val groupId = intent.getStringExtra("groupId")
        val name = intent.getStringExtra("name") ?: "Steady"
        NotificationHelper.showReminder(
            context,
            "$name time",
            "Tap to log your habits",
            groupId
        )
        // Reschedule next if needed (simple; full scheduler would handle)
    }
}
