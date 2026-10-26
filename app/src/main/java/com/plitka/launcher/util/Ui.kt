package com.plitka.launcher.util

import android.content.Context

fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun Context.dpi(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun android.view.View.dpf(v: Float): Float = context.dpf(v)

fun android.view.View.dpi(v: Int): Int = context.dpi(v)
