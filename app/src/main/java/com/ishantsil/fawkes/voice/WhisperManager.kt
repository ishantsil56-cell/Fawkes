package com.ishantsil.fawkes.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.File

/**
 * Command listening with Whisper (base, int8) via sherpa-onnx.
 *
 * Whisper hears accents the Vosk models keep missing, so it is the primary
 * engine for commands (see [SpeechEngine]). Vosk stays for the wake word
 * and as a fallback when the Whisper model hasn't been downloaded yet.
 *
 * Whisper is not a streaming recognizer: this manager records 16 kHz mono
 * audio, uses Silero VAD to find the end of the utterance (a short pause),
 * then transcribes the captured speech in one decode. There are no partial
 * results — the listener gets one final transcript.
 *
 * Model files (downloaded in-app by [ModelDownloadActivity], ~161 MB total):
 * base-encoder.int8.onnx, base-decoder.int8.onnx, base-tokens.txt from
 * Hugging Face `csukuangfj/sherpa-onnx-whisper-base`, plus silero_vad.onnx
 * from the sherpa-onnx `asr-models` release.
 */
object WhisperManager {

    private const val TAG = "FawkesWhisper"

    const val SAMPLE_RATE = 16000

    private const val MODEL_DIR_NAME = "whisper-base"

    const val ENCODER_FILE = "base-encoder.int8.onnx"
    const val DECODER_FILE = "base-decoder.int8.onnx"
    const val TOKENS_FILE = "base-tokens.txt"
    const val VAD_FILE = "silero_vad.onnx"

    const val URL_ENCODER =
        "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/main/base-encoder.int8.onnx"
    const val URL_DECODER =
        "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/main/base-decoder.int8.onnx"
    const val URL_TOKENS =
        "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base/resolve/main/base-tokens.txt"
    const val URL_VAD =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"

    /** Free space the download screen requires for the Whisper model (~161 MB). */
    const val MIN_FREE_BYTES = 300L * 1024 * 1024

    private const val CHUNK_SAMPLES = 1600 // 100 ms @ 16 kHz
    private const val MAX_RECORD_MS = 15_000L
    private const val NO_SPEECH_TIMEOUT_MS = 8_000L
    private const val SILENCE_AFTER_SPEECH_MS = 1_200L

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var recognizer: OfflineRecognizer? = null

    @Volatile
    private var vad: Vad? = null

    @Volatile
    private var loading = false

    @Volatile
    private var listening = false

    @Volatile
    private var stopRequested = false

    /** Bumped by stopListening/release so stale retries and decodes stay silent. */
    @Volatile
    private var sessionId = 0

    private var listenThread: Thread? = null

    fun modelDir(context: Context): File = File(context.filesDir, MODEL_DIR_NAME)

    fun isModelInstalled(context: Context): Boolean {
        val dir = modelDir(context)
        return File(dir, ENCODER_FILE).exists() &&
            File(dir, DECODER_FILE).exists() &&
            File(dir, TOKENS_FILE).exists() &&
            File(dir, VAD_FILE).exists()
    }

    fun isListening(): Boolean = listening

