package com.ishantsil.fawkes.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ishantsil.fawkes.R
import com.ishantsil.fawkes.databinding.ActivityHelpBinding

/** The full command list, on one scrollable screen. */
class HelpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.helpText.text = getString(R.string.help_text)
    }
}
