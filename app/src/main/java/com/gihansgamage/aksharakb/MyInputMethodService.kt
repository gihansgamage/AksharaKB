package com.gihansgamage.aksharakb

import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.TypedValue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.gihansgamage.aksharakb.data.KeyboardPreferences

class MyInputMethodService : InputMethodService(),
    KeyboardView.OnKeyboardActionListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private var keyboardView: MyKeyboardView? = null
    private var keyboard: Keyboard? = null
    private var candidatesContainer: LinearLayout? = null
    private var candidatesScroll: HorizontalScrollView? = null
    private var wordPredictor: WordPredictor? = null
    private var clipboard: KeyboardClipboard? = null
    private var prefs: KeyboardPreferences? = null

    // State
    private var isCaps       = false
    private var isSymbols    = false
    private var showClipboard= false
    private var currentInput = StringBuilder()

    // ──────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        prefs      = KeyboardPreferences(this)
        wordPredictor = WordPredictor(this)
        clipboard  = KeyboardClipboard(this)
        prefs?.registerListener(this)
    }

    override fun onDestroy() {
        prefs?.unregisterListener(this)
        super.onDestroy()
    }

    /** Called whenever SharedPreferences change — live-reload keyboard */
    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        when (key) {
            KeyboardPreferences.KEY_KEY_HEIGHT,
            KeyboardPreferences.KEY_THEME,
            KeyboardPreferences.KEY_BG_IMAGE_URI,
            KeyboardPreferences.KEY_SHOW_BORDER,
            KeyboardPreferences.KEY_SINHALA_LAYOUT,
            KeyboardPreferences.KEY_SHOW_NUMPAD,
            KeyboardPreferences.KEY_SHOW_POPUP -> {
                applyCurrentPrefs()
            }
            KeyboardPreferences.KEY_ENABLED_LANGS -> {
                // If current lang was removed, fall back to first enabled
                val enabled = prefs?.enabledLanguages ?: listOf(KeyboardPreferences.LANG_EN)
                val cur = prefs?.currentLanguage ?: KeyboardPreferences.LANG_EN
                if (cur !in enabled) {
                    prefs?.currentLanguage = enabled.firstOrNull() ?: KeyboardPreferences.LANG_EN
                }
                setKeyboardLayout()
            }
        }
    }

    // ──────────────────────────────────────────────
    // View creation
    // ──────────────────────────────────────────────

    override fun onCreateInputView(): View {
        val inputView = LayoutInflater.from(this).inflate(R.layout.keyboard_view, null)
        keyboardView         = inputView.findViewById(R.id.keyboard_view)
        candidatesContainer  = inputView.findViewById(R.id.candidates_container)
        candidatesScroll     = inputView.findViewById(R.id.candidates_view)

        keyboardView?.setOnKeyboardActionListener(this)
        applyCurrentPrefs()
        updateCandidates("")
        return inputView
    }

    // ──────────────────────────────────────────────
    // Prefs → View
    // ──────────────────────────────────────────────

    private fun applyCurrentPrefs() {
        val p = prefs ?: return
        val kv = keyboardView ?: return

        // Key height (dp → px)
        val heightPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            p.keyHeight.toFloat(),
            resources.displayMetrics
        ).toInt()
        kv.layoutParams = (kv.layoutParams ?: ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )).also {
            (it as? ViewGroup.LayoutParams)?.height = ViewGroup.LayoutParams.WRAP_CONTENT
        }

        // Background image
        if (p.theme == KeyboardPreferences.THEME_CUSTOM && p.bgImageUri.isNotEmpty()) {
            try {
                val uri = Uri.parse(p.bgImageUri)
                val stream = contentResolver.openInputStream(uri)
                val bmp = BitmapFactory.decodeStream(stream)
                stream?.close()
                kv.setKeyboardImage(bmp)
            } catch (_: Exception) {
                kv.setKeyboardImage(null)
            }
        } else {
            kv.setKeyboardImage(null)
        }

        // Popup keys
        kv.isPreviewEnabled = p.showPopupKeys
        kv.refreshPrefs()

        setKeyboardLayout()
    }

    private fun setKeyboardLayout() {
        val p = prefs ?: return
        val lang = p.currentLanguage
        val xmlId = when {
            isSymbols -> R.xml.symbols
            lang == KeyboardPreferences.LANG_SI ->
                if (p.sinhalaLayout == KeyboardPreferences.LAYOUT_WIJESEKARA) R.xml.wijesekara
                else R.xml.sinhala_phonetic
            lang == KeyboardPreferences.LANG_TA -> R.xml.tamil
            else -> if (p.showNumberPad) R.xml.qwerty else R.xml.qwerty_no_numbers
        }
        keyboard = Keyboard(this, xmlId)
        keyboardView?.keyboard = keyboard
        keyboard?.isShifted = isCaps
        keyboardView?.invalidateAllKeys()
    }

    // ──────────────────────────────────────────────
    // Key handling
    // ──────────────────────────────────────────────

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return
        vibrateKey()

        // Dismiss clipboard panel on any key
        if (showClipboard && primaryCode != -20) {
            showClipboard = false
            updateCandidates(currentInput.toString())
        }

        when (primaryCode) {

            Keyboard.KEYCODE_DELETE -> {
                ic.deleteSurroundingText(1, 0)
                if (currentInput.isNotEmpty()) currentInput.deleteCharAt(currentInput.length - 1)
                updateCandidates(currentInput.toString())
            }

            Keyboard.KEYCODE_SHIFT -> {
                isCaps = !isCaps
                keyboard?.isShifted = isCaps
                keyboardView?.invalidateAllKeys()
            }

            Keyboard.KEYCODE_MODE_CHANGE -> {   // ?123 / ABC toggle
                isSymbols = !isSymbols
                setKeyboardLayout()
            }

            Keyboard.KEYCODE_DONE -> {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                learnAndReset()
            }

            -10 -> cycleLang()          // 🌐 single lang-cycle key

            -20 -> {                    // clipboard key
                showClipboard = !showClipboard
                if (showClipboard) showClipboardPanel() else updateCandidates(currentInput.toString())
            }

            32 -> {                     // space
                ic.commitText(" ", 1)
                learnAndReset()
            }

            else -> {
                if (primaryCode > 0) {
                    var ch = primaryCode.toChar()
                    if (isCaps && ch.isLetter()) ch = ch.uppercaseChar()
                    ic.commitText(ch.toString(), 1)
                    currentInput.append(ch)
                    updateCandidates(currentInput.toString())
                    if (isCaps) { isCaps = false; keyboard?.isShifted = false; keyboardView?.invalidateAllKeys() }
                }
            }
        }
    }

    // ──────────────────────────────────────────────
    // Language cycling  (only cycles enabled langs)
    // ──────────────────────────────────────────────

    private fun cycleLang() {
        val enabled = prefs?.enabledLanguages ?: listOf(KeyboardPreferences.LANG_EN)
        if (enabled.size <= 1) return
        val cur   = prefs?.currentLanguage ?: KeyboardPreferences.LANG_EN
        val idx   = enabled.indexOf(cur)
        val next  = enabled[(idx + 1) % enabled.size]
        prefs?.currentLanguage = next
        isSymbols = false
        isCaps    = false
        currentInput.clear()
        setKeyboardLayout()
        updateCandidates("")
    }

    /** Label for the lang-cycle key e.g. "EN→SI" or "SI→TA" */
    fun getLangCycleLabel(): String {
        val enabled = prefs?.enabledLanguages ?: listOf(KeyboardPreferences.LANG_EN)
        if (enabled.size <= 1) return enabled.firstOrNull() ?: "EN"
        val cur = prefs?.currentLanguage ?: KeyboardPreferences.LANG_EN
        val idx = enabled.indexOf(cur)
        val next = enabled[(idx + 1) % enabled.size]
        return "$cur→$next"
    }

    // ──────────────────────────────────────────────
    // Candidates / clipboard bar
    // ──────────────────────────────────────────────

    private fun updateCandidates(input: String) {
        val container = candidatesContainer ?: return
        container.removeAllViews()
        if (!(prefs?.showPredictions ?: true)) return

        val suggestions = wordPredictor?.getSuggestions(input, prefs?.currentLanguage ?: "EN") ?: return
        val emojis = if ((prefs?.currentLanguage ?: "EN") == KeyboardPreferences.LANG_EN && input.isNotEmpty())
            wordPredictor?.getEmojiSuggestions(input) ?: emptyList()
        else emptyList()

        (suggestions + emojis).take(8).forEach { word ->
            addCandidateChip(container, word) { commitSuggestion(word) }
        }
    }

    private fun showClipboardPanel() {
        val container = candidatesContainer ?: return
        container.removeAllViews()
        val items = clipboard?.getAll() ?: emptyList()

        if (items.isEmpty()) {
            val tv = makeCandidateView("(empty clipboard)")
            tv.setOnClickListener { }
            container.addView(tv)
            return
        }

        items.take(10).forEach { item ->
            val short = if (item.length > 30) item.take(27) + "…" else item
            addCandidateChip(container, short) {
                currentInputConnection?.commitText(item, 1)
                clipboard?.save(item)
                showClipboard = false
                updateCandidates(currentInput.toString())
            }
        }
    }

    private fun addCandidateChip(container: LinearLayout, text: String, onClick: () -> Unit) {
        val tv = makeCandidateView(text)
        tv.setOnClickListener { onClick() }
        container.addView(tv)
    }

    private fun makeCandidateView(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 15f
            setPadding(28, 0, 28, 0)
            setTextColor(getThemeTextColor())
            setBackgroundResource(R.drawable.candidate_bar_bg)
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).also { it.setMargins(4, 6, 4, 6) }
        }
    }

    private fun getThemeTextColor(): Int {
        return when (prefs?.theme) {
            KeyboardPreferences.THEME_DARK,
            KeyboardPreferences.THEME_OCEAN,
            KeyboardPreferences.THEME_SUNSET -> android.graphics.Color.WHITE
            else -> android.graphics.Color.parseColor("#1A1A2E")
        }
    }

    private fun commitSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        if (currentInput.isNotEmpty()) ic.deleteSurroundingText(currentInput.length, 0)
        ic.commitText("$word ", 1)
        wordPredictor?.learnWord(word)
        clipboard?.save(word)
        currentInput.clear()
        updateCandidates("")
    }

    private fun learnAndReset() {
        if (currentInput.isNotEmpty()) {
            wordPredictor?.learnWord(currentInput.toString())
            clipboard?.save(currentInput.toString())
        }
        currentInput.clear()
        updateCandidates("")
    }

    // ──────────────────────────────────────────────
    // Swipe to cycle language
    // ──────────────────────────────────────────────

    override fun swipeLeft()  { cycleLang() }
    override fun swipeRight() { cycleLang() }

    // ──────────────────────────────────────────────
    // Vibration
    // ──────────────────────────────────────────────

    private fun vibrateKey() {
        if (!(prefs?.vibrateOnKey ?: true)) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(18)
                }
            }
        } catch (_: Exception) {}
    }

    // ──────────────────────────────────────────────
    // Unused listener callbacks
    // ──────────────────────────────────────────────
    override fun onPress(primaryCode: Int)    {}
    override fun onRelease(primaryCode: Int)  {}
    override fun onText(text: CharSequence?)  { currentInputConnection?.commitText(text, 1) }
    override fun swipeDown() {}
    override fun swipeUp()   {}
}