package com.ishantsil.fawkes.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File

/**
 * Thin wrapper around Vosk: model loading, live recognition for commands,
 * and keyword spotting for the wake word. Everything runs on the phone.
 *
 * SpeechService callbacks arrive on a background thread; this object posts
 * all listener calls to the main thread.
 */
object VoskManager {

    const val MODEL_EN = "vosk-model-small-en-us-0.15"
    const val MODEL_HI = "vosk-model-small-hi-0.22"
    const val MODEL_URL_EN = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    const val MODEL_URL_HI = "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip"

    const val SAMPLE_RATE = 16000f
    const val LISTEN_TIMEOUT_MS = 12_000L
    const val WAKE_PHRASE = "hey fawkes"

    private const val PREFS = "fawkes_prefs"
    private const val KEY_LANGUAGE = "language"
    private const val MODEL_MARKER = "am/final.mdl"

    interface Listener {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(message: String)
        fun onTimeout()
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var model: Model? = null
    private var modelLang: String? = null
    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null
    private var listening = false
    private var delivered = false
    private var timeoutRunnable: Runnable? = null

    // ---- language + model files ----

    fun currentLanguage(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, "en") ?: "en"

    fun setLanguage(context: Context, lang: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANGUAGE, lang).apply()
        // Drop the loaded model so the next listen loads the right one.
        if (modelLang != null && modelLang != lang) releaseModel()
    }

    fun modelDirNameFor(lang: String): String =
        if (lang == "hi") MODEL_HI else MODEL_EN

    fun modelUrlFor(lang: String): String =
        if (lang == "hi") MODEL_URL_HI else MODEL_URL_EN

    fun modelDir(context: Context, lang: String = currentLanguage(context)): File =
        File(context.filesDir, modelDirNameFor(lang))

    /** True when the unzipped model is present and looks complete. */
    fun isModelInstalled(context: Context, lang: String = currentLanguage(context)): Boolean {
        val dir = modelDir(context, lang)
        return dir.isDirectory && File(dir, MODEL_MARKER).isFile
    }

    // ---- model loading ----

    /** Loads the model off the main thread. Callbacks run on the main thread. */
    fun loadModel(
        context: Context,
        onReady: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val lang = currentLanguage(context)
        if (model != null && modelLang == lang) {
            onReady()
            return
        }
        Thread {
            try {
                releaseModel()
                model = Model(modelDir(context, lang).absolutePath)
                modelLang = lang
                mainHandler.post { onReady() }
            } catch (e: Exception) {
                mainHandler.post { onError("Could not load voice model: ${e.message}") }
            }
        }.start()
    }

    private fun releaseModel() {
        try {
            model?.close()
        } catch (_: Exception) {
        }
        model = null
        modelLang = null
    }

    // ---- live listening (commands) ----

    fun isListening(): Boolean = listening

    fun startListening(context: Context, listener: Listener) {
        if (listening) return
        if (model == null || modelLang != currentLanguage(context)) {
            loadModel(
                context,
                onReady = { startService(listener) },
                onError = { listener.onError(it) },
            )
            return
        }
        startService(listener)
    }

    private fun startService(listener: Listener) {
        val currentModel = model
        if (currentModel == null) {
            listener.onError("Voice model is not loaded.")
            return
        }
        try {
            delivered = false
            // Local val: Kotlin won't smart-cast the nullable member property.
            val newRecognizer = Recognizer(currentModel, SAMPLE_RATE)
            recognizer = newRecognizer
            speechService = SpeechService(newRecognizer, SAMPLE_RATE)
            speechService?.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String) {
                    val text = parseText(hypothesis, "partial")
                    if (text.isNotBlank()) mainHandler.post { listener.onPartial(text) }
                }

                override fun onResult(hypothesis: String) {
                    // Vosk finalised an utterance: take it and stop.
                    val text = parseText(hypothesis, "text")
                    if (text.isNotBlank()) deliverFinal(text, listener)
                }

                override fun onFinalResult(hypothesis: String) {
                    val text = parseText(hypothesis, "text")
                    if (text.isNotBlank()) deliverFinal(text, listener)
                }

                override fun onError(exception: Exception) {
                    stopListening()
                    mainHandler.post {
                        listener.onError("Listening failed: ${exception.message}")
                    }
                }

                override fun onTimeout() {
                    stopListening()
                    mainHandler.post { listener.onTimeout() }
                }
            })
            listening = true
            timeoutRunnable = Runnable {
                if (listening && !delivered) {
                    stopListening()
                    listener.onTimeout()
                }
            }
            mainHandler.postDelayed(timeoutRunnable!!, LISTEN_TIMEOUT_MS)
        } catch (e: Exception) {
            stopListening()
            listener.onError("Could not start listening: ${e.message}")
        }
    }

    private fun deliverFinal(text: String, listener: Listener) {
        if (delivered) return
        delivered = true
        stopListening()
        mainHandler.post { listener.onFinal(text) }
    }

    fun stopListening() {
        listening = false
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
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

    // ---- wake word ----

    /**
     * A recognizer restricted to the wake phrase (plus [unk] for everything
     * else). Used by WakeWordService for keyword spotting. The caller owns
     * the recognizer and must close it.
     */
    fun createWakeRecognizer(context: Context): Recognizer? {
        val currentModel = model ?: return null
        return try {
            Recognizer(currentModel, SAMPLE_RATE, "[\"$WAKE_PHRASE\", \"[unk]\"]")
        } catch (_: Exception) {
            null
        }
    }

    /** True when the given hypothesis text contains the wake phrase. */
    fun hypothesisHasWakePhrase(hypothesis: String): Boolean {
        val text = parseText(hypothesis, "text")
            .ifBlank { parseText(hypothesis, "partial") }
        return text.contains(WAKE_PHRASE)
    }

    // ---- helpers ----

    fun parseText(hypothesis: String, key: String): String {
        return try {
            JSONObject(hypothesis).optString(key, "").trim()
        } catch (_: Exception) {
            ""
        }
    }

    fun release() {
        stopListening()
        releaseModel()
    }
}
