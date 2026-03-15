package com.gihansgamage.aksharakb

import android.content.Context
import android.graphics.*
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet
import android.util.TypedValue
import com.gihansgamage.aksharakb.data.KeyboardPreferences

class MyKeyboardView(context: Context, attrs: AttributeSet) : KeyboardView(context, attrs) {

    private var keyboardBg: Bitmap? = null
    private var prefs = KeyboardPreferences(context)

    private val bgPaint        = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyFillPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val keyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val keyGlowPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val hlPaint        = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shadowPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint      = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface  = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val borderPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rrect = RectF()

    fun setKeyboardImage(bitmap: Bitmap?) { keyboardBg = bitmap; invalidate() }
    fun refreshPrefs()                    { prefs = KeyboardPreferences(context); invalidate() }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    // ── Theme data class ────────────────────────────────────────
    private data class T(
        val bgA: Int, val bgB: Int,
        val keyFill: Int, val specFill: Int,
        val stroke: Int, val glow: Int, val highlight: Int, val shadow: Int,
        val text: Int, val specText: Int
    )

    private fun theme(): T = when (prefs.theme) {
        KeyboardPreferences.THEME_DARK -> T(
            bgA       = 0xFF0D0D1A.toInt(),
            bgB       = 0xFF1A1030.toInt(),
            keyFill   = 0x1EFFFFFF,
            specFill  = 0x337C3AED,
            stroke    = 0x55A78BFA,
            glow      = 0x337C3AED,
            highlight = 0x44FFFFFF,
            shadow    = 0x33000000,
            text      = 0xFFEDE9FE.toInt(),
            specText  = 0xFFC4B5FD.toInt()
        )
        KeyboardPreferences.THEME_OCEAN -> T(
            bgA       = 0xFF010E1C.toInt(),
            bgB       = 0xFF04274A.toInt(),
            keyFill   = 0x221B3A5F,
            specFill  = 0x330E4D7A,
            stroke    = 0x660EA5E9,
            glow      = 0x330EA5E9,
            highlight = 0x4488CCEE,
            shadow    = 0x33000022,
            text      = 0xFFBAE6FF.toInt(),
            specText  = 0xFF7DD3FC.toInt()
        )
        KeyboardPreferences.THEME_SUNSET -> T(
            bgA       = 0xFF150010.toInt(),
            bgB       = 0xFF3A0840.toInt(),
            keyFill   = 0x222D0A35,
            specFill  = 0x33780A8C,
            stroke    = 0x77D946EF,
            glow      = 0x33D946EF,
            highlight = 0x44EE88FF,
            shadow    = 0x33100011,
            text      = 0xFFF5D0FE.toInt(),
            specText  = 0xFFE879F9.toInt()
        )
        else -> T(  // Default light-futuristic
            bgA       = 0xFFDDE1F5.toInt(),
            bgB       = 0xFFBBC3EA.toInt(),
            keyFill   = 0xDDFFFFFF.toInt(),
            specFill  = 0x227C3AED,
            stroke    = 0x887C3AED.toInt(),
            glow      = 0x227C3AED,
            highlight = 0xCCFFFFFF.toInt(),
            shadow    = 0x22000066,
            text      = 0xFF1E0A3C.toInt(),
            specText  = 0xFF5B21B6.toInt()
        )
    }

    private fun isSpecial(code: Int) = code == Keyboard.KEYCODE_DELETE ||
            code == Keyboard.KEYCODE_SHIFT || code == Keyboard.KEYCODE_DONE ||
            code == Keyboard.KEYCODE_MODE_CHANGE || code == 32 || code == -10 || code == -20

    // ── onDraw ───────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        val t = theme()
        val w = width.toFloat()
        val h = height.toFloat()
        val r = dp(4f)          // key corner radius
        val gap = dp(2.5f)      // half-gap around each key

        // 1 ── Background gradient
        if (keyboardBg != null && prefs.theme == KeyboardPreferences.THEME_CUSTOM) {
            canvas.drawBitmap(keyboardBg!!, null, Rect(0, 0, width, height), null)
            canvas.drawColor(0xCC000000.toInt())
        } else {
            bgPaint.shader = LinearGradient(0f, 0f, 0f, h, t.bgA, t.bgB, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, bgPaint)
        }

        // subtle horizontal scan-line pattern (every 4dp)
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x05FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        var ly = 0f
        val step = dp(4f)
        while (ly < h) { canvas.drawLine(0f, ly, w, ly, linePaint); ly += step }

        // 2 ── Keys
        val kb = keyboard ?: run { super.onDraw(canvas); return }
        val keys = kb.keys ?: run { super.onDraw(canvas); return }
        val baseSp = (prefs.keyHeight.coerceIn(44, 72) * 0.29f)
        val basePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, baseSp, resources.displayMetrics)

        for (key in keys) {
            val kx   = key.x.toFloat()
            val ky   = key.y.toFloat()
            val kw   = key.width.toFloat()
            val kh   = key.height.toFloat()
            val spec = isSpecial(key.codes.firstOrNull() ?: 0)

            val l = kx + gap
            val top = ky + gap
            val ri = kx + kw - gap
            val bot = ky + kh - gap

            // 2a shadow
            shadowPaint.color = t.shadow
            rrect.set(l + dp(1f), top + dp(2f), ri + dp(1f), bot + dp(2f))
            canvas.drawRoundRect(rrect, r, r, shadowPaint)

            // 2b outer glow (wider stroke, transparent)
            rrect.set(l - dp(1f), top - dp(1f), ri + dp(1f), bot + dp(1f))
            keyGlowPaint.color = t.glow
            keyGlowPaint.strokeWidth = dp(3f)
            canvas.drawRoundRect(rrect, r + dp(1f), r + dp(1f), keyGlowPaint)

            // 2c fill
            rrect.set(l, top, ri, bot)
            keyFillPaint.color = if (spec) t.specFill else t.keyFill
            canvas.drawRoundRect(rrect, r, r, keyFillPaint)

            // 2d stroke
            keyStrokePaint.color = t.stroke
            keyStrokePaint.strokeWidth = dp(0.7f)
            canvas.drawRoundRect(rrect, r, r, keyStrokePaint)

            // 2e top highlight (glass edge)
            hlPaint.color = t.highlight
            hlPaint.strokeWidth = dp(1f)
            canvas.drawLine(l + r, top + dp(1.5f), ri - r, top + dp(1.5f), hlPaint)

            // 2f label
            val label = when {
                key.label != null -> key.label.toString()
                key.codes.isNotEmpty() && key.codes[0] > 31 -> key.codes[0].toChar().toString()
                else -> ""
            }
            if (label.isNotEmpty()) {
                textPaint.color = if (spec) t.specText else t.text
                textPaint.textSize = if (label.length > 4) basePx * 0.72f else basePx
                val cx = kx + kw / 2f
                val cy = ky + kh / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
                canvas.drawText(label, cx, cy, textPaint)
            }
        }

        // 3 ── Border
        if (prefs.showBorder) {
            borderPaint.color = t.stroke
            borderPaint.strokeWidth = dp(0.8f)
            canvas.drawRect(0f, 0f, w, h, borderPaint)
        }

        // Do NOT call super.onDraw() — we've drawn everything ourselves
    }
}