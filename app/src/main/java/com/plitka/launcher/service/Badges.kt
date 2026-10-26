package com.plitka.launcher.service

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Счётчики уведомлений по пакетам. Живёт в памяти процесса лаунчера,
 * обновляется из [NotiListener].
 */
object Badges {

    private val counts = HashMap<String, Int>()
    private val callbacks = CopyOnWriteArrayList<() -> Unit>()

    @Volatile
    var connected: Boolean = false

    fun count(pkg: String?): Int {
        if (pkg == null) return 0
        return counts[pkg] ?: 0
    }

    fun update(map: Map<String, Int>) {
        counts.clear()
        counts.putAll(map)
        callbacks.forEach { it() }
    }

    fun clear() {
        counts.clear()
        callbacks.forEach { it() }
    }

    fun addCallback(cb: () -> Unit) {
        if (!callbacks.contains(cb)) callbacks.add(cb)
    }

    fun removeCallback(cb: () -> Unit) {
        callbacks.remove(cb)
    }
}
