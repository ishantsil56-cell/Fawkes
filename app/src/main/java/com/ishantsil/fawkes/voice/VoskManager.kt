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
 *
 * Accuracy: the default small English model (41 MB) mishears often, so the
 * recognizer asks Vosk for its 3 best guesses and the command matcher tries
 * each one. A bigger English model (lgraph, 128 MB) is available as a
 * download and is noticeably more accurate.
 */
object VoskManager {

    const val MODEL_EN = "vosk-model-small-en-us-0.15"
    const val MODEL_EN_LGRAPH = "vosk-model-en-us-0.22-lgraph"
    const val MODEL_EN_IN = "vosk-model-en-in-0.5"
    const val MODEL_HI = "vosk-model-small-hi-0.22"
    const val MODEL_URL_EN = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    const val MODEL_URL_EN_LGRAPH = "https://alphacephei.com/vosk/models/vosk-model-en-us-0.22-lgraph.zip"
    const val MODEL_URL_EN_IN = "https://alphacephei.com/vosk/models/vosk-model-en-in-0.5.zip"
    const val MODEL_URL_HI = "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip"

    /** Model keys used by the download screen. */
    const val MODEL_KEY_EN = "en"
    const val MODEL_KEY_EN_LGRAPH = "en-lgraph"
    const val MODEL_KEY_EN_IN = "en-in"
    const val MODEL_KEY_HI = "hi"

    const val SAMPLE_RATE = 16000f
    const val LISTEN_TIMEOUT_MS = 12_000L
    const val WAKE_PHRASE = "hey fawkes"
    private const val MAX_ALTERNATIVES = 3

    private const val PREFS = "fawkes_prefs"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_ENGLISH_MODEL = "english_model"
    private const val KEY_USE_LGRAPH = "use_lgraph" // 0.3.2 only; migrated once, see englishModel()
    private const val MODEL_MARKER = "am/final.mdl"

    interface Listener {
        fun onPartial(text: String)
        /** Best guess first, then Vosk's alternative guesses (may be empty). */
        fun onFinal(results: List<String>)
        fun onError(message: String)
        fun onTimeout()
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var model: Model? = null
    private var modelKey: String? = null
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
        if (modelKey != null && currentModelKey(context) != modelKey) releaseModel()
    }

