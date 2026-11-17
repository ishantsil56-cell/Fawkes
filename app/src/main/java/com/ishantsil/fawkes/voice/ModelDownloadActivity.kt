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
 * First-run screen: downloads Vosk voice models (English ~41 MB, Hindi ~44 MB),
 * shows progress, unzips into internal storage, verifies. The voice models are
 * the only downloads in the app — nothing else uses the network.
 */
class ModelDownloadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityModelDownloadBinding

    @Volatile
    private var cancelled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityModelDownloadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.downloadEnButton.setOnClickListener { startDownload("en") }
        binding.downloadHiButton.setOnClickListener { startDownload("hi") }
        binding.doneButton.setOnClickListener {
            setResult(Activity.RESULT_OK)
            finish()
        }

        refreshButtons()
        if (VoskManager.isModelInstalled(this, "en")) {
            setStatus("English voice model is installed. Hindi is optional.")
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
        val enInstalled = VoskManager.isModelInstalled(this, "en")
        val hiInstalled = VoskManager.isModelInstalled(this, "hi")
        binding.downloadEnButton.isEnabled = !enInstalled
        binding.downloadEnButton.text = if (enInstalled) {
            "English model installed"
        } else {
            "Download English model (~41 MB)"
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
                "Download the English voice model (~41 MB). $freeMb MB free. " +
                    "Hindi is optional.",
            )
        }
    }

    private fun startDownload(lang: String) {
        val url = VoskManager.modelUrlFor(lang)
        val zipName = url.substringAfterLast("/")
        val label = if (lang == "hi") "Hindi" else "English"
        binding.downloadEnButton.isEnabled = false
        binding.downloadHiButton.isEnabled = false
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
                if (!VoskManager.isModelInstalled(this, lang)) {
                    throw IllegalStateException("Download finished but the model looks incomplete.")
                }
                runOnUiThread {
                    refreshButtons()
                    setStatus("$label voice model installed.")
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
    }
}
