package com.ishantsil.fawkes.actions

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings

/** Volume via AudioManager; brightness via system settings (one-time grant). */
object VolumeAction {

    fun volume(context: Context, action: String): String {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return try {
            when (action) {
                "up" -> {
                    audio.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    "Volume up."
                }
                "down" -> {
                    audio.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    "Volume down."
                }
                "mute" -> {
                    audio.adjustVolume(AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                    "Muted."
                }
                "unmute" -> {
                    audio.adjustVolume(AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                    "Unmuted."
                }
                "max" -> {
                    audio.setStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                        AudioManager.FLAG_SHOW_UI,
                    )
                    "Volume at maximum."
                }
                "min" -> {
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
                    "Volume at minimum."
                }
                else -> "I don't know that volume command."
            }
        } catch (_: Exception) {
            "I couldn't change the volume."
        }
    }

    fun brightness(context: Context, action: String): String {
        if (!Settings.System.canWrite(context)) {
            // One-time system grant: opens the "modify system settings" page.
            return try {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "I need permission to change brightness. Turn it on for Fawkes, then try again."
            } catch (_: Exception) {
                "I need permission to change brightness, and I couldn't open the settings page."
            }
        }
        return try {
            val current = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128,
            )
            val next = when (action) {
                "up" -> (current + 40).coerceAtMost(255)
                "down" -> (current - 40).coerceAtLeast(10)
                "max" -> 255
                "min" -> 10
                else -> return "I don't know that brightness command."
            }
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                next,
            )
            "Brightness $action."
        } catch (_: Exception) {
            "I couldn't change the brightness."
        }
    }
}
