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
 * On-device AI answers via MediaPipe LLM Inference (Gemma 2B IT).
 * The model file (~2 GB) is NOT bundled: Gemma's licence requires the user
 * to accept it on Kaggle, so the model is imported from storage in Settings.
 * Until a model is imported, "ask" explains how to get one.
 *
 * MediaPipe does NOT apply a chat template: the raw question must be wrapped
 * in Gemma's <start_of_turn> format, or the instruct model behaves like a
 * base model (echoes, rambles, incoherent answers). Temperature is kept low
 * (0.3) because these are factual Q&A answers, not creative writing.
 */
object LlmManager {

    private const val LLM_DIR = "llm"
    private const val MODEL_FILE = "model.bin"
    private const val MAX_TOKENS = 256
    private const val MIN_MODEL_BYTES = 100_000_000L
    private const val MIN_FREE_RAM_BYTES = 1_800_000_000L

    private const val SYSTEM_PROMPT =
        "You are Fawkes, a helpful on-device assistant. " +
            "Answer briefly and accurately in plain language."

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
                val raw = engine.generateResponse(buildPrompt(question))?.trim().orEmpty()
                val answer = cleanAnswer(raw, question)
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

    /**
     * Gemma's chat template. The system instruction rides in the first user
     * turn (Gemma has no separate system role). Without this, the model
     * completes text instead of answering.
     */
    private fun buildPrompt(question: String): String =
        "<start_of_turn>user\n$SYSTEM_PROMPT\n\n$question<end_of_turn>\n<start_of_turn>model\n"

    /**
     * Strips template markers and any echo of the prompt/question. Gemma
     * sometimes repeats the question before answering, and sometimes keeps
     * role-playing a second turn — both are cut here.
     */
    private fun cleanAnswer(raw: String, question: String): String {
        var answer = raw
            .replace("<start_of_turn>model", "")
            .replace("<start_of_turn>user", "")
            .replace("<end_of_turn>", "")
            .replace("<eos>", "")
            .trim()
        if (answer.startsWith(question, ignoreCase = true)) {
            answer = answer.substring(question.length).trim()
        }
        if (answer.startsWith(SYSTEM_PROMPT, ignoreCase = true)) {
            answer = answer.substring(SYSTEM_PROMPT.length).trim()
        }
        val nextTurn = answer.indexOf("<start_of_turn>")
        if (nextTurn >= 0) {
            answer = answer.substring(0, nextTurn).trim()
        }
        return answer.trim().trimStart(':', '-', ' ').trim()
    }

    private fun createEngine(context: Context): LlmInference {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile(context).absolutePath)
            .setMaxTokens(MAX_TOKENS)
            .setTopK(40)
            .setTemperature(0.3f)
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
