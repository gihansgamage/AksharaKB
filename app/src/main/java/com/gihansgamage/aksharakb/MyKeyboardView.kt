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
    var lastTouchX: Float = 0f
    var lastTouchY: Float = 0f

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
        lastTouchX = me.x
        lastTouchY = me.y
        if (isEmojiMode) gestureDetector.onTouchEvent(me)
        handleCustomPreview(me)
        return super.onTouchEvent(me)
    }

    private fun handleCustomPreview(me: MotionEvent) {
        if (!prefs.showPopupKeys) return
        val kb = keyboard ?: return
        when (me.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val key = kb.keys?.firstOrNull { k ->
                    me.x >= k.x && me.x < k.x + k.width &&
                            me.y >= k.y && me.y < k.y + k.height
                } ?: run { hideCustomPreview(); return }
                val code = key.codes?.firstOrNull() ?: 0
                if (code <= 0) { hideCustomPreview(); return }
                // Always use code.toChar() for reliable preview
                // key.label can be empty for \ due to XML escape handling
                val lbl = when {
                    code == 92 -> "\\"
                    code > 31  -> code.toChar().toString()
                    else       -> key.label?.toString()?.trim() ?: ""
                }
                if (lbl.isNotEmpty()) showCustomPreview(key, lbl)
                else hideCustomPreview()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> hideCustomPreview()
        }
    }

    private fun showCustomPreview(key: Keyboard.Key, label: String) {
        val keyX   = key.x.toFloat()
        val keyY   = key.y.toFloat()
        val keyW   = key.width.toFloat()
        val keyH   = key.height.toFloat()
        val size   = dp(48f)
        val cx     = keyX + keyW / 2f
        val cy     = keyY - size * 0.3f
        val r      = dp(10f)
        // Draw preview as an overlay by posting an invalidate with preview state
        previewRect.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f)
        previewLabel = label
        invalidate(
            (previewRect.left - dp(4f)).toInt(),
            (previewRect.top  - dp(4f)).toInt(),
            (previewRect.right + dp(4f)).toInt(),
            (previewRect.bottom + dp(4f)).toInt()
        )
    }

    private fun hideCustomPreview() {
        if (previewLabel.isNotEmpty()) {
            previewLabel = ""
            invalidate()
        }
    }

    private var previewLabel = ""

    fun setKeyboardImage(b: Bitmap?) { keyboardBg = b; invalidate() }
    fun refreshPrefs() { prefs = KeyboardPreferences(context); invalidate() }

    private fun dp(v: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    // Static height — cache the largest measured height so all keyboard variants
    // (with/without number row, emoji panel) maintain the same height.
    private var cachedHeight = 0
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val h = measuredHeight
        if (h > cachedHeight) cachedHeight = h
        if (cachedHeight > 0) setMeasuredDimension(measuredWidth, cachedHeight)
    }



    fun applyBlurEffect() { /* no-op: blur done at window level */ }

    // Custom preview popup — drawn manually so ALL characters (incl. \) show correctly.
    // We disable the system preview and draw our own overlay window.
    private var previewPopup: android.widget.PopupWindow? = null
    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign  = Paint.Align.CENTER
        typeface   = Typeface.create("sans-serif-medium", Typeface.BOLD)
        color      = 0xFFFFFFFF.toInt()
        textSize   = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 22f,
            context.resources.displayMetrics)
    }
    private val previewBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCC7C3AED.toInt(); style = Paint.Style.FILL
    }
    private val previewRect = RectF()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setLayerType(LAYER_TYPE_HARDWARE, null)
        isPreviewEnabled = false   // disable system preview — we draw our own
    }


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
        key          = 0x88252836.toInt(),   // 53% opacity dark blue-grey
        keySpec      = 0x881E2030.toInt(),   // 53% opacity special
        keyActive    = 0xAA3A4880.toInt(),   // 67% opacity active shift
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
        key          = 0x99FFFFFF.toInt(),   // 60% opacity white
        keySpec      = 0x99F0F2F9.toInt(),   // 60% opacity off-white
        keyActive    = 0xAAD0DAFF.toInt(),   // 67% opacity active shift
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
        // Background handled by keyboard_panel container in keyboard_view.xml.
        // MyKeyboardView draws nothing here — fully transparent.

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
        // Draw custom key preview overlay
        if (previewLabel.isNotEmpty() && !previewRect.isEmpty) {
            val r = dp(10f)
            canvas.drawRoundRect(previewRect, r, r, previewBgPaint)
            val ty = previewRect.centerY() - (previewPaint.descent() + previewPaint.ascent()) / 2f
            canvas.drawText(previewLabel, previewRect.centerX(), ty, previewPaint)
        }
    }
}