    /**
     * Which English model is selected: "en" (small, 41 MB), "en-lgraph"
     * (128 MB) or "en-in" (Indian English, 1 GB). Migrates the 0.3.2
     * boolean once: lgraph on means "en-lgraph".
     */
    fun englishModel(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_ENGLISH_MODEL, null)
        if (saved != null) return saved
        val migrated = if (prefs.getBoolean(KEY_USE_LGRAPH, false)) {
            MODEL_KEY_EN_LGRAPH
        } else {
            MODEL_KEY_EN
        }
        prefs.edit().putString(KEY_ENGLISH_MODEL, migrated).apply()
        return migrated
    }

    fun setEnglishModel(context: Context, modelKey: String) {
        val changed = englishModel(context) != modelKey
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ENGLISH_MODEL, modelKey).apply()
        if (changed) releaseModel()
    }

    /** The model key for the current language + English model choice. */
    fun currentModelKey(context: Context): String {
        val lang = currentLanguage(context)
        return if (lang == "hi") MODEL_KEY_HI else englishModel(context)
    }

    fun modelDirNameForModel(modelKey: String): String = when (modelKey) {
        MODEL_KEY_EN_LGRAPH -> MODEL_EN_LGRAPH
        MODEL_KEY_EN_IN -> MODEL_EN_IN
        MODEL_KEY_HI -> MODEL_HI
        else -> MODEL_EN
    }

    fun modelUrlForModel(modelKey: String): String = when (modelKey) {
        MODEL_KEY_EN_LGRAPH -> MODEL_URL_EN_LGRAPH
        MODEL_KEY_EN_IN -> MODEL_URL_EN_IN
        MODEL_KEY_HI -> MODEL_URL_HI
        else -> MODEL_URL_EN
    }

    fun modelDir(context: Context, lang: String = currentLanguage(context)): File {
        val key = if (lang == "hi") MODEL_KEY_HI else englishModel(context)
        return File(context.filesDir, modelDirNameForModel(key))
    }

    /** True when the unzipped model is present and looks complete. */
    fun isModelInstalled(context: Context, lang: String = currentLanguage(context)): Boolean {
        val dir = modelDir(context, lang)
        return dir.isDirectory && File(dir, MODEL_MARKER).isFile
    }

    /** True when a specific model directory is present and looks complete. */
    fun isModelDirInstalled(context: Context, dirName: String): Boolean {
        val dir = File(context.filesDir, dirName)
        return dir.isDirectory && File(dir, MODEL_MARKER).isFile
    }

    // ---- model loading ----

    /** Loads the model off the main thread. Callbacks run on the main thread. */
    fun loadModel(
        context: Context,
        onReady: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val key = currentModelKey(context)
        if (model != null && modelKey == key) {
            onReady()
            return
        }
        Thread {
            try {
                releaseModel()
                model = Model(modelDir(context).absolutePath)
                modelKey = key
                mainHandler.post { onReady() }
            } catch (e: Throwable) {
                // Throwable, not Exception: the 1 GB Indian English model
                // can throw OutOfMemoryError while loading, and that must
                // surface as a clean error, not a crash.
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
        modelKey = null
    }

    // ---- live listening (commands) ----

    fun isListening(): Boolean = listening

    fun startListening(context: Context, listener: Listener) {
        if (listening) return
        if (model == null || modelKey != currentModelKey(context)) {
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
            // Ask Vosk for its N best guesses: the top one is often wrong on
            // the small model, and a lower-ranked guess may match a command.
            newRecognizer.setMaxAlternatives(MAX_ALTERNATIVES)
            recognizer = newRecognizer
            speechService = SpeechService(newRecognizer, SAMPLE_RATE)
            speechService?.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String) {
                    val text = parseText(hypothesis, "partial")
                    if (text.isNotBlank()) mainHandler.post { listener.onPartial(text) }
                }

                override fun onResult(hypothesis: String) {
                    // Vosk finalised an utterance: take it and stop.
                    val results = parseAlternatives(hypothesis)
                    if (results.isNotEmpty()) deliverFinal(results, listener)
                }

                override fun onFinalResult(hypothesis: String) {
                    val results = parseAlternatives(hypothesis)
                    if (results.isNotEmpty()) deliverFinal(results, listener)
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

    private fun deliverFinal(results: List<String>, listener: Listener) {
        if (delivered) return
        delivered = true
        stopListening()
        mainHandler.post { listener.onFinal(results) }
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
     *
     * Big static-vocabulary models (e.g. the Indian English model) don't
     * support grammar restriction, so the constructor throws — fall back
     * to a plain recognizer and spot the phrase in its text instead.
     */
    fun createWakeRecognizer(context: Context): Recognizer? {
        val currentModel = model ?: return null
        try {
            return Recognizer(currentModel, SAMPLE_RATE, "[\"$WAKE_PHRASE\", \"[unk]\"]")
        } catch (_: Exception) {
            // Static model: grammar not supported.
        }
        return try {
            Recognizer(currentModel, SAMPLE_RATE)
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

    /**
     * Best guess first, then Vosk's alternative guesses (present when
     * setMaxAlternatives was used). Duplicates and blanks removed.
     */
    fun parseAlternatives(hypothesis: String): List<String> {
        val results = mutableListOf<String>()
        val best = parseText(hypothesis, "text")
        if (best.isNotBlank()) results.add(best)
        try {
            val alternatives = JSONObject(hypothesis).optJSONArray("alternatives")
            if (alternatives != null) {
                for (i in 0 until alternatives.length()) {
                    val text = alternatives.optJSONObject(i)
                        ?.optString("text", "")?.trim().orEmpty()
                    if (text.isNotBlank() && text !in results) results.add(text)
                }
            }
        } catch (_: Exception) {
        }
        return results
    }

    fun release() {
        stopListening()
        releaseModel()
    }
}
