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

    // Emoji mode state — set by IME before invalidating
    var isEmojiMode: Boolean = false
    var activeCategoryTab: Int = 0  // which tab (0-7) is active

    private val bgPaint        = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyBodyPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shinePaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shadowPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val keyBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint      = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign      = Paint.Align.CENTER
        typeface       = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        isSubpixelText = true
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign      = Paint.Align.RIGHT
        typeface       = Typeface.create("sans-serif", Typeface.NORMAL)
        isSubpixelText = true
    }
    private val capsLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style     = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val rrect  = RectF()
    private val rrect2 = RectF()

    // Blur shadow paint — recreated when blur radius changes
    private var blurPaint: Paint? = null
    private var lastBlurR = -1f

    fun setKeyboardImage(b: Bitmap?) { keyboardBg = b; invalidate() }
    fun refreshPrefs() { prefs = KeyboardPreferences(context); invalidate() }

    private fun dp(v: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    // ── Static height ─────────────────────────────────────────────
    private var cachedHeight = 0
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val h = measuredHeight
        if (h > cachedHeight) cachedHeight = h
        if (cachedHeight > 0) setMeasuredDimension(measuredWidth, cachedHeight)
    }

    // ── Enable hardware layer for BlurMaskFilter ──────────────────
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    // ── Theme ─────────────────────────────────────────────────────
    private data class T(
        val bg1: Int, val bg2: Int,
        val key: Int,          // normal key — FULLY OPAQUE
        val keySpec: Int,      // shift/del/mode
        val keyActive: Int,    // shift when held
        val border: Int,
        val borderSpec: Int,
        val borderActive: Int,
        val shadowCol: Int,
        val shineHi: Int,      // very subtle shine — NOT a white line
        val shineLo: Int,
        val textNorm: Int,
        val textSpec: Int,
        val textHint: Int,
        val textActive: Int,
        val capsLineCol: Int,
        val isLight: Boolean
    )

    private fun isDark() =
        prefs.theme == KeyboardPreferences.THEME_DARK ||
                prefs.theme == KeyboardPreferences.THEME_OCEAN ||
                prefs.theme == KeyboardPreferences.THEME_SUNSET

    private fun buildTheme() = if (isDark()) T(
        // ── DARK ───────────────────────────────────────────────────
        // Background: very deep navy-black, completely solid
        bg1          = 0xFF11131C.toInt(),
        bg2          = 0xFF0C0E16.toInt(),
        // Regular keys: solid medium charcoal-blue, zero transparency
        key          = 0xFF2B2E40.toInt(),
        keySpec      = 0xFF222434.toInt(),
        keyActive    = 0xFF394480.toInt(),
        // Border: faint white outline only on dark theme (gives key separation)
        border       = 0x1EFFFFFF,
        borderSpec   = 0x2AFFFFFF,
        borderActive = 0xDD6680FF.toInt(),
        // Shadow: dark offset shadow, no transparency issues
        shadowCol    = 0xFF000000.toInt(),
        // Shine: VERY faint gradient — just enough for glass feel, NOT a line
        // Using 0x0A = 4% opacity → totally invisible as a white line
        shineHi      = 0x0AFFFFFF,
        shineLo      = 0x00FFFFFF,
        textNorm     = 0xFFFFFFFF.toInt(),
        textSpec     = 0xFFEEEEFF.toInt(),
        textHint     = 0x50A0A0CC,
        textActive   = 0xFFAABBFF.toInt(),
        capsLineCol  = 0xFF6677EE.toInt(),
        isLight      = false
    ) else T(
        // ── LIGHT ──────────────────────────────────────────────────
        bg1          = 0xFFECF1FB.toInt(),
        bg2          = 0xFFE0E8F5.toInt(),
        key          = 0xFFFFFFFF.toInt(),
        keySpec      = 0xFFF0F3FA.toInt(),
        keyActive    = 0xFFCDD8FF.toInt(),
        border       = 0x00FFFFFF,  // no border on light — shadow provides separation
        borderSpec   = 0x00FFFFFF,
        borderActive = 0x554466CC,
        shadowCol    = 0xFF8899BB.toInt(),
        // Shine: also very faint on light theme
        shineHi      = 0x10FFFFFF,
        shineLo      = 0x00FFFFFF,
        textNorm     = 0xFF3A3A50.toInt(),
        textSpec     = 0xFF2E2E48.toInt(),
        textHint     = 0x60777790,
        textActive   = 0xFF2233AA.toInt(),
        capsLineCol  = 0xFF3344CC.toInt(),
        isLight      = true
    )

    private fun isSpecial(code: Int) =
        code == Keyboard.KEYCODE_DELETE || code == Keyboard.KEYCODE_SHIFT ||
                code == Keyboard.KEYCODE_DONE  || code == Keyboard.KEYCODE_MODE_CHANGE

    private fun isCategoryTab(code: Int) = code in -58..-51
    private fun isEmojiKey(code: Int)    = code == -60
    private fun isNavKey(code: Int)      = code == -59 || code == -61

    // English uppercase map for shift label display on QWERTY
    private fun shiftedLabel(raw: String, code: Int): String {
        // For single lowercase ASCII letters, uppercase them
        if (raw.length == 1) {
            val c = raw[0]
            if (c in 'a'..'z') return c.uppercaseChar().toString()
        }
        return ""  // for non-letter keys, handled via popupCharacters
    }

    override fun onDraw(canvas: Canvas) {
        val t  = buildTheme()
        val W  = width.toFloat()
        val H  = height.toFloat()

        // ── Background — solid, no transparency ──────────────────
        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, H,
            intArrayOf(t.bg1, t.bg2), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, W, H, bgPaint)
        bgPaint.shader = null

        val kb   = keyboard ?: run { super.onDraw(canvas); return }
        val keys = kb.keys  ?: run { super.onDraw(canvas); return }

        val gap    = dp(2.6f)
        val r      = dp(11f)    // corner radius — smooth pill
        val keyH   = keys.firstOrNull()?.height?.toFloat() ?: dp(44f)
        val basePx = keyH * 0.42f
        val shifted = kb.isShifted

        // Blur paint for soft shadow — only recreate when needed
        val blurR = if (t.isLight) dp(5f) else dp(3.5f)
        if (blurPaint == null || lastBlurR != blurR) {
            blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style      = Paint.Style.FILL
                color      = if (t.isLight) 0x30000040.toInt() else 0x90000000.toInt()
                maskFilter = BlurMaskFilter(blurR, BlurMaskFilter.Blur.NORMAL)
            }
            lastBlurR = blurR
        }
        val blurP = blurPaint!!

        for (key in keys) {
            val kx   = key.x.toFloat()
            val ky   = key.y.toFloat()
            val kw   = key.width.toFloat()
            val kh   = key.height.toFloat()
            val code = key.codes.firstOrNull() ?: 0
            val spec    = isSpecial(code)
            val isShiftK = code == Keyboard.KEYCODE_SHIFT
            val active  = isShiftK && shifted

            val l   = kx + gap
            val top = ky + gap
            val ri  = kx + kw - gap
            val bot = ky + kh - gap

            // ── 1. SHADOW ─────────────────────────────────────────
            val offY = if (t.isLight) dp(3f) else dp(2f)
            rrect.set(l + dp(1f), top + offY, ri - dp(1f), bot + offY)
            canvas.drawRoundRect(rrect, r, r, blurP)

            // ── 2. KEY BODY (fully opaque — no alpha, no blending) ─
            rrect.set(l, top, ri, bot)
            keyBodyPaint.color = when {
                active -> t.keyActive
                spec   -> t.keySpec
                else   -> t.key
            }
            canvas.drawRoundRect(rrect, r, r, keyBodyPaint)

            // ── 3. SHINE — very faint gradient, NOT a visible line ─
            // Kept extremely subtle: max alpha 0x0A (4%) so it blends
            // into the key colour instead of appearing as a white stripe
            val shineEnd = top + (bot - top) * 0.45f
            shinePaint.shader = LinearGradient(
                0f, top, 0f, shineEnd,
                intArrayOf(t.shineHi, t.shineLo), null, Shader.TileMode.CLAMP)
            rrect2.set(l + dp(2f), top + dp(1f), ri - dp(2f), shineEnd)
            canvas.drawRoundRect(rrect2, r * 0.7f, r * 0.7f, shinePaint)
            shinePaint.shader = null

            // ── 4. BORDER ─────────────────────────────────────────
            val borderCol = when {
                active -> t.borderActive
                spec   -> t.borderSpec
                else   -> t.border
            }
            if (Color.alpha(borderCol) > 0) {
                rrect.set(l, top, ri, bot)
                keyBorderPaint.color       = borderCol
                keyBorderPaint.strokeWidth = dp(0.8f)
                canvas.drawRoundRect(rrect, r, r, keyBorderPaint)
            }

            // ── 5. CAPS INDICATOR ─────────────────────────────────
            if (active) {
                capsLinePaint.color       = t.capsLineCol
                capsLinePaint.strokeWidth = dp(2.5f)
                canvas.drawLine(l + r, top + dp(3f), ri - r, top + dp(3f), capsLinePaint)
            }

            // ── 6. LABELS ─────────────────────────────────────────
            val rawLabel = key.label?.toString()
                ?: if (code > 31) code.toChar().toString() else ""

            when {
                // ── Emoji keyboard keys ───────────────────────────
                isEmojiKey(code) -> {
                    // Full-size emoji centered in key
                    if (rawLabel.isNotBlank()) {
                        textPaint.color    = t.textNorm
                        textPaint.textSize = kh * 0.52f
                        val ey = ky + kh * 0.56f - (textPaint.descent() + textPaint.ascent()) / 2f
                        canvas.drawText(rawLabel, kx + kw / 2f, ey, textPaint)
                    }
                }
                isCategoryTab(code) -> {
                    // Category tab: emoji icon, highlight if active
                    textPaint.textSize = kh * 0.50f
                    val tabIdx = (-code) - 51
                    textPaint.color = t.textNorm
                    textPaint.alpha = if (tabIdx == activeCategoryTab) 0xFF else 0x66
                    val ty = ky + kh * 0.56f - (textPaint.descent() + textPaint.ascent()) / 2f
                    canvas.drawText(rawLabel, kx + kw / 2f, ty, textPaint)
                    textPaint.alpha = 0xFF
                }
                isNavKey(code) -> {
                    textPaint.color    = t.textSpec
                    textPaint.textSize = basePx * 0.70f
                    val ny = ky + kh * 0.54f - (textPaint.descent() + textPaint.ascent()) / 2f
                    canvas.drawText(rawLabel, kx + kw / 2f, ny, textPaint)
                }
                spec -> {
                    // Special keys: fixed symbol
                    val lbl = when (code) {
                        Keyboard.KEYCODE_DELETE      -> "⌫"
                        Keyboard.KEYCODE_SHIFT       -> "⇧"
                        Keyboard.KEYCODE_DONE        -> "↵"
                        Keyboard.KEYCODE_MODE_CHANGE -> rawLabel.ifEmpty { "?123" }
                        else                         -> rawLabel
                    }
                    if (lbl.isNotEmpty()) {
                        textPaint.color    = t.textSpec
                        textPaint.textSize = when {
                            lbl.length > 3 -> basePx * 0.60f
                            else           -> basePx * 0.78f
                        }
                        val sy = ky + kh * 0.54f - (textPaint.descent() + textPaint.ascent()) / 2f
                        canvas.drawText(lbl, kx + kw / 2f, sy, textPaint)
                    }
                }
                shifted && run {
                    val popupRaw   = key.popupCharacters?.toString()?.trim() ?: ""
                    val popupShift = popupRaw.split(" ").firstOrNull()?.trim() ?: ""
                    val autoShift  = if (popupShift.isEmpty()) shiftedLabel(rawLabel, code) else ""
                    val sv         = popupShift.ifEmpty { autoShift }
                    sv.isNotEmpty()
                } -> {
                    val popupRaw   = key.popupCharacters?.toString()?.trim() ?: ""
                    val popupShift = popupRaw.split(" ").firstOrNull()?.trim() ?: ""
                    val autoShift  = if (popupShift.isEmpty()) shiftedLabel(rawLabel, code) else ""
                    val sv         = popupShift.ifEmpty { autoShift }
                    textPaint.color    = t.textNorm
                    textPaint.textSize = basePx
                    val maxW = (ri - l) * 0.82f
                    if (textPaint.measureText(sv) > maxW) textPaint.textSize *= maxW / textPaint.measureText(sv)
                    val sy = ky + kh * 0.54f - (textPaint.descent() + textPaint.ascent()) / 2f
                    canvas.drawText(sv, kx + kw / 2f, sy, textPaint)
                }
                else -> {
                    // Normal key: main label + hint
                    val popupRaw   = key.popupCharacters?.toString()?.trim() ?: ""
                    val popupShift = popupRaw.split(" ").firstOrNull()?.trim() ?: ""
                    val autoShift  = if (popupShift.isEmpty()) shiftedLabel(rawLabel, code) else ""
                    val hintLbl    = popupShift.ifEmpty { autoShift }

                    if (rawLabel.isNotEmpty()) {
                        textPaint.color = if (active) t.textActive else t.textNorm
                        var sz = when {
                            rawLabel.length > 5 -> basePx * 0.46f
                            rawLabel.length > 3 -> basePx * 0.60f
                            rawLabel.length > 1 -> basePx * 0.74f
                            else                -> basePx
                        }
                        textPaint.textSize = sz
                        val maxW = (ri - l) * 0.82f
                        if (textPaint.measureText(rawLabel) > maxW) {
                            sz *= maxW / textPaint.measureText(rawLabel); textPaint.textSize = sz
                        }
                        val labelY = if (hintLbl.isNotEmpty())
                            ky + kh * 0.64f - (textPaint.descent() + textPaint.ascent()) / 2f
                        else
                            ky + kh * 0.54f - (textPaint.descent() + textPaint.ascent()) / 2f
                        canvas.drawText(rawLabel, kx + kw / 2f, labelY, textPaint)
                    }
                    if (hintLbl.isNotEmpty() && !shifted) {
                        hintPaint.color    = t.textHint
                        hintPaint.textSize = basePx * 0.33f
                        val hw = (ri - l) * 0.38f
                        if (hintPaint.measureText(hintLbl) > hw) hintPaint.textSize *= hw / hintPaint.measureText(hintLbl)
                        canvas.drawText(hintLbl, ri - dp(3.5f), top + dp(2.5f) - hintPaint.ascent(), hintPaint)
                    }
                }
            }
        }
    }

}