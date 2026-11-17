package com.ishantsil.fawkes.actions

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Time, date and battery answers. No permission needed. */
object InfoAction {

    fun timeNow(): String {
        return "It's " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) + "."
    }

    fun dateToday(): String {
        return "Today is " +
            SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date()) + "."
    }

    fun batteryLevel(context: Context): String {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?: return "I couldn't read the battery level."
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) return "I couldn't read the battery level."
            val percent = level * 100 / scale
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            "Battery is at $percent percent" + if (charging) " and charging." else "."
        } catch (_: Exception) {
            "I couldn't read the battery level."
        }
    }
}
