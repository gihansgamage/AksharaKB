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
    /** Set by IME to allow correct shifted-label rendering for Sinhala/special keys */
    var shiftMap: Map<Int, Int> = emptyMap()

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
        handleLongPress(me)
        return super.onTouchEvent(me)
    }

    // ── Long-press popup ──────────────────────────────────────────
    private val longPressHandler  = android.os.Handler(android.os.Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var longPressKey: Keyboard.Key? = null
    private var popupWindow: android.widget.PopupWindow? = null
    private val LONG_PRESS_DELAY = 400L  // ms

    var onPopupCharSelected: ((String) -> Unit)? = null

    private fun handleLongPress(me: MotionEvent) {
        when (me.action) {
            MotionEvent.ACTION_DOWN -> {
                val kb = keyboard ?: return
                val key = kb.keys?.firstOrNull { k ->
                    me.x >= k.x && me.x < k.x + k.width &&
                            me.y >= k.y && me.y < k.y + k.height
                } ?: return
                val popupRaw = key.popupCharacters?.toString()?.trim() ?: ""
                if (popupRaw.isEmpty()) return
                longPressKey = key
                val r = Runnable { showPopupChars(key, popupRaw) }
                longPressRunnable = r
                longPressHandler.postDelayed(r, LONG_PRESS_DELAY)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressHandler.removeCallbacks(longPressRunnable ?: return)
                longPressRunnable = null
                longPressKey = null
            }
            MotionEvent.ACTION_MOVE -> {
                val key = longPressKey ?: return
                if (me.x < key.x || me.x > key.x + key.width ||
                    me.y < key.y || me.y > key.y + key.height) {
                    longPressHandler.removeCallbacks(longPressRunnable ?: return)
                    longPressRunnable = null
                    longPressKey = null
                }
            }
        }
    }

    private fun showPopupChars(key: Keyboard.Key, popupRaw: String) {
        dismissPopup()
        val chars = popupRaw.split(" ").filter { it.isNotBlank() }
        if (chars.isEmpty()) return

        val isDarkTheme = prefs.theme == "dark"
        val bgColor  = if (isDarkTheme) 0x88252836.toInt() else 0xCCFFFFFF.toInt()
        val txtColor = if (isDarkTheme) 0xFFFFFFFF.toInt() else 0xFF111111.toInt()
        val cellPx   = dp(52f).toInt()
        val padPx    = dp(6f).toInt()

        // Build popup view
        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(padPx, padPx, padPx, padPx)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape        = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(14f)
                setColor(bgColor)
                setStroke(dp(0.7f).toInt(), if (isDarkTheme) 0x44FFFFFF else 0x44000000)
            }
        }

        chars.forEach { ch ->
            container.addView(android.widget.TextView(context).apply {
                text      = ch
                textSize  = 22f
                gravity   = android.view.Gravity.CENTER
                setTextColor(txtColor)
                layoutParams = android.widget.LinearLayout.LayoutParams(cellPx, cellPx)
                    .also { it.setMargins(dp(2f).toInt(), 0, dp(2f).toInt(), 0) }
                setOnClickListener {
                    onPopupCharSelected?.invoke(ch)
                    dismissPopup()
                }
            })
        }

        // Measure container
        container.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        )
        val pw = container.measuredWidth
        val ph = container.measuredHeight

        popupWindow = android.widget.PopupWindow(container, pw, ph, false).apply {
            isOutsideTouchable = true
            isTouchable        = true
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            setTouchInterceptor { _, ev ->
                if (ev.action == MotionEvent.ACTION_OUTSIDE) { dismissPopup(); true } else false
            }
        }

        // Position above the key
        val loc = IntArray(2); getLocationInWindow(loc)
        val kx = loc[0] + key.x + key.width / 2 - pw / 2
        val ky = loc[1] + key.y - ph - dp(8f).toInt()

        popupWindow?.showAtLocation(this, android.view.Gravity.NO_GRAVITY,
            kx.coerceAtLeast(0), ky.coerceAtLeast(0))

        // Cancel the key's own action so we don't also type the base char
        val cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        super.onTouchEvent(cancel)
        cancel.recycle()
    }

    private fun dismissPopup() {
        popupWindow?.dismiss()
        popupWindow = null
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
        val size   = dp(50f)
        val cx     = keyX + keyW / 2f
        // Position preview fully ABOVE the key with a gap
        val cy     = keyY - size - dp(4f)
        previewRect.set(cx - size / 2f, cy, cx + size / 2f, cy + size)
        previewLabel = label
        invalidate(
            (previewRect.left  - dp(4f)).toInt(),
            (previewRect.top   - dp(4f)).toInt(),
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

    // ── NO cached height — let layout shrink naturally when number row removed ──
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
        key          = 0x66404040.toInt(),   // 40% pure grey
        keySpec      = 0x66323232.toInt(),   // 40% dark grey
        keyActive    = 0x88606060.toInt(),   // 53% mid grey active
        // Border: single faint white rim
        border       = 0x00000000,
        borderActive = 0x66AAAAAA.toInt(),
        // Shadow: offset drop
        shadow       = 0x66000000.toInt(),
        shineHi      = 0x14FFFFFF,           // subtle shine
        textNorm     = 0xFFFFFFFF.toInt(),
        textSpec     = 0xFFDDDDDD.toInt(),
        textActive   = 0xFFCCCCCC.toInt(),
        capsLineCol  = 0xFFAAAAAA.toInt(),
        isLight      = false
    ) else T(
        // ── LIGHT — white keys on transparent background ──────────
        key          = 0x77FFFFFF.toInt(),   // 47% white
        keySpec      = 0x77EBEBEB.toInt(),   // 47% light grey
        keyActive    = 0x88D0D0D0.toInt(),   // 53% grey active
        border       = 0x00000000,
        borderActive = 0x33888888,
        shadow       = 0x1A000033.toInt(),
        shineHi      = 0x0AFFFFFF,
        textNorm     = 0xFF2A2A2A.toInt(),
        textSpec     = 0xFF1A1A1A.toInt(),
        textActive   = 0xFF555555.toInt(),
        capsLineCol  = 0xFF777777.toInt(),
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
        val blurR = if (t.isLight) dp(8f) else dp(6f)
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
            rrect.set(l + dp(0.5f), top + dp(3f), ri - dp(0.5f), bot + dp(1f))
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
                    // Shifted label: use shiftMap for Sinhala/special codes (>127),
                    // use popupCharacters only for Latin keys (codes 32-127)
                    val sv = when {
                        code > 127 && shiftMap.containsKey(code) ->
                            shiftMap[code]!!.toChar().toString()
                        code in 32..127 -> {
                            val popupRaw = key.popupCharacters?.toString()?.trim() ?: ""
                            val popupFirst = popupRaw.split(" ").firstOrNull()?.trim() ?: ""
                            popupFirst.ifEmpty { shiftedLabel(rawLabel) }.ifEmpty { rawLabel }
                        }
                        else -> shiftedLabel(rawLabel).ifEmpty { rawLabel }
                    }
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
                    // Show small hint ONLY on specific Sinhala long-press keys
                    // Or if the key explicitly has 3 (or more) characters defined in popup Raw
                    val hintKeyCodes = setOf(3484, 3490, 3497, 3503, 3540)
                    val popupRaw  = key.popupCharacters?.toString()?.trim() ?: ""
                    val popups = popupRaw.split(" ").filter { it.isNotBlank() }
                    
                    val hintChar = when {
                        popups.size >= 2 && !isSpecial(code) && code !in 32..127 -> popups[1] // 3-char logic (base + shift + long-press): the 2nd popup is the long press hint
                        code in hintKeyCodes && popups.isNotEmpty() -> popups[0]
                        else -> ""
                    }

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
                        // Main label always vertically centered
                        val labelY = ky + kh * 0.54f - (textPaint.descent() + textPaint.ascent()) / 2f
                        canvas.drawText(rawLabel, kx + kw / 2f, labelY, textPaint)
                    }

                    // Small hint in top-right corner
                    if (hintChar.isNotEmpty()) {
                        hintPaint.color    = if (isDark()) 0xAAFFFFFF.toInt() else 0x88222222.toInt()
                        hintPaint.textSize = basePx * 0.42f
                        val hx = ri - dp(2f)
                        val hy = ky + dp(2f) - hintPaint.ascent()
                        canvas.drawText(hintChar, hx, hy, hintPaint)
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