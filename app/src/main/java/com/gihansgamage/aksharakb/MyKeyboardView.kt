package com.gihansgamage.aksharakb

import android.content.Context
import android.graphics.*
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet
import com.gihansgamage.aksharakb.data.KeyboardPreferences

class MyKeyboardView(context: Context, attrs: AttributeSet) : KeyboardView(context, attrs) {

    private var keyboardBg: Bitmap? = null
    private var prefs: KeyboardPreferences = KeyboardPreferences(context)

    private val borderPaint = Paint().apply {
        color = Color.parseColor("#AAAAAA")
        style = Paint.Style.STROKE
        strokeWidth = 1f
        isAntiAlias = true
    }

    fun setKeyboardImage(bitmap: Bitmap?) {
        this.keyboardBg = bitmap
        invalidate()
    }

    fun refreshPrefs() {
        prefs = KeyboardPreferences(context)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        // Draw background
        if (keyboardBg != null && prefs.theme == KeyboardPreferences.THEME_CUSTOM) {
            canvas.drawBitmap(keyboardBg!!, null, Rect(0, 0, width, height), null)
        } else {
            val bgColor = getThemeBackground()
            canvas.drawColor(bgColor)
        }

        // Draw border if enabled
        if (prefs.showBorder) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), borderPaint)
        }

        // Draw keys on top
        super.onDraw(canvas)
    }

    private fun getThemeBackground(): Int {
        return when (prefs.theme) {
            KeyboardPreferences.THEME_DARK -> Color.parseColor("#1A1A2E")
            KeyboardPreferences.THEME_OCEAN -> Color.parseColor("#0D3B66")
            KeyboardPreferences.THEME_SUNSET -> Color.parseColor("#4A1942")
            else -> Color.parseColor("#F0F0F0") // default light
        }
    }
}