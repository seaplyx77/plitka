package com.plitka.launcher.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import com.plitka.launcher.data.Prefs
import com.plitka.launcher.util.dpf
import com.plitka.launcher.util.dpi

class TileBoardView(context: Context, private val prefs: Prefs) : ViewGroup(context) {

    private val tiles = ArrayList<TileView>()
    private val positions = HashMap<TileView, IntArray>()

    private val gap = context.dpi(10)
    private val pad = context.dpi(14)
    private var cell = 0

    /** Сколько держать палец над чужой плиткой, чтобы получилась папка. */
    private val folderDelay = 550L

    private val handler = Handler(Looper.getMainLooper())
    private var hoverCandidate: TileView? = null
    private var folderTarget: TileView? = null
    private val hoverRunnable = Runnable {
        val c = hoverCandidate ?: return@Runnable
        folderTarget = c
        c.setDropTarget(true)
    }

    var onOrderChanged: ((List<String>) -> Unit)? = null
    var onCreateFolder: ((String, String) -> Unit)? = null

    fun setTiles(list: List<TileView>) {
        clearHover()
        removeAllViews()
        tiles.clear()
        positions.clear()
        tiles.addAll(list)
        list.forEach { addView(it) }
        requestLayout()
        invalidate()
    }

    private fun cols(): Int = prefs.columns.coerceIn(3, 6)

    /**
     * Раскладка «первым подходящим местом» с учётом spanX/spanY.
     * Раньше позиция считалась как row*cols+col — из-за широких плиток индексы съезжали.
     */
    private fun pack(list: List<TileView>, cols: Int): HashMap<TileView, IntArray> {
        val res = HashMap<TileView, IntArray>()
        val taken = HashSet<Int>()

        for (t in list) {
            val sx = t.tile.spanX.coerceIn(1, cols)
            val sy = t.tile.spanY.coerceIn(1, 6)
            var row = 0
            while (true) {
                var placed = false
                for (c in 0..(cols - sx)) {
                    var free = true
                    loop@ for (dr in 0 until sy) {
                        for (dc in 0 until sx) {
                            if (taken.contains((row + dr) * 1000 + c + dc)) {
                                free = false
                                break@loop
                            }
                        }
                    }
                    if (free) {
                        res[t] = intArrayOf(c, row)
                        for (dr in 0 until sy) {
                            for (dc in 0 until sx) taken.add((row + dr) * 1000 + c + dc)
                        }
                        placed = true
                        break
                    }
                }
                if (placed) break
                row++
            }
        }
        return res
    }

    private fun rowCount(p: Map<TileView, IntArray>): Int {
        var rows = 1
        for ((t, pos) in p) {
            rows = maxOf(rows, pos[1] + t.tile.spanY.coerceIn(1, 6))
        }
        return rows
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val cols = cols()
        cell = ((width - pad * 2 - gap * (cols - 1)) / cols).coerceAtLeast(1)

        val packed = pack(tiles, cols)
        positions.clear()
        positions.putAll(packed)

        for (t in tiles) {
            val sx = t.tile.spanX.coerceIn(1, cols)
            val w = sx * cell + (sx - 1) * gap
            t.measure(
                MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(cell, MeasureSpec.EXACTLY)
            )
        }

        val rows = rowCount(packed)
        val height = pad * 2 + rows * cell + (rows - 1) * gap
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (tile in tiles) {
            val p = positions[tile] ?: continue
            val x = pad + p[0] * (cell + gap)
            val y = pad + p[1] * (cell + gap)
            tile.layout(x, y, x + tile.measuredWidth, y + tile.measuredHeight)
        }
    }

    fun refreshBadges() {
        for (i in 0 until childCount) getChildAt(i).invalidate()
    }

    // ---------------------------------------------------------------- перетаскивание

    fun beginDrag(tv: TileView) {
        clearHover()
        tv.bringToFront()
    }

