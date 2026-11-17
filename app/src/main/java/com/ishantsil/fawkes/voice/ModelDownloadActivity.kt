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
 * First-run screen: downloads the Vosk voice model (~41 MB), shows progress,
 * unzips it into internal storage, and verifies it. Nothing else in the app
 * uses the network — this is the one download, and it never repeats unless
 * the model is deleted.
 */
class ModelDownloadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityModelDownloadBinding

    @Volatile
    private var cancelled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityModelDownloadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.downloadButton.setOnClickListener { startDownload() }
        binding.doneButton.setOnClickListener {
            setResult(Activity.RESULT_OK)
            finish()
        }

        if (VoskManager.isModelInstalled(this)) {
            showDone("Voice model is already installed.")
        } else {
            checkFreeSpace()
        }
    }

    override fun onDestroy() {
        cancelled = true
        super.onDestroy()
    }

    private fun checkFreeSpace() {
        val freeBytes = StatFs(filesDir.absolutePath).availableBytes
        val freeMb = freeBytes / (1024 * 1024)
        if (freeBytes < MIN_FREE_BYTES) {
            showError("Low storage: only ${freeMb} MB free. The model needs about 100 MB unpacked.")
        } else {
            setStatus("Ready to download the voice model (~41 MB). ${freeMb} MB free.")
        }
    }

    private fun startDownload() {
        binding.downloadButton.isEnabled = false
        binding.downloadButton.visibility = View.GONE
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.progress = 0
        setStatus("Downloading voice model…")
        cancelled = false

        Thread {
            try {
                val zipFile = File(filesDir, ZIP_NAME)
                downloadZip(zipFile)
                if (cancelled) return@Thread
                runOnUiThread { setStatus("Unpacking…") }
                unzip(zipFile)
                zipFile.delete()
                if (!VoskManager.isModelInstalled(this)) {
                    throw IllegalStateException("Download finished but the model looks incomplete.")
                }
                runOnUiThread { showDone("Voice model installed. Fawkes can listen now.") }
            } catch (e: Exception) {
                if (!cancelled) {
                    runOnUiThread {
                        showError("Download failed: ${e.message ?: e.javaClass.simpleName}")
                    }
                }
            }
        }.start()
    }

    private fun downloadZip(zipFile: File) {
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
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

    private fun setStatus(text: String) {
        binding.statusText.text = text
    }

    private fun showError(text: String) {
        setStatus(text)
        binding.progressBar.visibility = View.GONE
        binding.progressText.text = ""
        binding.downloadButton.isEnabled = true
        binding.downloadButton.visibility = View.VISIBLE
        binding.downloadButton.text = "Retry download"
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    private fun showDone(text: String) {
        setStatus(text)
        binding.progressBar.visibility = View.GONE
        binding.progressText.text = ""
        binding.downloadButton.visibility = View.GONE
        binding.doneButton.visibility = View.VISIBLE
    }

    companion object {
        const val MODEL_URL =
            "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        private const val ZIP_NAME = "vosk-model-small-en-us-0.15.zip"
        private const val MIN_FREE_BYTES = 150L * 1024 * 1024
    }
}
