package com.ishantsil.fawkes.voice

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import com.ishantsil.fawkes.commands.CommandExecutor
import com.ishantsil.fawkes.notifications.NotificationHelper
import com.ishantsil.fawkes.tts.Speaker
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/**
 * Foreground service that listens for "Hey Fawkes" and then listens for the
 * command itself — no tap needed. The command runs in the service (the same
 * executor the app uses) and the answer is spoken aloud; then the service
 * goes back to spotting for the wake word.
 *
 * Keyword spotting runs on the phone via a grammar-restricted Vosk
 * recognizer. Toggleable in Settings; it costs battery, so it defaults off.
 *
 * The tap-to-talk notification is kept as a fallback (it opens the app
 * listening). MainActivity pauses the service while a command is being
 * recorded in the app (one microphone).
 */
class WakeWordService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null
    private var speaker: Speaker? = null
    private var paused = false
    private var commandListening = false
    private var lastTriggerAt = 0L
    private var retryRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        startForeground(
            NotificationHelper.WAKE_WORD_NOTIFICATION_ID,
            NotificationHelper.wakeWordNotification(this, listening = true),
        )
        speaker = Speaker(this)
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
        if (commandListening) {
            commandListening = false
            VoskManager.stopListening()
        }
        speaker?.shutdown()
        speaker = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startListening() {
        if (paused || commandListening || speechService != null) return
        if (!VoskManager.isModelInstalled(this)) {
            // No model: nothing to listen with. The user installs it from the app.
            stopSelf()
            return
        }
        VoskManager.loadModel(
            this,
            onReady = {
                if (paused || commandListening) return@loadModel
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
        if (commandListening) return
        if (!VoskManager.hypothesisHasWakePhrase(hypothesis)) return
        val now = System.currentTimeMillis()
        if (now - lastTriggerAt < TRIGGER_COOLDOWN_MS) return
        lastTriggerAt = now
        // Fallback: tap-to-talk notification (opens the app listening).
        NotificationHelper.showWakeDetected(this)
        // Hands-free: listen for the command right here, no tap needed.
        startCommandListening()
    }

    private fun startCommandListening() {
        if (commandListening) return
        commandListening = true
        stopListening() // Stop the wake recognizer; one microphone.
        VoskManager.startListening(this, object : VoskManager.Listener {
            override fun onPartial(text: String) {
                // Partials are ignored in the background.
            }

            override fun onFinal(results: List<String>) {
                CommandExecutor.executePhrase(applicationContext, results) { spoken, _ ->
                    speaker?.speak(spoken)
                    endCommandListening()
                }
            }

            override fun onError(message: String) {
                endCommandListening()
            }

            override fun onTimeout() {
                speaker?.speak("I didn't catch that.")
                endCommandListening()
            }
        })
    }

    /**
     * Back to spotting for the wake word. Spotting resumes only after the
     * command finishes, so the spoken confirmation can't retrigger it.
     * (For slow "ask" answers this leaves a gap of up to a minute.)
     */
    private fun endCommandListening() {
        commandListening = false
        VoskManager.stopListening()
        if (!paused && isEnabled(this)) startListening()
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
        if (commandListening) {
            commandListening = false
            VoskManager.stopListening()
        }
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
