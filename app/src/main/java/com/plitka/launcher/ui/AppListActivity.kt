package com.plitka.launcher.ui

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.plitka.launcher.data.AppEntry
import com.plitka.launcher.data.AppRepo
import com.plitka.launcher.data.Tile
import com.plitka.launcher.data.TileStore
import com.plitka.launcher.util.applySystemBarPadding
import com.plitka.launcher.util.dpi
import com.plitka.launcher.util.transparentSystemBars

class AppListActivity : Activity() {

    private sealed class Row {
        class Header(val letter: String) : Row()
        class App(val entry: AppEntry) : Row()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        transparentSystemBars()
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.parseColor("#0B0B0D")))
        val repo = AppRepo(this)
        val apps = repo.launchable()

        val rows = ArrayList<Row>()
        var current = ""
        for (a in apps) {
            val letter = a.label.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
            if (letter != current) {
                current = letter
                rows.add(Row.Header(letter))
            }
            rows.add(Row.App(a))
        }

        val list = ListView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0B0D"))
            divider = null
            adapter = Adapter(this@AppListActivity, rows, repo)
        }
        list.applySystemBarPadding()
        list.setOnItemClickListener { _, _, position, _ ->
            (rows[position] as? Row.App)?.let {
                repo.launch(it.entry.pkg)
                finish()
            }
        }

        list.setOnItemLongClickListener { _, _, position, _ ->
            val app = (rows[position] as? Row.App)?.entry
                ?: return@setOnItemLongClickListener false
            addToHome(app)
            true
        }

        setContentView(list)
    }

    private fun addToHome(app: AppEntry) {
        val tiles = TileStore.loadOrCreate(this)
        val onDesktop = tiles.any { it.type == "app" && it.pkg == app.pkg } ||
            tiles.any { t -> t.type == "folder" && t.items.any { it.pkg == app.pkg } }
        if (onDesktop) {
            Toast.makeText(this, "«${app.label}» уже на рабочем столе", Toast.LENGTH_SHORT).show()
            return
        }
        tiles.add(
            Tile(
                id = "app:" + app.pkg + ":" + System.currentTimeMillis(),
                type = "app",
                pkg = app.pkg,
                label = app.label,
                color = Tile.PALETTE[tiles.size % Tile.PALETTE.size],
                spanX = 1,
                spanY = 1
            )
        )
        TileStore.save(this, tiles)
        Toast.makeText(this, "Добавлено: ${app.label}", Toast.LENGTH_SHORT).show()
    }

    private class Adapter(
        private val ctx: Context,
        private val rows: List<Row>,
        private val repo: AppRepo
    ) : BaseAdapter() {

        override fun getCount(): Int = rows.size
        override fun getItem(position: Int): Any = rows[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getViewTypeCount(): Int = 2
        override fun getItemViewType(position: Int): Int = if (rows[position] is Row.Header) 0 else 1
        override fun isEnabled(position: Int): Boolean = rows[position] is Row.App

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            when (val r = rows[position]) {
                is Row.Header -> headerView(r.letter)
                is Row.App -> appView(r.entry)
            }

        private fun headerView(letter: String): TextView = TextView(ctx).apply {
            text = letter
            setTextColor(Color.parseColor("#4CC2FF"))
            textSize = 18f
            setTypeface(Typeface.DEFAULT_BOLD)
            setPadding(ctx.dpi(24), ctx.dpi(16), ctx.dpi(24), ctx.dpi(6))
        }

        private fun appView(entry: AppEntry): LinearLayout {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ctx.dpi(24), ctx.dpi(10), ctx.dpi(24), ctx.dpi(10))
            }
            val img = ImageView(ctx)
            repo.icon(entry.pkg, ctx.dpi(38))?.let { img.setImageBitmap(it) }
            row.addView(img, LinearLayout.LayoutParams(ctx.dpi(38), ctx.dpi(38)))

            val tv = TextView(ctx).apply {
                text = entry.label
                setTextColor(Color.WHITE)
                textSize = 17f
                setPadding(ctx.dpi(18), 0, 0, 0)
            }
            row.addView(tv)
            return row
        }
    }
}
