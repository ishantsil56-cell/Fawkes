package com.ishantsil.fawkes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ishantsil.fawkes.commands.CommandExecutor
import com.ishantsil.fawkes.commands.ConversationMemory
import com.ishantsil.fawkes.databinding.ActivityMainBinding
import com.ishantsil.fawkes.tts.Speaker
import com.ishantsil.fawkes.ui.HelpActivity
import com.ishantsil.fawkes.ui.SettingsActivity
import com.ishantsil.fawkes.voice.ModelDownloadActivity
import com.ishantsil.fawkes.voice.VoskManager
import com.ishantsil.fawkes.voice.WakeWordService
import com.ishantsil.fawkes.widget.FawkesWidget

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var speaker: Speaker
    private var autoListenPending = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var continuousRunnable: Runnable? = null

    private val downloadLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshModelStatus() }

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filterValues { !it }.keys
        if (denied.isEmpty()) {
            log("Permissions granted.")
        } else {
            log("Permissions denied: ${denied.joinToString(", ")} — some commands won't work.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        speaker = Speaker(this)
        ConversationMemory.load(this)

        binding.micButton.setOnClickListener { onMicTapped() }
        binding.downloadButton.setOnClickListener { openModelDownload() }
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.helpButton.setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }
        binding.continuousSwitch.isChecked = isContinuousListen()
        binding.continuousSwitch.setOnCheckedChangeListener { _, enabled ->
            setContinuousListen(enabled)
            if (!enabled) cancelContinuousRestart()
        }

        askForPermissionsIfNeeded()
        refreshModelStatus()
        log("Fawkes 0.3.1 — tap to listen, or say \"Hey Fawkes\" (Settings).")

        autoListenPending = intent?.getBooleanExtra(EXTRA_LISTEN, false) == true
    }

    override fun onResume() {
        super.onResume()
        refreshModelStatus()
        if (autoListenPending) {
            autoListenPending = false
            binding.root.postDelayed({ onMicTapped() }, 500)
        }
    }

    override fun onPause() {
        cancelContinuousRestart()
        super.onPause()
    }

    override fun onDestroy() {
        cancelContinuousRestart()
        stopListening()
        speaker.shutdown()
        VoskManager.release()
        super.onDestroy()
    }

    private fun onMicTapped() {
        if (VoskManager.isListening()) {
            cancelContinuousRestart()
            stopListening()
            log("Stopped listening.")
            return
        }
        if (!VoskManager.isModelInstalled(this)) {
            log("No voice model yet — download it first.")
            openModelDownload()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            log("Microphone permission needed.")
            askForPermissionsIfNeeded()
            return
        }
        startListening()
    }

    private fun startListening() {
        WakeWordService.pause(this)
        binding.micButton.text = getString(R.string.listening_tap_to_stop)
        binding.heardText.text = getString(R.string.listening_hint)
        VoskManager.startListening(this, object : VoskManager.Listener {
            override fun onPartial(text: String) {
                binding.heardText.text = text
            }

            override fun onFinal(results: List<String>) {
                onHeard(results)
            }

            override fun onError(message: String) {
                stopListening()
                log(message)
                speaker.speak("Something went wrong while listening.")
            }

            override fun onTimeout() {
                stopListening()
                log("Didn't catch that.")
                speaker.speak("I didn't catch that.")
            }
        })
    }

    private fun stopListening() {
        VoskManager.stopListening()
        if (::binding.isInitialized) {
            binding.micButton.text = getString(R.string.tap_to_listen)
        }
        WakeWordService.resume(this)
    }

    private fun onHeard(results: List<String>) {
        stopListening()
        val text = results.firstOrNull().orEmpty()
        binding.heardText.text = "\"$text\""
        FawkesWidget.updateLastHeard(this, text)
        log(
            "Heard: \"$text\"" +
                if (results.size > 1) " (+${results.size - 1} alternatives)" else "",
        )
        CommandExecutor.executePhrase(this, results) { spoken, logLine ->
            log(logLine)
            speaker.speak(spoken)
            scheduleContinuousRestart(spoken)
        }
    }

    /**
     * Continuous listen: after a command finishes, start listening again so
     * commands chain without retapping. The restart waits for the spoken
     * confirmation to finish (roughly 15 chars/second, capped at 6 s) so the
     * mic doesn't pick up Fawkes' own voice and re-run the command.
     * Timeouts and errors don't restart — silence shouldn't loop forever.
     */
    private fun scheduleContinuousRestart(spoken: String) {
        cancelContinuousRestart()
        if (!isContinuousListen()) return
        val delayMs = (1_000L + spoken.length * 1000L / 15).coerceAtMost(6_000L)
        continuousRunnable = Runnable {
            if (isContinuousListen() && isResumed) onMicTapped()
        }
        mainHandler.postDelayed(continuousRunnable!!, delayMs)
    }

    private fun cancelContinuousRestart() {
        continuousRunnable?.let { mainHandler.removeCallbacks(it) }
        continuousRunnable = null
    }

    private fun isContinuousListen(): Boolean =
        getSharedPreferences("fawkes_prefs", MODE_PRIVATE)
            .getBoolean("continuous_listen", false)

    private fun setContinuousListen(enabled: Boolean) {
        getSharedPreferences("fawkes_prefs", MODE_PRIVATE)
            .edit().putBoolean("continuous_listen", enabled).apply()
    }

    private fun openModelDownload() {
        downloadLauncher.launch(Intent(this, ModelDownloadActivity::class.java))
    }

    private fun askForPermissionsIfNeeded() {
        val wanted = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wanted.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionsLauncher.launch(missing.toTypedArray())
        }
    }

    private fun refreshModelStatus() {
        val lang = VoskManager.currentLanguage(this)
        val installed = VoskManager.isModelInstalled(this)
        val langName = if (lang == "hi") "Hindi"
        else if (VoskManager.useLgraph(this)) "English (better accuracy)"
        else "English"
        binding.modelStatus.text = if (installed) {
            getString(R.string.model_ready) + " ($langName)"
        } else {
            getString(R.string.model_missing)
        }
        binding.downloadButton.isEnabled = !installed
    }

    private fun log(line: String) {
        binding.logView.append(line + "\n")
        binding.logScroll.post {
            binding.logScroll.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }

    companion object {
        /** Open MainActivity and start listening immediately (widget / tile / wake word). */
        const val EXTRA_LISTEN = "extra_listen"
    }
}
