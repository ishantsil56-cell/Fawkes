package com.ishantsil.fawkes.voice

import android.app.Activity
import android.os.Bundle
import android.os.StatFs
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ishantsil.fawkes.databinding.ActivityModelDownloadBinding
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * First-run screen: downloads voice models — Whisper (~161 MB, the primary
 * engine; hears accents best) and the Vosk models — English standard
 * (~41 MB), English better-accuracy (~128 MB), Indian English (~1 GB),
 * Hindi (~44 MB) — shows progress, unpacks into internal storage, verifies.
 * The voice models are the only downloads in the app — nothing else uses
 * the network.
 *
 * Whisper downloads as plain files (no zip); the Vosk models are zips.
 * Downloading a bigger English model switches English to it automatically.
 */
class ModelDownloadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityModelDownloadBinding

    @Volatile
    private var cancelled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityModelDownloadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.downloadWhisperButton.setOnClickListener { startWhisperDownload() }
        binding.downloadEnButton.setOnClickListener { startDownload(VoskManager.MODEL_KEY_EN) }
        binding.downloadLgraphButton.setOnClickListener {
            startDownload(VoskManager.MODEL_KEY_EN_LGRAPH)
        }
        binding.downloadEnInButton.setOnClickListener {
            startDownload(VoskManager.MODEL_KEY_EN_IN)
        }
        binding.downloadHiButton.setOnClickListener { startDownload(VoskManager.MODEL_KEY_HI) }
        binding.doneButton.setOnClickListener {
            setResult(Activity.RESULT_OK)
            finish()
        }

        refreshButtons()
        if (WhisperManager.isModelInstalled(this)) {
            setStatus("Whisper voice model is installed — Fawkes listens with Whisper.")
            binding.doneButton.visibility = View.VISIBLE
        } else if (VoskManager.isModelInstalled(this, "en")) {
            setStatus(
                "English voice model is installed. Whisper (above) hears accents " +
                    "more reliably — download it when you can.",
            )
            binding.doneButton.visibility = View.VISIBLE
        } else {
            checkFreeSpace()
        }
    }

    override fun onDestroy() {
        cancelled = true
        super.onDestroy()
    }

    private fun refreshButtons() {
        val whisperInstalled = WhisperManager.isModelInstalled(this)
        val enInstalled = VoskManager.isModelDirInstalled(this, VoskManager.MODEL_EN)
        val lgraphInstalled =
            VoskManager.isModelDirInstalled(this, VoskManager.MODEL_EN_LGRAPH)
        val enInInstalled =
            VoskManager.isModelDirInstalled(this, VoskManager.MODEL_EN_IN)
        val hiInstalled = VoskManager.isModelDirInstalled(this, VoskManager.MODEL_HI)
        binding.downloadWhisperButton.isEnabled = !whisperInstalled
        binding.downloadWhisperButton.text = if (whisperInstalled) {
            "Whisper model installed"
        } else {
            "Download Whisper model (~161 MB, recommended)"
        }
        binding.downloadEnButton.isEnabled = !enInstalled
        binding.downloadEnButton.text = if (enInstalled) {
            "English model installed"
        } else {
            "Download English model (~41 MB)"
        }
        binding.downloadLgraphButton.isEnabled = !lgraphInstalled
        binding.downloadLgraphButton.text = if (lgraphInstalled) {
            "Better-accuracy English model installed"
        } else {
            "Download better-accuracy English model (~128 MB)"
        }
        binding.downloadEnInButton.isEnabled = !enInInstalled
        binding.downloadEnInButton.text = if (enInInstalled) {
            "Indian English model installed"
        } else {
            "Download Indian English model (~1 GB)"
        }
        binding.downloadHiButton.isEnabled = !hiInstalled
        binding.downloadHiButton.text = if (hiInstalled) {
            "Hindi model installed"
        } else {
            "Download Hindi model (~44 MB)"
        }
    }

    private fun checkFreeSpace() {
        val freeBytes = StatFs(filesDir.absolutePath).availableBytes
        val freeMb = freeBytes / (1024 * 1024)
        if (freeBytes < MIN_FREE_BYTES) {
            showError("Low storage: only ${freeMb} MB free. The model needs about 100 MB unpacked.")
        } else {
            setStatus(
                "Download the Whisper voice model (~161 MB) — it hears accents " +
                    "best. $freeMb MB free. The small English model (~41 MB) works " +
                    "too, and runs the wake word.",
            )
        }
    }

    private fun startDownload(modelKey: String) {
        val url = VoskManager.modelUrlForModel(modelKey)
        val zipName = url.substringAfterLast("/")
        val label = when (modelKey) {
            VoskManager.MODEL_KEY_EN_LGRAPH -> "Better-accuracy English"
            VoskManager.MODEL_KEY_EN_IN -> "Indian English"
            VoskManager.MODEL_KEY_HI -> "Hindi"
            else -> "English"
        }
        // The bigger models need more room unpacked (lgraph ~300 MB, Indian ~2 GB).
        val neededBytes = when (modelKey) {
            VoskManager.MODEL_KEY_EN_IN -> MIN_FREE_BYTES_EN_IN
            VoskManager.MODEL_KEY_EN_LGRAPH -> MIN_FREE_BYTES_LGRAPH
            else -> MIN_FREE_BYTES
        }
        val freeBytes = StatFs(filesDir.absolutePath).availableBytes
        if (freeBytes < neededBytes) {
            showError(
                "Low storage: only ${freeBytes / (1024 * 1024)} MB free. " +
                    "This model needs about ${neededBytes / (1024 * 1024)} MB.",
            )
            return
        }
        setAllDownloadButtonsEnabled(false)
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.progress = 0
        binding.progressText.text = ""
        setStatus("Downloading $label voice model…")
        cancelled = false

        Thread {
            try {
                val zipFile = File(filesDir, zipName)
                downloadZip(url, zipFile)
                if (cancelled) return@Thread
                runOnUiThread { setStatus("Unpacking…") }
                unzip(zipFile)
                zipFile.delete()
                val dirName = VoskManager.modelDirNameForModel(modelKey)
                if (!VoskManager.isModelDirInstalled(this, dirName)) {
                    throw IllegalStateException("Download finished but the model looks incomplete.")
                }
                // Downloading a bigger English model switches to it.
                if (modelKey == VoskManager.MODEL_KEY_EN_LGRAPH ||
                    modelKey == VoskManager.MODEL_KEY_EN_IN
                ) {
                    VoskManager.setEnglishModel(this, modelKey)
                }
                runOnUiThread {
                    refreshButtons()
                    setStatus(
                        if (modelKey == VoskManager.MODEL_KEY_EN_LGRAPH ||
                            modelKey == VoskManager.MODEL_KEY_EN_IN
                        ) {
                            "$label model installed and active."
                        } else {
                            "$label voice model installed."
                        },
                    )
                    binding.progressBar.visibility = View.GONE
                    binding.progressText.text = ""
                    binding.doneButton.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                if (!cancelled) {
                    runOnUiThread {
                        showError("Download failed: ${e.message ?: e.javaClass.simpleName}")
                    }
                }
            }
        }.start()
    }

    private fun downloadZip(url: String, zipFile: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 30_000
            connect()
        }
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IllegalStateException("Server replied ${connection.responseCode}")
        }
        val totalBytes = connection.contentLengthLong
        var downloadedBytes = 0L
        connection.inputStream.use { input ->
            BufferedInputStream(input).use { buffered ->
                FileOutputStream(zipFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (!cancelled) {
                        val read = buffered.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            val percent = (downloadedBytes * 100 / totalBytes).toInt()
                            runOnUiThread {
                                binding.progressBar.progress = percent
                                binding.progressText.text = "$percent%"
                            }
                        }
                    }
                }
            }
        }
        if (cancelled) {
            zipFile.delete()
            throw InterruptedException("Download cancelled.")
        }
    }

    private fun unzip(zipFile: File) {
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zip ->
            var entry = zip.nextEntry
            val destDir = filesDir.canonicalPath
            while (entry != null) {
                val outFile = File(filesDir, entry.name)
                // Guard against zip-slip paths escaping internal storage.
                require(outFile.canonicalPath.startsWith(destDir)) {
                    "Unsafe entry in model archive: ${entry.name}"
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output ->
                        zip.copyTo(output)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    /**
     * Downloads the Whisper model: encoder, decoder, tokens (Hugging Face)
     * plus the Silero VAD model (sherpa-onnx releases), ~161 MB total.
     * Files already present at the expected size are skipped, so a
     * half-finished download resumes instead of starting over.
     */
    private fun startWhisperDownload() {
        val freeBytes = StatFs(filesDir.absolutePath).availableBytes
        if (freeBytes < MIN_FREE_BYTES_WHISPER) {
            showError(
                "Low storage: only ${freeBytes / (1024 * 1024)} MB free. " +
                    "Whisper needs about ${MIN_FREE_BYTES_WHISPER / (1024 * 1024)} MB.",
            )
            return
        }
        setAllDownloadButtonsEnabled(false)
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.progress = 0
        binding.progressText.text = ""
        setStatus("Downloading Whisper voice model…")
        cancelled = false

        Thread {
            try {
                val dir = WhisperManager.modelDir(this)
                dir.mkdirs()
                val files = listOf(
                    WhisperManager.URL_ENCODER to WhisperManager.ENCODER_FILE,
                    WhisperManager.URL_DECODER to WhisperManager.DECODER_FILE,
                    WhisperManager.URL_TOKENS to WhisperManager.TOKENS_FILE,
                    WhisperManager.URL_VAD to WhisperManager.VAD_FILE,
                )
                // Known sizes (bytes), verified against the hosts, so progress
                // is honest even if a server omits Content-Length.
                val sizes = listOf(29_120_534L, 130_672_026L, 816_730L, 643_854L)
                val totalBytes = sizes.sum()
                var doneBytes = 0L
                files.forEachIndexed { i, (url, name) ->
                    if (cancelled) return@Thread
                    val dest = File(dir, name)
                    val expected = sizes[i]
                    if (dest.exists() && dest.length() == expected) {
                        doneBytes += expected
                        updateProgress(doneBytes, totalBytes)
                    } else {
                        runOnUiThread { setStatus("Downloading Whisper ($name)…") }
                        downloadFile(url, dest) { bytes ->
                            updateProgress(doneBytes + bytes, totalBytes)
                        }
                        doneBytes += expected
                    }
                }
                if (!WhisperManager.isModelInstalled(this)) {
                    throw IllegalStateException("Download finished but the model looks incomplete.")
                }
                runOnUiThread {
                    refreshButtons()
                    setStatus("Whisper model installed and active.")
                    binding.progressBar.visibility = View.GONE
                    binding.progressText.text = ""
                    binding.doneButton.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                if (!cancelled) {
                    runOnUiThread {
                        showError("Download failed: ${e.message ?: e.javaClass.simpleName}")
                    }
                }
            }
        }.start()
    }

    private fun updateProgress(doneBytes: Long, totalBytes: Long) {
        val percent = (doneBytes * 100 / totalBytes).toInt().coerceIn(0, 100)
        runOnUiThread {
            binding.progressBar.progress = percent
            binding.progressText.text = "$percent%"
        }
    }

    /** Downloads one file, reporting bytes as they arrive. Atomic via a .tmp rename. */
    private fun downloadFile(url: String, dest: File, onBytes: (Long) -> Unit) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 30_000
            connect()
        }
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IllegalStateException("Server replied ${connection.responseCode}")
        }
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        var downloaded = 0L
        try {
            connection.inputStream.use { input ->
                BufferedInputStream(input).use { buffered ->
                    FileOutputStream(tmp).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (!cancelled) {
                            val read = buffered.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onBytes(downloaded)
                        }
                    }
                }
            }
            if (cancelled) throw InterruptedException("Download cancelled.")
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }

    private fun setAllDownloadButtonsEnabled(enabled: Boolean) {
        binding.downloadWhisperButton.isEnabled = enabled
        binding.downloadEnButton.isEnabled = enabled
        binding.downloadLgraphButton.isEnabled = enabled
        binding.downloadEnInButton.isEnabled = enabled
        binding.downloadHiButton.isEnabled = enabled
    }

    private fun setStatus(text: String) {
        binding.statusText.text = text
    }

    private fun showError(text: String) {
        setStatus(text)
        binding.progressBar.visibility = View.GONE
        binding.progressText.text = ""
        refreshButtons()
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val MIN_FREE_BYTES = 150L * 1024 * 1024
        private const val MIN_FREE_BYTES_LGRAPH = 450L * 1024 * 1024
        private const val MIN_FREE_BYTES_EN_IN = 2560L * 1024 * 1024
        private const val MIN_FREE_BYTES_WHISPER = 300L * 1024 * 1024
    }
}
