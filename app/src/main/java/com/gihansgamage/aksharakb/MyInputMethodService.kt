package com.gihansgamage.aksharakb

import android.content.Intent
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
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageButton
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

    // Caps states: NONE → SHIFT (one letter) → CAPS_LOCK (stays on)
    private enum class CapsState { NONE, SHIFT, CAPS_LOCK }
    private var capsState     = CapsState.NONE
    private var isSymbols     = false
    private var showClipboard = false
    private var showEmoji     = false
    private var emojiCategory = 0
    private var currentInput  = StringBuilder()

    // ── Emoji categories ────────────────────────────────────────
    private val emojiCategories = listOf(
        "😀" to listOf(
            "😀","😃","😄","😁","😆","😅","😂","🤣","😊","😇","🙂","🙃","😉","😌","😍","🥰","😘","😗",
            "😙","😚","😋","😛","😜","🤪","😝","🤑","🤗","🤭","🤫","🤔","🤐","🤨","😐","😑","😶","😏",
            "😒","🙄","😬","🤥","😔","😪","🤤","😴","😷","🤒","🤕","🤢","🤮","🤧","🥵","🥶","🥴","😵",
            "🤯","🤠","🥳","😎","🤓","🧐","😕","😟","🙁","☹️","😮","😯","😲","😳","🥺","😦","😧","😨",
            "😰","😥","😢","😭","😱","😖","😣","😞","😓","😩","😫","🥱","😤","😡","😠","🤬","😈","👿",
            "💀","☠️","💩","🤡","👹","👺","👻","👽","👾","🤖","😺","😸","😹","😻","😼","😽","🙀","😿","😾"
        ),
        "👋" to listOf(
            "👋","🤚","🖐","✋","🖖","👌","🤌","🤏","✌️","🤞","🤟","🤘","🤙","👈","👉","👆","🖕","👇",
            "☝️","👍","👎","✊","👊","🤛","🤜","👏","🙌","👐","🤲","🤝","🙏","✍️","💅","🤳","💪","🦾",
            "🦵","🦶","👂","🦻","👃","🧠","🦷","🦴","👀","👁","👅","👄","💋","🫀","🫁","🧬","🦱","🦰",
            "🦳","🦲","👶","🧒","👦","👧","🧑","👱","👨","🧔","👩","🧓","👴","👵","🙍","🙎","🙅","🙆",
            "💁","🙋","🧏","🙇","🤦","🤷","👮","🕵","💂","🥷","👷","🫅","🤴","👸","👳","👲","🧕","🤵",
            "👰","🤰","🤱","👼","🎅","🤶","🧑‍🎄","🦸","🦹","🧙","🧝","🧛","🧟","🧌","🧞","🧜","🧚"
        ),
        "❤️" to listOf(
            "❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔","❣️","💕","💞","💓","💗","💖","💘","💝",
            "💟","☮️","✝️","☪️","🕉","✡️","🔯","🕎","☯️","☦️","🛐","⛎","♈","♉","♊","♋","♌","♍",
            "♎","♏","♐","♑","♒","♓","⛎","🆔","⚛️","🉑","☢️","☣️","📴","📳","🈶","🈚","🈸","🈺",
            "🈷️","✴️","🆚","💮","🉐","㊙️","㊗️","🈴","🈵","🈹","🈲","🅰️","🅱️","🆎","🆑","🅾️","🆘",
            "⛔","📛","🚫","💯","💢","♨️","🚷","🚯","🚳","🚱","🔞","📵","🚭","❗","❕","❓","❔","‼️",
            "⁉️","🔅","🔆","〽️","⚠️","🔱","⚜️","🔰","♻️","✅","🈯","💹","❎","🌐","🌀","➿","🌁"
        ),
        "🐶" to listOf(
            "🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐨","🐯","🦁","🐮","🐷","🐽","🐸","🐵","🙈","🙉",
            "🙊","🐒","🐔","🐧","🐦","🐤","🦆","🦅","🦉","🦇","🐺","🐗","🐴","🦄","🐝","🌱","🌲","🌳",
            "🌴","🌵","🎋","🌾","🍀","🌿","☘️","🍃","🍂","🍁","🍄","🐚","🌾","💐","🌷","🌹","🥀","🌺",
            "🌸","🌼","🌻","🌞","🌝","🌛","🌜","🌚","🌕","🌖","🌗","🌘","🌑","🌒","🌓","🌔","🌙","🌟",
            "⭐","🌠","⛅","⛈","🌤","🌥","🌦","🌧","🌨","🌩","🌪","🌫","🌬","🌀","🌈","🌂","☂️","☔",
            "⛱","⚡","❄️","☃️","⛄","🌊","💧","💦","🔥","🌋","🌎","🌍","🌏","🗺","🧭","🏔","⛰"
        ),
        "🍎" to listOf(
            "🍎","🍊","🍋","🍇","🍓","🍈","🍒","🍑","🥭","🍍","🥥","🥝","🍅","🍆","🥑","🥦","🥬","🥒",
            "🌶","🥕","🧄","🧅","🥔","🍠","🥐","🥯","🍞","🥖","🥨","🧀","🥚","🍳","🥞","🧇","🥓","🥩",
            "🍗","🍖","🌭","🍔","🍟","🍕","🌮","🌯","🥙","🧆","🥚","🍝","🍜","🍲","🍛","🍣","🍱","🥟",
            "🍤","🍙","🍚","🍘","🍥","🥮","🍢","🧁","🍰","🎂","🍮","🍭","🍬","🍫","🍿","🍩","🍪","🌰",
            "🥜","🍯","🥛","🍼","☕","🍵","🧃","🥤","🧋","🍶","🍺","🍻","🥂","🍷","🥃","🍸","🍹","🧉",
            "🍾","🥄","🍴","🍽","🥢","🧂","🫙","🧊","🥡","🥠","🍱","🧁","🍮","🥧","🧇","🥞","🫕"
        ),
        "🚗" to listOf(
            "🚗","🚕","🚙","🚌","🚎","🏎","🚓","🚑","🚒","🚐","🛻","🚚","🚛","🚜","🏍","🛵","🚲","🛴",
            "🛹","🛼","🚏","⛽","🛣","🛤","🗺","🏔","⛰","🌋","🗻","🏕","🏖","🏜","🏝","🏞","🏟","🏛",
            "🏗","🏘","🏚","🏠","🏡","🏢","🏣","🏤","🏥","🏦","🏨","🏩","🏪","🏫","🏬","🏭","🏯","🏰",
            "💒","🗼","🗽","⛪","🕌","🛕","🕍","⛩","🕋","⛲","⛺","🌁","🌃","🏙","🌄","🌅","🌆","🌇",
            "🌉","♨️","🌌","🌠","🎇","🎆","🗾","🎑","⛱","🗿","🚀","✈️","🛸","🚁","🛶","⛵","🚤","🛥",
            "🛳","⛴","🚢","🛩","🛫","🛬","💺","🚂","🚃","🚄","🚅","🚆","🚇","🚈","🚉","🚊","🚝","🚞"
        ),
        "⚽" to listOf(
            "⚽","🏀","🏈","⚾","🥎","🎾","🏐","🏉","🥏","🎱","🪀","🏓","🏸","🏒","🥍","🏑","🏏","🪃",
            "⛳","🏹","🎣","🤿","🎽","🎿","🛷","🥌","🎯","🪁","🎱","🔮","🎮","🕹","🎰","🎲","🧩","🧸",
            "♟","🪆","🪅","🎭","🎨","🖼","🎪","🤹","🎠","🎡","🎢","🎪","🎤","🎧","🎼","🎵","🎶","🎷",
            "🪗","🎸","🎹","🎺","🎻","🪘","🥁","🪩","🎬","🎥","📽","🎞","📞","☎️","📟","📠","📺","📷",
            "📸","📹","🎙","🎚","🎛","🧭","⏱","⏲","⏰","🕰","⌛","⏳","📡","🔋","🔌","💡","🔦","🕯",
            "🪔","🧯","🛢","💰","💴","💵","💶","💷","💸","💳","🪙","💹","💱","💲","✉️","📧","📨","📩"
        ),
        "🎉" to listOf(
            "🎉","🎊","🎈","🎁","🎀","🎗","🎟","🎫","🏆","🥇","🥈","🥉","🎖","🏅","🎪","🤹","🎭","🎨",
            "🖼","🎠","🎡","🎢","🎪","🎤","🎧","🎼","🎵","🎶","🎷","🎸","🎹","🎺","🎻","🥁","🪘","🎬",
            "🎥","📽","🎞","🎮","🕹","🎯","🎲","🧩","🧸","♟","🪆","🪅","🎴","🃏","🀄","🎭","🎪","🎠",
            "🌟","⭐","🌠","🎆","🎇","✨","🎍","🎋","🎃","🎑","🎄","🎆","🎇","🧧","🎐","🎏","🎑","🎀",
            "🎁","🎗","🎟","🎫","🎖","🏆","🥇","🎈","🎉","🎊","🎋","🎌","🎍","🎎","🎏","🎐","🎑","🧧"
        )
    )

    override fun onCreate() {
        super.onCreate()
        prefs         = KeyboardPreferences(this)
        wordPredictor = WordPredictor(this)
        clipboard     = KeyboardClipboard(this)
        prefs?.registerListener(this)
    }

    override fun onDestroy() {
        prefs?.unregisterListener(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        when (key) {
            KeyboardPreferences.KEY_KEY_HEIGHT,
            KeyboardPreferences.KEY_THEME,
            KeyboardPreferences.KEY_BG_IMAGE_URI,
            KeyboardPreferences.KEY_SHOW_BORDER,
            KeyboardPreferences.KEY_SINHALA_LAYOUT,
            KeyboardPreferences.KEY_SHOW_NUMPAD,
            KeyboardPreferences.KEY_SHOW_POPUP -> applyCurrentPrefs()
            KeyboardPreferences.KEY_ENABLED_LANGS -> {
                val enabled = prefs?.enabledLanguages ?: listOf(KeyboardPreferences.LANG_EN)
                val cur = prefs?.currentLanguage ?: KeyboardPreferences.LANG_EN
                if (cur !in enabled) prefs?.currentLanguage = enabled.firstOrNull() ?: KeyboardPreferences.LANG_EN
                setKeyboardLayout()
            }
        }
    }

    // ── View ──────────────────────────────────────────────────────

    override fun onCreateInputView(): View {
        val inputView       = LayoutInflater.from(this).inflate(R.layout.keyboard_view, null)
        keyboardView        = inputView.findViewById(R.id.keyboard_view)
        candidatesContainer = inputView.findViewById(R.id.candidates_container)
        candidatesScroll    = inputView.findViewById(R.id.candidates_view)

        inputView.findViewById<ImageButton>(R.id.btn_settings)?.setOnClickListener {
            vibrateKey()
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }?.let { startActivity(it) }
        }

        keyboardView?.setOnKeyboardActionListener(this)
        applyCurrentPrefs()
        updateCandidates("")
        return inputView
    }

    private fun applyCurrentPrefs() {
        val p  = prefs ?: return
        val kv = keyboardView ?: return
        if (p.theme == KeyboardPreferences.THEME_CUSTOM && p.bgImageUri.isNotEmpty()) {
            try {
                val s = contentResolver.openInputStream(Uri.parse(p.bgImageUri))
                kv.setKeyboardImage(BitmapFactory.decodeStream(s)); s?.close()
            } catch (_: Exception) { kv.setKeyboardImage(null) }
        } else { kv.setKeyboardImage(null) }
        kv.isPreviewEnabled = p.showPopupKeys
        kv.refreshPrefs()
        setKeyboardLayout()
    }

    private fun setKeyboardLayout() {
        val p    = prefs ?: return
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
        // Apply current caps state to keyboard
        keyboard?.isShifted = (capsState != CapsState.NONE)
        keyboardView?.invalidateAllKeys()
    }

    // ── Key handling ──────────────────────────────────────────────

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return
        vibrateKey()

        if (showClipboard && primaryCode != -20) { showClipboard = false; updateCandidates(currentInput.toString()) }
        if (showEmoji     && primaryCode != -40) { showEmoji = false; updateCandidates(currentInput.toString()) }

        when (primaryCode) {

            Keyboard.KEYCODE_DELETE -> {
                ic.deleteSurroundingText(1, 0)
                if (currentInput.isNotEmpty()) currentInput.deleteCharAt(currentInput.length - 1)
                updateCandidates(currentInput.toString())
            }

            Keyboard.KEYCODE_SHIFT -> {
                // Single tap → SHIFT, double tap → CAPS_LOCK, tap again → NONE
                capsState = when (capsState) {
                    CapsState.NONE      -> CapsState.SHIFT
                    CapsState.SHIFT     -> CapsState.CAPS_LOCK
                    CapsState.CAPS_LOCK -> CapsState.NONE
                }
                keyboard?.isShifted = (capsState != CapsState.NONE)
                keyboardView?.invalidateAllKeys()
            }

            Keyboard.KEYCODE_MODE_CHANGE -> {
                isSymbols = !isSymbols
                setKeyboardLayout()
            }

            Keyboard.KEYCODE_DONE -> {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP,   KeyEvent.KEYCODE_ENTER))
                learnAndReset()
            }

            -10 -> cycleLang()

            -20 -> {
                showEmoji     = false
                showClipboard = !showClipboard
                if (showClipboard) showClipboardPanel() else updateCandidates(currentInput.toString())
            }

            -40 -> {
                showClipboard = false
                showEmoji     = !showEmoji
                if (showEmoji) showEmojiPanel(emojiCategory) else updateCandidates(currentInput.toString())
            }

            32 -> { ic.commitText(" ", 1); learnAndReset() }

            else -> {
                if (primaryCode > 0) {
                    var ch = primaryCode.toChar()
                    if (capsState != CapsState.NONE && ch.isLetter()) ch = ch.uppercaseChar()
                    ic.commitText(ch.toString(), 1)
                    currentInput.append(ch)
                    updateCandidates(currentInput.toString())

                    // After typing one letter in SHIFT mode, go back to NONE
                    if (capsState == CapsState.SHIFT) {
                        capsState = CapsState.NONE
                        keyboard?.isShifted = false
                        keyboardView?.invalidateAllKeys()
                    }
                    // CAPS_LOCK stays active
                }
            }
        }
    }

    private fun cycleLang() {
        val enabled = prefs?.enabledLanguages ?: listOf(KeyboardPreferences.LANG_EN)
        if (enabled.size <= 1) return
        val cur  = prefs?.currentLanguage ?: KeyboardPreferences.LANG_EN
        val next = enabled[(enabled.indexOf(cur) + 1) % enabled.size]
        prefs?.currentLanguage = next
        isSymbols = false; capsState = CapsState.NONE
        currentInput.clear(); setKeyboardLayout(); updateCandidates("")
    }

    // ── Panels ────────────────────────────────────────────────────

    private fun updateCandidates(input: String) {
        val c = candidatesContainer ?: return
        c.removeAllViews()
        if (!(prefs?.showPredictions ?: true)) return
        val lang   = prefs?.currentLanguage ?: "EN"
        val words  = wordPredictor?.getSuggestions(input, lang) ?: emptyList()
        val emojis = if (lang == KeyboardPreferences.LANG_EN && input.isNotEmpty())
            wordPredictor?.getEmojiSuggestions(input) ?: emptyList() else emptyList()
        (words + emojis).take(8).forEach { w -> addChip(c, w) { commitSuggestion(w) } }
    }

    private fun showEmojiPanel(catIndex: Int) {
        val c = candidatesContainer ?: return
        c.removeAllViews()
        emojiCategory = catIndex.coerceIn(0, emojiCategories.lastIndex)

        // Category tabs
        emojiCategories.forEachIndexed { idx, (icon, _) ->
            val tab = TextView(this).apply {
                text = icon
                textSize = 18f
                setPadding(14, 0, 14, 0)
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).also { it.setMargins(2, 4, 2, 4) }
                // Highlight active tab
                if (idx == emojiCategory) {
                    setBackgroundResource(R.drawable.candidate_bar_bg)
                }
                setOnClickListener {
                    emojiCategory = idx
                    showEmojiPanel(idx)
                }
            }
            c.addView(tab)
        }

        // Divider
        c.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT)
                .also { it.setMargins(6, 8, 6, 8) }
            setBackgroundColor(0x33A78BFA)
        })

        // Emojis for this category
        emojiCategories[emojiCategory].second.forEach { emoji ->
            addChip(c, emoji) {
                currentInputConnection?.commitText(emoji, 1)
                // Keep panel open
            }
        }
    }

    private fun showClipboardPanel() {
        val c = candidatesContainer ?: return
        c.removeAllViews()
        val items = clipboard?.getAll() ?: emptyList()
        if (items.isEmpty()) { addChip(c, "📋 Empty") {}; return }
        items.take(10).forEach { item ->
            val s = if (item.length > 28) item.take(25) + "…" else item
            addChip(c, s) {
                currentInputConnection?.commitText(item, 1)
                showClipboard = false; updateCandidates(currentInput.toString())
            }
        }
    }

    private fun addChip(container: LinearLayout, text: String, onClick: () -> Unit) {
        container.addView(TextView(this).apply {
            this.text = text; textSize = 14f
            setPadding(18, 0, 18, 0)
            setTextColor(chipTextColor())
            setBackgroundResource(R.drawable.candidate_bar_bg)
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).also { it.setMargins(3, 5, 3, 5) }
            setOnClickListener { onClick() }
        })
    }

    private fun chipTextColor(): Int = when (prefs?.theme) {
        KeyboardPreferences.THEME_DARK,
        KeyboardPreferences.THEME_OCEAN,
        KeyboardPreferences.THEME_SUNSET -> 0xFFEEDDFF.toInt()
        else -> 0xFF1E0A3C.toInt()
    }

    private fun commitSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        if (currentInput.isNotEmpty()) ic.deleteSurroundingText(currentInput.length, 0)
        ic.commitText("$word ", 1); wordPredictor?.learnWord(word); clipboard?.save(word)
        currentInput.clear(); updateCandidates("")
    }

    private fun learnAndReset() {
        if (currentInput.isNotEmpty()) { wordPredictor?.learnWord(currentInput.toString()); clipboard?.save(currentInput.toString()) }
        currentInput.clear(); updateCandidates("")
    }

    private fun vibrateKey() {
        if (!(prefs?.vibrateOnKey ?: true)) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
                    ?.vibrate(VibrationEffect.createOneShot(14, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    v?.vibrate(VibrationEffect.createOneShot(14, VibrationEffect.DEFAULT_AMPLITUDE))
                else @Suppress("DEPRECATION") v?.vibrate(14)
            }
        } catch (_: Exception) {}
    }

    override fun swipeLeft()  { cycleLang() }
    override fun swipeRight() { cycleLang() }
    override fun onPress(p: Int) {}
    override fun onRelease(p: Int) {}
    override fun onText(t: CharSequence?) { currentInputConnection?.commitText(t, 1) }
    override fun swipeDown() {}
    override fun swipeUp() {}
}