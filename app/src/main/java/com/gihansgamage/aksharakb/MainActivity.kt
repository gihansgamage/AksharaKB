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
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun setupListeners() {
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
        binding.btnUserGuide.setOnClickListener {
            startActivity(Intent(this, UserGuideActivity::class.java))
        }
        binding.btnWebsite.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://gihansgamage.github.io/AksharaKB-web/"))
            startActivity(intent)
        }
    }

    private fun updateStatus() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        if (enabled) {
            binding.step1Card.alpha = 0.6f
            binding.step1Badge.text = "✓"
            binding.btnEnableKeyboard.text = "AksharaKB Enabled ✓"
            binding.btnEnableKeyboard.isEnabled = false
        } else {
            binding.step1Card.alpha = 1f
            binding.step1Badge.text = "1"
            binding.btnEnableKeyboard.text = getString(R.string.enable_keyboard)
            binding.btnEnableKeyboard.isEnabled = true
        }
    }
}