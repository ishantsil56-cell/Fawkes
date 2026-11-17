package com.ishantsil.fawkes.voice

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import com.ishantsil.fawkes.notifications.NotificationHelper
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/**
 * Foreground service that listens for "Hey Fawkes" and raises a tap-to-talk
 * notification. Keyword spotting runs on the phone via a grammar-restricted
 * Vosk recognizer. Toggleable in Settings; it costs battery, so it defaults off.
 *
 * MainActivity pauses it while a command is being recorded (one microphone).
 */
class WakeWordService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null
    private var paused = false
    private var lastTriggerAt = 0L
    private var retryRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        startForeground(
            NotificationHelper.WAKE_WORD_NOTIFICATION_ID,
            NotificationHelper.wakeWordNotification(this, listening = true),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                if (!isEnabled(this)) {
                    stopSelf()
                } else {
                    pauseListening()
                }
            }
            ACTION_RESUME -> {
                if (!isEnabled(this)) {
                    stopSelf()
                } else {
                    resumeListening()
                }
            }
            ACTION_STOP -> stopSelf()
            else -> startListening()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        retryRunnable?.let { mainHandler.removeCallbacks(it) }
        stopListening()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startListening() {
        if (paused || speechService != null) return
        if (!VoskManager.isModelInstalled(this)) {
            // No model: nothing to listen with. The user installs it from the app.
            stopSelf()
            return
        }
        VoskManager.loadModel(
            this,
            onReady = {
                if (paused) return@loadModel
                try {
                    recognizer = VoskManager.createWakeRecognizer(this)
                    val wakeRecognizer = recognizer ?: run {
                        scheduleRetry()
                        return@loadModel
                    }
                    speechService = SpeechService(wakeRecognizer, VoskManager.SAMPLE_RATE)
                    speechService?.startListening(wakeListener)
                } catch (_: Exception) {
                    scheduleRetry()
                }
            },
            onError = { scheduleRetry() },
        )
    }

    private val wakeListener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String) {
            checkWake(hypothesis)
        }

        override fun onResult(hypothesis: String) {
            checkWake(hypothesis)
        }

        override fun onFinalResult(hypothesis: String) {
            checkWake(hypothesis)
        }

        override fun onError(exception: Exception) {
            stopListening()
            scheduleRetry()
        }

        override fun onTimeout() {
            // Keyword spotting doesn't time out; ignore.
        }
    }

    private fun checkWake(hypothesis: String) {
        if (!VoskManager.hypothesisHasWakePhrase(hypothesis)) return
        val now = System.currentTimeMillis()
        if (now - lastTriggerAt < TRIGGER_COOLDOWN_MS) return
        lastTriggerAt = now
        NotificationHelper.showWakeDetected(this)
    }

    private fun scheduleRetry() {
        if (paused) return
        retryRunnable?.let { mainHandler.removeCallbacks(it) }
        retryRunnable = Runnable { startListening() }
        mainHandler.postDelayed(retryRunnable!!, RETRY_DELAY_MS)
    }

    private fun pauseListening() {
        paused = true
        retryRunnable?.let { mainHandler.removeCallbacks(it) }
        stopListening()
        updateNotification(listening = false)
    }

    private fun resumeListening() {
        paused = false
        updateNotification(listening = true)
        startListening()
    }

    private fun updateNotification(listening: Boolean) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            NotificationHelper.WAKE_WORD_NOTIFICATION_ID,
            NotificationHelper.wakeWordNotification(this, listening),
        )
    }

    private fun stopListening() {
        try {
            speechService?.stop()
        } catch (_: Exception) {
        }
        try {
            speechService?.shutdown()
        } catch (_: Exception) {
        }
        try {
            recognizer?.close()
        } catch (_: Exception) {
        }
        speechService = null
        recognizer = null
    }

    companion object {
        const val ACTION_PAUSE = "com.ishantsil.fawkes.PAUSE_WAKE_WORD"
        const val ACTION_RESUME = "com.ishantsil.fawkes.RESUME_WAKE_WORD"
        const val ACTION_STOP = "com.ishantsil.fawkes.STOP_WAKE_WORD"

        private const val TRIGGER_COOLDOWN_MS = 10_000L
        private const val RETRY_DELAY_MS = 5_000L
        private const val PREFS = "fawkes_prefs"
        private const val KEY_WAKE_ENABLED = "wake_word_enabled"

        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_WAKE_ENABLED, false)

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_WAKE_ENABLED, enabled).apply()
            if (enabled) {
                start(context)
            } else {
                stop(context)
            }
        }

        fun start(context: Context) {
            val intent = Intent(context, WakeWordService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WakeWordService::class.java))
        }

        fun pause(context: Context) {
            if (!isEnabled(context)) return
            context.startService(
                Intent(context, WakeWordService::class.java).apply {
                    action = ACTION_PAUSE
                },
            )
        }

        fun resume(context: Context) {
            if (!isEnabled(context)) return
            context.startService(
                Intent(context, WakeWordService::class.java).apply {
                    action = ACTION_RESUME
                },
            )
        }
    }
}
