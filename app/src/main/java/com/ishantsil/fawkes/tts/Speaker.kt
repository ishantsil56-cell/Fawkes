package com.ishantsil.fawkes.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Speaks confirmations back to the user. Android TTS works fully offline. */
class Speaker(context: Context) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)

    @Volatile
    private var ready = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.US)
            ready = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    fun speak(text: String) {
        if (ready) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "fawkes-utterance")
        }
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
