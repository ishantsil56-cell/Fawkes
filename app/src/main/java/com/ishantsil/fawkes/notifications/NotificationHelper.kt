package com.ishantsil.fawkes.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ishantsil.fawkes.MainActivity
import com.ishantsil.fawkes.R

/** Notification channels and builders for reminders and the wake word. */
object NotificationHelper {

    const val CHANNEL_REMINDERS = "fawkes_reminders"
    const val CHANNEL_WAKE_WORD = "fawkes_wake_word"
    const val WAKE_WORD_NOTIFICATION_ID = 1001
    private const val WAKE_DETECTED_NOTIFICATION_ID = 1002

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDERS,
                "Reminders",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Fawkes reminders" },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WAKE_WORD,
                "Wake word",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Fawkes is listening for Hey Fawkes" },
        )
    }

    fun showReminder(context: Context, id: Int, text: String) {
        createChannels(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pending = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Fawkes reminder")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(id, notification)
    }

    /** Persistent notification for the wake-word foreground service. */
    fun wakeWordNotification(context: Context, listening: Boolean): Notification {
        createChannels(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_WAKE_WORD)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Fawkes is listening")
            .setContentText(if (listening) "Say \"Hey Fawkes\" to talk." else "Paused.")
            .setOngoing(true)
            .setContentIntent(pending)
            .build()
    }

    /** High-priority "I heard you" notification after the wake word fires. */
    fun showWakeDetected(context: Context) {
        createChannels(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_LISTEN, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Fawkes heard you")
            .setContentText("Tap to talk.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(WAKE_DETECTED_NOTIFICATION_ID, notification)
    }
}