    /** Вызывается на каждом MOVE: ищем плитку под пальцем и через паузу превращаем её в цель папки. */
    fun onDragMove(tv: TileView) {
        if (cell <= 0) return
        val cx = tv.left + tv.translationX + tv.width / 2f
        val cy = tv.top + tv.translationY + tv.height / 2f
        val under = tileAt(cx, cy, tv)
        val candidate = if (under != null && canMerge(tv, under)) under else null

        if (candidate === hoverCandidate) return

        hoverCandidate?.setDropTarget(false)
        folderTarget?.setDropTarget(false)
        folderTarget = null
        hoverCandidate = candidate
        handler.removeCallbacks(hoverRunnable)
        if (candidate != null) handler.postDelayed(hoverRunnable, folderDelay)
    }

    fun endDrag(tv: TileView, dropX: Float, dropY: Float) {
        val target = folderTarget
        clearHover()

        if (target != null) {
            onCreateFolder?.invoke(tv.tile.id, target.tile.id)
            return
        }
        if (cell <= 0) {
            tv.settleBack()
            return
        }

        val cols = cols()
        val col = ((dropX - pad) / (cell + gap)).toInt().coerceIn(0, cols - 1)
        val row = ((dropY - pad) / (cell + gap)).toInt().coerceAtLeast(0)

        val from = tiles.indexOf(tv)
        if (from < 0) return

        // Куда вставлять: перед первой плиткой, которая стоит в сетке не раньше места броска.
        val rest = ArrayList(tiles)
        rest.removeAt(from)
        val restPacked = pack(rest, cols)
        var index = rest.size
        for (i in rest.indices) {
            val p = restPacked[rest[i]] ?: continue
            if (p[1] > row || (p[1] == row && p[0] >= col)) {
                index = i
                break
            }
        }

        val old = HashMap<TileView, IntArray>()
        for (t in tiles) positions[t]?.let { old[t] = it }

        tiles.add(index, tiles.removeAt(from))

        val packed = pack(tiles, cols)
        positions.clear()
        positions.putAll(packed)

        // Плитка «долетает» из точки броска в свою клетку, остальные плавно разъезжаются.
        val movedPos = packed[tv]
        if (movedPos != null) {
            val toX = (pad + movedPos[0] * (cell + gap)).toFloat()
            val toY = (pad + movedPos[1] * (cell + gap)).toFloat()
            tv.translationX = dropX - tv.width / 2f - toX
            tv.translationY = dropY - tv.height / 2f - toY
            tv.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f)
                .alpha(1f).setDuration(220).start()
            tv.elevation = dpf(6f)
            postDelayed({ tv.elevation = 0f }, 240)
        } else {
            tv.settleBack()
        }

        for (t in tiles) {
            if (t === tv) continue
            val o = old[t] ?: continue
            val n = packed[t] ?: continue
            val fromX = pad + o[0] * (cell + gap)
            val fromY = pad + o[1] * (cell + gap)
            val toX = pad + n[0] * (cell + gap)
            val toY = pad + n[1] * (cell + gap)
            if (fromX != toX || fromY != toY) {
                t.animate().cancel()
                t.translationX = (fromX - toX).toFloat()
                t.translationY = (fromY - toY).toFloat()
                t.animate().translationX(0f).translationY(0f).setDuration(220).start()
            }
        }

        requestLayout()
        invalidate()
        onOrderChanged?.invoke(tiles.map { it.tile.id })
    }

    private fun tileAt(x: Float, y: Float, except: TileView?): TileView? {
        for (t in tiles) {
            if (t === except) continue
            if (x >= t.left && x <= t.right && y >= t.top && y <= t.bottom) return t
        }
        return null
    }

    private fun canMerge(dragged: TileView, target: TileView): Boolean {
        val a = dragged.tile.type
        val b = target.tile.type
        val aOk = a == "app" || a == "folder"
        val bOk = b == "app" || b == "folder"
        return aOk && bOk
    }

    private fun clearHover() {
        handler.removeCallbacks(hoverRunnable)
        hoverCandidate?.setDropTarget(false)
        folderTarget?.setDropTarget(false)
        hoverCandidate = null
        folderTarget = null
    }
}
