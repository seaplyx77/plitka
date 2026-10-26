package com.plitka.launcher.util

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController

/**
 * Прозрачные системные панели: контент рисуется под полосой жестов и статус-баром,
 * фон окна совпадает с фоном рабочего стола.
 */
fun Activity.transparentSystemBars() {
    val w = window
    if (Build.VERSION.SDK_INT >= 30) {
        w.setDecorFitsSystemWindows(false)
        w.decorView.windowInsetsController?.systemBarsBehavior =
            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    } else {
        @Suppress("DEPRECATION")
        w.decorView.systemUiVisibility = w.decorView.systemUiVisibility or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }
    @Suppress("DEPRECATION")
    w.statusBarColor = Color.TRANSPARENT
    @Suppress("DEPRECATION")
    w.navigationBarColor = Color.TRANSPARENT
    if (Build.VERSION.SDK_INT >= 29) {
        w.isStatusBarContrastEnforced = false
        w.isNavigationBarContrastEnforced = false
    }
}

/** Спрятать/показать верхнюю панель (время, батарея, значки уведомлений). */
fun Activity.setStatusBarHidden(hidden: Boolean) {
    if (Build.VERSION.SDK_INT >= 30) {
        val c = window.decorView.windowInsetsController ?: return
        if (hidden) c.hide(WindowInsets.Type.statusBars())
        else c.show(WindowInsets.Type.statusBars())
    } else {
        @Suppress("DEPRECATION")
        val v = window.decorView
        @Suppress("DEPRECATION")
        var flags = v.systemUiVisibility
        @Suppress("DEPRECATION")
        flags = if (hidden) flags or View.SYSTEM_UI_FLAG_FULLSCREEN
        else flags and View.SYSTEM_UI_FLAG_FULLSCREEN.inv()
        v.systemUiVisibility = flags
    }
}

/** Отступы под системные панели, чтобы контент не уезжал под часы и полосу жестов. */
fun View.applySystemBarPadding() {
    setOnApplyWindowInsetsListener { v, insets ->
        if (Build.VERSION.SDK_INT >= 30) {
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
        } else {
            @Suppress("DEPRECATION")
            v.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
        }
        insets
    }
    requestApplyInsets()
}
