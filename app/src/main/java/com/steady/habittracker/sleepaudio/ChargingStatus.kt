package com.steady.habittracker.sleepaudio

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/** On-device charging / plugged-in checks for sleep-audio gate. */
object ChargingStatus {

    fun isCharging(context: Context): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return false
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        if (status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        ) {
            return true
        }
        // Some devices report plugged but status not CHARGING momentarily
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        return plugged == BatteryManager.BATTERY_PLUGGED_AC ||
            plugged == BatteryManager.BATTERY_PLUGGED_USB ||
            plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS
    }
}
