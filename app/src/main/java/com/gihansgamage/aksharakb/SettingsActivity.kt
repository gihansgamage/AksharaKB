package com.gihansgamage.aksharakb

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.gihansgamage.aksharakb.data.KeyboardPreferences
import com.gihansgamage.aksharakb.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: KeyboardPreferences

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            // Persist URI permission
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            prefs.bgImageUri = uri.toString()
            prefs.theme = KeyboardPreferences.THEME_CUSTOM
            binding.rgTheme.clearCheck()
            Toast.makeText(this, "Background image set!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)

        prefs = KeyboardPreferences(this)
        loadCurrentSettings()
        setupListeners()
    }

    private fun loadCurrentSettings() {
        // Theme
        when (prefs.theme) {
            KeyboardPreferences.THEME_DEFAULT -> binding.rgTheme.check(R.id.rb_theme_default)
            KeyboardPreferences.THEME_DARK -> binding.rgTheme.check(R.id.rb_theme_dark)
            KeyboardPreferences.THEME_OCEAN -> binding.rgTheme.check(R.id.rb_theme_ocean)
            KeyboardPreferences.THEME_SUNSET -> binding.rgTheme.check(R.id.rb_theme_sunset)
        }

        // Border
        binding.swBorder.isChecked = prefs.showBorder

        // Sinhala layout
        if (prefs.sinhalaLayout == KeyboardPreferences.LAYOUT_WIJESEKARA) {
            binding.rgSinhalaLayout.check(R.id.rb_wijesekara)
        } else {
            binding.rgSinhalaLayout.check(R.id.rb_phonetic)
        }

        // Vibration
        binding.swVibrate.isChecked = prefs.vibrateOnKey

        // Sound
        binding.swSound.isChecked = prefs.soundOnKey

        // Predictions
        binding.swPredictions.isChecked = prefs.showPredictions

        // Key height
        binding.seekKeyHeight.progress = prefs.keyHeight - 40 // offset: min 40dp
        binding.tvKeyHeightValue.text = "${prefs.keyHeight}dp"
    }

    private fun setupListeners() {
        // Theme selection
        binding.rgTheme.setOnCheckedChangeListener { _, id ->
            prefs.theme = when (id) {
                R.id.rb_theme_dark -> KeyboardPreferences.THEME_DARK
                R.id.rb_theme_ocean -> KeyboardPreferences.THEME_OCEAN
                R.id.rb_theme_sunset -> KeyboardPreferences.THEME_SUNSET
                else -> KeyboardPreferences.THEME_DEFAULT
            }
        }

        // Custom background image
        binding.btnSetBg.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            imagePickerLauncher.launch(intent)
        }

        // Clear image
        binding.btnClearBg.setOnClickListener {
            prefs.bgImageUri = ""
            prefs.theme = KeyboardPreferences.THEME_DEFAULT
            binding.rgTheme.check(R.id.rb_theme_default)
            Toast.makeText(this, "Background cleared", Toast.LENGTH_SHORT).show()
        }

        // Border toggle
        binding.swBorder.setOnCheckedChangeListener { _, isChecked ->
            prefs.showBorder = isChecked
        }

        // Sinhala layout
        binding.rgSinhalaLayout.setOnCheckedChangeListener { _, id ->
            prefs.sinhalaLayout = if (id == R.id.rb_wijesekara)
                KeyboardPreferences.LAYOUT_WIJESEKARA
            else
                KeyboardPreferences.LAYOUT_QWERTY
        }

        // Vibrate toggle
        binding.swVibrate.setOnCheckedChangeListener { _, isChecked ->
            prefs.vibrateOnKey = isChecked
        }

        // Sound toggle
        binding.swSound.setOnCheckedChangeListener { _, isChecked ->
            prefs.soundOnKey = isChecked
        }

        // Predictions toggle
        binding.swPredictions.setOnCheckedChangeListener { _, isChecked ->
            prefs.showPredictions = isChecked
        }

        // Key height seeker
        binding.seekKeyHeight.setOnSeekBarChangeListener(object :
            android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: android.widget.SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                val height = progress + 40 // offset
                prefs.keyHeight = height
                binding.tvKeyHeightValue.text = "${height}dp"
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}