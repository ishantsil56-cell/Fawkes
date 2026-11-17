package com.ishantsil.fawkes.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ishantsil.fawkes.data.ReminderStore
import com.ishantsil.fawkes.notifications.NotificationHelper

/** Fires when a reminder's alarm goes off: shows the notification. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, 0)
        val text = intent.getStringExtra(EXTRA_TEXT) ?: "Reminder"
        ReminderStore.remove(context, id)
        NotificationHelper.showReminder(context, id, text)
    }

    companion object {
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_TEXT = "reminder_text"
    }
}
