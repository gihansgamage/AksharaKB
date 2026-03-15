package com.gihansgamage.aksharakb

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.gihansgamage.aksharakb.data.KeyboardPreferences
import com.gihansgamage.aksharakb.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: KeyboardPreferences

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            prefs.bgImageUri = uri.toString()
            prefs.theme = KeyboardPreferences.THEME_CUSTOM
            // Deselect all theme radio buttons
            binding.rgTheme.clearCheck()
            Toast.makeText(this, "Background image set!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        prefs = KeyboardPreferences(this)
        loadSettings()
        setupListeners()
    }

    private fun loadSettings() {
        // Theme
        when (prefs.theme) {
            KeyboardPreferences.THEME_DEFAULT -> binding.rgTheme.check(R.id.rbThemeDefault)
            KeyboardPreferences.THEME_DARK    -> binding.rgTheme.check(R.id.rbThemeDark)
            KeyboardPreferences.THEME_OCEAN   -> binding.rgTheme.check(R.id.rbThemeOcean)
            KeyboardPreferences.THEME_SUNSET  -> binding.rgTheme.check(R.id.rbThemeSunset)
        }

        // Border
        binding.swBorder.isChecked = prefs.showBorder

        // Sinhala layout
        binding.rgSinhalaLayout.check(
            if (prefs.sinhalaLayout == KeyboardPreferences.LAYOUT_WIJESEKARA)
                R.id.rbWijesekara else R.id.rbPhonetic
        )

        // Enabled languages checkboxes
        val langs = prefs.enabledLanguages
        binding.cbLangEn.isChecked = KeyboardPreferences.LANG_EN in langs
        binding.cbLangSi.isChecked = KeyboardPreferences.LANG_SI in langs
        binding.cbLangTa.isChecked = KeyboardPreferences.LANG_TA in langs

        // Typing
        binding.swVibrate.isChecked     = prefs.vibrateOnKey
        binding.swSound.isChecked       = prefs.soundOnKey
        binding.swPredictions.isChecked = prefs.showPredictions
        binding.swPopupKeys.isChecked   = prefs.showPopupKeys
        binding.swNumberPad.isChecked   = prefs.showNumberPad

        // Key height
        val heightProgress = prefs.keyHeight - 40
        binding.seekKeyHeight.progress = heightProgress
        binding.tvKeyHeightValue.text  = "${prefs.keyHeight}dp"
    }

    private fun setupListeners() {
        // Back button
        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // Theme
        binding.rgTheme.setOnCheckedChangeListener { _, id ->
            prefs.theme = when (id) {
                R.id.rbThemeDark   -> KeyboardPreferences.THEME_DARK
                R.id.rbThemeOcean  -> KeyboardPreferences.THEME_OCEAN
                R.id.rbThemeSunset -> KeyboardPreferences.THEME_SUNSET
                else               -> KeyboardPreferences.THEME_DEFAULT
            }
        }

        // Custom background
        binding.btnSetBg.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            imagePicker.launch(intent)
        }
        binding.btnClearBg.setOnClickListener {
            prefs.bgImageUri = ""
            prefs.theme = KeyboardPreferences.THEME_DEFAULT
            binding.rgTheme.check(R.id.rbThemeDefault)
            Toast.makeText(this, "Background cleared", Toast.LENGTH_SHORT).show()
        }

        // Border
        binding.swBorder.setOnCheckedChangeListener { _, v -> prefs.showBorder = v }

        // Sinhala layout
        binding.rgSinhalaLayout.setOnCheckedChangeListener { _, id ->
            prefs.sinhalaLayout = if (id == R.id.rbWijesekara)
                KeyboardPreferences.LAYOUT_WIJESEKARA else KeyboardPreferences.LAYOUT_PHONETIC
        }

        // Language checkboxes — save whenever any changes
        fun saveLangs() {
            val enabled = mutableListOf<String>()
            if (binding.cbLangEn.isChecked) enabled.add(KeyboardPreferences.LANG_EN)
            if (binding.cbLangSi.isChecked) enabled.add(KeyboardPreferences.LANG_SI)
            if (binding.cbLangTa.isChecked) enabled.add(KeyboardPreferences.LANG_TA)
            if (enabled.isEmpty()) {
                // Must keep at least one language
                enabled.add(KeyboardPreferences.LANG_EN)
                binding.cbLangEn.isChecked = true
                Toast.makeText(this, "At least one language must be enabled", Toast.LENGTH_SHORT).show()
            }
            prefs.enabledLanguages = enabled
        }
        binding.cbLangEn.setOnCheckedChangeListener { _, _ -> saveLangs() }
        binding.cbLangSi.setOnCheckedChangeListener { _, _ -> saveLangs() }
        binding.cbLangTa.setOnCheckedChangeListener { _, _ -> saveLangs() }

        // Typing toggles
        binding.swVibrate.setOnCheckedChangeListener     { _, v -> prefs.vibrateOnKey     = v }
        binding.swSound.setOnCheckedChangeListener       { _, v -> prefs.soundOnKey       = v }
        binding.swPredictions.setOnCheckedChangeListener { _, v -> prefs.showPredictions  = v }
        binding.swPopupKeys.setOnCheckedChangeListener   { _, v -> prefs.showPopupKeys    = v }
        binding.swNumberPad.setOnCheckedChangeListener   { _, v -> prefs.showNumberPad    = v }

        // Key height seeker
        binding.seekKeyHeight.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                    val h = p + 40
                    prefs.keyHeight = h
                    binding.tvKeyHeightValue.text = "${h}dp"
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
            }
        )
    }
}