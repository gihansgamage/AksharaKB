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

    // ── Paints ───────────────────────────────────────────────────
    private val bgPaint     = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyFill     = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val keyStroke   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val innerGlow   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val topShine    = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bottomFade  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val mainText    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface  = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val hintPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        typeface  = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val capsLine    = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style     = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rrect  = RectF()
    private val rrect2 = RectF()

    fun setKeyboardImage(b: Bitmap?) { keyboardBg = b; invalidate() }
    fun refreshPrefs() { prefs = KeyboardPreferences(context); invalidate() }

    private fun dp(v: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    // ── Static height: override onMeasure so height never shrinks ──
    // We cache the tallest height measured and always use at least that.
    private var cachedHeight = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val h = measuredHeight
        if (h > cachedHeight) cachedHeight = h
        if (cachedHeight > 0)
            setMeasuredDimension(measuredWidth, cachedHeight)
    }

    // ── Theme — much higher alpha values for less transparency ────
    private data class Theme(
        val bgTop: Int, val bgBot: Int,
        val glassBase: Int,      // regular key fill — opaque enough to read
        val glassTint: Int,      // color overlay
        val specGlass: Int,      // shift/del/mode keys
        val accentGlass: Int,    // active shift key
        val rimTop: Int,
        val innerGlowCol: Int,
        val shadowCol: Int,
        val textMain: Int,
        val textSpec: Int,
        val textHint: Int
    )

    private fun theme(): Theme = when (prefs.theme) {
        KeyboardPreferences.THEME_DARK -> Theme(
            bgTop        = 0xFF0A0A16.toInt(),
            bgBot        = 0xFF121224.toInt(),
            // Keys: 0x90 = 144/255 ≈ 56% opacity — clearly visible glass
            glassBase    = 0x90262640.toInt(),
            glassTint    = 0x28503AFA.toInt(),
            specGlass    = 0xA0302050.toInt(),
            accentGlass  = 0xB06040D0.toInt(),
            rimTop       = 0x88FFFFFF.toInt(),
            innerGlowCol = 0x30606080,
            shadowCol    = 0x60000000,
            textMain     = 0xFFEEEEFF.toInt(),
            textSpec     = 0xFFCCAAFF.toInt(),
            textHint     = 0x99AAAAFF.toInt()
        )
        KeyboardPreferences.THEME_OCEAN -> Theme(
            bgTop        = 0xFF02101E.toInt(),
            bgBot        = 0xFF041828.toInt(),
            glassBase    = 0x90102040.toInt(),
            glassTint    = 0x20006890.toInt(),
            specGlass    = 0xA0082040.toInt(),
            accentGlass  = 0xB0005888.toInt(),
            rimTop       = 0x7799DDFF,
            innerGlowCol = 0x280060AA,
            shadowCol    = 0x55000020,
            textMain     = 0xFFCCEEFF.toInt(),
            textSpec     = 0xFF88DDFF.toInt(),
            textHint     = 0x886699DD.toInt()
        )
        KeyboardPreferences.THEME_SUNSET -> Theme(
            bgTop        = 0xFF0E0012.toInt(),
            bgBot        = 0xFF280630.toInt(),
            glassBase    = 0x90280040.toInt(),
            glassTint    = 0x20660050.toInt(),
            specGlass    = 0xA0400050.toInt(),
            accentGlass  = 0xB0880090.toInt(),
            rimTop       = 0x77FFAAFF,
            innerGlowCol = 0x28880060,
            shadowCol    = 0x55100018,
            textMain     = 0xFFF0D0FF.toInt(),
            textSpec     = 0xFFFF99FF.toInt(),
            textHint     = 0x88CC88EE.toInt()
        )
        else -> Theme(  // Default deep indigo
            bgTop        = 0xFF0E0E2A.toInt(),
            bgBot        = 0xFF080820.toInt(),
            glassBase    = 0x90202048.toInt(),
            glassTint    = 0x20303080.toInt(),
            specGlass    = 0xA0282860.toInt(),
            accentGlass  = 0xB0484898.toInt(),
            rimTop       = 0x88FFFFFF.toInt(),
            innerGlowCol = 0x28404080,
            shadowCol    = 0x55000040,
            textMain     = 0xFFEEEEFF.toInt(),
            textSpec     = 0xFFBBBBFF.toInt(),
            textHint     = 0x77AAAAFF
        )
    }

    private fun isSpecial(code: Int) =
        code == Keyboard.KEYCODE_DELETE || code == Keyboard.KEYCODE_SHIFT ||
                code == Keyboard.KEYCODE_DONE  || code == Keyboard.KEYCODE_MODE_CHANGE

    override fun onDraw(canvas: Canvas) {
        val t = theme()
        val W = width.toFloat()
        val H = height.toFloat()

        // ── Background ───────────────────────────────────────────
        if (keyboardBg != null && prefs.theme == KeyboardPreferences.THEME_CUSTOM) {
            canvas.drawBitmap(keyboardBg!!, null, Rect(0, 0, width, height), null)
            canvas.drawColor(0xCC000000.toInt())
        } else {
            bgPaint.shader = LinearGradient(0f, 0f, 0f, H,
                intArrayOf(t.bgTop, t.bgBot), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, W, H, bgPaint)
        }

        val kb   = keyboard ?: run { super.onDraw(canvas); return }
        val keys = kb.keys  ?: run { super.onDraw(canvas); return }

        // ── Metrics ───────────────────────────────────────────────
        val pad        = dp(2.6f)
        val r          = dp(10f)
        val keyHeightPx = keys.firstOrNull()?.height?.toFloat() ?: dp(44f)
        val basePx     = keyHeightPx * 0.42f    // 42% of key height
        val isShifted  = kb.isShifted

        for (key in keys) {
            val kx   = key.x.toFloat()
            val ky   = key.y.toFloat()
            val kw   = key.width.toFloat()
            val kh   = key.height.toFloat()
            val code = key.codes.firstOrNull() ?: 0
            val spec = isSpecial(code)
            val isShiftK = code == Keyboard.KEYCODE_SHIFT

            val l   = kx + pad
            val top = ky + pad
            val ri  = kx + kw - pad
            val bot = ky + kh - pad

            // ── Shadow ────────────────────────────────────────────
            shadowPaint.color = t.shadowCol
            rrect.set(l + dp(0.5f), top + dp(2f), ri + dp(0.5f), bot + dp(2f))
            canvas.drawRoundRect(rrect, r, r, shadowPaint)

            // ── Key fill ─────────────────────────────────────────
            rrect.set(l, top, ri, bot)
            keyFill.color = when {
                isShiftK && isShifted -> t.accentGlass
                spec                  -> t.specGlass
                else                  -> t.glassBase
            }
            canvas.drawRoundRect(rrect, r, r, keyFill)

            // Color tint on regular keys
            if (!spec) {
                keyFill.color = t.glassTint
                canvas.drawRoundRect(rrect, r, r, keyFill)
            }

            // ── Outer stroke ─────────────────────────────────────
            keyStroke.color       = when {
                isShiftK && isShifted -> 0xCC9980FF.toInt()
                spec                  -> 0x776060BB.toInt()
                else                  -> 0x44FFFFFF
            }
            keyStroke.strokeWidth = dp(0.8f)
            canvas.drawRoundRect(rrect, r, r, keyStroke)

            // ── Inner glow ────────────────────────────────────────
            innerGlow.color       = if (isShiftK && isShifted) 0x40A090FF.toInt() else t.innerGlowCol
            innerGlow.strokeWidth = dp(0.6f)
            canvas.drawRoundRect(rrect, r, r, innerGlow)

            // ── Top catch-light ───────────────────────────────────
            val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color       = t.rimTop
                strokeWidth = dp(0.8f)
                style       = Paint.Style.STROKE
                strokeCap   = Paint.Cap.ROUND
            }
            canvas.drawLine(l + r, top + dp(0.8f), ri - r, top + dp(0.8f), rimPaint)

            // ── Top shine gradient ────────────────────────────────
            val shH = (bot - top) * 0.36f
            rrect2.set(l + dp(3f), top + dp(1f), ri - dp(3f), top + shH)
            topShine.shader = LinearGradient(0f, top + dp(1f), 0f, top + shH,
                t.rimTop, 0x00FFFFFF, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(rrect2, r * 0.6f, r * 0.6f, topShine)

            // ── Bottom depth ──────────────────────────────────────
            val fadeY = top + (bot - top) * 0.60f
            rrect2.set(l, fadeY, ri, bot)
            bottomFade.shader = LinearGradient(0f, fadeY, 0f, bot,
                0x00000000, 0x30000000, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(rrect2, r, r, bottomFade)

            // ── Caps indicator ────────────────────────────────────
            if (isShiftK && isShifted) {
                capsLine.color       = 0xFFD0B0FF.toInt()
                capsLine.strokeWidth = dp(2f)
                canvas.drawLine(l + r, top + dp(2.5f), ri - r, top + dp(2.5f), capsLine)
            }

            // ── Label + shift logic ───────────────────────────────
            val rawLabel   = key.label?.toString()
                ?: if (code > 31) code.toChar().toString() else ""
            val popupStr   = key.popupCharacters?.toString()?.trim() ?: ""
            val shiftLabel = popupStr.split(" ").firstOrNull()?.trim() ?: ""

            val displayLabel: String
            val displayHint: String

            when {
                spec -> {
                    // Special keys: always show their fixed symbol, never swap
                    displayLabel = when (code) {
                        Keyboard.KEYCODE_DELETE      -> "⌫"
                        Keyboard.KEYCODE_SHIFT       -> "⇧"
                        Keyboard.KEYCODE_DONE        -> "↵"
                        Keyboard.KEYCODE_MODE_CHANGE -> rawLabel.ifEmpty { "?123" }
                        else                         -> rawLabel
                    }
                    displayHint  = ""
                }
                isShifted && shiftLabel.isNotEmpty() -> {
                    // Shift active: show shift character as main label, original as small hint
                    displayLabel = shiftLabel
                    displayHint  = rawLabel
                }
                else -> {
                    // Normal: main label + small shift hint top-right
                    displayLabel = rawLabel
                    displayHint  = shiftLabel
                }
            }

            // Draw main label
            if (displayLabel.isNotEmpty()) {
                mainText.color = when {
                    isShiftK && isShifted -> 0xFFDDBBFF.toInt()
                    spec                  -> t.textSpec
                    isShifted             -> 0xFFFFFFFF.toInt()
                    else                  -> t.textMain
                }
                val rawSz = when {
                    displayLabel.length > 5 -> basePx * 0.50f
                    displayLabel.length > 3 -> basePx * 0.64f
                    displayLabel.length > 1 -> basePx * 0.78f
                    else                    -> basePx
                }
                mainText.textSize = rawSz
                val maxW = (ri - l) * 0.80f
                if (mainText.measureText(displayLabel) > maxW)
                    mainText.textSize = rawSz * maxW / mainText.measureText(displayLabel)

                val cx = kx + kw / 2f
                // Push label down slightly when hint is showing
                val showHint = displayHint.isNotEmpty()
                val cy = if (showHint)
                    ky + kh * 0.64f - (mainText.descent() + mainText.ascent()) / 2f
                else
                    ky + kh * 0.54f - (mainText.descent() + mainText.ascent()) / 2f
                canvas.drawText(displayLabel, cx, cy, mainText)
            }

            // Draw hint (small top-right, only when NOT shifted)
            if (displayHint.isNotEmpty() && !isShifted) {
                hintPaint.color    = t.textHint
                hintPaint.textSize = basePx * 0.36f
                val hw = (ri - l) * 0.40f
                if (hintPaint.measureText(displayHint) > hw)
                    hintPaint.textSize *= hw / hintPaint.measureText(displayHint)
                canvas.drawText(displayHint, ri - dp(3f),
                    top + dp(2f) - hintPaint.ascent(), hintPaint)
            }
        }
    }
}