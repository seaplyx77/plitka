package com.plitka.launcher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import com.plitka.launcher.data.AppRepo
import com.plitka.launcher.data.Prefs
import com.plitka.launcher.data.Tile
import com.plitka.launcher.service.Badges
import com.plitka.launcher.util.dpf
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TileView(
    context: Context,
    val tile: Tile,
    private val repo: AppRepo,
    private val prefs: Prefs
) : View(context) {

    var onTileClick: ((Tile) -> Unit)? = null
    var onTileLongClick: ((TileView) -> Unit)? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(50, 255, 255, 255)
    }
    private val hoverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(220, 255, 255, 255)
    }
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = context.dpf(12f)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
    private val datePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 255, 255, 255)
        textSize = context.dpf(12f)
    }
    private val badgeBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val badgeFg = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0B0B0D")
        textSize = context.dpf(11f)
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    private var icon: Bitmap? = null
    private var folderIcons: List<Bitmap> = emptyList()
    private var pressedDown = false
    private var dragging = false
    private var hovered = false
    private var downRawX = 0f
    private var downRawY = 0f
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    private val dragSlop = dpf(10f)

    private val longPressRunnable = Runnable {
        if (pressedDown) {
            pressedDown = false
            dragging = true
            invalidate()
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            scaleX = 1.04f
            scaleY = 1.04f
            alpha = 0.92f
            elevation = dpf(10f)
            (parent as? TileBoardView)?.beginDrag(this)
            parent?.requestDisallowInterceptTouchEvent(true)
        }
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, 20_000)
        }
    }

    init {
        isClickable = true
        isFocusable = true
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        when (tile.type) {
            "app" -> {
                val side = (minOf(w, h) * 0.42f).toInt().coerceAtLeast(24)
                icon = tile.pkg?.let { repo.icon(it, side) }
            }
            "folder" -> {
                val side = (minOf(w, h) * 0.24f).toInt().coerceAtLeast(16)
                folderIcons = tile.items.take(4).mapNotNull { repo.icon(it.pkg, side) }
            }
            else -> Unit
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (tile.type == "clock") postDelayed(tickRunnable, 20_000)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(tickRunnable)
        removeCallbacks(longPressRunnable)
    }

    /** Подсветка плитки, на которую сейчас «ложится» папка. */
    fun setDropTarget(on: Boolean) {
        if (hovered == on) return
        hovered = on
        animate().scaleX(if (on) 1.07f else 1f).scaleY(if (on) 1.07f else 1f)
            .setDuration(120).start()
        invalidate()
    }

    /** Плавно вернуть плитку на место (отмена перетаскивания). */
    fun settleBack() {
        animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f)
            .alpha(1f).setDuration(180).start()
        elevation = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val radius = prefs.radiusDp * resources.displayMetrics.density
        val alpha = prefs.opacity * 255 / 100
        bgPaint.color = (tile.color and 0x00FFFFFF) or (alpha shl 24)
        val r = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(r, radius, radius, bgPaint)

        if (hovered) {
            hoverPaint.strokeWidth = dpf(3f)
            val inset = hoverPaint.strokeWidth / 2f
            val hr = RectF(inset, inset, width - inset, height - inset)
            canvas.drawRoundRect(hr, radius, radius, hoverPaint)
        }

        if (pressedDown && !dragging) {
            canvas.drawRoundRect(r, radius, radius, pressedPaint)
        }

        when (tile.type) {
            "clock" -> drawClock(canvas)
            "apps" -> drawSpecial(canvas, "•••", "Все приложения")
            "settings" -> drawSpecial(canvas, "⚙", "Настройки")
            "folder" -> drawFolder(canvas)
            else -> drawApp(canvas)
        }

        drawBadge(canvas)
    }

    private fun drawBadge(canvas: Canvas) {
        val n = when (tile.type) {
            "app" -> Badges.count(tile.pkg)
            "folder" -> tile.items.sumOf { Badges.count(it.pkg) }
            else -> 0
        }
        if (n <= 0) return

        val text = if (n > 99) "99+" else n.toString()
        val radius = dpf(11f)
        val cx = width - dpf(14f)
        val cy = dpf(14f)

        canvas.drawCircle(cx, cy, radius, badgeBg)
        val fm = badgeFg.fontMetrics
        canvas.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f, badgeFg)
    }

    private fun drawApp(canvas: Canvas) {
        icon?.let {
            val left = (width - it.width) / 2f
            val centerY = if (prefs.showLabels) height * 0.40f else height * 0.50f
            val top = centerY - it.height / 2f
            canvas.drawBitmap(it, left, top, null)
        }
        if (prefs.showLabels) drawLabel(canvas, tile.label)
    }

    /** Папка: до четырёх иконок сеткой 2×2. */
    private fun drawFolder(canvas: Canvas) {
        if (folderIcons.isEmpty()) {
            drawSpecial(canvas, "▦", tile.label.ifBlank { "Папка" })
            return
        }
        val area = minOf(width, height) * 0.54f
        val left0 = (width - area) / 2f
        val top0 = if (prefs.showLabels) height * 0.40f - area / 2f else (height - area) / 2f
        val step = area / 2f
        val pad = step * 0.12f
        for (i in folderIcons.indices) {
            val bmp = folderIcons[i]
            val col = i % 2
            val row = i / 2
            val cellLeft = left0 + col * step
            val cellTop = top0 + row * step
            val avail = step - pad * 2
            val side = minOf(avail, bmp.width.toFloat().coerceAtLeast(1f))
            val dx = cellLeft + (step - side) / 2f
            val dy = cellTop + (step - side) / 2f
            canvas.drawBitmap(
                bmp, null,
                RectF(dx, dy, dx + side, dy + side),
                null
            )
        }
        if (prefs.showLabels) drawLabel(canvas, tile.label.ifBlank { "Папка" })
    }

    private fun drawSpecial(canvas: Canvas, glyph: String, fallback: String) {
        val big = TextPaint(timePaint).apply { textSize = minOf(width, height) * 0.34f }
        val tw = big.measureText(glyph)
        canvas.drawText(glyph, (width - tw) / 2f, height * 0.46f - (big.descent() + big.ascent()) / 2f, big)
        if (prefs.showLabels) drawLabel(canvas, fallback)
    }

    private fun drawLabel(canvas: Canvas, text: String) {
        val avail = (width - dpf(20f)).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, labelPaint, avail)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()
        canvas.save()
        canvas.translate(dpf(10f), height - dpf(8f) - layout.height)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawClock(canvas: Canvas) {
        val now = Calendar.getInstance().time
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        val date = SimpleDateFormat("d MMMM, EEEE", Locale("ru")).format(now)

        timePaint.textSize = minOf(height * 0.44f, width * 0.27f)
        val ty = height * 0.50f - (timePaint.descent() + timePaint.ascent()) / 2f
        canvas.drawText(time, dpf(14f), ty, timePaint)

        val maxW = width - dpf(28f)
        val d = TextUtils.ellipsize(date, datePaint, maxW, TextUtils.TruncateAt.END).toString()
        canvas.drawText(d, dpf(14f), height - dpf(12f), datePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedDown = true
                downRawX = event.rawX
                downRawY = event.rawY
                invalidate()
                postDelayed(longPressRunnable, 450)
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    translationX = event.rawX - downRawX
                    translationY = event.rawY - downRawY
                    (parent as? TileBoardView)?.onDragMove(this)
                } else if (pressedDown) {
                    if (kotlin.math.abs(event.rawX - downRawX) > touchSlop ||
                        kotlin.math.abs(event.rawY - downRawY) > touchSlop
                    ) {
                        pressedDown = false
                        removeCallbacks(longPressRunnable)
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                if (dragging) {
                    val dropX = left + translationX + width / 2f
                    val dropY = top + translationY + height / 2f
                    val moved = kotlin.math.abs(event.rawX - downRawX) +
                        kotlin.math.abs(event.rawY - downRawY)
                    dragging = false
                    if (moved < dragSlop) {
                        settleBack()
                        performLongClick()
                    } else {
                        (parent as? TileBoardView)?.endDrag(this, dropX, dropY)
                    }
                    pressedDown = false
                    invalidate()
                } else {
                    val wasPressed = pressedDown
                    pressedDown = false
                    invalidate()
                    if (wasPressed) performClick()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                if (dragging) {
                    dragging = false
                    settleBack()
                }
                pressedDown = false
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        onTileClick?.invoke(tile)
        return true
    }

    override fun performLongClick(): Boolean {
        onTileLongClick?.invoke(this)
        return true
    }
}
