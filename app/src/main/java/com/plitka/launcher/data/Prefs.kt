package com.plitka.launcher.data

import android.content.Context

class Prefs(ctx: Context) {

    private val sp = ctx.getSharedPreferences("plitka", Context.MODE_PRIVATE)

    var columns: Int
        get() = sp.getInt("columns", 3)
        set(v) = sp.edit().putInt("columns", v.coerceIn(3, 6)).apply()

    var accent: Int
        get() = sp.getInt("accent", Tile.DEFAULT_BLUE)
        set(v) = sp.edit().putInt("accent", v).apply()

    var radiusDp: Int
        get() = sp.getInt("radius", 8)
        set(v) = sp.edit().putInt("radius", v.coerceIn(0, 30)).apply()

    var opacity: Int
        get() = sp.getInt("opacity", 92)
        set(v) = sp.edit().putInt("opacity", v.coerceIn(20, 100)).apply()

    var showLabels: Boolean
        get() = sp.getBoolean("labels", true)
        set(v) = sp.edit().putBoolean("labels", v).apply()

    /** Спрятать верхнюю панель (время, батарея, значки уведомлений). */
    var hideStatusBar: Boolean
        get() = sp.getBoolean("hide_status", false)
        set(v) = sp.edit().putBoolean("hide_status", v).apply()

    /** Первый запуск: уже спрашивали про домашний экран? */
    var homePrompted: Boolean
        get() = sp.getBoolean("home_prompted", false)
        set(v) = sp.edit().putBoolean("home_prompted", v).apply()

    var tilesJson: String?
        get() = sp.getString("tiles", null)
        set(v) = sp.edit().putString("tiles", v).apply()
}
