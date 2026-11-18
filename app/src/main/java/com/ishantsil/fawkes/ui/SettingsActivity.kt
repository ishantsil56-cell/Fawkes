package com.ishantsil.fawkes.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.ishantsil.fawkes.databinding.ActivitySettingsBinding
import com.ishantsil.fawkes.voice.LlmManager
import com.ishantsil.fawkes.voice.ModelDownloadActivity
import com.ishantsil.fawkes.voice.VoskManager
import com.ishantsil.fawkes.voice.WakeWordService

/** Settings: voice speed, language, wake word, models, on-device AI. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val modelImportLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        binding.llmStatus.text = "Importing model… (a 2 GB file takes a while)"
        LlmManager.importModel(this, uri) { _, message ->
            binding.llmStatus.text = message
            refreshLlmStatus()
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Voice speed (0.5x – 2.0x).
        val rate = speakerRate()
        binding.rateSeek.progress = ((rate - 0.5f) / 1.5f * 100).toInt().coerceIn(0, 100)
        binding.rateLabel.text = "Voice speed: ${"%.1f".format(rate)}x"
        binding.rateSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val newRate = 0.5f + progress / 100f * 1.5f
                setSpeakerRate(newRate)
                binding.rateLabel.text = "Voice speed: ${"%.1f".format(newRate)}x"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        // Language.
        val lang = VoskManager.currentLanguage(this)
        if (lang == "hi") {
            binding.langHindi.isChecked = true
        } else {
            binding.langEnglish.isChecked = true
        }
        binding.languageGroup.setOnCheckedChangeListener { _, checkedId ->
            val newLang = if (checkedId == binding.langHindi.id) "hi" else "en"
            if (!VoskManager.isModelInstalled(this, newLang)) {
                val name = if (newLang == "hi") "Hindi" else "English"
                Toast.makeText(this, "Download the $name model first.", Toast.LENGTH_LONG).show()
                openModelDownload()
                // Revert the radio selection.
                if (newLang == "hi") {
                    binding.langEnglish.isChecked = true
                } else {
                    binding.langHindi.isChecked = true
                }
                return@setOnCheckedChangeListener
            }
            VoskManager.setLanguage(this, newLang)
            refreshModelStatus()
        }

        // Wake word.
        binding.wakeSwitch.isChecked = WakeWordService.isEnabled(this)
        binding.wakeSwitch.setOnCheckedChangeListener { _, enabled ->
            if (enabled && !VoskManager.isModelInstalled(this)) {
                Toast.makeText(this, "Download the voice model first.", Toast.LENGTH_LONG).show()
                binding.wakeSwitch.isChecked = false
                return@setOnCheckedChangeListener
            }
            WakeWordService.setEnabled(this, enabled)
        }
        binding.batteryButton.setOnClickListener {
            // Opens the battery-optimisation list so Fawkes can be exempted
            // (Samsung aggressively kills background listeners otherwise).
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(this, "Couldn't open battery settings.", Toast.LENGTH_SHORT).show()
            }
        }

        // Better-accuracy English model.
        binding.lgraphSwitch.isChecked = VoskManager.useLgraph(this)
        binding.lgraphSwitch.setOnCheckedChangeListener { _, enabled ->
            if (enabled && !VoskManager.isModelDirInstalled(this, VoskManager.MODEL_EN_LGRAPH)) {
                Toast.makeText(this, "Download the better-accuracy model first.", Toast.LENGTH_LONG).show()
                openModelDownload()
                binding.lgraphSwitch.isChecked = false
                return@setOnCheckedChangeListener
            }
            if (!enabled && !VoskManager.isModelDirInstalled(this, VoskManager.MODEL_EN)) {
                Toast.makeText(this, "Download the standard English model first.", Toast.LENGTH_LONG).show()
                openModelDownload()
                binding.lgraphSwitch.isChecked = true
                return@setOnCheckedChangeListener
            }
            VoskManager.setUseLgraph(this, enabled)
            refreshModelStatus()
        }

        // Models.
        binding.modelsButton.setOnClickListener { openModelDownload() }

        // On-device AI.
        binding.llmImportButton.setOnClickListener {
            modelImportLauncher.launch("*/*")
        }
        binding.llmHelp.text =
            "On-device answers need a Gemma model file (~2 GB). Download " +
                "gemma-2b-it-gpu-int4.bin from Kaggle (free account, accept the " +
                "Gemma licence), then import it here. Answers run fully offline " +
                "but are slow on this phone — expect 30–60 seconds for the " +
                "first answer."

        refreshModelStatus()
        refreshLlmStatus()
    }

    private fun refreshModelStatus() {
        val en = VoskManager.isModelDirInstalled(this, VoskManager.MODEL_EN)
        val lgraph = VoskManager.isModelDirInstalled(this, VoskManager.MODEL_EN_LGRAPH)
        val hi = VoskManager.isModelDirInstalled(this, VoskManager.MODEL_HI)
        val active = when (VoskManager.currentModelKey(this)) {
            VoskManager.MODEL_KEY_EN_LGRAPH -> "English (better accuracy)"
            VoskManager.MODEL_KEY_HI -> "Hindi"
            else -> "English"
        }
        binding.modelStatus.text =
            "English model: ${if (en) "installed" else "not installed"}\n" +
                "Better-accuracy English model: ${if (lgraph) "installed" else "not installed"}\n" +
                "Hindi model: ${if (hi) "installed" else "not installed"}\n" +
                "Active: $active"
    }

    private fun refreshLlmStatus() {
        binding.llmStatus.text = if (LlmManager.isModelInstalled(this)) {
            "AI model: installed (${LlmManager.modelSizeMb(this)} MB). " +
                "Say \"ask …\" to use it."
        } else {
            "AI model: not installed. Import a Gemma .bin file to enable \"ask\" answers."
        }
    }

    private fun openModelDownload() {
        startActivity(Intent(this, ModelDownloadActivity::class.java))
    }

    private fun speakerRate(): Float =
        getSharedPreferences("fawkes_prefs", MODE_PRIVATE).getFloat("speech_rate", 1.0f)

    private fun setSpeakerRate(rate: Float) {
        getSharedPreferences("fawkes_prefs", MODE_PRIVATE)
            .edit().putFloat("speech_rate", rate).apply()
    }
}
