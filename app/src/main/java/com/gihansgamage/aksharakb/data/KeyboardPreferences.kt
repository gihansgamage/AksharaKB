package com.gihansgamage.aksharakb.data

import android.content.Context
import android.content.SharedPreferences

class KeyboardPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("aksharakb_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_THEME            = "keyboard_theme"
        const val KEY_BG_IMAGE_URI     = "bg_image_uri"
        const val KEY_SHOW_BORDER      = "show_border"
        const val KEY_SINHALA_LAYOUT   = "sinhala_layout"
        const val KEY_ENABLED_LANGS    = "enabled_languages"
        const val KEY_CURRENT_LANG     = "current_language"
        const val KEY_VIBRATE          = "vibrate_on_key"
        const val KEY_SOUND            = "sound_on_key"
        const val KEY_KEY_HEIGHT       = "key_height"
        const val KEY_SHOW_PREDICTIONS = "show_predictions"
        const val KEY_SHOW_POPUP       = "show_popup_keys"
        const val KEY_SHOW_NUMPAD      = "show_number_pad"
        const val KEY_RECENT_EMOJIS    = "recent_emojis"

        // Two themes only
        const val THEME_LIGHT   = "default"   // kept as "default" for backwards compat
        const val THEME_DARK    = "dark"
        // Legacy aliases (map to one of the two)
        const val THEME_DEFAULT = THEME_LIGHT
        const val THEME_OCEAN   = THEME_DARK
        const val THEME_SUNSET  = THEME_DARK
        const val THEME_CUSTOM  = THEME_DARK

        const val LAYOUT_PHONETIC   = "phonetic"
        const val LAYOUT_WIJESEKARA = "wijesekara"

        const val LANG_EN = "EN"
        const val LANG_SI = "SI"
        const val LANG_TA = "TA"

        val ALL_LANGUAGES = listOf(LANG_EN, LANG_SI, LANG_TA)
    }

    var theme: String
        get() {
            val raw = prefs.getString(KEY_THEME, THEME_LIGHT) ?: THEME_LIGHT
            // Normalise legacy values to the two supported themes
            return when (raw) {
                THEME_DARK, "ocean", "sunset", "custom" -> THEME_DARK
                else -> THEME_LIGHT
            }
        }
        set(v) = prefs.edit().putString(KEY_THEME, v).apply()

    var bgImageUri: String
        get() = prefs.getString(KEY_BG_IMAGE_URI, "") ?: ""
        set(v) = prefs.edit().putString(KEY_BG_IMAGE_URI, v).apply()

    var showBorder: Boolean
        get() = prefs.getBoolean(KEY_SHOW_BORDER, false)
        set(v) = prefs.edit().putBoolean(KEY_SHOW_BORDER, v).apply()

    var sinhalaLayout: String
        get() = prefs.getString(KEY_SINHALA_LAYOUT, LAYOUT_PHONETIC) ?: LAYOUT_PHONETIC
        set(v) = prefs.edit().putString(KEY_SINHALA_LAYOUT, v).apply()

    fun isPhonetic(lang: String): Boolean {
        if (lang == LANG_EN) return false
        return sinhalaLayout == LAYOUT_PHONETIC
    }

    var enabledLanguages: List<String>
        get() {
            val raw = prefs.getString(KEY_ENABLED_LANGS, "$LANG_EN,$LANG_SI,$LANG_TA") ?: "$LANG_EN,$LANG_SI,$LANG_TA"
            return raw.split(",").filter { it.isNotBlank() }
        }
        set(v) = prefs.edit().putString(KEY_ENABLED_LANGS, v.joinToString(",")).apply()

    var currentLanguage: String
        get() = prefs.getString(KEY_CURRENT_LANG, LANG_EN) ?: LANG_EN
        set(v) = prefs.edit().putString(KEY_CURRENT_LANG, v).apply()

    var vibrateOnKey: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE, true)
        set(v) = prefs.edit().putBoolean(KEY_VIBRATE, v).apply()

    var soundOnKey: Boolean
        get() = prefs.getBoolean(KEY_SOUND, false)
        set(v) = prefs.edit().putBoolean(KEY_SOUND, v).apply()

    var keyHeight: Int
        get() = prefs.getInt(KEY_KEY_HEIGHT, 52)
        set(v) = prefs.edit().putInt(KEY_KEY_HEIGHT, v).apply()

    var showPredictions: Boolean
        get() = prefs.getBoolean(KEY_SHOW_PREDICTIONS, true)
        set(v) = prefs.edit().putBoolean(KEY_SHOW_PREDICTIONS, v).apply()

    var showPopupKeys: Boolean
        get() = prefs.getBoolean(KEY_SHOW_POPUP, true)
        set(v) = prefs.edit().putBoolean(KEY_SHOW_POPUP, v).apply()

    var showNumberPad: Boolean
        get() = prefs.getBoolean(KEY_SHOW_NUMPAD, true)
        set(v) = prefs.edit().putBoolean(KEY_SHOW_NUMPAD, v).apply()

    var recentEmojis: String
        get() = prefs.getString(KEY_RECENT_EMOJIS, "") ?: ""
        set(v) = prefs.edit().putString(KEY_RECENT_EMOJIS, v).apply()

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
}