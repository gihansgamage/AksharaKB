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

    private val bgPaint     = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyFill     = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val keyStroke   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val keyGlow     = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val topShine    = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val keyText     = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface  = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val scanPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 0.4f
    }
    private val rrect  = RectF()
    private val rrect2 = RectF()

    fun setKeyboardImage(b: Bitmap?) { keyboardBg = b; invalidate() }
    fun refreshPrefs() { prefs = KeyboardPreferences(context); invalidate() }

    private fun dp(v: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private data class Theme(
        val bgA: Int, val bgB: Int,
        val fill: Int, val specFill: Int, val accentFill: Int,
        val stroke: Int, val glow: Int, val shine: Int,
        val shadow: Int, val scan: Int,
        val text: Int, val specText: Int
    )

    private fun theme(): Theme = when (prefs.theme) {
        KeyboardPreferences.THEME_DARK -> Theme(
            bgA = 0xFF0A0A14.toInt(), bgB = 0xFF141428.toInt(),
            fill = 0x20FFFFFF, specFill = 0x2A6D28E0, accentFill = 0x3A7C3AED,
            stroke = 0x388B7CF8, glow = 0x166D28E0, shine = 0x16FFFFFF,
            shadow = 0x22000000, scan = 0x05FFFFFF,
            text = 0xFFEEE8FF.toInt(), specText = 0xFFB39DDB.toInt()
        )
        KeyboardPreferences.THEME_OCEAN -> Theme(
            bgA = 0xFF010B18.toInt(), bgB = 0xFF031E3A.toInt(),
            fill = 0x1A1E5080, specFill = 0x2A0D4F7A, accentFill = 0x330E6EA5,
            stroke = 0x4038BDF8, glow = 0x160E6EA5, shine = 0x1488DDFF,
            shadow = 0x22000022, scan = 0x05AADDFF,
            text = 0xFFCCEEFF.toInt(), specText = 0xFF7DD3FC.toInt()
        )
        KeyboardPreferences.THEME_SUNSET -> Theme(
            bgA = 0xFF0D0010.toInt(), bgB = 0xFF2A0630.toInt(),
            fill = 0x1E3D0A46, specFill = 0x2A6A0F80, accentFill = 0x33A020C0,
            stroke = 0x44D946EF, glow = 0x16C026E0, shine = 0x14FF88FF,
            shadow = 0x22100010, scan = 0x05FF88FF,
            text = 0xFFF0D0FF.toInt(), specText = 0xFFE879F9.toInt()
        )
        else -> Theme( // Default light
            bgA = 0xFFCDD5F4.toInt(), bgB = 0xFFB0BCE8.toInt(),
            fill = 0xCCFFFFFF.toInt(), specFill = 0x1E7C3AED, accentFill = 0x2E7C3AED,
            stroke = 0x667C3AED, glow = 0x107C3AED, shine = 0xAAFFFFFF.toInt(),
            shadow = 0x18000055, scan = 0x056666AA,
            text = 0xFF1E0A40.toInt(), specText = 0xFF5B21B6.toInt()
        )
    }

    private fun isSpecial(code: Int) =
        code == Keyboard.KEYCODE_DELETE || code == Keyboard.KEYCODE_SHIFT ||
                code == Keyboard.KEYCODE_DONE  || code == Keyboard.KEYCODE_MODE_CHANGE ||
                code == -10 || code == -20 || code == -40

    private fun isAccent(code: Int) =
        code == Keyboard.KEYCODE_DONE || code == -10

    override fun onDraw(canvas: Canvas) {
        val t = theme()
        val W = width.toFloat()
        val H = height.toFloat()

        // Background
        if (keyboardBg != null && prefs.theme == KeyboardPreferences.THEME_CUSTOM) {
            canvas.drawBitmap(keyboardBg!!, null, Rect(0, 0, width, height), null)
            canvas.drawColor(0xBB000000.toInt())
        } else {
            bgPaint.shader = LinearGradient(0f, 0f, 0f, H, t.bgA, t.bgB, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, W, H, bgPaint)
        }

        // Scan lines
        scanPaint.color = t.scan
        var sy = 0f; val ss = dp(3f)
        while (sy < H) { canvas.drawLine(0f, sy, W, sy, scanPaint); sy += ss }

        val kb   = keyboard ?: run { super.onDraw(canvas); return }
        val keys = kb.keys  ?: run { super.onDraw(canvas); return }

        // ── Key spacing ──────────────────────────────────────────
        // All XML gap values are 0dp — we control spacing entirely here.
        // innerPad = space from key cell boundary to visible key rect (half-gap each side)
        val innerPad = dp(2.2f)   // 2.2dp each side → 4.4dp between keys, looks perfect
        val r        = dp(10f)    // corner radius — large = liquid pill look
        val kh0      = if (keys.isNotEmpty()) keys[0].height.toFloat() else dp(44f)
        val basePx   = kh0 * 0.30f

        for (key in keys) {
            val kx     = key.x.toFloat()
            val ky     = key.y.toFloat()
            val kw     = key.width.toFloat()
            val kh     = key.height.toFloat()
            val spec   = isSpecial(key.codes.firstOrNull() ?: 0)
            val accent = isAccent(key.codes.firstOrNull() ?: 0)

            val l   = kx + innerPad
            val top = ky + innerPad
            val ri  = kx + kw - innerPad
            val bot = ky + kh - innerPad

            // Shadow
            shadowPaint.color = t.shadow
            rrect.set(l + dp(0.5f), top + dp(1.8f), ri + dp(0.5f), bot + dp(1.8f))
            canvas.drawRoundRect(rrect, r, r, shadowPaint)

            // Glow halo
            rrect2.set(l - dp(1f), top - dp(0.5f), ri + dp(1f), bot + dp(0.5f))
            keyGlow.color = t.glow; keyGlow.strokeWidth = dp(3f)
            canvas.drawRoundRect(rrect2, r + dp(1f), r + dp(1f), keyGlow)

            // Fill
            rrect.set(l, top, ri, bot)
            keyFill.color = when {
                accent -> t.accentFill
                spec   -> t.specFill
                else   -> t.fill
            }
            canvas.drawRoundRect(rrect, r, r, keyFill)

            // Stroke
            keyStroke.color = t.stroke; keyStroke.strokeWidth = dp(0.5f)
            canvas.drawRoundRect(rrect, r, r, keyStroke)

            // Top shine
            val shH = (bot - top) * 0.32f
            rrect2.set(l + dp(2f), top + dp(1f), ri - dp(2f), top + shH)
            topShine.shader = LinearGradient(
                0f, top + dp(1f), 0f, top + shH, t.shine, 0x00FFFFFF, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(rrect2, r * 0.5f, r * 0.5f, topShine)

            // Caps-active indicator: bright top line on shift key
            if ((key.codes.firstOrNull() ?: 0) == Keyboard.KEYCODE_SHIFT && keyboard?.isShifted == true) {
                val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = dp(2f)
                    color = 0xFFB39DDB.toInt()
                }
                canvas.drawLine(l + r, top + dp(1f), ri - r, top + dp(1f), capPaint)
            }

            // Label
            val label = when {
                key.label != null -> key.label.toString()
                key.codes.isNotEmpty() && key.codes[0] > 31 -> key.codes[0].toChar().toString()
                else -> ""
            }
            if (label.isNotEmpty()) {
                keyText.color = if (spec || accent) t.specText else t.text
                keyText.textSize = when {
                    label.length > 5 -> basePx * 0.60f
                    label.length > 3 -> basePx * 0.75f
                    label.length > 1 -> basePx * 0.85f
                    else             -> basePx
                }
                val maxW = (ri - l) * 0.84f
                if (keyText.measureText(label) > maxW) {
                    keyText.textSize *= maxW / keyText.measureText(label)
                }
                val cx = kx + kw / 2f
                val cy = ky + kh / 2f - (keyText.descent() + keyText.ascent()) / 2f
                canvas.drawText(label, cx, cy, keyText)
            }
        }

        if (prefs.showBorder) {
            keyStroke.color = t.stroke; keyStroke.strokeWidth = dp(0.8f)
            canvas.drawRect(0f, 0f, W, H, keyStroke)
        }
    }
}