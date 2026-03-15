package com.gihansgamage.aksharakb

import android.content.Context
import android.graphics.*
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet

class MyKeyboardView(context: Context, attrs: AttributeSet) : KeyboardView(context, attrs) {

    private var keyboardBg: Bitmap? = null

    // Call this from your Service to set the background image
    fun setKeyboardImage(bitmap: Bitmap?) {
        this.keyboardBg = bitmap
        invalidate() // Redraw
    }

    override fun onDraw(canvas: Canvas) {
        // 1. Draw Background Image if exists
        if (keyboardBg != null) {
            canvas.drawBitmap(keyboardBg!!, null, Rect(0, 0, width, height), null)
        } else {
            canvas.drawColor(Color.parseColor("#EEEEEE")) // Default Grey
        }

        // 2. Draw Borders (if enabled in settings - simplified here)
        val paint = Paint()
        paint.color = Color.parseColor("#CCCCCC")
        paint.style = Paint.Style.STROKE
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // 3. Draw Keys
        super.onDraw(canvas)
    }
}