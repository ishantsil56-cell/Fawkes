package com.ishantsil.fawkes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.ishantsil.fawkes.commands.CommandMatcher
import com.ishantsil.fawkes.databinding.ActivityMainBinding
import com.ishantsil.fawkes.tts.Speaker
import com.ishantsil.fawkes.voice.ModelDownloadActivity
import com.ishantsil.fawkes.voice.VoskManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var speaker: Speaker

    private val downloadLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshModelStatus() }

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            log("Microphone permission granted.")
        } else {
            log("Microphone permission denied — voice input needs it.")
            Toast.makeText(this, "Fawkes needs the microphone to listen.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        speaker = Speaker(this)

        binding.micButton.setOnClickListener { onMicTapped() }
        binding.downloadButton.setOnClickListener { openModelDownload() }

        askForMicrophoneIfNeeded()
        refreshModelStatus()
        log("Fawkes 0.1.0 — Phase 0 scaffold. Tap-to-listen lands in Phase 1.")
    }

    override fun onResume() {
        super.onResume()
        refreshModelStatus()
    }

    override fun onDestroy() {
        speaker.shutdown()
        VoskManager.release()
        super.onDestroy()
    }

    private fun onMicTapped() {
        if (!VoskManager.isModelInstalled(this)) {
            log("No voice model yet — download it first.")
            openModelDownload()
            return
        }
        // Phase 1 wires this to live recognition. For now, prove the matcher path.
        val result = CommandMatcher.match("flashlight on")
        log("Heard (test): \"flashlight on\" → ${result.describe()}")
        speaker.speak("Voice commands arrive in phase one.")
    }

    private fun openModelDownload() {
        downloadLauncher.launch(Intent(this, ModelDownloadActivity::class.java))
    }

    private fun askForMicrophoneIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun refreshModelStatus() {
        val installed = VoskManager.isModelInstalled(this)
        binding.modelStatus.text = if (installed) {
            getString(R.string.model_ready)
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
}
