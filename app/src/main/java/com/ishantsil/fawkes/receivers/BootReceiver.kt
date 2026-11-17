package com.ishantsil.fawkes.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ishantsil.fawkes.data.ReminderStore
import com.ishantsil.fawkes.voice.WakeWordService

/** After a reboot, re-schedules pending reminders and restarts the wake word. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        ReminderStore.rescheduleAll(context)
        if (WakeWordService.isEnabled(context)) {
            WakeWordService.start(context)
        }
    }
}
