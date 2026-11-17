package com.ishantsil.fawkes.actions

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent

/** Media controls via media-key events — works with whatever app is playing. */
object MusicAction {

    fun control(context: Context, action: String): String {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val keyCode = when (action) {
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return "I don't know that music command."
        }
        return try {
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            when (action) {
                "play" -> "Playing."
                "pause" -> "Paused."
                "next" -> "Next song."
                "previous" -> "Previous song."
                else -> "Done."
            }
        } catch (_: Exception) {
            "I couldn't control the music."
        }
    }
}
