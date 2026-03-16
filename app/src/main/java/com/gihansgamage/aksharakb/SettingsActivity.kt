package com.gihansgamage.aksharakb

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.gihansgamage.aksharakb.data.KeyboardPreferences
import com.gihansgamage.aksharakb.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: KeyboardPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = KeyboardPreferences(this)
        applyAppTheme()
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()
        loadSettings()
        setupListeners()
    }

    private fun applyAppTheme() {
        if (prefs.theme == KeyboardPreferences.THEME_DARK) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            setTheme(R.style.Theme_AksharaKB)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            setTheme(R.style.Theme_AksharaKB_Light)
        }
    }

    private fun loadSettings() {
        binding.rgTheme.check(
            if (prefs.theme == KeyboardPreferences.THEME_DARK) R.id.rbThemeDark
            else R.id.rbThemeLight
        )
        binding.rgSinhalaLayout.check(
            if (prefs.sinhalaLayout == KeyboardPreferences.LAYOUT_WIJESEKARA)
                R.id.rbWijesekara else R.id.rbPhonetic
        )
        val langs = prefs.enabledLanguages
        binding.cbLangEn.isChecked = KeyboardPreferences.LANG_EN in langs
        binding.cbLangSi.isChecked = KeyboardPreferences.LANG_SI in langs
        binding.cbLangTa.isChecked = KeyboardPreferences.LANG_TA in langs
        binding.swBorder.isChecked    = prefs.showBorder
        binding.swNumberPad.isChecked = prefs.showNumberPad
        binding.seekKeyHeight.progress = prefs.keyHeight - 40
        binding.tvKeyHeightValue.text  = "${prefs.keyHeight}dp"
        binding.swVibrate.isChecked     = prefs.vibrateOnKey
        binding.swSound.isChecked       = prefs.soundOnKey
        binding.swPredictions.isChecked = prefs.showPredictions
        binding.swPopupKeys.isChecked   = prefs.showPopupKeys
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.rgTheme.setOnCheckedChangeListener { _, id ->
            val newTheme = if (id == R.id.rbThemeDark) KeyboardPreferences.THEME_DARK
            else KeyboardPreferences.THEME_LIGHT
            if (prefs.theme != newTheme) { prefs.theme = newTheme; recreate() }
        }

        binding.rgSinhalaLayout.setOnCheckedChangeListener { _, id ->
            prefs.sinhalaLayout = if (id == R.id.rbWijesekara)
                KeyboardPreferences.LAYOUT_WIJESEKARA else KeyboardPreferences.LAYOUT_PHONETIC
        }

        fun saveLangs() {
            val enabled = mutableListOf<String>()
            if (binding.cbLangEn.isChecked) enabled.add(KeyboardPreferences.LANG_EN)
            if (binding.cbLangSi.isChecked) enabled.add(KeyboardPreferences.LANG_SI)
            if (binding.cbLangTa.isChecked) enabled.add(KeyboardPreferences.LANG_TA)
            if (enabled.isEmpty()) {
                enabled.add(KeyboardPreferences.LANG_EN); binding.cbLangEn.isChecked = true
                Toast.makeText(this, "At least one language must be enabled", Toast.LENGTH_SHORT).show()
            }
            prefs.enabledLanguages = enabled
        }
        binding.cbLangEn.setOnCheckedChangeListener { _, _ -> saveLangs() }
        binding.cbLangSi.setOnCheckedChangeListener { _, _ -> saveLangs() }
        binding.cbLangTa.setOnCheckedChangeListener { _, _ -> saveLangs() }

        binding.swBorder.setOnCheckedChangeListener    { _, v -> prefs.showBorder    = v }
        binding.swNumberPad.setOnCheckedChangeListener { _, v -> prefs.showNumberPad = v }

        binding.seekKeyHeight.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                    val h = p + 40; prefs.keyHeight = h; binding.tvKeyHeightValue.text = "${h}dp"
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
            })

        binding.swVibrate.setOnCheckedChangeListener     { _, v -> prefs.vibrateOnKey    = v }
        binding.swSound.setOnCheckedChangeListener       { _, v -> prefs.soundOnKey      = v }
        binding.swPredictions.setOnCheckedChangeListener { _, v -> prefs.showPredictions = v }
        binding.swPopupKeys.setOnCheckedChangeListener   { _, v -> prefs.showPopupKeys   = v }
    }
}