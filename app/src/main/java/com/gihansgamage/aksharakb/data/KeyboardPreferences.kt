package com.gihansgamage.aksharakb.data

import android.content.Context
import android.content.SharedPreferences

class KeyboardPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("aksharakb_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_THEME = "keyboard_theme"
        const val KEY_BG_IMAGE_URI = "bg_image_uri"
        const val KEY_SHOW_BORDER = "show_border"
        const val KEY_SINHALA_LAYOUT = "sinhala_layout"
        const val KEY_LANGUAGE = "current_language"
        const val KEY_VIBRATE = "vibrate_on_key"
        const val KEY_SOUND = "sound_on_key"
        const val KEY_KEY_HEIGHT = "key_height"
        const val KEY_SHOW_PREDICTIONS = "show_predictions"

        const val THEME_DEFAULT = "default"
        const val THEME_DARK = "dark"
        const val THEME_OCEAN = "ocean"
        const val THEME_SUNSET = "sunset"
        const val THEME_CUSTOM = "custom"

        const val LAYOUT_QWERTY = "qwerty"
        const val LAYOUT_WIJESEKARA = "wijesekara"

        const val LANG_EN = "EN"
        const val LANG_SI = "SI"
        const val LANG_TA = "TA"
    }

    var theme: String
        get() = prefs.getString(KEY_THEME, THEME_DEFAULT) ?: THEME_DEFAULT
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    var bgImageUri: String
        get() = prefs.getString(KEY_BG_IMAGE_URI, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BG_IMAGE_URI, value).apply()

    var showBorder: Boolean
        get() = prefs.getBoolean(KEY_SHOW_BORDER, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_BORDER, value).apply()

    var sinhalaLayout: String
        get() = prefs.getString(KEY_SINHALA_LAYOUT, LAYOUT_QWERTY) ?: LAYOUT_QWERTY
        set(value) = prefs.edit().putString(KEY_SINHALA_LAYOUT, value).apply()

    var currentLanguage: String
        get() = prefs.getString(KEY_LANGUAGE, LANG_EN) ?: LANG_EN
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    var vibrateOnKey: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATE, value).apply()

    var soundOnKey: Boolean
        get() = prefs.getBoolean(KEY_SOUND, false)
        set(value) = prefs.edit().putBoolean(KEY_SOUND, value).apply()

    var keyHeight: Int
        get() = prefs.getInt(KEY_KEY_HEIGHT, 50)
        set(value) = prefs.edit().putInt(KEY_KEY_HEIGHT, value).apply()

    var showPredictions: Boolean
        get() = prefs.getBoolean(KEY_SHOW_PREDICTIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_PREDICTIONS, value).apply()
}