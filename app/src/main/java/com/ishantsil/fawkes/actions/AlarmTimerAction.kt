package com.ishantsil.fawkes.actions

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock

/** Alarms and timers via the system Clock app. No permission needed. */
object AlarmTimerAction {

    fun setAlarm(context: Context, hour: Int, minute: Int): String {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, "Fawkes alarm")
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Alarm set for ${formatTime(hour, minute)}."
        } catch (_: Exception) {
            "I couldn't open the clock app."
        }
    }

    fun setTimer(context: Context, seconds: Int): String {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, "Fawkes timer")
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Timer set for ${formatDuration(seconds)}."
        } catch (_: Exception) {
            "I couldn't open the clock app."
        }
    }

    fun formatTime(hour: Int, minute: Int): String {
        val h = if (hour % 12 == 0) 12 else hour % 12
        val amPm = if (hour < 12) "AM" else "PM"
        return "%d:%02d %s".format(h, minute, amPm)
    }

    fun formatDuration(seconds: Int): String {
        return when {
            seconds >= 3600 -> {
                val hours = seconds / 3600
                val minutes = seconds % 3600 / 60
                "$hours hours" + if (minutes > 0) " $minutes minutes" else ""
            }
            seconds >= 60 -> "${seconds / 60} minutes"
            else -> "$seconds seconds"
        }
    }
}
