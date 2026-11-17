package com.ishantsil.fawkes.actions

import android.content.Context
import android.os.SystemClock

/** Morse SOS (... --- ...) on the flashlight. Three cycles, then stops. */
object SosAction {

    private const val DOT_MS = 200L
    private const val DASH_MS = 600L
    private const val GAP_MS = 200L
    private const val LETTER_GAP_MS = 600L
    private const val CYCLE_GAP_MS = 1000L
    private const val CYCLES = 3

    @Volatile
    private var running = false

    fun start(context: Context): String {
        if (running) return "SOS is already running."
        if (!FlashlightAction.isAvailable(context)) {
            return "This phone has no flashlight to blink."
        }
        running = true
        val appContext = context.applicationContext
        Thread {
            try {
                repeat(CYCLES) {
                    if (!running) return@Thread
                    blinkLetter(appContext, listOf(DOT_MS, DOT_MS, DOT_MS))
                    blinkLetter(appContext, listOf(DASH_MS, DASH_MS, DASH_MS))
                    blinkLetter(appContext, listOf(DOT_MS, DOT_MS, DOT_MS))
                    SystemClock.sleep(CYCLE_GAP_MS)
                }
            } finally {
                running = false
                FlashlightAction.setTorch(appContext, false)
            }
        }.start()
        return "Sending SOS on the flashlight."
    }

    fun stop(context: Context): String {
        if (!running) return "SOS isn't running."
        running = false
        FlashlightAction.setTorch(context, false)
        return "SOS stopped."
    }

    private fun blinkLetter(context: Context, marks: List<Long>) {
        for (mark in marks) {
            if (!running) return
            FlashlightAction.setTorch(context, true)
            SystemClock.sleep(mark)
            FlashlightAction.setTorch(context, false)
            SystemClock.sleep(GAP_MS)
        }
        SystemClock.sleep(LETTER_GAP_MS - GAP_MS)
    }
}
