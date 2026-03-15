package com.gihansgamage.aksharakb

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import com.gihansgamage.aksharakb.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        updateSetupStatus()
    }

    override fun onResume() {
        super.onResume()
        updateSetupStatus()
    }

    private fun setupClickListeners() {
        binding.btnEnableKeyboard.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        binding.btnSelectKeyboard.setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        binding.btnOpenSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun updateSetupStatus() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val enabledMethods = imm.enabledInputMethodList
        val isEnabled = enabledMethods.any {
            it.packageName == packageName
        }

        if (isEnabled) {
            binding.step1Status.setImageResource(R.drawable.ic_check)
            binding.btnEnableKeyboard.text = getString(R.string.step1_done)
        } else {
            binding.step1Status.setImageResource(R.drawable.ic_pending)
            binding.btnEnableKeyboard.text = getString(R.string.enable_keyboard)
        }
    }
}