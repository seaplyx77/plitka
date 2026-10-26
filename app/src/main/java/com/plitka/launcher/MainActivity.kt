package com.plitka.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.Toast
import com.plitka.launcher.data.AppRepo
import com.plitka.launcher.data.Prefs
import com.plitka.launcher.data.Tile
import com.plitka.launcher.data.TileStore
import com.plitka.launcher.service.Badges
import com.plitka.launcher.ui.AppListActivity
import com.plitka.launcher.ui.FolderDialog
import com.plitka.launcher.ui.SettingsActivity
import com.plitka.launcher.ui.TileBoardView
import com.plitka.launcher.ui.TileView
import com.plitka.launcher.util.applySystemBarPadding
import com.plitka.launcher.util.isDefaultHome
import com.plitka.launcher.util.requestDefaultHome
import com.plitka.launcher.util.setStatusBarHidden
import com.plitka.launcher.util.transparentSystemBars
import kotlin.math.abs

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepo
    private lateinit var board: TileBoardView

    private val gesture by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                if (dx > 180 && abs(velocityX) > abs(velocityY) * 1.5f) {
                    openApps()
                    return true
                }
                return false
            }
        })
    }

    private val badgeCallback: () -> Unit = {
        runOnUiThread { board.refreshBadges() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        transparentSystemBars()
        window.setBackgroundDrawable(ColorDrawable(Color.parseColor("#0B0B0D")))
        prefs = Prefs(this)
        repo = AppRepo(this)
        board = TileBoardView(this, prefs)
        board.onOrderChanged = { ids ->
            val current = TileStore.loadOrCreate(this)
            val byId = HashMap<String, Tile>()
            current.forEach { byId[it.id] = it }
            val reordered = ArrayList<Tile>(ids.size)
            ids.forEach { id -> byId.remove(id)?.let { reordered.add(it) } }
            reordered.addAll(byId.values)
            TileStore.save(this, reordered)
        }
        board.onCreateFolder = { draggedId, targetId ->
            if (FolderDialog.makeFolder(this, draggedId, targetId)) {
                Toast.makeText(this, "Папка создана", Toast.LENGTH_SHORT).show()
            }
            rebuild()
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            setBackgroundColor(Color.parseColor("#0B0B0D"))
            addView(board)
        }
        scroll.applySystemBarPadding()
        setContentView(scroll)
        askDefaultHomeOnce()
    }

    /** При первом запуске предлагаем выбрать «Плитку» домашним экраном. */
    private fun askDefaultHomeOnce() {
        if (prefs.homePrompted) return
        prefs.homePrompted = true
        if (isDefaultHome()) return

        AlertDialog.Builder(this)
            .setTitle("Сделать «Плитку» домашним экраном?")
            .setMessage(
                "Тогда кнопка «Домой» и включение телефона будут открывать этот рабочий стол.\n\n" +
                    "Передумать можно в любой момент: Настройки → «Сделать домашним экраном»."
            )
            .setPositiveButton("Выбрать") { _, _ -> requestDefaultHome() }
            .setNegativeButton("Позже", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        setStatusBarHidden(prefs.hideStatusBar)
        Badges.addCallback(badgeCallback)
        rebuild()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setStatusBarHidden(prefs.hideStatusBar)
    }

    override fun onPause() {
        Badges.removeCallback(badgeCallback)
        super.onPause()
    }

    private fun rebuild() {
        val tiles = TileStore.loadOrCreate(this)
        val views = tiles.map { t ->
            TileView(this, t, repo, prefs).apply {
                onTileClick = ::handleClick
                onTileLongClick = ::showTileMenu
            }
        }
        board.setTiles(views)
    }

    private fun handleClick(tile: Tile) {
        when (tile.type) {
            "app" -> tile.pkg?.let { repo.launch(it) }
            "folder" -> FolderDialog.show(this, tile.id, repo) { rebuild() }
            "apps" -> openApps()
            "settings" -> startActivity(Intent(this, SettingsActivity::class.java))
            "clock" -> Unit
        }
    }

    private fun openApps() {
        startActivity(Intent(this, AppListActivity::class.java))
        overridePendingTransition(0, 0)
    }

    private fun showTileMenu(view: TileView) {
        val popup = PopupMenu(this, view)
        popup.menu.add(0, 1, 0, "Шире")
        popup.menu.add(0, 2, 1, "Уже")
        popup.menu.add(0, 3, 2, "Сменить цвет")
        if (view.tile.type == "folder") popup.menu.add(0, 5, 3, "Разобрать папку")
        popup.menu.add(0, 4, 4, "Убрать")
        popup.setOnMenuItemClickListener { item ->
            val tiles = TileStore.loadOrCreate(this)
            val t = tiles.firstOrNull { it.id == view.tile.id }
            when (item.itemId) {
                1 -> if (t != null && t.spanX < prefs.columns) t.spanX++
                2 -> if (t != null && t.spanX > 1) t.spanX--
                3 -> if (t != null) t.color = nextColor(t.color)
                4 -> if (t != null) tiles.remove(t)
                5 -> if (t != null && t.type == "folder") {
                    val at = tiles.indexOf(t)
                    tiles.removeAt(at)
                    val unpacked = t.items.map { fi ->
                        Tile(
                            id = "app:" + fi.pkg + ":" + System.currentTimeMillis(),
                            type = "app",
                            pkg = fi.pkg,
                            label = fi.label,
                            color = t.color,
                            spanX = 1,
                            spanY = 1
                        )
                    }
                    tiles.addAll(at, unpacked)
                }
            }
            TileStore.save(this, tiles)
            rebuild()
            true
        }
        popup.show()
    }

    private fun nextColor(current: Int): Int {
        val idx = Tile.PALETTE.indexOf(current)
        return Tile.PALETTE[(if (idx < 0) 0 else idx + 1) % Tile.PALETTE.size]
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gesture.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // на домашнем экране "назад" ничего не делает
    }
}
