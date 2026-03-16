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

    private val bgPaint      = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyFill      = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val keyStroke    = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val keyGlow      = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val topShine     = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val mainText     = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface  = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val hintText     = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        typeface  = Typeface.DEFAULT
    }
    private val shadowPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val capsLine     = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.5f; strokeCap = Paint.Cap.ROUND
    }
    private val rrect  = RectF()
    private val rrect2 = RectF()

    fun setKeyboardImage(b: Bitmap?) { keyboardBg = b; invalidate() }
    fun refreshPrefs() { prefs = KeyboardPreferences(context); invalidate() }

    // dp helper — converts dp to pixels
    private fun dp(v: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private data class Theme(
        val bgA: Int, val bgB: Int,
        val fill: Int, val specFill: Int, val accentFill: Int,
        val stroke: Int, val glow: Int, val shine: Int, val shadow: Int,
        val text: Int, val specText: Int, val hint: Int
    )

    private fun theme(): Theme = when (prefs.theme) {
        KeyboardPreferences.THEME_DARK -> Theme(
            bgA = 0xFF0A0A14.toInt(), bgB = 0xFF141428.toInt(),
            fill = 0x26FFFFFF, specFill = 0x2A6D28E0, accentFill = 0x3A7C3AED,
            stroke = 0x408B7CF8, glow = 0x186D28E0, shine = 0x18FFFFFF, shadow = 0x28000000,
            text = 0xFFEEE8FF.toInt(), specText = 0xFFB39DDB.toInt(), hint = 0x88C4B5FD.toInt()
        )
        KeyboardPreferences.THEME_OCEAN -> Theme(
            bgA = 0xFF010B18.toInt(), bgB = 0xFF031E3A.toInt(),
            fill = 0x201E5080, specFill = 0x2A0D4F7A, accentFill = 0x330E6EA5,
            stroke = 0x4838BDF8, glow = 0x180E6EA5, shine = 0x1888DDFF, shadow = 0x28000022,
            text = 0xFFCCEEFF.toInt(), specText = 0xFF7DD3FC.toInt(), hint = 0x8899EEFF.toInt()
        )
        KeyboardPreferences.THEME_SUNSET -> Theme(
            bgA = 0xFF0D0010.toInt(), bgB = 0xFF2A0630.toInt(),
            fill = 0x223D0A46, specFill = 0x2A6A0F80, accentFill = 0x33A020C0,
            stroke = 0x48D946EF, glow = 0x18C026E0, shine = 0x16FF88FF, shadow = 0x28100010,
            text = 0xFFF0D0FF.toInt(), specText = 0xFFE879F9.toInt(), hint = 0x88FFAAEE.toInt()
        )
        else -> Theme( // Default light
            bgA = 0xFFCDD5F4.toInt(), bgB = 0xFFB0BCE8.toInt(),
            fill = 0xD0FFFFFF.toInt(), specFill = 0x227C3AED, accentFill = 0x307C3AED,
            stroke = 0x707C3AED, glow = 0x127C3AED, shine = 0xBBFFFFFF.toInt(), shadow = 0x1A000055,
            text = 0xFF1E0A40.toInt(), specText = 0xFF5B21B6.toInt(), hint = 0x665B21B6
        )
    }

    private fun isSpecial(code: Int) =
        code == Keyboard.KEYCODE_DELETE || code == Keyboard.KEYCODE_SHIFT ||
                code == Keyboard.KEYCODE_DONE  || code == Keyboard.KEYCODE_MODE_CHANGE

    private fun isAccent(code: Int) = code == Keyboard.KEYCODE_DONE

    override fun onDraw(canvas: Canvas) {
        val t  = theme()
        val W  = width.toFloat()
        val H  = height.toFloat()

        // ── Background ───────────────────────────────────────────
        if (keyboardBg != null && prefs.theme == KeyboardPreferences.THEME_CUSTOM) {
            canvas.drawBitmap(keyboardBg!!, null, Rect(0, 0, width, height), null)
            canvas.drawColor(0xBB000000.toInt())
        } else {
            bgPaint.shader = LinearGradient(0f, 0f, 0f, H, t.bgA, t.bgB, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, W, H, bgPaint)
        }

        val kb   = keyboard ?: run { super.onDraw(canvas); return }
        val keys = kb.keys  ?: run { super.onDraw(canvas); return }

        // ── Sizing ───────────────────────────────────────────────
        // innerPad = visual gap between key cells (2.4dp each side = ~4.8dp between adjacent keys)
        val innerPad = dp(2.4f)
        val r        = dp(9f)

        // basePx = main character size in pixels.
        // Use actual key height from layout, multiply by 0.42 for good fill.
        // Keys are typically 42-52dp tall; 42*0.42 ≈ 17.6dp → comfortable size.
        val firstKey    = keys.firstOrNull()
        val keyHeightPx = firstKey?.height?.toFloat() ?: dp(44f)
        val basePx      = keyHeightPx * 0.40f   // 40% of key height → large, clear text

        val isShifted   = kb.isShifted

        for (key in keys) {
            val kx  = key.x.toFloat()
            val ky  = key.y.toFloat()
            val kw  = key.width.toFloat()
            val kh  = key.height.toFloat()

            val spec      = isSpecial(key.codes.firstOrNull() ?: 0)
            val accent    = isAccent(key.codes.firstOrNull() ?: 0)
            val isShiftK  = (key.codes.firstOrNull() ?: 0) == Keyboard.KEYCODE_SHIFT

            val l   = kx + innerPad
            val top = ky + innerPad
            val ri  = kx + kw - innerPad
            val bot = ky + kh - innerPad

            // Shadow
            shadowPaint.color = t.shadow
            rrect.set(l + dp(0.5f), top + dp(1.5f), ri + dp(0.5f), bot + dp(1.5f))
            canvas.drawRoundRect(rrect, r, r, shadowPaint)

            // Glow halo
            rrect2.set(l - dp(0.8f), top - dp(0.3f), ri + dp(0.8f), bot + dp(0.3f))
            keyGlow.color      = if (isShiftK && isShifted) t.accentFill else t.glow
            keyGlow.strokeWidth = dp(3f)
            canvas.drawRoundRect(rrect2, r + dp(1f), r + dp(1f), keyGlow)

            // Key fill
            rrect.set(l, top, ri, bot)
            keyFill.color = when {
                isShiftK && isShifted -> t.accentFill
                accent                -> t.accentFill
                spec                  -> t.specFill
                else                  -> t.fill
            }
            canvas.drawRoundRect(rrect, r, r, keyFill)

            // Key stroke
            keyStroke.color       = t.stroke
            keyStroke.strokeWidth = dp(if (isShiftK && isShifted) 1f else 0.6f)
            canvas.drawRoundRect(rrect, r, r, keyStroke)

            // Caps-lock indicator line
            if (isShiftK && isShifted) {
                capsLine.color = t.specText
                canvas.drawLine(l + r, top + dp(1.5f), ri - r, top + dp(1.5f), capsLine)
            }

            // Top shine
            val shH = (bot - top) * 0.30f
            rrect2.set(l + dp(2f), top + dp(0.8f), ri - dp(2f), top + shH)
            topShine.shader = LinearGradient(0f, top + dp(0.8f), 0f, top + shH,
                t.shine, 0x00FFFFFF, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(rrect2, r * 0.5f, r * 0.5f, topShine)

            // ── Label ────────────────────────────────────────────
            val label = key.label?.toString()
                ?: if (key.codes.isNotEmpty() && key.codes[0] > 31) key.codes[0].toChar().toString()
                else ""

            if (label.isNotEmpty()) {
                mainText.color = if (spec || accent) t.specText else t.text

                // Scale text size based on label length
                val rawSize = when {
                    label.length > 5 -> basePx * 0.52f
                    label.length > 3 -> basePx * 0.66f
                    label.length > 1 -> basePx * 0.80f
                    else             -> basePx        // single char: full size
                }
                mainText.textSize = rawSize

                // Never wider than 82% of key
                val maxW = (ri - l) * 0.82f
                if (mainText.measureText(label) > maxW)
                    mainText.textSize = rawSize * maxW / mainText.measureText(label)

                val cx = kx + kw / 2f
                // If key has a shift hint, push label down a bit
                val hasHint = !spec && key.popupCharacters?.isNotEmpty() == true
                val cy = if (hasHint)
                    ky + kh * 0.63f - (mainText.descent() + mainText.ascent()) / 2f
                else
                    ky + kh * 0.53f - (mainText.descent() + mainText.ascent()) / 2f

                canvas.drawText(label, cx, cy, mainText)
            }

            // ── Shift hint (top-right corner) ─────────────────────
            val popup = key.popupCharacters
            if (!spec && !popup.isNullOrEmpty()) {
                val hint = popup.toString().trim().split(" ").firstOrNull()?.trim() ?: ""
                if (hint.isNotEmpty()) {
                    hintText.color    = t.hint
                    hintText.textSize = basePx * 0.38f
                    // clamp hint width
                    val hw = (ri - l) * 0.42f
                    if (hintText.measureText(hint) > hw)
                        hintText.textSize = hintText.textSize * hw / hintText.measureText(hint)
                    canvas.drawText(hint, ri - dp(3f), top + dp(1.5f) - hintText.ascent(), hintText)
                }
            }
        }

        // Optional border
        if (prefs.showBorder) {
            keyStroke.color       = t.stroke
            keyStroke.strokeWidth = dp(0.8f)
            canvas.drawRect(0f, 0f, W, H, keyStroke)
        }
    }
}