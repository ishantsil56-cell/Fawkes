package com.ishantsil.fawkes.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.vosk.Model
import java.io.File

/**
 * Thin wrapper around Vosk. Phase 0 only loads the model and reports
 * whether one is installed; live recognition is wired up in Phase 1.
 */
object VoskManager {

    const val MODEL_DIR_NAME = "vosk-model-small-en-us-0.15"
    private const val MODEL_MARKER = "am/final.mdl"

    private var model: Model? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun modelDir(context: Context): File = File(context.filesDir, MODEL_DIR_NAME)

    /** True when the unzipped model is present and looks complete. */
    fun isModelInstalled(context: Context): Boolean {
        val dir = modelDir(context)
        return dir.isDirectory && File(dir, MODEL_MARKER).isFile
    }

    /** Loads the model off the main thread. Callbacks run on the main thread. */
    fun loadModel(
        context: Context,
        onReady: () -> Unit,
        onError: (String) -> Unit,
    ) {
        if (model != null) {
            onReady()
            return
        }
        Thread {
            try {
                model = Model(modelDir(context).absolutePath)
                mainHandler.post { onReady() }
            } catch (e: Exception) {
                mainHandler.post { onError("Could not load voice model: ${e.message}") }
            }
        }.start()
    }

    fun release() {
        model?.close()
        model = null
    }
}
