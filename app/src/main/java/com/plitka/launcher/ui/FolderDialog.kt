package com.plitka.launcher.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import com.plitka.launcher.data.AppRepo
import com.plitka.launcher.data.FolderItem
import com.plitka.launcher.data.Tile
import com.plitka.launcher.data.TileStore
import com.plitka.launcher.util.dpi

/** Окно папки: сетка приложений, тап — запуск, долгое нажатие — убрать из папки. */
object FolderDialog {

    private const val COLS = 3

    fun show(activity: Activity, folderId: String, repo: AppRepo, onChanged: () -> Unit) {
        val tile = TileStore.loadOrCreate(activity).firstOrNull { it.id == folderId } ?: return
        if (tile.type != "folder") return

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dpi(18), activity.dpi(18), activity.dpi(18), activity.dpi(20))
            background = GradientDrawable().apply {
                cornerRadius = activity.dpi(22).toFloat()
                setColor(Color.parseColor("#17171A"))
            }
        }

        root.addView(TextView(activity).apply {
            text = tile.label.ifBlank { "Папка" }
            setTextColor(Color.WHITE)
            textSize = 20f
            setPadding(activity.dpi(6), 0, 0, activity.dpi(12))
        })

        val side = activity.dpi(52)
        var row: LinearLayout? = null
        for ((i, item) in tile.items.withIndex()) {
            if (i % COLS == 0) {
                row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                root.addView(row, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ))
            }
            val cell = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(activity.dpi(4), activity.dpi(10), activity.dpi(4), activity.dpi(10))
            }
            val iv = ImageView(activity).apply {
                setImageBitmap(repo.icon(item.pkg, side))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val tv = TextView(activity).apply {
                text = item.label
                setTextColor(Color.WHITE)
                textSize = 11f
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, activity.dpi(4), 0, 0)
            }
            cell.addView(iv, LinearLayout.LayoutParams(side, side))
            cell.addView(tv, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))

            cell.setOnClickListener {
                dialog.dismiss()
                if (!repo.launch(item.pkg)) {
                    Toast.makeText(activity, "«${item.label}» не запускается", Toast.LENGTH_SHORT).show()
                }
            }
            cell.setOnLongClickListener {
                val popup = PopupMenu(activity, cell)
                popup.menu.add(0, 1, 0, "Убрать из папки")
                popup.setOnMenuItemClickListener {
                    removeFromFolder(activity, folderId, item.pkg)
                    dialog.dismiss()
                    onChanged()
                    true
                }
                popup.show()
                true
            }

            row?.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(GradientDrawable().apply { setColor(Color.TRANSPARENT) })
            val w = (activity.resources.displayMetrics.widthPixels * 0.88f).toInt()
            setLayout(w, WindowManager.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }

    /** Убрать приложение из папки; если осталось одно — папка снова становится обычной плиткой. */
    private fun removeFromFolder(activity: Activity, folderId: String, pkg: String) {
        val tiles = TileStore.loadOrCreate(activity)
        val idx = tiles.indexOfFirst { it.id == folderId }
        if (idx < 0) return
        val folder = tiles[idx]
        folder.items.removeAll { it.pkg == pkg }

        if (folder.items.isEmpty()) {
            tiles.removeAt(idx)
        } else if (folder.items.size == 1) {
            val last = folder.items[0]
            tiles[idx] = Tile(
                id = "app:" + last.pkg + ":" + System.currentTimeMillis(),
                type = "app",
                pkg = last.pkg,
                label = last.label,
                color = folder.color,
                spanX = 1,
                spanY = 1
            )
        }
        TileStore.save(activity, tiles)
    }

    /** Собрать папку из двух плиток (вызывается при перетаскивании одной на другую). */
    fun makeFolder(activity: Activity, draggedId: String, targetId: String): Boolean {
        val tiles = TileStore.loadOrCreate(activity)
        val dragged = tiles.firstOrNull { it.id == draggedId } ?: return false
        val target = tiles.firstOrNull { it.id == targetId } ?: return false
        if (dragged.id == target.id) return false

        val items = ArrayList<FolderItem>()
        fun collect(t: Tile) {
            when (t.type) {
                "folder" -> t.items.forEach { if (items.none { i -> i.pkg == it.pkg }) items.add(it) }
                "app" -> t.pkg?.let { p -> if (items.none { it.pkg == p }) items.add(FolderItem(p, t.label)) }
            }
        }
        collect(target)
        collect(dragged)
        if (items.size < 2) return false

        val index = tiles.indexOf(target)
        val folder = Tile(
            id = "folder:" + System.currentTimeMillis(),
            type = "folder",
            pkg = null,
            label = "Папка",
            color = target.color,
            spanX = 1,
            spanY = 1
        )
        folder.items = items
        tiles[index] = folder
        tiles.remove(dragged)
        TileStore.save(activity, tiles)
        return true
    }
}
