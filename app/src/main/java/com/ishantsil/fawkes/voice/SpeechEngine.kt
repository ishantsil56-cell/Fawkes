package com.ishantsil.fawkes.voice

import android.content.Context

/**
 * Chooses the speech engine for command listening.
 *
 * Whisper (via sherpa-onnx, see [WhisperManager]) is the primary engine —
 * it hears accents the Vosk models keep missing. Vosk is the fallback when
 * the Whisper model hasn't been downloaded yet, and Vosk still runs the
 * wake word either way.
 *
 * Both engines report through [VoskManager.Listener], so callers (the app
 * and the wake-word service) never care which engine is active.
 */
object SpeechEngine {

    enum class Engine { WHISPER, VOSK }

    fun activeEngine(context: Context): Engine =
        if (WhisperManager.isModelInstalled(context)) Engine.WHISPER else Engine.VOSK

    fun isListening(): Boolean =
        WhisperManager.isListening() || VoskManager.isListening()

    fun startListening(context: Context, listener: VoskManager.Listener) {
        if (activeEngine(context) == Engine.WHISPER) {
            val wrapped = object : VoskManager.Listener {
                override fun onPartial(text: String) = listener.onPartial(text)
                override fun onFinal(results: List<String>) = listener.onFinal(results)
                override fun onTimeout() = listener.onTimeout()
                override fun onError(message: String) {
                    // Whisper failed (e.g. the model won't load) — fall back
                    // to Vosk once, with the original listener so a Vosk
                    // failure reports straight through instead of looping.
                    if (VoskManager.isModelInstalled(context)) {
                        VoskManager.startListening(context, listener)
                    } else {
                        listener.onError(message)
                    }
                }
            }
            WhisperManager.startListening(context, wrapped)
        } else {
            VoskManager.startListening(context, listener)
        }
    }

    fun stopListening() {
        WhisperManager.stopListening()
        VoskManager.stopListening()
    }
}
