package com.ishantsil.fawkes.voice

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * On-device AI answers via MediaPipe LLM Inference (Gemma 2B).
 * The model file (~2 GB) is NOT bundled: Gemma's licence requires the user
 * to accept it on Kaggle, so the model is imported from storage in Settings.
 * Until a model is imported, "ask" explains how to get one.
 */
object LlmManager {

    private const val LLM_DIR = "llm"
    private const val MODEL_FILE = "model.bin"
    private const val MAX_TOKENS = 256
    private const val MIN_MODEL_BYTES = 100_000_000L
    private const val MIN_FREE_RAM_BYTES = 1_800_000_000L

    private val mainHandler = Handler(Looper.getMainLooper())

    private var llm: LlmInference? = null

    @Volatile
    private var busy = false

    fun modelFile(context: Context): File = File(File(context.filesDir, LLM_DIR), MODEL_FILE)

    fun isModelInstalled(context: Context): Boolean {
        val file = modelFile(context)
        return file.isFile && file.length() > MIN_MODEL_BYTES
    }

    fun modelSizeMb(context: Context): Long {
        val file = modelFile(context)
        return if (file.isFile) file.length() / (1024 * 1024) else 0
    }

    /** Copies a user-picked .bin file into app storage. Runs off the main thread. */
    fun importModel(
        context: Context,
        uri: Uri,
        onDone: (success: Boolean, message: String) -> Unit,
    ) {
        Thread {
            try {
                val dir = File(context.filesDir, LLM_DIR)
                dir.mkdirs()
                val dest = File(dir, MODEL_FILE)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Could not read that file.")
                if (!isModelInstalled(context)) {
                    dest.delete()
                    throw IllegalStateException("That file doesn't look like an LLM model.")
                }
                closeEngine()
                mainHandler.post {
                    onDone(true, "Model imported (${modelSizeMb(context)} MB).")
                }
            } catch (e: Exception) {
                mainHandler.post { onDone(false, "Import failed: ${e.message}") }
            }
        }.start()
    }

    /** Downloads a model from a direct URL (for models that allow it). */
    fun downloadModel(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit,
        onDone: (success: Boolean, message: String) -> Unit,
    ) {
        Thread {
            try {
                val dir = File(context.filesDir, LLM_DIR)
                dir.mkdirs()
                val dest = File(dir, MODEL_FILE)
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 30_000
                    readTimeout = 30_000
                    connect()
                }
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException("Server replied ${connection.responseCode}")
                }
                val total = connection.contentLengthLong
                var downloaded = 0L
                connection.inputStream.use { input ->
                    FileOutputStream(dest).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total > 0) {
                                val percent = (downloaded * 100 / total).toInt()
                                mainHandler.post { onProgress(percent) }
                            }
                        }
                    }
                }
                if (!isModelInstalled(context)) {
                    dest.delete()
                    throw IllegalStateException("Downloaded file doesn't look like a model.")
                }
                closeEngine()
                mainHandler.post {
                    onDone(true, "Model downloaded (${modelSizeMb(context)} MB).")
                }
            } catch (e: Exception) {
                mainHandler.post { onDone(false, "Download failed: ${e.message}") }
            }
        }.start()
    }

    /** Asks the on-device model. Callbacks run on the main thread. */
    fun ask(
        context: Context,
        question: String,
        onAnswer: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        if (busy) {
            onError("I'm still thinking about the last question.")
            return
        }
        if (!isModelInstalled(context)) {
            onError(
                "No AI model installed yet. In Settings, import a Gemma model file " +
                    "to turn on on-device answers.",
            )
            return
        }
        if (!hasEnoughRam(context)) {
            onError("Not enough free memory right now to run the AI model. Try again later.")
            return
        }
        busy = true
        Thread {
            try {
                val engine = llm ?: createEngine(context)
                val answer = engine.generateResponse(question)?.trim().orEmpty()
                mainHandler.post {
                    busy = false
                    if (answer.isNotBlank()) {
                        onAnswer(answer)
                    } else {
                        onError("The model had no answer.")
                    }
                }
            } catch (e: Exception) {
                closeEngine()
                mainHandler.post {
                    busy = false
                    onError("The AI model failed: ${e.message}")
                }
            }
        }.start()
    }

    private fun createEngine(context: Context): LlmInference {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile(context).absolutePath)
            .setMaxTokens(MAX_TOKENS)
            .setTopk(40)
            .setTemperature(0.8f)
            .build()
        val engine = LlmInference.createFromOptions(context, options)
        llm = engine
        return engine
    }

    private fun hasEnoughRam(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return info.availMem >= MIN_FREE_RAM_BYTES
    }

    fun closeEngine() {
        try {
            llm?.close()
        } catch (_: Exception) {
        }
        llm = null
    }
}
