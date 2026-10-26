package com.plitka.launcher.data

import android.content.Context
import org.json.JSONArray

object TileStore {

    fun loadOrCreate(ctx: Context): MutableList<Tile> {
        val prefs = Prefs(ctx)
        val raw = prefs.tilesJson
        if (!raw.isNullOrBlank()) {
            try {
                val arr = JSONArray(raw)
                val list = ArrayList<Tile>(arr.length())
                for (i in 0 until arr.length()) list.add(Tile.fromJson(arr.getJSONObject(i)))
                if (list.isNotEmpty()) return list
            } catch (e: Exception) {
                // битый json — пересоберём
            }
        }
        return defaultTiles(ctx).also { save(ctx, it) }
    }

    fun save(ctx: Context, tiles: List<Tile>) {
        val arr = JSONArray()
        tiles.forEach { arr.put(it.toJson()) }
        Prefs(ctx).tilesJson = arr.toString()
    }

    fun reset(ctx: Context) {
        Prefs(ctx).tilesJson = null
    }

    fun defaultTiles(ctx: Context): MutableList<Tile> {
        val repo = AppRepo(ctx)
        val apps = repo.launchable()
        val out = ArrayList<Tile>()

        out.add(Tile("clock", "clock", null, "Часы", Tile.PALETTE[0], 2, 1))
        out.add(Tile("apps", "apps", null, "Приложения", Tile.PALETTE[5], 1, 1))
        out.add(Tile("settings", "settings", null, "Настройки", Tile.PALETTE[6], 1, 1))

        var i = 0
        for (a in apps) {
            if (out.size >= 33) break
            out.add(
                Tile(
                    id = "app:" + a.pkg,
                    type = "app",
                    pkg = a.pkg,
                    label = a.label,
                    color = Tile.PALETTE[(i + 3) % Tile.PALETTE.size],
                    spanX = 1,
                    spanY = 1
                )
            )
            i++
        }
        return out
    }
}
