package com.plitka.launcher.data

import org.json.JSONArray
import org.json.JSONObject

/** Приложение внутри папки. */
data class FolderItem(var pkg: String, var label: String) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("pkg", pkg)
        put("label", label)
    }

    companion object {
        fun fromJson(o: JSONObject) = FolderItem(o.optString("pkg"), o.optString("label"))
    }
}

/**
 * Одна плитка на рабочем столе.
 *
 * type:
 *  - "app"      — запуск приложения по pkg
 *  - "folder"   — папка с приложениями (список в items)
 *  - "clock"    — живые часы
 *  - "apps"     — список приложений
 *  - "settings" — настройки лаунчера
 */
data class Tile(
    val id: String,
    var type: String = "app",
    var pkg: String? = null,
    var label: String = "",
    var color: Int = DEFAULT_BLUE,
    var spanX: Int = 1,
    var spanY: Int = 1,
    var items: MutableList<FolderItem> = ArrayList()
) {

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type)
        put("pkg", pkg ?: "")
        put("label", label)
        put("color", color)
        put("spanX", spanX)
        put("spanY", spanY)
        if (items.isNotEmpty()) {
            val arr = JSONArray()
            items.forEach { arr.put(it.toJson()) }
            put("items", arr)
        }
    }

    companion object {
        const val DEFAULT_BLUE = 0xFF0078D7.toInt()

        val PALETTE = intArrayOf(
            0xFF0078D7.toInt(), // windows blue
            0xFF00A300.toInt(), // green
            0xFFE51400.toInt(), // red
            0xFFF0A30A.toInt(), // amber
            0xFF6A00FF.toInt(), // purple
            0xFF00ABA9.toInt(), // teal
            0xFFD80073.toInt(), // magenta
            0xFF647687.toInt()  // steel
        )

        fun fromJson(o: JSONObject): Tile {
            val items = ArrayList<FolderItem>()
            val arr = o.optJSONArray("items")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val it = arr.optJSONObject(i) ?: continue
                    items.add(FolderItem.fromJson(it))
                }
            }
            return Tile(
                id = o.optString("id"),
                type = o.optString("type", "app"),
                pkg = o.optString("pkg").ifEmpty { null },
                label = o.optString("label"),
                color = o.optInt("color", DEFAULT_BLUE),
                spanX = o.optInt("spanX", 1),
                spanY = o.optInt("spanY", 1),
                items = items
            )
        }
    }
}
