package com.gihansgamage.aksharakb

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.View
import android.view.inputmethod.InputConnection

class MyInputMethodService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private var keyboardView: MyKeyboardView? = null
    private var keyboard: Keyboard? = null

    // Current Language State
    private var currentLang = "EN"

    override fun onCreateInputView(): View {
        val inflater = layoutInflater
        val inputView = inflater.inflate(R.layout.keyboard_view, null)
        keyboardView = inputView.findViewById(R.id.keyboard_view) as MyKeyboardView

        // Apply Customizations (from Settings in a real app)
        // keyboardView?.setKeyboardImage(myBitmap)

        setKeyboardLayout("QWERTY") // Default
        keyboardView?.setOnKeyboardActionListener(this)
        return inputView
    }

    // Switch between EN, SI, TA
    private fun setKeyboardLayout(type: String) {
        val xmlId = when (currentLang) {
            "SI" -> R.xml.wijesekara // You must create this XML
            "TA" -> R.xml.tamil      // You must create this XML
            else -> R.xml.qwerty
        }

        keyboard = Keyboard(this, xmlId)
        keyboardView?.keyboard = keyboard
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Handle hardware keyboard if needed
        return super.onKeyDown(keyCode, event)
    }

    // --- KeyboardView.OnKeyboardActionListener Implementation ---

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val inputConnection = currentInputConnection

        when (primaryCode) {
            -1 -> { // CAPS
                // Implement Caps Lock toggle logic here
            }
            -2 -> { // Switch to Symbols/Numbers
                // Load a different XML layout for numbers
            }
            -5 -> { // DELETE
                inputConnection.deleteSurroundingText(1, 0)
            }
            -4 -> { // ENTER
                inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
            else -> { // Normal Character
                inputConnection.commitText(primaryCode.toChar().toString(), 1)

                // OPTIMIZE: Simple prediction trigger
                // updateSuggestions()
            }
        }
    }

    // Function to switch language called by a button on keyboard or settings
    fun switchLanguage(lang: String) {
        currentLang = lang
        setKeyboardLayout("QWERTY") // Reloads XML based on currentLang
    }

    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {
        currentInputConnection.commitText(text, 1)
    }
    override fun swipeLeft() {
        // Optional: Swipe to change language
        if (currentLang == "EN") switchLanguage("SI")
        else if (currentLang == "SI") switchLanguage("TA")
        else switchLanguage("EN")
    }
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
}