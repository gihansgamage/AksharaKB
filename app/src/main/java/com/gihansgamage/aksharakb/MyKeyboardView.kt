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

    var isEmojiMode: Boolean = false
    var activeCategoryTab: Int = 0
    var onEmojiSwipe: ((direction: Int) -> Unit)? = null

    private val keyBodyPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val keyBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shinePaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shadowPaint    = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
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

    // ── Swipe for emoji paging ────────────────────────────────────
    private val gestureDetector = GestureDetector(context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent,
                velocityX: Float, velocityY: Float
            ): Boolean {
                if (!isEmojiMode) return false
                val dx = e2.x - (e1?.x ?: return false)
                if (Math.abs(dx) < dp(50f)) return false
                if (Math.abs(velocityX) < 100f) return false
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

    // ── NO cached height — let layout shrink naturally when number row removed ──
    // onMeasure uses default KeyboardView behaviour

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setLayerType(LAYER_TYPE_HARDWARE, null)
        // Blur is applied at window level via window.setBackgroundBlurRadius()
        // called from MyInputMethodService.onWindowShown() — that blurs the
        // window background (what shows through transparent regions) without
        // touching the drawn key content.
    }

    fun applyBlurEffect() { /* no-op: blur done at window level */ }


    // ── Theme ─────────────────────────────────────────────────────
    private data class T(
        // Keys — fully opaque fills
        val key: Int,           // normal key body
        val keySpec: Int,       // shift/del/mode
        val keyActive: Int,     // shift when held
        // Border — thin crisp outline
        val border: Int,
        val borderActive: Int,
        // Shadow
        val shadow: Int,
        // Shine — very faint top highlight
        val shineHi: Int,
        // Text
        val textNorm: Int,
        val textSpec: Int,
        val textActive: Int,
        val capsLineCol: Int,
        val isLight: Boolean
    )

    private fun isDark() = prefs.theme == KeyboardPreferences.THEME_DARK

    private fun buildTheme() = if (isDark()) T(
        // ── DARK — deep charcoal keys on transparent background ───
        key          = 0xCC252836.toInt(),   // 80% opacity dark blue-grey
        keySpec      = 0xCC1E2030.toInt(),   // 80% opacity special
        keyActive    = 0xDD3A4880.toInt(),   // 87% opacity active shift
        // Border: single faint white rim
        border       = 0x28FFFFFF,
        borderActive = 0xCC8899EE.toInt(),
        // Shadow: offset drop
        shadow       = 0xAA000000.toInt(),
        shineHi      = 0x08FFFFFF,           // barely visible, no white line
        textNorm     = 0xFFFFFFFF.toInt(),
        textSpec     = 0xFFDDDDFF.toInt(),
        textActive   = 0xFFAABBFF.toInt(),
        capsLineCol  = 0xFF7788EE.toInt(),
        isLight      = false
    ) else T(
        // ── LIGHT — white keys on transparent background ──────────
        key          = 0xD8FFFFFF.toInt(),   // 85% opacity white
        keySpec      = 0xD8F0F2F9.toInt(),   // 85% opacity off-white
        keyActive    = 0xDDD0DAFF.toInt(),   // 87% opacity active shift
        border       = 0x00000000,           // no border — shadow only
        borderActive = 0x554466BB,
        shadow       = 0x30000044.toInt(),
        shineHi      = 0x0AFFFFFF,
        textNorm     = 0xFF3A3A52.toInt(),
        textSpec     = 0xFF2C2C48.toInt(),
        textActive   = 0xFF2233AA.toInt(),
        capsLineCol  = 0xFF3344CC.toInt(),
        isLight      = true
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

        // ── Background: faded-in frosted tint ────────────────────
        // Background: very low opacity neutral tint over the blurred window bg.
        // window.setBackgroundBlurRadius (in onWindowShown) does the actual blur.
        // We just draw a faint tint so keys have something to sit against.
        // 0x22 = ~13% opacity — barely visible, lets blurred bg dominate.
        val bgTint = if (isDark()) 0x22000000.toInt() else 0x18FFFFFF.toInt()
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgTint }
        canvas.drawRect(0f, 0f, W, H, bgPaint)

        val kb      = keyboard ?: run { super.onDraw(canvas); return }
        val keys    = kb.keys  ?: run { super.onDraw(canvas); return }

        val gap     = dp(2.6f)
        val r       = dp(11f)
        val keyH    = keys.firstOrNull()?.height?.toFloat() ?: dp(44f)
        val basePx  = keyH * 0.42f
        val shifted = kb.isShifted

        // Blur shadow — recreate only when blur radius changes
        val blurR = if (t.isLight) dp(5f) else dp(3f)
        if (blurPaint == null || lastBlurR != blurR) {
            blurPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style      = Paint.Style.FILL
                color      = t.shadow
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

            // ── 1. Drop shadow ────────────────────────────────────
            rrect.set(l + dp(1f), top + dp(2f), ri - dp(1f), bot + dp(2f))
            canvas.drawRoundRect(rrect, r, r, blurP)

            // ── 2. Key body ───────────────────────────────────────
            rrect.set(l, top, ri, bot)
            keyBodyPaint.color = when {
                active -> t.keyActive
                spec   -> t.keySpec
                else   -> t.key
            }
            canvas.drawRoundRect(rrect, r, r, keyBodyPaint)

            // ── 4. Very faint top shine (liquid surface) ──────────
            val shineH = (bot - top) * 0.38f
            shinePaint.shader = LinearGradient(
                0f, top, 0f, top + shineH,
                intArrayOf(t.shineHi, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            rrect2.set(l + dp(2f), top, ri - dp(2f), top + shineH)
            canvas.drawRoundRect(rrect2, r * 0.8f, r * 0.8f, shinePaint)
            shinePaint.shader = null

            // ── 5. Crisp border ────────────────────────────────────
            if (Color.alpha(t.border) > 0 || active) {
                rrect.set(l, top, ri, bot)
                keyBorderPaint.color       = if (active) t.borderActive else t.border
                keyBorderPaint.strokeWidth = dp(0.7f)
                canvas.drawRoundRect(rrect, r, r, keyBorderPaint)
            }

            // ── 6. Caps indicator ─────────────────────────────────
            if (active) {
                capsLinePaint.color       = t.capsLineCol
                capsLinePaint.strokeWidth = dp(2f)
                canvas.drawLine(l + r, top + dp(3f), ri - r, top + dp(3f), capsLinePaint)
            }

            // ── 7. Labels ─────────────────────────────────────────
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
                    textPaint.textSize = kh * 0.48f
                    textPaint.color    = t.textNorm
                    textPaint.alpha    = if (tabIdx == activeCategoryTab) 0xFF else 0x66
                    val ty = ky + kh * 0.56f - (textPaint.descent() + textPaint.ascent()) / 2f
                    canvas.drawText(rawLabel, kx + kw / 2f, ty, textPaint)
                    textPaint.alpha = 0xFF
                }
                isNavKey(code) -> {
                    textPaint.color    = t.textSpec
                    textPaint.textSize = basePx * 0.68f
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
                            lbl.length > 3 -> basePx * 0.56f
                            else           -> basePx * 0.76f
                        }
                        val sy = ky + kh * 0.54f - (textPaint.descent() + textPaint.ascent()) / 2f
                        canvas.drawText(lbl, kx + kw / 2f, sy, textPaint)
                    }
                }
                shifted -> {
                    // Shift active: show shifted variant large, no hint
                    val popupRaw   = key.popupCharacters?.toString()?.trim() ?: ""
                    val popupShift = popupRaw.split(" ").firstOrNull()?.trim() ?: ""
                    val sv         = popupShift.ifEmpty { shiftedLabel(rawLabel) }.ifEmpty { rawLabel }
                    textPaint.color    = t.textNorm
                    var sz = when {
                        sv.length > 5 -> basePx * 0.46f
                        sv.length > 3 -> basePx * 0.60f
                        sv.length > 1 -> basePx * 0.74f
                        else          -> basePx
                    }
                    textPaint.textSize = sz
                    val maxW = (ri - l) * 0.82f
                    if (textPaint.measureText(sv) > maxW) { sz *= maxW / textPaint.measureText(sv); textPaint.textSize = sz }
                    val sy = ky + kh * 0.54f - (textPaint.descent() + textPaint.ascent()) / 2f
                    canvas.drawText(sv, kx + kw / 2f, sy, textPaint)
                }
                else -> {
                    // Normal: main label only, centered, no hint
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
                            sz *= maxW / textPaint.measureText(rawLabel); textPaint.textSize = sz
                        }
                        val labelY = ky + kh * 0.54f - (textPaint.descent() + textPaint.ascent()) / 2f
                        canvas.drawText(rawLabel, kx + kw / 2f, labelY, textPaint)
                    }
                }
            }
        }
    }
}