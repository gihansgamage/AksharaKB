package com.gihansgamage.aksharakb

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.gihansgamage.aksharakb.data.KeyboardPreferences

class MyInputMethodService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private var keyboardView: MyKeyboardView? = null
    private var keyboard: Keyboard? = null
    private var candidatesContainer: LinearLayout? = null
    private var wordPredictor: WordPredictor? = null
    private var prefs: KeyboardPreferences? = null

    // State
    private var isCaps = false
    private var isSymbols = false
    private var currentLang = KeyboardPreferences.LANG_EN
    private var currentInput = StringBuilder()

    override fun onCreate() {
        super.onCreate()
        prefs = KeyboardPreferences(this)
        wordPredictor = WordPredictor(this)
        currentLang = prefs?.currentLanguage ?: KeyboardPreferences.LANG_EN
    }

    override fun onCreateInputView(): View {
        val inputView = layoutInflater.inflate(R.layout.keyboard_view, null)
        keyboardView = inputView.findViewById(R.id.keyboard_view)
        candidatesContainer = inputView.findViewById(R.id.candidates_container)

        setKeyboardLayout()
        keyboardView?.setOnKeyboardActionListener(this)
        keyboardView?.isPreviewEnabled = true

        updateCandidates("")
        return inputView
    }

    private fun setKeyboardLayout() {
        val xmlId = when {
            isSymbols -> R.xml.symbols
            currentLang == KeyboardPreferences.LANG_SI -> {
                if (prefs?.sinhalaLayout == KeyboardPreferences.LAYOUT_WIJESEKARA)
                    R.xml.wijesekara
                else
                    R.xml.sinhala_phonetic
            }
            currentLang == KeyboardPreferences.LANG_TA -> R.xml.tamil
            else -> R.xml.qwerty
        }

        keyboard = Keyboard(this, xmlId)
        keyboardView?.keyboard = keyboard
        applyCapsState()
    }

    private fun applyCapsState() {
        keyboard?.isShifted = isCaps
        keyboardView?.invalidateAllKeys()
    }

    // ---------- KeyboardView.OnKeyboardActionListener ----------

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return
        vibrateKey()

        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> {
                ic.deleteSurroundingText(1, 0)
                if (currentInput.isNotEmpty()) {
                    currentInput.deleteCharAt(currentInput.length - 1)
                }
                updateCandidates(currentInput.toString())
            }

            Keyboard.KEYCODE_SHIFT -> {
                isCaps = !isCaps
                applyCapsState()
            }

            Keyboard.KEYCODE_MODE_CHANGE -> {
                isSymbols = !isSymbols
                setKeyboardLayout()
            }

            Keyboard.KEYCODE_DONE -> {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                learnCurrentWord()
                currentInput.clear()
                updateCandidates("")
            }

            -10 -> switchLanguage(KeyboardPreferences.LANG_EN)
            -11 -> switchLanguage(KeyboardPreferences.LANG_SI)
            -12 -> switchLanguage(KeyboardPreferences.LANG_TA)

            32 -> { // Space
                ic.commitText(" ", 1)
                learnCurrentWord()
                currentInput.clear()
                updateCandidates("")
            }

            else -> {
                if (primaryCode > 0) {
                    var char = primaryCode.toChar()
                    if (isCaps && char.isLetter()) char = char.uppercaseChar()
                    val text = char.toString()
                    ic.commitText(text, 1)
                    currentInput.append(char)
                    updateCandidates(currentInput.toString())

                    // Auto-disable caps after typing one letter (shift mode)
                    if (isCaps) {
                        isCaps = false
                        applyCapsState()
                    }
                }
            }
        }
    }

    private fun updateCandidates(input: String) {
        val container = candidatesContainer ?: return
        container.removeAllViews()

        if (!(prefs?.showPredictions ?: true)) return

        val suggestions = mutableListOf<String>()

        // Get word suggestions
        suggestions.addAll(wordPredictor?.getSuggestions(input, currentLang) ?: emptyList())

        // Add emojis if typing English
        if (currentLang == KeyboardPreferences.LANG_EN && input.isNotEmpty()) {
            suggestions.addAll(wordPredictor?.getEmojiSuggestions(input) ?: emptyList())
        }

        suggestions.take(7).forEach { word ->
            val tv = TextView(this).apply {
                text = word
                textSize = 16f
                setPadding(24, 8, 24, 8)
                setTextColor(getCandidateTextColor())
                setBackgroundResource(R.drawable.candidate_bg)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).also { it.setMargins(4, 4, 4, 4) }

                setOnClickListener {
                    commitSuggestion(word)
                }
            }
            container.addView(tv)
        }
    }

    private fun commitSuggestion(word: String) {
        val ic = currentInputConnection ?: return

        // Delete current partial input
        if (currentInput.isNotEmpty()) {
            ic.deleteSurroundingText(currentInput.length, 0)
        }

        ic.commitText("$word ", 1)
        wordPredictor?.learnWord(word)
        currentInput.clear()
        updateCandidates("")
    }

    private fun learnCurrentWord() {
        if (currentInput.isNotEmpty()) {
            wordPredictor?.learnWord(currentInput.toString())
        }
    }

    private fun getCandidateTextColor(): Int {
        return when (prefs?.theme) {
            KeyboardPreferences.THEME_DARK,
            KeyboardPreferences.THEME_OCEAN,
            KeyboardPreferences.THEME_SUNSET -> android.graphics.Color.WHITE
            else -> android.graphics.Color.BLACK
        }
    }

    fun switchLanguage(lang: String) {
        currentLang = lang
        prefs?.currentLanguage = lang
        isSymbols = false
        isCaps = false
        currentInput.clear()
        setKeyboardLayout()
        updateCandidates("")
    }

    private fun vibrateKey() {
        if (!(prefs?.vibrateOnKey ?: true)) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(20)
                }
            }
        } catch (_: Exception) {}
    }

    // ---------- Swipe to cycle language ----------
    override fun swipeLeft() {
        val next = when (currentLang) {
            KeyboardPreferences.LANG_EN -> KeyboardPreferences.LANG_SI
            KeyboardPreferences.LANG_SI -> KeyboardPreferences.LANG_TA
            else -> KeyboardPreferences.LANG_EN
        }
        switchLanguage(next)
    }

    override fun swipeRight() {
        val next = when (currentLang) {
            KeyboardPreferences.LANG_EN -> KeyboardPreferences.LANG_TA
            KeyboardPreferences.LANG_TA -> KeyboardPreferences.LANG_SI
            else -> KeyboardPreferences.LANG_EN
        }
        switchLanguage(next)
    }

    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {
        currentInputConnection?.commitText(text, 1)
    }
    override fun swipeDown() {}
    override fun swipeUp() {}
}