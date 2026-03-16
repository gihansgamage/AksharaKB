package com.gihansgamage.aksharakb

import android.content.Context
import android.graphics.*
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import com.gihansgamage.aksharakb.data.KeyboardPreferences

class MyKeyboardView(context: Context, attrs: AttributeSet) : KeyboardView(context, attrs) {

    private var keyboardBg: Bitmap? = null
    private var prefs = KeyboardPreferences(context)

    // Emoji mode state
    var isEmojiMode: Boolean = false
    var activeCategoryTab: Int = 0
    // Callback for emoji swipe: +1 = next page, -1 = prev page
    var onEmojiSwipe: ((direction: Int) -> Unit)? = null

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
    private val hintPaint      = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign      = Paint.Align.RIGHT
        typeface       = Typeface.create("sans-serif", Typeface.NORMAL)
        isSubpixelText = true
    }
    private val capsLinePaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }

    private val rrect  = RectF()
    private val rrect2 = RectF()

    private var blurPaint: Paint? = null
    private var lastBlurR = -1f

    // ── Swipe gesture detector for emoji keyboard paging ──────────
    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_MIN_DISTANCE = dp(50f)
            private val SWIPE_MIN_VELOCITY = 100f

            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent,
                velocityX: Float, velocityY: Float
            ): Boolean {
                if (!isEmojiMode) return false
                val e1x = e1?.x ?: return false
                val dx  = e2.x - e1x
                if (Math.abs(dx) < SWIPE_MIN_DISTANCE) return false
                if (Math.abs(velocityX) < SWIPE_MIN_VELOCITY) return false
                // Swipe right = prev page, swipe left = next page
                onEmojiSwipe?.invoke(if (dx < 0) 1 else -1)
                return true
            }
        })

    override fun onTouchEvent(me: MotionEvent): Boolean {
        if (isEmojiMode) gestureDetector.onTouchEvent(me)
        return super.onTouchEvent(me)
    }

    fun setKeyboardImage(b: Bitmap?) { keyboardBg = b; invalidate() }
    fun refreshPrefs() { prefs = KeyboardPreferences(context); invalidate() }

    private fun dp(v: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    // ── Static height: enforce 5-row keyboard height ──────────────
    // We clamp to the tallest measured height so all layouts look identical.
    private var cachedHeight = 0
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val h = measuredHeight
        if (h > cachedHeight) cachedHeight = h
        if (cachedHeight > 0) setMeasuredDimension(measuredWidth, cachedHeight)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    // ── Theme ─────────────────────────────────────────────────────
    private data class T(
        val bg1: Int, val bg2: Int,
        val key: Int, val keySpec: Int, val keyActive: Int,
        val border: Int, val borderSpec: Int, val borderActive: Int,
        val shadowCol: Int, val shineHi: Int, val shineLo: Int,
        val textNorm: Int, val textSpec: Int, val textHint: Int, val textActive: Int,
        val capsLineCol: Int, val isLight: Boolean
    )

    private fun isDark() =
        prefs.theme == KeyboardPreferences.THEME_DARK ||
                prefs.theme == KeyboardPreferences.THEME_OCEAN ||
                prefs.theme == KeyboardPreferences.THEME_SUNSET

    private fun buildTheme() = if (isDark()) T(
        bg1 = 0xFF11131C.toInt(), bg2 = 0xFF0C0E16.toInt(),
        key = 0xFF2B2E40.toInt(), keySpec = 0xFF222434.toInt(), keyActive = 0xFF394480.toInt(),
        border = 0x1EFFFFFF, borderSpec = 0x2AFFFFFF, borderActive = 0xDD6680FF.toInt(),
        shadowCol = 0xFF000000.toInt(), shineHi = 0x0AFFFFFF, shineLo = 0x00FFFFFF,
        textNorm = 0xFFFFFFFF.toInt(), textSpec = 0xFFEEEEFF.toInt(),
        textHint = 0x50A0A0CC, textActive = 0xFFAABBFF.toInt(),
        capsLineCol = 0xFF6677EE.toInt(), isLight = false
    ) else T(
        bg1 = 0xFFECF1FB.toInt(), bg2 = 0xFFE0E8F5.toInt(),
        key = 0xFFFFFFFF.toInt(), keySpec = 0xFFF0F3FA.toInt(), keyActive = 0xFFCDD8FF.toInt(),
        border = 0x00FFFFFF, borderSpec = 0x00FFFFFF, borderActive = 0x554466CC,
        shadowCol = 0xFF8899BB.toInt(), shineHi = 0x10FFFFFF, shineLo = 0x00FFFFFF,
        textNorm = 0xFF3A3A50.toInt(), textSpec = 0xFF2E2E48.toInt(),
        textHint = 0x60777790, textActive = 0xFF2233AA.toInt(),
        capsLineCol = 0xFF3344CC.toInt(), isLight = true
    )

    private fun isSpecial(code: Int) =
        code == Keyboard.KEYCODE_DELETE || code == Keyboard.KEYCODE_SHIFT ||
                code == Keyboard.KEYCODE_DONE  || code == Keyboard.KEYCODE_MODE_CHANGE

    private fun isCategoryTab(code: Int) = code in -60..-51
    private fun isEmojiKey(code: Int)    = code == -70
    private fun isNavKey(code: Int)      = code == -61 || code == -62

    private fun shiftedLabel(raw: String): String {
        if (raw.length == 1 && raw[0] in 'a'..'z') return raw[0].uppercaseChar().toString()
        return ""
    }

    override fun onDraw(canvas: Canvas) {
        val t       = buildTheme()
        val W       = width.toFloat()
        val H       = height.toFloat()

        bgPaint.shader = LinearGradient(0f, 0f, 0f, H,
            intArrayOf(t.bg1, t.bg2), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, W, H, bgPaint)
        bgPaint.shader = null

        val kb      = keyboard ?: run { super.onDraw(canvas); return }
        val keys    = kb.keys  ?: run { super.onDraw(canvas); return }

        val gap     = dp(2.6f)
        val r       = dp(11f)
        val keyH    = keys.firstOrNull()?.height?.toFloat() ?: dp(44f)
        val basePx  = keyH * 0.42f
        val shifted = kb.isShifted

        // Blur shadow paint
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
            val kx      = key.x.toFloat()
            val ky      = key.y.toFloat()
            val kw      = key.width.toFloat()
            val kh      = key.height.toFloat()
            val code    = key.codes.firstOrNull() ?: 0
            val spec    = isSpecial(code)
            val isShiftK = code == Keyboard.KEYCODE_SHIFT
            val active  = isShiftK && shifted

            val l   = kx + gap
            val top = ky + gap
            val ri  = kx + kw - gap
            val bot = ky + kh - gap

            // ── Shadow ────────────────────────────────────────────
            val offY = if (t.isLight) dp(3f) else dp(2f)
            rrect.set(l + dp(1f), top + offY, ri - dp(1f), bot + offY)
            canvas.drawRoundRect(rrect, r, r, blurP)

            // ── Key body ──────────────────────────────────────────
            rrect.set(l, top, ri, bot)
            keyBodyPaint.color = when {
                active -> t.keyActive
                spec   -> t.keySpec
                else   -> t.key
            }
            canvas.drawRoundRect(rrect, r, r, keyBodyPaint)

            // ── Shine gradient ────────────────────────────────────
            val shineEnd = top + (bot - top) * 0.45f
            shinePaint.shader = LinearGradient(0f, top, 0f, shineEnd,
                intArrayOf(t.shineHi, t.shineLo), null, Shader.TileMode.CLAMP)
            rrect2.set(l + dp(2f), top + dp(1f), ri - dp(2f), shineEnd)
            canvas.drawRoundRect(rrect2, r * 0.7f, r * 0.7f, shinePaint)
            shinePaint.shader = null

            // ── Border ────────────────────────────────────────────
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

            // ── Caps indicator ────────────────────────────────────
            if (active) {
                capsLinePaint.color       = t.capsLineCol
                capsLinePaint.strokeWidth = dp(2.5f)
                canvas.drawLine(l + r, top + dp(3f), ri - r, top + dp(3f), capsLinePaint)
            }

            // ── Labels ────────────────────────────────────────────
            val rawLabel = key.label?.toString()
                ?: if (code > 31) code.toChar().toString() else ""

            when {
                isEmojiKey(code) -> {
                    if (rawLabel.isNotBlank()) {
                        textPaint.color    = t.textNorm
                        textPaint.textSize = kh * 0.52f
                        val ey = ky + kh * 0.56f - (textPaint.descent() + textPaint.ascent()) / 2f
                        canvas.drawText(rawLabel, kx + kw / 2f, ey, textPaint)
                    }
                }

                isCategoryTab(code) -> {
                    val tabIdx = (-code) - 51
                    textPaint.textSize = kh * 0.50f
                    textPaint.color    = t.textNorm
                    textPaint.alpha    = if (tabIdx == activeCategoryTab) 0xFF else 0x66
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
                    val lbl = when (code) {
                        Keyboard.KEYCODE_DELETE      -> "⌫"
                        Keyboard.KEYCODE_SHIFT       -> "⇧"
                        Keyboard.KEYCODE_DONE        -> "↵"
                        Keyboard.KEYCODE_MODE_CHANGE -> rawLabel.ifEmpty { "?123" }
                        else                         -> rawLabel
                    }
                    if (lbl.isNotEmpty()) {
                        textPaint.color    = if (active) t.textActive else t.textSpec
                        textPaint.textSize = when {
                            lbl.length > 3 -> basePx * 0.58f
                            else           -> basePx * 0.78f
                        }
                        val sy = ky + kh * 0.54f - (textPaint.descent() + textPaint.ascent()) / 2f
                        canvas.drawText(lbl, kx + kw / 2f, sy, textPaint)
                    }
                }

                shifted -> {
                    // ── Shift active: show shifted char large, no hint ─
                    val popupRaw   = key.popupCharacters?.toString()?.trim() ?: ""
                    val popupShift = popupRaw.split(" ").firstOrNull()?.trim() ?: ""
                    val autoShift  = if (popupShift.isEmpty()) shiftedLabel(rawLabel) else ""
                    val sv         = popupShift.ifEmpty { autoShift }.ifEmpty { rawLabel }

                    textPaint.color    = t.textNorm
                    var sz = when {
                        sv.length > 5 -> basePx * 0.46f
                        sv.length > 3 -> basePx * 0.60f
                        sv.length > 1 -> basePx * 0.74f
                        else          -> basePx
                    }
                    textPaint.textSize = sz
                    val maxW = (ri - l) * 0.82f
                    if (textPaint.measureText(sv) > maxW) {
                        sz *= maxW / textPaint.measureText(sv); textPaint.textSize = sz
                    }
                    val sy = ky + kh * 0.54f - (textPaint.descent() + textPaint.ascent()) / 2f
                    canvas.drawText(sv, kx + kw / 2f, sy, textPaint)
                    // NO hint drawn when shifted
                }

                else -> {
                    // ── Normal (not shifted): show main label only, NO hint ──
                    // Requirement: don't show the shift variant as a hint in normal mode
                    if (rawLabel.isNotEmpty()) {
                        textPaint.color = t.textNorm
                        var sz = when {
                            rawLabel.length > 5 -> basePx * 0.46f
                            rawLabel.length > 3 -> basePx * 0.60f
                            rawLabel.length > 1 -> basePx * 0.74f
                            else                -> basePx
                        }
                        textPaint.textSize = sz
                        val maxW = (ri - l) * 0.82f
                        if (textPaint.measureText(rawLabel) > maxW) {
                            sz *= maxW / textPaint.measureText(rawLabel)
                            textPaint.textSize = sz
                        }
                        // Centered — no hint so always perfectly centered
                        val labelY = ky + kh * 0.54f - (textPaint.descent() + textPaint.ascent()) / 2f
                        canvas.drawText(rawLabel, kx + kw / 2f, labelY, textPaint)
                    }
                    // ── HINT REMOVED: never show shift variant when not shifted ──
                }
            }
        }
    }
}