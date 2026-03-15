package com.gihansgamage.aksharakb

import android.content.Context
import android.graphics.*
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet
import com.gihansgamage.aksharakb.data.KeyboardPreferences

class MyKeyboardView(context: Context, attrs: AttributeSet) : KeyboardView(context, attrs) {

    private var keyboardBg: Bitmap? = null
    private var prefs = KeyboardPreferences(context)

    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.8f
        isAntiAlias = true
    }

    fun setKeyboardImage(bitmap: Bitmap?) {
        keyboardBg = bitmap
        invalidate()
    }

    fun refreshPrefs() {
        prefs = KeyboardPreferences(context)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        // 1. Background
        if (keyboardBg != null && prefs.theme == KeyboardPreferences.THEME_CUSTOM) {
            canvas.drawBitmap(keyboardBg!!, null, Rect(0, 0, width, height), null)
        } else {
            canvas.drawColor(getThemeBgColor())
        }

        // 2. Optional border
        if (prefs.showBorder) {
            borderPaint.color = getBorderColor()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), borderPaint)
        }

        // 3. Keys
        super.onDraw(canvas)
    }

    private fun getThemeBgColor(): Int = when (prefs.theme) {
        KeyboardPreferences.THEME_DARK   -> Color.parseColor("#1C1C1E")
        KeyboardPreferences.THEME_OCEAN  -> Color.parseColor("#0A2540")
        KeyboardPreferences.THEME_SUNSET -> Color.parseColor("#3D0C40")
        else -> Color.parseColor("#D1D5DB")    // light grey default
    }

    private fun getBorderColor(): Int = when (prefs.theme) {
        KeyboardPreferences.THEME_DARK,
        KeyboardPreferences.THEME_OCEAN,
        KeyboardPreferences.THEME_SUNSET -> Color.parseColor("#55FFFFFF")
        else -> Color.parseColor("#55000000")
    }
}