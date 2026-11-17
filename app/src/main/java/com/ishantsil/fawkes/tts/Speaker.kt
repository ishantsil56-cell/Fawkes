package com.ishantsil.fawkes.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Speaks confirmations back to the user. Android TTS works fully offline. */
class Speaker(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val tts = TextToSpeech(appContext, this)

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
            // Speech rate is re-read every time, so Settings changes apply immediately.
            val rate = appContext.getSharedPreferences("fawkes_prefs", Context.MODE_PRIVATE)
                .getFloat("speech_rate", 1.0f)
            tts.setSpeechRate(rate)
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "fawkes-utterance")
        }
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