    /**
     * Loads the Whisper recognizer and the VAD on a background thread.
     * Loading takes a few seconds (the decoder is 131 MB); callers that
     * arrive while a load is running are retried by [startListening].
     */
    fun loadModel(context: Context, onReady: () -> Unit, onError: (String) -> Unit) {
        if (recognizer != null && vad != null) {
            mainHandler.post(onReady)
            return
        }
        if (loading) return
        loading = true
        Thread {
            try {
                val dir = modelDir(context)
                val config = OfflineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                    modelConfig = OfflineModelConfig(
                        whisper = OfflineWhisperModelConfig(
                            encoder = File(dir, ENCODER_FILE).absolutePath,
                            decoder = File(dir, DECODER_FILE).absolutePath,
                            language = "en",
                            task = "transcribe",
                        ),
                        tokens = File(dir, TOKENS_FILE).absolutePath,
                        modelType = "whisper",
                        numThreads = 4,
                        provider = "cpu",
                    ),
                )
                recognizer = OfflineRecognizer(assetManager = null, config = config)
                vad = Vad(
                    config = VadModelConfig(
                        sileroVadModelConfig = SileroVadModelConfig(
                            model = File(dir, VAD_FILE).absolutePath,
                            threshold = 0.5F,
                            minSilenceDuration = 0.8F,
                            minSpeechDuration = 0.25F,
                            windowSize = 512,
                            maxSpeechDuration = 12.0F,
                        ),
                        sampleRate = SAMPLE_RATE,
                        numThreads = 1,
                        provider = "cpu",
                    ),
                )
                mainHandler.post(onReady)
            } catch (e: Throwable) {
                Log.e(TAG, "Whisper load failed", e)
                mainHandler.post { onError("Could not load Whisper model: ${e.message}") }
            } finally {
                loading = false
            }
        }.start()
    }

    /**
     * Records until the user pauses, then transcribes. Same listener shape
     * as Vosk (see [VoskManager.Listener]) so [SpeechEngine] can swap the
     * engines without the callers caring. Partials are only used for the
     * "Transcribing…" state — Whisper has no live partials.
     */
    fun startListening(context: Context, listener: VoskManager.Listener) {
        if (listening) return
        val session = sessionId
        val rec = recognizer
        val v = vad
        if (rec == null || v == null) {
            if (loading) {
                // Another caller is loading the model; retry shortly.
                mainHandler.postDelayed(
                    { if (sessionId == session) startListening(context, listener) },
                    500,
                )
                return
            }
            loadModel(
                context,
                onReady = { if (sessionId == session) startListening(context, listener) },
                onError = { if (sessionId == session) listener.onError(it) },
            )
            return
        }
        listening = true
        stopRequested = false
        listenThread = Thread {
            val outcome = listenAndTranscribe(rec, v, session, listener)
            listening = false
            when (outcome) {
                is Outcome.Text -> mainHandler.post { listener.onFinal(listOf(outcome.text)) }
                Outcome.Timeout -> mainHandler.post { listener.onTimeout() }
                is Outcome.Error -> mainHandler.post { listener.onError(outcome.message) }
                Outcome.Cancelled -> { /* Tap-to-stop: discard, no callback. */ }
            }
        }
        listenThread?.start()
    }

    /** Cancels the current listen. The partial utterance is discarded. */
    fun stopListening() {
        stopRequested = true
        sessionId++
    }

    fun releaseModel() {
        stopListening()
        try {
            recognizer?.release()
        } catch (_: Exception) {
        }
        try {
            vad?.release()
        } catch (_: Exception) {
        }
        recognizer = null
        vad = null
    }

    private sealed interface Outcome {
        data class Text(val text: String) : Outcome
        object Timeout : Outcome
        data class Error(val message: String) : Outcome
        object Cancelled : Outcome
    }

    /**
     * Records and transcribes one utterance on the listen thread. Returns
     * the outcome; the caller posts the matching listener callback.
     */
    private fun listenAndTranscribe(
        rec: OfflineRecognizer,
        v: Vad,
        session: Int,
        listener: VoskManager.Listener,
    ): Outcome {
        var recorder: AudioRecord? = null
        try {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuf <= 0) return Outcome.Error("Could not open the microphone.")
            recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, CHUNK_SAMPLES * 4),
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                return Outcome.Error("Could not open the microphone.")
            }
            v.reset()
            v.clear()
            recorder.startRecording()
            val shortBuf = ShortArray(CHUNK_SAMPLES)
            val floatBuf = FloatArray(CHUNK_SAMPLES)
            val startMs = System.currentTimeMillis()
            var speechSeen = false
            var lastSpeechMs = startMs
            var segment: FloatArray? = null
            while (!stopRequested) {
                val n = recorder.read(shortBuf, 0, CHUNK_SAMPLES)
                if (n < 0) return Outcome.Error("Microphone read failed.")
                if (n == 0) continue
                for (i in 0 until n) floatBuf[i] = shortBuf[i] / 32768.0f
                v.acceptWaveform(floatBuf.copyOf(n))
                if (v.isSpeechDetected()) {
                    speechSeen = true
                    lastSpeechMs = System.currentTimeMillis()
                }
                if (!v.empty()) {
                    segment = v.front().samples
                    v.pop()
                    break
                }
                val now = System.currentTimeMillis()
                if (!speechSeen && now - startMs > NO_SPEECH_TIMEOUT_MS) {
                    return Outcome.Timeout
                }
                if (speechSeen && now - lastSpeechMs > SILENCE_AFTER_SPEECH_MS) {
                    // Speech ended but VAD hasn't flushed a segment; flush it.
                    v.flush()
                    if (!v.empty()) {
                        segment = v.front().samples
                        v.pop()
                    }
                    break
                }
                if (now - startMs > MAX_RECORD_MS) {
                    v.flush()
                    if (!v.empty()) {
                        segment = v.front().samples
                        v.pop()
                    }
                    break
                }
            }
            if (stopRequested) return Outcome.Cancelled
            if (segment == null || segment.isEmpty()) return Outcome.Timeout
            mainHandler.post { listener.onPartial("Transcribing…") }
            val stream = rec.createStream()
            try {
                stream.acceptWaveform(segment, SAMPLE_RATE)
                rec.decode(stream)
                val text = rec.getResult(stream).text.trim()
                if (sessionId != session) return Outcome.Cancelled
                return if (text.isEmpty()) Outcome.Timeout else Outcome.Text(text)
            } finally {
                stream.release()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Whisper listen failed", e)
            return Outcome.Error("Listening failed: ${e.message}")
        } finally {
            try {
                recorder?.stop()
            } catch (_: Exception) {
            }
            try {
                recorder?.release()
            } catch (_: Exception) {
            }
        }
    }
}
