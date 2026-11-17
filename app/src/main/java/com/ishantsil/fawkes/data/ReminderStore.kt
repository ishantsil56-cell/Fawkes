package com.ishantsil.fawkes.data

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.ishantsil.fawkes.receivers.ReminderReceiver
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Reminders: JSON persistence + exact AlarmManager scheduling. */
object ReminderStore {

    private const val FILE_NAME = "reminders.json"

    data class Reminder(val id: Int, val text: String, val triggerAt: Long)

    /** Schedules a reminder; returns the spoken confirmation. */
    fun addAndSchedule(context: Context, text: String, triggerAt: Long): String {
        if (triggerAt <= System.currentTimeMillis()) {
            return "That time has already passed."
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()
        ) {
            // One-time grant: opens the exact-alarm settings page for Fawkes.
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
            }
            return "I need permission for exact alarms. Turn it on for Fawkes, then try again."
        }
        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val reminder = Reminder(id, text, triggerAt)
        val array = JsonStore.readArray(context, FILE_NAME)
        array.put(
            JSONObject().apply {
                put("id", id)
                put("text", text)
                put("triggerAt", triggerAt)
            },
        )
        JsonStore.writeArray(context, FILE_NAME, array)
        schedule(context, reminder)
        val whenText = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(triggerAt))
        val notifyWarning = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            " Notifications are off, so you won't see it — turn them on in settings."
        } else {
            ""
        }
        return "Reminder set for $whenText.$notifyWarning"
    }

    fun schedule(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_ID, reminder.id)
            putExtra(ReminderReceiver.EXTRA_TEXT, reminder.text)
        }
        val pending = PendingIntent.getBroadcast(
            context, reminder.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, reminder.triggerAt, pending,
            )
        } catch (_: SecurityException) {
            // Exact-alarm permission revoked after scheduling; fall back to inexact.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAt, pending)
        }
    }

    fun remove(context: Context, id: Int) {
        val array = JsonStore.readArray(context, FILE_NAME)
        val kept = JSONArray()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optInt("id", -1) != id) kept.put(obj)
        }
        JsonStore.writeArray(context, FILE_NAME, kept)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pending)
    }

    /** Re-schedules all pending reminders (used after reboot). */
    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        list(context).forEach { reminder ->
            if (reminder.triggerAt > now) {
                schedule(context, reminder)
            } else {
                remove(context, reminder.id)
            }
        }
    }

    fun list(context: Context): List<Reminder> {
        val array = JsonStore.readArray(context, FILE_NAME)
        val result = mutableListOf<Reminder>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            result.add(
                Reminder(
                    id = obj.optInt("id", 0),
                    text = obj.optString("text", "Reminder"),
                    triggerAt = obj.optLong("triggerAt", 0),
                ),
            )
        }
        return result
    }
}
